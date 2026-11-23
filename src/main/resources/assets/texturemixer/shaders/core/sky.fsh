#version 330
uniform sampler2D Sampler0;
uniform sampler2D DestSampler;
in vec2 texCoord;
in float visibility;
out vec4 fragColor;
void main() {
    vec4 source = texture(Sampler0, texCoord);
    vec3 d = texelFetch(DestSampler, ivec2(gl_FragCoord.xy), 0).rgb;
    vec3 s = source.rgb;
    float a = clamp(source.a * visibility, 0.0, 1.0);
    vec3 result;
#if BLEND_MODE == 0
    result = d + s * a;
#elif BLEND_MODE == 1
    result = d - s * a;
#elif BLEND_MODE == 2
    result = d * (s * a + 1.0 - a);
#elif BLEND_MODE == 3
    result = min(vec3(1.0), d / max(vec3(0.00001), 1.0 - s * a));
#elif BLEND_MODE == 4
    result = 1.0 - min(vec3(1.0), (1.0 - d) / max(vec3(0.00001), s * a + 1.0 - a));
#elif BLEND_MODE == 5
    result = 1.0 - (1.0 - d) * (1.0 - s * a);
#elif BLEND_MODE == 6
    result = s;
#elif BLEND_MODE == 7
    result = d * (2.0 * s * a + 1.0 - a);
#else
    result = s * a + d * (1.0 - a);
#endif
    fragColor = vec4(clamp(result, 0.0, 1.0), 1.0);
}
