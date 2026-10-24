/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

/*
 * Tinte del sitio web, como Safari ("Permitir tinte del sitio web"): la barra de arriba toma
 * el color de la parte superior de la página. Se toma una miniatura diminuta de lo visible
 * (5 % de tamaño, unos pocos píxeles) y se promedia su borde de arriba. Barato: solo al
 * terminar de cargar o al cambiar de pestaña.
 * Ajuste: pyx.tint.enabled (activado por defecto).
 */
"use strict";
(() => {
  const SCALE = 0.05;
  const canvas = document.createElementNS("http://www.w3.org/1999/xhtml", "canvas");
  const ctx = canvas.getContext("2d", { willReadFrequently: true });
  let timer = 0;

  const FyxTint = {
    enabled: () => Services.prefs.getBoolPref("pyx.tint.enabled", true),

    async sample(browser) {
      try {
        const wg = browser.browsingContext?.currentWindowGlobal;
        if (!wg) return null;
        const bmp = await wg.drawSnapshot(null, SCALE, "white");
        canvas.width = bmp.width;
        canvas.height = Math.min(2, bmp.height);
        ctx.drawImage(bmp, 0, 0);
        bmp.close();
        const d = ctx.getImageData(0, 0, canvas.width, canvas.height).data;
        let r = 0, g = 0, b = 0, n = 0;
        for (let i = 0; i < d.length; i += 4) { r += d[i]; g += d[i + 1]; b += d[i + 2]; n++; }
        return n ? [r / n, g / n, b / n].map(Math.round) : null;
      } catch {
        return null;
      }
    },

    apply(rgb) {
      const root = document.documentElement;
      if (!rgb) {
        root.removeAttribute("pyx-tinted");
        root.style.removeProperty("--pyx-tint");
        return;
      }
      const [r, g, b] = rgb;
      const lum = (0.2126 * r + 0.7152 * g + 0.0722 * b) / 255;
      root.style.setProperty("--pyx-tint", `rgb(${r} ${g} ${b})`);
      // Claro u oscuro decide el color de íconos y texto de la barra (contraste legible).
      root.setAttribute("pyx-tinted", lum > 0.55 ? "light" : "dark");
    },

    async update() {
      const browser = gBrowser.selectedBrowser;
      if (!this.enabled() || !/^(https?|file|data):/.test(browser.currentURI?.spec || "")) return this.apply(null);
      const rgb = await this.sample(browser);
      if (browser == gBrowser.selectedBrowser) this.apply(rgb);
    },

    schedule(ms) {
      clearTimeout(timer);
      timer = setTimeout(() => this.update(), ms);
    },
  };

  gBrowser.tabContainer.addEventListener("TabSelect", () => FyxTint.schedule(80));
  gBrowser.addTabsProgressListener({
    onStateChange(browser, webProgress, request, flags) {
      if (browser == gBrowser.selectedBrowser && webProgress.isTopLevel && flags & Ci.nsIWebProgressListener.STATE_STOP) FyxTint.schedule(200);
    },
    onLocationChange(browser, webProgress) {
      if (browser == gBrowser.selectedBrowser && webProgress.isTopLevel) FyxTint.schedule(500);
    },
  });
  window.FyxTint = FyxTint;
})();
