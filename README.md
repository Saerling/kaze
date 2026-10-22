<p align="center">
  <img src="branding/kaze-logo.svg" width="96" alt="Kaze">
</p>

<h1 align="center">Kaze 風</h1>

<p align="center">
  Navegador basado en Firefox para <b>Windows</b> y <b>Android</b>, con espacios, Essentials y poca RAM.<br>
  Las funciones de <a href="https://github.com/zen-browser/desktop">Zen Browser</a> (espacios, Essentials, modo compacto)
  con la estética de Safari.
</p>

<p align="center">
  <img src="docs/capturas/escritorio-pestanas.png" width="640" alt="Kaze en Windows: vista de pestañas">
  &nbsp;
  <img src="docs/capturas/android-pestanas-claro.png" width="180" alt="Kaze en Android">
</p>

## Qué tiene

| | Windows | Android |
|---|---|---|
| Motor | Firefox 158 (Gecko) con identidad propia de Kaze | GeckoView 158 (el Firefox de Android) |
| Instalar y actualizar | instalador `.exe` en Releases; Kaze avisa de versiones nuevas y se actualiza encima | APK en Releases; la app avisa y se actualiza encima |
| **Espacios** con emoji y degradado propio | ✅ barra lateral + selector abajo, `Ctrl+Alt+1…9` | ✅ deslizar a los lados + selector abajo |
| **Essentials** (fijadas en todos los espacios) | ✅ cuadrícula arriba | ✅ cuadrícula arriba |
| Pestañas fijadas por espacio + "Limpiar" | ✅ | ✅ |
| Pestañas que se duermen solas (ahorro de RAM) | ✅ tras 20 min + al faltar memoria | ✅ máx. 1–3 cargadas + tras 10 min + al faltar memoria |
| Modo compacto | ✅ `Ctrl+Alt+C` | — |
| Página "flotante" con bordes redondeados | ✅ | — |
| **Perfiles** (grupos de espacios con cookies y sesiones propias) | ✅ con contenedores de Firefox | ✅ |
| Bloqueo de rastreadores estricto, sin telemetría | ✅ | ✅ |
| Historial, búsqueda con sugerencias, privadas, descargas, buscar en página, versión de escritorio | ✅ (Firefox) | ✅ |
| **Vista de pestañas en tarjetas** con miniaturas | ✅ `Ctrl+Mayús+\` | ✅ |
| **Página de inicio**: favoritos, frecuentes, lista de lectura, informe de privacidad | ✅ | ✅ |
| **Menú aA**: tamaño del texto, lector, traducir (en el equipo, sin enviar la página), buscar | ✅ en la barra de dirección | ✅ en la barra de abajo |
| Barra de dirección | arriba y centrada, como Safari en Mac | abajo, como Safari en iPhone |
| Firefox Sync | ✅ | próxima fase |

## Estructura

| Carpeta | Qué hay |
|---|---|
| [`android/`](android/) | App Kotlin + Jetpack Compose sobre GeckoView. |
| [`desktop/`](desktop/) | Kaze para Windows: la interfaz de Kaze (`src/browser/kaze/`), el empaquetado del instalador (`scripts/`, `windows/`) y la configuración para compilar Firefox entero (con `surfer`, la herramienta de Zen, y **solo 3 parches**). |
| [`shared/design/`](shared/design/) | Colores y medidas comunes a las dos versiones. |
| [`branding/`](branding/) | Logo y generador de íconos. |
| [`docs/`](docs/) | [Arquitectura](docs/ARQUITECTURA.md), [plan de trabajo](docs/ROADMAP.md) y capturas. |

## Probar

- **Android:** descarga el APK de [Releases](https://github.com/Saerling/kaze/releases) (`arm64-v8a`). Desde ahí la app se actualiza sola. Más en [`android/README.md`](android/README.md).
- **Windows:** descarga `Kaze-0.4.N-setup.exe` de [Releases](https://github.com/Saerling/kaze/releases) y ábrelo. Se instala sin permisos de administrador y desde ahí Kaze se actualiza solo. Más en [`desktop/README.md`](desktop/README.md).

## Licencia

[Mozilla Public License 2.0](LICENSE), como Firefox y Zen. Kaze no está afiliado a Mozilla, a Zen Browser ni a Z-Sync.
