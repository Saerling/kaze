#!/usr/bin/env python3
# This Source Code Form is subject to the terms of the Mozilla Public
# License, v. 2.0. If a copy of the MPL was not distributed with this
# file, You can obtain one at http://mozilla.org/MPL/2.0/.
"""
Prueba la interfaz de Kaze en un Firefox normal, sin compilar nada.

    python desktop/dev/instalar.py "C:\\Program Files\\Mozilla Firefox"     (Windows, como administrador)
    python desktop/dev/instalar.py /ruta/a/firefox                          (Linux)
    python desktop/dev/instalar.py /ruta/a/firefox --quitar                 (deshacer)

Copia a la carpeta de Firefox el cargador de Kaze (autoconfig), que registra
chrome://kaze/ apuntando a este repositorio, y las preferencias de Kaze.
Al editar kaze.js/kaze.css solo hace falta reiniciar Firefox para ver los cambios.

Úsalo con un perfil aparte:  firefox -P   (crea uno llamado "kaze").
"""
import argparse
import sys
from pathlib import Path

AQUI = Path(__file__).resolve().parent
sys.path.insert(0, str(AQUI.parent / "scripts"))
from kaze_comun import KAZE, cargador, preferencias_cfg  # noqa: E402

LOADER_PREF = "defaults/pref/kaze-dev-loader.js"


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("firefox", help="carpeta de instalación de Firefox (donde está firefox o firefox.exe)")
    ap.add_argument("--quitar", action="store_true")
    a = ap.parse_args()

    ff = Path(a.firefox)
    if not (ff / "omni.ja").exists():
        sys.exit(f"No parece una instalación de Firefox: {ff}")
    archivos = [ff / "kaze-dev.cfg", ff / LOADER_PREF]

    if a.quitar:
        for f in archivos:
            f.unlink(missing_ok=True)
        print("Cargador de Kaze quitado.")
        return

    cfg = cargador(str(KAZE)) + preferencias_cfg(windows=sys.platform == "win32")
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
