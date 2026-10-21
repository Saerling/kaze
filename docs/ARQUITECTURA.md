# Arquitectura de Kaze

## Idea general

```
                 ┌──────────────────────────────┐
                 │   Gecko (motor de Firefox)    │
                 └──────────────┬───────────────┘
                ┌───────────────┴────────────────┐
        ┌───────▼────────┐               ┌───────▼────────┐
        │  Kaze Windows  │               │  Kaze Android  │
        │  Zen + capa    │               │  GeckoView +   │
        │  Kaze (desktop/)│              │  Compose       │
        └───────┬────────┘               └───────┬────────┘
                └──────────► Firefox Sync ◄──────┘
                   (historial, marcadores, pestañas, contraseñas)
```

Usar **el mismo motor en los dos lados** es lo que hace que todo "esté conectado":
el formato de historial, marcadores, contraseñas y pestañas es el mismo, y Firefox Sync ya
sabe moverlos entre dispositivos con cifrado de extremo a extremo.

## Windows (`desktop/`)

Zen no es un fork "a mano" de Firefox: es un conjunto de **parches** que la herramienta
[`surfer`](https://github.com/zen-browser/surfer) aplica sobre el código de Firefox antes de compilar.

Kaze hace lo mismo un nivel más arriba:

1. `desktop/scripts/sync_zen.py` descarga Zen en la versión fijada en `desktop/zen-version.txt`.
2. Aplica la **capa Kaze** encima:
   - `desktop/kaze.json` → nombre, ID de app, nombre del ejecutable (sobrescribe `surfer.json`).
   - `branding/` → íconos y logo.
   - `desktop/prefs/kaze/*.yaml` → preferencias de rendimiento y bajo consumo.
   - `desktop/patches/*.patch` → cambios propios de Kaze sobre el código de Zen (vacío por ahora).
3. Luego se compila con los comandos normales de Zen (`npm run init`, `npm run build`).

**Ventaja:** cuando Zen sale con un Firefox nuevo, solo cambias `zen-version.txt` y vuelves a correr el script.
**Costo:** si un parche de Kaze choca con un cambio de Zen, hay que ajustarlo a mano.

## Android (`android/`)

Zen no tiene versión Android, así que esta parte es propia:

- **Motor:** [GeckoView](https://firefox-source-docs.mozilla.org/mobile/android/geckoview/) desde `maven.mozilla.org`.
  Es una librería, así que la app se compila en minutos (no hay que compilar Firefox).
- **Interfaz:** Kotlin + Jetpack Compose, Material 3.
- **Un solo `GeckoRuntime`** para toda la app (`KazeApp`). Cada pestaña es una `GeckoSession`.

### Cómo ahorra RAM en Android

| Técnica | Dónde |
|---|---|
| Solo la pestaña visible está "activa"; las demás se pausan (`setActive(false)`) | `TabManager.select()` |
| Máximo de pestañas vivas en memoria (`MAX_LIVE_SESSIONS`); las más viejas se **descargan**: se guarda su estado y se cierra la sesión | `TabManager.trimLiveSessions()` |
| Al volver a una pestaña descargada se restaura su estado (scroll, historial) | `Tab.ensureSession()` |
| Cuando Android avisa de memoria baja (`onTrimMemory`), se descargan todas las pestañas menos la actual | `KazeApp.onTrimMemory()` |
| Ajustes del runtime de Gecko: sin compilación de depuración, caché y procesos limitados | `KazeApp.crearRuntime()` |

## Sincronización

Fase 1 (escritorio): Firefox Sync funciona tal cual, igual que en Zen.

Fase 2 (Android): GeckoView por sí solo no trae Sync. El camino es integrar los
**Android Components** de Mozilla (`service-firefox-accounts`, `service-sync-logins`,
`feature-syncedtabs`, etc.), que son las mismas piezas que usa Firefox para Android.
Hay que revisar las condiciones de Mozilla para usar sus cuentas desde una app de terceros;
la alternativa es montar un servidor propio con [`syncstorage-rs`](https://github.com/mozilla-services/syncstorage-rs).

## Diseño compartido

`shared/design/tokens.json` define colores, radios y tiempos de animación.
En escritorio se aplican como tema CSS (Zen ya soporta temas/mods); en Android se leen en `ui/theme/`.
