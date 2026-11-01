/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

/*
 * Regulador de memoria de Fyx: mira cuánta RAM usa todo el navegador (todos sus procesos).
 * Si pasa del límite, duerme las pestañas que llevan más tiempo sin usarse, de todas las
 * ventanas (no la visible de cada ventana, ni las que suenan, ni las de vídeo flotante ni las
 * que comparten cámara o pantalla). Al volver a una, se recarga sola.
 *
 * Para no gastar CPU: solo mide una ventana (la primera que siga abierta), y el intervalo se
 * adapta: cada 20 s cerca del límite, cada 90 s si va holgado.
 */

"use strict";

(() => {
  if (window.FyxMemory) return;

  const FyxMemory = {
    limitMB() {
      const set = Services.prefs.getIntPref("pyx.memory.limitMB", 0);
      if (set > 0) return set;
      let total = 8192;
      try {
        total = Services.sysinfo.getProperty("memsize") / 1048576;
      } catch {}
      return Math.round(Math.min(3072, Math.max(1280, total * 0.25)));
    },

    async usedMB() {
      const info = await ChromeUtils.requestProcInfo();
      let bytes = info.memory || 0;
      for (const c of info.children || []) bytes += c.memory || 0;
      return bytes / 1048576;
    },

    /** Pestañas que se pueden dormir, de todas las ventanas, de la más vieja a la más nueva. */
    candidates() {
      const out = [];
      for (const w of Services.wm.getEnumerator("navigator:browser")) {
        if (w.closed || !w.gBrowser) continue;
        for (const t of w.gBrowser.tabs) {
          if (!t.selected && !t.closing && t.linkedPanel && !t.hasAttribute("pending") &&
            !t.soundPlaying && !t.pictureinpicture && !t.multiselected &&
            !t.hasAttribute("zen-split") && !t.hasAttribute("split-view") &&
            !t.hasAttribute("zen-empty-tab") &&
            !t.linkedBrowser?._sharingState?.webRTC?.sharing) out.push(t);
        }
      }
      return out.sort((a, b) => a.lastAccessed - b.lastAccessed);
    },

    /** La ventana que mide: la primera abierta (si se cierra, la siguiente toma el relevo). */
    isLeader() {
      for (const w of Services.wm.getEnumerator("navigator:browser")) {
        if (!w.closed && w.FyxMemory) return w == window;
      }
      return true;
    },

    async tick() {
      if (!Services.prefs.getBoolPref("pyx.memory.enabled", true) || !this.isLeader()) return 90_000;
      const used = await this.usedMB().catch(() => 0);
      const limit = this.limitMB();
      if (used < limit) return used < limit * 0.7 ? 90_000 : 20_000;
      // Cuanto más se pasa, más pestañas duerme de una vez (máximo 4 por vuelta).
      const n = Math.min(4, 1 + Math.floor((used - limit) / 300));
      for (const tab of this.candidates().slice(0, n)) {
        try {
          tab.ownerGlobal.gBrowser.discardBrowser(tab);
        } catch {}
      }
      return 20_000;
    },
  };

  window.FyxMemory = FyxMemory;
  let timer;
  const loop = async () => {
    const next = await FyxMemory.tick().catch(() => 60_000);
    if (!window.closed) timer = setTimeout(loop, next);
  };
  timer = setTimeout(loop, 30_000);
  window.addEventListener("unload", () => clearTimeout(timer), { once: true });
})();
