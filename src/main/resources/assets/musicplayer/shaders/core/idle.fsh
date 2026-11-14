#version 150

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;

// 逐帧参数（1.21.1 无 uniform buffer，26.x 的 IdleParams 拆成 4 个 vec4 uniform）
// xy = 主渲染目标尺寸（像素），z = 秒，w = 淡入淡出 0-1
uniform vec4 IdleResolutionTime;
// x = 低频，y = 中频，z = 高频，w = 总响度
uniform vec4 IdleAudioParams;
// rgb = 主题色，a = 强度倍率
uniform vec4 IdleColorIntensity;
// x = 播放进度 0-1，y = 封面宽高比（0 = 无封面），z = 预设编号 + 4*滤镜编号，w = 节拍脉冲
uniform vec4 IdleMeta;

in vec2 vUv;

out vec4 fragColor;

const float PI = 3.14159265359;

float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

float valueNoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float a = hash12(i);
    float b = hash12(i + vec2(1.0, 0.0));
    float c = hash12(i + vec2(0.0, 1.0));
    float d = hash12(i + vec2(1.0, 1.0));
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

float fbm(vec2 p) {
    float value = 0.0;
    float amplitude = 0.5;
    mat2 rotate = mat2(0.80, 0.60, -0.60, 0.80);
    for (int i = 0; i < 5; i++) {
        value += amplitude * valueNoise(p);
        p = rotate * p * 2.02 + 17.7;
        amplitude *= 0.5;
    }
    return value;
}

// 更便宜的 3 次噪声，用于焦散这类只需要大致形状的地方
float fbm3(vec2 p) {
    float value = 0.0;
    float amplitude = 0.5;
    mat2 rotate = mat2(0.80, 0.60, -0.60, 0.80);
    for (int i = 0; i < 3; i++) {
        value += amplitude * valueNoise(p);
        p = rotate * p * 2.06 + 11.3;
        amplitude *= 0.5;
    }
    return value;
}

// 叠加一圈向外扩散的波纹，同时累加它的解析梯度。
// 梯度就是水面的「斜率」，后面直接拿来当法线做折射与高光。
void rippleAdd(inout float height, inout vec2 slope, vec2 p, vec2 center,
               float frequency, float speed, float decay, float weight,
               float time, float wobble) {
    vec2 delta = p - center;
    float dist = length(delta) + 1.0E-5;
    float angle = atan(delta.y, delta.x);

    // 两段不同频率的角向扰动：一大一小，叠起来就没有明显的周期感
    float phaseA = angle * 3.0 + dist * 2.3 + time * 0.21;
    float phaseB = angle * 5.0 - dist * 3.7 - time * 0.34;
    float wobblePhase = wobble * (sin(phaseA) + 0.6 * sin(phaseB));

    float phase = dist * frequency - time * speed + wobblePhase;
    float envelope = exp(-dist * dist * decay);
    float wave = sin(phase);

    float freqPerturb = wobble * (2.3 * cos(phaseA) - 2.22 * cos(phaseB));
    float waveSlope = cos(phase) * (frequency + freqPerturb);

    height += wave * envelope * weight;
    slope += (waveSlope * delta / dist * envelope
            - wave * envelope * 2.0 * decay * delta) * weight;
}

vec3 saturateColor(vec3 color, float amount) {
    float luma = dot(color, vec3(0.2126, 0.7152, 0.0722));
    return mix(vec3(luma), color, amount);
}

vec3 aces(vec3 x) {
    const float a = 2.51;
    const float b = 0.03;
    const float c = 2.43;
    const float d = 0.59;
    const float e = 0.14;
    return clamp((x * (a * x + b)) / (x * (c * x + d) + e), 0.0, 1.0);
}

float grain(vec2 uv, float time) {
    return hash12(uv * vec2(1920.0, 1080.0) + time * 37.0) - 0.5;
}

vec3 vignette(vec3 color, vec2 screenUv, float strength) {
    vec2 q = screenUv - 0.5;
    float amount = 1.0 - smoothstep(0.18, 0.95, length(q) * 1.15);
    return color * mix(1.0, amount, strength);
}

