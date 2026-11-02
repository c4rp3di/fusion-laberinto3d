package com.laberinto3d.juego.game.render

/**
 * Código fuente GLSL ES 3.00 de todos los programas. Shaders simples, precisión highp
 * en fragment (obligatoria en ES 3.0), sin operaciones exóticas.
 *
 * Locations de atributos:
 *   world : 0 pos, 1 normal, 2..5 mat4 de instancia, 6 color de instancia
 *   ball  : 0 pos, 1 normal, 2 uv
 *   quad  : 0 pos (xy en [-1,1])
 */
object Shaders {

    const val WORLD_VERT = """#version 300 es
layout(location = 0) in vec3 aPos;
layout(location = 1) in vec3 aNormal;
layout(location = 2) in mat4 aModel;
layout(location = 6) in vec4 aColor;
uniform mat4 uViewProj;
out vec3 vNormal;
out vec4 vColor;
out vec3 vWorld;
void main() {
    vec4 w = aModel * vec4(aPos, 1.0);
    vWorld = w.xyz;
    vNormal = aNormal;
    vColor = aColor;
    gl_Position = uViewProj * w;
}
"""

    /** alpha del color de instancia = material: 0.5 suelo con rejilla, 1.0 pared. */
    const val WORLD_FRAG = """#version 300 es
precision highp float;
in vec3 vNormal;
in vec4 vColor;
in vec3 vWorld;
uniform vec3 uLightDir;
uniform float uAmbient;
uniform vec4 uCutBall;   // xyz = bola, w = radio^2 del cilindro de corte (0 = sin corte)
uniform vec3 uCutDir;    // unitario, de la bola hacia la cámara
uniform vec2 uSide;      // sombreado de caras laterales por altura (lo, hi); hi <= lo = constante
uniform vec2 uCutLim;    // el corte sólo afecta a lo que está más cerca de la cámara que x y más alto que y
layout(location = 0) out vec4 oColor;
void main() {
    if (uCutBall.w > 0.0) {
        vec3 dd = vWorld - uCutBall.xyz;
        float tt = dot(dd, uCutDir);
        vec3 pp = dd - uCutDir * tt;
        if (tt > uCutLim.x && dd.y > uCutLim.y && dot(pp, pp) < uCutBall.w) discard;
    }
    vec3 n = normalize(vNormal);
    float ndl = max(dot(n, uLightDir), 0.0);
    float shade = 1.0;
    if (n.y > 0.5) {
        if (vColor.a < 0.75) {
            vec2 g = abs(fract(vWorld.xz / 0.5 + 0.5) - 0.5);
            float line = 1.0 - smoothstep(0.0, 0.02, min(g.x, g.y));
            shade = 1.0 - 0.12 * line;
        }
    } else {
        // caras laterales: más oscuras cuanto más abajo (da sensación de profundidad)
        shade = (uSide.y > uSide.x) ? mix(0.45, 1.0, smoothstep(uSide.x, uSide.y, vWorld.y)) : 0.85;
    }
    vec3 lit = vColor.rgb * (uAmbient + ndl * 0.85) * shade;
    oColor = vec4(lit, 1.0);
}
"""

    /** Malla de triángulos del mundo (rampas): pos, normal, color rgba por vértice. */
    const val MESH_VERT = """#version 300 es
layout(location = 0) in vec3 aPos;
layout(location = 1) in vec3 aNormal;
layout(location = 6) in vec4 aColor;
uniform mat4 uViewProj;
uniform mat4 uModel;
out vec3 vNormal;
out vec4 vColor;
out vec3 vWorld;
void main() {
    vec4 w = uModel * vec4(aPos, 1.0);
    vWorld = w.xyz;
    vNormal = mat3(uModel) * aNormal;
    vColor = aColor;
    gl_Position = uViewProj * w;
}
"""

    /** Cáscara transparente de la gran esfera: sólo las caras lejanas, con una retícula de meridianos y paralelos. */
    const val SHELL_VERT = """#version 300 es
layout(location = 0) in vec3 aPos;
uniform mat4 uViewProj;
uniform float uRadius;
uniform mat4 uModel;
out vec3 vDir;
void main() {
    vDir = aPos;
    gl_Position = uViewProj * uModel * vec4(aPos * uRadius, 1.0);
}
"""

