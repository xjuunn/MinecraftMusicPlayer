package com.junhsiun.musicplayer.client.gui;

import com.junhsiun.musicplayer.client.gui.glass.GlassPanel;
import com.junhsiun.musicplayer.client.gui.glass.layout.GlassLayout;
import com.junhsiun.musicplayer.client.gui.glass.theme.GlassTheme;
import com.junhsiun.musicplayer.client.gui.glass.widget.GlassButton;
import com.junhsiun.musicplayer.client.gui.glass.widget.GlassLabel;
import com.junhsiun.musicplayer.client.gui.glass.widget.GlassSearchField;
import com.junhsiun.musicplayer.client.gui.icon.SvgIcons;
import com.junhsiun.musicplayer.client.gui.media.MediaListView;
import com.junhsiun.musicplayer.client.gui.window.GlassWindow;
import com.junhsiun.musicplayer.client.gui.window.GlassWindowScreen;
import com.junhsiun.musicplayer.client.gui.window.WindowAnchor;
import com.junhsiun.musicplayer.client.gui.window.WindowAnimations;
import com.junhsiun.musicplayer.client.gui.window.WindowBounds;
import com.junhsiun.musicplayer.client.gui.window.WindowSize;
import com.junhsiun.musicplayer.client.media.MediaBrowserService;
import com.junhsiun.musicplayer.disc.MusicDiscHelper;
import com.junhsiun.musicplayer.model.MediaItem;
import com.junhsiun.musicplayer.model.MediaKind;
import com.junhsiun.musicplayer.model.MediaPage;
import com.junhsiun.musicplayer.network.DiscMasterBurnPayload;
import com.junhsiun.musicplayer.network.DiscMasterBurnResultPayload;
import com.junhsiun.musicplayer.platform.url.UrlUtil;
import com.junhsiun.musicplayer.util.Messages;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * 手持唱片母版右键打开的界面：搜索歌曲并刻录到母版。
 *
 * <p>布局自上而下三块：搜索行、结果列表、底栏（翻页 + 状态）。搜索结果走
 * {@link MediaBrowserService} 客户端直连；刻录只把歌曲 ID 发给服务端，
 * 曲目内容由服务端解析后写入唱片（见 {@code DiscMasterService}）。</p>
 *
 * <p><b>列表行为什么不自己画：</b>这里直接用按 M 打开的浏览界面那套
 * {@link MediaListView}——同一个序号、封面、歌名/歌手、时长、行尾玻璃按钮的排布。
 * 两个界面里"一列歌"看起来是同一种东西，不必维护两份排版。行尾按钮的动作换成刻录，
 * 图标也跟着换成唱片（{@link SvgIcons#DISC}），免得让人以为是"加入播放队列"。</p>
 *
 * <p><b>界面状态为什么存在字段里：</b>{@link #buildContent} 会在窗口缩放、
 * {@code rebuildWidgets()} 时重跑，控件每次都是新对象。关键词、结果、页码、状态文案
 * 都挂在界面实例上，重建后按同一份状态重新摆一遍即可，不会丢结果。</p>
 *
 * <p><b>为什么每帧检查手持物：</b>刻录成功后母版会变成刻好的唱片，界面再开着就没有意义；
 * 玩家把母版换走、丢掉时同理。这个判断比等服务端回包更普适，也不会漏掉命令、
 * 其他玩家等外部改动。</p>
 */
public final class DiscMasterScreen extends GlassWindowScreen implements MediaListView.Listener {

    /**
     * 窗口内容区尺寸。
     *
     * <p>宽度按列表行的排布反推（序号 + 封面 + 歌名/歌手 + 时长 + 行尾按钮 + 滚动条）：
     * 扣掉这些固定占位后还剩不到两百像素给文字，取 360 时长歌名和"歌手 · 专辑"都放得下；
     * 再长的名字由 {@link MediaListView} 截断并补省略号，不为了极端长的名字把窗口撑宽。</p>
     */
    private static final int WIDTH = 360;
    /**
     * 窗口高度。
     *
     * <p>按"列表至少整行显示七首"取的：行高 {@link MediaListView#ROW_HEIGHT}（32）乘以 7
     * 再加上搜索行与底栏。再多就得滚动，而这两个界面本来就都能滚。</p>
     */
    private static final int HEIGHT = 300;
    /**
     * 窗口边缘到内容区的内边距。
     *
     * <p>不能太小：窗口圆角半径是 {@link GlassTheme.Sizes#WINDOW_RADIUS}（30），
     * 玻璃圆弧在距边缘 {@code d} 处的纵向高度是 {@code R − √(R² − (R−d)²)}。
     * 取 8 时该高度约 9.6px，而搜索按钮高 22px、上沿只在 8px 处，右上角会越过弧线
     * 戳到圆角外面，看起来就是"按钮贴着圆角"。取 14 后弧线高度降到约 4.6px，
     * 按钮四边都有明显余量。</p>
     */
    private static final int PADDING = 14;

    private static final int SEARCH_HEIGHT = 22;
    private static final int FOOTER_HEIGHT = 16;
    private static final int GAP = 8;
    private static final int SEARCH_BUTTON_WIDTH = 64;
    private static final int PAGE_BUTTON_WIDTH = 48;

    /** 最近一次搜索的结果。 */
    private List<MediaItem> results = List.of();
    /** 最近一次搜索的关键词，翻页时沿用。 */
    private String keyword = "";
    /** 输入框里的当前文本。 */
    private String typed = "";
    /** 当前页码，从 1 开始。 */
    private int page = 1;
    /** 关键词的总命中数；-1 表示数据来源没给，此时底部不显示"共 N 首"。 */
    private int total = -1;
    /** 打开后是否还需要在下一 tick 再确认一次输入框焦点。 */
    private boolean refocusOnTick;
    /** 搜索序号：异步返回时用它丢弃过期结果。 */
    private int searchToken;
    /** 是否已发出刻录请求、正在等服务端回包。 */
    private boolean burning;
    /** 是否正在等搜索结果。 */
    private boolean loading;
    /** 底栏状态文案；列表为空时这句也画在列表中间。 */
    private Component status = Component.translatable("musicplayer.discmaster.hint");

    private GlassSearchField searchField;
    private MediaListView listView;
    private GlassLabel statusLabel;
    private GlassButton prevButton;
    private GlassButton nextButton;

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

        // 小屏（低分辨率 + 高 GUI 缩放）下窗口会被压扁，列表至少留两行
        int listHeight = Math.max(MediaListView.ROW_HEIGHT * 2,
                content.height() - SEARCH_HEIGHT - FOOTER_HEIGHT - GAP * 2);

        GlassLayout.Column column = new GlassLayout.Column().gap(GAP);
        column.fill(SEARCH_HEIGHT, this::buildSearchRow);
        column.fill(listHeight, this::buildList);
        column.fill(FOOTER_HEIGHT, this::buildFooter);
        column.layout(content);

        // 打开界面直接把焦点交给输入框。
        //
        // 原版 Screen.init(Minecraft,int,int) 结尾会调一次无参 setInitialFocus()，
        // 但它只在"最后一次输入是键盘"时才用 Tab 顺序选焦点；右键打开窗口时最后一次
        // 输入是鼠标，那一步什么都不做，于是要再点一下输入框才能打字。
        // 这个窗口唯一的用途就是搜索，没有理由不让它一打开就能输入。
        if (searchField != null) {
            setInitialFocus(searchField);
            refocusOnTick = true;
        }
    }

    // ── 搜索行 ──────────────────────────────────────────────────────────

    private void buildSearchRow(GlassLayout.Slot slot) {
        new GlassLayout.Row().gap(GAP)
                .grow(SEARCH_HEIGHT, field -> {
                    searchField = addRenderableWidget(GlassSearchField.create(field.x(), field.y(),
                            field.width(), field.height(),
                            Component.translatable("musicplayer.discmaster.search_hint"),
                            value -> typed = value));
                    searchField.setValue(typed);
                })
                .add(SEARCH_BUTTON_WIDTH, SEARCH_HEIGHT, button -> addRenderableWidget(
                        GlassButton.large(button.x(), button.y(), button.width(),
                                Component.translatable("musicplayer.discmaster.search"),
                                () -> search(1), GlassButton.Kind.PROMINENT)))
                .layout(slot.toBounds());
    }

    // ── 结果列表 ────────────────────────────────────────────────────────

    /**
     * 结果列表：和浏览界面同一个控件，行尾按钮换成刻录图标。
     *
     * <p>控件每次 {@code buildContent} 都是新的，所以这里必须把当前结果、加载态与
     * 状态文案一起推回去，否则窗口缩放一次列表就空了。</p>
     */
    private void buildList(GlassLayout.Slot slot) {
        listView = addRenderableWidget(
                new MediaListView(slot.x(), slot.y(), slot.width(), slot.height(), this));
        listView.setActionIcon(SvgIcons.DISC);
        refreshList();
    }

    // ── 底栏 ────────────────────────────────────────────────────────────

    private void buildFooter(GlassLayout.Slot slot) {
        new GlassLayout.Row().gap(GAP).align(GlassLayout.Align.CENTER)
                .add(PAGE_BUTTON_WIDTH, FOOTER_HEIGHT, prev -> prevButton = addRenderableWidget(
                        GlassButton.regular(prev.x(), prev.y(), prev.width(),
                                Component.translatable("musicplayer.discmaster.prev"),
                                () -> search(page - 1))))
                .grow(FOOTER_HEIGHT, label -> statusLabel = addRenderableWidget(
                        new GlassLabel(label.x(), label.y(), label.width(), label.height(),
                                status, GlassTheme.Text.LABEL, GlassTheme.TEXT_SECONDARY)))
                .add(PAGE_BUTTON_WIDTH, FOOTER_HEIGHT, next -> nextButton = addRenderableWidget(
                        GlassButton.regular(next.x(), next.y(), next.width(),
                                Component.translatable("musicplayer.discmaster.next"),
                                () -> search(page + 1))))
                .layout(slot.toBounds());
        refreshPageButtons();
    }

    // ── 搜索 ────────────────────────────────────────────────────────────

    /** 以给定页码发起搜索；关键词取输入框当前内容。 */
    private void search(int targetPage) {
        String value = (searchField == null ? typed : searchField.getValue()).trim();
        if (value.isEmpty()) {
            setStatus(Component.translatable("musicplayer.discmaster.empty"));
            return;
        }
        keyword = value;
        page = Math.max(1, targetPage);
        int token = ++searchToken;
        loading = true;
        setStatus(Component.translatable("musicplayer.discmaster.loading"));
        refreshList();
        refreshPageButtons();
        MediaBrowserService.getInstance()
                .search(MediaKind.SONG, keyword, page, MediaBrowserService.PAGE_SIZE)
                .whenComplete((result, throwable) ->
                        Minecraft.getInstance().execute(() -> applyResults(token, result, throwable)));
    }

    private void applyResults(int token, MediaPage result, Throwable throwable) {
        // 连点搜索时只认最后一次：旧请求的回包可能后到
        if (token != searchToken) {
            return;
        }
        loading = false;
        if (throwable != null) {
            results = List.of();
            total = -1;
            setStatus(Component.translatable("musicplayer.discmaster.failed",
                    Messages.textOrTranslatable(UrlUtil.rootMessageKey(throwable))));
        } else {
            results = result == null ? List.of() : result.items();
            // 总数只有数据来源给出时才有意义；拿不到就记 -1，界面不显示"共 N 首"
            total = result == null ? -1 : result.total();
            setStatus(statusComponent());
        }
        refreshList();
        // 结果到齐才播一次错峰入场 + 回到顶部（和浏览界面的列表一致）
        if (listView != null) {
            listView.resetAndReveal();
        }
        refreshPageButtons();
    }

    /**
     * 底部状态行。
     *
     * <p>"共 N 首"必须是<b>总命中数</b>，不是本页条数——本页条数最多等于每页条数，
     * 显示出来会让人以为搜索结果只有这么几首。数据来源给不出总数时干脆不显示这一段，
     * 只保留页码（第 1 页连页码也没意义，就留空）。</p>
     */
    private Component statusComponent() {
        if (results.isEmpty()) {
            return Component.translatable("musicplayer.discmaster.no_result");
        }
        if (total > 0) {
            return Component.translatable("musicplayer.discmaster.count", total, page);
        }
        if (page > 1) {
            return Component.translatable("musicplayer.discmaster.page", page);
        }
        return Component.empty();
    }

    /** 把当前状态同步到列表；正在刻录时行尾按钮不可点。 */
    private void refreshList() {
        if (listView == null) {
            return;
        }
        listView.setLoading(loading);
        listView.setStatus(status);
        listView.setActionEnabled(!burning);
        listView.showItems(results, false, false);
    }

    private void refreshPageButtons() {
        int pageSize = MediaBrowserService.PAGE_SIZE;
        if (prevButton != null) {
            prevButton.active = page > 1;
        }
        if (nextButton != null) {
            // 有总数就按总数判断，避免"最后一页刚好满"时还能再翻一页空结果；
            // 没有总数只能退回"本页是否满页"的经验判断
            nextButton.active = total > 0
                    ? (long) page * pageSize < total
                    : results.size() >= pageSize;
        }
    }

    // ── 刻录 ────────────────────────────────────────────────────────────

    private void burn(MediaItem item) {
        if (burning || item == null || item.id().isBlank()) {
            return;
        }
        burning = true;
        setStatus(Component.translatable("musicplayer.discmaster.burning"));
        refreshList();
        ClientPlayNetworking.send(new DiscMasterBurnPayload(item.id()));
    }

    /** 服务端刻录结果回包（由客户端网络接收器转发）。 */
    public void handleBurnResult(DiscMasterBurnResultPayload payload) {
        burning = false;
        if (payload.success()) {
            setStatus(Component.translatable("musicplayer.discmaster.burned", payload.message()));
            // 母版已经变成唱片，界面没必要留着；关闭动画照常播
            onClose();
            return;
        }
        setStatus(Messages.textOrTranslatable(payload.message()));
        refreshList();
    }

    private void setStatus(Component value) {
        status = value;
        if (statusLabel != null) {
            statusLabel.setMessage(value);
        }
        if (listView != null) {
            // 列表为空时这句话画在列表中间：搜索提示、无结果、失败原因都从这里出
            listView.setStatus(value);
        }
    }

    // ── 列表回调 ────────────────────────────────────────────────────────

    /** 单曲没有详情页，点行本身不做事（列表也不会给歌曲行画箭头或按下反馈）。 */
    @Override
    public void onItemClick(MediaItem item) {
    }

    /** 行尾按钮：把这首歌唱片刻录到手持的母版。 */
    @Override
    public void onPlay(MediaItem item) {
        burn(item);
    }

    /** 这里是翻页，不是"加载更多"。 */
    @Override
    public void onLoadMore() {
    }

    // ── 生命周期与输入 ──────────────────────────────────────────────────

    /**
     * 手持物一旦不再是母版就关界面：刻录成功后母版已被替换，换手 / 丢弃时同理。
     *
     * <p>母版可能在副手（右键判定覆盖双手），所以两只手都要看。</p>
     */
    @Override
    public void tick() {
        super.tick();
        if (refocusOnTick) {
            refocusOnTick = false;
            // 原版 Screen.init(Minecraft,int,int) 在 init() 收尾还会调一次无参
            // setInitialFocus()：当最后一次输入是键盘时，它按 Tab 顺序把焦点挪到
            // 下一个控件（搜索按钮）。等一 tick 再确认一次，两种打开方式都落在输入框上。
            if (searchField != null) {
                setFocused(searchField);
            }
        }
        if (closing()) {
            return;
        }
        if (!holdsMaster()) {
            onClose();
        }
    }

    private static boolean holdsMaster() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return false;
        }
        return MusicDiscHelper.isDiscMaster(player.getMainHandItem())
                || MusicDiscHelper.isDiscMaster(player.getOffhandItem());
    }

    /** 回车直接搜：EditBox 自己不处理回车，落到这里。 */
    @Override
    public boolean keyPressed(KeyEvent event) {
        // key() 是 GLFW 原始键码，回车与数字键盘回车各有一套常量
        boolean enter = event.key() == InputConstants.KEY_RETURN
                || event.key() == InputConstants.KEY_NUMPADENTER;
        boolean typing = searchField != null && searchField.isFocused();
        if (enter && typing) {
            search(1);
            return true;
        }
        // 不在输入框里时，方向键 / PgUp / PgDn / Home / End 直接滚列表：
        // 列表是窗口里唯一可滚的东西，不必先点一下才能用键盘
        if (!typing && listView != null && listView.keyPressed(event)) {
            return true;
        }
        return super.keyPressed(event);
    }
}
