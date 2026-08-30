package com.github.allinone.sources.pandora;

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
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PandoraAudioSourceManager implements MirroringAudioSourceManager, AudioSearchManager {

    private static final Logger log = LoggerFactory.getLogger(PandoraAudioSourceManager.class);

    private static final String PANDORA_BASE_URL = "https://www.pandora.com";
    private static final String PANDORA_CDN_BASE = "https://content-images.p-cdn.com/";
    public static final String SEARCH_PREFIX_PD = "pdsearch:";

    private static final Pattern URL_PATTERN = Pattern.compile("https?://(?:www\\.)?pandora\\.com/(playlist|station|podcast|artist)/([^/?#]+)");
    private static final Pattern CSRF_COOKIE_PATTERN = Pattern.compile("csrftoken=([a-f0-9]{16})");

    private final AllInOneConfig config;
    private final MirroringAudioTrackResolver resolver;
    private final AudioPlayerManager audioPlayerManager;

    private String csrfToken = null;
    private String authToken = null;
    private long lastAuthTime = 0;

    public PandoraAudioSourceManager(AllInOneConfig config, AudioPlayerManager audioPlayerManager) {
        this.config = config;
        this.audioPlayerManager = audioPlayerManager;
        this.resolver = new DefaultMirroringAudioTrackResolver(config.getProviders());
    }

    @Override
    @NotNull
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

        HttpResponse<Void> headResponse = HttpHelper.head(PANDORA_BASE_URL, null, config.getPandoraProxy());
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

        String authResponse = HttpHelper.post(PANDORA_BASE_URL + "/api/v1/auth/anonymousLogin", "{}", authHeaders, config.getPandoraProxy());
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
                switch (type) {
                    case "playlist":
                        return getPlaylist(id);
                    case "station":
                        return getStation(id);
                    default:
                        break;
                }
            }
        } catch (Exception e) {
            log.error("Error loading Pandora item: {}", identifier, e);
        }

        return null;
    }

    @Override
    @Nullable
    public AudioSearchResult loadSearch(@NotNull String query, @NotNull Set<AudioSearchResult.Type> types) {
        try {
            List<AudioTrack> tracks = new ArrayList<>();
            if (types.contains(AudioSearchResult.Type.TRACK)) {
                AudioItem item = search(query);
                if (item instanceof AudioPlaylist) {
                    tracks.addAll(((AudioPlaylist) item).getTracks());
                }
            }

            if (tracks.isEmpty()) {
                return null;
            }

            return new BasicAudioSearchResult(tracks, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
        } catch (Exception e) {
            log.error("Error performing LavaSearch for Pandora: {}", query, e);
            return null;
        }
    }

    private AudioItem search(String query) throws Exception {
        JSONObject body = new JSONObject();
        body.put("query", query);
        body.put("types", new JSONArray(List.of("TR")));
        body.put("count", 15);

        String response = HttpHelper.post(PANDORA_BASE_URL + "/api/v1/music/search", body.toString(), getApiHeaders(), config.getPandoraProxy());
        JSONObject json = new JSONObject(response);

        List<AudioTrack> tracks = new ArrayList<>();
        JSONArray results = json.optJSONArray("results");
        if (results != null) {
            List<String> trackPandoraIds = new ArrayList<>();
            for (int i = 0; i < results.length(); i++) {
                JSONObject res = results.getJSONObject(i);
                String pandoraId = res.optString("pandoraId", null);
                if (pandoraId != null) {
                    trackPandoraIds.add(pandoraId);
                }
            }

            if (!trackPandoraIds.isEmpty()) {
                Map<String, JSONObject> annotated = annotateObjects(trackPandoraIds);
                for (String pid : trackPandoraIds) {
                    JSONObject item = annotated.get(pid);
                    if (item != null) {
                        AudioTrack track = parseAnnotatedTrack(item);
                        if (track != null) {
                            tracks.add(track);
                        }
                    }
                }
            }
        }

        if (tracks.isEmpty()) {
            return AudioReference.NO_TRACK;
        }

        return new PandoraAudioPlaylist("Pandora Search: " + query, tracks, null, true);
    }

    private AudioItem getPlaylist(String playlistId) throws Exception {
        JSONObject body = new JSONObject();
        body.put("pandoraId", playlistId);
        body.put("pageSize", 50);

        String response = HttpHelper.post(PANDORA_BASE_URL + "/api/v1/playlist/getTracks", body.toString(), getApiHeaders(), config.getPandoraProxy());
        JSONObject json = new JSONObject(response);

        List<AudioTrack> tracks = new ArrayList<>();
        JSONArray tracksArr = json.optJSONArray("tracks");
        if (tracksArr != null) {
            List<String> trackIds = new ArrayList<>();
            for (int i = 0; i < tracksArr.length(); i++) {
                JSONObject t = tracksArr.getJSONObject(i);
                String trackPandoraId = t.optString("trackPandoraId", null);
                if (trackPandoraId != null) {
                    trackIds.add(trackPandoraId);
                }
            }

            if (!trackIds.isEmpty()) {
                Map<String, JSONObject> annotated = annotateObjects(trackIds);
                for (String tid : trackIds) {
                    JSONObject item = annotated.get(tid);
                    if (item != null) {
                        AudioTrack track = parseAnnotatedTrack(item);
                        if (track != null) {
                            tracks.add(track);
                        }
                    }
                }
            }
        }

        return new PandoraAudioPlaylist("Pandora Playlist", tracks, null, false);
    }

    private AudioItem getStation(String stationId) throws Exception {
        JSONObject body = new JSONObject();
        body.put("stationId", stationId);

        String response = HttpHelper.post(PANDORA_BASE_URL + "/api/v1/station/getStation", body.toString(), getApiHeaders(), config.getPandoraProxy());
        JSONObject json = new JSONObject(response);

        String name = json.optString("name", "Pandora Station");
        return new PandoraAudioPlaylist(name, Collections.emptyList(), null, false);
    }

    private Map<String, JSONObject> annotateObjects(List<String> pandoraIds) throws Exception {
        JSONObject body = new JSONObject();
        body.put("pandoraIds", new JSONArray(pandoraIds));

        String response = HttpHelper.post(PANDORA_BASE_URL + "/api/v1/music/annotateObjects", body.toString(), getApiHeaders(), config.getPandoraProxy());
        JSONObject json = new JSONObject(response);

        Map<String, JSONObject> map = new HashMap<>();
        for (String key : json.keySet()) {
            map.put(key, json.getJSONObject(key));
        }
        return map;
    }

    private AudioTrack parseAnnotatedTrack(JSONObject item) {
        String name = item.optString("name", null);
        if (name == null) {
            return null;
        }

        String artistName = item.optString("artistName", "Unknown Artist");
        long duration = item.optLong("duration", 0) * 1000;
        String trackId = item.optString("pandoraId", item.optString("id", null));
        String shareName = item.optString("shareName", trackId);
        String uri = PANDORA_BASE_URL + "/track/" + shareName;

        String iconArtId = null;
        JSONObject iconNode = item.optJSONObject("icon");
        if (iconNode != null) {
            iconArtId = iconNode.optString("artId", null);
        }
        String artworkUrl = iconArtId != null ? PANDORA_CDN_BASE + iconArtId : null;
        String isrc = item.optString("isrc", null);

        AudioTrackInfo trackInfo = new AudioTrackInfo(
                name,
                artistName,
                duration,
                trackId,
                false,
                uri,
                artworkUrl,
                isrc
        );

        return new PandoraAudioTrack(
                trackInfo,
                item.optString("albumTitle", null),
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
