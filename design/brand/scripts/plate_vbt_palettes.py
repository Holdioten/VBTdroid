"""VBT plate mark in the four palettes the brand assets need, plus the v2 asset rebuild."""

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
BASE_R = 112
CAP = 81
TEXT = "VBT"

PALETTES = {
    "light": dict(plate="#D5202A", outline="#12171B", ring="#FFFFFF",
                  hole="#FFFFFF", hole_outline="#12171B", text="#FFFFFF"),
    "dark": dict(plate="#D5202A", outline="#E9EEF1", ring="#FFFFFF",
                 hole="#0A0E11", hole_outline="#E9EEF1", text="#FFFFFF"),
    "mono_black": dict(plate="#12171B", outline="#12171B", ring="#FFFFFF",
                       hole="#FFFFFF", hole_outline="#12171B", text="#FFFFFF"),
    "mono_white": dict(plate="#FFFFFF", outline="#FFFFFF", ring="#12171B",
                       hole="#12171B", hole_outline="#FFFFFF", text="#12171B"),
}


def glyphs_for(text, cap):
    size = cap / CAP_RATIO
    out = []
    for ch in text:
        path = TextPath((0, 0), ch, size=size, prop=BOLD)
        x0, y0, x1, y1 = path.get_extents().extents
        out.append({"path": path, "w": x1 - x0, "h": y1 - y0,
                    "cx": (x0 + x1) / 2, "cy": (y0 + y1) / 2})
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

    glyphs = glyphs_for(TEXT, CAP)
    centres = layout(glyphs, BASE_R, GAP)
    mid = math.pi / 2
    for glyph, offset in zip(glyphs, centres):
        theta = mid - offset / BASE_R
        r = BASE_R + glyph["cy"]
        x, y = CX + r * math.cos(theta), CY + r * math.sin(theta)
        rotation = math.degrees(theta) - 90
        transform = (Affine2D().translate(-glyph["cx"], -glyph["cy"])
                     .rotate_deg(rotation).translate(x, y))
        ax.add_patch(PathPatch(
            transform.transform_path(glyph["path"]),
            facecolor=palette["text"], edgecolor="none", lw=0, zorder=5))

    fig.savefig(os.path.join(OUT, f"plate_vbt_{name}.png"), dpi=100, transparent=True)
    plt.close(fig)


if __name__ == "__main__":
    for name, palette in PALETTES.items():
        draw(name, palette)
        print("wrote", name)
