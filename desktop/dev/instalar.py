#!/usr/bin/env python3
# This Source Code Form is subject to the terms of the Mozilla Public
# License, v. 2.0. If a copy of the MPL was not distributed with this
# file, You can obtain one at http://mozilla.org/MPL/2.0/.
"""
Prueba la interfaz de Kaze en un Firefox normal, sin compilar nada.

    python desktop/dev/instalar.py "C:\\Program Files\\Mozilla Firefox"     (Windows, como administrador)
    python desktop/dev/instalar.py /ruta/a/firefox                          (Linux)
    python desktop/dev/instalar.py /ruta/a/firefox --quitar                 (deshacer)

Copia a la carpeta de Firefox un cargador (autoconfig) que inyecta kaze.js y kaze.css
desde este repositorio, y las preferencias de Kaze. Al editar kaze.js/kaze.css solo
hace falta reiniciar Firefox para ver los cambios.

Úsalo con un perfil aparte:  firefox -P   (crea uno llamado "kaze").
"""
import argparse
import re
import sys
from pathlib import Path

AQUI = Path(__file__).resolve().parent
KAZE = AQUI.parent / "src" / "browser" / "kaze"


def prefs_planas(texto: str, windows: bool) -> str:
    """Quita el preprocesador (#ifdef) de kaze-prefs.js para usarlo como archivo de defaults."""
    out, activo = [], [True]
    for linea in texto.splitlines():
        s = linea.strip()
        if s.startswith("#ifdef"):
            activo.append(activo[-1] and (s.split()[1] == "XP_WIN") == windows)
        elif s.startswith("#endif"):
            activo.pop()
        elif s.startswith("#"):
            continue
        elif activo[-1]:
            out.append(linea)
    return "\n".join(out) + "\n"


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("firefox", help="carpeta de instalación de Firefox (donde está firefox o firefox.exe)")
    ap.add_argument("--quitar", action="store_true")
    a = ap.parse_args()

    ff = Path(a.firefox)
    if not (ff / "omni.ja").exists():
        sys.exit(f"No parece una instalación de Firefox: {ff}")
    archivos = [ff / "kaze-dev.cfg", ff / "defaults/pref/kaze-dev-loader.js"]

    if a.quitar:
        for f in archivos + [ff / "defaults/pref/kaze-prefs.js"]:
            f.unlink(missing_ok=True)
        print("Cargador de Kaze quitado.")
        return

    windows = sys.platform == "win32"
    # Las preferencias se aplican desde el .cfg (corre después de las de Firefox, así que las sobrescribe).
    prefs = re.sub(r"^pref\(", "defaultPref(", prefs_planas((KAZE / "kaze-prefs.js").read_text(encoding="utf-8"), windows), flags=re.M)
    cfg = (AQUI / "kaze-dev.cfg").read_text(encoding="utf-8").replace("@KAZE_DIR@", str(KAZE).replace("\\", "\\\\"))
    cfg += "\n// Preferencias de Kaze (desktop/src/browser/kaze/kaze-prefs.js)\n" + prefs
    archivos[0].write_text(cfg, encoding="utf-8")
    archivos[1].write_text(
        'pref("general.config.filename", "kaze-dev.cfg");\n'
        'pref("general.config.obscure_value", 0);\n'
        'pref("general.config.sandbox_enabled", false);\n',
        encoding="utf-8",
    )
    print(f"Listo. Abre Firefox ({ff}) con un perfil nuevo para probar Kaze.")


if __name__ == "__main__":
    main()
