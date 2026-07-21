# Minecraft Music Player

[![中文](https://img.shields.io/badge/README-中文-blue)](README.md) [![English](https://img.shields.io/badge/README-English-orange)](README_EN.md)

Minecraft Music Player is a Fabric dual-side music mod that provides NetEase Cloud Music search, song requests, shared queue playback, batched playlist/podcast playback, custom music disc burning, and jukebox URL playback.

The server handles commands, queue, sync, and jukebox control. The client handles audio playback. Both sides are required for full functionality.

## Features

- Search songs, artists, playlists, podcasts (radio), users
- Request single songs; auto-queues or plays immediately
- Playlist/Podcast mode: loads tracks in batches in the background, plays through the entire playlist seamlessly
- **Podcast (Radio) support**: browse podcast details, program list, play single programs or entire podcasts
- **Play order**: Sequential / Reverse / Shuffle, applies to both playlists and podcasts
- Dual-queue system: request queue (high priority) plays first, playlist queue (low priority) fills in when empty
- Skip: requester/admin skips instantly, others vote
- **Playback control**: Seek forward/backward, pause/resume, visual progress bar
- Click song/podcast titles to open in browser
- **URL detection**: Auto-parse `music.163.com` links for songs, playlists, and podcasts
- Burn custom music discs, play URL-based music in jukeboxes
- Jukebox music switches automatically based on distance (leave range → global audio, re-enter → resume jukebox)
- Real-time lyrics for both global and jukebox music, per-player toggle
- Loot container random music discs
- Paginated lists with clickable navigation

## Requirements

- Minecraft `26.2`
- Fabric Loader `0.19.3` or newer
- Fabric API `0.154.2+26.2` or compatible
- Java `25`
- A reachable NetEase music API service, default `https://mycelis.dpdns.org/`

## Installation

Install the mod and Fabric API on both server and clients that need to hear music.

## Player Commands

### Playback Control

| Command | Description |
| --- | --- |
| `/music now` | Show current track, progress bar, and paused state |
| `/music seek <seconds>` | Seek forward/backward (positive = forward, negative = backward) |
| `/music skip` | Skip current track (requester/admin skips, others vote) |
| `/music mute once` | Mute current song |

### Request

| Command | Description |
| --- | --- |
| `/music play song <ID>` | Request a single song |
| `/music play playlist <ID>` | Start playlist mode |
| `/music play program <ID>` | Play a single podcast program |
| `/music play radio <ID>` | Play an entire podcast (all programs queued) |

### Queue Management

| Command | Description |
| --- | --- |
| `/music queue` | View request queue |
| `/music queue promote <ID>` | Move to next position |
| `/music queue remove <ID>` | Remove from queue |

### Playlist & Podcast

| Command | Description |
| --- | --- |
| `/music playlist` | View playlist status and play order |
| `/music playlist list` | View loaded playlist tracks |
| `/music playlist stop` | Stop playlist mode |
| `/music playlist order sequential` | Set sequential playback order |
| `/music playlist order reverse` | Set reverse playback order |
| `/music playlist order shuffle` | Set shuffle playback order |
| `/music radio` | Podcast center |
| `/music radio hot [page]` | Hot podcasts |
| `/music radio categories` | Browse podcast categories |

### Search

| Command | Description |
| --- | --- |
| `/music search song <keyword>` | Search songs |
| `/music search artist <keyword>` | Search artists |
| `/music search playlist <keyword>` | Search playlists |
| `/music search radio <keyword>` | Search podcasts |
| `/music search user <keyword>` | Search users |

### View Details

| Command | Description |
| --- | --- |
| `/music view playlist <ID>` | View playlist details |
| `/music view artist <ID>` | View artist details |
| `/music view user <ID>` | View user playlists |
| `/music view radio <ID>` | View podcast details and program list |
| `/music view program <ID>` | View program details |
| `/music view <URL>` | Auto-detect `music.163.com` links (supports `/song`, `/playlist`, `/djradio`, `/dj`) |

### Other

| Command | Description |
| --- | --- |
| `/music random` | Generate 10 random hot songs |
| `/music burn song <ID>` | Burn disc |
| `/music join` | Join current playback |
| `/music leave` | Leave current playback |
| `/music lyrics` | Toggle real-time lyrics |
| `/music lyrics on/off` | Enable/disable lyrics |
| `/music lyrics status` | Check lyrics status |
| `/music help [subcommand]` | Show help |

## Admin Commands

| Command | Description |
| --- | --- |
| `/music skip` | Skip instantly |
| `/music stop` | Stop all playback |
| `/music pause` | Pause playback |
| `/music resume` | Resume playback |
| `/music queue clear` | Clear request queue |
| `/music config reload` | Reload config and clear URL cache |
| `/music config status` | View config |
| `/music config clearqueue` | Clear queue |

Config values are changed via `/music config set <key> <value>`.

## Configuration

`config/minecraft-music-player.json`. See default values in the file.

## Build

```powershell
.\gradlew.bat clean build
```
