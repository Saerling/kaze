/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

/*
 * Modo Lector de Kaze (como el de Safari): usa Readability (el mismo de Firefox)
 * para sacar el artículo y lo muestra limpio, con tipografía cómoda.
 * Habla con la app por mensajería nativa ("kaze").
 */
(() => {
  if (window.top !== window) return;

  const port = browser.runtime.connectNative("kaze");
  let original = null;
  let options = { tema: "claro", tamano: 19, fuente: "serif" };

  function readerable() {
    try {
      return isProbablyReaderable(document, { minContentLength: 140, minScore: 20 });
    } catch (e) {
      return false;
    }
  }

  function aplicarOpciones(root) {
    const temas = {
      claro: ["#ffffff", "#1d1d1f", "#6e6e73"],
      sepia: ["#f8f1e3", "#4b3f30", "#8c7a63"],
      gris: ["#4a4a4d", "#f2f2f7", "#c7c7cc"],
      oscuro: ["#000000", "#e5e5ea", "#8e8e93"],
    };
    const [bg, fg, soft] = temas[options.tema] || temas.claro;
    root.style.setProperty("--kz-bg", bg);
    root.style.setProperty("--kz-fg", fg);
    root.style.setProperty("--kz-soft", soft);
    root.style.setProperty("--kz-size", options.tamano + "px");
    root.style.setProperty(
      "--kz-font",
      options.fuente === "sans" ? "system-ui, -apple-system, Roboto, sans-serif" : "Georgia, 'Noto Serif', serif",
    );
  }

  function mostrarLector() {
    if (original) return;
    const art = new Readability(document.cloneNode(true)).parse();
    if (!art || !art.content) {
      port.postMessage({ type: "error", message: "Esta página no tiene un artículo para leer" });
      return;
    }
    original = { title: document.title, html: document.documentElement.innerHTML, scroll: window.scrollY };
    const minutos = Math.max(1, Math.round((art.length || 0) / 1100));
    document.documentElement.innerHTML = `
      <head>
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <style>
          html { background: var(--kz-bg); }
          body { margin: 0; padding: 28px 22px 120px; background: var(--kz-bg); color: var(--kz-fg);
                 font: var(--kz-size)/1.62 var(--kz-font); -webkit-text-size-adjust: 100%; }
          main { max-width: 680px; margin: 0 auto; }
          .kz-site { font: 600 13px/1.4 system-ui, sans-serif; color: var(--kz-soft); text-transform: uppercase; letter-spacing: .04em; }
          h1.kz-title { font: 700 1.55em/1.2 system-ui, -apple-system, sans-serif; margin: .3em 0 .2em; letter-spacing: -.02em; }
          .kz-meta { font: 14px/1.4 system-ui, sans-serif; color: var(--kz-soft); margin-bottom: 1.6em; }
          img, video, figure { max-width: 100%; height: auto; border-radius: 10px; }
          figure { margin: 1.2em 0; } figcaption { font-size: .8em; color: var(--kz-soft); }
          a { color: #0a84ff; } pre, code { font-size: .85em; white-space: pre-wrap; }
          blockquote { margin: 1em 0; padding-left: 1em; border-left: 3px solid var(--kz-soft); color: var(--kz-soft); }
          table { border-collapse: collapse; max-width: 100%; display: block; overflow-x: auto; }
        </style>
      </head>
      <body><main>
        <div class="kz-site"></div>
        <h1 class="kz-title"></h1>
        <div class="kz-meta"></div>
        <article class="kz-content"></article>
      </main></body>`;
    document.querySelector(".kz-site").textContent = art.siteName || location.hostname.replace(/^www\./, "");
    document.querySelector(".kz-title").textContent = art.title || original.title;
    document.querySelector(".kz-meta").textContent =
      [art.byline, `${minutos} min de lectura`].filter(Boolean).join(" · ");
    document.querySelector(".kz-content").innerHTML = art.content;
    document.title = art.title || original.title;
    aplicarOpciones(document.documentElement);
    window.scrollTo(0, 0);
    port.postMessage({ type: "reader", active: true });
  }

  function salirLector() {
    if (!original) return;
    // Volver a cargar la página es lo más fiable (los scripts de la página quedaron fuera).
    original = null;
    location.reload();
  }

  port.onMessage.addListener(msg => {
    if (!msg) return;
    if (msg.action === "reader") mostrarLector();
    else if (msg.action === "exitReader") salirLector();
    else if (msg.action === "options") {
      options = Object.assign(options, msg.options || {});
      if (original) aplicarOpciones(document.documentElement);
    } else if (msg.action === "check") {
      port.postMessage({ type: "readerable", value: readerable() });
    }
  });

  port.postMessage({ type: "readerable", value: readerable() });
})();
