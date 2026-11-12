"""VBT-only plate marks: the word on the top arc and on the bottom arc, larger and closer in."""

import math
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

RED = "#D5202A"
INK = "#12171B"
WHITE = "#FFFFFF"

SIZE = 1024
CX = CY = SIZE / 2
R_PLATE = 300
R_RING = 258
W_RING = 8
R_HOLE = 50
W_HOLE = 8

CAP_RATIO = 0.693
BOLD = FontProperties(family="Ubuntu", weight="bold")
GAP = 3
BASE_R = 112      # baseline radius: closer to the centre than the full wordmark
CAP = 81

TEXT = "VBT"


def glyphs_for(text, cap):
    size = cap / CAP_RATIO
    out = []
    for ch in text:
        path = TextPath((0, 0), ch, size=size, prop=BOLD)
        x0, y0, x1, y1 = path.get_extents().extents
        out.append({
            "path": path,
            "w": x1 - x0,
            "h": y1 - y0,
            "cx": (x0 + x1) / 2,
            "cy": (y0 + y1) / 2,
        })
    return out


def layout(glyphs, radius, gap, lean=0.25, iterations=5):
    halves = [g["w"] / 2 for g in glyphs]
    for _ in range(iterations):
        total = sum(2 * h for h in halves) + gap * (len(glyphs) - 1)
        walk = -total / 2.0
        for index, glyph in enumerate(glyphs):
            theta = (walk + halves[index]) / radius
            halves[index] = (
                glyph["w"] * abs(math.cos(theta)) + lean * glyph["h"] * abs(math.sin(theta))
            ) / 2
            walk += 2 * halves[index] + gap
    centres = []
    total = sum(2 * h for h in halves) + gap * (len(glyphs) - 1)
    walk = -total / 2.0
    for half in halves:
        centres.append(walk + half)
        walk += 2 * half + gap
    return centres


def arc_text(ax, color, top, radius, cap, z=5):
    glyphs = glyphs_for(TEXT, cap)
    centres = layout(glyphs, radius, GAP)
    mid = math.pi / 2 if top else -math.pi / 2
    for glyph, offset in zip(glyphs, centres):
        theta = mid - offset / radius if top else mid + offset / radius
        r = radius + glyph["cy"]
        x = CX + r * math.cos(theta)
        y = CY + r * math.sin(theta)
        rotation = math.degrees(theta) - 90 if top else math.degrees(theta) + 90
        transform = (
            Affine2D()
            .translate(-glyph["cx"], -glyph["cy"])
            .rotate_deg(rotation)
            .translate(x, y)
        )
        ax.add_patch(PathPatch(
            transform.transform_path(glyph["path"]),
            facecolor=color, edgecolor="none", lw=0, zorder=z,
        ))


def draw(name, color, top):
    fig = plt.figure(figsize=(SIZE / 100, SIZE / 100), dpi=100)
    ax = fig.add_axes([0, 0, 1, 1])
    ax.set_xlim(0, SIZE)
    ax.set_ylim(0, SIZE)
    ax.set_aspect("equal")
    ax.axis("off")

    ax.add_patch(Circle((CX, CY), R_PLATE, facecolor=RED, edgecolor=INK, lw=10, zorder=1))
    ax.add_patch(Circle((CX, CY), R_RING, facecolor="none", edgecolor=WHITE, lw=W_RING, zorder=2))
    ax.add_patch(Circle((CX, CY), R_HOLE, facecolor=WHITE, edgecolor=INK, lw=W_HOLE, zorder=2))

    arc_text(ax, color, top, BASE_R, CAP)
    fig.savefig(os.path.join(OUT, f"{name}.png"), dpi=100, transparent=True)
    plt.close(fig)


def contact_sheet(names):
    tiles = [Image.open(os.path.join(OUT, n + ".png")).convert("RGBA").resize((480, 480))
             for n in names]
    sheet = Image.new("RGB", (480 * len(tiles), 480), WHITE)
    for index, tile in enumerate(tiles):
        sheet.paste(tile, (index * 480, 0), tile)
    sheet.save(os.path.join(OUT, "contact_sheet_vbt.png"))


if __name__ == "__main__":
    names = []
    for color, label in ((INK, "black"), (WHITE, "white")):
        name = f"plate_vbt_top_{label}"
        draw(name, color, True)
        print("wrote", name)
        names.append(name)
    contact_sheet(names)
    print("done")
