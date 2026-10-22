# This Source Code Form is subject to the terms of the Mozilla Public
# License, v. 2.0. If a copy of the MPL was not distributed with this
# file, You can obtain one at http://mozilla.org/MPL/2.0/.
"""Piezas compartidas por el cargador de desarrollo y el empaquetado de Windows."""
import re
from pathlib import Path

DESKTOP = Path(__file__).resolve().parent.parent
KAZE = DESKTOP / "src" / "browser" / "kaze"


def prefs_planas(texto: str, windows: bool) -> str:
    """Quita el preprocesador (#ifdef) de kaze-prefs.js para usarlo fuera de la compilación."""
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


def preferencias_cfg(windows: bool, extra: dict | None = None) -> str:
    """kaze-prefs.js como defaultPref(...) para el .cfg (corre después de las de Firefox y las sobrescribe)."""
    prefs = prefs_planas((KAZE / "kaze-prefs.js").read_text(encoding="utf-8"), windows)
    prefs = re.sub(r"^pref\(", "defaultPref(", prefs, flags=re.M)
    for nombre, valor in (extra or {}).items():
        v = str(valor).lower() if isinstance(valor, bool) else valor if isinstance(valor, int) else f'"{valor}"'
        prefs += f'defaultPref("{nombre}", {v});\n'
    return "\n// Preferencias de Kaze (desktop/src/browser/kaze/kaze-prefs.js)\n" + prefs


def cargador(kaze_dir: str = "") -> str:
    """El cargador autoconfig; con kaze_dir vacío busca la carpeta "kaze" junto al ejecutable."""
    texto = (KAZE / "cargador.cfg").read_text(encoding="utf-8")
    return texto.replace("@KAZE_DIR@", kaze_dir.replace("\\", "\\\\"))
