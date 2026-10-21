#!/usr/bin/env python3
# This Source Code Form is subject to the terms of the Mozilla Public
# License, v. 2.0. If a copy of the MPL was not distributed with this
# file, You can obtain one at http://mozilla.org/MPL/2.0/.
"""
Comprobación rápida de Kaze escritorio, sin descargar ni compilar Firefox:

  1. Que cada parche de src/ aplique sobre la versión de Firefox de surfer.json
     (baja solo los archivos que tocan los parches, desde hg.mozilla.org).
  2. Que kaze.js no tenga errores de sintaxis (con Node).
  3. Que estén todos los archivos de marca que necesita surfer.

    python desktop/scripts/check.py
    python desktop/scripts/check.py --firefox-src /ruta/a/firefox   (usar un código fuente local)

Útil antes de compilar (que tarda horas) y al subir de versión de Firefox.
"""
import argparse
import json
import re
import shutil
import subprocess
import sys
import tempfile
import urllib.request
from pathlib import Path

DESKTOP = Path(__file__).resolve().parents[1]
SRC = DESKTOP / "src"
BRANDING = DESKTOP / "configs" / "branding" / "release"
NEEDED_BRANDING = ["logo.png", "logo-mac.png", "firefox.ico", "firefox64.ico", "MacOSInstaller.svg"] + [
    f"logo{n}.png" for n in (16, 22, 24, 32, 48, 64, 128, 256)
]

ok = True


def fail(msg: str) -> None:
    global ok
    ok = False
    print("  ✗", msg)


def firefox_tag() -> str:
    version = json.loads((DESKTOP / "surfer.json").read_text())["version"]["version"]
    return "FIREFOX_" + version.replace(".", "_") + "_RELEASE"


def fetch(tag: str, path: str) -> bytes:
    url = f"https://hg.mozilla.org/releases/mozilla-release/raw-file/{tag}/{path}"
    with urllib.request.urlopen(url, timeout=60) as r:
        return r.read()


def check_patches(local: Path | None) -> None:
    patches = sorted(SRC.rglob("*.patch"))
    tag = firefox_tag()
    print(f"Parches ({len(patches)}) contra Firefox {tag}:")
    with tempfile.TemporaryDirectory() as tmp:
        root = Path(tmp)
        for patch in patches:
            targets = re.findall(r"^\+\+\+ b/(\S+)", patch.read_text(encoding="utf-8"), re.M)
            for t in targets:
                dest = root / t
                if dest.exists():
                    continue
                dest.parent.mkdir(parents=True, exist_ok=True)
                if local:
                    shutil.copy(local / t, dest)
                else:
                    dest.write_bytes(fetch(tag, t))
            r = subprocess.run(
                ["git", "apply", "--check", "--ignore-space-change", "--ignore-whitespace", str(patch)],
                cwd=root, capture_output=True, text=True,
            )
            rel = patch.relative_to(DESKTOP)
            if r.returncode == 0:
                print("  ✓", rel)
                subprocess.run(["git", "apply", "--ignore-space-change", "--ignore-whitespace", str(patch)], cwd=root)
            else:
                fail(f"{rel}\n{r.stderr.strip()}")


def check_js() -> None:
    print("Sintaxis de JavaScript:")
    node = shutil.which("node")
    if not node:
        print("  (Node no está instalado; se omite)")
        return
    for js in sorted(SRC.rglob("*.js")):
        if js.name.endswith("-prefs.js"):
            continue  # usa el preprocesador de Mozilla (#ifdef)
        r = subprocess.run([node, "--check", str(js)], capture_output=True, text=True)
        if r.returncode == 0:
            print("  ✓", js.relative_to(DESKTOP))
        else:
            fail(f"{js.relative_to(DESKTOP)}\n{r.stderr.strip()}")


def check_branding() -> None:
    print("Marca (íconos):")
    missing = [f for f in NEEDED_BRANDING if not (BRANDING / f).exists()]
    if missing:
        fail("faltan " + ", ".join(missing) + " → python branding/generar_iconos.py")
    else:
        print(f"  ✓ {len(NEEDED_BRANDING)} archivos")


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--firefox-src", type=Path, help="código fuente local de Firefox (en vez de descargar)")
    a = ap.parse_args()
    check_patches(a.firefox_src)
    check_js()
    check_branding()
    print("\nTodo bien." if ok else "\nHay problemas.")
    sys.exit(0 if ok else 1)


if __name__ == "__main__":
    main()
