/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

/*
 * Pyx para Windows (sobre Zen): solo añade el aviso de versión nueva desde GitHub Releases,
 * la descarga y la instalación encima. Todo lo demás es Zen tal cual.
 */

"use strict";

(() => {
  if (window.PyxUpdater) return;
  const { Downloads } = ChromeUtils.importESModule("resource://gre/modules/Downloads.sys.mjs");
  const PREF_BUILD = "kaze.build";
  const PREF_UPDATE_REPO = "kaze.update.repo";
  const PREF_UPDATE_LAST = "kaze.update.lastCheck";
  const PREF_UPDATE_SKIP = "kaze.update.skip";

  const PyxUpdater = {
    // ── Actualizaciones (versión de Windows, desde GitHub Releases) ───

    /**
     * Las versiones de Windows se publican como "windows-<número>" con un instalador
     * "Pyx-…-setup.exe". El número de esta copia está en kaze.build (0 en desarrollo).
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

        await show("Actualización lista. Pyx se cerrará un momento para instalarla y se abrirá de nuevo.", [
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

  window.PyxUpdater = PyxUpdater;
  // Revisa al abrir (tras 15 s) y luego cada 6 horas.
  setTimeout(() => PyxUpdater.checkForUpdate(), 15_000);
  setInterval(() => PyxUpdater.checkForUpdate(), 6 * 3600_000);
})();
