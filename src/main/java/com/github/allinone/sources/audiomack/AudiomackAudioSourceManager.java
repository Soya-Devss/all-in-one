package com.github.allinone.sources.audiomack;

import com.github.allinone.mirror.DefaultMirroringAudioTrackResolver;
import com.github.allinone.mirror.MirroringAudioSourceManager;
import com.github.allinone.mirror.MirroringAudioTrackResolver;
import com.github.allinone.tools.HttpHelper;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.tools.DataFormatTools;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterfaceManager;
import com.sedmelluq.discord.lavaplayer.track.AudioItem;
import com.sedmelluq.discord.lavaplayer.track.AudioReference;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.github.topi314.lavasearch.AudioSearchManager;
import com.github.topi314.lavasearch.result.AudioSearchResult;
import com.github.topi314.lavasearch.result.BasicAudioSearchResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.util.Set;

public class AudiomackAudioSourceManager implements MirroringAudioSourceManager, AudioSearchManager {

    private static final Logger log = LoggerFactory.getLogger(AudiomackAudioSourceManager.class);

    private static final String API_BASE = "https://api.audiomack.com/v1";
    private static final String CONSUMER_KEY = "audiomack-web";
    private static final String CONSUMER_SECRET = "bd8a07e9f23fbe9d808646b730f89b8e";
    public static final String SEARCH_PREFIX_ADM = "admsearch:";
    public static final String SEARCH_PREFIX_AUDIOMACK = "audiomack:";

    private static final Pattern SONG_PATTERN = Pattern.compile("https?://(?:www\\.)?audiomack\\.com/([^/]+)/song/([^/?#]+)");
    private static final Pattern ALBUM_PATTERN = Pattern.compile("https?://(?:www\\.)?audiomack\\.com/([^/]+)/album/([^/?#]+)");
    private static final Pattern PLAYLIST_PATTERN = Pattern.compile("https?://(?:www\\.)?audiomack\\.com/([^/]+)/playlist/([^/?#]+)");

    private final MirroringAudioTrackResolver resolver;
    private final AudioPlayerManager audioPlayerManager;
    private final HttpInterfaceManager httpInterfaceManager;
    private final SecureRandom secureRandom = new SecureRandom();

    public AudiomackAudioSourceManager(String[] customProviders, AudioPlayerManager audioPlayerManager) {
        this.audioPlayerManager = audioPlayerManager;
        this.resolver = new DefaultMirroringAudioTrackResolver(customProviders);
        this.httpInterfaceManager = HttpClientTools.createDefaultThreadLocalManager();
    }

    @Override
    @NotNull
    public String getSourceName() {
        return "audiomack";
    }

    @Override
    @Nullable
    public AudioSearchResult loadSearch(@NotNull String query, @NotNull Set<AudioSearchResult.Type> types) {
        try {
            List<AudioTrack> tracks = new ArrayList<>();
            List<AudioPlaylist> albums = new ArrayList<>();
            List<AudioPlaylist> playlists = new ArrayList<>();

            if (types.contains(AudioSearchResult.Type.TRACK)) {
                AudioItem item = search(query);
                if (item instanceof AudioPlaylist) {
                    tracks.addAll(((AudioPlaylist) item).getTracks());
                }
            }

            if (types.contains(AudioSearchResult.Type.ALBUM)) {
                try {
                    Map<String, String> params = new TreeMap<>();
                    params.put("q", query);
                    params.put("show", "albums");
                    params.put("limit", "10");
                    String signedUrl = buildSignedUrl("GET", API_BASE + "/search", params);
                    String response = HttpHelper.get(signedUrl, Collections.emptyMap());
                    JSONObject json = new JSONObject(response);
                    JSONArray results = json.optJSONArray("results");
                    if (results != null) {
                        for (int i = 0; i < results.length(); i++) {
                            JSONObject albumObj = results.getJSONObject(i);
                            String title = albumObj.optString("title", "Album");
                            albums.add(new AudiomackAudioPlaylist(title, Collections.emptyList(), null, false));
                        }
                    }
                } catch (Exception ignored) {
                }
            }

            if (types.contains(AudioSearchResult.Type.PLAYLIST)) {
                try {
                    Map<String, String> params = new TreeMap<>();
                    params.put("q", query);
                    params.put("show", "playlists");
                    params.put("limit", "10");
                    String signedUrl = buildSignedUrl("GET", API_BASE + "/search", params);
                    String response = HttpHelper.get(signedUrl, Collections.emptyMap());
                    JSONObject json = new JSONObject(response);
                    JSONArray results = json.optJSONArray("results");
                    if (results != null) {
                        for (int i = 0; i < results.length(); i++) {
                            JSONObject playlistObj = results.getJSONObject(i);
                            String title = playlistObj.optString("title", "Playlist");
                            playlists.add(new AudiomackAudioPlaylist(title, Collections.emptyList(), null, false));
                        }
                    }
                } catch (Exception ignored) {
                }
            }

            if (tracks.isEmpty() && albums.isEmpty() && playlists.isEmpty()) {
                return null;
            }

            return new BasicAudioSearchResult(tracks, albums, Collections.emptyList(), playlists, Collections.emptyList());
        } catch (Exception e) {
            log.error("Error performing LavaSearch for Audiomack: {}", query, e);
            return null;
        }
    }

