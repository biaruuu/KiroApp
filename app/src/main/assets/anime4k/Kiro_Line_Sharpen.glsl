//!DESC Kiro-Line-Sharpen
//!HOOK MAIN
//!BIND HOOKED
float kl(vec3 c) { return dot(c, vec3(0.2126, 0.7152, 0.0722)); }
float kt(float x, float y) { return kl(HOOKED_texOff(vec2(x, y)).rgb); }
vec4 hook() {
    vec4 px = HOOKED_texOff(vec2(0.0, 0.0));
    float c = kl(px.rgb);
    float n = kt(0.0, -1.0); float s = kt(0.0, 1.0);
    float w = kt(-1.0, 0.0); float e = kt(1.0, 0.0);
    float nw = kt(-1.0, -1.0); float ne = kt(1.0, -1.0);
    float sw = kt(-1.0, 1.0); float se = kt(1.0, 1.0);
    float blur = (4.0 * c + 2.0 * (n + s + w + e) + (nw + ne + sw + se)) / 16.0;
    float lo = min(c, min(min(min(n, s), min(w, e)), min(min(nw, ne), min(sw, se))));
    float hi = max(c, max(max(max(n, s), max(w, e)), max(max(nw, ne), max(sw, se))));
    float out_l = clamp(c + 1.6 * (c - blur), lo - 0.15 * (hi - lo), hi + 0.15 * (hi - lo));
    return vec4(clamp(px.rgb + (out_l - c), 0.0, 1.0), px.a);
}