// ──────────────────────────────────────────────────────────────
//  背景滤镜：在特效最终画面上做一次整体调色。
//  编号与 IdleFilter 枚举顺序一致，经 IdleMeta.z 传入。
// ──────────────────────────────────────────────────────────────
vec3 applyFilter(vec3 color, int filterMode) {
    float luma = dot(color, vec3(0.299, 0.587, 0.114));
    if (filterMode == 1) {
        // 柔梦：提亮暗部、轻微去饱和、偏冷
        color = mix(color, vec3(luma), 0.16);
        color = pow(max(color, vec3(0.0)), vec3(0.92));
        color = color * 0.92 + vec3(0.032, 0.038, 0.052);
    } else if (filterMode == 2) {
        // 暖阳：琥珀色高光，轻微增饱和
        color = mix(color, color * vec3(1.12, 1.00, 0.86) + vec3(0.014, 0.007, 0.0), 0.55);
        color = mix(vec3(luma), color, 1.05);
    } else if (filterMode == 3) {
        // 冷夜：青蓝阴影，整体略压暗
        color = mix(color, color * vec3(0.84, 0.96, 1.18) + vec3(0.0, 0.008, 0.020), 0.60);
        color = mix(vec3(luma), color, 0.94);
        color *= 0.96;
    } else if (filterMode == 4) {
        // 黑白：带一点对比度的银盐质感
        float mono = clamp((luma - 0.5) * 1.16 + 0.5, 0.0, 1.0);
        color = vec3(mono);
    } else if (filterMode == 5) {
        // 霓虹：暗部偏青、亮部偏品红，双色分离并提高饱和
        color = mix(color, color * vec3(0.38, 0.96, 1.32) + vec3(0.0, 0.008, 0.026),
                (1.0 - smoothstep(0.10, 0.62, luma)) * 0.32);
        color = mix(color, color * vec3(1.18, 0.58, 1.06),
                smoothstep(0.38, 0.95, luma) * 0.26);
        color = mix(vec3(luma), color, 1.10);
    }
    return color;
}

