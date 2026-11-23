#version 330

#moj_import <minecraft:globals.glsl>

// Punish FX "Tiny Window": the whole picture shrinks into a little window that bounces round the
// screen like the old DVD logo. Params.x = size (fraction of the screen), Params.yz = bounce speeds.
uniform sampler2D InSampler;

in vec2 texCoord;

layout(std140) uniform FxConfig {
    vec4 Params;
};

out vec4 fragColor;

float tri(float u) {
    return 1.0 - abs(mod(u, 2.0) - 1.0);
}

void main(){
    vec2 size = vec2(textureSize(InSampler, 0));
    vec2 px = 1.0 / size;
    float s = Params.x;
    float bar = 0.05;                                  // title bar, as a fraction of screen height
    vec2 box = vec2(s, s);
    vec2 total = vec2(box.x, box.y + bar);
    float t = GameTime * 1200.0;                       // seconds
    vec2 pos = vec2(tri(t * Params.y), tri(t * Params.z)) * (1.0 - total);
    vec2 uv = texCoord;

    // background: dark with faint diagonal stripes
    float stripe = step(0.5, fract((uv.x * size.x + uv.y * size.y) / 28.0));
    vec3 col = vec3(0.035, 0.035, 0.05) + stripe * 0.012;

    // soft shadow down-right of the window
    vec2 sh = uv - (pos + vec2(0.008, -0.014));
    if (sh.x >= 0.0 && sh.y >= 0.0 && sh.x <= total.x && sh.y <= total.y) col *= 0.45;

    vec2 local = (uv - pos) / box;
    bool inContent = local.x >= 0.0 && local.x <= 1.0 && local.y >= 0.0 && local.y <= 1.0;
    bool inBar = uv.x >= pos.x && uv.x <= pos.x + box.x && uv.y > pos.y + box.y && uv.y <= pos.y + total.y;
    if (inContent) {
        col = texture(InSampler, local).rgb;
    } else if (inBar) {
        col = vec3(0.15, 0.17, 0.24);
        // three window buttons on the right of the bar
        float r = bar * 0.22;
        vec2 bc = vec2(pos.x + box.x - bar * 0.55, pos.y + box.y + bar * 0.5);
        vec2 d = (uv - bc) * vec2(size.x / size.y, 1.0);
        if (length(d) < r) col = vec3(0.93, 0.33, 0.31);
        d = (uv - bc + vec2(bar * 0.62 * size.y / size.x, 0.0)) * vec2(size.x / size.y, 1.0);
        if (length(d) < r) col = vec3(0.98, 0.74, 0.18);
        d = (uv - bc + vec2(bar * 1.24 * size.y / size.x, 0.0)) * vec2(size.x / size.y, 1.0);
        if (length(d) < r) col = vec3(0.30, 0.80, 0.39);
    }
    // a thin light frame round the whole window
    bool inTotal = uv.x >= pos.x && uv.x <= pos.x + total.x && uv.y >= pos.y && uv.y <= pos.y + total.y;
    if (inTotal && (uv.x - pos.x < 2.0 * px.x || pos.x + total.x - uv.x < 2.0 * px.x
            || uv.y - pos.y < 2.0 * px.y || pos.y + total.y - uv.y < 2.0 * px.y)) col = vec3(0.62, 0.64, 0.7);
    fragColor = vec4(col, 1.0);
}
