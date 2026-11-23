"""Writes the post-effect files for the screen punishments. Minecraft post effects can't change their
settings at runtime, so each setting is its own small file. ScreenFx picks the right ones and runs them
one after another. Run from punishfx-ci/:  python3 tools/gen_screen_fx.py"""
import json, os
HEIGHTS = [240, 180, 144, 120, 96, 72, 54, 36]          # ScreenFx.HEIGHTS
PALETTES = 4                                            # ScreenFx.PALETTE_NAMES
WINDOW_SIZES = [0.7, 0.55, 0.4, 0.25]                   # ScreenFx.WINDOW_SIZES
WINDOW_SPEEDS = [(0.08, 0.107), (0.15, 0.2), (0.27, 0.36)]    # ScreenFx.WINDOW_SPEEDS
BLUR_RADII = [4, 8, 14, 24]                             # ScreenFx.BLUR_NAMES
FISHEYE = [0.25, 0.45, 0.65, 0.85]                      # ScreenFx.FISHEYE_NAMES
out = "src/main/resources/assets/punishfx/post_effect/fx"
os.makedirs(out, exist_ok=True)
for f in os.listdir(out):
    os.remove(os.path.join(out, f))

BLIT = {"vertex_shader": "minecraft:core/screenquad", "fragment_shader": "minecraft:post/blit",
        "inputs": [{"sampler_name": "In", "target": "swap"}],
        "uniforms": {"BlitConfig": [{"name": "ColorModulate", "type": "vec4", "value": [1.0, 1.0, 1.0, 1.0]}]},
        "output": "minecraft:main"}

def single(shader, params):
    return {"targets": {"swap": {}}, "passes": [
        {"vertex_shader": "minecraft:core/screenquad", "fragment_shader": shader,
         "inputs": [{"sampler_name": "In", "target": "minecraft:main"}], "output": "swap",
         "uniforms": {"FxConfig": [{"name": "Params", "type": "vec4", "value": [float(v) for v in params]}]}},
        BLIT]}

def blur(radius):
    def p(src, dst, d):
        return {"vertex_shader": "minecraft:core/screenquad", "fragment_shader": "minecraft:post/box_blur",
                "inputs": [{"sampler_name": "In", "target": src, "bilinear": True}], "output": dst,
                "uniforms": {"BlurConfig": [{"name": "BlurDir", "type": "vec2", "value": d},
                                            {"name": "Radius", "type": "float", "value": float(radius)}]}}
    return {"targets": {"swap": {}}, "passes": [p("minecraft:main", "swap", [1.0, 0.0]), p("swap", "minecraft:main", [0.0, 1.0])]}

def write(name, data):
    with open(f"{out}/{name}.json", "w") as fh:
        json.dump(data, fh, indent=2)

n = 0
for m in (0, 1):
    for r in range(len(HEIGHTS) + 1):
        for p in range(PALETTES):
            if r == 0 and (m == 0 or p != 0):
                continue  # no pixelation: only "mirror alone" is a real effect
            write(f"m{m}_r{r}_p{p}", single("punishfx:post/screenfx", [0 if r == 0 else HEIGHTS[r - 1], m, p, 0])); n += 1
for i, k in enumerate(FISHEYE):
    write(f"fisheye_{i}", single("punishfx:post/fisheye", [k, 0, 0, 0])); n += 1
for i, rad in enumerate(BLUR_RADII):
    write(f"blur_{i}", blur(rad)); n += 1
for i, s in enumerate(WINDOW_SIZES):
    for j, (vx, vy) in enumerate(WINDOW_SPEEDS):
        write(f"window_{i}_{j}", single("punishfx:post/window", [s, vx, vy, 0])); n += 1
print(n, "files")
