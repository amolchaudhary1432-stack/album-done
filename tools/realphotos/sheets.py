"""Contact sheets of a raw folder, for a person (Claude) to choose an album the way a couple would
leaf through the photographer's photos: event by event (the sub-folders, in the photographer's
order), in the order they were taken, 30 numbered photos a sheet.

    python3 sheets.py RAW_DIR OUT_DIR [--index-only]

Writes OUT_DIR/index.tsv (number, folder/file, capture time) and OUT_DIR/sheet-NNN.jpg. Picks are
written back as numbers. Prints counts only.
"""
import os
import re
import sys

from PIL import Image, ImageDraw, ImageFont, ImageOps

PHOTO = (".jpg", ".jpeg", ".png", ".webp", ".heic", ".heif")
COLS, ROWS, CELL, LABEL = 6, 5, 300, 26


def taken(path):
    try:
        with Image.open(path) as im:
            ex = im.getexif()
            t = ex.get_ifd(0x8769).get(0x9003) or ex.get(0x0132)
            return str(t or "")
    except Exception:
        return ""


def folder_key(name):
    """Numbered folders by number (as the app orders events), the rest after, by name."""
    m = re.match(r"\s*(\d+)", name)
    return (0, int(m[1]), name.lower()) if m else (1, 0, name.lower())


def main(raw, out):
    os.makedirs(out, exist_ok=True)
    photos = []
    for root, _, files in os.walk(raw):
        for f in files:
            if f.lower().endswith(PHOTO) and not f.startswith("._"):
                rel = os.path.relpath(os.path.join(root, f), raw)
                photos.append((os.path.dirname(rel), rel, taken(os.path.join(root, f))))
    photos.sort(key=lambda p: (folder_key(p[0]), p[2] or "~", p[1].lower()))
    with open(os.path.join(out, "index.tsv"), "w") as fh:
        for i, (_, rel, t) in enumerate(photos, 1):
            fh.write(f"{i}\t{rel}\t{t}\n")
    if "--index-only" in sys.argv:
        print(f"{len(photos)} photos indexed")
        return
    font = ImageFont.load_default(size=18) if hasattr(ImageFont, "load_default") else None
    per = COLS * ROWS
    sheets = 0
    for start in range(0, len(photos), per):
        part = photos[start:start + per]
        sheet = Image.new("RGB", (COLS * CELL, ROWS * (CELL + LABEL) + 40), "white")
        d = ImageDraw.Draw(sheet)
        events = sorted({p[0] or "(top)" for p in part})
        d.text((8, 8), f"Sheet {start // per + 1}: photos {start + 1}-{start + len(part)} of {len(photos)} | {', '.join(events)}",
               fill="black", font=font)
        for k, (folder, rel, _) in enumerate(part):
            x, y = (k % COLS) * CELL, 40 + (k // COLS) * (CELL + LABEL)
            try:
                with Image.open(os.path.join(raw, rel)) as im:
                    im = ImageOps.exif_transpose(im).convert("RGB")
                    im.thumbnail((CELL - 6, CELL - 6))
                    sheet.paste(im, (x + (CELL - im.width) // 2, y + (CELL - im.height) // 2))
            except Exception:
                d.text((x + 10, y + CELL // 2), "unreadable", fill="red", font=font)
            d.text((x + 6, y + CELL + 2), f"#{start + k + 1}  {(folder or '')[:22]}", fill="black", font=font)
        sheets += 1
        sheet.save(os.path.join(out, f"sheet-{sheets:03d}.jpg"), quality=82)
    print(f"{len(photos)} photos on {sheets} contact sheets")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
