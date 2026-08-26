#version 150

uniform sampler2D DiffuseSampler;
uniform sampler2D DepthSampler;
uniform sampler2D HistorySampler;
uniform vec2 InSize;
uniform float Time;
uniform float TerritoryBlend;
uniform float Strength;
uniform float Pulse;
uniform float DepthAvailable;
uniform float HistoryReady;

in vec2 texCoord;
out vec4 fragColor;

float luminance(vec3 color) {
    return dot(color, vec3(0.299, 0.587, 0.114));
}

float hash21(vec2 point) {
    point = fract(point * vec2(123.34, 456.21));
    point += dot(point, point + 45.32);
    return fract(point.x * point.y);
}

float valueNoise(vec2 point) {
    vec2 cell = floor(point);
    vec2 local = fract(point);
    local = local * local * (3.0 - 2.0 * local);
    float lower = mix(hash21(cell), hash21(cell + vec2(1.0, 0.0)), local.x);
    float upper = mix(
            hash21(cell + vec2(0.0, 1.0)),
            hash21(cell + vec2(1.0, 1.0)),
            local.x);
    return mix(lower, upper, local.y);
}

float milkyField(vec2 point, vec2 drift) {
    float broad = valueNoise(point + drift);
    float warp = valueNoise(point * 1.73 - drift.yx + broad * 0.65);
    float fine = valueNoise(point * 3.11 + vec2(warp * 0.75) + drift.yx);
    return broad * 0.48 + warp * 0.34 + fine * 0.18;
}

void main() {
    vec2 pixel = 1.0 / max(InSize, vec2(1.0));
    vec4 source = texture(DiffuseSampler, texCoord);
    vec2 sampleStep = pixel * 1.75;
    vec3 left = texture(DiffuseSampler, texCoord - vec2(sampleStep.x, 0.0)).rgb;
    vec3 right = texture(DiffuseSampler, texCoord + vec2(sampleStep.x, 0.0)).rgb;
    vec3 up = texture(DiffuseSampler, texCoord - vec2(0.0, sampleStep.y)).rgb;
    vec3 down = texture(DiffuseSampler, texCoord + vec2(0.0, sampleStep.y)).rgb;

    float depth = texture(DepthSampler, texCoord).r;
    float depthLeft = texture(
            DepthSampler, texCoord - vec2(sampleStep.x, 0.0)).r;
    float depthRight = texture(
            DepthSampler, texCoord + vec2(sampleStep.x, 0.0)).r;
    float depthUp = texture(
            DepthSampler, texCoord - vec2(0.0, sampleStep.y)).r;
    float depthDown = texture(
            DepthSampler, texCoord + vec2(0.0, sampleStep.y)).r;

    float sourceValue = luminance(source.rgb);
    float colourBoundary = clamp(
            abs(sourceValue - luminance(left))
            + abs(sourceValue - luminance(right))
            + abs(sourceValue - luminance(up))
            + abs(sourceValue - luminance(down)),
            0.0, 1.0);
    float depthBoundary = clamp(
            (abs(depth - depthLeft)
            + abs(depth - depthRight)
            + abs(depth - depthUp)
            + abs(depth - depthDown)) * 420.0,
            0.0, 1.0);
    float boundary = mix(
            smoothstep(0.035, 0.48, colourBoundary) * 0.58,
            max(smoothstep(0.025, 0.78, depthBoundary),
                    smoothstep(0.055, 0.62, colourBoundary) * 0.48),
            DepthAvailable);

    float colourRelief = (
            luminance(left) - luminance(right)
            + luminance(up) - luminance(down)) * 1.35;
    float depthRelief = (
            depthLeft - depthRight
            + depthUp - depthDown) * 210.0;
    float relief = clamp(
            mix(colourRelief, depthRelief, DepthAvailable),
            -1.0,
            1.0) * boundary;

    // Depth is non-linear. Nearby surfaces remain tangible while distant
    // geometry and sky dissolve into the shared perception field.
    float nearSense = clamp(
            pow(max((1.0 - depth) * 42.0, 0.0), 0.38),
            0.0,
            1.0);
    nearSense = mix(0.58, nearSense, DepthAvailable);

    float aspect = InSize.x / max(InSize.y, 1.0);
    float phase = Time * 6.2831853;
    vec2 drift = vec2(sin(phase), cos(phase)) * 0.19;
    vec2 fieldUv = vec2(texCoord.x * aspect, texCoord.y) * 2.15;
    float field = milkyField(fieldUv, drift) - 0.5;
    float fineField = valueNoise(
            fieldUv * 4.6 + drift.yx * 1.7 + field * 0.8) - 0.5;
    float variation = field * 0.030 + fineField * 0.010;
    variation *= mix(1.0, 0.52, TerritoryBlend);

    vec3 coolMilk = vec3(0.885, 0.925, 0.970) + variation;
    vec3 warmMilk = vec3(0.970, 0.955, 0.900)
            + variation
            + vec3(0.010, 0.008, 0.002) * Pulse;
    vec3 milk = mix(coolMilk, warmMilk, TerritoryBlend);

    vec3 grey = vec3(sourceValue);
    vec3 coolScene = mix(grey, source.rgb, 0.18) * vec3(0.94, 0.985, 1.045);
    vec3 warmScene = mix(grey, source.rgb, 0.30) * vec3(1.025, 1.005, 0.955);
    vec3 sensedScene = mix(coolScene, warmScene, TerritoryBlend);
    // Slightly clearer than the historical version while preserving its
    // milk-field character and the territory contrast.
    float sceneClarity = mix(
            0.23 + nearSense * 0.69,
            0.58 + nearSense * 0.39,
            TerritoryBlend);
    vec3 transformed = mix(milk, sensedScene, sceneClarity);

    // Saturated outline pixels are produced for visible living bodies. Keep
    // their colour legible through the otherwise desaturated tactile field.
    float sourceMax = max(source.r, max(source.g, source.b));
    float sourceMin = min(source.r, min(source.g, source.b));
    float signalChroma = sourceMax - sourceMin;
    float bodySignal = smoothstep(0.34, 0.68, signalChroma)
            * smoothstep(0.22, 0.72, sourceMax);
    transformed = mix(transformed, source.rgb, bodySignal * 0.82);

    // Opposing light and shade turn silhouettes into a soft embossed surface,
    // suggesting touch distributed over the body rather than eyesight.
    vec3 reliefHighlight = mix(
            vec3(0.120, 0.150, 0.185),
            vec3(0.155, 0.140, 0.105),
            TerritoryBlend);
    vec3 reliefShadow = mix(
            vec3(0.085, 0.105, 0.135),
            vec3(0.105, 0.095, 0.075),
            TerritoryBlend);
    transformed += max(relief, 0.0) * reliefHighlight;
    transformed -= max(-relief, 0.0) * reliefShadow;
    transformed += boundary * mix(0.025, 0.045, TerritoryBlend)
            * mix(vec3(0.78, 0.88, 1.0), vec3(1.0, 0.96, 0.82), TerritoryBlend);

    // Moving surfaces leave a restrained tactile echo from the previous frame.
    // The hive territory is steadier, so the trace is naturally weaker there.
    if (HistoryReady > 0.5) {
        vec3 history = texture(HistorySampler, texCoord).rgb;
        float change = luminance(abs(transformed - history));
        float trace = smoothstep(0.018, 0.18, change)
                * mix(0.080, 0.035, TerritoryBlend);
        transformed = mix(transformed, history, trace);
    }

    transformed = clamp(transformed, vec3(0.0), vec3(1.0));
    fragColor = vec4(mix(source.rgb, transformed, Strength), source.a);
}
