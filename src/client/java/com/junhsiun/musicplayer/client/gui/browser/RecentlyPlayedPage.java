package com.junhsiun.musicplayer.client.gui.browser;

import com.junhsiun.musicplayer.client.ClientMusicUiState;
import com.junhsiun.musicplayer.model.MediaItem;
import com.junhsiun.musicplayer.model.MediaKind;
import com.junhsiun.musicplayer.model.RecentTrack;
import com.junhsiun.musicplayer.util.Messages;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/** 最近播放：服务端按玩家保存的历史，点击一条打开歌曲详情页。 */
public final class RecentlyPlayedPage extends BrowserPage {

    public RecentlyPlayedPage() {
        view().setSongsClickable(true);
    }

    @Override
    public Component title() {
        return Component.translatable("musicplayer.app.recent");
    }

    @Override
    public EnumSet<PageChrome> chrome() {
        return EnumSet.of(PageChrome.TITLE_BAR);
    }

    @Override
    public void apply() {
        List<MediaItem> items = new ArrayList<>();
        for (RecentTrack entry : ClientMusicUiState.getInstance().recent()) {
            String meta = entry.durationMillis() > 0L
                    ? Messages.formatDuration(entry.durationMillis())
                    : null;
            items.add(new MediaItem(entry.id(), MediaKind.SONG, entry.title(), entry.artist(),
                    entry.coverUrl(), meta));
        }
        view().setHeader(null);
        view().setLoading(false);
        view().setStatus(items.isEmpty()
                ? Component.translatable("musicplayer.app.recent_empty")
                : Component.empty());
        view().showItems(items, false, false);
        view().clearPagination();
    }

    @Override
    public void onResume(BrowserHost host) {
        apply();
    }
}