    const val SHELL_FRAG = """#version 300 es
precision highp float;
in vec3 vDir;
layout(location = 0) out vec4 oColor;
void main() {
    vec3 d = normalize(vDir);
    float lat = asin(clamp(d.y, -1.0, 1.0));
    float lon = atan(d.z, d.x);
    vec2 g = abs(fract(vec2(lon, lat) / 0.261799 + 0.5) - 0.5);
    float w = 0.035 / max(0.25, sqrt(1.0 - d.y * d.y));
    float lineLon = 1.0 - smoothstep(0.0, w, g.x);
    float lineLat = 1.0 - smoothstep(0.0, 0.035, g.y);
    float line = max(lineLon, lineLat);
    float a = 0.045 + 0.20 * line;
    oColor = vec4(mix(vec3(0.45, 0.65, 0.95), vec3(0.75, 0.88, 1.0), line), a);
}
"""

    const val BALL_VERT = """#version 300 es
layout(location = 0) in vec3 aPos;
layout(location = 1) in vec3 aNormal;
layout(location = 2) in vec2 aUv;
uniform mat4 uViewProj;
uniform mat4 uModel;
out vec3 vWorld;
out vec3 vNormal;
out vec2 vUv;
void main() {
    vec4 w = uModel * vec4(aPos, 1.0);
    vWorld = w.xyz;
    vNormal = mat3(uModel) * aNormal;
    vUv = aUv;
    gl_Position = uViewProj * w;
}
"""

    const val BALL_FRAG = """#version 300 es
precision highp float;
in vec3 vWorld;
in vec3 vNormal;
in vec2 vUv;
uniform sampler2D uTex;
uniform vec3 uLightDir;
uniform vec3 uCamPos;
uniform float uAmbient;
layout(location = 0) out vec4 oColor;
void main() {
    vec3 tex = texture(uTex, vUv).rgb;
    vec3 n = normalize(vNormal);
    vec3 v = normalize(uCamPos - vWorld);
    vec3 h = normalize(uLightDir + v);
    float diff = max(dot(n, uLightDir), 0.0);
    float spec = pow(max(dot(n, h), 0.0), 40.0);
    float rim = pow(1.0 - max(dot(n, v), 0.0), 3.0) * 0.12;
    vec3 col = tex * (uAmbient + diff * 0.85) + vec3(spec * 0.45) + vec3(rim);
    oColor = vec4(col, 1.0);
}
"""

    /** Quad horizontal en el suelo: centro (xyz) y radio (w) por uniform. */
    const val QUAD_VERT = """#version 300 es
layout(location = 0) in vec2 aPos;
uniform mat4 uViewProj;
uniform vec4 uCenterRadius;
uniform vec3 uAxisU;
uniform vec3 uAxisV;
out vec2 vUv;
void main() {
    vUv = aPos;
    vec3 w = uCenterRadius.xyz + (aPos.x * uAxisU + aPos.y * uAxisV) * uCenterRadius.w;
    gl_Position = uViewProj * vec4(w, 1.0);
}
"""

    const val SHADOW_FRAG = """#version 300 es
precision highp float;
in vec2 vUv;
uniform float uAlpha;
layout(location = 0) out vec4 oColor;
void main() {
    float d = length(vUv);
    float a = (1.0 - smoothstep(0.30, 1.0, d)) * uAlpha;
    oColor = vec4(0.0, 0.0, 0.0, a);
}
"""

    const val GOAL_FRAG = """#version 300 es
precision highp float;
in vec2 vUv;
uniform float uTime;
uniform vec3 uColA;
uniform vec3 uColB;
uniform float uPulse;
layout(location = 0) out vec4 oColor;
void main() {
    float d = length(vUv);
    float ring = smoothstep(0.80, 0.88, d) - smoothstep(0.95, 1.0, d);
    float fill = (1.0 - smoothstep(0.0, 0.85, d)) * 0.38;
    float pulse = 1.0 - uPulse + uPulse * (0.78 + 0.22 * sin(uTime * 3.0));
    float a = (ring * 0.95 + fill) * pulse;
    if (a < 0.01) discard;
    vec3 c = mix(uColA, uColB, ring);
    oColor = vec4(c, a);
}
"""
}
