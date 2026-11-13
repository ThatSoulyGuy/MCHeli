#version 150

// MCHeli thermal-vision camera (reference MCH_Camera mode 2, which loaded the 1.7.10 "pencil" post shader): the
// scene's luminance as a white-hot greyscale, outlined by a Sobel edge pass so shapes read like the pencil sketch.

uniform sampler2D DiffuseSampler;

in vec2 texCoord;
in vec2 oneTexel;

uniform float EdgeScale;
uniform float Gain;

out vec4 fragColor;

float luma(vec2 uv) {
    return dot(texture(DiffuseSampler, uv).rgb, vec3(0.299, 0.587, 0.114));
}

void main() {
    float tl = luma(texCoord + vec2(-oneTexel.x,  oneTexel.y));
    float t  = luma(texCoord + vec2(0.0,          oneTexel.y));
    float tr = luma(texCoord + vec2( oneTexel.x,  oneTexel.y));
    float l  = luma(texCoord + vec2(-oneTexel.x,  0.0));
    float c  = luma(texCoord);
    float r  = luma(texCoord + vec2( oneTexel.x,  0.0));
    float bl = luma(texCoord + vec2(-oneTexel.x, -oneTexel.y));
    float b  = luma(texCoord + vec2(0.0,         -oneTexel.y));
    float br = luma(texCoord + vec2( oneTexel.x, -oneTexel.y));
    float gx = (tr + 2.0 * r + br) - (tl + 2.0 * l + bl);
    float gy = (tl + 2.0 * t + tr) - (bl + 2.0 * b + br);
    float edge = clamp(sqrt(gx * gx + gy * gy) * EdgeScale, 0.0, 1.0);
    float heat = clamp(c * Gain, 0.0, 1.0);
    fragColor = vec4(vec3(heat * (1.0 - 0.8 * edge)), 1.0);
}
