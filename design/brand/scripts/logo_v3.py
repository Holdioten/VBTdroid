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

OUT = "/mnt/d/tmp/VBTdroid/logo/final_v3"
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
    arc_text(ax, X(px), Y(py), pr, scale, palette["text"], z=z + 2)


def arc_text(ax, cx, cy, pr, scale, color, z=5):
    """VBT on the plate face, same proportions as the standalone mark (cap 0.27R)."""
    pr_screen = pr * scale
    cap = 0.27 * pr_screen
    base = 0.373 * pr_screen
    gap = 0.01 * pr_screen
    size = cap / 0.693
    prop = FontProperties(family="Ubuntu", weight="bold")

    glyphs = []
    for ch in "VBT":
        path = TextPath((0, 0), ch, size=size, prop=prop)
        x0, y0, x1, y1 = path.get_extents().extents
        # flip into the y-down canvas once, up front
        flipped = Affine2D().scale(1.0, -1.0).transform_path(path)
        glyphs.append({"path": flipped, "w": x1 - x0, "h": y1 - y0,
                       "cx": (x0 + x1) / 2.0, "cy": -(y0 + y1) / 2.0})

    halves = [g["w"] / 2.0 for g in glyphs]
    for _ in range(5):
        total = sum(2 * h for h in halves) + gap * (len(glyphs) - 1)
        walk = -total / 2.0
        for index, glyph in enumerate(glyphs):
            theta = (walk + halves[index]) / base
            halves[index] = (glyph["w"] * abs(math.cos(theta))
                             + 0.25 * glyph["h"] * abs(math.sin(theta))) / 2.0
            walk += 2 * halves[index] + gap
    centres = []
    total = sum(2 * h for h in halves) + gap * (len(glyphs) - 1)
    walk = -total / 2.0
    for half in halves:
        centres.append(walk + half)
        walk += 2 * half + gap

    for glyph, offset in zip(glyphs, centres):
        theta = math.pi / 2 - offset / base
        r = base + (-glyph["cy"])
        x = cx + r * math.cos(theta)
        y = cy - r * math.sin(theta)
        rotation = 90.0 - math.degrees(theta)
        transform = (Affine2D().translate(-glyph["cx"], -glyph["cy"])
                     .rotate_deg(rotation).translate(x, y))
        ax.add_patch(PathPatch(transform.transform_path(glyph["path"]),
                               facecolor=color, edgecolor="none", lw=0, zorder=z))


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


def palette(background, ink, plate, ring, hole, text):
    return {"bg": background, "ink": ink, "plate": plate, "ring": ring,
            "hole": hole, "text": text}


PAL_LIGHT = palette(PAPER, INK, RED, PAPER, PAPER, PAPER)
PAL_DARK = palette(DARK_BG, LIGHT_INK, RED, PAPER, DARK_BG, PAPER)
PAL_MONO_BLACK = palette(PAPER, INK, INK, PAPER, PAPER, PAPER)
PAL_MONO_WHITE = palette(DARK_BG, PAPER, PAPER, DARK_BG, DARK_BG, DARK_BG)


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


def mark_transparent(name, pal):
    fig, ax = canvas(1024, 1024, None)
    draw_mark(ax, 512, 500, 640, pal)
    fig.savefig(os.path.join(OUT, f"{name}.png"), dpi=100, transparent=True)
    plt.close(fig)
    print("wrote", name)


if __name__ == "__main__":
    mark_transparent("mark_light", PAL_LIGHT)
    mark_transparent("mark_dark", PAL_DARK)
    mark_transparent("mark_mono_black", PAL_MONO_BLACK)
    mark_transparent("mark_mono_white", PAL_MONO_WHITE)
    lockup("logo_horizontal_light", PAL_LIGHT, PAPER)
    lockup("logo_horizontal_dark", PAL_DARK, DARK_BG)
    lockup("logo_horizontal_mono_black", PAL_MONO_BLACK, PAPER)
    lockup("logo_horizontal_mono_white", PAL_MONO_WHITE, DARK_BG)
    app_icon("app_icon_light", PAL_LIGHT, PAPER)
    app_icon("app_icon_dark", PAL_DARK, DARK_BG)
    splash("splash_dark", PAL_DARK, DARK_BG)
    splash("splash_light", PAL_LIGHT, PAPER)
