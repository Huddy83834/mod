"""Writes the post-effect files for Mirror World + Pixelated (one per combination).
Minecraft post effects can't change settings at runtime, so each combination is its own small file.
Run from punishfx-ci/:  python3 tools/gen_screen_fx.py"""
import json, os
HEIGHTS = [240, 180, 144, 120, 96, 72, 54, 36]   # must match ScreenFx.HEIGHTS
PALETTES = 4
out = "src/main/resources/assets/punishfx/post_effect/fx"
os.makedirs(out, exist_ok=True)
for f in os.listdir(out):
    os.remove(os.path.join(out, f))

def effect(cells, mirror, palette):
    return {
        "targets": {"swap": {}},
        "passes": [
            {"vertex_shader": "minecraft:core/screenquad", "fragment_shader": "punishfx:post/screenfx",
             "inputs": [{"sampler_name": "In", "target": "minecraft:main"}], "output": "swap",
             "uniforms": {"FxConfig": [{"name": "Params", "type": "vec4", "value": [float(cells), float(mirror), float(palette), 0.0]}]}},
            {"vertex_shader": "minecraft:core/screenquad", "fragment_shader": "minecraft:post/blit",
             "inputs": [{"sampler_name": "In", "target": "swap"}],
             "uniforms": {"BlitConfig": [{"name": "ColorModulate", "type": "vec4", "value": [1.0, 1.0, 1.0, 1.0]}]},
             "output": "minecraft:main"}]}

n = 0
for m in (0, 1):
    for r in range(len(HEIGHTS) + 1):
        for p in range(PALETTES):
            if r == 0 and (m == 0 or p != 0):
                continue  # no pixelation: only "mirror alone" is a real effect
            cells = 0 if r == 0 else HEIGHTS[r - 1]
            with open(f"{out}/m{m}_r{r}_p{p}.json", "w") as fh:
                json.dump(effect(cells, m, p), fh, indent=2)
            n += 1
print(n, "files")
