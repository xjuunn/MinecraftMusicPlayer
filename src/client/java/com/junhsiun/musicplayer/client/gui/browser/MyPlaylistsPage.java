package com.junhsiun.musicplayer.client.gui.browser;

import com.junhsiun.musicplayer.client.ClientPlaylistStore;
import com.junhsiun.musicplayer.model.MediaItem;
import com.junhsiun.musicplayer.model.MediaKind;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/** 我的歌单：客户端本地收藏的歌单列表，换服务器也不会丢。 */
public final class MyPlaylistsPage extends BrowserPage {

    @Override
    public Component title() {
        return Component.translatable("musicplayer.app.my_playlists");
    }

    @Override
    public EnumSet<PageChrome> chrome() {
        return EnumSet.of(PageChrome.TITLE_BAR);
    }

    @Override
    public void apply() {
        List<MediaItem> items = new ArrayList<>();
        for (ClientPlaylistStore.SavedPlaylist playlist : ClientPlaylistStore.getInstance().all()) {
            String meta = playlist.trackCount > 0
                    ? Component.translatable("musicplayer.entry.track_count", playlist.trackCount).getString()
                    : null;
            items.add(new MediaItem(playlist.id, MediaKind.PLAYLIST,
                    playlist.title, playlist.subtitle, playlist.coverUrl, meta));
        }
        view().setHeader(null);
        view().setLoading(false);
        view().setStatus(items.isEmpty()
                ? Component.translatable("musicplayer.app.my_playlists_empty")
                : Component.empty());
        view().showItems(items, false, false);
        view().clearPagination();
    }

    @Override
    public void onResume(BrowserHost host) {
        apply();
    }
}
