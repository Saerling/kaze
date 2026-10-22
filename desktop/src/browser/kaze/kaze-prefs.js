/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

// Preferencias por defecto de Kaze. Se incluyen al final de
// browser/app/profile/firefox.js, así que sobrescriben las de Firefox.
// El usuario puede cambiarlas en about:config.

// ── Interfaz ─────────────────────────────────────────────────────────
// Pestañas verticales nativas de Firefox: Kaze construye los espacios encima.
pref("sidebar.revamp", true);
pref("sidebar.verticalTabs", true);
pref("sidebar.visibility", "always-show");
pref("sidebar.main.tools", "history,bookmarks");
pref("sidebar.animation.enabled", true);
pref("browser.toolbars.bookmarks.visibility", "never");
pref("browser.tabs.inTitlebar", 1);
pref("browser.compactmode.show", true);
pref("browser.uidensity", 0);
pref("browser.aboutConfig.showWarning", false);
pref("toolkit.legacyUserProfileCustomizations.stylesheets", true);
// Página de inicio de Kaze (favoritos, frecuentes, lista de lectura, privacidad).
pref("browser.startup.homepage", "chrome://kaze/content/inicio.html");
pref("browser.startup.homepage_override.mstone", "ignore");
pref("startup.homepage_welcome_url", "");
// Los perfiles de Kaze usan contenedores de Firefox (cookies y sesiones separadas).
pref("privacy.userContext.enabled", true);
pref("privacy.userContext.ui.enabled", true);
#ifdef XP_WIN
// Windows 11: fondo acrílico translúcido detrás de la barra lateral, como Safari en macOS.
pref("widget.windows.mica", true);
pref("widget.windows.mica.toplevel-backdrop", 2);
#endif

// Kaze
pref("kaze.spaces.data", "");                 // JSON con los espacios (lo gestiona Kaze)
pref("kaze.compact.enabled", false);          // modo compacto: barra lateral y barra de dirección ocultas
pref("kaze.unloader.enabled", true);          // dormir pestañas sin usar
pref("kaze.unloader.minutes", 20);
pref("kaze.theme.floating", true);            // página "flotante" con bordes redondeados (estilo Zen)
pref("kaze.startpage.enabled", true);         // página de inicio de Kaze en las pestañas nuevas
pref("kaze.profiles.data", "");               // JSON con los perfiles (lo gestiona Kaze)
pref("kaze.build", 0);                        // número de versión de Windows (lo pone el instalador)
pref("kaze.update.repo", "Saerling/kaze");    // de dónde salen las actualizaciones

// ── Memoria ──────────────────────────────────────────────────────────
// Descargar pestañas cuando el sistema se queda sin memoria (y empezar antes que Firefox).
pref("browser.tabs.unloadOnLowMemory", true);
pref("browser.tabs.min_inactive_duration_before_unload", 300000);
#ifdef XP_WIN
pref("browser.low_commit_space_threshold_mb", 1024);
pref("browser.low_commit_space_threshold_percent", 8);
#endif
// Menos procesos de contenido compartidos; Fission sigue aislando sitios entre sí.
pref("dom.ipc.processCount", 4);
pref("dom.ipc.processCount.webIsolated", 1);
// Páginas completas en RAM para "atrás": 2 en vez de hasta 8.
pref("browser.sessionhistory.max_total_viewers", 2);
// Caché de red en RAM: 64 MB como máximo.
pref("browser.cache.memory.capacity", 65536);
// Imágenes decodificadas en RAM: tope de 256 MB (Firefox permite hasta ~2 GB).
pref("image.mem.surfacecache.max_size_kb", 262144);
// Pestañas en segundo plano: temporizadores como mucho cada 10 s.
pref("dom.min_background_timeout_value", 10000);
// Guardar la sesión cada 60 s en vez de cada 15 s.
pref("browser.sessionstore.interval", 60000);
// No precargar la página de nueva pestaña en segundo plano.
pref("browser.newtab.preload", false);

// ── Velocidad ────────────────────────────────────────────────────────
pref("network.http.max-persistent-connections-per-server", 10);
pref("network.dnsCacheEntries", 1000);
pref("network.dnsCacheExpiration", 3600);
pref("browser.startup.preXulSkeletonUI", false);

// ── Privacidad y menos ruido ─────────────────────────────────────────
pref("browser.contentblocking.category", "strict");
pref("privacy.globalprivacycontrol.enabled", true);
pref("datareporting.healthreport.uploadEnabled", false);
pref("datareporting.policy.dataSubmissionEnabled", false);
pref("toolkit.telemetry.enabled", false);
pref("toolkit.telemetry.unified", false);
pref("app.shield.optoutstudies.enabled", false);
pref("app.normandy.enabled", false);
pref("browser.newtabpage.activity-stream.telemetry", false);
pref("browser.newtabpage.activity-stream.showSponsored", false);
pref("browser.newtabpage.activity-stream.showSponsoredTopSites", false);
pref("browser.newtabpage.activity-stream.feeds.section.topstories", false);
pref("browser.newtabpage.activity-stream.showSponsoredCheckboxes", false);
pref("browser.urlbar.suggest.quicksuggest.sponsored", false);
pref("browser.urlbar.suggest.quicksuggest.nonsponsored", false);
pref("browser.discovery.enabled", false);
pref("browser.shell.checkDefaultBrowser", false);
pref("browser.aboutwelcome.enabled", false);
pref("browser.ml.chat.enabled", false);
pref("browser.tabs.groups.smart.enabled", false);
