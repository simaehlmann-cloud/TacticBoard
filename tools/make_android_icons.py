#!/usr/bin/env python3
"""
Erzeugt aus den Master-Icons im Repo-Hauptverzeichnis alle Dichte-Varianten,
die config.xml unter res/android/ erwartet.

  image.png    512x512  RGB   -> res/android/icon-<px>.png  (Launcher vor Android 8)
  icon_fg.png 1024x1024 RGBA  -> res/android/fg-<px>.png    (Adaptive-Vordergrund)
  icon_bg.png 1024x1024 RGB   -> res/android/bg-<px>.png    (Adaptive-Hintergrund)

Wird im Workflow vor "cordova platform add" ausgefuehrt, damit die zehn
Dichte-Dateien nicht einzeln im Repo liegen muessen.

Die Pruefungen am Anfang sind Absicht: genau hier ist frueher unbemerkt ein
JPEG mit .png-Endung durchgerutscht, und der Adaptive-Vordergrund hatte keinen
Alphakanal. Beides bricht den Build jetzt ab, statt still ein kaputtes Icon
in den Store zu schieben.
"""
import os
import sys

try:
    from PIL import Image
except ImportError:
    sys.exit("FEHLER: Pillow fehlt. Im Workflow: pip install --quiet pillow")

# Legacy-Launcher: 48dp. Adaptive-Ebenen: 108dp.
LEGACY = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
ADAPTIVE = {"mdpi": 108, "hdpi": 162, "xhdpi": 216, "xxhdpi": 324, "xxxhdpi": 432}

OUT = os.path.join("res", "android")


def load(path, want_alpha, min_px):
    if not os.path.isfile(path):
        sys.exit(f"FEHLER: {path} fehlt im Repo-Hauptverzeichnis.")
    im = Image.open(path)
    if im.format != "PNG":
        sys.exit(f"FEHLER: {path} ist kein PNG, sondern {im.format}. "
                 "Android kann das nicht als Ressource verarbeiten.")
    if im.size[0] != im.size[1]:
        sys.exit(f"FEHLER: {path} ist nicht quadratisch ({im.size[0]}x{im.size[1]}).")
    if im.size[0] < min_px:
        sys.exit(f"FEHLER: {path} ist nur {im.size[0]} px gross, "
                 f"mindestens {min_px} px noetig.")
    has_alpha = "A" in im.getbands()
    if want_alpha and not has_alpha:
        sys.exit(f"FEHLER: {path} hat keinen Alphakanal. Der Adaptive-Vordergrund "
                 "muss transparent sein, sonst liegt ein farbiger Kasten ueber dem "
                 "Hintergrund.")
    print(f"  ok  {path:18s} {im.size[0]}x{im.size[1]}  {im.mode}  Alpha={'ja' if has_alpha else 'nein'}")
    return im


def flatten(im):
    """Alphakanal auf Weiss legen - Legacy-Icons und der Hintergrund
       duerfen keine Transparenz haben."""
    if "A" not in im.getbands():
        return im.convert("RGB")
    flat = Image.new("RGB", im.size, (255, 255, 255))
    flat.paste(im, mask=im.convert("RGBA").split()[3])
    return flat


def main():
    print("Master-Icons pruefen:")
    legacy = flatten(load("image.png", want_alpha=False, min_px=192))
    fg = load("icon_fg.png", want_alpha=True, min_px=432).convert("RGBA")
    bg = flatten(load("icon_bg.png", want_alpha=False, min_px=432))

    os.makedirs(OUT, exist_ok=True)
    written = 0
    print("\nDichte-Dateien erzeugen:")
    for density in LEGACY:
        for src, prefix, sizes in ((legacy, "icon", LEGACY),
                                   (fg, "fg", ADAPTIVE),
                                   (bg, "bg", ADAPTIVE)):
            px = sizes[density]
            path = os.path.join(OUT, f"{prefix}-{px}.png")
            src.resize((px, px), Image.LANCZOS).save(path, "PNG", optimize=True)
            written += 1
        print(f"  {density:8s} icon-{LEGACY[density]}  fg-{ADAPTIVE[density]}  bg-{ADAPTIVE[density]}")

    print(f"\n{written} Dateien in {OUT}/ geschrieben.")


if __name__ == "__main__":
    main()
