#!/usr/bin/env python3
# This Source Code Form is subject to the terms of the Mozilla Public
# License, v. 2.0. If a copy of the MPL was not distributed with this
# file, You can obtain one at http://mozilla.org/MPL/2.0/.
"""
Arma Kaze para Windows sobre el Firefox oficial (sin compilar Firefox).

    python desktop/scripts/empaquetar_windows.py --setup "Firefox Setup 158.0.1.exe" --build 12

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
from kaze_comun import DESKTOP, KAZE, cargador, preferencias_cfg  # noqa: E402
from mozjar import JarReader, JarWriter  # noqa: E402

MARCA = DESKTOP / "configs" / "branding" / "release"
LOGO_SVG = DESKTOP.parent / "branding" / "kaze-logo.svg"

# Archivos de Firefox que Kaze no usa (actualizador de Mozilla, telemetría, ventana privada suelta).
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

MARCA_FTL = """-brand-shorter-name = Kaze
-brand-short-name = Kaze
-brand-shortcut-name = Kaze
-brand-full-name = Kaze
-brand-product-name = Kaze
-vendor-short-name = Mozilla
trademarkInfo = Kaze es un navegador basado en Firefox. Firefox es una marca registrada de la Fundación Mozilla.
"""
MARCA_PROPERTIES = "brandShorterName = Kaze\nbrandShortName = Kaze\nbrandFullName = Kaze\n"
WORDMARK = (
    '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 172 42">'
    '<text x="0" y="33" font-family="Segoe UI, system-ui, sans-serif" font-size="36" '
    'font-weight="700" fill="context-fill" letter-spacing="-1">Kaze</text></svg>\n'
)


def extraer(setup: Path, destino: Path) -> Path:
    print(f"Extrayendo {setup.name}…")
    subprocess.run(["7z", "x", "-y", f"-o{destino}", str(setup)], check=True, stdout=subprocess.DEVNULL)
    core = destino / "core"
    if not (core / "firefox.exe").exists():
        sys.exit("El instalador no tiene core/firefox.exe: ¿es el instalador completo de Firefox?")
    return core


def identidad(app: Path, build_id: str) -> None:
    """browser/application.ini con el nombre de Kaze; firefox.exe -app lo usa en vez de los datos de Firefox."""
    texto = (app / "application.ini").read_text(encoding="utf-8")
    lineas = [l for l in texto.splitlines() if not l.startswith(";")]
    texto = "\n".join(lineas) + "\n"
    texto = re.sub(r"^Vendor=.*$", "Vendor=Kaze", texto, flags=re.M)
    texto = re.sub(r"^Name=.*$", "Name=Kaze", texto, flags=re.M)
    texto = re.sub(r"^RemotingName=.*$", "RemotingName=kaze", texto, flags=re.M)
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


def copiar_kaze(app: Path, build: int, version: str) -> None:
    destino = app / "kaze"
    if destino.exists():
        shutil.rmtree(destino)
    destino.mkdir()
    shutil.copy2(KAZE / "kaze.manifest", destino / "kaze.manifest")
    shutil.copytree(KAZE / "content", destino / "content")

    prefs = preferencias_cfg(windows=True, extra={"kaze.build": build, "kaze.version": version})
    # Las preferencias de Kaze solo se aplican al abrir Kaze (no al abrir firefox.exe a secas).
    cfg = cargador("") + '\nif (Services.appinfo.name == "Kaze") {\n' + prefs + "}\n"
    (app / "kaze.cfg").write_text(cfg, encoding="utf-8")
    (app / "defaults" / "pref" / "kaze-loader.js").write_text(
        'pref("general.config.filename", "kaze.cfg");\n'
        'pref("general.config.obscure_value", 0);\n'
        'pref("general.config.sandbox_enabled", false);\n',
        encoding="utf-8",
    )
    dist = app / "distribution"
    dist.mkdir(exist_ok=True)
    (dist / "policies.json").write_text(json.dumps(POLITICAS, indent=2), encoding="utf-8")

    # Ícono de la ventana y de la barra de tareas (Firefox busca chrome/icons/default/<id de ventana>.ico).
    iconos = app / "browser" / "chrome" / "icons" / "default"
    iconos.mkdir(parents=True, exist_ok=True)
    shutil.copy2(MARCA / "firefox.ico", iconos / "main-window.ico")
    shutil.copy2(MARCA / "firefox.ico", app / "kaze.ico")


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--setup", required=True, type=Path, help='instalador oficial "Firefox Setup X.exe" (win64)')
    ap.add_argument("--build", required=True, type=int, help="número de esta versión de Kaze (sube con cada una)")
    ap.add_argument("--salida", type=Path, default=DESKTOP / "dist")
    ap.add_argument("--sin-instalador", action="store_true", help="solo la carpeta, sin llamar a makensis")
    a = ap.parse_args()

    surfer = json.loads((DESKTOP / "surfer.json").read_text(encoding="utf-8"))
    firefox = surfer["version"]["version"]
    version = f"0.4.{a.build}"
    build_id = datetime.datetime.now(datetime.timezone.utc).strftime("%Y%m%d%H%M%S")

    a.salida.mkdir(parents=True, exist_ok=True)
    app = a.salida / "Kaze"
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
    (app / "kaze-version.txt").write_text(f"Kaze {version} (Firefox {firefox}, build {build_id})\n", encoding="utf-8")
    print(f"Carpeta lista: {app}")

    if a.sin_instalador:
        return
    makensis = shutil.which("makensis")
    if not makensis:
        sys.exit("Falta makensis (NSIS) para crear el instalador; usa --sin-instalador para solo la carpeta.")
    exe = a.salida / f"Kaze-{version}-setup.exe"
    subprocess.run([
        makensis, "-V2", "-INPUTCHARSET", "UTF8",
        f"-DVERSION={version}", f"-DFIREFOX={firefox}", f"-DBUILD={a.build}",
        f"-DORIGEN={app}", f"-DSALIDA={exe}", f"-DICONO={MARCA / 'firefox.ico'}",
        str(DESKTOP / "windows" / "kaze.nsi"),
    ], check=True)
    print(f"Instalador: {exe} ({exe.stat().st_size / 1048576:.0f} MB)")


if __name__ == "__main__":
    main()
