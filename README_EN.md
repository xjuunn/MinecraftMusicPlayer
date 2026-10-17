# Minecraft Music Player

[![中文](https://img.shields.io/badge/README-中文-blue)](README.md) [![English](https://img.shields.io/badge/README-English-orange)](README_EN.md)

Minecraft Music Player is a Fabric dual-side music mod for NetEase Cloud Music search, song requests, playlist and podcast playback, network background music, real-time lyrics, custom music discs, and jukebox playback.

The server handles commands, queues, configuration, and synchronization. The client handles audio playback. Install the mod on the server and on every client that needs to hear music.

[Demo](https://www.youtube.com/watch?v=4kOa9Ak62Bk)

## Features

- Search songs, artists, playlists, users, and podcasts
- Request a song: plays immediately when idle, otherwise joins the queue
- Playlist and podcast mode: loads tracks in batches, supports sequential, reverse, and shuffle order
- Request queue has priority; playlist queue fills in automatically
- Vote to skip; requesters and admins can skip instantly
- Seek forward and backward, pause, resume, and progress display
- Real-time lyrics for global playback and jukeboxes, with per-player toggle
- Network background music: replaces vanilla background music, with a separate toggle and volume in sound settings
- Share content: send playlists, artists, users, podcasts, programs, or links to other players
- Music discs: burn songs onto discs and play them in jukeboxes; jukebox volume fades with distance
- Random music discs in loot containers
- Song and podcast titles can be clicked to open in a browser
- `music.163.com` song, playlist, and podcast link support
- Paginated lists with clickable actions
- Simplified Chinese, English, and Japanese included

## Requirements

- Minecraft `26.3`
- Fabric Loader `0.19.3` or newer
- Fabric API `0.161.0+26.3` or compatible
- Java `25`
- A reachable NetEase music API, default `https://mycelis.dpdns.org/`

## Installation

Install the mod and Fabric API on the server and on every client that needs to hear music.

Client settings under `Options -> Sound`:

- Network Background Music: toggle
- Background Music Volume: 0% - 100%

The network background music playlist is configured on the server. The main menu always uses the default API address.

## Player Commands

### Playback

| Command | Description |
| --- | --- |
| `/music now` | Show the current track, progress, and pause state |
| `/music seek <seconds>` | Seek forward or backward; positive values seek forward |
| `/music skip` | Skip the current track; requesters and admins skip instantly, others vote |
| `/music mute once` | Mute the current track until the next one starts |

### Requests

| Command | Description |
| --- | --- |
| `/music play song <song ID>` | Request a song |
| `/music play playlist <playlist ID>` | Play a playlist in order |
| `/music play playlist <playlist ID> reverse` | Play a playlist from the last track |
| `/music play program <program ID>` | Play a single podcast program |
| `/music play radio <radio ID>` | Play an entire podcast in order |
| `/music play radio <radio ID> reverse` | Play a podcast from the last program |

### Queue

| Command | Description |
| --- | --- |
| `/music queue [page]` | View the request queue |
| `/music queue promote <song ID>` | Move a song to the top |
| `/music queue remove <song ID>` | Remove a song from the queue |

### Playlist and Podcast

| Command | Description |
| --- | --- |
| `/music playlist` | View playlist playback status |
| `/music playlist list` | View loaded playlist tracks |
| `/music playlist stop` | Stop playlist mode |
| `/music playlist order` | Show the current play order |
| `/music playlist order sequential` | Play in sequential order |
| `/music playlist order reverse` | Play in reverse order |
| `/music playlist order shuffle` | Play in shuffle order |
| `/music radio` | Podcast center |
| `/music radio hot [page]` | Hot podcasts |
| `/music radio categories` | Podcast categories |

### Search

| Command | Description |
| --- | --- |
| `/music search song <keyword>` | Search songs |
| `/music search artist <keyword>` | Search artists; `author` is an alias |
| `/music search playlist <keyword>` | Search playlists |
| `/music search user <keyword>` | Search users |
| `/music search radio <keyword>` | Search podcasts |

All search commands accept `page <page>` after the keyword.

### View Details

| Command | Description |
| --- | --- |
| `/music view playlist <playlist ID> [page <page>]` | View playlist details and tracks |
| `/music view artist <artist ID> [page <page>]` | View artist details and songs; `author` is an alias |
| `/music view user <user ID> [page <page>]` | View playlists created by a user |
| `/music view radio <radio ID> [page <page>]` | View podcast details and programs |
| `/music view program <program ID>` | View program details |
| `/music view <URL>` | Parse a `music.163.com` song, playlist, or podcast link |

### Sharing

| Command | Description |
| --- | --- |
| `/music share <targets> playlist <playlist ID> [page <page>]` | Share a playlist |
| `/music share <targets> user <user ID> [page <page>]` | Share a user's playlists |
| `/music share <targets> artist <artist ID> [page <page>]` | Share an artist's songs; `author` is an alias |
| `/music share <targets> radio <radio ID> [page <page>]` | Share a podcast |
| `/music share <targets> program <program ID>` | Share a program |
| `/music share <targets> <URL>` | Share linked content |

`/music share` accepts the same arguments as `/music view`. Targets can be `@a`, `@p`, or player names, and multiple targets are supported. Recipients can click the message to view it.

### Other

| Command | Description |
| --- | --- |
| `/music random [refresh]` | Generate 10 random hot songs |
| `/music burn song <song ID>` | Burn a song onto the held disc |
| `/music join` | Join current playback |
| `/music leave` | Leave current playback |
| `/music lyrics [on\|off\|status]` | Toggle, enable, disable, or check lyrics |
| `/music help [subcommand]` | Show help |

## Admin Commands

| Command | Description |
| --- | --- |
| `/music skip` | Skip the current track instantly |
| `/music pause` | Pause playback |
| `/music resume` | Resume playback |
| `/music queue clear` | Clear the request queue |
| `/music config reload` | Reload configuration and clear source caches |
| `/music config status` | Show the current configuration |
| `/music config clearqueue` | Clear the request queue |
| `/music config set <key> <value>` | Change a configuration value |
| `/music config reset <key\|bgmPlaylist\|all>` | Reset configuration |

## Configuration

Server configuration: `config/minecraft-music-player.json`

| Key | Default | Description |
| --- | --- | --- |
| `neteaseBaseUrl` | `https://mycelis.dpdns.org/` | NetEase API address |
| `bgmPlaylistId` | `18481322122` | Network background music playlist; empty disables it |
| `proxy` | empty | Manual proxy; `none` clears it |
| `useSystemProxy` | `true` | Use the system proxy |
| `preferIpv4` | `true` | Prefer IPv4 |
| `connectTimeoutSeconds` / `readTimeoutSeconds` | `10` / `20` | Connect and read timeouts |
| `allowCustomServer` | `true` | Allow changing the API address |
| `allowSongRequest` | `true` | Allow song requests |
| `allowPlaylistRequest` | `true` | Allow playlist and podcast requests |
| `autoAdvance` | `true` | Advance to the next track automatically |
| `announceQueueChanges` | `true` | Broadcast queue changes |
| `showLoadingHints` | `true` | Show loading hints |
| `showLyrics` | `true` | Enable lyrics by default |
| `searchLimit` | `8` | Items per page |
| `maxQueueSize` | `40` | Request queue limit |
| `maxSongsPerPlayer` | `5` | Requests per player limit |
| `playlistQueueLimit` | `20` | Playlist preload limit |
| `queueCacheSize` | `3` | Source cache size |
| `voteSkipPercent` | `0.6` | Vote skip ratio |
| `enableLootMusicDiscs` | `true` | Enable random loot discs |
| `lootMusicDiscChance` / `lootMusicDiscCount` | `0.3` / `1` | Chance and count per container |

Use `/music config set bgmPlaylist off` to disable `bgmPlaylistId`, or `reset` to restore the default. `baseUrl` accepts `default`; `proxy` accepts `none`.

Client configuration: `config/minecraft-music-player-client.json`

| Key | Default | Description |
| --- | --- | --- |
| `enableNetworkBgm` | `true` | Enable network background music |
| `bgmVolume` | `0.7` | Background music volume |

## Build

Java 25 is required:

```powershell
.\gradlew.bat clean build
```

## License

MIT
