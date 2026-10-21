#!/usr/bin/env python3
# This Source Code Form is subject to the terms of the Mozilla Public
# License, v. 2.0. If a copy of the MPL was not distributed with this
# file, You can obtain one at http://mozilla.org/MPL/2.0/.
"""
Descarga Zen Browser en la versión de desktop/zen-version.txt y le aplica la capa Kaze.

Uso (desde la raíz del repo):
    python desktop/scripts/sync_zen.py            # descarga (si hace falta) y aplica
    python desktop/scripts/sync_zen.py --limpio   # borra desktop/build/zen y empieza de cero

Resultado: desktop/build/zen/ queda listo para compilar con los comandos de Zen.
"""
import argparse
import json
import re
import shutil
import subprocess
import sys
from pathlib import Path

RAIZ = Path(__file__).resolve().parents[2]
DESKTOP = RAIZ / "desktop"
ZEN = DESKTOP / "build" / "zen"
ZEN_REPO = "https://github.com/zen-browser/desktop.git"


def run(*cmd: str, cwd: Path | None = None) -> None:
    print("  $", " ".join(cmd))
    subprocess.run(cmd, cwd=cwd, check=True)


def descargar_zen(version: str) -> None:
    if (ZEN / ".git").exists():
        actual = subprocess.run(
            ["git", "describe", "--tags", "--exact-match"],
            cwd=ZEN, capture_output=True, text=True,
        ).stdout.strip()
        if actual == version:
            print(f"· Zen {version} ya está descargado; restaurando archivos originales")
            run("git", "reset", "--hard", "-q", cwd=ZEN)
            run("git", "clean", "-fdq", "-e", "engine", "-e", "node_modules", cwd=ZEN)
            return
        print(f"· Cambiando de Zen {actual or '?'} a {version}")
        run("git", "fetch", "--depth", "1", "origin", "tag", version, cwd=ZEN)
        run("git", "checkout", "-q", "-f", version, cwd=ZEN)
        run("git", "clean", "-fdq", "-e", "engine", "-e", "node_modules", cwd=ZEN)
        return
    print(f"· Descargando Zen {version}")
    ZEN.parent.mkdir(parents=True, exist_ok=True)
    run("git", "clone", "--depth", "1", "--branch", version, ZEN_REPO, str(ZEN))


def fusionar(base: dict, capa: dict) -> dict:
    """Fusión profunda; un valor null en la capa elimina la clave."""
    for clave, valor in capa.items():
        if valor is None:
            base.pop(clave, None)
        elif isinstance(valor, dict) and isinstance(base.get(clave), dict):
            fusionar(base[clave], valor)
        else:
            base[clave] = valor
    return base


def aplicar_surfer_json() -> None:
    print("· Nombre e identidad (surfer.json)")
    ruta = ZEN / "surfer.json"
    surfer = json.loads(ruta.read_text(encoding="utf-8"))
    capa = json.loads((DESKTOP / "kaze.json").read_text(encoding="utf-8"))
    ruta.write_text(json.dumps(fusionar(surfer, capa), indent=2) + "\n", encoding="utf-8")


def aplicar_branding() -> None:
    print("· Íconos y logo")
    origen = RAIZ / "branding" / "desktop"
    if not origen.exists():
        sys.exit("Faltan los íconos: corre primero  python branding/generar_iconos.py")
    for marca in ("release", "twilight"):
        destino = ZEN / "configs" / "branding" / marca
        shutil.copytree(origen, destino, dirs_exist_ok=True)


NOMBRE_PREF = re.compile(r"^- name:\s*(\S+)", re.M)


def aplicar_prefs() -> None:
    print("· Preferencias de rendimiento")
    archivos_kaze = sorted((DESKTOP / "prefs").rglob("*.yaml"))
    nuestras = {n for f in archivos_kaze for n in NOMBRE_PREF.findall(f.read_text(encoding="utf-8"))}

    # Quitar de Zen las preferencias que Kaze sobrescribe, para no tener duplicados.
    quitadas = 0
    for f in (ZEN / "prefs").rglob("*.yaml"):
        texto = f.read_text(encoding="utf-8")
        bloques = re.split(r"(?m)^(?=- name:)", texto)
        conservados = []
        for b in bloques:
            m = NOMBRE_PREF.match(b)
            if m and m.group(1) in nuestras:
                quitadas += 1
                continue
            conservados.append(b)
        if len(conservados) != len(bloques):
            f.write_text("".join(conservados), encoding="utf-8")
    print(f"    {len(nuestras)} preferencias de Kaze ({quitadas} reemplazan valores de Zen)")

    destino = ZEN / "prefs" / "kaze"
    destino.mkdir(parents=True, exist_ok=True)
    for f in archivos_kaze:
        shutil.copy2(f, destino / f.name)


def aplicar_parches() -> None:
    parches = sorted((DESKTOP / "patches").glob("*.patch"))
    if not parches:
        return
    print(f"· Aplicando {len(parches)} parche(s) de Kaze")
    for p in parches:
        run("git", "apply", "--whitespace=nowarn", str(p), cwd=ZEN)


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--limpio", action="store_true", help="borrar desktop/build/zen antes de empezar")
    args = ap.parse_args()

    version = (DESKTOP / "zen-version.txt").read_text().strip()
    if args.limpio and ZEN.exists():
        shutil.rmtree(ZEN)

    descargar_zen(version)
    aplicar_surfer_json()
    aplicar_branding()
    aplicar_prefs()
    aplicar_parches()

    print(f"\nListo: {ZEN.relative_to(RAIZ)} es Zen {version} con la capa Kaze.")
    print("Siguiente paso:  cd desktop/build/zen  &&  npm install  &&  npm run init  &&  npm run build")


if __name__ == "__main__":
    main()
