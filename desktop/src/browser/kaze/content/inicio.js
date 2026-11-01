/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

/*
 * Página de inicio de Fyx, como la de Safari 27: saludo, favoritos (la barra de marcadores),
 * los sitios más visitados, el informe de privacidad, "Reanudar navegación" (pestañas cerradas
 * hace poco y las abiertas en otros dispositivos con Firefox Sync) y la lista de lectura.
 * Es una página interna: lee los datos de Firefox directamente y no carga nada de la red.
 * Todo lo que viene de páginas (títulos, direcciones) se pone con textContent.
 */

"use strict";

const { PlacesUtils } = ChromeUtils.importESModule("resource://gre/modules/PlacesUtils.sys.mjs");
const win = window.browsingContext.topChromeWindow;

const $ = s => document.querySelector(s);
const el = (tag, cls, text) => {
  const e = document.createElement(tag);
  if (cls) e.className = cls;
  if (text != null) e.textContent = text;
  return e;
};

const host = url => {
  try {
    return new URL(url).hostname.replace(/^www\./, "");
  } catch {
    return url;
  }
};
const web = url => /^https?:/.test(url || "");

/** Abre en esta pestaña, o en una nueva con clic central / Ctrl. */
function link(a, url) {
  a.href = url;
  a.addEventListener("click", e => {
    if (e.button > 1 || e.defaultPrevented) return;
    e.preventDefault();
    const tab = e.button == 1 || e.ctrlKey || e.metaKey;
    win.openTrustedLinkIn(url, tab ? "tab" : "current");
  });
  a.addEventListener("auxclick", e => {
    if (e.button != 1) return;
    e.preventDefault();
    win.openTrustedLinkIn(url, "tab");
  });
}

/** Ícono del sitio; sin ícono guardado, la inicial (como Safari) en vez del globo genérico. */
function siteIcon(url, size, cls = "icono") {
  const icon = el("span", cls);
  const letter = () => {
    icon.replaceChildren();
    icon.textContent = (host(url)[0] || "?").toUpperCase();
    icon.classList.add("letra");
    // Un color por sitio (siempre el mismo), como los mosaicos de Safari sin ícono.
    let n = 0;
    for (const c of host(url)) n = (n * 31 + c.charCodeAt(0)) % 360;
    icon.style.setProperty("--h", n);
  };
  const img = el("img");
  img.alt = "";
  img.onerror = letter;
  icon.append(img);
  PlacesUtils.favicons.getFaviconForPage(Services.io.newURI(url), size)
    .then(f => (f ? (img.src = "page-icon:" + url) : letter()))
    .catch(letter);
  return icon;
}

function tile(f) {
  const a = el("a", "mosaico");
  link(a, f.url);
  a.title = `${f.title || host(f.url)}\n${f.url}`;
  a.append(siteIcon(f.url, 64), el("span", "nombre", f.title || host(f.url)));
  return a;
}

/** Fila con ícono, título y sitio (Visitados, Reanudar, Lista de lectura). */
function row(item, { onOpen, onRemove, removeTitle, extra } = {}) {
  const a = el("a", "fila");
  if (onOpen) {
    a.href = item.url;
    a.addEventListener("click", e => {
      if (e.button) return;
      e.preventDefault();
      onOpen(e);
    });
  } else {
    link(a, item.url);
  }
  a.title = item.url;
  const text = el("span", "textos");
  text.append(el("span", "titulo", item.title || host(item.url)), el("span", "sitio", extra || host(item.url)));
  a.append(siteIcon(item.url, 32, "icono chico"), text);
  if (onRemove) {
    const x = el("button", "quitar", "×");
    x.title = removeTitle;
    x.addEventListener("click", e => {
      e.preventDefault();
      e.stopPropagation();
      onRemove(a);
    });
    a.append(x);
  }
  return a;
}

// ── Datos (todo local) ─────────────────────────────────────────────

