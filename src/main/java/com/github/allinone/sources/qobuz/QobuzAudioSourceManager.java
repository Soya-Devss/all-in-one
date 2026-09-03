package com.github.allinone.sources.qobuz;

import com.github.allinone.AllInOneConfig;
import com.github.allinone.mirror.DefaultMirroringAudioTrackResolver;
import com.github.allinone.mirror.MirroringAudioSourceManager;
import com.github.allinone.mirror.MirroringAudioTrackResolver;
import com.github.allinone.tools.HttpHelper;
import com.github.topi314.lavasearch.AudioSearchManager;
import com.github.topi314.lavasearch.result.AudioSearchResult;
import com.github.topi314.lavasearch.result.BasicAudioSearchResult;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.tools.DataFormatTools;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterfaceManager;
import com.sedmelluq.discord.lavaplayer.track.AudioItem;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioReference;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class QobuzAudioSourceManager implements MirroringAudioSourceManager, AudioSearchManager {

    private static final Logger log = LoggerFactory.getLogger(QobuzAudioSourceManager.class);

    private static final String API_BASE_URL = "https://www.qobuz.com/api.json/0.2";
    private static final String WEB_PLAYER_BASE_URL = "https://play.qobuz.com";

    public static final String SEARCH_PREFIX_QB = "qbsearch:";
    public static final String SEARCH_PREFIX_ISRC = "qbisrc:";
    public static final String SEARCH_PREFIX_REC = "qbrec:";
    public static final String SEARCH_PREFIX_QOBUZ = "qobuz:";

    private static final Pattern URL_PATTERN = Pattern.compile(
            "https?://(?:www\\.|play\\.|open\\.)?qobuz\\.com/(?:(?:[a-z]{2}-[a-z]{2}/)?(track|album|playlist|artist)/(?:.+?/)?([a-zA-Z0-9]+)|(playlist)/(\\d+))"
    );

    private final AllInOneConfig config;
    private final MirroringAudioTrackResolver resolver;
    private final AudioPlayerManager audioPlayerManager;
    private final HttpInterfaceManager httpInterfaceManager;

    private String appId = null;
    private String appSecret = null;
    private boolean initialized = false;

    public QobuzAudioSourceManager(AllInOneConfig config, AudioPlayerManager audioPlayerManager) {
        this.config = config;
        this.audioPlayerManager = audioPlayerManager;
        this.resolver = new DefaultMirroringAudioTrackResolver(config.getProviders());
        this.httpInterfaceManager = HttpClientTools.createDefaultThreadLocalManager();
        initializeCredentials();
    }

    public AllInOneConfig getConfig() {
        return config;
    }

    public HttpInterface getHttpInterface() {
        return this.httpInterfaceManager.getInterface();
    }

    private synchronized void initializeCredentials() {
        if (config.getQobuzAppId() != null && !config.getQobuzAppId().isBlank()
                && config.getQobuzAppSecret() != null && !config.getQobuzAppSecret().isBlank()) {
            this.appId = config.getQobuzAppId();
            this.appSecret = config.getQobuzAppSecret();
            this.initialized = true;
            log.info("Initialized Qobuz with configured appId: {}", this.appId);
            return;
        }

        try {
            String loginHtml = HttpHelper.get(WEB_PLAYER_BASE_URL + "/login", null, config.getQobuzProxy());
            if (loginHtml == null || loginHtml.isBlank()) {
                log.warn("Failed to fetch Qobuz login page for credential extraction.");
                return;
            }

            Matcher bundleMatcher = Pattern.compile("<script src=\"(/resources/\\d+\\.\\d+\\.\\d+-[a-z]\\d{3}/bundle\\.js)\"").matcher(loginHtml);
            if (!bundleMatcher.find()) {
                bundleMatcher = Pattern.compile("<script src=\"(/resources/[^\"/]+/bundle\\.js)\"").matcher(loginHtml);
            }

            if (!bundleMatcher.find(0)) {
                log.warn("Failed to find Qobuz bundle.js script tag.");
                return;
            }

            String bundlePath = bundleMatcher.group(1);
            String bundleJs = HttpHelper.get(WEB_PLAYER_BASE_URL + bundlePath, null, config.getQobuzProxy());
            if (bundleJs == null || bundleJs.isBlank()) {
                log.warn("Failed to download Qobuz bundle.js.");
                return;
            }

            this.appId = extractAppId(bundleJs);
            this.appSecret = extractAppSecret(bundleJs);

            if (this.appId != null && this.appSecret != null) {
                this.initialized = true;
                log.info("Successfully extracted Qobuz credentials (appId: {})", this.appId);
            } else {
                log.warn("Could not extract appId or appSecret from Qobuz bundle.");
            }
        } catch (Exception e) {
            log.warn("Failed to initialize dynamic Qobuz credentials: {}", e.getMessage());
        }
    }

    private String extractAppId(String content) {
        Matcher matcher = Pattern.compile("production:\\{api:\\{appId:\"([^\"]+)\"").matcher(content);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private String extractAppSecret(String content) {
        try {
            Matcher seedMatch = Pattern.compile("\\):[a-z]\\.initialSeed\\(\"([^\"]+)\",window\\.utimezone\\.([a-zA-Z0-9_]+)\\)").matcher(content);
            if (!seedMatch.find()) {
                return null;
            }

            String seed = seedMatch.group(1);
            String tzRaw = seedMatch.group(2);
            String timezone = tzRaw.substring(0, 1).toUpperCase() + tzRaw.substring(1).toLowerCase();

            Pattern infoPattern = Pattern.compile("timezones:\\[.*?name:.*?/" + Pattern.quote(timezone) + "\",info:\"(?<info>[^\"]+)\",extras:\"(?<extras>[^\"]+)\"");
            Matcher infoMatcher = infoPattern.matcher(content);
            if (!infoMatcher.find()) {
                return null;
            }

            String info = infoMatcher.group("info");
            String extras = infoMatcher.group("extras");
            String encoded = (seed + info + extras);
            if (encoded.length() > 44) {
                encoded = encoded.substring(0, encoded.length() - 44);
            }

            byte[] decoded = Base64.getDecoder().decode(encoded);
            return new String(decoded, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.debug("Error extracting Qobuz appSecret: {}", e.getMessage());
            return null;
        }
    }

    private void ensureCredentials() {
        if (!this.initialized || this.appId == null || this.appSecret == null) {
            initializeCredentials();
        }
    }

    @Override
    @NotNull
    public String getSourceName() {
        return "qobuz";
    }

    @Override
    public MirroringAudioTrackResolver getResolver() {
        return this.resolver;
    }

    @Override
    public AudioPlayerManager getAudioPlayerManager() {
        return this.audioPlayerManager;
    }

    @Override
    public AudioItem loadItem(AudioPlayerManager manager, AudioReference reference) {
        String identifier = reference.identifier;
        if (identifier == null) {
            return null;
        }

        try {
            if (identifier.startsWith(SEARCH_PREFIX_QB)) {
                return search(identifier.substring(SEARCH_PREFIX_QB.length()).trim());
            }
            if (identifier.startsWith(SEARCH_PREFIX_ISRC)) {
                return search(identifier.substring(SEARCH_PREFIX_ISRC.length()).trim());
            }
            if (identifier.startsWith(SEARCH_PREFIX_REC)) {
                return resolveRecommendations(identifier.substring(SEARCH_PREFIX_REC.length()).trim());
            }
            if (identifier.startsWith(SEARCH_PREFIX_QOBUZ)) {
                return search(identifier.substring(SEARCH_PREFIX_QOBUZ.length()).trim());
            }

            Matcher matcher = URL_PATTERN.matcher(identifier);
            if (matcher.find()) {
                String type = matcher.group(1);
                String id = matcher.group(2);
                if (type == null) {
                    type = matcher.group(3);
                    id = matcher.group(4);
                }

                if (type == null || id == null) {
                    return null;
                }

                switch (type.toLowerCase()) {
                    case "track":
                        return resolveTrack(id);
                    case "album":
                        return resolveAlbum(id);
                    case "playlist":
                        return resolvePlaylist(id);
                    case "artist":
                        return resolveArtist(id);
                    default:
                        return null;
                }
            }
        } catch (Exception e) {
            log.error("Failed to load Qobuz audio item: {}", identifier, e);
            throw new FriendlyException("Failed to load Qobuz audio item: " + e.getMessage(), FriendlyException.Severity.COMMON, e);
        }

        return null;
    }

    public AudioItem search(String query) {
        ensureCredentials();
        Map<String, String> params = new HashMap<>();
        params.put("query", query);
        params.put("limit", "10");
        params.put("type", "tracks");

        JSONObject response = apiRequest("/catalog/search", params);
        if (response == null) {
            return AudioReference.NO_TRACK;
        }

        JSONObject tracksBlock = response.optJSONObject("tracks");
        if (tracksBlock == null) {
            return AudioReference.NO_TRACK;
        }

        JSONArray items = tracksBlock.optJSONArray("items");
        if (items == null || items.isEmpty()) {
            return AudioReference.NO_TRACK;
        }

        List<AudioTrack> tracks = new ArrayList<>();
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item != null) {
                tracks.add(buildTrack(item));
            }
        }

        if (tracks.isEmpty()) {
            return AudioReference.NO_TRACK;
        }

        return new QobuzAudioPlaylist("Qobuz Search: " + query, tracks, tracks.get(0), true);
    }

    public AudioItem resolveTrack(String id) {
        ensureCredentials();
        Map<String, String> params = new HashMap<>();
        params.put("track_id", id);

        JSONObject data = apiRequest("/track/get", params);
        if (data == null) {
            params.clear();
            params.put("query", id);
            params.put("type", "tracks");
            params.put("limit", "1");
            JSONObject search = apiRequest("/catalog/search", params);
            if (search != null && search.has("tracks")) {
                JSONArray items = search.getJSONObject("tracks").optJSONArray("items");
                if (items != null && !items.isEmpty()) {
                    data = items.optJSONObject(0);
                }
            }
        }

        if (data == null || !data.has("id")) {
            return AudioReference.NO_TRACK;
        }

        return buildTrack(data);
    }

    public AudioItem resolveAlbum(String id) {
        ensureCredentials();
        int max = config.getQobuzAlbumLimit();
        Map<String, String> params = new HashMap<>();
        params.put("album_id", id);
        params.put("limit", String.valueOf(Math.min(max, 50)));

        JSONObject data = apiRequest("/album/get", params);
        if (data == null) {
            params.clear();
            params.put("query", id);
            params.put("type", "albums");
            params.put("limit", "1");
            JSONObject search = apiRequest("/catalog/search", params);
            if (search != null && search.has("albums")) {
                JSONArray albums = search.getJSONObject("albums").optJSONArray("items");
                if (albums != null && !albums.isEmpty()) {
                    JSONObject album = albums.getJSONObject(0);
                    String foundId = album.optString("id", String.valueOf(album.opt("qobuz_id")));
                    params.clear();
                    params.put("album_id", foundId);
                    params.put("limit", String.valueOf(Math.min(max, 50)));
                    data = apiRequest("/album/get", params);
                }
            }
        }

        if (data == null) {
            return AudioReference.NO_TRACK;
        }

        JSONObject tracksBlock = data.optJSONObject("tracks");
        if (tracksBlock == null) {
            return AudioReference.NO_TRACK;
        }

        List<JSONObject> allItems = fetchRemainingTracks("/album/get", Map.of("album_id", String.valueOf(data.opt("id"))), tracksBlock, max);
        List<AudioTrack> tracks = new ArrayList<>();
        for (JSONObject item : allItems) {
            if (!item.has("album")) {
                JSONObject albumInfo = new JSONObject();
                albumInfo.put("title", data.optString("title"));
                albumInfo.put("image", data.optJSONObject("image"));
                albumInfo.put("id", data.opt("id"));
                item.put("album", albumInfo);
            }
            tracks.add(buildTrack(item));
        }

        String albumName = data.optString("title", "Unknown Album");
        return new QobuzAudioPlaylist(albumName, tracks, tracks.isEmpty() ? null : tracks.get(0), false);
    }

    public AudioItem resolvePlaylist(String id) {
        ensureCredentials();
        int max = config.getQobuzPlaylistLimit();
        Map<String, String> params = new HashMap<>();
        params.put("playlist_id", id);
        params.put("extra", "tracks");
        params.put("limit", String.valueOf(Math.min(max, 50)));

        JSONObject data = apiRequest("/playlist/get", params);
        if (data == null) {
            return AudioReference.NO_TRACK;
        }

        JSONObject tracksBlock = data.optJSONObject("tracks");
        if (tracksBlock == null) {
            return AudioReference.NO_TRACK;
        }

        List<JSONObject> allItems = fetchRemainingTracks("/playlist/get", Map.of("playlist_id", id, "extra", "tracks"), tracksBlock, max);
        List<AudioTrack> tracks = new ArrayList<>();
        for (JSONObject item : allItems) {
            tracks.add(buildTrack(item));
        }

        String name = data.optString("name", "Unknown Playlist");
        return new QobuzAudioPlaylist(name, tracks, tracks.isEmpty() ? null : tracks.get(0), false);
    }

    public AudioItem resolveArtist(String id) {
        ensureCredentials();
        int max = config.getQobuzArtistLimit();
        Map<String, String> params = new HashMap<>();
        params.put("artist_id", id);
        params.put("extra", "tracks");
        params.put("limit", String.valueOf(Math.min(max, 50)));

        JSONObject data = apiRequest("/artist/get", params);
        if (data == null) {
            return AudioReference.NO_TRACK;
        }

        JSONObject tracksBlock = data.optJSONObject("tracks");
        if (tracksBlock == null) {
            return AudioReference.NO_TRACK;
        }

        List<JSONObject> allItems = fetchRemainingTracks("/artist/get", Map.of("artist_id", id, "extra", "tracks"), tracksBlock, max);
        List<AudioTrack> tracks = new ArrayList<>();
        for (JSONObject item : allItems) {
            tracks.add(buildTrack(item));
        }

        String name = data.optString("name", "Unknown Artist") + "'s Top Tracks";
        return new QobuzAudioPlaylist(name, tracks, tracks.isEmpty() ? null : tracks.get(0), false);
    }

    public AudioItem resolveRecommendations(String id) {
        ensureCredentials();
        try {
            Map<String, String> params = new HashMap<>();
            params.put("track_id", id);
            JSONObject trackData = apiRequest("/track/get", params);
            if (trackData == null) {
                return AudioReference.NO_TRACK;
            }

            JSONObject performer = trackData.optJSONObject("performer");
            JSONObject artist = trackData.optJSONObject("artist");
            long artistId = 0;
            if (performer != null && performer.has("id")) {
                artistId = performer.optLong("id");
            } else if (artist != null && artist.has("id")) {
                artistId = artist.optLong("id");
            }

            if (artistId == 0) {
                return AudioReference.NO_TRACK;
            }

            JSONObject payload = new JSONObject();
            payload.put("limit", 20);
            payload.put("listened_tracks_ids", new JSONArray(List.of(Long.parseLong(id))));

            JSONObject trackToAnalyse = new JSONObject();
            trackToAnalyse.put("track_id", Long.parseLong(id));
            trackToAnalyse.put("artist_id", artistId);
            payload.put("track_to_analyse", new JSONArray(List.of(trackToAnalyse)));

            Map<String, String> headers = new HashMap<>();
            headers.put("Content-Type", "application/json");
            if (this.appId != null) {
                headers.put("x-app-id", this.appId);
            }
            if (config.getQobuzUserToken() != null && !config.getQobuzUserToken().isBlank()) {
                headers.put("x-user-auth-token", config.getQobuzUserToken());
            }

            String responseBody = HttpHelper.post(API_BASE_URL + "/dynamic/suggest", payload.toString(), headers, config.getQobuzProxy());
            if (responseBody == null || responseBody.isBlank()) {
                return AudioReference.NO_TRACK;
            }

            JSONObject json = new JSONObject(responseBody);
            JSONObject tracksBlock = json.optJSONObject("tracks");
            if (tracksBlock == null) {
                return AudioReference.NO_TRACK;
            }

            JSONArray items = tracksBlock.optJSONArray("items");
            if (items == null || items.isEmpty()) {
                return AudioReference.NO_TRACK;
            }

            List<AudioTrack> tracks = new ArrayList<>();
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.optJSONObject(i);
                if (item != null) {
                    tracks.add(buildTrack(item));
                }
            }

            return new QobuzAudioPlaylist("Qobuz Recommendations", tracks, tracks.isEmpty() ? null : tracks.get(0), false);
        } catch (Exception e) {
            log.warn("Failed to fetch Qobuz recommendations for track {}: {}", id, e.getMessage());
            return AudioReference.NO_TRACK;
        }
    }

    public String getPlaybackStreamUrl(String trackId) {
        ensureCredentials();
        String userToken = config.getQobuzUserToken();
        if (userToken == null || userToken.isBlank() || this.appSecret == null || this.appSecret.isBlank()) {
            return null;
        }

        try {
            long unixTs = System.currentTimeMillis() / 1000L;
            String formatId = config.getQobuzFormatId() != null && !config.getQobuzFormatId().isBlank() ? config.getQobuzFormatId() : "5";
            String sigData = "trackgetFileUrlformat_id" + formatId + "intentstreamtrack_id" + trackId + unixTs + this.appSecret;
            String requestSig = md5(sigData);

            Map<String, String> params = new HashMap<>();
            params.put("request_ts", String.valueOf(unixTs));
            params.put("request_sig", requestSig);
            params.put("track_id", trackId);
            params.put("format_id", formatId);
            params.put("intent", "stream");

            JSONObject data = apiRequest("/track/getFileUrl", params);
            if (data != null) {
                String url = data.optString("url", null);
                boolean sample = data.optBoolean("sample", false) || "true".equalsIgnoreCase(data.optString("sample"));
                if (url != null && !url.isBlank() && !sample) {
                    return url;
                }
            }
        } catch (Exception e) {
            log.debug("Direct stream request failed for Qobuz track {}: {}", trackId, e.getMessage());
        }

        return null;
    }

    private List<JSONObject> fetchRemainingTracks(String path, Map<String, String> params, JSONObject initialTracks, int max) {
        List<JSONObject> items = new ArrayList<>();
        JSONArray initialItems = initialTracks.optJSONArray("items");
        if (initialItems != null) {
            for (int i = 0; i < initialItems.length(); i++) {
                JSONObject item = initialItems.optJSONObject(i);
                if (item != null) {
                    items.add(item);
                }
            }
        }

        int total = Math.min(initialTracks.optInt("total", items.size()), max);
        int offset = items.size();

        while (items.size() < total) {
            int limit = Math.min(50, total - items.size());
            Map<String, String> currentParams = new HashMap<>(params);
            currentParams.put("limit", String.valueOf(limit));
            currentParams.put("offset", String.valueOf(offset));

            JSONObject data = apiRequest(path, currentParams);
            if (data == null) break;

            JSONObject tracksBlock = data.optJSONObject("tracks");
            if (tracksBlock == null) break;

            JSONArray batchItems = tracksBlock.optJSONArray("items");
            if (batchItems == null || batchItems.isEmpty()) break;

            for (int i = 0; i < batchItems.length(); i++) {
                JSONObject item = batchItems.optJSONObject(i);
                if (item != null) {
                    items.add(item);
                }
            }

            offset += batchItems.length();
            if (batchItems.length() < limit) break;
        }

        if (items.size() > max) {
            return items.subList(0, max);
        }
        return items;
    }

    private JSONObject apiRequest(String path, Map<String, String> params) {
        try {
            StringBuilder sb = new StringBuilder(API_BASE_URL).append(path);
            if (params != null && !params.isEmpty()) {
                sb.append("?");
                boolean first = true;
                for (Map.Entry<String, String> entry : params.entrySet()) {
                    if (!first) sb.append("&");
                    sb.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8))
                            .append("=")
                            .append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
                    first = false;
                }
            }

            Map<String, String> headers = new HashMap<>();
            if (this.appId != null) {
                headers.put("x-app-id", this.appId);
            }
            if (config.getQobuzUserToken() != null && !config.getQobuzUserToken().isBlank()) {
                headers.put("x-user-auth-token", config.getQobuzUserToken());
            }

            String responseBody = HttpHelper.get(sb.toString(), headers, config.getQobuzProxy());
            if (responseBody == null || responseBody.isBlank()) {
                return null;
            }

            return new JSONObject(responseBody);
        } catch (Exception e) {
            log.debug("Qobuz API request failed for {}: {}", path, e.getMessage());
            return null;
        }
    }

    private AudioTrack buildTrack(JSONObject item) {
        JSONObject artist = item.optJSONObject("artist");
        JSONObject performer = item.optJSONObject("performer");
        JSONObject album = item.optJSONObject("album");
        JSONObject albumImage = album != null ? album.optJSONObject("image") : item.optJSONObject("image");

        String author = "Unknown Artist";
        if (artist != null && !artist.optString("name", "").isBlank()) {
            author = artist.optString("name");
        } else if (performer != null && !performer.optString("name", "").isBlank()) {
            author = performer.optString("name");
        }

        String title = item.optString("title", "Unknown Title");
        long duration = item.optLong("duration", 0L) * 1000L;
        String identifier = String.valueOf(item.opt("id"));
        String isrc = item.optString("isrc", null);
        String artworkUrl = null;
        if (albumImage != null) {
            artworkUrl = albumImage.optString("large", albumImage.optString("small", null));
        }
        String uri = "https://open.qobuz.com/track/" + identifier;
        String albumName = album != null ? album.optString("title", null) : null;
        String albumUrl = album != null && album.has("id") ? "https://open.qobuz.com/album/" + album.opt("id") : null;
        String artistUrl = artist != null && artist.has("id") ? "https://open.qobuz.com/artist/" + artist.opt("id") : null;

        AudioTrackInfo trackInfo = new AudioTrackInfo(title, author, duration, identifier, false, uri, artworkUrl, isrc);
        return new QobuzAudioTrack(trackInfo, albumName, albumUrl, artistUrl, artworkUrl, null, false, this);
    }

    private String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    @Override
    @Nullable
    public AudioSearchResult loadSearch(@NotNull String query, @NotNull Set<AudioSearchResult.Type> types) {
        try {
            List<AudioTrack> tracks = new ArrayList<>();
            List<AudioPlaylist> albums = new ArrayList<>();
            List<AudioPlaylist> playlists = new ArrayList<>();
            List<AudioPlaylist> artists = new ArrayList<>();

            if (types.contains(AudioSearchResult.Type.TRACK)) {
                AudioItem item = search(query);
                if (item instanceof AudioPlaylist) {
                    tracks.addAll(((AudioPlaylist) item).getTracks());
                }
            }

            if (types.contains(AudioSearchResult.Type.ALBUM)) {
                try {
                    Map<String, String> params = new HashMap<>();
                    params.put("query", query);
                    params.put("type", "albums");
                    params.put("limit", "10");
                    JSONObject response = apiRequest("/catalog/search", params);
                    if (response != null && response.has("albums")) {
                        JSONArray items = response.getJSONObject("albums").optJSONArray("items");
                        if (items != null) {
                            for (int i = 0; i < items.length(); i++) {
                                JSONObject albumObj = items.getJSONObject(i);
                                String albumId = albumObj.optString("id", String.valueOf(albumObj.opt("qobuz_id")));
                                AudioItem resolved = resolveAlbum(albumId);
                                if (resolved instanceof AudioPlaylist) {
                                    albums.add((AudioPlaylist) resolved);
                                }
                            }
                        }
                    }
                } catch (Exception ignored) {}
            }

            if (types.contains(AudioSearchResult.Type.PLAYLIST)) {
                try {
                    Map<String, String> params = new HashMap<>();
                    params.put("query", query);
                    params.put("type", "playlists");
                    params.put("limit", "10");
                    JSONObject response = apiRequest("/catalog/search", params);
                    if (response != null && response.has("playlists")) {
                        JSONArray items = response.getJSONObject("playlists").optJSONArray("items");
                        if (items != null) {
                            for (int i = 0; i < items.length(); i++) {
                                JSONObject plObj = items.getJSONObject(i);
                                String plId = String.valueOf(plObj.opt("id"));
                                AudioItem resolved = resolvePlaylist(plId);
                                if (resolved instanceof AudioPlaylist) {
                                    playlists.add((AudioPlaylist) resolved);
                                }
                            }
                        }
                    }
                } catch (Exception ignored) {}
            }

            if (types.contains(AudioSearchResult.Type.ARTIST)) {
                try {
                    Map<String, String> params = new HashMap<>();
                    params.put("query", query);
                    params.put("type", "artists");
                    params.put("limit", "10");
                    JSONObject response = apiRequest("/catalog/search", params);
                    if (response != null && response.has("artists")) {
                        JSONArray items = response.getJSONObject("artists").optJSONArray("items");
                        if (items != null) {
                            for (int i = 0; i < items.length(); i++) {
                                JSONObject artObj = items.getJSONObject(i);
                                String artId = String.valueOf(artObj.opt("id"));
                                AudioItem resolved = resolveArtist(artId);
                                if (resolved instanceof AudioPlaylist) {
                                    artists.add((AudioPlaylist) resolved);
                                }
                            }
                        }
                    }
                } catch (Exception ignored) {}
            }

            return new BasicAudioSearchResult(tracks, albums, playlists, artists, Collections.emptyList());
        } catch (Exception e) {
            log.error("Failed to execute Qobuz search for: {}", query, e);
            return null;
        }
    }

    @Override
    public boolean isTrackEncodable(AudioTrack track) {
        return true;
    }

    @Override
    public void encodeTrack(AudioTrack track, DataOutput output) throws IOException {
        QobuzAudioTrack qobuzTrack = (QobuzAudioTrack) track;
        DataFormatTools.writeNullableText(output, qobuzTrack.getAlbumName());
        DataFormatTools.writeNullableText(output, qobuzTrack.getAlbumUrl());
        DataFormatTools.writeNullableText(output, qobuzTrack.getArtistUrl());
        DataFormatTools.writeNullableText(output, qobuzTrack.getArtistArtworkUrl());
        DataFormatTools.writeNullableText(output, qobuzTrack.getPreviewUrl());
        output.writeBoolean(qobuzTrack.isPreview());
    }

    @Override
    public AudioTrack decodeTrack(AudioTrackInfo trackInfo, DataInput input) throws IOException {
        String albumName = null;
        String albumUrl = null;
        String artistUrl = null;
        String artistArtworkUrl = null;
        String previewUrl = null;
        boolean isPreview = false;

        if (((DataInputStream) input).available() > 0) {
            albumName = DataFormatTools.readNullableText(input);
            albumUrl = DataFormatTools.readNullableText(input);
            artistUrl = DataFormatTools.readNullableText(input);
            artistArtworkUrl = DataFormatTools.readNullableText(input);
            previewUrl = DataFormatTools.readNullableText(input);
            isPreview = input.readBoolean();
        }

        return new QobuzAudioTrack(
                trackInfo,
                albumName,
                albumUrl,
                artistUrl,
                artistArtworkUrl,
                previewUrl,
                isPreview,
                this
        );
    }

    @Override
    public void shutdown() {
        try {
            this.httpInterfaceManager.close();
        } catch (Exception ignored) {
        }
    }
}