// ──────────────────────────────────────────────────────────────
//  液态玻璃：分频波纹 + 解析法线折射 + 波峰高光 + 薄膜虹彩
// ──────────────────────────────────────────────────────────────
vec3 liquid(vec3 world, vec2 vUv, vec2 uv, vec2 p, float aspect,
            float time, float bass, float mid, float treble, float energy,
            float beat, float intensity, vec3 accent) {
    // 整体脉冲：鼓点把画面微微向外推
    float pulse = beat * 0.65 + bass * 0.35;
    vec2 p0 = p * (1.0 - 0.014 * pulse * intensity);

    // 屏幕轻晃：慢速低幅的呼吸式摇摆
    vec2 shake = vec2(sin(time * 17.0) * 0.6 + sin(time * 11.0) * 0.4,
                      cos(time * 13.0) * 0.6 + cos(time * 9.0) * 0.4);
    vec2 jitter = shake * (0.0007 * beat + 0.0003 * treble) * intensity;

    // 基础流场：两层域扭曲，中频让流体卷得更快
    vec2 flow = p0 * 1.6;
    float n1 = fbm(flow + vec2(time * 0.10, -time * 0.07));
    float n2 = fbm3(flow * 1.28 + vec2(-time * 0.09, time * 0.12) + n1 * 0.85);
    vec2 warp = (vec2(n1, n2) - 0.5) * (0.012 + 0.028 * mid + 0.016 * energy) * intensity;
    warp += vec2(n2 - 0.5, n1 - 0.5) * treble * 0.006 * intensity;

    // 噪声位移 + 随半径增长的旋转：同心圆被打散成带旋臂的不规则浪
    float swirl = (n1 - 0.5) * 2.4 + length(p0) * 1.2;
    float swirlCos = cos(swirl);
    float swirlSin = sin(swirl);
    vec2 rippleP = mat2(swirlCos, -swirlSin, swirlSin, swirlCos) * p0;
    rippleP += (vec2(n1, n2) - 0.5) * (0.22 + 0.22 * mid);

    // 分频波纹场：四个缓慢漂移的水滴源
    float height = 0.0;
    vec2 slope = vec2(0.0);

    vec2 srcBass = vec2(sin(time * 0.19 + 4.1), cos(time * 0.23 + 1.7)) * vec2(0.34, 0.24);
    vec2 srcMidA = vec2(sin(time * 0.27 + 0.6), cos(time * 0.31 + 3.2)) * vec2(0.48, 0.34);
    vec2 srcMidB = vec2(sin(time * 0.33 + 2.4), cos(time * 0.29 + 0.9)) * vec2(0.52, 0.36);
    vec2 srcTreble = vec2(sin(time * 0.41 + 5.0), cos(time * 0.37 + 2.6)) * vec2(0.30, 0.22);

    rippleAdd(height, slope, rippleP, srcBass,
            7.5, 1.60, 1.00, 0.18 + 0.80 * bass, time,
            0.65 + 0.30 * mid);

    rippleAdd(height, slope, rippleP, srcMidA,
            13.0, 2.70, 2.40, 0.12 + 0.45 * mid, time,
            0.90);
    rippleAdd(height, slope, rippleP, srcMidB,
            17.0, 3.30, 3.00, 0.09 + 0.32 * mid, time,
            0.75 + 0.30 * treble);

    rippleAdd(height, slope, rippleP, srcTreble,
            33.0, 4.60, 5.20, 0.05 + 0.22 * treble, time,
            0.55);

    // 法线 = 波纹斜率
    vec2 refraction = slope * 0.0006 * intensity + warp + jitter;
    refraction = clamp(refraction, vec2(-0.038), vec2(0.038));

    // 分通道折射：高频越强，色散越明显
    vec2 sampleUv = (vUv - 0.5) * (1.0 + 0.005 * pulse * intensity) + 0.5;
    float dispersion = (0.0016 + 0.006 * treble) * intensity;
    vec3 color;
    color.r = texture(Sampler0, sampleUv + refraction * (1.0 + dispersion)).r;
    color.g = texture(Sampler0, sampleUv + refraction).g;
    color.b = texture(Sampler0, sampleUv + refraction * (1.0 - dispersion)).b;

    color = saturateColor(color, 1.20);
    color = pow(max(color, vec3(0.0)), vec3(0.95));

    // 水面法线：斜率柔性压缩
    float normalStrength = 1.0 + 1.4 * intensity;
    vec2 slopeN = slope * (0.06 * normalStrength);
    slopeN = slopeN / (1.0 + length(slopeN));
    vec3 normal = normalize(vec3(-slopeN, 1.0));
    vec3 lightDir = normalize(vec3(-0.42, 0.60, 0.68));
    float lambert = max(dot(normal, lightDir), 0.0);
    float crest = smoothstep(0.16, 0.82, height);

    // 双层高光：锐利星芒 + 柔和泛光晕
    float core = pow(lambert, 64.0);
    float halo = pow(lambert, 5.0);
    vec3 glint = mix(vec3(1.0), accent, 0.30);
    color += glint * (core * 0.50 + halo * 0.12)
            * (0.25 + 0.35 * beat + 0.15 * crest) * intensity;

    // 波峰自发光
    color += mix(accent, vec3(1.0), 0.35) * pow(crest, 3.0)
            * (0.030 + 0.050 * beat) * intensity;

    // 薄膜虹彩
    float fresnel = pow(1.0 - clamp(normal.z, 0.0, 1.0), 1.8);
    vec3 spectrum = 0.5 + 0.5 * cos(6.2831853
            * (vec3(0.00, 0.33, 0.67) + height * 0.35 + fresnel * 0.55 + time * 0.03));
    color += spectrum * fresnel * (0.09 + 0.16 * treble) * intensity;

    // 虹彩绸带
    float silkPhase = height * 5.0 + p0.y * 2.4 - p0.x * 0.6 - time * 0.35;
    float silk = pow(0.5 + 0.5 * sin(silkPhase), 4.0);
    vec3 silkTint = 0.5 + 0.5 * cos(6.2831853
            * (vec3(0.00, 0.33, 0.67) + silkPhase * 0.08 + time * 0.015));
    color += silkTint * silk * smoothstep(0.25, 0.95, length(p0))
            * (0.025 + 0.045 * mid) * intensity;

    // 中心柔光
    float centerGlow = exp(-dot(p0, p0) * 7.0);
    color += mix(accent, vec3(1.0), 0.45) * centerGlow
            * (0.020 + 0.045 * bass + 0.020 * beat) * intensity;

    // 焦散
    float caustic = smoothstep(0.66, 1.00,
            fbm3(p0 * 2.2 + slope * 1.8 + vec2(time * 0.17, -time * 0.13)));
    color += accent * caustic * (0.05 + 0.09 * energy) * intensity;

    // 星尘
    vec2 dustGrid = (p0 * vec2(0.55, 0.50) + vec2(time * 0.010, -time * 0.008)) * 110.0;
    vec2 dustCell = floor(dustGrid);
    vec2 dustLocal = fract(dustGrid) - 0.5;
    float dust = step(0.993, hash12(dustCell))
            * (1.0 - smoothstep(0.0, 0.18, length(dustLocal)));
    dust *= 0.5 + 0.5 * sin(time * 3.1 + hash12(dustCell) * 43.0);
    color += mix(vec3(1.0), accent, 0.40) * dust
            * (0.18 + 0.55 * treble) * intensity;

    // 漂浮光斑
    for (int i = 0; i < 4; i++) {
        float fi = float(i);
        vec2 center = vec2(sin(time * 0.13 + fi * 2.4), cos(time * 0.11 + fi * 1.6))
                * vec2(0.52, 0.34);
        float d = length(p0 - center);
        float radius = 0.070 + 0.020 * sin(time * 0.70 + fi * 2.0) + 0.018 * bass;
        float glow = exp(-d * d / max(radius * radius, 1.0E-4))
                * (0.020 + 0.040 * mid + 0.030 * beat);
        color += mix(accent, vec3(1.0), 0.35 + 0.30 * treble) * glow * intensity;
    }

    // 亮部柔光
    vec3 bright = max(color - vec3(0.70), vec3(0.0));
    color += bright * bright * 0.22 * intensity;
    color = saturateColor(color, 1.05);

    // 边缘辉光 / 暗角 / 颗粒
    float edge = smoothstep(0.55, 1.02, abs(p.x) + abs(p.y) * 0.62);
    color += accent * edge * (0.028 + 0.018 * energy);
    color = vignette(color, uv, 0.48);
    color += vec3(grain(uv, time) * 0.010);
    return color;
}

