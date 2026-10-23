#!/usr/bin/env python3
# This Source Code Form is subject to the terms of the Mozilla Public
# License, v. 2.0. If a copy of the MPL was not distributed with this
# file, You can obtain one at http://mozilla.org/MPL/2.0/.
"""
Arma Fyx para Windows sobre Zen Browser (sin compilar nada): Zen tal cual, con la marca,
la identidad y el actualizador de Fyx.

    python desktop/scripts/empaquetar_windows.py --setup zen.installer.exe --build 12

Deja la carpeta lista en desktop/dist/Kaze y, si está makensis, el instalador
desktop/dist/Kaze-<versión>-setup.exe.

Qué hace:
  - Extrae el Firefox oficial (necesita 7z).
  - Le da a Kaze su propia identidad (browser/application.ini: Name=Kaze), así tiene su
    propia carpeta de perfiles y no comparte nada con un Firefox instalado.
  - Cambia la marca (nombre "Kaze" e íconos) dentro de browser/omni.ja.
  - Copia la interfaz de Kaze (carpeta kaze/), el cargador y las preferencias.
  - Desactiva las actualizaciones de Mozilla: Kaze se actualiza solo desde GitHub.
"""
import argparse
import datetime
import json
import re
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

AQUI = Path(__file__).resolve().parent
sys.path.insert(0, str(AQUI))
from kaze_comun import DESKTOP, KAZE, cargador, preferencias_cfg, prefs_planas  # noqa: E402
from mozjar import JarReader, JarWriter  # noqa: E402

MARCA = DESKTOP / "configs" / "branding" / "release"
LOGO_SVG = DESKTOP.parent / "branding" / "kaze-logo.svg"

EXE = "zen.exe"

# Archivos que Fyx no usa (actualizador de Mozilla, telemetría, ventana privada suelta).
QUITAR = [
    "updater.exe", "updater.ini", "update-settings.ini", "maintenanceservice.exe",
    "maintenanceservice_installer.exe", "default-browser-agent.exe", "pingsender.exe",
    "private_browsing.exe", "private_browsing.VisualElementsManifest.xml", "uninstall",
    "precomplete", "removed-files",
]

POLITICAS = {
    "policies": {
        "DisableAppUpdate": True,
        "DisableTelemetry": True,
        "DisableFirefoxStudies": True,
        "DisableDefaultBrowserAgent": True,
        "DontCheckDefaultBrowser": True,
        "OverrideFirstRunPage": "",
        "OverridePostUpdatePage": "",
    }
}

MARCA_FTL = """-brand-shorter-name = Fyx
-brand-short-name = Fyx
-brand-shortcut-name = Fyx
-brand-full-name = Fyx
-brand-product-name = Fyx
-vendor-short-name = Mozilla
trademarkInfo = Fyx está basado en Zen Browser y Firefox. Firefox es una marca registrada de la Fundación Mozilla.
"""
MARCA_PROPERTIES = "brandShorterName = Fyx\nbrandShortName = Fyx\nbrandFullName = Fyx\n"
WORDMARK = (
    '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 172 42">'
    '<text x="0" y="33" font-family="Segoe UI, system-ui, sans-serif" font-size="36" '
    'font-weight="700" fill="context-fill" letter-spacing="-1">Fyx</text></svg>\n'
)


def extraer(setup: Path, destino: Path) -> Path:
    print(f"Extrayendo {setup.name}…")
    subprocess.run(["7z", "x", "-y", f"-o{destino}", str(setup)], check=True, stdout=subprocess.DEVNULL)
    core = destino / "core"
    if not (core / EXE).exists():
        sys.exit(f"El instalador no tiene core/{EXE}: ¿es zen.installer.exe?")
    return core


def identidad(app: Path, build_id: str) -> None:
    """browser/application.ini con el nombre de Kaze; firefox.exe -app lo usa en vez de los datos de Firefox."""
    texto = (app / "application.ini").read_text(encoding="utf-8")
    lineas = [l for l in texto.splitlines() if not l.startswith(";")]
    texto = "\n".join(lineas) + "\n"
    texto = re.sub(r"^Vendor=.*$", "Vendor=Fyx", texto, flags=re.M)
    texto = re.sub(r"^Name=.*$", "Name=Fyx", texto, flags=re.M)
    texto = re.sub(r"^RemotingName=.*$", "RemotingName=pyx", texto, flags=re.M)
    texto = re.sub(r"^Profile=.*$", "Profile=pyx", texto, flags=re.M)
    # Un BuildID propio por versión de Kaze: Firefox limpia su caché de arranque al cambiar.
    texto = re.sub(r"^BuildID=.*$", f"BuildID={build_id}", texto, flags=re.M)
    texto = re.sub(r"^EnableProfileMigrator=.*$", "EnableProfileMigrator=0", texto, flags=re.M)
    texto = re.sub(r"\[Crash Reporter\][^\[]*", "[Crash Reporter]\nEnabled=0\n\n", texto)
    texto = re.sub(r"\[AppUpdate\][^\[]*", "", texto)
    (app / "browser" / "application.ini").write_text(texto.strip() + "\n", encoding="utf-8")


