package com.junhsiun.musicplayer.platform.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.junhsiun.musicplayer.model.MediaDetail;
import com.junhsiun.musicplayer.model.MediaItem;
import com.junhsiun.musicplayer.model.MediaKind;
import com.junhsiun.musicplayer.model.MediaPage;
import com.junhsiun.musicplayer.model.MediaStat;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 浏览界面数据模型的统一解析层。
 *
 * <p><b>为什么单独抽一层：</b>直连（{@code music.163.com} 的 {@code /api/} 明文接口）与自建后端
 * （binaryify 版 NeteaseCloudMusicApi，路径形如 {@code /cloudsearch}）返回的是<b>同一套网易云 JSON</b>，
 * 只是请求地址不同。字段映射写两份必然会分叉——一处改了 {@code coverImgUrl}，另一处忘了改，
 * 结果就是"直连有封面、走后端没封面"这种难查的差异。所以解析只写一份，两边各自只负责取回 JSON。</p>
 *
 * <p><b>字段全部实测过</b>（2026-11 直连 {@code music.163.com} 核对）：</p>
 * <ul>
 *   <li>搜索 {@code /api/cloudsearch/pc}：{@code result.songs|artists|playlists|userprofiles|djRadios}，
 *       总数在 {@code result.songCount|artistCount|playlistCount|userprofileCount|djRadiosCount}；</li>
 *   <li>歌单 {@code /api/v6/playlist/detail}：{@code playlist.coverImgUrl|description|playCount|subscribedCount|trackCount|creator.avatarUrl}；</li>
 *   <li>歌手 {@code /api/v1/artist/{id}}：{@code artist.picUrl|briefDesc|musicSize|albumSize}；</li>
 *   <li>用户 {@code /api/user/playlist}：{@code playlist[].creator.avatarUrl|nickname|signature}；</li>
 *   <li>播客 {@code /api/djradio/v2/get}：{@code data.picUrl|desc|programCount|subCount|playCount}；</li>
 *   <li>节目 {@code /api/dj/program/byradio}：{@code programs[].coverUrl|duration|mainTrackId}。</li>
 * </ul>
 *
 * <p>数据来源没给的字段一律留空，界面自己画占位——不猜、不编。</p>
 */
public final class MediaJson {

    /** 单曲搜索 / 歌单 / 歌手 / 用户 / 播客在云搜索里的类型码。 */
    public static int searchType(MediaKind kind) {
        return switch (kind) {
            case SONG -> 1;
            case ARTIST -> 100;
            case PLAYLIST -> 1000;
            case USER -> 1002;
            case RADIO -> 1009;
        };
    }

    private MediaJson() {
    }

    // ── 搜索 ────────────────────────────────────────────────────────────

    /** 解析云搜索响应（{@code {result: {...}}}）。 */
    public static MediaPage searchPage(JsonObject root, MediaKind kind) {
        JsonObject result = ApiJson.obj(root, "result");
        if (result == null) {
            return MediaPage.EMPTY;
        }
        List<MediaItem> items = searchItems(result, kind);
        return new MediaPage(items, totalOf(result, kind));
    }

    private static List<MediaItem> searchItems(JsonObject result, MediaKind kind) {
        JsonArray list = ApiJson.arr(result, listKey(kind));
        if (list == null || list.isEmpty()) {
            return List.of();
        }
        List<MediaItem> items = new ArrayList<>(list.size());
        for (JsonElement element : list) {
            if (!element.isJsonObject()) {
                continue;
            }
            MediaItem item = searchItem(element.getAsJsonObject(), kind);
            if (item != null) {
                items.add(item);
            }
        }
        return items;
    }

    private static String listKey(MediaKind kind) {
        return switch (kind) {
            case SONG -> "songs";
            case ARTIST -> "artists";
            case PLAYLIST -> "playlists";
            case USER -> "userprofiles";
            case RADIO -> "djRadios";
        };
    }

    private static int totalOf(JsonObject result, MediaKind kind) {
        return switch (kind) {
            case SONG -> ApiJson.intVal(result, "songCount");
            case ARTIST -> ApiJson.intVal(result, "artistCount");
            case PLAYLIST -> ApiJson.intVal(result, "playlistCount");
            case USER -> ApiJson.intVal(result, "userprofileCount");
            case RADIO -> ApiJson.intVal(result, "djRadiosCount");
        };
    }

