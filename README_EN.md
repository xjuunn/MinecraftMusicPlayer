# Minecraft Music Player

Minecraft Music Player is a Fabric music mod for Minecraft `1.21.11`. It provides NetEase Cloud Music search, song requests, shared queue playback, playlist playback, and custom music disc support.

The server handles commands, queue logic, search requests, sync, disc burning, and jukebox control. The client handles actual audio download and playback. This is a dual-side mod, not a pure server-side audio mod.

## Features

- Search songs, artists, playlists, and users
- View artist top songs, playlist details, and user playlists
- Shared song requests, playlist playback, queue playback, and auto-advance
- Vote skip, duplicate request deduplication, and move queued songs to next
- Unified paging and navigation for search results, detail pages, and queue pages
- Clickable chat UI for songs, artists, playlist owners, and detail entry points
- Clickable download entry for the current track, opening the direct URL in the browser
- Server-side queue prefetching to reduce delay when switching tracks
- Burn songs into vanilla music discs and play URL-based music from jukeboxes

## Music Discs

### Supported workflow

- Use `/music burn song <song_id>` to burn the held disc into a custom music disc
- Burned discs store:
  - song ID
  - song title
  - artist name
  - artist ID
  - duration
  - available source URLs
- The disc display name becomes `Title - Artist`
- Hovering the disc shows extended metadata
- Inserting the burned disc into a jukebox automatically plays the stored URL music
- Playback stops automatically when the disc is ejected, replaced, the jukebox is broken, the chunk unloads, or the player leaves audible range

### Supported disc bases

Any vanilla `Music Disc` item can be used as a burnable base disc.

### Burn entry points

When the player is holding a burnable disc in the main hand, a `[Burn]` action is shown automatically in these views:

- `/music now`
- `/music search song ...`
- `/music view playlist ...`
- `/music view artist ...`
- `/music view author ...`
- `/music queue`

Clicking it runs `/music burn song <song_id>` directly.

## How It Works

- With the mod installed on the server, the server handles queue logic, command processing, search requests, jukebox playback sync, and config management
- With the mod installed on the client, the client plays the actual audio stream and custom jukebox audio
- If only the server installs the mod, commands and queue logic work, but clients cannot hear music
- Players who want to hear music must install the mod on the client

Recommended setup:

1. Install `Minecraft Music Player` on the server
2. Install `Minecraft Music Player` on all clients that should hear music
3. Make sure the server or clients can access a working music API endpoint

## Requirements

- Minecraft `1.21.11`
- Fabric Loader `0.18.4` or newer
- Fabric API `0.141.3+1.21.11` or a compatible version
- Java `21`
- A reachable NetEase-related music API service, defaulting to `https://mycelis.dpdns.org/`

## Installation

### Server

Put the mod jar and `Fabric API` into the server `mods` folder.

### Client

Put the mod jar and `Fabric API` into the client `mods` folder.

### Default API Address

Default configuration uses:

```text
https://mycelis.dpdns.org/
```

Admins can override it by command and can restore the default value later.

## Player Commands

| Command | Description |
| --- | --- |
| `/music` | Show help |
| `/music now` | Show the current track with a clickable download entry |
| `/music queue [page]` | Show the current queue |
| `/music queue next <song_id>` | Move a queued song to the next position |
| `/music join` | Join current playback |
| `/music leave` | Leave current playback |
| `/music mute once` | Stop receiving only the current song |
| `/music vote next` | Vote to skip to the next track |
| `/music play song <song_id>` | Request a single song |
| `/music play playlist <playlist_id>` | Switch into playlist playback mode |
| `/music burn song <song_id>` | Burn the held disc into a music disc |
| `/music search song <keyword>` | Search songs |
| `/music search song page <page> <keyword>` | View a specific page of song search results |
| `/music search artist <keyword>` | Search artists |
| `/music search artist page <page> <keyword>` | View a specific page of artist search results |
| `/music search author <keyword>` | Alias of `artist` |
| `/music search playlist <keyword>` | Search playlists |
| `/music search playlist page <page> <keyword>` | View a specific page of playlist search results |
| `/music search user <keyword>` | Search users |
| `/music search user page <page> <keyword>` | View a specific page of user search results |
| `/music view artist <artist_id>` | View artist top songs |
| `/music view artist page <page> <artist_id>` | View a specific page of artist details |
| `/music view author <artist_id>` | Alias of `artist` |
| `/music view playlist <playlist_id>` | View playlist details |
| `/music view playlist page <page> <playlist_id>` | View a specific page of playlist details |
| `/music view user <user_id>` | View user playlists |
| `/music view user page <page> <user_id>` | View a specific page of user playlists |

