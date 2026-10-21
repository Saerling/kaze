#!/usr/bin/env python3
# This Source Code Form is subject to the terms of the Mozilla Public
# License, v. 2.0. If a copy of the MPL was not distributed with this
# file, You can obtain one at http://mozilla.org/MPL/2.0/.
"""
Genera todos los íconos de Kaze a partir de branding/kaze-logo.svg.

  pip install cairosvg pillow
  python branding/generar_iconos.py

Salida:
  desktop/configs/branding/release/  -> los archivos que espera surfer para la marca de Windows/Linux
  android/app/src/main/res/mipmap-*/ic_launcher.png (+ round)
"""
from io import BytesIO
from pathlib import Path

import cairosvg
from PIL import Image

RAIZ = Path(__file__).resolve().parent.parent
SVG = (RAIZ / "branding" / "kaze-logo.svg").read_bytes()
DESKTOP = RAIZ / "desktop" / "configs" / "branding" / "release"
ANDROID_RES = RAIZ / "android" / "app" / "src" / "main" / "res"


def render(tam: int) -> Image.Image:
    png = cairosvg.svg2png(bytestring=SVG, output_width=tam, output_height=tam)
    return Image.open(BytesIO(png)).convert("RGBA")


def con_margen(lienzo: int, logo: int) -> Image.Image:
    img = Image.new("RGBA", (lienzo, lienzo), (0, 0, 0, 0))
    off = (lienzo - logo) // 2
    img.alpha_composite(render(logo), (off, off))
    return img


def escritorio() -> None:
    (DESKTOP / "content").mkdir(parents=True, exist_ok=True)
    for t in (16, 22, 24, 32, 48, 64, 128, 256, 512, 1024):
        render(t).save(DESKTOP / f"logo{t}.png")
    render(1024).save(DESKTOP / "logo.png")
    con_margen(1024, 820).save(DESKTOP / "logo-mac.png")
    # Mosaicos del menú Inicio de Windows
    con_margen(270, 180).save(DESKTOP / "PrivateBrowsing_150.png")
    con_margen(126, 84).save(DESKTOP / "PrivateBrowsing_70.png")
    con_margen(1042, 700).save(DESKTOP / "VisualElements_150.png")
    con_margen(1042, 700).save(DESKTOP / "VisualElements_70.png")
    # Íconos .ico de Windows
    tams = [(16, 16), (32, 32), (48, 48), (64, 64), (256, 256)]
    for nombre in ("firefox.ico", "firefox64.ico"):
        render(256).save(DESKTOP / nombre, sizes=tams)
    # Logo de about:
    render(192).save(DESKTOP / "content" / "about-logo.png")
    render(384).save(DESKTOP / "content" / "about-logo@2x.png")
    (DESKTOP / "content" / "about-logo.svg").write_bytes(SVG)
    (DESKTOP / "MacOSInstaller.svg").write_bytes(SVG)


def android() -> None:
    densidades = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
    for d, t in densidades.items():
        carpeta = ANDROID_RES / f"mipmap-{d}"
        carpeta.mkdir(parents=True, exist_ok=True)
        icono = con_margen(t, int(t * 0.92))
        icono.save(carpeta / "ic_launcher.png")
        icono.save(carpeta / "ic_launcher_round.png")


if __name__ == "__main__":
    escritorio()
    android()
    print("Íconos generados en", DESKTOP, "y", ANDROID_RES)