def remarcar_omni(omni: Path) -> None:
    """Cambia el nombre y los íconos de Firefox por los de Kaze dentro de browser/omni.ja."""
    lector = JarReader(str(omni))
    reemplazos: dict[str, bytes] = {}
    for entrada in lector:
        n = entrada.filename
        if re.fullmatch(r"localization/[^/]+/branding/brand\.ftl", n):
            reemplazos[n] = MARCA_FTL.encode()
        elif re.fullmatch(r"chrome/[^/]+/locale/branding/brand\.properties", n):
            reemplazos[n] = MARCA_PROPERTIES.encode()
    base = "chrome/browser/content/branding/"
    for tam in (16, 32, 48, 64, 128):
        reemplazos[f"{base}icon{tam}.png"] = (MARCA / f"logo{tam}.png").read_bytes()
    reemplazos[base + "about-logo.png"] = (MARCA / "logo256.png").read_bytes()
    reemplazos[base + "about-logo@2x.png"] = (MARCA / "logo512.png").read_bytes()
    reemplazos[base + "about-logo.svg"] = LOGO_SVG.read_bytes()
    reemplazos[base + "about.png"] = (MARCA / "logo256.png").read_bytes()
    reemplazos[base + "about-wordmark.svg"] = WORDMARK.encode()
    reemplazos[base + "firefox-wordmark.svg"] = WORDMARK.encode()

    orden = [e.filename for e in lector]
    nombres = set(orden)
    nuevo = omni.with_suffix(".nuevo")
    escritor = JarWriter(file=str(nuevo), compress=True)
    # Se conserva el orden "precargado" de Mozilla (lo que Firefox lee al arrancar va primero).
    if lector.is_optimized and lector.last_preloaded in nombres:
        escritor.preload(orden[: orden.index(lector.last_preloaded) + 1])
    for entrada in lector:
        n = entrada.filename
        datos = reemplazos.get(n)
        # Las entradas sin cambios se copian tal cual, sin descomprimir.
        escritor.add(n, datos if datos is not None else entrada, compress=entrada.compressed)
    escritor.finish()
    faltan = [n for n in reemplazos if n not in nombres]
    if faltan:
        print("  (no estaban en omni.ja, se omiten:", ", ".join(faltan) + ")")
    del lector
    nuevo.replace(omni)
    print(f"  marca cambiada en {len([n for n in reemplazos if n in nombres])} archivos de omni.ja")


def prefs_pyx(build: int, version: str, windows: bool = True) -> str:
    """pyx-prefs.js como defaultPref(...) (el usuario puede cambiarlas en about:config) + la versión."""
    texto = prefs_planas((KAZE / "pyx-prefs.js").read_text(encoding="utf-8"), windows=windows)
    lineas = [re.sub(r"^pref\(", "  defaultPref(", l) for l in texto.splitlines() if l.startswith("pref(")]
    lineas += [f'  defaultPref("kaze.build", {build});', f'  defaultPref("kaze.version", "{version}");']
    return "\n".join(lineas) + "\n"