    private static MediaItem searchItem(JsonObject node, MediaKind kind) {
        return switch (kind) {
            case SONG -> {
                String id = ApiJson.str(node, "id");
                if (id.isBlank()) {
                    yield null;
                }
                yield new MediaItem(id, kind, ApiJson.str(node, "name"),
                        songSubtitle(node), ApiJson.albumCover(node), songMeta(node));
            }
            case ARTIST -> {
                String id = ApiJson.str(node, "id");
                if (id.isBlank()) {
                    yield null;
                }
                yield new MediaItem(id, kind, ApiJson.str(node, "name"), artistSubtitle(node),
                        ApiJson.firstNonBlank(node, "picUrl", "img1v1Url"));
            }
            case PLAYLIST -> {
                String id = ApiJson.str(node, "id");
                if (id.isBlank()) {
                    yield null;
                }
                JsonObject creator = ApiJson.obj(node, "creator");
                int trackCount = ApiJson.intVal(node, "trackCount");
                StringBuilder subtitle = new StringBuilder(ApiJson.str(creator, "nickname"));
                if (trackCount > 0) {
                    if (!subtitle.isEmpty()) {
                        subtitle.append(" · ");
                    }
                    subtitle.append(count(trackCount)).append(' ')
                            .append(Component.translatable("musicplayer.browser.stat.tracks").getString());
                }
                yield new MediaItem(id, kind, ApiJson.str(node, "name"), subtitle.toString(),
                        ApiJson.firstNonBlank(node, "coverImgUrl", "picUrl"), playMeta(node));
            }
            case USER -> {
                String id = ApiJson.str(node, "userId");
                if (id.isBlank()) {
                    yield null;
                }
                yield new MediaItem(id, kind, ApiJson.str(node, "nickname"),
                        ApiJson.str(node, "signature"), ApiJson.firstNonBlank(node, "avatarUrl", "avatar"));
            }
            case RADIO -> {
                String id = ApiJson.str(node, "id");
                if (id.isBlank()) {
                    yield null;
                }
                yield new MediaItem(id, kind, ApiJson.str(node, "name"),
                        ApiJson.firstNonBlank(node, "category", "secondCategory"),
                        ApiJson.firstNonBlank(node, "picUrl", "coverUrl"));
            }
        };
    }

    // ── 详情 ────────────────────────────────────────────────────────────

    /** 歌手行的副标题：有别名用别名（"Jay Chou"），没有就报作品数。 */
    private static String artistSubtitle(JsonObject node) {
        JsonArray alias = ApiJson.arr(node, "alias");
        if (alias != null) {
            for (JsonElement element : alias) {
                if (element.isJsonPrimitive()) {
                    String text = element.getAsString();
                    if (!text.isBlank()) {
                        return text;
                    }
                }
            }
        }
        int songs = ApiJson.intVal(node, "musicSize");
        return songs > 0
                ? count(songs) + " " + Component.translatable("musicplayer.browser.stat.songs").getString()
                : "";
    }

    /** 歌单详情（{@code {playlist: {...}}}）。 */
    public static MediaDetail playlistDetail(JsonObject root) {
        JsonObject playlist = ApiJson.obj(root, "playlist");
        if (playlist == null) {
            return MediaDetail.empty(MediaKind.PLAYLIST, "");
        }
        JsonObject creator = ApiJson.obj(playlist, "creator");
        int trackCount = ApiJson.intVal(playlist, "trackCount");
        String description = ApiJson.str(playlist, "description");
        if (description.isBlank()) {
            description = String.join(" / ", tags(playlist));
        }
        return new MediaDetail(
                ApiJson.str(playlist, "id"),
                MediaKind.PLAYLIST,
                ApiJson.str(playlist, "name"),
                ApiJson.str(creator, "nickname"),
                ApiJson.firstNonBlank(playlist, "coverImgUrl", "picUrl"),
                description,
                stats(
                        stat("musicplayer.browser.stat.tracks", trackCount),
                        stat("musicplayer.browser.stat.play", ApiJson.lng(playlist, "playCount")),
                        stat("musicplayer.browser.stat.subscribed", ApiJson.lng(playlist, "subscribedCount"))
                ));
    }

