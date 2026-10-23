/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

/*
 * Página de inicio de Kaze, como la de Safari: favoritos (la barra de marcadores),
 * los sitios más visitados, el informe de privacidad y la lista de lectura.
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

/** Abre en esta pestaña, o en una nueva con clic central / Ctrl. */
function link(a, url) {
  a.href = url;
  a.addEventListener("click", e => {
    if (e.button > 1) return;
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

function tile(url, title) {
  const a = el("a", "mosaico");
  link(a, url);
  a.title = `${title}\n${url}`;
  const icon = el("span", "icono");
  const letter = () => {
    icon.replaceChildren();
    icon.textContent = (host(url)[0] || "?").toUpperCase();
  };
  const img = el("img");
  img.alt = "";
  img.onerror = letter;
  // Sin ícono guardado, la inicial del sitio (como Safari) en vez del globo genérico.
  PlacesUtils.favicons.getFaviconForPage(Services.io.newURI(url), 64)
    .then(f => (f ? (img.src = "page-icon:" + url) : letter()))
    .catch(letter);
  icon.append(img);
  a.append(icon, el("span", "nombre", title || host(url)));
  return a;
}

async function favorites() {
  const tree = await PlacesUtils.promiseBookmarksTree(PlacesUtils.bookmarks.toolbarGuid).catch(() => null);
  return (tree?.children || []).filter(b => b.uri && /^https?:/.test(b.uri)).slice(0, 16)
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

function fill(section, items, make) {
  const s = $(section);
  s.hidden = !items.length;
  s.querySelector(".cuadricula, .lista").replaceChildren(...items.map(make));
}

function readingCard(item) {
  const a = el("a", "tarjeta lectura");
  link(a, item.url);
  const icon = el("img", "favicon");
  icon.alt = "";
  icon.src = "page-icon:" + item.url;
  icon.onerror = () => icon.remove();
  const text = el("span", "textos");
  text.append(el("span", "titulo", item.title || host(item.url)), el("span", "sitio", host(item.url)));
  const remove = el("button", "quitar", "×");
  remove.title = "Quitar de la lista de lectura";
  remove.addEventListener("click", async e => {
    e.preventDefault();
    e.stopPropagation();
    await PlacesUtils.bookmarks.remove(item.guid);
    a.remove();
    if (!$("#lectura .lista").childElementCount) $("#lectura").hidden = true;
  });
  a.append(icon, text, remove);
  return a;
}

function applySpaceColors() {
  const root = win?.document.documentElement;
  if (!root) return;
  for (const n of ["--kaze-c1", "--kaze-c2", "--kaze-c3"]) {
    const v = root.style.getPropertyValue(n);
    if (v) document.documentElement.style.setProperty(n, v);
  }
}

async function render() {
  applySpaceColors();
  const fav = await favorites();
  const [freq, reading, blocked] = await Promise.all([frequent(fav), readingList(), trackers()]);
  fill("#favoritos", fav, f => tile(f.url, f.title));
  fill("#frecuentes", freq, f => tile(f.url, f.title));
  fill("#lectura", reading, readingCard);
  $("#rastreadores").textContent = blocked.toLocaleString("es");
  $("#rastreadores-texto").textContent = blocked
    ? `rastreadores bloqueados en los últimos siete días. Zev evitó que te siguieran de un sitio a otro.`
    : "En los últimos siete días, Zev no encontró rastreadores en los sitios que visitaste.";
  $("#vacio").hidden = !!(fav.length || freq.length || reading.length);
}

$("#buscar").addEventListener("click", () => win.gURLBar?.focus());
document.addEventListener("visibilitychange", () => {
  if (document.visibilityState == "visible") render();
});
render();
