# Minecraft Music Player

[![中文](https://img.shields.io/badge/README-中文-blue)](README.md) [![English](https://img.shields.io/badge/README-English-orange)](README_EN.md)

Minecraft Music Player is a Fabric dual-side music mod that provides NetEase Cloud Music search, song requests, shared queue playback, batched playlist playback, custom music disc burning, and jukebox URL playback.

The server handles commands, queue, sync, and jukebox control. The client handles audio playback. Both sides are required for full functionality.

## Features

- Search songs, artists, playlists, users
- Request single songs; auto-queues or plays immediately
- Playlist mode: loads tracks in batches in the background, plays through the entire playlist seamlessly
- Dual-queue system: request queue (high priority) plays first, playlist queue (low priority) fills in when empty
- Skip: requester/admin skips instantly, others vote
- Click song titles to open in browser
- Burn custom music discs, play URL-based music in jukeboxes
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

| Command | Description |
| --- | --- |
| `/music now` | Show current playback and progress |
| `/music play song <ID>` | Request a single song |
| `/music play playlist <ID>` | Start playlist mode |
| `/music skip` | Skip current track (requester/admin skips, others vote) |
| `/music queue` | View request queue |
| `/music queue promote <ID>` | Move to next position |
| `/music queue remove <ID>` | Remove from queue |
| `/music playlist` | View playlist status |
| `/music playlist list` | View loaded playlist tracks |
| `/music playlist stop` | Stop playlist mode |
| `/music search song <keyword>` | Search songs |
| `/music search artist <keyword>` | Search artists |
| `/music search playlist <keyword>` | Search playlists |
| `/music search user <keyword>` | Search users |
| `/music view playlist <ID>` | View playlist details |
| `/music view artist <ID>` | View artist details |
| `/music view user <ID>` | View user playlists |
| `/music random` | Generate 10 random hot songs |
| `/music burn song <ID>` | Burn disc |
| `/music join` | Join current playback |
| `/music leave` | Leave current playback |
| `/music mute once` | Mute current song |
| `/music help [subcommand]` | Show help |

## Admin Commands

| Command | Description |
| --- | --- |
| `/music skip` | Skip instantly |
| `/music stop` | Stop all playback |
| `/music queue clear` | Clear request queue |
| `/music admin reload` | Reload config |
| `/music admin status` | View config |

Config values are changed via `/music admin set <key> <value>`.

## Configuration

`config/minecraft-music-player.json`. See default values in the file.

## Build

```powershell
.\gradlew.bat clean build
```
