package com.junhsiun.musicplayer.client.gui;

import com.junhsiun.musicplayer.client.gui.glass.GlassPanel;
import com.junhsiun.musicplayer.client.gui.glass.theme.GlassTheme;
import com.junhsiun.musicplayer.client.gui.window.GlassWindow;
import com.junhsiun.musicplayer.client.gui.window.GlassWindowScreen;
import com.junhsiun.musicplayer.client.gui.window.WindowAnchor;
import com.junhsiun.musicplayer.client.gui.window.WindowAnimations;
import com.junhsiun.musicplayer.client.gui.window.WindowBounds;
import com.junhsiun.musicplayer.client.gui.window.WindowSize;

/**
 * 手持唱片母版打开的界面。
 *
 * <p>目前是一个空窗口：只验证窗口框架本身（定位、开场动画、液态玻璃背景、投影）。
 * 内容控件留待后续接入。</p>
 */
public final class DiscMasterScreen extends GlassWindowScreen {

    /** 窗口内容区尺寸。 */
    private static final int WIDTH = 420;
    private static final int HEIGHT = 260;
    /** 窗口边缘到内容区的内边距。 */
    private static final int PADDING = 8;

    public DiscMasterScreen() {
        super(new GlassWindow()
                .style(GlassTheme.windowStyle())
                .size(WindowSize.preferred(WIDTH + PADDING * 2, HEIGHT + PADDING * 2, 32))
                .anchor(WindowAnchor.CENTER)
                .contentPadding(PADDING, PADDING)
                .opening(WindowAnimations.riseFromBottom()));
    }

    @Override
    protected void buildContent(WindowBounds content) {
        window().style(GlassTheme.windowStyle());
        // 管线编译与离屏贴图创建放在这里（渲染循环之外），避免首帧黑屏
        GlassPanel.prewarm(window().style());
    }
}
