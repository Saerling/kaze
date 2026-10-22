# Arquitectura de Kaze

Kaze se rehízo desde cero con una idea: **apoyarse en lo que Firefox ya trae** y añadir
solo la capa que falta. Menos código propio = menos que se rompa con cada versión de Firefox
y menos memoria.

```
                    ┌──────────────────────────────┐
                    │     Gecko (motor de Firefox)  │
                    └──────────────┬───────────────┘
              ┌────────────────────┴────────────────────┐
     ┌────────▼─────────┐                      ┌────────▼─────────┐
     │  Kaze Windows    │                      │  Kaze Android    │
     │  Firefox 158     │                      │  GeckoView 158   │
     │  + 3 parches     │                      │  + Compose       │
     │  + kaze.js/.css  │                      │                  │
     └────────┬─────────┘                      └────────┬─────────┘
              └──────── misma estética (shared/design) ─┘
```

## Windows (`desktop/`)

### Comparado con Zen

Zen mantiene ~260 parches sobre Firefox y su propia barra lateral. Kaze usa **3 parches**:

| Parche | Para qué |
|---|---|
| `browser/base/content/browser-xhtml.patch` | carga `kaze.css` y `kaze.js` en la ventana del navegador |
| `browser/base/jar-mn.patch` | empaqueta los archivos de Kaze en `chrome://kaze/content/` (en el instalador de Windows los registra el cargador `cargador.cfg`) |
| `browser/app/profile/firefox-js.patch` | incluye `kaze-prefs.js` (preferencias por defecto) |

Todo lo demás vive en `desktop/src/browser/kaze/` y se construye **encima de las pestañas
verticales nativas de Firefox** (que ya existen desde Firefox 136):

- **Espacios** — cada pestaña guarda su espacio en SessionStore (sobrevive a reinicios).
  Cambiar de espacio oculta las pestañas de los demás con `gBrowser.hideTab()`.
- **Essentials** — pestañas fijadas con la marca `kaze-essential`; Firefox ya las dibuja en
  cuadrícula, Kaze solo las estiliza y las deja visibles en todos los espacios.
- **Fijadas del espacio** — pestañas fijadas sin esa marca; solo se ven en su espacio.
- **Modo compacto, página flotante, degradados** — CSS.
- **Dormir pestañas** — cada minuto descarga (`discardBrowser`) las que llevan 20 min sin usarse.

Los espacios se guardan en la preferencia `kaze.spaces.data` (JSON), compartida entre ventanas.

### Compilación

`surfer` (la herramienta de Zen, MPL) descarga el código de Firefox de la versión de
`surfer.json`, enlaza `src/` dentro, aplica los parches y la marca de `configs/branding/`
y compila con `mach`. `scripts/check.py` comprueba en segundos que los parches aplican.

### Cargador de desarrollo

`dev/instalar.py` copia un *autoconfig* a un Firefox normal que inyecta `kaze.js` y `kaze.css`
desde el repositorio. Es el **mismo código** que va compilado, así que sirve para desarrollar
la interfaz sin compilar Firefox (reiniciar Firefox basta para ver los cambios).

## Android (`android/`)

Zen no tiene versión Android; esta parte es propia, con la interfaz de Z-Sync.

```
KazeApp ── Kaze (contenedor, solo en el proceso principal)
             ├── Settings     preferencias (SharedPreferences + estado de Compose)
             ├── Db           SQLite del sistema: espacios, pestañas, historial
             ├── Store        estado en memoria que dibuja la UI; escribe a Db en segundo plano
             ├── GeckoRuntime uno solo (Engine.kt), con prefs de bajo consumo en YAML
             └── SessionPool  sesiones de Gecko por pestaña: crear, dormir, restaurar
MainActivity ── un único GeckoView + pantallas Compose (espacios, navegador, búsqueda, hojas)
```

### Por qué se cerraba la versión anterior

GeckoView abre procesos propios (uno por pestaña, GPU, medios) y Android crea la clase
`Application` en **cada uno**. La versión anterior creaba el motor en `Application.onCreate`,
así que cada proceso hijo intentaba crear otro motor y la app se cerraba al abrir.
Ahora `KazeApp` no crea nada; el contenedor `Kaze` se crea perezosamente desde la actividad,
que solo existe en el proceso principal.

### Ahorro de memoria

| Técnica | Dónde |
|---|---|
| Máximo de páginas cargadas (1 en celulares con poca RAM, 2–3 en el resto) | `SessionPool.trim()` |
| Dormir pestañas sin usar (10 min por defecto) | `SessionPool.sweepIdle()` |
| Al faltar memoria, dormir todo menos la pestaña visible | `KazeApp.onTrimMemory()` |
| Restaurar desde el estado guardado (historial, scroll, formularios) | `SessionPool.ensure()` |
| Un solo `GeckoView`; se oculta en la pantalla de espacios (libera la superficie de GPU) | `MainActivity` |
| Sin Fission por defecto (un proceso por sitio usa varias veces más RAM) | `Engine.create()` |
| Prefs de Gecko: back-forward cache de 1 página, caché de 16 MB, imágenes ≤ 48 MB | `Engine.PREFS` |
| Sin Room/OkHttp/Coil: SQLite, `HttpURLConnection` y `BitmapFactory` del sistema | `data/` |
| Escrituras agrupadas (el estado de sesión cambia con cada scroll) | `Store.schedulePersist()` |

### Pruebas

`app/src/test/`: lógica de URLs, `Store` (espacios, fijadas, privadas, persistencia, historial)
y capturas de la interfaz con Robolectric + Roborazzi (sin teléfono), que quedan en `docs/capturas/`.

## Sincronización (siguiente fase)

Windows ya trae Firefox Sync. Para Android hay dos caminos: integrar los *Android Components*
de Mozilla (cuentas y sync de Firefox), o como hace Z-Sync, hablar directamente con Firefox Sync
y guardar los espacios en una colección propia. Z-Sync (MIT) es una buena referencia para eso.
