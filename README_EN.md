# Minecraft Music Player

Minecraft Music Player is a Fabric mod for Minecraft `1.21.11` that provides a shared NetEase Cloud Music queue, search, song requests, and synchronized playback.

The server handles commands, queue state, search, synchronization, and configuration. The client handles the actual audio download and playback. This means the mod requires both sides for full functionality and is not a pure server-side audio mod.

## Feature Overview

- Search songs, artists, playlists, and users
- View artist top songs, playlist details, and user playlists
- Let players request songs, import playlists, and inspect the current queue
- Paginated search results with clickable `Previous Page` and `Next Page` buttons
- Automatic progression to the next track
- Vote skip support
- Multi-source playback fallback when a source fails
- Prefer third-party sources that can fully play some VIP tracks instead of 30-second preview links
- Admin configuration for API endpoint, proxy, IPv4 preference, timeouts, queue size, and search size
- Lightweight loading hints during search, parsing, and remote fetches
- Clickable chat actions for song requests, paging, and detail views

## How It Works

- The server mod provides queue logic, commands, and synchronization
- The client mod is required for actual audio playback
- If only the server has the mod, commands and synchronization still work, but clients will not hear audio
- Players who want to hear music must install the client mod

Recommended setup:

1. Install `Minecraft Music Player` on the server
2. Install `Minecraft Music Player` on every client that should hear music
3. Ensure the server or clients can reach a working music API endpoint

## Requirements

- Minecraft `1.21.11`
- Fabric Loader `0.18.4` or newer
- Fabric API `0.141.3+1.21.11` or compatible
- Java `21`
- A reachable NetEase-related API service, defaulting to `https://odlimemusicapi.vercel.app`

## Installation

### Server

Place the mod jar and `Fabric API` in the server `mods` directory.

### Client

Place the mod jar and `Fabric API` in the client `mods` directory.

### Default API Endpoint

The default configuration uses:

```text
https://odlimemusicapi.vercel.app
```

Admins can change the endpoint by command or reset it back to the default value.

## Player Commands

| Command | Description |
| --- | --- |
| `/music` | Show help |
| `/music now` | Show the current track |
| `/music queue` | Show the current queue |
| `/music join` | Join the current playback |
| `/music leave` | Leave the current playback |
| `/music mute once` | Stop receiving only the current track |
| `/music vote next` | Vote to skip to the next track |
| `/music play song <songId>` | Request a single song by ID |
| `/music play playlist <playlistId>` | Import a playlist into the queue |
| `/music search song <keyword>` | Search songs |
| `/music search song page <page> <keyword>` | Open a specific page of song search results |
| `/music search artist <keyword>` | Search artists |
| `/music search artist page <page> <keyword>` | Open a specific page of artist search results |
| `/music search author <keyword>` | Alias for `artist` |
| `/music search author page <page> <keyword>` | Alias for paged `artist` search |
| `/music search playlist <keyword>` | Search playlists |
| `/music search playlist page <page> <keyword>` | Open a specific page of playlist search results |
| `/music search user <keyword>` | Search users |
| `/music search user page <page> <keyword>` | Open a specific page of user search results |
| `/music view artist <artistId>` | View top songs for an artist |
| `/music view author <artistId>` | Alias for `artist` |
| `/music view playlist <playlistId>` | View playlist details |
| `/music view user <userId>` | View user playlists |

Each search page shows:

- a clickable `Previous Page` button when applicable
- a clickable `Next Page` button when more results are likely available
- the current page number

## Admin Commands

| Command | Description |
| --- | --- |
| `/music admin reload` | Reload configuration |
| `/music admin status` | Show current configuration state |
| `/music admin clearqueue` | Clear the pending queue |
| `/music next` | Skip to the next track immediately |
| `/music stop` | Stop playback |
| `/music admin set baseUrl <url>` | Set the music API endpoint, use `default` to reset |
| `/music admin set allowCustomServer <true\|false>` | Allow or deny custom API endpoints |
| `/music admin set allowSongRequest <true\|false>` | Allow or deny song requests |
| `/music admin set allowPlaylistRequest <true\|false>` | Allow or deny playlist imports |
| `/music admin set autoAdvance <true\|false>` | Enable or disable automatic progression |
| `/music admin set announceQueueChanges <true\|false>` | Enable or disable queue change broadcasts |
| `/music admin set showLoadingHints <true\|false>` | Show or hide loading hints |
| `/music admin set preferIpv4 <true\|false>` | Prefer IPv4 for outbound requests |
| `/music admin set proxy <host:port>` | Configure an HTTP proxy |
| `/music admin set proxy none` | Clear the proxy setting |
| `/music admin set connectTimeoutSeconds <3-60>` | Set the connect timeout |
| `/music admin set readTimeoutSeconds <3-120>` | Set the read timeout |
| `/music admin set searchLimit <3-20>` | Set the number of search results per page |
| `/music admin set maxQueueSize <1-200>` | Set the queue capacity |
| `/music admin set playlistQueueLimit <1-100>` | Set the maximum number of imported playlist tracks |
| `/music admin set voteSkipPercent <0.1-1.0>` | Set the vote skip threshold |

## Configuration

Configuration file path:

```text
config/minecraft-music-player.json
```

Default configuration example:

```json
{
  "neteaseBaseUrl": "https://odlimemusicapi.vercel.app",
  "proxy": "",
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
  "voteSkipPercent": 0.6
}
```

## Source Selection and Fallback

To improve playback reliability, the mod tries multiple candidate sources in order.

Current strategy:

1. Prefer third-party sources that are more suitable for full VIP-track playback
2. Then try playable mp3 URLs returned by the NetEase API
3. Use NetEase outer links only as the last fallback

The mod also filters obvious preview-only links such as:

- `musicrep-ts`
- `jd-musicrep-ts`

This reduces the chance that some VIP tracks only play for 30 seconds.

## Build

```powershell
.\gradlew.bat clean build
```

Artifact example:

```text
minecraft-music-player-2.0.0-fabricmc1.21.11.jar
```

## Release

The repository supports GitHub Actions based releases.

### Release by Tag

```bash
git tag v2.0.0
git push origin v2.0.0
```

### Manual Release

Run the `release` workflow from the GitHub `Actions` page and provide the tag to publish.

## Notes

- The project uses Mojang official mappings
- Interaction is based on chat messages and clickable actions instead of a custom GUI
- The client player currently prioritizes directly playable mp3 sources
- The Chinese documentation is available in [README.md](README.md)
