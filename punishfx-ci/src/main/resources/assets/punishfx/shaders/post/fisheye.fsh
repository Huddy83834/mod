#version 330

// Punish FX "Fisheye": a GoPro-style barrel bulge with darker edges. Params.x = strength (0..1).
uniform sampler2D InSampler;

in vec2 texCoord;

layout(std140) uniform FxConfig {
    vec4 Params;
};

out vec4 fragColor;

void main(){
    vec2 size = vec2(textureSize(InSampler, 0));
    float aspect = size.x / size.y;
    vec2 c = texCoord * 2.0 - 1.0;
    c.x *= aspect;
    float norm = length(vec2(aspect, 1.0));
    vec2 n = c / norm;                 // the corners are at radius 1
    float r = length(n);
    float k = Params.x;
    vec2 src = n * mix(1.0 - k, 1.0, r * r) * norm;
    src.x /= aspect;
    vec2 uv = clamp(src * 0.5 + 0.5, 0.0, 1.0);
    vec3 col = texture(InSampler, uv).rgb;
    col *= 1.0 - k * 0.65 * smoothstep(0.55, 1.0, r);
    fragColor = vec4(col, 1.0);
}
