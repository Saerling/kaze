// Pyx para Windows (sobre Zen): preferencias que se aplican al abrir Pyx.
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
pref("zen.view.show-clear-tabs-button", true);
pref("zen.updates.show-update-notification", false);
pref("zen.welcome-screen.seen", true);
#ifdef XP_WIN
pref("widget.windows.mica", true);
pref("widget.windows.mica.toplevel-backdrop", 2);
#endif

// ── Procesos: el mayor gasto de RAM ───────────────────────────────────
// Aislar en su propio proceso solo los sitios de alto valor (los que tienen cuenta o pago),
// no todos: con muchas pestañas son decenas de procesos menos (igual que Firefox en Android).
pref("fission.webContentIsolationStrategy", 2);
pref("dom.ipc.processCount", 4);
pref("dom.ipc.processCount.webIsolated", 1);
pref("dom.ipc.keepProcessesAlive.web", 0);
pref("dom.ipc.processPrelaunch.enabled", false);

// ── Pestañas dormidas ─────────────────────────────────────────────────
pref("zen.tab-unloader.enabled", true);
pref("zen.tab-unloader.timeout-minutes", 15);
pref("browser.tabs.unloadOnLowMemory", true);
pref("browser.tabs.min_inactive_duration_before_unload", 300000);
pref("browser.low_commit_space_threshold_mb", 2048);
pref("browser.low_commit_space_threshold_percent", 10);
// Regulador de Pyx: si todo el navegador pasa de este límite, duerme las pestañas más viejas.
pref("pyx.memory.enabled", true);
pref("pyx.memory.limitMB", 0); // 0 = automático (30 % de la RAM del PC, entre 1,5 y 4 GB)

// ── Cachés en RAM ─────────────────────────────────────────────────────
pref("browser.sessionhistory.max_total_viewers", 1);
pref("browser.cache.memory.capacity", 32768);
pref("image.mem.surfacecache.max_size_kb", 131072);
pref("browser.newtab.preload", false);
pref("network.prefetch-next", false);
pref("browser.sessionstore.interval", 60000);

// ── Menos ruido ───────────────────────────────────────────────────────
pref("extensions.pocket.enabled", false);
pref("browser.ml.chat.enabled", false);
pref("datareporting.healthreport.uploadEnabled", false);
pref("toolkit.telemetry.enabled", false);