## Admin Commands

| Command | Description |
| --- | --- |
| `/music admin reload` | Reload config |
| `/music admin status` | Show current config status |
| `/music admin clearqueue` | Clear queued tracks |
| `/music next` | Skip immediately to the next track |
| `/music stop` | Stop playback |
| `/music admin set baseUrl <url>` | Set the music API base URL, or use `default` to restore the default |
| `/music admin set allowCustomServer <true\|false>` | Allow custom API URL or not |
| `/music admin set allowSongRequest <true\|false>` | Allow song requests or not |
| `/music admin set allowPlaylistRequest <true\|false>` | Allow playlist import or not |
| `/music admin set autoAdvance <true\|false>` | Enable auto-advance or not |
| `/music admin set announceQueueChanges <true\|false>` | Broadcast queue changes or not |
| `/music admin set showLoadingHints <true\|false>` | Show search and resolve hints or not |
| `/music admin set useSystemProxy <true\|false>` | Enable system proxy discovery or not |
| `/music admin set preferIpv4 <true\|false>` | Prefer IPv4 or not |
| `/music admin set proxy <host:port>` | Set HTTP proxy |
| `/music admin set proxy none` | Clear proxy |
| `/music admin set connectTimeoutSeconds <3-60>` | Set connect timeout |
| `/music admin set readTimeoutSeconds <3-120>` | Set read timeout |
| `/music admin set searchLimit <3-20>` | Set page size for search results |
| `/music admin set maxQueueSize <1-200>` | Set queue size limit |
| `/music admin set playlistQueueLimit <1-100>` | Set maximum imported songs per playlist |
| `/music admin set queueCacheSize <0-20>` | Set server-side queue prefetch size |
| `/music admin set voteSkipPercent <0.1-1.0>` | Set vote skip ratio |

## Configuration

Configuration file path:

```text
config/minecraft-music-player.json
```

Default config example:

```json
{
  "neteaseBaseUrl": "https://mycelis.dpdns.org/",
  "proxy": "",
  "useSystemProxy": true,
  "preferIpv4": true,
  "allowCustomServer": true,
  "allowSongRequest": true,
  "allowPlaylistRequest": true,
  "autoAdvance": true,
  "announceQueueChanges": true,
  "showLoadingHints": true,
  "connectTimeoutSeconds": 10,
  "readTimeoutSeconds": 20,
  "searchLimit": 8,
  "maxQueueSize": 40,
  "playlistQueueLimit": 20,
  "queueCacheSize": 3,
  "voteSkipPercent": 0.6
}
```

## Queue Prefetch

- The server automatically pre-resolves the next queued tracks
- Default prefetch size is `3`
- Track switching prefers cached resolution results
- You can tune it with `/music admin set queueCacheSize <0-20>`
- Set it to `0` to disable queue prefetching

## Source Fallback Strategy

To improve playback availability, the mod tries multiple playback sources in order:

1. Third-party sources that are more suitable for full VIP song playback
2. Playable mp3 URLs returned by the NetEase-related API
3. NetEase outer-link fallback URLs as the final fallback

It also filters obvious preview-only links such as:

- `musicrep-ts`
- `jd-musicrep-ts`

This reduces the issue where some VIP tracks only play for 30 seconds.

## Build

```powershell
.\gradlew.bat clean build
```

Build artifact example:

```text
minecraft-music-player-2.0.2-fabricmc1.21.11.jar
```

## Release

The repository supports GitHub Actions based releases.

### Release by tag

```bash
git tag v2.0.2
git push origin v2.0.2
```

### Manual release

Run the `release` workflow from the GitHub `Actions` page and provide the tag to publish.

## Notes

- The project uses Mojang official mappings
- Interaction is based on chat messages and clickable components, without a custom GUI
- The current client player prefers directly playable mp3 sources
- Chinese documentation is available in [README.md](README.md)