    /** 歌手详情（{@code {artist: {...}}}）。 */
    public static MediaDetail artistDetail(JsonObject root) {
        JsonObject artist = ApiJson.obj(root, "artist");
        if (artist == null) {
            return MediaDetail.empty(MediaKind.ARTIST, "");
        }
        return new MediaDetail(
                ApiJson.str(artist, "id"),
                MediaKind.ARTIST,
                ApiJson.str(artist, "name"),
                joinAlias(artist),
                ApiJson.firstNonBlank(artist, "picUrl", "img1v1Url"),
                ApiJson.str(artist, "briefDesc"),
                stats(
                        stat("musicplayer.browser.stat.songs", ApiJson.intVal(artist, "musicSize")),
                        stat("musicplayer.browser.stat.albums", ApiJson.intVal(artist, "albumSize"))
                ));
    }

    /**
     * 用户详情（{@code /api/v1/user/detail/{uid}} 的 {@code profile}）。
     *
     * <p>退路：数据来源只给了歌单列表时，从 {@code playlist[].creator} 里捡昵称、头像、签名——
     * 至少不会显示成一页空白。</p>
     */
    public static MediaDetail userDetail(JsonObject root, String userId) {
        JsonObject profile = ApiJson.obj(root, "profile");
        if (profile != null) {
            return new MediaDetail(
                    ApiJson.str(profile, "userId", userId),
                    MediaKind.USER,
                    ApiJson.str(profile, "nickname"),
                    null,
                    ApiJson.firstNonBlank(profile, "avatarUrl", "avatar"),
                    ApiJson.str(profile, "signature"),
                    stats(
                            stat("musicplayer.browser.stat.playlists", ApiJson.intVal(profile, "playlistCount")),
                            stat("musicplayer.browser.stat.follows", ApiJson.intVal(profile, "follows")),
                            stat("musicplayer.browser.stat.followers", ApiJson.intVal(profile, "followeds"))
                    ));
        }
        JsonArray playlists = ApiJson.arr(root, "playlist");
        JsonObject creator = null;
        if (playlists != null) {
            for (JsonElement element : playlists) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject candidate = ApiJson.obj(element.getAsJsonObject(), "creator");
                if (candidate == null) {
                    continue;
                }
                creator = candidate;
                if (!userId.isBlank() && ApiJson.str(candidate, "userId").equals(userId)) {
                    break;
                }
            }
        }
        int count = playlists == null ? 0 : playlists.size();
        return new MediaDetail(
                userId,
                MediaKind.USER,
                creator == null ? "" : ApiJson.str(creator, "nickname"),
                null,
                creator == null ? "" : ApiJson.firstNonBlank(creator, "avatarUrl", "avatar"),
                creator == null ? "" : ApiJson.str(creator, "signature"),
                stats(stat("musicplayer.browser.stat.playlists", count)));
    }

    /** 播客详情（{@code {data: {...}}}）。 */
    public static MediaDetail radioDetail(JsonObject root) {
        JsonObject data = ApiJson.obj(root, "data");
        if (data == null) {
            data = ApiJson.obj(root, "djRadio");
        }
        if (data == null) {
            return MediaDetail.empty(MediaKind.RADIO, "");
        }
        String category = ApiJson.firstNonBlank(data, "category", "secondCategory");
        return new MediaDetail(
                ApiJson.str(data, "id"),
                MediaKind.RADIO,
                ApiJson.str(data, "name"),
                category,
                ApiJson.firstNonBlank(data, "picUrl", "coverUrl"),
                ApiJson.str(data, "desc"),
                stats(
                        stat("musicplayer.browser.stat.programs", ApiJson.intVal(data, "programCount")),
                        stat("musicplayer.browser.stat.subscribed", ApiJson.intVal(data, "subCount")),
                        stat("musicplayer.browser.stat.play", ApiJson.lng(data, "playCount"))
                ));
    }

    /** 单曲详情（{@code {songs: [...]}}）。 */
    public static MediaDetail songDetail(JsonObject root, String songId) {
        JsonObject song = firstObject(ApiJson.arr(root, "songs"));
        if (song == null) {
            return MediaDetail.empty(MediaKind.SONG, songId);
        }
        return new MediaDetail(
                ApiJson.str(song, "id"),
                MediaKind.SONG,
                ApiJson.str(song, "name"),
                artistOf(song),
                coverOf(song),
                albumNameOf(song),
                stats(new MediaStat(Component.empty(), formatDuration(durationOf(song)))));
    }

    // ── 详情内的条目分页 ─────────────────────────────────────────────────

    /** 歌单 / 歌手曲目（{@code {songs: [...]}}），带专辑封面。 */
    public static List<MediaItem> songItems(JsonObject root) {
        JsonArray songs = ApiJson.arr(root, "songs");
        if (songs == null || songs.isEmpty()) {
            return List.of();
        }
        List<JsonObject> nodes = new ArrayList<>(songs.size());
        for (JsonElement element : songs) {
            if (element.isJsonObject()) {
                nodes.add(element.getAsJsonObject());
            }
        }
        return songNodes(nodes);
    }

    /** 已经取到歌曲节点时用这个重载，省一次包装成 {@code {songs: [...]}} 的功夫。 */
    public static List<MediaItem> songNodes(List<JsonObject> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return List.of();
        }
        List<MediaItem> items = new ArrayList<>(nodes.size());
        for (JsonObject song : nodes) {
            String id = ApiJson.str(song, "id");
            if (id.isBlank()) {
                continue;
            }
            items.add(new MediaItem(id, MediaKind.SONG, ApiJson.str(song, "name"),
                    songSubtitle(song), coverOf(song), songMeta(song)));
        }
        return items;
    }

    /**
     * 歌曲副标题：{@code 歌手 · 专辑}。
     *
     * <p>只写歌手的话，一行里除了歌名就只有一个人名，右边再空着一大片；
     * 补上专辑名既填了空白，也更容易一眼认出是哪一首（同名曲目靠专辑区分）。
     * 专辑缺失或与歌手同名时退化成只留歌手。</p>
     */
    private static String songSubtitle(JsonObject song) {
        String artist = artistOf(song);
        String album = ApiJson.albumName(song);
        if (album.isBlank() || album.equals(artist)) {
            return artist;
        }
        if (artist.isBlank()) {
            return album;
        }
        return artist + " · " + album;
    }

    /**
     * 列表右侧的补充信息：歌曲显示时长，歌单显示播放量。
     *
     * <p>没有这行字的时候，标题左边的封面和右边的大片空白让每一行看起来都很空；
     * 补一条右对齐的次要信息既填了空白，也让"哪首更长、哪个歌单更热"一眼可见。
     * 数据源没给就返回 {@code null}，界面那边整段不画，不会留一个空的占位。</p>
     */
    private static String songMeta(JsonObject song) {
        long millis = durationOf(song);
        return millis > 0L ? formatDuration(millis).getString() : null;
    }

    private static String playMeta(JsonObject node) {
        long plays = ApiJson.lng(node, "playCount");
        if (plays <= 0L) {
            return null;
        }
        return count(plays).getString() + " "
                + Component.translatable("musicplayer.browser.stat.play").getString();
    }

    /**
     * 歌曲封面。
     *
     * <p>网易云有<b>两套</b>歌曲节点形状：搜索/详情用新结构（{@code al.picUrl}、{@code ar[]}），
     * 而 {@code /artist/songs} 这类老接口用 {@code album.picUrl}、{@code artists[]}。
     * 只认其中一种，就会出现"歌手页的歌全是占位图"。</p>
     */
    private static String coverOf(JsonObject song) {
        String cover = ApiJson.albumCover(song);
        if (!cover.isBlank()) {
            return cover;
        }
        JsonObject album = ApiJson.obj(song, "album");
        return album == null ? "" : ApiJson.firstNonBlank(album, "picUrl", "blurPicUrl");
    }

    private static String albumNameOf(JsonObject song) {
        String name = ApiJson.albumName(song);
        if (!name.isBlank()) {
            return name;
        }
        JsonObject album = ApiJson.obj(song, "album");
        return album == null ? "" : ApiJson.str(album, "name");
    }

    private static String artistOf(JsonObject song) {
        String artist = ApiJson.firstArtistName(song);
        if (!artist.isBlank()) {
            return artist;
        }
        JsonArray artists = ApiJson.arr(song, "artists");
        if (artists == null) {
            return "";
        }
        for (JsonElement element : artists) {
            if (!element.isJsonObject()) {
                continue;
            }
            String name = ApiJson.str(element.getAsJsonObject(), "name");
            if (!name.isBlank()) {
                return name;
            }
        }
        return "";
    }

    private static long durationOf(JsonObject song) {
        long millis = ApiJson.durationMillis(song);
        return millis > 0L ? millis : ApiJson.lng(song, "duration");
    }

    /** 用户的歌单（{@code {playlist: [...]}}）。 */
    public static List<MediaItem> playlistItems(JsonObject root) {
        JsonArray playlists = ApiJson.arr(root, "playlist");
        if (playlists == null || playlists.isEmpty()) {
            return List.of();
        }
        List<MediaItem> items = new ArrayList<>(playlists.size());
        for (JsonElement element : playlists) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject node = element.getAsJsonObject();
            String id = ApiJson.str(node, "id");
            if (id.isBlank()) {
                continue;
            }
            JsonObject creator = ApiJson.obj(node, "creator");
            items.add(new MediaItem(id, MediaKind.PLAYLIST, ApiJson.str(node, "name"),
                    ApiJson.str(creator, "nickname"),
                    ApiJson.firstNonBlank(node, "coverImgUrl", "picUrl")));
        }
        return items;
    }

    /** 播客节目（{@code {programs: [...]}}）。 */
    public static List<MediaItem> programItems(JsonObject root) {
        JsonArray programs = ApiJson.arr(root, "programs");
        if (programs == null || programs.isEmpty()) {
            return List.of();
        }
        List<MediaItem> items = new ArrayList<>(programs.size());
        for (JsonElement element : programs) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject node = element.getAsJsonObject();
            String id = ApiJson.str(node, "id");
            if (id.isBlank()) {
                continue;
            }
            JsonObject radio = ApiJson.obj(node, "radio");
            String subtitle = formatDuration(ApiJson.lng(node, "duration")).getString();
            String radioName = ApiJson.str(radio, "name");
            if (!radioName.isBlank()) {
                subtitle = radioName + " · " + subtitle;
            }
            items.add(new MediaItem(id, MediaKind.RADIO, ApiJson.str(node, "name"), subtitle,
                    ApiJson.firstNonBlank(node, "coverUrl", "blurCoverUrl")));
        }
        return items;
    }

    // ── 小工具 ──────────────────────────────────────────────────────────

    private static JsonObject firstObject(JsonArray array) {
        if (array == null) {
            return null;
        }
        for (JsonElement element : array) {
            if (element.isJsonObject()) {
                return element.getAsJsonObject();
            }
        }
        return null;
    }

    private static List<String> tags(JsonObject playlist) {
        JsonArray tags = ApiJson.arr(playlist, "tags");
        if (tags == null || tags.isEmpty()) {
            return List.of();
        }
        List<String> result = new ArrayList<>(tags.size());
        for (JsonElement element : tags) {
            if (element.isJsonPrimitive()) {
                result.add(element.getAsString());
            }
        }
        return result;
    }

    private static String joinAlias(JsonObject artist) {
        JsonArray alias = ApiJson.arr(artist, "alias");
        if (alias == null || alias.isEmpty()) {
            return null;
        }
        List<String> names = new ArrayList<>(alias.size());
        for (JsonElement element : alias) {
            if (element.isJsonPrimitive() && !element.getAsString().isBlank()) {
                names.add(element.getAsString());
            }
        }
        return names.isEmpty() ? null : String.join(" / ", names);
    }

    private static MediaStat stat(String labelKey, int value) {
        return new MediaStat(Component.translatable(labelKey), count(value));
    }

    private static MediaStat stat(String labelKey, long value) {
        return new MediaStat(Component.translatable(labelKey), count(value));
    }

    private static MediaStat stat(String labelKey, Component value) {
        return new MediaStat(Component.translatable(labelKey), value);
    }

    private static List<MediaStat> stats(MediaStat... stats) {
        List<MediaStat> result = new ArrayList<>(stats.length);
        for (MediaStat stat : stats) {
            if (!stat.isEmpty()) {
                result.add(stat);
            }
        }
        return result;
    }

    /** 播放量这类大数字按"万 / 亿"缩写，详情页的小标签放不下长数字。 */
    public static Component count(long value) {
        if (value < 10_000L) {
            return Component.literal(Long.toString(Math.max(0L, value)));
        }
        if (value < 100_000_000L) {
            return Component.translatable("musicplayer.browser.count.ten_thousand",
                    Component.literal(trim(value / 10_000.0D)));
        }
        return Component.translatable("musicplayer.browser.count.hundred_million",
                Component.literal(trim(value / 100_000_000.0D)));
    }

    private static String trim(double value) {
        String text = String.format(java.util.Locale.ROOT, "%.1f", value);
        return text.endsWith(".0") ? text.substring(0, text.length() - 2) : text;
    }

    /** 毫秒 → {@code m:ss}。 */
    public static Component formatDuration(long millis) {
        long totalSeconds = Math.max(0L, millis) / 1000L;
        return Component.literal(String.format(java.util.Locale.ROOT, "%d:%02d",
                totalSeconds / 60L, totalSeconds % 60L));
    }
}
