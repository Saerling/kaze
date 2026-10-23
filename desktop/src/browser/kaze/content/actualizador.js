/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

/*
 * Fyx para Windows (sobre Zen): solo añade el aviso de versión nueva desde GitHub Releases,
 * la descarga y la instalación encima. Todo lo demás es Zen tal cual.
 */

"use strict";

(() => {
  if (window.FyxUpdater) return;
  const { Downloads } = ChromeUtils.importESModule("resource://gre/modules/Downloads.sys.mjs");
  const PREF_BUILD = "kaze.build";
  const PREF_UPDATE_REPO = "kaze.update.repo";
  const PREF_UPDATE_LAST = "kaze.update.lastCheck";
  const PREF_UPDATE_SKIP = "kaze.update.skip";

  const FyxUpdater = {
    // ── Actualizaciones (versión de Windows, desde GitHub Releases) ───

    /**
     * Las versiones de Windows se publican como "windows-<número>" con un instalador
     * "Fyx-…-setup.exe". El número de esta copia está en kaze.build (0 en desarrollo).
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

        await show("Actualización lista. Fyx se cerrará un momento para instalarla y se abrirá de nuevo.", [
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

  };

  /**
   * En Ajustes → Acerca de Fyx, el bloque de actualizaciones de Zen se cambia por el de Fyx:
   * versión instalada y "Buscar actualizaciones" (que descarga e instala desde GitHub).
   */
  FyxUpdater.injectAbout = function (doc) {
    if (!doc || doc.getElementById("pyx-update-box")) return;
    const group = doc.getElementById("setting-control-updateApp") || doc.getElementById("updateApp");
    if (!group) return;
    for (const id of ["setting-control-updateState", "setting-control-showUpdateHistory", "setting-control-updateRadioGroup",
      "setting-control-backgroundUpdate", "setting-control-showUpdatePromptsHeader", "setting-control-updatesManagedByOS"]) {
      doc.getElementById(id)?.setAttribute("hidden", "true");
    }
    const NS = "http://www.w3.org/1999/xhtml";
    const el = (t, css, txt) => { const e = doc.createElementNS(NS, t); if (css) e.style.cssText = css; if (txt) e.textContent = txt; return e; };
    const version = Services.prefs.getStringPref("kaze.version", "");
    // Versión de Zen ("Versión 1.x (64-bit) · Novedades"): se esconde, Fyx muestra la suya.
    const zenInfo = doc.getElementById("updateAppInfo");
    (zenInfo?.closest("setting-control") || zenInfo)?.setAttribute("hidden", "true");
    // Texto a la izquierda (ocupa lo que sobra) y botón a la derecha; si no cabe, el botón baja.
    const box = el("div", "display:flex;flex-wrap:wrap;align-items:center;gap:12px 16px;padding:12px 16px;width:100%;box-sizing:border-box;");
    box.id = "pyx-update-box";
    const text = el("div", "flex:1 1 260px;min-width:220px;display:flex;flex-direction:column;gap:3px;overflow-wrap:normal;word-break:normal;");
    const status = el("span", "font-size:.9em;color:#0A84FF;");
    text.append(
      el("span", "font-weight:600;", `Fyx ${version}`),
      el("span", "opacity:.7;font-size:.9em;", "Basado en Zen. Fyx busca versiones nuevas solo y se actualiza encima."),
      status,
    );
    const btn = el("button", "flex:none;padding:7px 16px;border-radius:8px;border:0;background:#0A84FF;color:white;font-weight:600;cursor:pointer;white-space:nowrap;", "Buscar actualizaciones");
    btn.addEventListener("click", async () => {
      btn.disabled = true;
      status.textContent = "Buscando…";
      const n = await FyxUpdater.checkForUpdate({ manual: true });
      status.textContent = n ? "Hay una versión nueva: mira el aviso arriba de la ventana." : "Fyx está al día.";
      btn.disabled = false;
    });
    box.append(text, btn);
    group.before(box);
  };
  gBrowser.addTabsProgressListener({
    onLocationChange(browser, webProgress, request, location) {
      if (!webProgress.isTopLevel || !location.spec.startsWith("about:preferences")) return;
      let tries = 0;
      const t = setInterval(() => {
        const doc = browser.contentDocument;
        if (doc?.getElementById("pyx-update-box") || ++tries > 40) return clearInterval(t);
        FyxUpdater.injectAbout(doc);
      }, 250);
    },
  });

  window.FyxUpdater = FyxUpdater;
  // Revisa al abrir (tras 15 s) y luego cada 6 horas.
  setTimeout(() => FyxUpdater.checkForUpdate(), 15_000);
  setInterval(() => FyxUpdater.checkForUpdate(), 6 * 3600_000);
})();
