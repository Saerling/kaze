/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

/*
 * Funciones de Fyx (Android) que le faltaban a Fyx para Windows:
 *  - La página de inicio de Fyx en las pestañas nuevas (favoritos, frecuentes, privacidad,
 *    "Reanudar navegación" y lista de lectura). Pref pyx.startpage.enabled.
 *  - Lista de lectura, como Safari: Ctrl+Mayús+D o el menú contextual de la página la añaden
 *    o la quitan. Se guarda en una carpeta de marcadores (se sincroniza con Firefox Sync).
 */

"use strict";

(() => {
  if (window.FyxExtras) return;
  const HTML = "http://www.w3.org/1999/xhtml";
  const XUL = "http://www.mozilla.org/keymaster/gatekeeper/there.is.only.xul";
  const START_URL = "chrome://kaze/content/inicio.html";
  const PREF_START = "pyx.startpage.enabled";
  const PREF_READING = "kaze.readinglist.guid";

  const FyxExtras = {
    // ── Página de inicio ─────────────────────────────────────────────
    startPage() {
      if (!Services.prefs.getBoolPref(PREF_START, true)) return;
      try {
        const { AboutNewTab } = ChromeUtils.importESModule("resource:///modules/AboutNewTab.sys.mjs");
        if (AboutNewTab.newTabURL != START_URL) AboutNewTab.newTabURL = START_URL;
      } catch (e) {
        console.warn("[Fyx] página de inicio", e);
      }
      // Que la dirección quede vacía (y con el cursor) en la página de inicio, como con about:newtab.
      if (window.gInitialPages && !gInitialPages.includes(START_URL)) gInitialPages.push(START_URL);
    },

    // ── Lista de lectura ─────────────────────────────────────────────
    async folder() {
      let guid = Services.prefs.getStringPref(PREF_READING, "");
      if (guid && await PlacesUtils.bookmarks.fetch(guid)) return guid;
      const f = await PlacesUtils.bookmarks.insert({
        parentGuid: PlacesUtils.bookmarks.menuGuid,
        type: PlacesUtils.bookmarks.TYPE_FOLDER,
        title: "Lista de lectura",
      });
      Services.prefs.setStringPref(PREF_READING, f.guid);
      return f.guid;
    },

    async inReadingList(url) {
      const guid = Services.prefs.getStringPref(PREF_READING, "");
      if (!guid) return [];
      const found = [];
      await PlacesUtils.bookmarks.fetch({ url }, b => {
        if (b.parentGuid == guid) found.push(b.guid);
      });
      return found;
    },

    async toggleReadingList(browser = gBrowser.selectedBrowser) {
      const url = browser.currentURI.spec;
      if (!/^https?:/.test(url)) return this.toast("Esta página no se puede añadir a la lista de lectura");
      const items = await this.inReadingList(url);
      if (items.length) {
        await PlacesUtils.bookmarks.remove(items);
        this.toast("Quitada de la lista de lectura");
      } else {
        const parentGuid = await this.folder();
        await PlacesUtils.bookmarks.insert({ parentGuid, url, title: browser.contentTitle || url, index: 0 });
        this.toast("Añadida a la lista de lectura");
      }
    },

    readingMenu() {
      const menu = document.getElementById("contentAreaContextMenu");
      if (!menu || document.getElementById("pyx-context-reading")) return;
      const item = document.createElementNS(XUL, "menuitem");
      item.id = "pyx-context-reading";
      item.setAttribute("label", "Añadir a la lista de lectura");
      item.addEventListener("command", () => this.toggleReadingList());
      const anchor = document.getElementById("context-savepage");
      anchor ? anchor.before(item) : menu.append(item);
      menu.addEventListener("popupshowing", async e => {
        if (e.target != menu) return;
        const cm = window.gContextMenu;
        const show = !!cm && !(cm.isContentSelected || cm.onTextInput || cm.onLink || cm.onImage ||
          cm.onVideo || cm.onAudio || cm.onCanvas) && /^https?:/.test(gBrowser.currentURI.spec);
        item.hidden = !show;
        if (!show) return;
        const has = (await this.inReadingList(gBrowser.currentURI.spec)).length;
        item.setAttribute("label", has ? "Quitar de la lista de lectura" : "Añadir a la lista de lectura");
      });
    },

    toast(text) {
      this._toast?.remove();
      const t = document.createElementNS(HTML, "div");
      t.id = "pyx-toast";
      t.setAttribute("role", "status");
      t.textContent = text;
      document.documentElement.append(t);
      this._toast = t;
      clearTimeout(this._toastTimer);
      this._toastTimer = setTimeout(() => {
        t.setAttribute("closing", "true");
        setTimeout(() => t.remove(), 200);
      }, 1800);
    },
  };

  window.FyxExtras = FyxExtras;
  FyxExtras.startPage();
  FyxExtras.readingMenu();
  window.addEventListener("keydown", e => {
    // Ctrl+Mayús+D: lista de lectura (como Cmd+Mayús+D en Safari).
    if ((e.ctrlKey || e.metaKey) && e.shiftKey && !e.altKey && e.code == "KeyD") {
      e.preventDefault();
      e.stopPropagation();
      FyxExtras.toggleReadingList();
    }
  }, true);
})();
