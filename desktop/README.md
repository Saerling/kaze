# Kaze para Windows

Kaze de escritorio = **Zen Browser + la capa Kaze**. No copiamos el código de Zen en este repo:
un script lo descarga en la versión fijada y le aplica nuestros cambios.

```
desktop/
├── zen-version.txt        versión de Zen sobre la que se construye (p. ej. 1.23.3b)
├── kaze.json              nombre, ID de app y ejecutable → se fusiona en surfer.json de Zen
├── prefs/kaze/*.yaml      preferencias de Gecko (bajo consumo de RAM, velocidad)
├── patches/*.patch        cambios propios sobre el código de Zen
├── scripts/sync_zen.py    descarga Zen y aplica todo lo anterior
└── build/zen/             (ignorado por git) Zen con la capa Kaze, listo para compilar
```

## Requisitos (Windows)

Compilar Firefox es pesado. Lo mínimo razonable:

- **RAM:** 16 GB (32 GB recomendado) · **Disco:** ~60 GB libres, idealmente SSD
- [Git](https://git-scm.com/download/win), [Python 3.11+](https://www.python.org/downloads/), [Node.js 22+](https://nodejs.org/)
- [MozillaBuild](https://firefox-source-docs.mozilla.org/setup/windows_build.html) — la terminal oficial de Mozilla para compilar en Windows
- Visual Studio Build Tools y Rust: `mach bootstrap` (paso 3) los instala o te dice qué falta

La guía oficial de Mozilla para Windows explica cada requisito:
https://firefox-source-docs.mozilla.org/setup/windows_build.html

## Compilar

Desde la terminal de **MozillaBuild**, en la raíz del repo `kaze`:

```bash
# 1. Generar íconos (solo si cambiaste el logo; ya vienen generados)
pip install cairosvg pillow && python branding/generar_iconos.py

# 2. Descargar Zen y aplicar la capa Kaze
python desktop/scripts/sync_zen.py

# 3. Descargar Firefox, aplicar los parches de Zen e instalar herramientas (la primera vez tarda)
cd desktop/build/zen
npm install
npm run init

# 4. Compilar (la primera vez: 1–3 h según el PC; luego es incremental)
npm run build

# 5. Abrir Kaze
npm start
```

Para generar un instalador: `npm run package` (queda en `desktop/build/zen/dist/`).

## Actualizar a una versión nueva de Zen

1. Cambia el número en `zen-version.txt` (las versiones están en https://github.com/zen-browser/desktop/tags).
2. `python desktop/scripts/sync_zen.py`
3. `cd desktop/build/zen && npm run init && npm run build`

Si algún archivo de `patches/` deja de aplicar, el script se detiene y dice cuál; hay que rehacer ese parche.

## Qué cambia Kaze respecto a Zen (por ahora)

- Nombre, ejecutable (`kaze.exe`) e íconos propios
- Pestañas que se descargan antes cuando falta memoria, menos procesos duplicados, cachés en RAM más pequeñas
  (detalle y explicación de cada valor en [`prefs/kaze/rendimiento.yaml`](prefs/kaze/rendimiento.yaml))
- Sin actualizaciones automáticas desde los servidores de Zen

Pendiente: textos de la interfaz que todavía digan "Zen" (ver [`docs/ROADMAP.md`](../docs/ROADMAP.md)).
