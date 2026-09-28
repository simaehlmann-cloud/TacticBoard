#!/usr/bin/env python3
"""Erzeugt den vollstaendigen iOS-Icon-Satz und das Startbild nach res/ios/.

    python3 tools/make_ios_icons.py

Wird im Workflow ios.yml aufgerufen. Die erzeugten Dateien gehoeren NICHT
ins Repo (siehe .gitignore) - sie entstehen bei jedem Build neu aus den
Mastern, genau wie bei Android.

Quellen (liegen im Repo-Hauptverzeichnis):
  icon_store.png  1024x1024 RGB   - vollflaechiges Motiv, wird zum App-Icon
  icon_fg.png     1024x1024 RGBA  - freigestelltes Motiv, fuer das Startbild
  icon_bg.png     1024x1024 RGB   - Rasen, liefert die Farbe des Startbilds

Warum ein eigenes Skript:
1. iOS-Icons duerfen keinen Alphakanal haben. App Store Connect weist
   solche Uploads erst nach der Verarbeitung zurueck - dann ist die
   Build-Nummer schon verbraucht.
2. iOS rundet die Ecken selbst; die Quelle muss ein volles Quadrat sein.
   icon_store.png ist genau das.
3. Das Startbild entsteht bei cordova-ios aus einem Storyboard. Ein
   quadratisches Bild von 2732x2732 deckt iPhone, iPad und beide Lagen ab.
"""

import os
import sys

try:
    from PIL import Image
except ImportError:
    sys.exit("Pillow fehlt.  Abhilfe:  python3 -m pip install Pillow")

ICON_QUELLE = "icon_store.png"
FG_QUELLE = "icon_fg.png"
BG_QUELLE = "icon_bg.png"
ZIEL = os.path.join("res", "ios")

# Von cordova-ios erwarteter Satz fuer iPhone UND iPad
# (76/152/167 sind die iPad-Groessen).
GROESSEN = [20, 29, 40, 58, 60, 76, 80, 87, 120, 152, 167, 180, 1024]

SPLASH_KANTE = 2732
SPLASH_ICON_ANTEIL = 0.45   # icon_fg hat eigenen Rand (Adaptive-Sicherheitszone)


def pruefe_quelle(pfad, kante, alpha_noetig=False):
    if not os.path.exists(pfad):
        sys.exit("%s fehlt. Das Skript laeuft im Repo-Hauptverzeichnis." % pfad)
    bild = Image.open(pfad)
    if bild.size != (kante, kante):
        sys.exit("%s ist %dx%d, erwartet %dx%d." % (pfad, bild.width, bild.height, kante, kante))
    if alpha_noetig and bild.mode != "RGBA":
        sys.exit("%s braucht einen Alphakanal (ist %s)." % (pfad, bild.mode))
    return bild


def ohne_alpha(bild, farbe=(0x4C, 0xAF, 0x50)):
    """Legt das Bild auf eine deckende Flaeche und entfernt den Alphakanal."""
    if bild.mode == "RGB":
        return bild
    rgba = bild.convert("RGBA")
    flaeche = Image.new("RGB", rgba.size, farbe)
    flaeche.paste(rgba, mask=rgba.split()[3])
    return flaeche


def main():
    icon = ohne_alpha(pruefe_quelle(ICON_QUELLE, 1024))
    fg = pruefe_quelle(FG_QUELLE, 1024, alpha_noetig=True).convert("RGBA")
    bg = pruefe_quelle(BG_QUELLE, 1024).convert("RGB")

    os.makedirs(ZIEL, exist_ok=True)

    for kante in GROESSEN:
        ziel = os.path.join(ZIEL, "icon-%d.png" % kante)
        icon.resize((kante, kante), Image.LANCZOS).save(ziel, "PNG")
        print("  %s  (%dx%d)" % (ziel, kante, kante))

    # ---- Startbild: Rasen-Hintergrund, Motiv mittig ----
    # Der Rasen wird auf die volle Kante gezogen; seine Streifen bleiben
    # dabei erhalten, und auf jedem Geraet schneidet iOS nur Rand ab.
    splash = bg.resize((SPLASH_KANTE, SPLASH_KANTE), Image.LANCZOS)
    motiv_kante = int(SPLASH_KANTE * SPLASH_ICON_ANTEIL)
    motiv = fg.resize((motiv_kante, motiv_kante), Image.LANCZOS)
    versatz = (SPLASH_KANTE - motiv_kante) // 2
    splash.paste(motiv, (versatz, versatz), motiv)
    splash_ziel = os.path.join(ZIEL, "Default@2x~universal~anyany.png")
    splash.save(splash_ziel, "PNG")
    print("  %s  (%dx%d)" % (splash_ziel, SPLASH_KANTE, SPLASH_KANTE))

    # ---- Kontrolle ----
    fehler = []
    for kante in GROESSEN:
        pruef = Image.open(os.path.join(ZIEL, "icon-%d.png" % kante))
        if pruef.mode != "RGB":
            fehler.append("icon-%d.png hat Modus %s" % (kante, pruef.mode))
        if pruef.size != (kante, kante):
            fehler.append("icon-%d.png ist %dx%d" % (kante, pruef.width, pruef.height))
    if fehler:
        sys.exit("Kontrolle fehlgeschlagen:\n  " + "\n  ".join(fehler))

    print("\n%d Icons und 1 Startbild geschrieben, alle ohne Alphakanal." % len(GROESSEN))


if __name__ == "__main__":
    main()