    @Override
    public MirroringAudioTrackResolver getResolver() {
        return this.resolver;
    }

    @Override
    public AudioPlayerManager getAudioPlayerManager() {
        return this.audioPlayerManager;
    }

    public HttpInterface getHttpInterface() {
        return this.httpInterfaceManager.getInterface();
    }

    @Override
    public AudioItem loadItem(AudioPlayerManager manager, AudioReference reference) {
        String identifier = reference.identifier;

        try {
            if (identifier.startsWith(SEARCH_PREFIX_ADM)) {
                return search(identifier.substring(SEARCH_PREFIX_ADM.length()).trim());
            }
            if (identifier.startsWith(SEARCH_PREFIX_AUDIOMACK)) {
                return search(identifier.substring(SEARCH_PREFIX_AUDIOMACK.length()).trim());
            }

            Matcher songMatcher = SONG_PATTERN.matcher(identifier);
            if (songMatcher.find()) {
                return getSong(songMatcher.group(1), songMatcher.group(2), identifier);
            }

            Matcher albumMatcher = ALBUM_PATTERN.matcher(identifier);
            if (albumMatcher.find()) {
                return getAlbum(albumMatcher.group(1), albumMatcher.group(2));
            }

            Matcher playlistMatcher = PLAYLIST_PATTERN.matcher(identifier);
            if (playlistMatcher.find()) {
                return getPlaylist(playlistMatcher.group(1), playlistMatcher.group(2));
            }
        } catch (Exception e) {
            log.error("Error loading Audiomack item: {}", identifier, e);
        }

        return null;
    }

    private AudioItem search(String query) throws Exception {
        Map<String, String> params = new TreeMap<>();
        params.put("q", query);
        params.put("limit", "20");
        params.put("show", "music");
        params.put("sort", "popular");
        params.put("page", "1");
        params.put("section", "/search");

        String signedUrl = buildSignedUrl("GET", API_BASE + "/search", params);
        String response = HttpHelper.get(signedUrl, Collections.emptyMap());
        JSONObject json = new JSONObject(response);

        List<AudioTrack> tracks = new ArrayList<>();
        if (json.has("results")) {
            JSONArray results = json.getJSONArray("results");
            for (int i = 0; i < results.length(); i++) {
                JSONObject item = results.getJSONObject(i);
                if ("song".equalsIgnoreCase(item.optString("type"))) {
                    AudioTrack track = parseTrack(item, null);
                    if (track != null) {
                        tracks.add(track);
                    }
                }
            }
        }

        if (tracks.isEmpty()) {
            return AudioReference.NO_TRACK;
        }

        return new AudiomackAudioPlaylist("Audiomack Search: " + query, tracks, null, true);
    }

    private AudioItem getSong(String artistSlug, String songSlug, String originalUrl) throws Exception {
        Map<String, String> params = new TreeMap<>();
        params.put("section", "/" + artistSlug + "/song/" + songSlug);

        String signedUrl = buildSignedUrl("GET", API_BASE + "/music/song/" + artistSlug + "/" + songSlug, params);
        String response = HttpHelper.get(signedUrl, Collections.emptyMap());
        JSONObject json = new JSONObject(response);

        JSONObject trackObj = json.has("results") ? json.optJSONObject("results") : (json.has("result") ? json.optJSONObject("result") : json);
        if (trackObj == null && json.has("results") && json.optJSONArray("results") != null && !json.getJSONArray("results").isEmpty()) {
            trackObj = json.getJSONArray("results").getJSONObject(0);
        }

        if (trackObj == null) {
            return AudioReference.NO_TRACK;
        }

        AudioTrack track = parseTrack(trackObj, originalUrl);
        return track != null ? track : AudioReference.NO_TRACK;
    }