async function favorites() {
  const tree = await PlacesUtils.promiseBookmarksTree(PlacesUtils.bookmarks.toolbarGuid).catch(() => null);
  return (tree?.children || []).filter(b => web(b.uri)).slice(0, 16)
    .map(b => ({ url: b.uri, title: b.title }));
}

async function frequent(skip) {
  const db = await PlacesUtils.promiseDBConnection();
  const rows = await db.executeCached(`
    SELECT url, title FROM moz_places
    WHERE hidden = 0 AND frecency > 0 AND (url LIKE :https OR url LIKE :http)
    ORDER BY frecency DESC LIMIT 60`, { https: "https://%", http: "http://%" });
  const seen = new Set(skip.map(f => host(f.url)));
  const out = [];
  for (const r of rows) {
    const url = r.getResultByName("url");
    const h = host(url);
    if (seen.has(h)) continue;
    seen.add(h);
    out.push({ url, title: r.getResultByName("title") || h });
    if (out.length == 8) break;
  }
  return out;
}

async function readingList() {
  const guid = Services.prefs.getStringPref("kaze.readinglist.guid", "");
  if (!guid) return [];
  const tree = await PlacesUtils.promiseBookmarksTree(guid).catch(() => null);
  return (tree?.children || []).filter(b => b.uri).slice(0, 12)
    .map(b => ({ url: b.uri, title: b.title, guid: b.guid }));
}

async function trackers() {
  try {
    const db = Cc["@mozilla.org/tracking-db-service;1"].getService(Ci.nsITrackingDBService);
    const now = Date.now();
    const rows = await db.getEventsByDateRange(now - 7 * 86400_000, now);
    let total = 0;
    for (const r of rows) total += r.getResultByName("count");
    return total;
  } catch {
    return 0;
  }
}

/** Pestañas cerradas hace poco en esta ventana (SessionStore), sin repetir la página de inicio. */
function closedTabs() {
  const ss = win.SessionStore;
  if (!ss) return [];
  let list = [];
  try {
    list = ss.getClosedTabDataForWindow ? ss.getClosedTabDataForWindow(win) : JSON.parse(ss.getClosedTabData(win));
  } catch {
    return [];
  }
  const out = [];
  list.forEach((t, index) => {
    const entries = t.state?.entries || [];
    const e = entries[(t.state?.index || entries.length) - 1] || entries.at(-1);
    if (!e || !web(e.url)) return;
    out.push({ url: e.url, title: t.title || e.title, index, closedAt: t.closedAt, closedId: t.closedId });
  });
  return out.slice(0, 6);
}

/** Pestañas de otros dispositivos (Firefox Sync). Sin cuenta de Sync no se carga nada. */
async function syncedTabs() {
  if (!Services.prefs.getStringPref("services.sync.username", "")) return [];
  try {
    const { SyncedTabs } = ChromeUtils.importESModule("resource://services-sync/SyncedTabs.sys.mjs");
    if (!SyncedTabs.isConfiguredToSyncTabs) return [];
    const clients = await SyncedTabs.getTabClients();
    return clients.filter(c => c.tabs?.length).slice(0, 3).map(c => ({
      name: c.name,
      tabs: c.tabs.filter(t => web(t.url)).sort((a, b) => (b.lastUsed || 0) - (a.lastUsed || 0)).slice(0, 4),
    })).filter(c => c.tabs.length);
  } catch {
    return [];
  }
}

const ago = ms => {
  const m = Math.round((Date.now() - ms) / 60000);
  if (!ms || m < 1) return "ahora";
  if (m < 60) return `hace ${m} min`;
  const h = Math.round(m / 60);
  return h < 24 ? `hace ${h} h` : `hace ${Math.round(h / 24)} d`;
};

// ── Pintar ─────────────────────────────────────────────────────────

function fill(section, items, make) {
  const s = $(section);
  s.hidden = !items.length;
  s.querySelector(".cuadricula, .filas").replaceChildren(...items.map(make));
}