// ──────────────────────────────────────────────────────────────
//  极光：星空 + 三层流动光帘 + 地平线辉光
// ──────────────────────────────────────────────────────────────
vec3 aurora(vec3 world, vec2 uv, vec2 p, float time, float energy,
            float intensity, vec3 accent) {
    vec3 sky = mix(vec3(0.010, 0.018, 0.055), vec3(0.035, 0.045, 0.130), 1.0 - uv.y);

    vec2 grid = uv * vec2(2.2, 1.0) * 230.0;
    vec2 cell = floor(grid);
    vec2 local = fract(grid) - 0.5;
    float star = step(0.986, hash12(cell)) * (1.0 - smoothstep(0.0, 0.16, length(local)));
    star *= 0.55 + 0.45 * sin(time * 2.2 + hash12(cell) * 40.0);
    sky += vec3(0.78, 0.88, 1.0) * star * 0.95;

    float curtain = 0.0;
    for (int i = 0; i < 3; i++) {
        float fi = float(i);
        vec2 q = p * vec2(0.90, 1.45) + vec2(0.0, -0.28 + fi * 0.24);
        float n = fbm(q * 1.35 + vec2(time * (0.045 + fi * 0.012), time * 0.020));
        float ribbon = exp(-abs(q.y * 2.35 + (n - 0.5) * 1.9 + fi * 0.28) * 3.7);
        curtain += ribbon;
    }
    vec3 auroraColor = mix(vec3(0.12, 1.0, 0.60), accent, 0.52);
    auroraColor = mix(auroraColor, vec3(0.55, 0.35, 1.0), smoothstep(0.15, 1.0, uv.y));

    vec3 color = sky + auroraColor * curtain * (0.60 + 0.85 * energy) * intensity;
    color += accent * exp(-pow((uv.y - 0.30) * 5.5, 2.0)) * 0.07;

    // 底部保留世界的剪影感
    float ground = smoothstep(0.42, 0.60, uv.y);
    color = mix(color, world * 0.42 + color * 0.55, ground * 0.60);

    color = vignette(color, uv, 0.72);
    color += vec3(grain(uv, time) * 0.016);
    return color;
}

