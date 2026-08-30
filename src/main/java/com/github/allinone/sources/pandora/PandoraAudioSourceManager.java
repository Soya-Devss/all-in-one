package com.github.allinone.sources.pandora;

import com.github.allinone.mirror.DefaultMirroringAudioTrackResolver;
import com.github.allinone.mirror.MirroringAudioSourceManager;
import com.github.allinone.mirror.MirroringAudioTrackResolver;
import com.github.allinone.tools.HttpHelper;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.tools.DataFormatTools;
import com.sedmelluq.discord.lavaplayer.track.AudioItem;
import com.sedmelluq.discord.lavaplayer.track.AudioReference;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.IOException;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PandoraAudioSourceManager implements MirroringAudioSourceManager {

    private static final Logger log = LoggerFactory.getLogger(PandoraAudioSourceManager.class);

    private static final String PANDORA_BASE_URL = "https://www.pandora.com";
    private static final String PANDORA_CDN_BASE = "https://content-images.p-cdn.com/";
    public static final String SEARCH_PREFIX_PD = "pdsearch:";

    private static final Pattern URL_PATTERN = Pattern.compile("https?://(?:www\\.)?pandora\\.com/(playlist|station|podcast|artist)/([^/?#]+)");
    private static final Pattern CSRF_COOKIE_PATTERN = Pattern.compile("csrftoken=([a-f0-9]{16})");

    private final MirroringAudioTrackResolver resolver;
    private final AudioPlayerManager audioPlayerManager;

    private String csrfToken = null;
    private String authToken = null;
    private long lastAuthTime = 0;

    public PandoraAudioSourceManager(String[] customProviders, AudioPlayerManager audioPlayerManager) {
        this.audioPlayerManager = audioPlayerManager;
        this.resolver = new DefaultMirroringAudioTrackResolver(customProviders);
    }

    @Override
    public String getSourceName() {
        return "pandora";
    }

    @Override
    public MirroringAudioTrackResolver getResolver() {
        return this.resolver;
    }

    @Override
    public AudioPlayerManager getAudioPlayerManager() {
        return this.audioPlayerManager;
    }

    private synchronized void ensureAuth() throws Exception {
        long now = System.currentTimeMillis();
        if (this.authToken != null && this.csrfToken != null && (now - lastAuthTime < 12 * 3600 * 1000L)) {
            return;
        }

        HttpResponse<Void> headResponse = HttpHelper.head(PANDORA_BASE_URL, null);
        List<String> setCookies = headResponse.headers().allValues("set-cookie");
        for (String cookie : setCookies) {
            Matcher m = CSRF_COOKIE_PATTERN.matcher(cookie);
            if (m.find()) {
                this.csrfToken = m.group(1);
                break;
            }
        }

        if (this.csrfToken == null) {
            this.csrfToken = "1234567890abcdef";
        }

        Map<String, String> authHeaders = new HashMap<>();
        authHeaders.put("Cookie", "csrftoken=" + this.csrfToken);
        authHeaders.put("X-CsrfToken", this.csrfToken);
        authHeaders.put("Content-Type", "application/json");

        String authResponse = HttpHelper.post(PANDORA_BASE_URL + "/api/v1/auth/anonymousLogin", "{}", authHeaders);
        JSONObject authJson = new JSONObject(authResponse);
        this.authToken = authJson.optString("authToken", null);
        this.lastAuthTime = System.currentTimeMillis();
    }

    private Map<String, String> getApiHeaders() throws Exception {
        ensureAuth();
        Map<String, String> headers = new HashMap<>();
        headers.put("Cookie", "csrftoken=" + this.csrfToken);
        headers.put("X-CsrfToken", this.csrfToken);
        headers.put("X-AuthToken", this.authToken != null ? this.authToken : "");
        headers.put("Content-Type", "application/json");
        return headers;
    }

    @Override
    public AudioItem loadItem(AudioPlayerManager manager, AudioReference reference) {
        String identifier = reference.identifier;

        try {
            if (identifier.startsWith(SEARCH_PREFIX_PD)) {
                return search(identifier.substring(SEARCH_PREFIX_PD.length()).trim());
            }

            Matcher matcher = URL_PATTERN.matcher(identifier);
            if (matcher.find()) {
                String type = matcher.group(1);
                String id = matcher.group(2);
                return resolveUrl(type, id, identifier);
            }
        } catch (Exception e) {
            log.error("Error loading Pandora item: {}", identifier, e);
        }

        return null;
    }

    private AudioItem search(String query) throws Exception {
        JSONObject body = new JSONObject();
        body.put("query", query);
        body.put("types", new JSONArray().put("TR"));
        body.put("listener", JSONObject.NULL);
        body.put("start", 0);
        body.put("count", 15);
        body.put("annotate", true);
        body.put("searchTime", 0);
        body.put("annotationRecipe", "CLASS_OF_2019");

        String response = HttpHelper.post(PANDORA_BASE_URL + "/api/v3/sod/search", body.toString(), getApiHeaders());
        JSONObject json = new JSONObject(response);

        List<AudioTrack> tracks = new ArrayList<>();
        JSONObject annotations = json.optJSONObject("annotations");
        if (annotations != null) {
            for (String key : annotations.keySet()) {
                JSONObject item = annotations.optJSONObject(key);
                if (item != null && "TR".equalsIgnoreCase(item.optString("type"))) {
                    AudioTrack track = parseTrack(item);
                    if (track != null) {
                        tracks.add(track);
                    }
                }
            }
        }

        if (tracks.isEmpty()) {
            return AudioReference.NO_TRACK;
        }

        return new PandoraAudioPlaylist("Pandora Search: " + query, tracks, null, true);
    }

    private AudioItem resolveUrl(String type, String id, String originalUrl) throws Exception {
        JSONObject body = new JSONObject();
        body.put("catalogVersion", 4);
        body.put("pandoraIds", new JSONArray().put(id));

        String response = HttpHelper.post(PANDORA_BASE_URL + "/api/v1/aesop/annotateObjects", body.toString(), getApiHeaders());
        JSONObject json = new JSONObject(response);

        List<AudioTrack> tracks = new ArrayList<>();
        String collectionName = "Pandora " + type;
        JSONObject annotations = json.optJSONObject("annotations");
        if (annotations != null) {
            for (String key : annotations.keySet()) {
                JSONObject item = annotations.optJSONObject(key);
                if (item != null) {
                    if ("TR".equalsIgnoreCase(item.optString("type"))) {
                        AudioTrack track = parseTrack(item);
                        if (track != null) {
                            tracks.add(track);
                        }
                    } else if (item.has("name")) {
                        collectionName = item.getString("name");
                    }
                }
            }
        }

        if (tracks.isEmpty()) {
            return AudioReference.NO_TRACK;
        }

        if (tracks.size() == 1) {
            return tracks.get(0);
        }

        return new PandoraAudioPlaylist(collectionName, tracks, null, false);
    }

    private AudioTrack parseTrack(JSONObject item) {
        String id = item.optString("pandoraId", item.optString("id", null));
        String title = item.optString("name", "Unknown Title");
        if (id == null || id.isBlank()) {
            return null;
        }

        String author = "Unknown Artist";
        if (item.has("artistName")) {
            Object aObj = item.get("artistName");
            if (aObj instanceof JSONObject) {
                author = ((JSONObject) aObj).optString("name", "Unknown Artist");
            } else {
                author = String.valueOf(aObj);
            }
        }

        long duration = item.optLong("duration", item.optLong("trackLength", item.optLong("length", 0))) * 1000;
        String isrc = item.optString("isrc", null);

        String artworkUrl = null;
        JSONObject icon = item.optJSONObject("icon");
        if (icon != null && icon.has("artUrl")) {
            String art = icon.getString("artUrl");
            artworkUrl = art.startsWith("http") ? art : PANDORA_CDN_BASE + art;
        }

        String path = item.optString("shareableUrlPath", item.optString("urlPath", ""));
        String uri = path.startsWith("http") ? path : PANDORA_BASE_URL + path;

        AudioTrackInfo trackInfo = new AudioTrackInfo(
                title,
                author,
                duration,
                id,
                false,
                uri,
                artworkUrl,
                isrc
        );

        return new PandoraAudioTrack(
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

    @Override
    public boolean isTrackEncodable(AudioTrack track) {
        return true;
    }

    @Override
    public void encodeTrack(AudioTrack track, DataOutput output) throws IOException {
        PandoraAudioTrack pandoraTrack = (PandoraAudioTrack) track;
        DataFormatTools.writeNullableText(output, pandoraTrack.getAlbumName());
        DataFormatTools.writeNullableText(output, pandoraTrack.getAlbumUrl());
        DataFormatTools.writeNullableText(output, pandoraTrack.getArtistUrl());
        DataFormatTools.writeNullableText(output, pandoraTrack.getArtistArtworkUrl());
        DataFormatTools.writeNullableText(output, pandoraTrack.getPreviewUrl());
        output.writeBoolean(pandoraTrack.isPreview());
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

        return new PandoraAudioTrack(
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
    }
}
