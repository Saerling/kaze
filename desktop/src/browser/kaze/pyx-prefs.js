// Fyx para Windows (sobre Zen): preferencias que se aplican al abrir Fyx.
// Pensadas para gastar mucha menos RAM sin que se note, sobre todo con muchas pestañas y ventanas.

// ── Idioma y actualizaciones ──────────────────────────────────────────
pref("intl.locale.requested", "es-ES");
pref("app.update.auto", false);
pref("kaze.update.repo", "Saerling/kaze");

// ── Diseño: barra lateral de Zen + barra superior con la dirección centrada (como Safari) ──
pref("zen.view.use-single-toolbar", false);
pref("zen.urlbar.behavior", "normal");
pref("zen.theme.border-radius", 12);
pref("zen.theme.acrylic-sidebar", true);
// Transparencia del vidrio de Fyx (0 = más transparente, 1 = opaco). Se cambia también desde el menú de Fyx.
pref("pyx.glass.opacity", "0.5");
pref("zen.view.show-clear-tabs-button", true);
pref("zen.updates.show-update-notification", false);
pref("zen.welcome-screen.seen", true);
// Página de inicio de Fyx (favoritos, frecuentes, privacidad, reanudar navegación, lista de lectura).
pref("pyx.startpage.enabled", true);
pref("browser.startup.homepage", "chrome://kaze/content/inicio.html");
// Ctrl+T abre una pestaña con la página de inicio (como Safari), no la dirección flotante de Zen.
pref("zen.urlbar.replace-newtab", false);
pref("zen.urlbar.open-on-startup", false);
#ifdef XP_WIN
pref("widget.windows.mica", true);
pref("widget.windows.mica.toplevel-backdrop", 2);
pref("widget.windows.mica.popups", 1);
#endif

// ── Procesos: el mayor gasto de RAM ───────────────────────────────────
// Aislar en su propio proceso solo los sitios de alto valor (los que tienen cuenta o pago),
// no todos: con muchas pestañas son decenas de procesos menos (igual que Firefox en Android).
pref("fission.webContentIsolationStrategy", 2);
pref("dom.ipc.processCount", 3);
pref("dom.ipc.processCount.webIsolated", 1);
pref("dom.ipc.keepProcessesAlive.web", 0);
pref("dom.ipc.processPrelaunch.enabled", false);

// ── Pestañas dormidas ─────────────────────────────────────────────────
pref("zen.tab-unloader.enabled", true);
pref("zen.tab-unloader.timeout-minutes", 10);
pref("browser.tabs.unloadOnLowMemory", true);
// Con poca memoria, Firefox puede dormir pestañas que lleven 2 min sin usarse (antes 5).
pref("browser.tabs.min_inactive_duration_before_unload", 120000);
pref("browser.low_commit_space_threshold_mb", 2048);
pref("browser.low_commit_space_threshold_percent", 10);
// Regulador de Fyx: si todo el navegador pasa de este límite, duerme las pestañas más viejas.
pref("pyx.memory.enabled", true);
pref("pyx.memory.limitMB", 0); // 0 = automático (25 % de la RAM del PC, entre 1,25 y 3 GB)

// ── Cachés en RAM ─────────────────────────────────────────────────────
pref("browser.sessionhistory.max_total_viewers", 0);
pref("browser.cache.memory.capacity", 32768);
pref("image.mem.surfacecache.max_size_kb", 131072);
pref("browser.newtab.preload", false);
pref("network.prefetch-next", false);
pref("browser.sessionstore.interval", 60000);
// Sesión más ligera: menos pestañas cerradas guardadas y menos historial por pestaña
// (se guarda en RAM y se reescribe en disco cada minuto).
pref("browser.sessionstore.max_tabs_undo", 15);
pref("browser.sessionstore.max_windows_undo", 2);
pref("browser.sessionstore.max_serialize_back", 6);
pref("browser.sessionstore.max_serialize_forward", 2);
pref("browser.sessionhistory.max_entries", 30);
// Caché de imágenes decodificadas: se descarta antes lo que no está a la vista.
pref("image.mem.surfacecache.min_expiration_ms", 30000);
pref("media.memory_cache_max_size", 16384);

// ── Menos ruido ───────────────────────────────────────────────────────
pref("extensions.pocket.enabled", false);
pref("browser.ml.chat.enabled", false);
// Funciones de IA local de Firefox: cargan un proceso de inferencia que gasta cientos de MB.
pref("browser.ml.enable", false);
pref("browser.ml.linkPreview.enabled", false);
pref("browser.tabs.groups.smart.enabled", false);
pref("places.semanticHistory.featureGate", false);
pref("extensions.ml.enabled", false);
// Sugerencias patrocinadas y precargas que no hacen falta.
pref("browser.urlbar.quicksuggest.enabled", false);
pref("browser.topsites.contile.enabled", false);
pref("network.dns.disablePrefetch", true);
pref("network.predictor.enabled", false);
// Sin conexiones anticipadas al pasar el ratón o escribir en la dirección.
pref("network.http.speculative-parallel-limit", 0);
pref("browser.urlbar.speculativeConnect.enabled", false);
pref("browser.places.speculativeConnect.enabled", false);
// Desplazamiento más fluido: física de inercia como en Safari y rebote al llegar al borde.
pref("general.smoothScroll.msdPhysics.enabled", true);
pref("apz.overscroll.enabled", true);
pref("datareporting.healthreport.uploadEnabled", false);
pref("toolkit.telemetry.enabled", false);
pref("app.update.enabled", false);
pref("app.update.service.enabled", false);
pref("app.update.background.enabled", false);
pref("app.shield.optoutstudies.enabled", false);
pref("app.normandy.enabled", false);
pref("browser.shell.checkDefaultBrowser", false);
