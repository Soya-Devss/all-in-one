# All-In-One Lavalink Plugin

A comprehensive audio source plugin for **Lavalink v4**, porting high-performance support for **Audiomack**, **Gaana**, and **Pandora** directly into your Lavalink node with built-in YouTube Music mirroring.

---

## Features

- **Audiomack**: Official API integration with OAuth 1.0 HMAC-SHA1 signing for search (`admsearch:`, `audiomack:`), song URLs, albums, and playlists. Direct stream playback with mirror fallback.
- **Gaana**: Full search (`gnsearch:`, `gaanasearch:`), song, album, playlist, and artist top tracks resolution with AES-128 stream decryption and YouTube Music fallback.
- **Pandora**: Anonymous session handling with CSRF validation for search (`pdsearch:`) and tracks/playlists/stations, resolved seamlessly through mirroring.
- **YouTube Music First Mirroring**: Smart fallback resolver prioritizing `ytmsearch:` by ISRC and track title/author.
- **Pre-Configured Hosting Bundle**: Includes a complete `lavalink/` folder with `Lavalink.jar` (v4.0.8), `plugins/all-in-one-plugin-1.0.0.jar`, startup scripts (`start.bat`, `start.sh`), and a ready-to-host `application.yml`.

---

## Quick Start (Ready to Host)

A ready-to-run setup is provided in the `lavalink/` directory.

### Windows
```cmd
cd lavalink
start.bat
```

### Linux / macOS
```bash
cd lavalink
chmod +x start.sh
./start.sh
```

---

## Building from Source

To compile the plugin JAR from source:

```bash
git clone https://github.com/Soya-Devss/all-in-one.git
cd all-in-one
./gradlew build
```

The compiled plugin will be located at:
```
build/libs/all-in-one-1.0.0.jar
```

Copy this `.jar` into your Lavalink server's `plugins/` directory.

---

## Configuration (`application.yml`)

Add the `allinone` configuration block under the `plugins:` section of your `application.yml`.

```yaml
server:
  port: 2333
  address: 0.0.0.0

lavalink:
  server:
    password: "youshallnotpass"
    sources:
      youtube: false
      bandcamp: true
      soundcloud: true
      twitch: true
      vimeo: true
      http: true
      local: false
    bufferDurationMs: 400
    frameBufferDurationMs: 5000
    opusEncodingQuality: 10
    resamplingQuality: HIGH
    trackStuckThresholdMs: 10000
    useSeekGhosting: true
    playerUpdateInterval: 5
    youtubeSearchEnabled: true
    soundcloudSearchEnabled: true

plugins:
  allinone:
    # Enable or disable the entire plugin
    enabled: true

    # Individual source toggles
    audiomack: true
    gaana: true
    pandora: true
    autoUpdate: true
    repository: "Soya-Devss/all-in-one"
    gitBranch: "main"

    # Mirroring providers (executed in order when direct playback requires fallback)
    providers:
      - "ytmsearch:\"%ISRC%\""
      - "ytmsearch:%QUERY%"
      - "ytsearch:\"%ISRC%\""
      - "ytsearch:%QUERY%"
      - "scsearch:%QUERY%"
```

### Configuration Options

| Option | Type | Default | Description |
| :--- | :--- | :--- | :--- |
| `enabled` | Boolean | `true` | Enables or disables all sources registered by this plugin. |
| `audiomack` | Boolean | `true` | Enables Audiomack search and URL resolution. |
| `gaana` | Boolean | `true` | Enables Gaana search and URL resolution. |
| `pandora` | Boolean | `true` | Enables Pandora search and URL resolution. |
| `autoUpdate` | Boolean | `true` | Pulls Git updates and checks releases on startup. |
| `repository` | String | `Soya-Devss/all-in-one` | GitHub repository slug used for release checks. |
| `gitBranch` | String | `main` | Git branch to pull updates from when autoUpdate is enabled. |
| `providers` | List | *(see above)* | Ordered list of search templates for track mirroring. Supports `%ISRC%` and `%QUERY%`. |

---

## Supported Prefixes and URL Formats

### Audiomack
- **Search**: `admsearch:<query>` or `audiomack:<query>`
- **Song**: `https://audiomack.com/<artist>/song/<slug>`
- **Album**: `https://audiomack.com/<artist>/album/<slug>`
- **Playlist**: `https://audiomack.com/<artist>/playlist/<slug>`

### Gaana
- **Search**: `gnsearch:<query>` or `gaanasearch:<query>`
- **Song**: `https://gaana.com/song/<slug>`
- **Album**: `https://gaana.com/album/<slug>`
- **Playlist**: `https://gaana.com/playlist/<slug>`
- **Artist Top Tracks**: `https://gaana.com/artist/<slug>`

### Pandora
- **Search**: `pdsearch:<query>`
- **Playlist**: `https://www.pandora.com/playlist/<id>`
- **Station**: `https://www.pandora.com/station/<id>`
- **Podcast**: `https://www.pandora.com/podcast/<id>`
- **Artist**: `https://www.pandora.com/artist/<id>`

---