    private AudioItem getAlbum(String artistSlug, String albumSlug) throws Exception {
        Map<String, String> params = new TreeMap<>();
        params.put("section", "/" + artistSlug + "/album/" + albumSlug);

        String signedUrl = buildSignedUrl("GET", API_BASE + "/music/album/" + artistSlug + "/" + albumSlug, params);
        String response = HttpHelper.get(signedUrl, Collections.emptyMap());
        JSONObject json = new JSONObject(response);

        JSONObject albumObj = json.has("results") ? json.optJSONObject("results") : json;
        if (albumObj == null && json.has("results") && json.optJSONArray("results") != null && !json.getJSONArray("results").isEmpty()) {
            albumObj = json.getJSONArray("results").getJSONObject(0);
        }

        if (albumObj == null) {
            return AudioReference.NO_TRACK;
        }

        String albumTitle = albumObj.optString("title", "Unknown Album");
        List<AudioTrack> tracks = new ArrayList<>();
        JSONArray tracksArr = albumObj.optJSONArray("tracks");
        if (tracksArr != null) {
            for (int i = 0; i < tracksArr.length(); i++) {
                AudioTrack track = parseTrack(tracksArr.getJSONObject(i), null);
                if (track != null) {
                    tracks.add(track);
                }
            }
        }

        return new AudiomackAudioPlaylist(albumTitle, tracks, null, false);
    }

    private AudioItem getPlaylist(String artistSlug, String playlistSlug) throws Exception {
        Map<String, String> params = new TreeMap<>();
        params.put("section", "/" + artistSlug + "/playlist/" + playlistSlug);

        String signedUrl = buildSignedUrl("GET", API_BASE + "/music/playlist/" + artistSlug + "/" + playlistSlug, params);
        String response = HttpHelper.get(signedUrl, Collections.emptyMap());
        JSONObject json = new JSONObject(response);

        JSONObject playlistObj = json.has("results") ? json.optJSONObject("results") : json;
        if (playlistObj == null && json.has("results") && json.optJSONArray("results") != null && !json.getJSONArray("results").isEmpty()) {
            playlistObj = json.getJSONArray("results").getJSONObject(0);
        }

        if (playlistObj == null) {
            return AudioReference.NO_TRACK;
        }

        String playlistTitle = playlistObj.optString("title", "Unknown Playlist");
        List<AudioTrack> tracks = new ArrayList<>();
        JSONArray tracksArr = playlistObj.optJSONArray("tracks");
        if (tracksArr != null) {
            for (int i = 0; i < tracksArr.length(); i++) {
                AudioTrack track = parseTrack(tracksArr.getJSONObject(i), null);
                if (track != null) {
                    tracks.add(track);
                }
            }
        }

        return new AudiomackAudioPlaylist(playlistTitle, tracks, null, false);
    }

    private AudioTrack parseTrack(JSONObject item, String fallbackUri) {
        String id = item.optString("id", null);
        if (id == null || id.isBlank()) {
            return null;
        }

        String title = item.optString("title", "Unknown Title");
        String artist = item.optString("artist", "Unknown Artist");
        long duration = item.optLong("duration", 0) * 1000;
        String artworkUrl = item.optString("image", null);
        String isrc = item.optString("isrc", null);

        String uri = fallbackUri;
        if (uri == null || uri.isBlank()) {
            String uploaderSlug = item.optString("uploader_url_slug", item.optString("artist_slug", ""));
            String songSlug = item.optString("url_slug", item.optString("slug", ""));
            if (!uploaderSlug.isBlank() && !songSlug.isBlank()) {
                uri = "https://audiomack.com/" + uploaderSlug + "/song/" + songSlug;
            } else {
                uri = "https://audiomack.com/song/" + id;
            }
        }

        AudioTrackInfo trackInfo = new AudioTrackInfo(
                title,
                artist,
                duration,
                id,
                false,
                uri,
                artworkUrl,
                isrc
        );

        return new AudiomackAudioTrack(
                trackInfo,
                null,
                null,
                null,
                artworkUrl,
                null,
                false,
                this
        );
    }

