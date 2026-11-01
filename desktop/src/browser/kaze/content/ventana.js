/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

/*
 * Ventana de Fyx al estilo Safari (macOS 27):
 * - Transparencia del vidrio ajustable (pref pyx.glass.opacity, 0 = más transparente, 1 = opaco),
 *   con un control deslizante en el menú de Fyx.
 * - Recargar/detener dentro de la dirección, como Safari.
 * - Botón Compartir en la barra (en Windows abre el panel de compartir del sistema).
 * - Animaciones de abrir/cerrar pestañas en la barra lateral con la curva de Fyx (más suaves que las de Zen).
 */
"use strict";
(() => {
  const PREF_GLASS = "pyx.glass.opacity";
  const root = document.documentElement;
  const XUL = "http://www.mozilla.org/keymaster/gatekeeper/there.is.only.xul";
  const HTML = "http://www.w3.org/1999/xhtml";
  const EASE = [0.16, 1, 0.3, 1];

  // ── Transparencia del vidrio ───────────────────────────────────────
  const glass = () => {
    let v = 0.5;
    try { v = parseFloat(Services.prefs.getStringPref(PREF_GLASS, "0.5")); } catch {}
    return Math.min(1, Math.max(0, Number.isFinite(v) ? v : 0.5));
  };
  const applyGlass = () => {
    const v = glass();
    root.style.setProperty("--pyx-glass-o", String(v));
    const slider = document.getElementById("pyx-glass-slider");
    if (slider && slider.value != Math.round(v * 100)) slider.value = Math.round(v * 100);
  };
  applyGlass();
  Services.prefs.addObserver(PREF_GLASS, applyGlass);
  window.addEventListener("unload", () => Services.prefs.removeObserver(PREF_GLASS, applyGlass), { once: true });

  // Control deslizante en el menú de Fyx (☰), arriba del todo.
  const addGlassControl = view => {
    if (!view || view.querySelector("#pyx-glass-item")) return;
    const body = view.querySelector(".panel-subview-body") || view;
    const item = document.createXULElement("toolbaritem");
    item.id = "pyx-glass-item";
    item.setAttribute("closemenu", "none");
    const label = document.createXULElement("label");
    label.id = "pyx-glass-label";
    label.setAttribute("value", "Vidrio");
    const less = document.createXULElement("image");
    less.className = "pyx-glass-end pyx-glass-less";
    const slider = document.createElementNS(HTML, "input");
    slider.id = "pyx-glass-slider";
    slider.type = "range";
    slider.min = 0;
    slider.max = 100;
    slider.step = 5;
    slider.value = Math.round(glass() * 100);
    slider.setAttribute("aria-label", "Transparencia del vidrio");
    slider.title = "Más transparente ↔ más opaco";
    slider.addEventListener("input", () => Services.prefs.setStringPref(PREF_GLASS, String(slider.value / 100)));
    const more = document.createXULElement("image");
    more.className = "pyx-glass-end pyx-glass-more";
    item.append(label, less, slider, more);
    const sep = document.createXULElement("toolbarseparator");
    sep.id = "pyx-glass-sep";
    body.prepend(item, sep);
  };
  window.addEventListener("ViewShowing", e => {
    if (e.target.id == "appMenu-mainView") addGlassControl(e.target);
  });

  // Los menús (menupopup) no traen animación de apertura fuera de macOS: se marca [pyx-open]
  // para que pyx.css los haga aparecer desde su origen.
  window.addEventListener("popupshowing", e => {
    if (e.target.localName == "menupopup") e.target.setAttribute("pyx-open", "");
  }, true);
  window.addEventListener("popuphidden", e => {
    if (e.target.localName == "menupopup") e.target.removeAttribute("pyx-open");
  }, true);

  // ── Recargar dentro de la dirección ────────────────────────────────
  const reload = () => {
    const input = document.querySelector("#urlbar .urlbar-input-container");
    if (!input || document.getElementById("pyx-reload")) return;
    const box = document.createXULElement("hbox");
    box.id = "pyx-reload";
    box.className = "urlbar-page-action";
    box.setAttribute("role", "button");
    box.setAttribute("tooltiptext", "Recargar esta página (Ctrl+R)");
    const img = document.createXULElement("image");
    img.className = "urlbar-icon";
    box.append(img);
    box.addEventListener("click", e => {
      if (e.button != 0) return;
      if (box.hasAttribute("loading")) BrowserCommands.stop();
      else if (e.shiftKey || e.ctrlKey) BrowserCommands.reloadSkipCache();
      else BrowserCommands.reload();
    });
    input.append(box);
    root.setAttribute("pyx-reload-inside", "");
    const sync = () => {
      const busy = !!gBrowser.selectedBrowser?.webProgress?.isLoadingDocument;
      box.toggleAttribute("loading", busy);
      box.setAttribute("tooltiptext", busy ? "Detener la carga (Esc)" : "Recargar esta página (Ctrl+R)");
    };
    gBrowser.addProgressListener({
      QueryInterface: ChromeUtils.generateQI(["nsIWebProgressListener", "nsISupportsWeakReference"]),
      onStateChange(wp) { if (wp.isTopLevel) sync(); },
      onLocationChange: sync,
    });
    gBrowser.tabContainer.addEventListener("TabSelect", sync);
    sync();
  };

  // ── Compartir ──────────────────────────────────────────────────────
  const share = win => {
    const b = win.gBrowser.selectedBrowser;
    const url = b.currentURI?.displaySpec || "";
    const title = b.contentTitle || url;
    if (!url) return;
    try {
      if (AppConstants.platform == "win") {
        Cc["@mozilla.org/windows-ui-utils;1"].getService(Ci.nsIWindowsUIUtils).shareUrl(url, title);
        return;
      }
    } catch (e) {
      console.warn("[Fyx] compartir", e);
    }
    Cc["@mozilla.org/widget/clipboardhelper;1"].getService(Ci.nsIClipboardHelper).copyString(url);
    const anchor = win.document.getElementById("pyx-share-button");
    try { win.ConfirmationHint.show(anchor, "confirmation-hint-copy-url"); } catch {}
  };
  const layout = () => {
    const CUI = window.CustomizableUI;
    if (!CUI) return;
    try {
      if (CUI.getWidget("pyx-share-button")?.provider != CUI.PROVIDER_API) {
        CUI.createWidget({
          id: "pyx-share-button", type: "button", label: "Compartir",
          tooltiptext: "Compartir esta página", defaultArea: CUI.AREA_NAVBAR,
          onCommand: e => share(e.target.ownerGlobal),
        });
      }
      if (!Services.prefs.getBoolPref("pyx.layout.share", false)) {
        const place = CUI.getPlacementOfWidget("new-tab-button");
        if (place?.area == CUI.AREA_NAVBAR) CUI.addWidgetToArea("pyx-share-button", CUI.AREA_NAVBAR, place.position);
        Services.prefs.setBoolPref("pyx.layout.share", true);
      }
    } catch (e) {
      console.warn("[Fyx] compartir", e);
    }
  };

  // ── Pestañas: abrir y cerrar con la curva de Fyx ───────────────────
  const tabs = () => {
    const vt = window.gZenVerticalTabsManager;
    if (!vt || typeof vt._startItemOpenAnimation != "function" || typeof vt.animateItemClose != "function") return;
    vt._startItemOpenAnimation = function (item, marginBottom) {
      const clean = () => {
        for (const p of ["margin-bottom", "transform", "opacity"]) item.style.removeProperty(p);
      };
      try {
        gZenUIManager.motion
          .animate(item, { opacity: [0, 1], transform: ["translateY(-4px) scale(0.97)", "translateY(0px) scale(1)"], marginBottom },
            { duration: 0.24, ease: EASE, easing: EASE })
          .then(clean, clean);
      } catch {
        clean();
      }
    };
    const close = vt.animateItemClose.bind(vt);
    vt.animateItemClose = function (item) {
      if (item.hasAttribute("zen-essential") || item.group?.hasAttribute("split-view-group") ||
        !gZenUIManager.motion || gReduceMotion || document.hidden) return close(item);
      const height = window.windowUtils.getBoundsWithoutFlushing(item).height;
      const items = gBrowser.tabContainer.ariaFocusableItems;
      const last = items[items.length - 1] === item;
      return gZenUIManager.motion.animate(item, {
        opacity: [1, 0],
        transform: ["scale(1)", "scale(0.96)"],
        ...(last ? {} : { marginBottom: ["0px", `-${height}px`] }),
      }, { duration: 0.2, ease: EASE, easing: EASE });
    };
  };

  const init = () => {
    for (const f of [reload, layout, tabs]) {
      try { f(); } catch (e) { console.error("[Fyx] ventana", e); }
    }
  };
  init();
  window.FyxWindow = { share, applyGlass };
})();
