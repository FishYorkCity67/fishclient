#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position;
in vec4 Color;

// Positions arrive relative to the camera, so the position itself is the direction the eye looks through the portal.
out vec3 viewDir;
out vec4 vertexColor;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    viewDir = Position;
    vertexColor = Color;
}
