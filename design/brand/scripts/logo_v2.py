"""VBTdroid identity: icon (plate + speed streaks), wordmark, lockups and variants."""

import math
import os

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib.font_manager import FontProperties
from matplotlib.patches import Circle, FancyBboxPatch, PathPatch
from matplotlib.textpath import TextPath
from matplotlib.transforms import Affine2D
from PIL import Image

OUT = "/mnt/d/tmp/VBTdroid/logo/final_v2"
SVG = os.path.join(OUT, "svg")
os.makedirs(SVG, exist_ok=True)

RED = "#D5202A"
INK = "#12171B"
PAPER = "#FFFFFF"
DARK_BG = "#0A0E11"
LIGHT_INK = "#E9EEF1"

PX = 72.0 / 100.0

# Mark geometry: plate with three trails below, tops following the arc (concept 19).
PLATE = (512.0, 420.0, 280.0)
TRAILS = ((-92, 100, 22), (0, 120, 30), (92, 100, 22))
TRAIL_GAP = 40.0
MARK_BOUNDS = (232.0, 140.0, 792.0, 875.0)  # left, top, right, bottom


def pt(px):
    return px * PX


def canvas(width, height, background):
    fig = plt.figure(figsize=(width / 100.0, height / 100.0), dpi=100)
    ax = fig.add_axes([0, 0, 1, 1])
    ax.set_xlim(0, width)
    ax.set_ylim(height, 0)
    ax.axis("off")
    if background is not None:
        ax.add_patch(plt.Rectangle((0, 0), width, height, facecolor=background,
                                   edgecolor="none", zorder=0))
    return fig, ax


def draw_mark(ax, cx, cy, height, palette, z=2):
    """Draw the mark scaled to [height] and centred on (cx, cy)."""
    left, top, right, bottom = MARK_BOUNDS
    scale = height / (bottom - top)
    ox = cx - (left + right) / 2 * scale
    oy = cy - (top + bottom) / 2 * scale

    def X(x):
        return ox + x * scale

    def Y(y):
        return oy + y * scale

    px, py, pr = PLATE
    for dx, length, line_width in TRAILS:
        x = px + dx
        arc_y = py + math.sqrt(pr * pr - dx * dx)
        top = arc_y + TRAIL_GAP
        ax.plot([X(x), X(x)], [Y(top), Y(top + length)], color=palette["ink"],
                lw=pt(line_width * scale), solid_capstyle="round", zorder=z)
    ax.add_patch(Circle((X(px), Y(py)), pr * scale, facecolor=palette["plate"],
                        edgecolor=palette["ink"], lw=pt(14 * scale), zorder=z + 1))
    ax.add_patch(Circle((X(px), Y(py)), pr * 0.82 * scale, facecolor="none",
                        edgecolor=palette["ring"], lw=pt(10 * scale), zorder=z + 1.1))
    ax.add_patch(Circle((X(px), Y(py)), pr * 0.18 * scale, facecolor=palette["hole"],
                        edgecolor=palette["ink"], lw=pt(10 * scale), zorder=z + 1.2))


def text_path(text, weight, em):
    prop = FontProperties(family="Ubuntu", weight=weight)
    path = TextPath((0, 0), text, size=100, prop=prop)
    # The canvas uses a y-down axis; flip the text so it reads upright.
    return Affine2D().scale(em / 100.0, -em / 100.0).transform_path(path)


def measure_wordmark(em):
    bold = text_path("VBT", "bold", em)
    light = text_path("droid", "regular", em)
    return bold.get_extents().width + em * 0.06 + light.get_extents().width