    public String getPlaybackStreamUrl(String trackId, String trackUri) {
        try {
            Map<String, String> params = new TreeMap<>();
            params.put("environment", "desktop-web");
            params.put("hq", "true");
            String section = "/search";
            if (trackUri != null && !trackUri.isBlank()) {
                try {
                    section = new java.net.URI(trackUri).getPath();
                } catch (Exception ignored) {
                }
            }
            params.put("section", section);

            String signedUrl = buildSignedUrl("GET", API_BASE + "/music/play/" + trackId, params);
            String response = HttpHelper.get(signedUrl, Collections.emptyMap());
            JSONObject json = new JSONObject(response);

            JSONObject data = json.has("results") ? json.optJSONObject("results") : (json.has("result") ? json.optJSONObject("result") : json);
            if (data == null && json.has("results") && json.optJSONArray("results") != null && !json.getJSONArray("results").isEmpty()) {
                data = json.getJSONArray("results").getJSONObject(0);
            }

            if (data != null) {
                String streamUrl = data.optString("signedUrl", null);
                if (streamUrl == null) streamUrl = data.optString("signed_url", null);
                if (streamUrl == null) streamUrl = data.optString("url", null);
                if (streamUrl == null) streamUrl = data.optString("streamUrl", null);
                if (streamUrl == null) streamUrl = data.optString("stream_url", null);
                return streamUrl;
            }
        } catch (Exception e) {
            log.debug("Failed to get direct Audiomack stream URL for {}: {}", trackId, e.getMessage());
        }
        return null;
    }

    private String buildSignedUrl(String method, String url, Map<String, String> additionalParams) throws Exception {
        Map<String, String> params = new TreeMap<>(additionalParams);
        params.put("oauth_consumer_key", CONSUMER_KEY);
        byte[] nonceBytes = new byte[16];
        secureRandom.nextBytes(nonceBytes);
        StringBuilder nonce = new StringBuilder();
        for (byte b : nonceBytes) {
            nonce.append(String.format("%02x", b));
        }
        params.put("oauth_nonce", nonce.toString());
        params.put("oauth_signature_method", "HMAC-SHA1");
        params.put("oauth_timestamp", String.valueOf(System.currentTimeMillis() / 1000));
        params.put("oauth_version", "1.0");

        StringBuilder paramString = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (!first) {
                paramString.append("&");
            }
            paramString.append(strictEncode(entry.getKey()))
                    .append("=")
                    .append(strictEncode(entry.getValue()));
            first = false;
        }

        String signatureBase = method.toUpperCase() + "&" + strictEncode(url) + "&" + strictEncode(paramString.toString());
        String signingKey = strictEncode(CONSUMER_SECRET) + "&";

        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(new SecretKeySpec(signingKey.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
        byte[] signatureBytes = mac.doFinal(signatureBase.getBytes(StandardCharsets.UTF_8));
        String signature = Base64.getEncoder().encodeToString(signatureBytes);

        return url + "?" + paramString + "&oauth_signature=" + strictEncode(signature);
    }

    private String strictEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8)
                .replace("+", "%20")
                .replace("*", "%2A")
                .replace("%7E", "~");
    }

    @Override
    public boolean isTrackEncodable(AudioTrack track) {
        return true;
    }

    @Override
    public void encodeTrack(AudioTrack track, DataOutput output) throws IOException {
        AudiomackAudioTrack audiomackTrack = (AudiomackAudioTrack) track;
        DataFormatTools.writeNullableText(output, audiomackTrack.getAlbumName());
        DataFormatTools.writeNullableText(output, audiomackTrack.getAlbumUrl());
        DataFormatTools.writeNullableText(output, audiomackTrack.getArtistUrl());
        DataFormatTools.writeNullableText(output, audiomackTrack.getArtistArtworkUrl());
        DataFormatTools.writeNullableText(output, audiomackTrack.getPreviewUrl());
        output.writeBoolean(audiomackTrack.isPreview());
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

        return new AudiomackAudioTrack(
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
