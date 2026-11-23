#version 330

// Punish FX "Mirror World": sample the frame flipped left-to-right.
uniform sampler2D InSampler;

in vec2 texCoord;

out vec4 fragColor;

void main(){
    fragColor = vec4(texture(InSampler, vec2(1.0 - texCoord.x, texCoord.y)).rgb, 1.0);
}
