"""Plate mark with VBT set horizontally instead of on the arc."""

import os

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib.font_manager import FontProperties
from matplotlib.patches import Circle, PathPatch
from matplotlib.textpath import TextPath
from matplotlib.transforms import Affine2D
from PIL import Image

OUT = "/mnt/d/tmp/VBTdroid/logo/plate_text"
os.makedirs(OUT, exist_ok=True)

SIZE = 1024
CX = CY = SIZE / 2
R_PLATE = 300
R_RING = 258
W_RING = 8
R_HOLE = 50
W_HOLE = 8

BOLD = FontProperties(family="Ubuntu", weight="bold")
CAP_RATIO = 0.693
TEXT = "VBT"
CAP = 81
GAP = 3
TEXT_CY = 662.0   # matplotlib y grows upward: the word sits above the hub

PALETTES = {
    "white": dict(plate="#D5202A", outline="#12171B", ring="#FFFFFF",
                  hole="#FFFFFF", hole_outline="#12171B", text="#FFFFFF"),
    "black": dict(plate="#D5202A", outline="#12171B", ring="#FFFFFF",
                  hole="#FFFFFF", hole_outline="#12171B", text="#12171B"),
    "white_dark": dict(plate="#D5202A", outline="#E9EEF1", ring="#FFFFFF",
                       hole="#0A0E11", hole_outline="#E9EEF1", text="#FFFFFF"),
}


def glyphs(text, cap):
    size = cap / CAP_RATIO
    out = []
    for ch in text:
        path = TextPath((0, 0), ch, size=size, prop=BOLD)
        x0, y0, x1, y1 = path.get_extents().extents
        out.append({"path": path, "w": x1 - x0, "h": y1 - y0,
                    "cx": (x0 + x1) / 2, "cy": (y0 + y1) / 2})
    return out


def draw(name, palette):
    fig = plt.figure(figsize=(SIZE / 100, SIZE / 100), dpi=100)
    ax = fig.add_axes([0, 0, 1, 1])
    ax.set_xlim(0, SIZE)
    ax.set_ylim(0, SIZE)
    ax.set_aspect("equal")
    ax.axis("off")

    ax.add_patch(Circle((CX, CY), R_PLATE, facecolor=palette["plate"],
                        edgecolor=palette["outline"], lw=10, zorder=1))
    ax.add_patch(Circle((CX, CY), R_RING, facecolor="none",
                        edgecolor=palette["ring"], lw=W_RING, zorder=2))
    ax.add_patch(Circle((CX, CY), R_HOLE, facecolor=palette["hole"],
                        edgecolor=palette["hole_outline"], lw=W_HOLE, zorder=2))

    glyphs_list = glyphs(TEXT, CAP)
    total = sum(g["w"] for g in glyphs_list) + GAP * (len(glyphs_list) - 1)
    x = CX - total / 2
    for glyph in glyphs_list:
        centre_x = x + glyph["w"] / 2
        x += glyph["w"] + GAP
        transform = (
            Affine2D()
            .translate(-glyph["cx"], -glyph["cy"])
            .translate(centre_x, TEXT_CY)
        )
        ax.add_patch(PathPatch(
            transform.transform_path(glyph["path"]),
            facecolor=palette["text"], edgecolor="none", lw=0, zorder=5))

    fig.savefig(os.path.join(OUT, f"plate_flat_{name}.png"), dpi=100, transparent=True)
    plt.close(fig)


if __name__ == "__main__":
    for name, palette in PALETTES.items():
        draw(name, palette)
        print("wrote", name)

    tiles = [Image.open(os.path.join(OUT, f"plate_flat_{n}.png")).convert("RGBA").resize((420, 420))
             for n in PALETTES]
    sheet = Image.new("RGB", (420 * len(tiles), 420), "#7A7A7A")
    for index, tile in enumerate(tiles):
        sheet.paste(tile, (index * 420, 0), tile)
    sheet.save("/tmp/opencode/flat_compare.png")
