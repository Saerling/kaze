/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

/*
 * Regulador de memoria de Fyx: cada 30 s mira cuánta RAM usa todo el navegador (todos sus
 * procesos). Si pasa del límite, duerme las pestañas que llevan más tiempo sin usarse (no la
 * visible, ni las que suenan, ni las de vídeo flotante). Al volver a una, se recarga sola.
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
      return Math.round(Math.min(4096, Math.max(1536, total * 0.3)));
    },

    async usedMB() {
      const info = await ChromeUtils.requestProcInfo();
      let bytes = info.memory || 0;
      for (const c of info.children || []) bytes += c.memory || 0;
      return bytes / 1048576;
    },

    candidates() {
      return gBrowser.tabs
        .filter(t => !t.selected && !t.closing && t.linkedPanel && !t.hasAttribute("pending") &&
          !t.soundPlaying && !t.pictureinpicture && !t.multiselected &&
          !t.hasAttribute("zen-split") && !t.hasAttribute("split-view") &&
          !t.linkedBrowser?._sharingState?.webRTC?.sharing)
        .sort((a, b) => a.lastAccessed - b.lastAccessed);
    },

    async tick() {
      if (!Services.prefs.getBoolPref("pyx.memory.enabled", true)) return;
      // Solo una ventana mide (la de arriba); cada ventana duerme sus propias pestañas.
      const used = await this.usedMB().catch(() => 0);
      const limit = this.limitMB();
      if (used < limit) return;
      // Cuanto más se pasa, más pestañas duerme de una vez (máximo 4 por vuelta).
      const n = Math.min(4, 1 + Math.floor((used - limit) / 300));
      for (const tab of this.candidates().slice(0, n)) {
        try {
          gBrowser.discardBrowser(tab);
        } catch {}
      }
    },
  };

  window.FyxMemory = FyxMemory;
  const timer = setInterval(() => FyxMemory.tick(), 30_000);
  window.addEventListener("unload", () => clearInterval(timer), { once: true });
})();
