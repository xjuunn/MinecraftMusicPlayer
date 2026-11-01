package com.junhsiun.musicplayer.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 统一配置模型的默认值与规范化行为。
 *
 * <p>只测纯数据层（不涉及 Fabric 的运行目录），因此可在普通 JVM 下跑。</p>
 */
class MusicPlayerConfigSectionTest {

    @Test
    void 新实例自带非空分节() {
        MusicPlayerConfig config = new MusicPlayerConfig();
        assertNotNull(config.client);
        assertNotNull(config.glass);
    }

    @Test
    void 玻璃分节默认值() {
        MusicPlayerConfig.GlassSettings glass = new MusicPlayerConfig.GlassSettings();
        assertEquals(0.0D, glass.opacity, 0.0001);
        assertEquals(0.20D, glass.blur, 0.0001);
        assertFalse(glass.shadow);
        assertEquals(30.0F, glass.refraction, 0.0001F);
        assertEquals(0.65F, glass.dispersion, 0.0001F);
        assertEquals(6.40F, glass.centerDispersion, 0.0001F);
        assertEquals(5.0F, glass.aDistance, 0.0001F);
        assertEquals(0.62F, glass.contraRatio, 0.0001F);
        assertEquals(7.34F, glass.shrink, 0.0001F);
    }

    @Test
    void 玻璃默认值与窗口材质一致() {
        // 配置默认值必须和 GlassStyle.WINDOW 里烘焙的值对得上，
        // 否则"恢复默认"会让外观突变。数值以文档表格为准。
        MusicPlayerConfig.GlassSettings glass = new MusicPlayerConfig.GlassSettings();
        assertEquals(30.0F, glass.refraction, 0.0001F);
        assertEquals(0.65F, glass.dispersion, 0.0001F);
        assertEquals(6.40F, glass.centerDispersion, 0.0001F);
        assertEquals(5.0F, glass.aDistance, 0.0001F);
        assertEquals(7.34F, glass.shrink, 0.0001F);
    }

    @Test
    void 客户端分节默认值() {
        MusicPlayerConfig.ClientSettings client = new MusicPlayerConfig.ClientSettings();
        assertEquals(0.7D, client.bgmVolume, 0.0001);
        assertTrue(client.enableNetworkBgm);
    }

    @Test
    void 玻璃分节规范化会把越界值拉回() {
        MusicPlayerConfig.GlassSettings glass = new MusicPlayerConfig.GlassSettings();
        glass.opacity = 5.0D;
        glass.blur = -3.0D;
        glass.refraction = -10.0F;
        glass.dispersion = 4.0F;
        glass.shrink = -1.0F;
        glass.normalize();

        assertEquals(1.0D, glass.opacity, 0.0001);
        assertEquals(0.0D, glass.blur, 0.0001);
        assertEquals(MusicPlayerConfig.GlassSettings.DEFAULT_REFRACTION, glass.refraction, 0.0001F);
        assertEquals(1.0F, glass.dispersion, 0.0001F);
        assertEquals(MusicPlayerConfig.GlassSettings.DEFAULT_SHRINK, glass.shrink, 0.0001F);
    }

    @Test
    void 玻璃分节规范化会保留合法值() {
        MusicPlayerConfig.GlassSettings glass = new MusicPlayerConfig.GlassSettings();
        glass.opacity = 0.8D;
        glass.refraction = 20.0F;
        glass.shrink = 5.0F;
        glass.normalize();

        assertEquals(0.8D, glass.opacity, 0.0001);
        assertEquals(20.0F, glass.refraction, 0.0001F);
        assertEquals(5.0F, glass.shrink, 0.0001F);
    }

    @Test
    void 玻璃分节重置恢复全部默认() {
        MusicPlayerConfig.GlassSettings glass = new MusicPlayerConfig.GlassSettings();
        glass.opacity = 0.9D;
        glass.blur = 0.1D;
        glass.shadow = true;
        glass.refraction = 5.0F;
        glass.dispersion = 0.1F;
        glass.centerDispersion = 1.0F;
        glass.aDistance = 2.0F;
        glass.contraRatio = 0.5F;
        glass.shrink = 1.0F;
        glass.resetToDefaults();

        assertEquals(0.0D, glass.opacity, 0.0001);
        assertEquals(0.20D, glass.blur, 0.0001);
        assertFalse(glass.shadow);
        assertEquals(30.0F, glass.refraction, 0.0001F);
        assertEquals(0.65F, glass.dispersion, 0.0001F);
        assertEquals(6.40F, glass.centerDispersion, 0.0001F);
        assertEquals(5.0F, glass.aDistance, 0.0001F);
        assertEquals(0.62F, glass.contraRatio, 0.0001F);
        assertEquals(7.34F, glass.shrink, 0.0001F);
    }

    @Test
    void 客户端分节重置不影响玻璃分节() {
        MusicPlayerConfig config = new MusicPlayerConfig();
        config.glass.refraction = 5.0F;
        config.client.resetToDefaults();
        assertEquals(5.0F, config.glass.refraction, 0.0001F);
    }

    @Test
    void 玻璃分节重置不影响客户端分节() {
        MusicPlayerConfig config = new MusicPlayerConfig();
        config.client.bgmVolume = 0.2D;
        config.glass.resetToDefaults();
        assertEquals(0.2D, config.client.bgmVolume, 0.0001);
    }

    @Test
    void 客户端音量NaN会被规范化() {
        MusicPlayerConfig.ClientSettings client = new MusicPlayerConfig.ClientSettings();
        client.bgmVolume = Double.NaN;
        client.normalize();
        assertEquals(0.7D, client.bgmVolume, 0.0001);
    }

    @Test
    void 分节字段互相独立() {
        MusicPlayerConfig a = new MusicPlayerConfig();
        MusicPlayerConfig b = new MusicPlayerConfig();
        a.glass.refraction = 1.0F;
        a.client.bgmVolume = 0.1D;
        assertEquals(30.0F, b.glass.refraction, 0.0001F);
        assertEquals(0.7D, b.client.bgmVolume, 0.0001);
        assertFalse(a.client == b.client);
        assertFalse(a.glass == b.glass);
    }
}
