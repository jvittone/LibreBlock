"""Adapte les captures d'écran au format Google Play.

Play refuse les images dont le grand côté dépasse le double du petit : les captures d'un téléphone récent
(1080 × 2424, rapport 2,24) sont élargies à 1216 × 2424 en prolongeant la couleur des bords de chaque ligne.
Entrée : docs/store/fr et docs/store/en. Sortie : docs/store/play/fr et docs/store/play/en.
"""
from pathlib import Path

from PIL import Image

STORE = Path(__file__).resolve().parent.parent / "docs" / "store"
MAX_RATIO = 1.995  # un peu sous 2, par sécurité

for lang in ("fr", "en"):
    out_dir = STORE / "play" / lang
    out_dir.mkdir(parents=True, exist_ok=True)
    for src in sorted((STORE / lang).glob("*.png")):
        img = Image.open(src).convert("RGB")
        w, h = img.size
        target = max(w, -(-int(h / MAX_RATIO) // 2) * 2)
        pad = (target - w) // 2
        canvas = Image.new("RGB", (target, h))
        canvas.paste(img, (pad, 0))
        # Prolonge le pixel de bord de chaque ligne, pour un fond continu (barres, cartes, fond).
        left = img.crop((0, 0, 1, h)).resize((pad, h))
        right = img.crop((w - 1, 0, w, h)).resize((target - w - pad, h))
        canvas.paste(left, (0, 0))
        canvas.paste(right, (pad + w, 0))
        dest = out_dir / src.name
        canvas.save(dest, optimize=True)
        print(dest.relative_to(STORE), canvas.size, round(h / target, 3))
