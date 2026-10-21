/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

/*
 * Kaze — capa de interfaz sobre las pestañas verticales de Firefox.
 *
 * Escrita desde cero tomando como guía las ideas de Zen Browser, pero apoyándose
 * en lo que Firefox ya trae (pestañas verticales, pestañas fijadas, SessionStore)
 * para necesitar muy pocos parches y romperse poco con cada versión nueva.
 *
 *  - Espacios: cada pestaña pertenece a un espacio; solo se ven las del espacio activo.
 *  - Essentials: pestañas fijadas globales, en cuadrícula, visibles en todos los espacios.
 *  - Fijadas del espacio: pestañas fijadas que solo aparecen en su espacio.
 *  - Modo compacto: barra lateral y barra de dirección ocultas hasta pasar el ratón.
 *  - Dormir pestañas: las pestañas sin usar se descargan de la memoria.
 *
 * Se carga en browser.xhtml (build de Kaze) o con el cargador de desarrollo
 * (desktop/dev) sobre un Firefox normal. El código es el mismo en ambos casos.
 */

"use strict";

(() => {
  if (window.KazeUI) return;

  // browser.js ya define SessionStore en la ventana; por si acaso, se importa (la ruta cambió en Firefox 14x).
  const SessionStore = window.SessionStore ?? (() => {
    for (const url of [
      "moz-src:///browser/components/sessionstore/SessionStore.sys.mjs",
      "resource:///modules/sessionstore/SessionStore.sys.mjs",
    ]) {
      try {
        return ChromeUtils.importESModule(url).SessionStore;
      } catch {}
    }
    throw new Error("[Kaze] no se encontró SessionStore");
  })();

  const PREF_SPACES = "kaze.spaces.data";
  const PREF_COMPACT = "kaze.compact.enabled";
  const PREF_UNLOAD = "kaze.unloader.enabled";
  const PREF_UNLOAD_MIN = "kaze.unloader.minutes";
  const PREF_FLOATING = "kaze.theme.floating";
  const XUL = "http://www.mozilla.org/keymaster/gatekeeper/there.is.only.xul";
  const HTML = "http://www.w3.org/1999/xhtml";

  /** Los mismos degradados que la app de Android (shared/design/tokens.json). */
  const PALETTE = [
    { name: "Salvia", colors: ["#DCE8D4", "#B9D3B0"] },
    { name: "Arena", colors: ["#F1E3D3", "#E8C7A8"] },
    { name: "Rosa", colors: ["#F5D9DF", "#E9B5C4", "#F3E1C9"] },
    { name: "Cielo", colors: ["#D7E3F4", "#B7CBEA"] },
    { name: "Lavanda", colors: ["#E3DAF5", "#C9B8EC", "#D6E6F7"] },
    { name: "Menta", colors: ["#D4EEEA", "#A8DAD2"] },
    { name: "Limón", colors: ["#F6EBC4", "#F0D58C"] },
    { name: "Durazno", colors: ["#F4D6C8", "#E9A98C", "#F3DCC2"] },
    { name: "Grafito", colors: ["#E2E2E2", "#C8C8CC"] },
  ];
  const EMOJIS = [
    "🌿", "💼", "🏠", "🎮", "📚", "🎵", "🎨", "✈️", "🛒", "💡", "🧪", "📈",
    "🍳", "⚽", "🎬", "📷", "💻", "🧘", "❤️", "⭐", "🔥", "🌙", "☕", "🐾",
  ];

  const uid = () => Services.uuid.generateUUID().toString().slice(1, 9);

  const KazeUI = {
    spaces: [],
    activeId: null,
    _applying: false,
    _unloadTimer: null,

    // ── Arranque ──────────────────────────────────────────────────────

    init() {
      if (this._ready) return;
      this._ready = true;
      document.documentElement.setAttribute("kaze", "true");
      this.loadSpaces();
      this.activeId = SessionStore.getCustomWindowValue(window, "kaze-space") || this.spaces[0].id;
      if (!this.space(this.activeId)) this.activeId = this.spaces[0].id;

      for (const tab of gBrowser.tabs) this.adoptTab(tab);
      this.buildUI();
      this.listen();
      this.applyCompact();
      this.applyFloating();
      this.switchTo(this.activeId, { force: true });
      this.startUnloader();
      console.info("[Kaze] interfaz lista");
    },

    // ── Datos de espacios (compartidos entre ventanas vía preferencia) ─

    loadSpaces() {
      let data = [];
      try {
        data = JSON.parse(Services.prefs.getStringPref(PREF_SPACES, "") || "[]");
      } catch (e) {
        console.warn("[Kaze] espacios dañados, se crean de nuevo", e);
      }
      if (!Array.isArray(data) || !data.length) {
        data = [
          { id: uid(), name: "Personal", emoji: "🌿", colors: PALETTE[0].colors },
          { id: uid(), name: "Trabajo", emoji: "💼", colors: PALETTE[3].colors },
        ];
        this.spaces = data;
        this.saveSpaces();
      }
      this.spaces = data;
    },

    saveSpaces() {
      Services.prefs.setStringPref(PREF_SPACES, JSON.stringify(this.spaces));
    },

    space(id) {
      return this.spaces.find(s => s.id == id);
    },

    // ── Pestañas ──────────────────────────────────────────────────────

    isEssential(tab) {
      return tab.pinned && tab.hasAttribute("kaze-essential");
    },

    /** SessionStore manda: el atributo es solo una copia para el CSS (al restaurar puede llegar tarde). */
    spaceOf(tab) {
      const saved = SessionStore.getCustomTabValue(tab, "kaze-space");
      if (saved && saved != tab.getAttribute("kaze-space")) tab.setAttribute("kaze-space", saved);
      return saved || tab.getAttribute("kaze-space");
    },

    /** Lee de SessionStore el espacio de una pestaña. Si no tiene, se asume el activo (sin guardarlo aún). */
    adoptTab(tab) {
      const essential = SessionStore.getCustomTabValue(tab, "kaze-essential") == "1";
      tab.toggleAttribute("kaze-essential", essential && tab.pinned);
      const sid = SessionStore.getCustomTabValue(tab, "kaze-space");
      if (sid && this.space(sid)) tab.setAttribute("kaze-space", sid);
      else if (sid) this.setTabSpace(tab, this.spaces[0].id); // su espacio fue eliminado
      else if (!tab.hasAttribute("kaze-space")) tab.setAttribute("kaze-space", this.activeId || this.spaces[0].id);
    },

    setTabSpace(tab, sid) {
      tab.setAttribute("kaze-space", sid);
      SessionStore.setCustomTabValue(tab, "kaze-space", sid);
    },

    setEssential(tab, on) {
      if (on) {
        if (!tab.pinned) gBrowser.pinTab(tab);
        tab.setAttribute("kaze-essential", "true");
        SessionStore.setCustomTabValue(tab, "kaze-essential", "1");
      } else {
        tab.removeAttribute("kaze-essential");
        SessionStore.deleteCustomTabValue(tab, "kaze-essential");
        this.setTabSpace(tab, this.activeId);
      }
      this.applyVisibility();
    },

    moveTab(tab, sid) {
      if (this.isEssential(tab)) this.setEssential(tab, false);
      this.setTabSpace(tab, sid);
      if (tab.selected && sid != this.activeId) this.switchTo(sid);
      else this.applyVisibility();
    },

    tabsIn(sid) {
      return gBrowser.tabs.filter(t => !this.isEssential(t) && this.spaceOf(t) == sid);
    },

    /** Muestra las pestañas del espacio activo y las Essentials; esconde el resto. */
    applyVisibility() {
      if (this._applying) return;
      this._applying = true;
      try {
        for (const tab of gBrowser.tabs) {
          if (tab.closing) continue;
          const show = this.isEssential(tab) || this.spaceOf(tab) == this.activeId;
          if (tab.pinned) {
            // gBrowser.hideTab() no acepta fijadas: las de otro espacio se ocultan con el atributo.
            if (show == tab.hidden) {
              tab.toggleAttribute("hidden", !show);
              gBrowser.tabContainer._invalidateCachedVisibleTabs?.();
            }
          } else if (show) {
            gBrowser.showTab(tab);
          } else {
            gBrowser.hideTab(tab);
          }
        }
      } finally {
        this._applying = false;
      }
    },

    // ── Cambiar de espacio ────────────────────────────────────────────

    switchTo(sid, { force = false } = {}) {
      const target = this.space(sid);
      if (!target || (!force && sid == this.activeId)) return;
      this.activeId = sid;
      SessionStore.setCustomWindowValue(window, "kaze-space", sid);

      // Primero elegir una pestaña visible del nuevo espacio (la seleccionada no se puede ocultar).
      const sel = gBrowser.selectedTab;
      if (!this.isEssential(sel) && this.spaceOf(sel) != sid) {
        const candidates = this.tabsIn(sid).sort((a, b) => b.lastAccessed - a.lastAccessed);
        if (candidates.length) {
          gBrowser.selectedTab = candidates[0];
        } else {
          this._opening = sid;
          gBrowser.selectedTab = gBrowser.addTrustedTab(BROWSER_NEW_TAB_URL, { skipAnimation: true });
          this._opening = null;
        }
      }
      this.applyVisibility();
      this.paint();
    },

    step(delta) {
      const i = this.spaces.findIndex(s => s.id == this.activeId);
      const next = this.spaces[(i + delta + this.spaces.length) % this.spaces.length];
      this.switchTo(next.id);
    },

    // ── Crear / editar espacios ───────────────────────────────────────

    addSpace() {
      const name = this.ask("Nuevo espacio", "Nombre del espacio:", "Espacio " + (this.spaces.length + 1));
      if (name == null) return;
      const used = new Set(this.spaces.map(s => s.emoji));
      const emoji = EMOJIS.find(e => !used.has(e)) || "⭐";
      const colors = PALETTE[this.spaces.length % PALETTE.length].colors;
      const s = { id: uid(), name: name.trim() || "Espacio", emoji, colors };
      this.spaces.push(s);
      this.saveSpaces();
      this.switchTo(s.id);
    },

    renameSpace(sid) {
      const s = this.space(sid);
      const name = s && this.ask("Renombrar espacio", "Nombre:", s.name);
      if (!name) return;
      s.name = name.trim();
      this.saveSpaces();
      this.paint();
    },

    setSpaceEmoji(sid, emoji) {
      const s = this.space(sid);
      if (!s) return;
      s.emoji = emoji;
      this.saveSpaces();
      this.paint();
    },

    setSpaceColors(sid, colors) {
      const s = this.space(sid);
      if (!s) return;
      s.colors = colors;
      this.saveSpaces();
      this.paint();
    },

    moveSpace(sid, delta) {
      const i = this.spaces.findIndex(s => s.id == sid);
      const j = i + delta;
      if (i < 0 || j < 0 || j >= this.spaces.length) return;
      const [s] = this.spaces.splice(i, 1);
      this.spaces.splice(j, 0, s);
      this.saveSpaces();
      this.paint();
    },

    deleteSpace(sid) {
      if (this.spaces.length < 2) return;
      const s = this.space(sid);
      const tabs = this.tabsIn(sid);
      if (tabs.length) {
        const ok = Services.prompt.confirm(
          window, "Eliminar espacio",
          `Se cerrarán ${tabs.length} pestaña(s) de «${s.name}». ¿Continuar?`,
        );
        if (!ok) return;
      }
      const fallback = this.spaces.find(x => x.id != sid);
      if (this.activeId == sid) this.switchTo(fallback.id);
      gBrowser.removeTabs(tabs, { animate: false, suppressWarnAboutClosingWindow: true });
      this.spaces = this.spaces.filter(x => x.id != sid);
      this.saveSpaces();
      this.paint();
    },

    /** Cierra las pestañas normales del espacio activo (las fijadas se quedan), como el "Limpiar" de Zen. */
    clearSpace() {
      const tabs = this.tabsIn(this.activeId).filter(t => !t.pinned && !t.selected);
      gBrowser.removeTabs(tabs, { animate: false });
    },

    ask(title, text, value) {
      const out = { value };
      return Services.prompt.prompt(window, title, text, out, null, {}) ? out.value : null;
    },

    // ── Interfaz ──────────────────────────────────────────────────────

    buildUI() {
      const vt = document.getElementById("vertical-tabs");
      if (!vt) {
        console.warn("[Kaze] no hay pestañas verticales (sidebar.verticalTabs = false)");
        return;
      }

      // Cabecera del espacio: emoji + nombre + menú.
      // browser.xhtml es XML: se construye el DOM a mano (innerHTML no crea elementos HTML aquí).
      const el = (tag, cls, text, title) => {
        const e = document.createElementNS(HTML, tag);
        if (cls) e.className = cls;
        if (text) e.textContent = text;
        if (title) e.title = title;
        return e;
      };
      const header = el("div");
      header.id = "kaze-space-header";
      header.append(
        el("span", "kaze-emoji"),
        el("span", "kaze-name"),
        el("button", "kaze-clear", "Limpiar", "Cerrar las pestañas no fijadas de este espacio"),
        el("button", "kaze-more", "⋯", "Opciones del espacio"),
      );
      header.querySelector(".kaze-more").addEventListener("click", e => this.openSpaceMenu(e.currentTarget));
      header.querySelector(".kaze-name").addEventListener("dblclick", () => this.renameSpace(this.activeId));
      header.querySelector(".kaze-clear").addEventListener("click", () => this.clearSpace());

      // Selector de espacios abajo.
      const bar = document.createElementNS(HTML, "div");
      bar.id = "kaze-space-bar";
      bar.addEventListener("wheel", e => {
        if (Math.abs(e.deltaY) < 1) return;
        e.preventDefault();
        this.step(e.deltaY > 0 ? 1 : -1);
      }, { passive: false });

      // Arriba el nombre del espacio; debajo Essentials, las fijadas del espacio, una línea y las normales.
      vt.prepend(header);
      vt.append(bar);
      this.header = header;
      this.bar = bar;

      this.buildMenus();
      this.paint();
    },

    paint() {
      const s = this.space(this.activeId);
      if (!s) return;
      const root = document.documentElement;
      const c = s.colors;
      root.style.setProperty("--kaze-c1", c[0]);
      root.style.setProperty("--kaze-c2", c[1] || c[0]);
      root.style.setProperty("--kaze-c3", c[2] || c[1] || c[0]);
      root.setAttribute("kaze-space-colors", String(c.length));

      if (this.header) {
        this.header.querySelector(".kaze-emoji").textContent = s.emoji;
        this.header.querySelector(".kaze-name").textContent = s.name;
      }
      if (this.bar) {
        this.bar.replaceChildren();
        for (const sp of this.spaces) {
          const b = document.createElementNS(HTML, "button");
          b.className = "kaze-space-btn";
          b.textContent = sp.emoji;
          b.title = sp.name;
          b.toggleAttribute("selected", sp.id == this.activeId);
          b.addEventListener("click", () => this.switchTo(sp.id));
          b.addEventListener("contextmenu", e => {
            e.preventDefault();
            this.openSpaceMenu(b, sp.id);
          });
          this.bar.append(b);
        }
        const add = document.createElementNS(HTML, "button");
        add.className = "kaze-space-btn kaze-add";
        add.textContent = "+";
        add.title = "Nuevo espacio";
        add.addEventListener("click", () => this.addSpace());
        this.bar.append(add);
      }
    },

    buildMenus() {
      // Menú del espacio (⋯ y clic derecho en el selector).
      const popup = document.createXULElement("menupopup");
      popup.id = "kaze-space-menu";
      const item = (label, cmd) => {
        const m = document.createXULElement("menuitem");
        m.setAttribute("label", label);
        m.addEventListener("command", cmd);
        return m;
      };
      const submenu = (label, fill) => {
        const menu = document.createXULElement("menu");
        menu.setAttribute("label", label);
        const p = document.createXULElement("menupopup");
        fill(p);
        menu.append(p);
        return menu;
      };
      popup.addEventListener("popupshowing", e => {
        if (e.target != popup) return;
        const sid = popup._sid;
        popup.replaceChildren(
          item("Renombrar…", () => this.renameSpace(sid)),
          submenu("Ícono", p => EMOJIS.forEach(em => p.append(item(em, () => this.setSpaceEmoji(sid, em))))),
          submenu("Colores", p => PALETTE.forEach(pal => p.append(item(pal.name, () => this.setSpaceColors(sid, pal.colors))))),
          item("Mover a la izquierda", () => this.moveSpace(sid, -1)),
          item("Mover a la derecha", () => this.moveSpace(sid, +1)),
          document.createXULElement("menuseparator"),
          item("Nuevo espacio…", () => this.addSpace()),
          Object.assign(item("Eliminar espacio", () => this.deleteSpace(sid)), { disabled: this.spaces.length < 2 }),
        );
      });
      document.getElementById("mainPopupSet").append(popup);
      this.spaceMenu = popup;

      // Menú contextual de pestañas: Essentials y mover a otro espacio.
      const ctx = document.getElementById("tabContextMenu");
      if (!ctx) return;
      const sep = document.createXULElement("menuseparator");
      sep.id = "kaze-ctx-sep";
      const ess = item("Añadir a Essentials", () => {
        const tab = TabContextMenu.contextTab;
        this.setEssential(tab, !this.isEssential(tab));
      });
      ess.id = "kaze-ctx-essential";
      const move = document.createXULElement("menu");
      move.id = "kaze-ctx-move";
      move.setAttribute("label", "Mover a espacio");
      const movePopup = document.createXULElement("menupopup");
      move.append(movePopup);
      const pin = document.getElementById("context_pinTab");
      (pin || ctx.firstChild).before(sep, ess, move);

      ctx.addEventListener("popupshowing", e => {
        if (e.target != ctx) return;
        const tab = TabContextMenu.contextTab;
        ess.setAttribute("label", this.isEssential(tab) ? "Quitar de Essentials" : "Añadir a Essentials");
        movePopup.replaceChildren(
          ...this.spaces.filter(s => s.id != this.spaceOf(tab) || this.isEssential(tab)).map(s =>
            item(`${s.emoji}  ${s.name}`, () => {
              const targets = tab.multiselected ? gBrowser.selectedTabs : [tab];
              targets.forEach(t => this.moveTab(t, s.id));
            })),
        );
        move.disabled = movePopup.childElementCount == 0;
      });
    },

    openSpaceMenu(anchor, sid = this.activeId) {
      this.spaceMenu._sid = sid;
      this.spaceMenu.openPopup(anchor, "after_start");
    },

    // ── Modo compacto y página flotante ───────────────────────────────

    applyCompact() {
      document.documentElement.toggleAttribute("kaze-compact", Services.prefs.getBoolPref(PREF_COMPACT, false));
    },

    toggleCompact() {
      Services.prefs.setBoolPref(PREF_COMPACT, !Services.prefs.getBoolPref(PREF_COMPACT, false));
    },

    applyFloating() {
      document.documentElement.toggleAttribute("kaze-floating", Services.prefs.getBoolPref(PREF_FLOATING, true));
    },

    // ── Dormir pestañas sin usar ──────────────────────────────────────

    startUnloader() {
      clearInterval(this._unloadTimer);
      this._unloadTimer = setInterval(() => this.unloadIdle(), 60_000);
    },

    unloadIdle() {
      if (!Services.prefs.getBoolPref(PREF_UNLOAD, true)) return;
      const minutes = Services.prefs.getIntPref(PREF_UNLOAD_MIN, 20);
      if (minutes <= 0) return;
      const limit = Date.now() - minutes * 60_000;
      for (const tab of gBrowser.tabs) {
        if (tab.selected || tab.closing || !tab.linkedPanel || tab.soundPlaying || tab.pictureinpicture) continue;
        if (tab.lastAccessed > limit) continue;
        if (tab.linkedBrowser?._sharingState?.webRTC?.sharing) continue;
        gBrowser.discardBrowser(tab);
      }
    },

    // ── Eventos ───────────────────────────────────────────────────────

    listen() {
      const tc = gBrowser.tabContainer;
      tc.addEventListener("TabOpen", e => {
        const tab = e.target;
        // Las pestañas nuevas van al espacio activo (o al que se está abriendo). Se guarda un poco
        // después: si la pestaña viene de restaurar la sesión, SessionStore trae su espacio real.
        if (tab.hasAttribute("kaze-space")) return;
        tab.setAttribute("kaze-space", this._opening || this.activeId);
        tab._kazeSave = setTimeout(() => {
          if (!tab.closing && !SessionStore.getCustomTabValue(tab, "kaze-space")) {
            this.setTabSpace(tab, tab.getAttribute("kaze-space"));
          }
        }, 1500);
      });
      tc.addEventListener("SSTabRestoring", e => {
        clearTimeout(e.target._kazeSave);
        this.adoptTab(e.target);
        this.scheduleVisibility();
      });
      window.addEventListener("SSWindowRestored", () => {
        for (const tab of gBrowser.tabs) this.adoptTab(tab);
        const saved = SessionStore.getCustomWindowValue(window, "kaze-space");
        this.switchTo(this.space(saved) ? saved : this.activeId, { force: true });
      });
      tc.addEventListener("TabSelect", e => {
        const tab = e.target;
        // Seleccionar una pestaña de otro espacio (p. ej. desde la lista de pestañas) cambia de espacio.
        if (!this.isEssential(tab) && this.spaceOf(tab) && this.spaceOf(tab) != this.activeId) {
          this.switchTo(this.spaceOf(tab));
        }
      });
      tc.addEventListener("TabUnpinned", e => {
        const tab = e.target;
        if (tab.hasAttribute("kaze-essential")) {
          tab.removeAttribute("kaze-essential");
          SessionStore.deleteCustomTabValue(tab, "kaze-essential");
          this.setTabSpace(tab, this.activeId);
        }
      });

      // Atajos: Ctrl+Alt+1…9 espacio N · Ctrl+Alt+←/→ anterior/siguiente · Ctrl+Alt+C compacto.
      window.addEventListener("keydown", e => {
        if (!e.ctrlKey || !e.altKey || e.metaKey) return;
        if (/^Digit[1-9]$/.test(e.code)) {
          const s = this.spaces[Number(e.code.slice(5)) - 1];
          if (s) this.switchTo(s.id);
        } else if (e.code == "ArrowRight") this.step(1);
        else if (e.code == "ArrowLeft") this.step(-1);
        else if (e.code == "KeyC") this.toggleCompact();
        else return;
        e.preventDefault();
        e.stopPropagation();
      }, true);

      this._prefObserver = {
        observe: (subject, topic, data) => {
          if (data == PREF_COMPACT) this.applyCompact();
          else if (data == PREF_FLOATING) this.applyFloating();
          else if (data == PREF_SPACES && !this._saving) {
            // Otra ventana cambió los espacios.
            this.loadSpaces();
            if (!this.space(this.activeId)) this.switchTo(this.spaces[0].id, { force: true });
            this.paint();
          }
        },
      };
      Services.prefs.addObserver("kaze.", this._prefObserver);
      window.addEventListener("unload", () => {
        Services.prefs.removeObserver("kaze.", this._prefObserver);
        clearInterval(this._unloadTimer);
      }, { once: true });
    },

    scheduleVisibility() {
      if (this._visTimer) return;
      this._visTimer = setTimeout(() => {
        this._visTimer = null;
        this.applyVisibility();
      }, 50);
    },
  };

  // Evitar que el observador reaccione a nuestras propias escrituras.
  const save = KazeUI.saveSpaces;
  KazeUI.saveSpaces = function () {
    this._saving = true;
    try {
      save.call(this);
    } finally {
      this._saving = false;
    }
  };

  window.KazeUI = KazeUI;

  if (window.gBrowserInit?.delayedStartupFinished) {
    KazeUI.init();
  } else {
    const obs = subject => {
      if (subject != window) return;
      Services.obs.removeObserver(obs, "browser-delayed-startup-finished");
      KazeUI.init();
    };
    Services.obs.addObserver(obs, "browser-delayed-startup-finished");
  }
})();
