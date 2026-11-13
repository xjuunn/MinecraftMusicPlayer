package com.junhsiun.musicplayer.client.idle;

/**
 * 极轻量的音频能量分析器，供纯享特效做音乐响应。
 *
 * <p>分析点放在音频解码线程（{@code BackgroundMusicAudioDevice.writeImpl}），
 * 每收到一块 PCM 就更新一次低 / 中 / 高频能量与总响度，渲染线程只读结果。
 * 之所以不做 FFT：这里只需要"低频鼓点有多重、整体有多满"这类宏观量，
 * 一阶滤波 + RMS 已经足够，代价却只有一次 O(n) 扫描。</p>
 *
 * <p>结果全部是 0 - 1 的归一化值，并带有快起慢落包络，
 * 所以画面上不会出现逐帧跳变。</p>
 */
public final class AudioAnalyzer {

    /** 低频（约 150 Hz 以下）。 */
    private static volatile float bass;
    /** 中频（约 250 - 2000 Hz）。 */
    private static volatile float mid;
    /** 高频（约 2500 Hz 以上）。 */
    private static volatile float treble;
    /** 总响度。 */
    private static volatile float level;

    // 以下状态只在解码线程里读写，不需要同步。
    private static float lowState;
    private static float lowState2;
    private static float midState;
    private static float highState;
    private static float bassEnvelope;
    private static float midEnvelope;
    private static float trebleEnvelope;
    private static float levelEnvelope;

    private AudioAnalyzer() {
    }

    public static float bass() {
        return bass;
    }

    public static float mid() {
        return mid;
    }

    public static float treble() {
        return treble;
    }

    public static float level() {
        return level;
    }

    /**
     * 分析一块有符号 16 位 PCM。
     *
     * @param samples    样本数组
     * @param offset     起始下标
     * @param length     样本数量（含所有声道）
     * @param channels   声道数
     * @param sampleRate 采样率
     */
    public static void feed(short[] samples, int offset, int length, int channels, float sampleRate) {
        if (samples == null || length <= 0 || sampleRate <= 0.0F) {
            return;
        }
        int stride = Math.max(1, channels);
        int frames = Math.max(1, length / stride);

        // 一阶低通/高通系数：低频取 150 Hz，中频用 250 / 2000 Hz 两个拐点相减。
        float alphaLow = onePole(150.0F, sampleRate);
        float alphaLow2 = onePole(250.0F, sampleRate);
        float alphaMid = onePole(2000.0F, sampleRate);
        float alphaHigh = onePole(2500.0F, sampleRate);

        double sumAll = 0.0D;
        double sumBass = 0.0D;
        double sumMid = 0.0D;
        double sumTreble = 0.0D;

        for (int frame = 0; frame < frames; frame++) {
            int index = offset + frame * stride;
            if (index >= offset + length) {
                break;
            }
            float sample = samples[index] / 32768.0F;
            // 多声道取第一声道即可：背景音乐左右声道能量基本一致。
            lowState += (sample - lowState) * alphaLow;
            lowState2 += (sample - lowState2) * alphaLow2;
            midState += (sample - midState) * alphaMid;
            highState += (sample - highState) * alphaHigh;

            float lowBand = lowState;
            float midBand = midState - lowState2;
            float highBand = sample - highState;

            sumAll += sample * sample;
            sumBass += lowBand * lowBand;
            sumMid += midBand * midBand;
            sumTreble += highBand * highBand;
        }

        float norm = 1.0F / frames;
        float rmsAll = (float) Math.sqrt(sumAll * norm);
        float rmsBass = (float) Math.sqrt(sumBass * norm);
        float rmsMid = (float) Math.sqrt(sumMid * norm);
        float rmsTreble = (float) Math.sqrt(sumTreble * norm);

        levelEnvelope = envelope(levelEnvelope, curve(rmsAll * 3.2F), 0.62F, 0.10F);
        bassEnvelope = envelope(bassEnvelope, curve(rmsBass * 6.0F), 0.72F, 0.07F);
        midEnvelope = envelope(midEnvelope, curve(rmsMid * 9.0F), 0.66F, 0.10F);
        trebleEnvelope = envelope(trebleEnvelope, curve(rmsTreble * 16.0F), 0.80F, 0.19F);

        level = levelEnvelope;
        bass = bassEnvelope;
        mid = midEnvelope;
        treble = trebleEnvelope;
    }

    /** 播放停止时清空包络，避免下一条歌一开始带着上一条的余量。 */
    public static void reset() {
        lowState = 0.0F;
        lowState2 = 0.0F;
        midState = 0.0F;
        highState = 0.0F;
        bassEnvelope = 0.0F;
        midEnvelope = 0.0F;
        trebleEnvelope = 0.0F;
        levelEnvelope = 0.0F;
        bass = 0.0F;
        mid = 0.0F;
        treble = 0.0F;
        level = 0.0F;
    }

    private static float onePole(float cutoff, float sampleRate) {
        return (float) (1.0D - Math.exp(-2.0D * Math.PI * cutoff / sampleRate));
    }

    /** 软饱和：小信号近似线性，大信号压到 1 附近，避免爆音把特效闪白。 */
    private static float curve(float value) {
        if (value <= 0.0F) {
            return 0.0F;
        }
        return (float) Math.tanh(value);
    }

    /** 快起慢落包络，鼓点有冲击、松开时不抖。 */
    private static float envelope(float current, float target, float attack, float release) {
        float rate = target > current ? attack : release;
        return current + (target - current) * rate;
    }
}
