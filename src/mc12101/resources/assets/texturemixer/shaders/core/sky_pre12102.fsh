#version 150
uniform sampler2D Sampler0;
uniform sampler2D DestSampler;
uniform int BlendMode;
in vec2 texCoord;
in float visibility;
out vec4 fragColor;
void main() {
    vec4 source = texture(Sampler0, texCoord);
    vec3 d = texelFetch(DestSampler, ivec2(gl_FragCoord.xy), 0).rgb;
    vec3 s = source.rgb;
    float a = clamp(source.a * visibility, 0.0, 1.0);
    vec3 result;
    if (BlendMode == 0) {
    result = d + s * a;
    } else if (BlendMode == 1) {
    result = d - s * a;
    } else if (BlendMode == 2) {
    result = d * (s * a + 1.0 - a);
    } else if (BlendMode == 3) {
    result = min(vec3(1.0), d / max(vec3(0.00001), 1.0 - s * a));
    } else if (BlendMode == 4) {
    result = 1.0 - min(vec3(1.0), (1.0 - d) / max(vec3(0.00001), s * a + 1.0 - a));
    } else if (BlendMode == 5) {
    result = 1.0 - (1.0 - d) * (1.0 - s * a);
    } else if (BlendMode == 6) {
    result = s;
    } else if (BlendMode == 7) {
    result = d * (2.0 * s * a + 1.0 - a);
    } else {
    result = s * a + d * (1.0 - a);
    }
    fragColor = vec4(clamp(result, 0.0, 1.0), 1.0);
}
