#version 330

// Punish FX screen effects in one pass: Mirror World and Pixelated (with retro colour palettes).
// Params.x = pixel rows (0 = full resolution), Params.y = mirror (1 = flip left-right),
// Params.z = palette (0 full colour, 1 retro dithered, 2 Game Boy, 3 greyscale).
uniform sampler2D InSampler;

in vec2 texCoord;

layout(std140) uniform FxConfig {
    vec4 Params;
};

out vec4 fragColor;

const float BAYER[16] = float[16](
     0.0,  8.0,  2.0, 10.0,
    12.0,  4.0, 14.0,  6.0,
     3.0, 11.0,  1.0,  9.0,
    15.0,  7.0, 13.0,  5.0);

void main(){
    vec2 uv = texCoord;
    if (Params.y > 0.5) uv.x = 1.0 - uv.x;

    vec2 cell = floor(uv * vec2(textureSize(InSampler, 0)));
    if (Params.x > 0.5) {
        vec2 size = vec2(textureSize(InSampler, 0));
        vec2 cells = vec2(max(1.0, floor(Params.x * size.x / size.y + 0.5)), Params.x);
        cell = floor(uv * cells);
        uv = (cell + 0.5) / cells;
    }
    vec3 c = texture(InSampler, uv).rgb;

    int bi = int(mod(cell.y, 4.0)) * 4 + int(mod(cell.x, 4.0));
    float dither = (BAYER[bi] + 0.5) / 16.0 - 0.5;

    if (Params.z > 0.5 && Params.z < 1.5) {
        // retro: 5 levels per channel (125 colours) with ordered dithering
        c = clamp(floor(c * 4.0 + dither + 0.5) / 4.0, 0.0, 1.0);
    } else if (Params.z > 1.5 && Params.z < 2.5) {
        // Game Boy: 4 shades of green
        float l = dot(c, vec3(0.299, 0.587, 0.114));
        float s = clamp(floor(clamp(l + dither * 0.33, 0.0, 1.0) * 3.0 + 0.5), 0.0, 3.0);
        vec3 pal0 = vec3(0.059, 0.220, 0.059);
        vec3 pal1 = vec3(0.188, 0.384, 0.188);
        vec3 pal2 = vec3(0.545, 0.675, 0.059);
        vec3 pal3 = vec3(0.608, 0.737, 0.059);
        c = s < 0.5 ? pal0 : s < 1.5 ? pal1 : s < 2.5 ? pal2 : pal3;
    } else if (Params.z > 2.5) {
        float l = dot(c, vec3(0.299, 0.587, 0.114));
        c = vec3(l);
    }
    fragColor = vec4(c, 1.0);
}
