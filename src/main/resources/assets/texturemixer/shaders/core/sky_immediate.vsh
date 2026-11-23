#version 330
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform float LayerAlpha;
in vec3 Position;
in vec2 UV0;
out vec2 texCoord;
out float visibility;
void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    texCoord = UV0;
    visibility = LayerAlpha;
}