def copiar_kaze(app: Path, build: int, version: str) -> None:
    destino = app / "kaze"
    if destino.exists():
        shutil.rmtree(destino)
    (destino / "content").mkdir(parents=True)
    shutil.copy2(KAZE / "kaze.manifest", destino / "kaze.manifest")
    shutil.copy2(KAZE / "content" / "actualizador.js", destino / "content" / "actualizador.js")

    prefs = prefs_pyx(build, version)
    cfg = (KAZE / "cargador-zen.cfg").read_text(encoding="utf-8")
    cfg += '\nif (Services.appinfo.name == "Fyx") {\n' + prefs + "}\n"
    for nombre in ("memoria.js", "pyx.css", "pestanas.js", "iconos/pestanas-pyx.svg"):
        (destino / "content" / nombre).parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(KAZE / "content" / nombre, destino / "content" / nombre)
    (app / "kaze.cfg").write_text(cfg, encoding="utf-8")
    (app / "defaults" / "pref" / "kaze-loader.js").write_text(
        'pref("general.config.filename", "kaze.cfg");\n'
        'pref("general.config.obscure_value", 0);\n'
        'pref("general.config.sandbox_enabled", false);\n',
        encoding="utf-8",
    )
    # Sin políticas: Firefox mostraría "administrado por su organización". Las actualizaciones de Zen
    # quedan apagadas porque browser/application.ini no tiene servidor de actualizaciones.
    (app / "distribution" / "policies.json").unlink(missing_ok=True)

    # Ícono de la ventana y de la barra de tareas (Firefox busca chrome/icons/default/<id de ventana>.ico).
    iconos = app / "browser" / "chrome" / "icons" / "default"
    iconos.mkdir(parents=True, exist_ok=True)
    shutil.copy2(MARCA / "firefox.ico", iconos / "main-window.ico")
    shutil.copy2(MARCA / "firefox.ico", app / "kaze.ico")


def poner_icono_exe(exe: Path, ico: Path) -> None:
    """Cambia los íconos dentro de firefox.exe (barra de tareas, Alt+Tab) con resedit (Node, sin Windows)."""
    script = AQUI / "icono_exe.mjs"
    try:
        subprocess.run(["node", str(script), str(exe), str(ico)], check=True, cwd=AQUI)
        print("  ícono de firefox.exe cambiado por el de Fyx")
    except (OSError, subprocess.CalledProcessError) as e:
        print(f"  (no se pudo cambiar el ícono del .exe: {e})")


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--setup", required=True, type=Path, help="instalador de Zen (zen.installer.exe)")
    ap.add_argument("--build", required=True, type=int, help="número de esta versión de Kaze (sube con cada una)")
    ap.add_argument("--salida", type=Path, default=DESKTOP / "dist")
    ap.add_argument("--sin-instalador", action="store_true", help="solo la carpeta, sin llamar a makensis")
    a = ap.parse_args()
    # NSIS resuelve las rutas relativas desde la carpeta del .nsi: todo absoluto.
    a.setup = a.setup.resolve()
    a.salida = a.salida.resolve()

    surfer = json.loads((DESKTOP / "surfer.json").read_text(encoding="utf-8"))
    firefox = surfer["version"]["version"]
    version = f"0.4.{a.build}"
    build_id = datetime.datetime.now(datetime.timezone.utc).strftime("%Y%m%d%H%M%S")

    a.salida.mkdir(parents=True, exist_ok=True)
    app = a.salida / "Fyx"
    if app.exists():
        shutil.rmtree(app)
    with tempfile.TemporaryDirectory() as tmp:
        core = extraer(a.setup, Path(tmp))
        shutil.move(str(core), app)

    for nombre in QUITAR:
        p = app / nombre
        if p.is_dir():
            shutil.rmtree(p)
        elif p.exists():
            p.unlink()
    identidad(app, build_id)
    remarcar_omni(app / "browser" / "omni.ja")
    copiar_kaze(app, a.build, version)
    poner_icono_exe(app / EXE, MARCA / "firefox.ico")
    zen = re.search(r"^Version=(.*)$", (app / "application.ini").read_text(encoding="utf-8"), re.M)
    (app / "kaze-version.txt").write_text(f"Fyx {version} (Zen {zen.group(1) if zen else '?'}, build {build_id})\n", encoding="utf-8")
    print(f"Carpeta lista: {app}")

    if a.sin_instalador:
        return
    makensis = shutil.which("makensis")
    if not makensis:
        sys.exit("Falta makensis (NSIS) para crear el instalador; usa --sin-instalador para solo la carpeta.")
    exe = a.salida / f"Fyx-{version}-setup.exe"
    subprocess.run([
        makensis, "-V2", "-INPUTCHARSET", "UTF8",
        f"-DVERSION={version}", f"-DFIREFOX={firefox}", f"-DBUILD={a.build}",
        f"-DORIGEN={app}", f"-DSALIDA={exe}", f"-DICONO={MARCA / 'firefox.ico'}", f"-DEXE={EXE}",
        str(DESKTOP / "windows" / "kaze.nsi"),
    ], check=True)
    print(f"Instalador: {exe} ({exe.stat().st_size / 1048576:.0f} MB)")


if __name__ == "__main__":
    main()