function restoreClosed(item) {
  const ss = win.SessionStore;
  const here = win.gBrowser.getTabForBrowser(window.browsingContext.embedderElement);
  try {
    const tab = ss.undoCloseTab(win, item.index);
    if (tab && here && here != tab) win.gBrowser.removeTab(here, { animate: false });
  } catch {
    win.openTrustedLinkIn(item.url, "current");
  }
}

function renderResume(closed, devices) {
  fill("#cerradas", closed, c => row(c, { onOpen: () => restoreClosed(c), extra: `${host(c.url)} · ${ago(c.closedAt)}` }));
  const box = $("#dispositivos");
  box.hidden = !devices.length;
  box.replaceChildren(...devices.map(d => {
    const g = el("div", "grupo");
    const list = el("div", "filas");
    list.append(...d.tabs.map(t => row({ url: t.url, title: t.title }, { extra: `${host(t.url)} · ${ago(t.lastUsed * 1000)}` })));
    g.append(el("h3", "dispositivo", d.name), list);
    return g;
  }));
  $("#reanudar").hidden = !closed.length && !devices.length;
}

function applySpaceColors() {
  const root = win?.document.documentElement;
  if (!root) return;
  for (const n of ["--kaze-c1", "--kaze-c2", "--kaze-c3"]) {
    const v = root.style.getPropertyValue(n);
    if (v) document.documentElement.style.setProperty(n, v);
  }
}

let busy = false;
async function render() {
  if (busy) return;
  busy = true;
  try {
    applySpaceColors();
    // Lo que está en memoria primero (cerradas), para que la página aparezca enseguida.
    renderResume(closedTabs(), []);
    const fav = await favorites();
    const [freq, reading, blocked, devices] = await Promise.all([
      frequent(fav), readingList(), trackers(), syncedTabs(),
    ]);
    fill("#favoritos", fav, tile);
    fill("#frecuentes", freq, f => row(f));
    fill("#lectura", reading, item => row(item, {
      removeTitle: "Quitar de la lista de lectura",
      onRemove: async a => {
        await PlacesUtils.bookmarks.remove(item.guid).catch(() => {});
        a.remove();
        if (!$("#lectura .filas").childElementCount) $("#lectura").hidden = true;
      },
    }));
    renderResume(closedTabs(), devices);
    $("#rastreadores").textContent = blocked.toLocaleString("es");
    $("#rastreadores-texto").textContent = blocked
      ? (blocked == 1 ? "rastreador bloqueado" : "rastreadores bloqueados") +
        " en los últimos siete días. Fyx evitó que te siguieran de un sitio a otro."
      : "En los últimos siete días, Fyx no encontró rastreadores en los sitios que visitaste.";
    $("#vacio").hidden = !!(fav.length || freq.length || reading.length);
  } finally {
    busy = false;
  }
}

function greet() {
  const now = new Date();
  const h = now.getHours();
  $("#hola").textContent = h < 6 ? "Buenas noches" : h < 12 ? "Buenos días" : h < 20 ? "Buenas tardes" : "Buenas noches";
  const d = now.toLocaleDateString("es", { weekday: "long", day: "numeric", month: "long" });
  $("#fecha").textContent = d.charAt(0).toUpperCase() + d.slice(1);
}

greet();
$("#buscar").addEventListener("click", () => win.gURLBar?.focus());
// Sin temporizadores mientras la pestaña está oculta: se actualiza al volver a ella.
let clock = setInterval(greet, 60_000);
document.addEventListener("visibilitychange", () => {
  clearInterval(clock);
  if (document.visibilityState != "visible") return;
  // La entrada escalonada solo la primera vez; al volver a la pestaña no se repite.
  document.documentElement.classList.add("listo");
  greet();
  clock = setInterval(greet, 60_000);
  render();
});
render();