// ──────────────────────────────────────────────────────────────
//  电影感：慢推镜、青橙调色、暗角、颗粒、变形宽银幕与漏光
// ──────────────────────────────────────────────────────────────
vec3 cinematic(vec3 world, vec2 uv, vec2 screenUv, float time, float energy,
               float intensity, vec3 accent) {
    float zoom = 1.025 + 0.020 * sin(time * 0.055) + 0.030 * energy;
    vec2 cuv = (screenUv - 0.5) / zoom + 0.5;
    vec2 dir = screenUv - 0.5;
    float edge = dot(dir, dir);
    float chroma = 0.0035 * edge * intensity;

    vec3 base;
    base.r = texture(Sampler0, cuv + dir * chroma).r;
    base.g = texture(Sampler0, cuv).g;
    base.b = texture(Sampler0, cuv - dir * chroma).b;

    vec3 bloom = texture(Sampler0, cuv + vec2(0.006, 0.0)).rgb
            + texture(Sampler0, cuv - vec2(0.006, 0.0)).rgb
            + texture(Sampler0, cuv + vec2(0.0, 0.006)).rgb
            + texture(Sampler0, cuv - vec2(0.0, 0.006)).rgb;
    bloom *= 0.25;
    float bloomMask = smoothstep(0.55, 1.05, dot(bloom, vec3(0.333)));
    vec3 color = mix(base, max(base, bloom), bloomMask * 0.38);

    float luma = dot(color, vec3(0.299, 0.587, 0.114));
    color = mix(vec3(luma), color, 1.0 - 0.18 * intensity);
    color *= mix(vec3(0.90, 0.97, 1.10), vec3(1.08, 1.00, 0.90), uv.y);
    color += accent * 0.055 * smoothstep(0.55, 1.20, luma);

    // 2.39:1 宽银幕
    float bar = smoothstep(0.088, 0.104, uv.y) * (1.0 - smoothstep(0.900, 0.916, uv.y));
    color *= bar;

    // 斜向漏光
    float leak = exp(-pow((uv.x * 0.80 + uv.y * 0.60 - 0.86 - 0.05 * sin(time * 0.31)) * 3.4, 2.0));
    color += accent * leak * 0.10 * (0.4 + 0.6 * energy);

    color = vignette(color, uv, 0.95);
    color += vec3(grain(uv, time) * 0.028);
    return color;
}

void main() {
    vec2 uv = vec2(vUv.x, 1.0 - vUv.y);
    float aspect = IdleResolutionTime.x / max(IdleResolutionTime.y, 1.0);
    vec2 p = (uv - 0.5) * vec2(aspect, 1.0);

    float time = IdleResolutionTime.z;
    float fade = clamp(IdleResolutionTime.w, 0.0, 1.0);
    float bass = IdleAudioParams.x;
    float mid = IdleAudioParams.y;
    float treble = IdleAudioParams.z;
    float energy = clamp(IdleAudioParams.w * 0.72 + bass * 0.28, 0.0, 1.0);
    float intensity = IdleColorIntensity.a;
    vec3 accent = IdleColorIntensity.rgb;
    float beat = clamp(IdleMeta.w, 0.0, 1.0);
    float progress = clamp(IdleMeta.x, 0.0, 1.0);
    // IdleMeta.z = 预设编号 + 4 * 滤镜编号；1.21.1 无 define，预设按运行时分支
    float modeFloat = IdleMeta.z;
    int filterMode = int(floor(modeFloat * 0.25));
    int idleMode = int(floor(modeFloat)) - filterMode * 4;
    bool hasCover = IdleMeta.y > 0.0;

    vec3 world = texture(Sampler0, vUv).rgb;

    vec3 color;
    if (idleMode == 1) {
        color = aurora(world, uv, p, time, energy, intensity, accent);
    } else if (idleMode == 2) {
        color = cinematic(world, uv, vUv, time, energy, intensity, accent);
    } else {
        color = liquid(world, vUv, uv, p, aspect, time,
                bass, mid, treble, energy, beat, intensity, accent);
    }

    if (hasCover) {
        // 封面配色：取四个点近似平均色，让整体色调随专辑封面轻微偏移
        vec3 coverTint = textureLod(Sampler1, vec2(0.30, 0.30), 0.0).rgb
                + textureLod(Sampler1, vec2(0.70, 0.30), 0.0).rgb
                + textureLod(Sampler1, vec2(0.30, 0.70), 0.0).rgb
                + textureLod(Sampler1, vec2(0.70, 0.70), 0.0).rgb;
        coverTint = saturateColor(coverTint * 0.25, 1.15);
        color = mix(color, color * (0.72 + coverTint * 0.55), 0.14 * intensity);
    }

    // 高频只做很轻微的星点/闪光
    color += accent * treble * 0.008 * smoothstep(0.78, 0.99, valueNoise(p * 8.0 + time * 0.4));

    // 让所有预设都能感知节拍与播放进度
    float breath = 0.5 + 0.5 * sin(time * 1.3 + progress * 6.2831853);
    color *= 1.0 + 0.018 * beat * breath;
    color = mix(color, color * vec3(1.05, 1.0, 0.94), progress * 0.12);

    color = aces(color * (1.0 + 0.10 * energy));

    // 背景滤镜：整幅画面统一调色，未淡入时回落到纯世界底图
    color = applyFilter(color, filterMode);
    color = mix(applyFilter(world, filterMode), color, fade);
    fragColor = vec4(max(color, vec3(0.0)), 1.0);
}