def draw_wordmark(ax, left, center_y, em, color, z=5):
    """VBT bold + droid light, one baseline, centred vertically on center_y."""
    bold = text_path("VBT", "bold", em)
    light = text_path("droid", "regular", em)
    gap = em * 0.06
    bold_box = bold.get_extents()
    light_box = light.get_extents()
    total_width = bold_box.width + gap + light_box.width
    # After the y-flip the baseline is still y=0; centre the union of both words.
    union_top = min(bold_box.y0, light_box.y0)
    union_bottom = max(bold_box.y1, light_box.y1)
    baseline = center_y - (union_top + union_bottom) / 2
    parts = (
        (bold, left - bold_box.x0, baseline, color),
        (light, left + bold_box.width + gap - light_box.x0, baseline, color),
    )
    for path, dx, dy, colour in parts:
        ax.add_patch(PathPatch(Affine2D().translate(dx, dy).transform_path(path),
                               facecolor=colour, edgecolor="none", lw=0, zorder=z))
    return total_width


def save(fig, name, sizes=(1024, 192, 48)):
    png = os.path.join(OUT, f"{name}.png")
    fig.savefig(png, dpi=100)
    fig.savefig(os.path.join(SVG, f"{name}.svg"))
    plt.close(fig)
    image = Image.open(png).convert("RGB")
    for size in sizes:
        image.resize((size, size), Image.LANCZOS).save(
            os.path.join(OUT, f"{name}_{size}.png"))
    print("wrote", png)


def palette(background, ink, plate, ring, hole):
    return {"bg": background, "ink": ink, "plate": plate, "ring": ring, "hole": hole}


PAL_LIGHT = palette(PAPER, INK, RED, PAPER, PAPER)
PAL_DARK = palette(DARK_BG, LIGHT_INK, RED, PAPER, DARK_BG)
PAL_MONO_BLACK = palette(PAPER, INK, INK, PAPER, PAPER)
PAL_MONO_WHITE = palette(DARK_BG, PAPER, PAPER, DARK_BG, DARK_BG)


def lockup(name, pal, background):
    width, height = 2200, 700
    fig, ax = canvas(width, height, background)
    draw_mark(ax, 355, 350, 600, pal)
    draw_wordmark(ax, 760, 350, 205, pal["ink"])
    fig.savefig(os.path.join(OUT, f"{name}.png"), dpi=100)
    fig.savefig(os.path.join(SVG, f"{name}.svg"))
    plt.close(fig)
    print("wrote", name)


def app_icon(name, pal, background, radius=220):
    fig, ax = canvas(1024, 1024, None)
    ax.add_patch(FancyBboxPatch((0, 0), 1024, 1024,
                                boxstyle=f"round,pad=0,rounding_size={radius}",
                                facecolor=background, edgecolor="none", zorder=0))
    draw_mark(ax, 512, 500, 640, pal)
    fig.savefig(os.path.join(OUT, f"{name}.png"), dpi=100)
    fig.savefig(os.path.join(SVG, f"{name}.svg"))
    plt.close(fig)
    Image.open(os.path.join(OUT, f"{name}.png")).convert("RGB").resize(
        (192, 192), Image.LANCZOS).save(os.path.join(OUT, f"{name}_192.png"))
    print("wrote", name)


def splash(name, pal, background):
    width, height = 1080, 1920
    fig, ax = canvas(width, height, background)
    draw_mark(ax, 540, 700, 560, pal)
    em = 150
    word_width = measure_wordmark(em)
    draw_wordmark(ax, (width - word_width) / 2, 1075, em, pal["ink"])
    fig.savefig(os.path.join(OUT, f"{name}.png"), dpi=100)
    fig.savefig(os.path.join(SVG, f"{name}.svg"))
    plt.close(fig)
    print("wrote", name)


if __name__ == "__main__":
    lockup("logo_horizontal_light", PAL_LIGHT, PAPER)
    lockup("logo_horizontal_dark", PAL_DARK, DARK_BG)
    lockup("logo_horizontal_mono_black", PAL_MONO_BLACK, PAPER)
    lockup("logo_horizontal_mono_white", PAL_MONO_WHITE, DARK_BG)
    app_icon("app_icon_light", PAL_LIGHT, PAPER)
    app_icon("app_icon_dark", PAL_DARK, DARK_BG)
    splash("splash_dark", PAL_DARK, DARK_BG)
    splash("splash_light", PAL_LIGHT, PAPER)
