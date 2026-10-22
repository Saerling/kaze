/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

/*
 * Kaze — capa de interfaz sobre las pestañas verticales de Firefox.
 *
 * Las funciones de Zen, con la estética de Safari:
 *  - Espacios: cada pestaña pertenece a un espacio; solo se ven las del espacio activo.
 *  - Essentials: pestañas fijadas en cuadrícula, visibles en todos los espacios del perfil.
 *  - Perfiles: grupos de espacios con sus propias cookies y sesiones (un contenedor de Firefox).
 *  - Vista de pestañas en tarjetas (Ctrl+Mayús+\), como "Mostrar todas las pestañas" de Safari.
 *  - Menú aA: tamaño del texto, lector, traducir, buscar, lista de lectura, informe de privacidad.
 *  - Página de inicio propia (favoritos, frecuentes, lista de lectura, privacidad).
 *  - Modo compacto y pestañas que se duermen para ahorrar RAM.
 *  - Aviso de versión nueva desde GitHub (versión de Windows).
 *
 * Se carga en browser.xhtml (build de Kaze), con el instalador de Windows o con el
 * cargador de desarrollo (desktop/dev) sobre un Firefox normal. El código es el mismo.
 */

"use strict";

(() => {
  if (window.KazeUI) return;

  const imp = (...urls) => {
    for (const url of urls) {
      try {
        return ChromeUtils.importESModule(url);
      } catch {}
    }
    return {};
  };
  // browser.js ya define SessionStore en la ventana; por si acaso, se importa (la ruta cambió en Firefox 14x).
  const SessionStore = window.SessionStore ?? imp(
    "moz-src:///browser/components/sessionstore/SessionStore.sys.mjs",
    "resource:///modules/sessionstore/SessionStore.sys.mjs",
  ).SessionStore;
  const { ContextualIdentityService } = imp(
    "resource://gre/modules/ContextualIdentityService.sys.mjs",
    "moz-src:///toolkit/components/contextualidentity/ContextualIdentityService.sys.mjs",
  );
  const { AboutNewTab } = imp("resource:///modules/AboutNewTab.sys.mjs", "moz-src:///browser/modules/AboutNewTab.sys.mjs");
  const { Downloads } = imp("resource://gre/modules/Downloads.sys.mjs");

  const BASE = "chrome://kaze/content/";
  const START_URL = BASE + "inicio.html";

  const PREF_SPACES = "kaze.spaces.data";
  const PREF_PROFILES = "kaze.profiles.data";
  const PREF_COMPACT = "kaze.compact.enabled";
  const PREF_UNLOAD = "kaze.unloader.enabled";
  const PREF_UNLOAD_MIN = "kaze.unloader.minutes";
  const PREF_FLOATING = "kaze.theme.floating";
  const PREF_START = "kaze.startpage.enabled";
  const PREF_READING = "kaze.readinglist.guid";
  const PREF_BUILD = "kaze.build";
  const PREF_UPDATE_REPO = "kaze.update.repo";
  const PREF_UPDATE_LAST = "kaze.update.lastCheck";
  const PREF_UPDATE_SKIP = "kaze.update.skip";
  const HTML = "http://www.w3.org/1999/xhtml";
  const DEFAULT_PROFILE = "personal";

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
  const PROFILE_EMOJIS = ["🙂", "💼", "🎓", "🏠", "🎮", "🧑‍💻", "🌙", "⭐", "🦊", "🐱"];
  const CONTAINER_COLORS = ["blue", "turquoise", "green", "yellow", "orange", "red", "pink", "purple"];

  const uid = () => Services.uuid.generateUUID().toString().slice(1, 9);

  /** Crea elementos HTML (browser.xhtml es XML: innerHTML no sirve aquí). */
  const h = (tag, props = {}, ...children) => {
    const e = document.createElementNS(HTML, tag);
    for (const [k, v] of Object.entries(props)) {
      if (v == null || v === false) continue;
      if (k.startsWith("on")) e.addEventListener(k.slice(2), v);
      else if (k == "class") e.className = v;
      else if (k == "text") e.textContent = v;
      else e.setAttribute(k, v === true ? "" : v);
    }
    e.append(...children.flat().filter(c => c != null));
    return e;
  };

  const KazeUI = {
    spaces: [],
    profiles: [],
    activeId: null,
    thumbs: new WeakMap(),
    _applying: false,
    _unloadTimer: null,

    // ── Arranque ──────────────────────────────────────────────────────

    init() {
      if (this._ready) return;
      this._ready = true;
      document.documentElement.setAttribute("kaze", "true");
      this.loadProfiles();
      this.loadSpaces();
      this.activeId = SessionStore.getCustomWindowValue(window, "kaze-space") || this.spaces[0].id;
      if (!this.space(this.activeId)) this.activeId = this.spaces[0].id;

      for (const tab of gBrowser.tabs) this.adoptTab(tab);
      this.setupStartPage();
      this.routeNewTabs();
      this.buildUI();
      this.listen();
      this.applyCompact();
      this.applyFloating();
      this.switchTo(this.activeId, { force: true });
      this.startUnloader();
      setTimeout(() => this.checkForUpdate(), 15_000);
      console.info("[Kaze] interfaz lista");
    },

    // ── Perfiles: grupos de espacios con cookies propias ──────────────

    loadProfiles() {
      let data = [];
      try {
        data = JSON.parse(Services.prefs.getStringPref(PREF_PROFILES, "") || "[]");
      } catch {}
      if (!Array.isArray(data)) data = [];
      if (!data.some(p => p.id == DEFAULT_PROFILE)) {
        data.unshift({ id: DEFAULT_PROFILE, name: "Personal", emoji: "🙂", container: 0 });
      }
      this.profiles = data;
    },

    saveProfiles() {
      this._saving = true;
      try {
        Services.prefs.setStringPref(PREF_PROFILES, JSON.stringify(this.profiles));
      } finally {
        this._saving = false;
      }
    },

    profileById(id) {
      return this.profiles.find(p => p.id == id) || this.profiles[0];
    },

    get profile() {
      return this.profileById(this.space(this.activeId)?.profileId);
    },

    profileSpaces(pid = this.profile.id) {
      return this.spaces.filter(s => (s.profileId || DEFAULT_PROFILE) == pid);
    },

    /** El contenedor (userContextId) del perfil activo; 0 = el normal de Firefox. */
    container(profile = this.profile) {
      if (!profile.container || PrivateBrowsingUtils.isWindowPrivate(window)) return 0;
      if (!ContextualIdentityService?.getPublicIdentityFromId(profile.container)) {
        // Lo borraron desde los ajustes de Firefox: se crea otro.
        profile.container = this.createContainer(profile.name);
        this.saveProfiles();
      }
      return profile.container;
    },

    createContainer(name) {
      if (!ContextualIdentityService) return 0;
      Services.prefs.setBoolPref("privacy.userContext.enabled", true);
      const color = CONTAINER_COLORS[this.profiles.length % CONTAINER_COLORS.length];
      return ContextualIdentityService.create(`Kaze · ${name}`, "fingerprint", color).userContextId;
    },

    addProfile() {
      const name = this.ask("Nuevo perfil", "Cada perfil tiene sus propios espacios, cookies y sesiones.\nNombre:", "Trabajo");
      if (!name?.trim()) return;
      const used = new Set(this.profiles.map(p => p.emoji));
      const p = {
        id: uid(),
        name: name.trim(),
        emoji: PROFILE_EMOJIS.find(e => !used.has(e)) || "⭐",
        container: this.createContainer(name.trim()),
      };
      this.profiles.push(p);
      this.saveProfiles();
      const s = this.newSpaceData("General", p.id);
      this.spaces.push(s);
      this.saveSpaces();
      this.switchTo(s.id);
    },

    renameProfile(pid) {
      const p = this.profileById(pid);
      const name = this.ask("Renombrar perfil", "Nombre:", p.name);
      if (!name?.trim()) return;
      p.name = name.trim();
      if (p.container) ContextualIdentityService?.update(p.container, `Kaze · ${p.name}`, "fingerprint",
        ContextualIdentityService.getPublicIdentityFromId(p.container)?.color || "blue");
      this.saveProfiles();
      this.paint();
    },

    setProfileEmoji(pid, emoji) {
      this.profileById(pid).emoji = emoji;
      this.saveProfiles();
      this.paint();
    },

    deleteProfile(pid) {
      if (pid == DEFAULT_PROFILE) return;
      const p = this.profileById(pid);
      const spaces = this.profileSpaces(pid);
      const tabs = gBrowser.tabs.filter(t => spaces.some(s => s.id == this.spaceOf(t)) || this.essentialProfile(t) == pid);
      const ok = Services.prompt.confirm(window, "Eliminar perfil",
        `Se eliminará «${p.name}» con sus ${spaces.length} espacio(s), ${tabs.length} pestaña(s) y sus cookies. ¿Continuar?`);
      if (!ok) return;
      if (this.profile.id == pid) this.switchProfile(DEFAULT_PROFILE);
      gBrowser.removeTabs(tabs, { animate: false, suppressWarnAboutClosingWindow: true });
      this.spaces = this.spaces.filter(s => !spaces.includes(s));
      this.profiles = this.profiles.filter(x => x.id != pid);
      if (p.container) ContextualIdentityService?.remove(p.container);
      this.saveSpaces();
      this.saveProfiles();
      this.paint();
    },

    switchProfile(pid) {
      if (pid == this.profile.id) return;
      let spaces = this.profileSpaces(pid);
      if (!spaces.length) {
        const s = this.newSpaceData("General", pid);
        this.spaces.push(s);
        this.saveSpaces();
        spaces = [s];
      }
      const last = SessionStore.getCustomWindowValue(window, "kaze-space-" + pid);
      this.switchTo(spaces.find(s => s.id == last)?.id || spaces[0].id);
    },

    /** Las pestañas nuevas (Ctrl+T, el botón +, la barra de dirección) se abren en el contenedor del perfil. */
    routeNewTabs() {
      for (const name of ["openLinkIn", "openTrustedLinkIn"]) {
        const original = window[name];
        if (typeof original != "function") continue;
        window[name] = function (url, where, params = {}) {
          if ((where == "tab" || where == "tabshifted") && params?.userContextId == null) {
            const c = KazeUI.container();
            if (c) params = { ...params, userContextId: c };
          }
          return original.call(this, url, where, params);
        };
      }
    },

    // ── Datos de espacios (compartidos entre ventanas vía preferencia) ─

    newSpaceData(name, profileId = this.profile?.id || DEFAULT_PROFILE) {
      const used = new Set(this.spaces.map(s => s.emoji));
      return {
        id: uid(),
        name,
        emoji: EMOJIS.find(e => !used.has(e)) || "⭐",
        colors: PALETTE[this.spaces.length % PALETTE.length].colors,
        profileId,
      };
    },

    loadSpaces() {
      let data = [];
      try {
        data = JSON.parse(Services.prefs.getStringPref(PREF_SPACES, "") || "[]");
      } catch (e) {
        console.warn("[Kaze] espacios dañados, se crean de nuevo", e);
      }
      if (!Array.isArray(data) || !data.length) {
        data = [
          { id: uid(), name: "Personal", emoji: "🌿", colors: PALETTE[0].colors, profileId: DEFAULT_PROFILE },
          { id: uid(), name: "Trabajo", emoji: "💼", colors: PALETTE[3].colors, profileId: DEFAULT_PROFILE },
        ];
        this.spaces = data;
        this.saveSpaces();
      }
      for (const s of data) {
        if (!this.profiles.some(p => p.id == s.profileId)) s.profileId = DEFAULT_PROFILE;
      }
      this.spaces = data;
    },

    saveSpaces() {
      this._saving = true;
      try {
        Services.prefs.setStringPref(PREF_SPACES, JSON.stringify(this.spaces));
      } finally {
        this._saving = false;
      }
    },

    space(id) {
      return this.spaces.find(s => s.id == id);
    },

    // ── Pestañas ──────────────────────────────────────────────────────

    isEssential(tab) {
      return tab.pinned && tab.hasAttribute("kaze-essential");
    },

    /** Perfil al que pertenece una pestaña Essential (las de versiones viejas guardaban "1"). */
    essentialProfile(tab) {
      if (!this.isEssential(tab)) return null;
      const v = tab.getAttribute("kaze-essential");
      return this.profiles.some(p => p.id == v) ? v : DEFAULT_PROFILE;
    },

    /** SessionStore manda: el atributo es solo una copia para el CSS (al restaurar puede llegar tarde). */
    spaceOf(tab) {
      const saved = SessionStore.getCustomTabValue(tab, "kaze-space");
      if (saved && saved != tab.getAttribute("kaze-space")) tab.setAttribute("kaze-space", saved);
      return saved || tab.getAttribute("kaze-space");
    },

    /** Lee de SessionStore el espacio de una pestaña. Si no tiene, se asume el activo (sin guardarlo aún). */
    adoptTab(tab) {
      const essential = SessionStore.getCustomTabValue(tab, "kaze-essential");
      if (essential && tab.pinned) tab.setAttribute("kaze-essential", essential == "1" ? DEFAULT_PROFILE : essential);
      else tab.removeAttribute("kaze-essential");
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
        const pid = this.profile.id;
        tab.setAttribute("kaze-essential", pid);
        SessionStore.setCustomTabValue(tab, "kaze-essential", pid);
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

    essentials() {
      const pid = this.profile.id;
      return gBrowser.tabs.filter(t => this.essentialProfile(t) == pid);
    },

    visible(tab) {
      return this.isEssential(tab) ? this.essentialProfile(tab) == this.profile.id : this.spaceOf(tab) == this.activeId;
    },

    /** Muestra las pestañas del espacio activo y las Essentials del perfil; esconde el resto. */
    applyVisibility() {
      if (this._applying) return;
      this._applying = true;
      try {
        for (const tab of gBrowser.tabs) {
          if (tab.closing) continue;
          const show = this.visible(tab);
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
      SessionStore.setCustomWindowValue(window, "kaze-space-" + (target.profileId || DEFAULT_PROFILE), sid);

      // Primero elegir una pestaña visible del nuevo espacio (la seleccionada no se puede ocultar).
      const sel = gBrowser.selectedTab;
      if (!this.visible(sel)) {
        const candidates = this.tabsIn(sid).sort((a, b) => b.lastAccessed - a.lastAccessed);
        if (candidates.length) {
          gBrowser.selectedTab = candidates[0];
        } else {
          this._opening = sid;
          gBrowser.selectedTab = gBrowser.addTrustedTab(BROWSER_NEW_TAB_URL, {
            skipAnimation: true,
            userContextId: this.container(),
          });
          this._opening = null;
        }
      }
      this.applyVisibility();
      this.paint();
      if (this.overview?.isConnected) this.renderOverview();
    },

    step(delta) {
      const list = this.profileSpaces();
      const i = list.findIndex(s => s.id == this.activeId);
      const next = list[(i + delta + list.length) % list.length];
      this.switchTo(next.id);
    },

    // ── Crear / editar espacios ───────────────────────────────────────

    addSpace() {
      const name = this.ask("Nuevo espacio", "Nombre del espacio:", "Espacio " + (this.profileSpaces().length + 1));
      if (name == null) return;
      const s = this.newSpaceData(name.trim() || "Espacio");
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
      const list = this.profileSpaces();
      const i = list.findIndex(s => s.id == sid);
      const other = list[i + delta];
      if (i < 0 || !other) return;
      const a = this.spaces.indexOf(list[i]);
      const b = this.spaces.indexOf(other);
      [this.spaces[a], this.spaces[b]] = [this.spaces[b], this.spaces[a]];
      this.saveSpaces();
      this.paint();
    },

    deleteSpace(sid) {
      const list = this.profileSpaces();
      if (list.length < 2) return;
      const s = this.space(sid);
      const tabs = this.tabsIn(sid);
      if (tabs.length) {
        const ok = Services.prompt.confirm(
          window, "Eliminar espacio",
          `Se cerrarán ${tabs.length} pestaña(s) de «${s.name}». ¿Continuar?`,
        );
        if (!ok) return;
      }
      const fallback = list.find(x => x.id != sid);
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
      this.buildToolbar();
      this.buildMenus();
      const vt = document.getElementById("vertical-tabs");
      if (!vt) {
        console.warn("[Kaze] no hay pestañas verticales (sidebar.verticalTabs = false)");
        return;
      }

      // Cabecera del grupo, como la barra lateral de Safari: emoji, nombre, número de pestañas.
      const header = h("div", { id: "kaze-space-header" },
        h("span", { class: "kaze-emoji" }),
        h("span", { class: "kaze-name", ondblclick: () => this.renameSpace(this.activeId) }),
        h("span", { class: "kaze-count" }),
        h("button", { class: "kaze-clear", title: "Cerrar las pestañas no fijadas de este espacio", text: "Limpiar", onclick: () => this.clearSpace() }),
        h("button", { class: "kaze-more", title: "Opciones del espacio", text: "⋯", onclick: e => this.openSpaceMenu(e.currentTarget) }),
      );

      // Abajo: perfil y selector de espacios.
      const bar = h("div", { id: "kaze-space-bar" });
      bar.addEventListener("wheel", e => {
        if (Math.abs(e.deltaY) < 1) return;
        e.preventDefault();
        this.step(e.deltaY > 0 ? 1 : -1);
      }, { passive: false });
      const profileBtn = h("button", {
        id: "kaze-profile-btn",
        title: "Perfil",
        onclick: e => this.openProfileMenu(e.currentTarget),
      }, h("span", { class: "kaze-emoji" }), h("span", { class: "kaze-name" }));
      const footer = h("div", { id: "kaze-sidebar-footer" }, profileBtn, bar);

      vt.prepend(header);
      vt.append(footer);
      this.header = header;
      this.bar = bar;
      this.profileBtn = profileBtn;
      this.paint();
    },

    /** Botones de la barra de herramientas: aA dentro de la barra de dirección y la vista de pestañas. */
    buildToolbar() {
      const aa = document.createXULElement("image");
      aa.id = "kaze-aa-button";
      aa.className = "urlbar-icon urlbar-page-action";
      aa.setAttribute("role", "button");
      aa.setAttribute("tooltiptext", "Opciones de la página (aA)");
      aa.addEventListener("click", e => {
        if (e.button == 0) this.openPageMenu(e.currentTarget);
      });
      const actions = document.getElementById("page-action-buttons");
      actions?.prepend(aa);

      const CUI = window.CustomizableUI;
      if (CUI && CUI.getWidget("kaze-overview-button")?.provider != CUI.PROVIDER_API) {
        try {
          CUI.createWidget({
            id: "kaze-overview-button",
            type: "button",
            label: "Todas las pestañas",
            tooltiptext: "Mostrar todas las pestañas (Ctrl+Mayús+\\)",
            defaultArea: CUI.AREA_NAVBAR,
            onCommand: e => e.target.ownerGlobal.KazeUI?.toggleOverview(),
          });
        } catch {
          // Ya existe (otra ventana lo creó).
        }
      }
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
        const n = this.tabsIn(s.id).length;
        this.header.querySelector(".kaze-count").textContent = n ? String(n) : "";
      }
      if (this.profileBtn) {
        const p = this.profile;
        this.profileBtn.querySelector(".kaze-emoji").textContent = p.emoji;
        this.profileBtn.querySelector(".kaze-name").textContent = p.name;
        this.profileBtn.title = `Perfil: ${p.name}`;
        this.profileBtn.hidden = this.profiles.length < 2;
      }
      if (this.bar) {
        this.bar.replaceChildren(
          ...this.profileSpaces().map(sp => h("button", {
            class: "kaze-space-btn",
            text: sp.emoji,
            title: sp.name,
            selected: sp.id == this.activeId,
            onclick: () => this.switchTo(sp.id),
            oncontextmenu: e => {
              e.preventDefault();
              this.openSpaceMenu(e.currentTarget, sp.id);
            },
          })),
          h("button", { class: "kaze-space-btn kaze-add", text: "+", title: "Nuevo espacio", onclick: () => this.addSpace() }),
        );
      }
    },

    menuItem(label, cmd, { disabled = false, checked = null } = {}) {
      const m = document.createXULElement("menuitem");
      m.setAttribute("label", label);
      if (checked != null) {
        m.setAttribute("type", "checkbox");
        if (checked) m.setAttribute("checked", "true");
      }
      if (disabled) m.setAttribute("disabled", "true");
      m.addEventListener("command", cmd);
      return m;
    },

    submenu(label, fill) {
      const menu = document.createXULElement("menu");
      menu.setAttribute("label", label);
      const p = document.createXULElement("menupopup");
      fill(p);
      menu.append(p);
      return menu;
    },

    buildMenus() {
      const item = this.menuItem.bind(this);
      const sep = () => document.createXULElement("menuseparator");

      // Menú del espacio (⋯ y clic derecho en el selector).
      const popup = document.createXULElement("menupopup");
      popup.id = "kaze-space-menu";
      popup.addEventListener("popupshowing", e => {
        if (e.target != popup) return;
        const sid = popup._sid;
        popup.replaceChildren(
          item("Renombrar…", () => this.renameSpace(sid)),
          this.submenu("Ícono", p => EMOJIS.forEach(em => p.append(item(em, () => this.setSpaceEmoji(sid, em))))),
          this.submenu("Colores", p => PALETTE.forEach(pal => p.append(item(pal.name, () => this.setSpaceColors(sid, pal.colors))))),
          item("Mover a la izquierda", () => this.moveSpace(sid, -1)),
          item("Mover a la derecha", () => this.moveSpace(sid, +1)),
          sep(),
          item("Nuevo espacio…", () => this.addSpace()),
          item("Eliminar espacio", () => this.deleteSpace(sid), { disabled: this.profileSpaces().length < 2 }),
        );
      });

      // Menú del perfil.
      const profiles = document.createXULElement("menupopup");
      profiles.id = "kaze-profile-menu";
      profiles.addEventListener("popupshowing", e => {
        if (e.target != profiles) return;
        const cur = this.profile;
        profiles.replaceChildren(
          ...this.profiles.map(p => item(`${p.emoji}  ${p.name}`, () => this.switchProfile(p.id), { checked: p.id == cur.id })),
          sep(),
          item("Nuevo perfil…", () => this.addProfile()),
          item(`Renombrar «${cur.name}»…`, () => this.renameProfile(cur.id)),
          this.submenu("Ícono del perfil", p => PROFILE_EMOJIS.forEach(em => p.append(item(em, () => this.setProfileEmoji(cur.id, em))))),
          item(`Eliminar «${cur.name}»`, () => this.deleteProfile(cur.id), { disabled: cur.id == DEFAULT_PROFILE }),
        );
      });

      document.getElementById("mainPopupSet").append(popup, profiles);
      this.spaceMenu = popup;
      this.profileMenu = profiles;

      // Menú contextual de pestañas: Essentials y mover a otro espacio.
      const ctx = document.getElementById("tabContextMenu");
      if (!ctx) return;
      const ctxSep = sep();
      ctxSep.id = "kaze-ctx-sep";
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
      (pin || ctx.firstChild).before(ctxSep, ess, move);

      ctx.addEventListener("popupshowing", e => {
        if (e.target != ctx) return;
        const tab = TabContextMenu.contextTab;
        ess.setAttribute("label", this.isEssential(tab) ? "Quitar de Essentials" : "Añadir a Essentials");
        movePopup.replaceChildren(
          ...this.profileSpaces().filter(s => s.id != this.spaceOf(tab) || this.isEssential(tab)).map(s =>
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

    openProfileMenu(anchor) {
      this.profileMenu.openPopup(anchor, "before_start");
    },

    // ── Menú aA (el de Safari) ────────────────────────────────────────

    openPageMenu(anchor) {
      if (!this.pagePanel) {
        const panel = document.createXULElement("panel");
        panel.id = "kaze-page-panel";
        panel.setAttribute("type", "arrow");
        panel.setAttribute("role", "menu");
        const body = h("div", { class: "kaze-page-menu" });
        panel.append(body);
        document.getElementById("mainPopupSet").append(panel);
        this.pagePanel = panel;
      }
      this.renderPageMenu();
      this.pagePanel.openPopup(anchor, "bottomright topright", 0, 4);
    },

    async renderPageMenu() {
      const body = this.pagePanel.querySelector(".kaze-page-menu");
      const browser = gBrowser.selectedBrowser;
      const url = browser.currentURI?.spec || "";
      const web = /^https?:/.test(url);
      const inReader = url.startsWith("about:reader");
      const zoom = Math.round(ZoomManager.getZoomForBrowser(browser) * 100);
      const close = () => this.pagePanel.hidePopup();
      const row = (icon, label, action, { disabled = false, hint = null } = {}) => h("button", {
        class: "kaze-row",
        disabled,
        onclick: e => {
          close();
          action(e);
        },
      }, h("span", { class: "kaze-row-icon", text: icon }), h("span", { class: "kaze-row-label", text: label }),
      hint ? h("span", { class: "kaze-row-hint", text: hint }) : null);

      const zoomLabel = h("button", {
        class: "kaze-zoom-value",
        text: `${zoom} %`,
        title: "Tamaño normal",
        onclick: () => {
          FullZoom.reset();
          this.renderPageMenu();
        },
      });
      const reading = web ? await this.readingListHas(url) : false;
      const translatable = !document.getElementById("translations-button")?.hidden;

      body.replaceChildren(...[
        h("div", { class: "kaze-zoom" },
          h("button", { class: "kaze-zoom-small", text: "A", title: "Texto más pequeño", onclick: () => {
            FullZoom.reduce();
            this.renderPageMenu();
          } }),
          zoomLabel,
          h("button", { class: "kaze-zoom-big", text: "A", title: "Texto más grande", onclick: () => {
            FullZoom.enlarge();
            this.renderPageMenu();
          } }),
        ),
        h("div", { class: "kaze-sep" }),
        row("📖", inReader ? "Ocultar Lector" : "Mostrar Lector", e => AboutReaderParent.toggleReaderMode(e),
          { disabled: !(inReader || browser.isArticle), hint: "Ctrl+Alt+R" }),
        row("🌐", "Traducir página…", e => FullPageTranslationsPanel.open(e), { disabled: !translatable || !web }),
        row("🔍", "Buscar en la página…", () => gLazyFindCommand("onFindCommand"), { hint: "Ctrl+F" }),
        h("div", { class: "kaze-sep" }),
        row("👓", reading ? "Quitar de la lista de lectura" : "Añadir a la lista de lectura",
          () => this.toggleReadingList(browser), { disabled: !web }),
        row("⭐", "Añadir a Favoritos", () => this.addFavorite(browser), { disabled: !web }),
        row("🔗", "Copiar enlace", () => {
          Cc["@mozilla.org/widget/clipboardhelper;1"].getService(Ci.nsIClipboardHelper).copyString(url);
        }, { disabled: !web }),
        AppConstants.platform == "win"
          ? row("📤", "Compartir…", () => {
            Cc["@mozilla.org/windows-ui-utils;1"].getService(Ci.nsIWindowsUIUtils)
              .shareUrl(url, browser.contentTitle || url);
          }, { disabled: !web })
          : null,
        h("div", { class: "kaze-sep" }),
        row("🛡️", "Informe de privacidad", e => gProtectionsHandler.showProtectionsPopup({ event: e }), { disabled: !web }),
        row(Services.prefs.getBoolPref(PREF_COMPACT, false) ? "▣" : "▢",
          Services.prefs.getBoolPref(PREF_COMPACT, false) ? "Mostrar barras" : "Ocultar barras",
          () => this.toggleCompact(), { hint: "Ctrl+Alt+C" }),
        row("▦", "Todas las pestañas", () => this.toggleOverview(), { hint: "Ctrl+Mayús+\\" }),
      ].filter(Boolean));
    },

    // ── Lista de lectura y favoritos (en los marcadores de Firefox) ───

    async readingListFolder() {
      const { PlacesUtils } = window;
      let guid = Services.prefs.getStringPref(PREF_READING, "");
      if (guid && await PlacesUtils.bookmarks.fetch(guid)) return guid;
      const folder = await PlacesUtils.bookmarks.insert({
        parentGuid: PlacesUtils.bookmarks.menuGuid,
        type: PlacesUtils.bookmarks.TYPE_FOLDER,
        title: "Lista de lectura",
      });
      Services.prefs.setStringPref(PREF_READING, folder.guid);
      return folder.guid;
    },

    async readingListHas(url) {
      const guid = Services.prefs.getStringPref(PREF_READING, "");
      if (!guid) return false;
      let found = false;
      await PlacesUtils.bookmarks.fetch({ url }, b => {
        if (b.parentGuid == guid) found = true;
      });
      return found;
    },

    async toggleReadingList(browser) {
      const url = browser.currentURI.spec;
      const folder = await this.readingListFolder();
      const items = [];
      await PlacesUtils.bookmarks.fetch({ url }, b => {
        if (b.parentGuid == folder) items.push(b.guid);
      });
      if (items.length) {
        await PlacesUtils.bookmarks.remove(items);
        this.toast("Quitado de la lista de lectura");
      } else {
        await PlacesUtils.bookmarks.insert({ parentGuid: folder, url, title: browser.contentTitle || url, index: 0 });
        this.toast("Añadido a la lista de lectura");
      }
    },

    async addFavorite(browser) {
      const url = browser.currentURI.spec;
      let exists = false;
      await PlacesUtils.bookmarks.fetch({ url }, b => {
        if (b.parentGuid == PlacesUtils.bookmarks.toolbarGuid) exists = true;
      });
      if (!exists) {
        await PlacesUtils.bookmarks.insert({
          parentGuid: PlacesUtils.bookmarks.toolbarGuid,
          url,
          title: browser.contentTitle || url,
        });
      }
      this.toast("En Favoritos");
    },

    toast(text) {
      this._toast?.remove();
      const t = h("div", { id: "kaze-toast", text });
      document.body.append(t);
      this._toast = t;
      setTimeout(() => t.remove(), 1800);
    },

    // ── Vista de pestañas en tarjetas ─────────────────────────────────

    /** Miniatura pequeña (320×200, JPEG) de una pestaña cargada. Las dormidas no se tocan. */
    async capture(tab) {
      try {
        if (!tab || tab.closing || !tab.linkedPanel || tab.hasAttribute("pending")) return;
        const browser = tab.linkedBrowser;
        if (!browser?.browsingContext?.currentWindowGlobal || !window.PageThumbs) return;
        const canvas = h("canvas", { width: 320, height: 200 });
        if ((await PageThumbs.captureTabPreviewThumbnail(browser, canvas)) === false) return;
        const blob = await new Promise(r => canvas.toBlob(r, "image/jpeg", 0.72));
        if (!blob || tab.closing) return;
        const old = this.thumbs.get(tab);
        if (old) URL.revokeObjectURL(old);
        this.thumbs.set(tab, URL.createObjectURL(blob));
      } catch {
        // Páginas internas o que se cerraron a mitad: se quedan con su ícono.
      }
    },

    toggleOverview() {
      if (this.overview?.isConnected) this.closeOverview();
      else this.openOverview();
    },

    async openOverview() {
      await this.capture(gBrowser.selectedTab);
      this.overviewQuery = "";
      this.overview = h("div", { id: "kaze-overview", tabindex: "-1", onclick: e => {
        if (e.target == this.overview) this.closeOverview();
      } });
      this.overview.addEventListener("keydown", e => {
        if (e.key == "Escape") {
          e.preventDefault();
          this.closeOverview();
        }
      });
      document.body.append(this.overview);
      this.renderOverview();
      this.overview.querySelector("input")?.focus();
    },

    closeOverview() {
      this.overview?.remove();
      this.overview = null;
      gBrowser.selectedBrowser.focus();
    },

    renderOverview() {
      const o = this.overview;
      const q = (this.overviewQuery || "").toLowerCase();
      const spaces = this.profileSpaces();
      const match = t => !q || t.label.toLowerCase().includes(q) ||
        (t.linkedBrowser?.currentURI?.spec || "").toLowerCase().includes(q);
      // Al buscar se ve todo el perfil; si no, solo el espacio activo (como un grupo de Safari).
      const tabs = q
        ? gBrowser.tabs.filter(t => !t.closing && (spaces.some(s => s.id == this.spaceOf(t)) || this.essentialProfile(t) == this.profile.id) && match(t))
        : [...this.essentials(), ...this.tabsIn(this.activeId)];

      const card = tab => {
        const thumb = this.thumbs.get(tab);
        const icon = tab.getAttribute("image");
        return h("div", {
          class: "kaze-card",
          selected: tab.selected,
          title: tab.label,
          onclick: () => {
            gBrowser.selectedTab = tab;
            this.closeOverview();
          },
        },
        h("div", { class: "kaze-card-thumb" },
          thumb ? h("img", { src: thumb, alt: "" }) : h("img", { class: "kaze-card-icon", src: icon || "chrome://global/skin/icons/defaultFavicon.svg", alt: "" }),
          this.isEssential(tab) ? h("span", { class: "kaze-card-badge", text: "Essential" }) : null,
        ),
        h("div", { class: "kaze-card-title" },
          icon ? h("img", { src: icon, alt: "" }) : null,
          h("span", { text: tab.label }),
          h("button", {
            class: "kaze-card-close",
            title: "Cerrar pestaña",
            text: "×",
            onclick: e => {
              e.stopPropagation();
              gBrowser.removeTab(tab, { animate: false });
              setTimeout(() => this.overview && this.renderOverview(), 50);
            },
          }),
        ));
      };

      const search = h("input", {
        type: "search",
        placeholder: "Buscar pestañas",
        value: this.overviewQuery || "",
      });
      search.addEventListener("input", () => {
        this.overviewQuery = search.value;
        const pos = search.selectionStart;
        this.renderOverview();
        const ns = this.overview.querySelector("input");
        ns.focus();
        ns.setSelectionRange(pos, pos);
      });

      o.replaceChildren(h("div", { class: "kaze-overview-panel" },
        h("div", { class: "kaze-overview-top" },
          h("div", { class: "kaze-overview-spaces" },
            ...spaces.map(s => h("button", {
              class: "kaze-chip",
              selected: s.id == this.activeId && !q,
              onclick: () => {
                this.overviewQuery = "";
                this.switchTo(s.id);
              },
            }, h("span", { text: s.emoji }), h("span", { text: s.name }),
            h("span", { class: "kaze-chip-count", text: String(this.tabsIn(s.id).length) }))),
          ),
          search,
          h("button", { class: "kaze-overview-new", title: "Pestaña nueva", text: "+", onclick: () => {
            this.closeOverview();
            BrowserCommands.openTab();
          } }),
          h("button", { class: "kaze-overview-done", text: "Listo", onclick: () => this.closeOverview() }),
        ),
        tabs.length
          ? h("div", { class: "kaze-overview-grid" }, ...tabs.map(card))
          : h("div", { class: "kaze-overview-empty", text: q ? "Ninguna pestaña coincide." : "No hay pestañas en este espacio." }),
      ));
    },

    // ── Página de inicio ──────────────────────────────────────────────

    setupStartPage() {
      if (!Services.prefs.getBoolPref(PREF_START, true)) return;
      try {
        if (AboutNewTab && AboutNewTab.newTabURL != START_URL) AboutNewTab.newTabURL = START_URL;
      } catch (e) {
        console.warn("[Kaze] no se pudo poner la página de inicio", e);
      }
      // Que la barra de dirección quede vacía en la página de inicio, como con about:newtab.
      if (!gInitialPages.includes(START_URL)) gInitialPages.push(START_URL);
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

    // ── Actualizaciones (versión de Windows, desde GitHub Releases) ───

    /**
     * Las versiones de Windows se publican como "windows-<número>" con un instalador
     * "Kaze-…-setup.exe". El número de esta copia está en kaze.build (0 en desarrollo).
     */
    async checkForUpdate({ manual = false } = {}) {
      const build = Services.prefs.getIntPref(PREF_BUILD, 0);
      if (!build || AppConstants.platform != "win") return null;
      // Una sola ventana pregunta, y como mucho cada 6 horas.
      const now = Math.floor(Date.now() / 1000);
      if (!manual) {
        const top = window.BrowserWindowTracker?.getTopWindow?.();
        if (top && top != window) return null;
        if (now - Services.prefs.getIntPref(PREF_UPDATE_LAST, 0) < 6 * 3600) return null;
      }
      Services.prefs.setIntPref(PREF_UPDATE_LAST, now);
      try {
        const repo = Services.prefs.getStringPref(PREF_UPDATE_REPO, "Saerling/kaze");
        const res = await fetch(`https://api.github.com/repos/${repo}/releases?per_page=30`, {
          headers: { Accept: "application/vnd.github+json" },
        });
        if (!res.ok) throw new Error("GitHub respondió " + res.status);
        const latest = this.pickUpdate(await res.json(), build);
        if (!latest) return null;
        if (!manual && Services.prefs.getIntPref(PREF_UPDATE_SKIP, 0) == latest.number) return null;
        this.offerUpdate(latest.number, latest.name, latest.asset);
        return latest.number;
      } catch (e) {
        console.warn("[Kaze] no se pudo buscar actualizaciones", e);
        return null;
      }
    },

    /** De la lista de Releases de GitHub, la versión de Windows más nueva que esta (o null). */
    pickUpdate(releases, build) {
      const latest = (Array.isArray(releases) ? releases : [])
        .filter(r => !r.draft && !r.prerelease && /^windows-\d+$/.test(r.tag_name))
        .map(r => ({
          number: Number(r.tag_name.slice(8)),
          name: r.name || r.tag_name,
          asset: (r.assets || []).find(x => /setup\.exe$/i.test(x.name)),
        }))
        .filter(r => r.asset)
        .sort((a, b) => b.number - a.number)[0];
      return latest && latest.number > build ? latest : null;
    },

    async offerUpdate(number, name, asset) {
      const box = gNotificationBox;
      box.getNotificationWithValue("kaze-update")?.close();
      const mb = (asset.size / 1048576).toFixed(0);
      await box.appendNotification("kaze-update", {
        label: `Hay una nueva versión: ${name} (${mb} MB). Se instala encima, sin perder nada.`,
        priority: box.PRIORITY_INFO_HIGH,
      }, [
        {
          label: "Actualizar",
          accessKey: "A",
          callback: () => {
            this.installUpdate(asset);
            return true;
          },
        },
        {
          label: "Ahora no",
          accessKey: "N",
          callback: () => {
            Services.prefs.setIntPref(PREF_UPDATE_SKIP, number);
          },
        },
      ]);
    },

    async installUpdate(asset) {
      const box = gNotificationBox;
      const show = (label, buttons = []) => {
        box.getNotificationWithValue("kaze-update")?.close();
        return box.appendNotification("kaze-update", { label, priority: box.PRIORITY_INFO_HIGH }, buttons);
      };
      try {
        const target = PathUtils.join(PathUtils.tempDir, asset.name);
        await show(`Descargando la actualización (${(asset.size / 1048576).toFixed(0)} MB)…`);
        const dl = await Downloads.createDownload({ source: asset.browser_download_url, target });
        dl.onchange = () => {
          const n = box.getNotificationWithValue("kaze-update");
          if (n && dl.hasProgress) n.label = `Descargando la actualización… ${dl.progress} %`;
        };
        await dl.start();
        const info = await IOUtils.stat(target);
        if (asset.size && info.size != asset.size) throw new Error("la descarga quedó incompleta");

        await show("Actualización lista. Kaze se cerrará un momento para instalarla y se abrirá de nuevo.", [
          {
            label: "Reiniciar y actualizar",
            accessKey: "R",
            callback: () => {
              const exe = Cc["@mozilla.org/file/local;1"].createInstance(Ci.nsIFile);
              exe.initWithPath(target);
              const proc = Cc["@mozilla.org/process/util;1"].createInstance(Ci.nsIProcess);
              proc.init(exe);
              // /S = sin preguntas; el instalador espera a que Kaze se cierre y lo vuelve a abrir.
              proc.runwAsync(["/S", "/actualizar"], 2);
              Services.startup.quit(Ci.nsIAppStartup.eAttemptQuit);
              return false;
            },
          },
        ]);
      } catch (e) {
        console.error("[Kaze] falló la actualización", e);
        await show("No se pudo descargar la actualización: " + e.message, [
          { label: "Reintentar", callback: () => this.installUpdate(asset) },
        ]);
      }
    },

    // ── Eventos ───────────────────────────────────────────────────────

    listen() {
      const tc = gBrowser.tabContainer;
      tc.addEventListener("TabOpen", e => {
        const tab = e.target;
        this.scheduleRepaint();
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
      tc.addEventListener("TabClose", e => {
        const url = this.thumbs.get(e.target);
        if (url) URL.revokeObjectURL(url);
        this.thumbs.delete(e.target);
        this.scheduleRepaint();
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
        // La miniatura de la pestaña que se deja, para la vista de tarjetas.
        const prev = e.detail?.previousTab;
        if (prev && !prev.closing) this.capture(prev);
        // Seleccionar una pestaña de otro espacio (p. ej. desde la lista de pestañas) cambia de espacio.
        if (this.isEssential(tab)) {
          if (this.essentialProfile(tab) != this.profile.id) this.switchProfile(this.essentialProfile(tab));
        } else if (this.spaceOf(tab) && this.spaceOf(tab) != this.activeId && this.space(this.spaceOf(tab))) {
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

      // Atajos: Ctrl+Alt+1…9 espacio N · Ctrl+Alt+←/→ anterior/siguiente · Ctrl+Alt+C compacto ·
      // Ctrl+Mayús+\ todas las pestañas (como Safari).
      window.addEventListener("keydown", e => {
        if (e.ctrlKey && e.shiftKey && !e.altKey && e.code == "Backslash") {
          this.toggleOverview();
        } else if (e.ctrlKey && e.altKey && !e.metaKey) {
          if (/^Digit[1-9]$/.test(e.code)) {
            const s = this.profileSpaces()[Number(e.code.slice(5)) - 1];
            if (s) this.switchTo(s.id);
          } else if (e.code == "ArrowRight") this.step(1);
          else if (e.code == "ArrowLeft") this.step(-1);
          else if (e.code == "KeyC") this.toggleCompact();
          else return;
        } else {
          return;
        }
        e.preventDefault();
        e.stopPropagation();
      }, true);

      this._prefObserver = {
        observe: (subject, topic, data) => {
          if (data == PREF_COMPACT) this.applyCompact();
          else if (data == PREF_FLOATING) this.applyFloating();
          else if ((data == PREF_SPACES || data == PREF_PROFILES) && !this._saving) {
            // Otra ventana cambió los espacios o los perfiles.
            this.loadProfiles();
            this.loadSpaces();
            if (!this.space(this.activeId)) this.switchTo(this.spaces[0].id, { force: true });
            this.applyVisibility();
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

    scheduleRepaint() {
      if (this._paintTimer) return;
      this._paintTimer = setTimeout(() => {
        this._paintTimer = null;
        this.paint();
      }, 100);
    },
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
