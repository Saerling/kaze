# Kaze para Windows

Firefox 158 compilado como **Kaze**, con la interfaz de Kaze (espacios, Essentials, modo compacto,
pestañas que se duermen) escrita desde cero sobre las pestañas verticales de Firefox.

<img src="../docs/capturas/escritorio-claro.png" width="720" alt="Kaze en Windows">

```
desktop/
├── surfer.json                 nombre, versión de Firefox, marca
├── package.json                comandos (usa surfer, la herramienta de compilación de Zen)
├── configs/
│   ├── common/mozconfig        opciones de compilación (optimizado, sin telemetría)
│   ├── windows/mozconfig
│   ├── linux/mozconfig
│   └── branding/release/       íconos (los genera branding/generar_iconos.py)
├── src/browser/
│   ├── kaze/                   ← la interfaz de Kaze
│   │   ├── content/kaze.js       espacios, Essentials, menús, atajos, dormir pestañas
│   │   ├── content/kaze.css      estética papel y tinta, página flotante, modo compacto
│   │   ├── kaze-prefs.js         preferencias por defecto (memoria, privacidad, interfaz)
│   │   └── jar.inc.mn
│   ├── base/content/browser-xhtml.patch   ┐
│   ├── base/jar-mn.patch                  ├ los únicos 3 cambios al código de Firefox
│   └── app/profile/firefox-js.patch       ┘
├── dev/                        probar la interfaz sobre un Firefox normal, sin compilar
└── scripts/check.py            comprueba que los parches aplican (segundos, sin compilar)
```

## Atajos

| | |
|---|---|
| `Ctrl+Alt+1…9` | ir al espacio 1…9 |
| `Ctrl+Alt+←` / `→` | espacio anterior / siguiente (o rueda del ratón sobre los emojis de abajo) |
| `Ctrl+Alt+C` | modo compacto |
| Clic derecho en una pestaña | Añadir a Essentials · Mover a espacio |
| Clic derecho en un emoji / `⋯` | renombrar, ícono, colores, mover, eliminar espacio |

Preferencias en `about:config`: `kaze.unloader.minutes` (dormir pestañas, por defecto 20),
`kaze.theme.floating` (página flotante), `kaze.compact.enabled`.

## Probar sin compilar

El cargador de desarrollo pone la interfaz de Kaze sobre tu Firefox normal (158 o cercano):

```bash
# Windows: abre la terminal como administrador
python desktop\dev\instalar.py "C:\Program Files\Mozilla Firefox"
```

Luego abre Firefox con un perfil nuevo (`firefox -P` → *Crear perfil* → "kaze") para no tocar el tuyo.
Al editar `kaze.js` o `kaze.css`, reinicia Firefox para ver los cambios.
Para quitarlo: `python desktop\dev\instalar.py "C:\Program Files\Mozilla Firefox" --quitar`.

## Compilar

Compilar Firefox es pesado: **16 GB de RAM** (32 GB ideal), **~60 GB libres** en SSD y
de 1 a 3 horas la primera vez (luego los cambios en `kaze.js`/`kaze.css` tardan segundos).

### En Windows

1. Instala [MozillaBuild](https://firefox-source-docs.mozilla.org/setup/windows_build.html#mozillabuild),
   [Git](https://git-scm.com/download/win) y [Node.js 22+](https://nodejs.org/).
2. Abre `C:\mozilla-build\start-shell.bat` (la terminal de Mozilla) y:

```bash
cd /c/ruta/a/kaze/desktop
python scripts/check.py     # comprobar que todo aplica (segundos)
npm install
npm run init                # descarga Firefox 158, aplica Kaze e instala herramientas (la 1.ª vez tarda)
npm run build               # compilar
npm start                   # abrir Kaze
npm run package             # instalador/zip en dist/
```

Para builds de publicación (más optimizadas, compilan más lento): `KAZE_RELEASE=1 npm run build`.

Si el PC se queda sin memoria al compilar, descomenta `MOZ_MAKE_FLAGS="-j4"` en `configs/common/mozconfig`.

### Cambios rápidos en la interfaz

Tras la primera compilación, al tocar solo `kaze.js`, `kaze.css` o `kaze-prefs.js`:

```bash
cd engine && ./mach build faster && ./mach run
```

## Actualizar a una versión nueva de Firefox

1. Cambia `version` y `candidate` en `surfer.json`.
2. `python scripts/check.py` — si un parche no aplica, dice cuál (son 3 cambios de pocas líneas, fáciles de rehacer).
3. `npm run download && npm run import && npm run build`.

## Cómo encaja con Zen

Kaze toma las ideas de Zen (espacios, Essentials, compacto, página flotante) y su herramienta
de compilación (`surfer`), pero el código es propio y mucho más pequeño: en lugar de reemplazar
la barra de pestañas de Firefox, se monta sobre las pestañas verticales que Firefox ya trae.
Detalles en [docs/ARQUITECTURA.md](../docs/ARQUITECTURA.md).
