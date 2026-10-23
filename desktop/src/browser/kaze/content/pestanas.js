/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

/*
 * Vista de todas las pestañas como en Safari: cuadrícula de miniaturas que se abre con una
 * animación desde la página. Botón en la barra o Ctrl+Mayús+\. Solo el espacio actual de Zen.
 * También ordena la barra superior como Safari (atrás/adelante a la izquierda, dirección al
 * centro, pestaña nueva y todas las pestañas a la derecha).
 */

"use strict";

(() => {
  if (window.FyxTabs) return;
  const HTML = "http://www.w3.org/1999/xhtml";
  const h = (tag, props = {}, ...kids) => {
    const e = document.createElementNS(HTML, tag);
    for (const [k, v] of Object.entries(props)) {
      if (v == null || v === false) continue;
      if (k.startsWith("on")) e.addEventListener(k.slice(2), v);
      else if (k == "class") e.className = v;
      else if (k == "text") e.textContent = v;
      else e.setAttribute(k, v === true ? "" : v);
    }
    e.append(...kids.flat().filter(c => c != null));
    return e;
  };

  const FyxTabs = {
    thumbs: new WeakMap(),

    async capture(tab) {
      try {
        if (!tab || tab.closing || !tab.linkedPanel || tab.hasAttribute("pending") || !window.PageThumbs) return;
        const canvas = h("canvas", { width: 480, height: 300 });
        if ((await PageThumbs.captureTabPreviewThumbnail(tab.linkedBrowser, canvas)) === false) return;
        const blob = await new Promise(r => canvas.toBlob(r, "image/jpeg", 0.8));
        if (!blob || tab.closing) return;
        const old = this.thumbs.get(tab);
        if (old) URL.revokeObjectURL(old);
        this.thumbs.set(tab, URL.createObjectURL(blob));
      } catch {}
    },

    toggle() {
      if (this.el?.isConnected) this.close();
      else this.open();
    },

    async open() {
      await this.capture(gBrowser.selectedTab);
      this.query = "";
      this.el = h("div", { id: "pyx-overview", tabindex: "-1", onclick: e => { if (e.target == this.el) this.close(); } });
      this.el.addEventListener("keydown", e => { if (e.key == "Escape") { e.preventDefault(); this.close(); } });
      document.documentElement.setAttribute("pyx-overview", "true");
      document.body.append(this.el);
      this.render();
      this.el.querySelector("input")?.focus();
    },

    close() {
      if (!this.el) return;
      const el = this.el;
      this.el = null;
      el.setAttribute("closing", "true");
      document.documentElement.removeAttribute("pyx-overview");
      setTimeout(() => el.remove(), 180);
      gBrowser.selectedBrowser.focus();
    },

    render() {
      const q = (this.query || "").toLowerCase();
      const tabs = gBrowser.visibleTabs.filter(t => !t.closing && (!q || t.label.toLowerCase().includes(q) ||
        (t.linkedBrowser?.currentURI?.spec || "").toLowerCase().includes(q)));
      const card = (tab, i) => {
        const thumb = this.thumbs.get(tab);
        const icon = tab.getAttribute("image") || "chrome://global/skin/icons/defaultFavicon.svg";
        return h("div", {
          class: "pyx-card", selected: tab.selected, style: `--i:${i}`,
          onclick: () => { gBrowser.selectedTab = tab; this.close(); },
        },
        h("div", { class: "pyx-thumb" },
          thumb ? h("img", { src: thumb, alt: "" }) : h("img", { class: "pyx-icon", src: icon, alt: "" }),
          h("button", { class: "pyx-close", title: "Cerrar", text: "×", onclick: e => {
            e.stopPropagation();
            gBrowser.removeTab(tab, { animate: false });
            setTimeout(() => this.el && this.render(), 40);
          } })),
        h("div", { class: "pyx-title" }, h("img", { src: icon, alt: "" }), h("span", { text: tab.label })));
      };
      const search = h("input", { type: "search", placeholder: "Buscar pestañas", value: this.query });
      search.addEventListener("input", () => {
        this.query = search.value;
        this.render();
        const s = this.el.querySelector("input");
        s.focus();
        s.setSelectionRange(s.value.length, s.value.length);
      });
      this.el.replaceChildren(h("div", { class: "pyx-panel" },
        h("div", { class: "pyx-top" },
          h("span", { class: "pyx-count", text: `${gBrowser.visibleTabs.length} pestañas` }),
          search,
          h("button", { class: "pyx-new", title: "Organizar pestañas por sitio", text: "✦", onclick: () => { this.organize(); this.render(); } }),
          h("button", { class: "pyx-new", title: "Pestaña nueva", text: "+", onclick: () => { this.close(); BrowserCommands.openTab(); } }),
          h("button", { class: "pyx-done", text: "Listo", onclick: () => this.close() })),
        tabs.length ? h("div", { class: "pyx-grid" }, ...tabs.map(card))
          : h("div", { class: "pyx-empty", text: "Ninguna pestaña coincide." })));
    },

    /** Organizar pestañas (como Safari en iPadOS 27): junta las del mismo sitio, en el orden en que aparecen. */
    organize() {
      const host = t => { try { return new URL(t.linkedBrowser.currentURI.spec).hostname.replace(/^www\./, ""); } catch { return ""; } };
      const tabs = gBrowser.visibleTabs.filter(t => !t.pinned && !t.hasAttribute("zen-essential"));
      if (tabs.length < 3) return;
      const order = [...new Set(tabs.map(host))];
      const sorted = [...tabs].sort((a, b) => order.indexOf(host(a)) - order.indexOf(host(b)));
      const start = tabs[0]._tPos;
      sorted.forEach((t, i) => {
        try { gBrowser.moveTabTo(t, { tabIndex: start + i }); } catch { gBrowser.moveTabTo(t, start + i); }
      });
    },

    layout() {
      const CUI = window.CustomizableUI;
      if (!CUI) return;
      try {
        if (CUI.getWidget("pyx-overview-button")?.provider != CUI.PROVIDER_API) {
          CUI.createWidget({
            id: "pyx-overview-button", type: "button", label: "Todas las pestañas",
            tooltiptext: "Mostrar todas las pestañas (Ctrl+Mayús+\\)", defaultArea: CUI.AREA_NAVBAR,
            onCommand: e => e.target.ownerGlobal.FyxTabs?.toggle(),
          });
        }
      } catch {}
      if (Services.prefs.getBoolPref("pyx.layout.safari", false)) return;
      try {
        ["back-button", "forward-button", "stop-reload-button", "urlbar-container", "downloads-button",
          "new-tab-button", "pyx-overview-button", "unified-extensions-button"].forEach((id, i) => {
          if (CUI.getWidget(id)?.provider) CUI.addWidgetToArea(id, CUI.AREA_NAVBAR, i);
        });
        Services.prefs.setBoolPref("pyx.layout.safari", true);
      } catch (e) {
        console.warn("[Fyx] barra", e);
      }
    },
  };

  window.FyxTabs = FyxTabs;
  FyxTabs.layout();
  gBrowser.tabContainer.addEventListener("TabSelect", e => {
    const prev = e.detail?.previousTab;
    if (prev && !prev.closing) FyxTabs.capture(prev);
  });
  gBrowser.tabContainer.addEventListener("TabClose", e => {
    const u = FyxTabs.thumbs.get(e.target);
    if (u) URL.revokeObjectURL(u);
  });
  window.addEventListener("keydown", e => {
    if (e.ctrlKey && e.shiftKey && !e.altKey && e.code == "Backslash") {
      e.preventDefault();
      FyxTabs.toggle();
    }
  }, true);
})();
