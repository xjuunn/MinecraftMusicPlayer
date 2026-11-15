# Minecraft Music Player

<p align="center">
  <img src="src/main/resources/assets/musicplayer/icon.png" width="128" alt="Minecraft Music Player icon">
</p>

[![中文](https://img.shields.io/badge/README-中文-blue)](README.md) [![English](https://img.shields.io/badge/README-English-orange)](README_EN.md)

Minecraft Music Player is a Fabric mod for both the server and the client: browse and request NetEase Cloud Music in game, with playlists and podcasts, real-time lyrics, network background music, burnable music discs, and jukebox playback.

The server handles commands, queues, configuration, and synchronization. The client handles audio playback and the UI. Install the mod on the server and on every client that needs to hear music.

[Demo](https://www.youtube.com/watch?v=4kOa9Ak62Bk)

## Features

- Liquid glass UI: the music browser, settings, and disc burning share one visual style and animation set.
- Music browsing: search songs, playlists, artists, users, and podcasts; save playlists and view recently played.
- Requests and queue: request single songs, priority request queue, automatic playlist refill, vote skip.
- Playlists and podcasts: loaded in batches in the background, with sequential, reverse, and shuffle order.
- Real-time lyrics: supported for global playback and jukeboxes, toggled per player.
- Network background music: takes over vanilla background music, with a separate toggle and volume.
- Idle immersive: hides the HUD after a period of inactivity and overlays configurable effects and lyrics.
- Music discs: burn songs onto discs and play them in jukeboxes, with cover art and distance-based volume.
- Sharing: send playlists, artists, users, podcasts, or links to other players.
- Simplified Chinese, English, and Japanese included.

### Music Browser

Open with the default key `M`; rebindable in `Options → Controls`.

- Left navigation: search, current queue, saved playlists, recently played, settings.
- Enter keywords in the search field, or paste a `music.163.com` song, playlist, or podcast link directly.
- The search page switches between songs, playlists, artists, users, and podcasts.
- List rows support play and add to queue; playlists can be saved from their detail page, and song details show lyrics.

### Idle Immersive

Enable it on the Client tab of the settings. After 60 seconds without keyboard or mouse input while music is playing, the mod hides the HUD and shows effects and lyrics; move the mouse or press any key to exit. The effect preset, background filter, idle delay, effect intensity, and lyric scale are configurable, and a preview is available.

### Music Discs

- Craft a **Music Disc Master** from an iron ingot and an amethyst shard.
- Right-click the air while holding the master to open the burning screen, then search for and burn a song.
- Burned discs play in jukeboxes; the jukebox shows cover art and its volume fades with distance.
- Loot containers can generate random music discs; the chance and count are configurable.

### Network Background Music

- The server picks the playlist through `bgmPlaylistId`; it starts playing when players join.
- Clients can toggle network background music and adjust its volume in `Options → Sound`.
- Falls back to vanilla background music when the API is unavailable.

### Sharing

`/music share <player> <playlist|artist|user|podcast|program|link>` sends the content as a clickable message; recipients can click it to open it in the music browser.

## Commands

Player commands:

| Purpose | Command |
| --- | --- |
| Playback control | `/music now`, `/music seek <seconds>`, `/music skip`, `/music mute once` |
| Requests | `/music play song <song ID>`, `/music play playlist <playlist ID> [reverse]`, `/music play program <program ID>`, `/music play radio <podcast ID> [reverse]` |
| Queue | `/music queue [page]`, `/music queue promote <song ID>`, `/music queue remove <song ID>` |
| Playback mode | `/music playlist`, `/music playlist list`, `/music playlist stop`, `/music playlist order [sequential\|reverse\|shuffle]` |
| Search | `/music search <song\|artist\|playlist\|user\|radio> <keyword> [page <page>]` |
| View | `/music view <playlist\|artist\|user\|radio\|program> <ID> [page <page>]`, `/music view <link>` |
| Sharing | `/music share <player> <same arguments as view>` |
| Discs | `/music burn song <song ID>` |
| Other | `/music random [refresh]`, `/music join`, `/music leave`, `/music lyrics [on\|off\|status]`, `/music help` |

Admin commands:

| Purpose | Command |
| --- | --- |
| Playback management | `/music pause`, `/music resume`, `/music skip`, `/music queue clear` |
| Configuration | `/music config reload`, `/music config status`, `/music config clearqueue`, `/music config set <key> <value>`, `/music config reset <key\|bgmPlaylist\|all>` |

## Configuration

All settings live in `config/minecraft-music-player.json`, split into shared server fields plus `client`, `glass`, and `idle` sections. Common keys:

| Key | Default | Description |
| --- | --- | --- |
| `neteaseBaseUrl` | `https://mycelis.dpdns.org/` | Backend API address |
| `apiMode` | `auto` | `auto` prefers direct connection, `direct` uses it only, `proxy` uses the backend only |
| `bgmPlaylistId` | `18481322122` | Network background music playlist; empty disables it |
| `proxy` / `useSystemProxy` | empty / `true` | Manual proxy and system proxy |
| `preferIpv4` | `true` | Prefer IPv4 |
| `allowSongRequest` / `allowPlaylistRequest` | `true` | Allow players to request songs / playlists and podcasts |
| `autoAdvance` | `true` | Advance to the next track automatically |
| `searchLimit` | `8` | Items per page |
| `maxQueueSize` / `maxSongsPerPlayer` | `40` / `5` | Request queue limit and requests per player |
| `voteSkipPercent` | `0.6` | Vote skip ratio |
| `enableLootMusicDiscs` | `true` | Random music discs in loot containers |

Changes made in the UI or through commands take effect immediately and are written back to the file. See the [configuration docs](docs/configuration.md) for the full reference.

## Documentation

- [Configuration](docs/configuration.md)
- [UI and liquid glass](docs/glass-window.md)
- [Music browser](docs/music-browser.md)
- [Version porting](docs/version-porting.md)

## Building

Java 25 is required:

```powershell
.\gradlew.bat clean build
```

Artifacts are placed in `build/libs`. For a local release build, use `release.bat`.

## License

MIT
