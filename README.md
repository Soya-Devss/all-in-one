# All-In-One Lavalink Plugin

A comprehensive audio source plugin for **Lavalink v4**, porting high-performance support for **Audiomack**, **Gaana**, **Pandora**, and **Qobuz** directly into your Lavalink node with built-in YouTube Music mirroring.

---

## Features

- **Audiomack**: Official API integration with OAuth 1.0 HMAC-SHA1 signing for search (`admsearch:`, `audiomack:`), song URLs, albums, and playlists. Direct stream playback with mirror fallback.
- **Gaana**: Full search (`gnsearch:`, `gaanasearch:`), song, album, playlist, and artist top tracks resolution with AES-128 stream decryption and YouTube Music fallback.
- **Pandora**: Anonymous session handling with CSRF validation for search (`pdsearch:`) and tracks/playlists/stations, resolved seamlessly through mirroring.
- **Qobuz**: Search (`qbsearch:`, `qbisrc:`, `qobuz:`), recommendations (`qbrec:`), tracks, albums, playlists, and artist top tracks resolution with dynamic web player credential extraction, signed direct streaming, and mirror fallback.
- **YouTube Music First Mirroring**: Smart fallback resolver prioritizing `ytmsearch:` by ISRC and track title/author.
- **Pre-Configured Hosting Bundle**: Includes a complete `lavalink/` folder with `Lavalink.jar` (v4.0.8), `plugins/all-in-one-1.0.5.jar`, startup scripts (`start.bat`, `start.sh`), and a ready-to-host `application.yml`.

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
build/libs/all-in-one-1.0.5.jar
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
    qobuz: true

    # Custom Gaana API & Proxy options
    gaanaApiUrl: "https://gaana-api-2.vercel.app/api"
    gaanaProxy: "" # Optional proxy URL (e.g. http://host:port or http://user:pass@host:port)
    pandoraProxy: "" # Optional proxy URL

    # Qobuz options (optional user token for direct stream, formatId: 5 = MP3 320kbps)
    qobuzUserToken: ""
    qobuzFormatId: "5"
    qobuzProxy: "" # Optional proxy URL

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
| `qobuz` | Boolean | `true` | Enables Qobuz search and URL resolution. |
| `qobuzUserToken` | String | `""` | Optional Qobuz user auth token for high quality direct playback. |
| `qobuzFormatId` | String | `"5"` | Qobuz stream format ID (`5` for 320kbps MP3). |
| `qobuzProxy` | String | `""` | Optional HTTP/SOCKS5 proxy specifically for Qobuz requests. |
| `gaanaApiUrl` | String | `https://gaana-api-2.vercel.app/api` | Gaana API base URL. |
| `gaanaProxy` | String | `""` | Optional HTTP/SOCKS5 proxy specifically for Gaana requests. |
| `pandoraProxy` | String | `""` | Optional HTTP/SOCKS5 proxy specifically for Pandora requests. |
| `providers` | List | *(see above)* | Ordered list of search templates for track mirroring. Supports `%ISRC%` and `%QUERY%`. |

---

## LavaSearch Support

This plugin implements the **LavaSearch** extension. When used alongside the LavaSearch plugin, you can query `/v4/loadsearch` for tracks, albums, playlists, and artists:

```http
GET /v4/loadsearch?query=kesariya&types=track,album,artist,playlist
```

---

## Global Hosting & Geo-Unblocking

When hosting outside India (e.g. Canada, US, Europe), Gaana runs seamlessly:

1. **Custom Vercel API**: Uses `https://gaana-api-2.vercel.app/api` hosted in Mumbai (ap-south-1) for zero geo-restriction on song searches, album lookups, and playlist fetching worldwide.
2. **Dedicated Proxy**: You can optionally route requests through `gaanaProxy: "http://host:port"` in `application.yml`.
3. **YouTube Music Mirroring**: If Akamai CDN direct streaming blocks non-Indian IPs during track playback, audio playback automatically streams the identical track via YouTube Music (`ytmsearch:`) using title, artist, and ISRC. Playback never fails.

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

### Qobuz
- **Search**: `qbsearch:<query>`, `qbisrc:<isrc>`, or `qobuz:<query>`
- **Recommendations**: `qbrec:<trackId>`
- **Track**: `https://open.qobuz.com/track/<id>` or `https://play.qobuz.com/track/<id>`
- **Album**: `https://open.qobuz.com/album/<id>` or `https://play.qobuz.com/album/<id>`
- **Playlist**: `https://open.qobuz.com/playlist/<id>` or `https://play.qobuz.com/playlist/<id>`
- **Artist**: `https://open.qobuz.com/artist/<id>` or `https://play.qobuz.com/artist/<id>`

---
