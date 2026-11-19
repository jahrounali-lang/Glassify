package com.liquidclock.render

/**
 * AGSL (Android 13+) Liquid Glass. Not a backdrop blur:
 *  - "hmap" is a blurred height field of the glyphs; its gradient gives the bezel normal.
 *  - The wallpaper is re-sampled at an offset along that normal (true refraction, per-channel dispersion).
 *  - Flat interior = clear glass; the bending happens on the bevel. Rim light + specular come from the normal.
 */
object GlassShader {
    const val SRC = """
uniform shader bg;
uniform shader mask;
uniform shader hmap;
uniform float refr;
uniform float gradScale;
uniform float blurPx;
uniform float frost;
uniform float opac;
uniform float edgeI;
uniform float reflI;
uniform float thick;
uniform float shadowI;
uniform float shadowOff;
uniform float2 lightDir;

float hash(float2 p) { return fract(sin(dot(p, float2(12.9898, 78.233))) * 43758.5453); }

float3 sampleBg(float2 p, float r) {
  float3 c = bg.eval(p).rgb;
  if (r < 0.5) return c;
  float3 acc = c;
  for (int i = 0; i < 8; i++) {
    float a = 0.7853982 * float(i);
    float2 d = float2(cos(a), sin(a)) * r;
    acc += bg.eval(p + d).rgb + bg.eval(p + d * 0.5).rgb;
  }
  return acc / 17.0;
}

half4 main(float2 p) {
  float m = mask.eval(p).a;
  float sh = hmap.eval(p - lightDir * shadowOff).a;
  float shadowA = (1.0 - m) * sh * shadowI * 0.55;
  if (m < 0.003) return half4(0.0, 0.0, 0.0, half(shadowA));

  float e = 1.5;
  float2 g = float2(
    hmap.eval(p + float2(e, 0.0)).a - hmap.eval(p - float2(e, 0.0)).a,
    hmap.eval(p + float2(0.0, e)).a - hmap.eval(p - float2(0.0, e)).a) / (2.0 * e);
  float gl = length(g);
  float2 gd = gl > 0.00001 ? g / gl : float2(0.0);
  float gm = clamp(gl * gradScale * 2.0, 0.0, 1.5);

  float2 off = gd * gm * refr;
  float disp = 0.05 + 0.002 * refr;
  float br = blurPx + frost * 18.0;
  float2 jit = float2(0.0);
  if (frost > 0.0) jit = (float2(hash(p), hash(p + 17.3)) - 0.5) * frost * 10.0;

  float3 col;
  col.r = sampleBg(p + off * (1.0 + disp) + jit, br).r;
  col.g = sampleBg(p + off + jit, br).g;
  col.b = sampleBg(p + off * (1.0 - disp) + jit, br).b;

  float lit = max(dot(-gd, lightDir), 0.0);
  float opp = max(dot(gd, lightDir), 0.0);
  float rimMask = smoothstep(0.08, 0.9, gm);
  float rim = (lit + 0.55 * opp) * rimMask;
  float spec = pow(lit, 6.0) * smoothstep(0.35, 1.0, gm);

  col *= 1.0 - gm * 0.10 * thick;
  col = mix(col, float3(1.0), gm * 0.10 * thick + 0.02 * thick);
  col = mix(col, float3(1.0), frost * 0.22);
  col += float3(1.0) * (rim * edgeI * 0.55 + spec * reflI * 0.9);
  col = clamp(col, 0.0, 1.0);

  float a = m * opac;
  float outA = a + shadowA * (1.0 - a);
  return half4(half3(col * a), half(outA));
}
"""
}
