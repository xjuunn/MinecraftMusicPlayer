# Minecraft Music Player

Minecraft Music Player is a Fabric mod for Minecraft `1.21.11` that provides a shared NetEase Cloud Music queue for servers and clients.

The server manages commands, queue state, search, and synchronization. Clients handle the actual audio playback.

## Features

- Search songs, artists, playlists, and users from NetEase Cloud Music
- View artist top songs, playlist details, and user playlists
- Let players request songs and import playlists into the shared queue
- Automatic queue progression
- Vote skip for the next song
- Admin controls for reload, stop, skip, clear queue, and configuration
- Default NetEase API endpoint with optional custom endpoint support
- Lightweight loading hints during search and remote fetches
- Clickable chat actions for play, view playlist, and view user playlist

## How It Works

This is not a pure server-side audio mod.

- The server mod provides queue logic, commands, and synchronization
- The client mod is required for actual audio playback
- A client without the mod will not hear music even if the server is running it

Recommended setup:

1. Install the mod on the server
2. Install the mod on every client that should hear music
3. Ensure the server or clients can reach a working NetEase API instance

## Requirements

- Minecraft `1.21.11`
- Fabric Loader `0.18.4` or newer
- Fabric API `0.141.3+1.21.11` or compatible
- Java `21`
- A working [NeteaseCloudMusicApi](https://github.com/Binaryify/NeteaseCloudMusicApi) instance, defaulting to `http://127.0.0.1:3000`

## Player Commands

| Command | Description |
| --- | --- |
| `/music` | Show help |
| `/music now` | Show the current track |
| `/music queue` | Show the queue |
| `/music join` | Join the current playback |
| `/music leave` | Leave the current playback |
| `/music mute once` | Stop listening to the current track |
| `/music vote next` | Vote to skip to the next track |
| `/music play song <songId>` | Request a song by ID |
| `/music play playlist <playlistId>` | Import a playlist into the queue |
| `/music search song <keyword>` | Search songs |
| `/music search artist <keyword>` | Search artists |
| `/music search author <keyword>` | Alias for artist search |
| `/music search playlist <keyword>` | Search playlists |
| `/music search user <keyword>` | Search users |
| `/music view artist <artistId>` | View top songs for an artist |
| `/music view author <artistId>` | Alias for artist view |
| `/music view playlist <playlistId>` | View playlist details |
| `/music view user <userId>` | View user playlists |

## Admin Commands

| Command | Description |
| --- | --- |
| `/music admin reload` | Reload config |
| `/music admin status` | Show current config state |
| `/music admin clearqueue` | Clear the pending queue |
| `/music next` | Skip to the next track immediately |
| `/music stop` | Stop playback |
| `/music admin set baseUrl <url>` | Set the NetEase API endpoint, use `default` to reset |
| `/music admin set allowCustomServer <true\|false>` | Allow or deny custom API endpoints |
| `/music admin set allowSongRequest <true\|false>` | Allow or deny song requests |
| `/music admin set allowPlaylistRequest <true\|false>` | Allow or deny playlist imports |
| `/music admin set autoAdvance <true\|false>` | Enable or disable automatic next track |
| `/music admin set announceQueueChanges <true\|false>` | Broadcast queue updates |
| `/music admin set showLoadingHints <true\|false>` | Show loading hints for search and fetch operations |
| `/music admin set searchLimit <3-20>` | Set search result limit |
| `/music admin set maxQueueSize <1-200>` | Set queue capacity |
| `/music admin set playlistQueueLimit <1-100>` | Set the max tracks imported from one playlist |
| `/music admin set voteSkipPercent <0.1-1.0>` | Set the vote skip threshold |

## Config

Config file path:

```text
config/minecraft-music-player.json
```

## Build

```powershell
.\gradlew.bat clean build
```

Artifact example:

```text
minecraft-music-player-2.0.0-fabricmc1.21.11.jar
```

## GitHub Release

### Release by tag

```bash
git tag v2.0.0
git push origin v2.0.0
```

### Manual release

Run the `release` workflow from the GitHub `Actions` page and provide the target tag.