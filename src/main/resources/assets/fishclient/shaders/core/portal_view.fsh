#version 330

#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;

in vec3 viewDir;
in vec4 vertexColor;

out vec4 fragColor;

// The atlas holds six faces in a 3 x 2 grid: outward, +90, +180, -90 degrees of turn, then up and down.
// Red carries how far to turn the view (0 to 3 quarter turns), so looking into this portal means looking outward there.
void main() {
    vec3 d = normalize(viewDir);
    int k = int(floor(vertexColor.r * 3.0 + 0.5));
    float c = k == 0 ? 1.0 : (k == 2 ? -1.0 : 0.0);
    float s = k == 1 ? 1.0 : (k == 3 ? -1.0 : 0.0);
    vec3 l = vec3(d.x * c + d.z * s, d.y, -d.x * s + d.z * c);
    vec3 a = abs(l);

    int face;
    vec3 f;
    vec3 r;
    vec3 u;
    if (a.y >= a.x && a.y >= a.z) {
        if (l.y > 0.0) {
            face = 4; f = vec3(0.0, 1.0, 0.0); r = vec3(-1.0, 0.0, 0.0); u = vec3(0.0, 0.0, -1.0);
        } else {
            face = 5; f = vec3(0.0, -1.0, 0.0); r = vec3(-1.0, 0.0, 0.0); u = vec3(0.0, 0.0, 1.0);
        }
    } else if (a.z >= a.x) {
        if (l.z > 0.0) {
            face = 0; f = vec3(0.0, 0.0, 1.0); r = vec3(-1.0, 0.0, 0.0); u = vec3(0.0, 1.0, 0.0);
        } else {
            face = 2; f = vec3(0.0, 0.0, -1.0); r = vec3(1.0, 0.0, 0.0); u = vec3(0.0, 1.0, 0.0);
        }
    } else {
        if (l.x < 0.0) {
            face = 1; f = vec3(-1.0, 0.0, 0.0); r = vec3(0.0, 0.0, -1.0); u = vec3(0.0, 1.0, 0.0);
        } else {
            face = 3; f = vec3(1.0, 0.0, 0.0); r = vec3(0.0, 0.0, 1.0); u = vec3(0.0, 1.0, 0.0);
        }
    }

    float depth = dot(l, f);
    vec2 uv = vec2(0.5 + 0.5 * dot(l, r) / depth, 0.5 - 0.5 * dot(l, u) / depth);
    uv = clamp(uv, vec2(0.002), vec2(0.998));
    vec2 atlas = vec2((float(face % 3) + uv.x) / 3.0, (float(face / 3) + uv.y) / 2.0);

    vec4 color = texture(Sampler0, atlas);
    // A faint portal tint keeps it reading as a portal and not as a hole in the wall.
    color.rgb = mix(color.rgb, vec3(0.55, 0.28, 0.85), 0.12);
    fragColor = vec4(color.rgb, vertexColor.a) * ColorModulator;
}
