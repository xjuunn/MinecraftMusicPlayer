package com.junhsiun.musicplayer.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.junhsiun.musicplayer.MusicPlayerMod;
import com.junhsiun.musicplayer.util.FileAtomicWrite;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 客户端歌单收藏。
 *
 * <p>与「最近播放」不同，这份数据属于客户端：跟着客户端配置目录走，不随服务器切换而丢失，
 * 也不需要管理员权限。文件放在 {@code config/musicplayer-playlists.json}。</p>
 */
public final class ClientPlaylistStore {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir()
            .resolve("musicplayer-playlists.json");
    private static final ClientPlaylistStore INSTANCE = new ClientPlaylistStore();

    /** 一条收藏的歌单。字段保持简单，方便直接序列化。 */
    public static final class SavedPlaylist {
        public String id = "";
        public String title = "";
        public String subtitle = "";
        public String coverUrl = "";
        public int trackCount;
        public long savedAt;

        public SavedPlaylist() {
        }

        public SavedPlaylist(String id, String title, String subtitle, String coverUrl,
                             int trackCount, long savedAt) {
            this.id = id == null ? "" : id;
            this.title = title == null ? "" : title;
            this.subtitle = subtitle == null ? "" : subtitle;
            this.coverUrl = coverUrl == null ? "" : coverUrl;
            this.trackCount = Math.max(0, trackCount);
            this.savedAt = savedAt;
        }
    }

    private final List<SavedPlaylist> playlists = new ArrayList<>();
    private boolean loaded;

    private ClientPlaylistStore() {
    }

    public static ClientPlaylistStore getInstance() {
        return INSTANCE;
    }

    public synchronized List<SavedPlaylist> all() {
        load();
        return List.copyOf(playlists);
    }

    public synchronized boolean contains(String id) {
        load();
        if (id == null || id.isBlank()) {
            return false;
        }
        return playlists.stream().anyMatch(playlist -> id.equals(playlist.id));
    }

    public synchronized void add(SavedPlaylist playlist) {
        load();
        if (playlist == null || playlist.id == null || playlist.id.isBlank()) {
            return;
        }
        playlists.removeIf(existing -> playlist.id.equals(existing.id));
        playlists.addFirst(playlist);
        save();
    }

    public synchronized boolean remove(String id) {
        load();
        if (id == null || id.isBlank()) {
            return false;
        }
        boolean removed = playlists.removeIf(playlist -> id.equals(playlist.id));
        if (removed) {
            save();
        }
        return removed;
    }

    /** 切换收藏状态，返回切换后是否处于「已收藏」。 */
    public synchronized boolean toggle(SavedPlaylist playlist) {
        if (playlist == null || playlist.id == null || playlist.id.isBlank()) {
            return false;
        }
        if (contains(playlist.id)) {
            remove(playlist.id);
            return false;
        }
        add(playlist);
        return true;
    }

    private void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        if (Files.notExists(PATH)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(PATH)) {
            SavedPlaylist[] loadedPlaylists = GSON.fromJson(reader, SavedPlaylist[].class);
            if (loadedPlaylists == null) {
                return;
            }
            playlists.clear();
            for (SavedPlaylist playlist : loadedPlaylists) {
                if (playlist != null && playlist.id != null && !playlist.id.isBlank()) {
                    playlists.add(playlist);
                }
            }
        } catch (IOException | RuntimeException exception) {
            MusicPlayerMod.LOGGER.warn("读取客户端歌单收藏失败，将使用空列表。", exception);
        }
    }

    private void save() {
        try {
            FileAtomicWrite.writeString(PATH, GSON.toJson(playlists));
        } catch (IOException exception) {
            MusicPlayerMod.LOGGER.error("保存客户端歌单收藏失败。", exception);
        }
    }
}
