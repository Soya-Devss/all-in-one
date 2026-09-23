package com.github.allinone.sources.amazonmusic;

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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AmazonMusicAudioSourceManager implements MirroringAudioSourceManager, AudioSearchManager {

    private static final Logger log = LoggerFactory.getLogger(AmazonMusicAudioSourceManager.class);

    public static final String SEARCH_PREFIX_AZ = "azsearch:";
    public static final String SEARCH_PREFIX_AMAZON = "amazonsearch:";
    public static final String SEARCH_PREFIX_AMAZON_MUSIC = "amazonmusic:";

    private static final Pattern TRACK_ASIN_PATTERN = Pattern.compile("[?&]trackAsin=([A-Za-z0-9]+)");
    private static final Pattern URL_PATTERN = Pattern.compile(
            "https?://music\\.amazon\\.[a-z.]+/(?:(?:[a-z]{2}-[a-z]{2}/)?(tracks|albums|artists|playlists|user-playlists)/([A-Za-z0-9]+))"
    );

    private final AllInOneConfig config;
    private final MirroringAudioTrackResolver resolver;
    private final AudioPlayerManager audioPlayerManager;
    private final HttpInterfaceManager httpInterfaceManager;

    public AmazonMusicAudioSourceManager(AllInOneConfig config, AudioPlayerManager audioPlayerManager) {
        this.config = config;
        this.audioPlayerManager = audioPlayerManager;
        this.resolver = new DefaultMirroringAudioTrackResolver(config.getProviders());
        this.httpInterfaceManager = HttpClientTools.createDefaultThreadLocalManager();
    }

    @Override
    @NotNull
    public String getSourceName() {
        return "amazonmusic";
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

    private String getNormalizedApiUrl() {
        String url = config.getAmazonMusicApiUrl();
        if (url == null || url.isBlank()) {
            return null;
        }
        url = url.trim();
        if (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        return url;
    }

    @Override
    public AudioItem loadItem(AudioPlayerManager manager, AudioReference reference) {
        String identifier = reference.identifier;
        if (identifier == null) {
            return null;
        }
        identifier = identifier.trim();

        try {
            if (identifier.startsWith(SEARCH_PREFIX_AZ)) {
                return search(identifier.substring(SEARCH_PREFIX_AZ.length()).trim());
            }
            if (identifier.startsWith(SEARCH_PREFIX_AMAZON)) {
                return search(identifier.substring(SEARCH_PREFIX_AMAZON.length()).trim());
            }
            if (identifier.startsWith(SEARCH_PREFIX_AMAZON_MUSIC)) {
                return search(identifier.substring(SEARCH_PREFIX_AMAZON_MUSIC.length()).trim());
            }

            String normalizedIdentifier = identifier.replaceAll("(?<!:)//+", "/");
            if (!normalizedIdentifier.contains("music.amazon.")) {
                return null;
            }

            Matcher asinMatcher = TRACK_ASIN_PATTERN.matcher(normalizedIdentifier);
            if (asinMatcher.find()) {
                String trackAsin = asinMatcher.group(1);
                return getTrack(trackAsin, normalizedIdentifier);
            }

            Matcher matcher = URL_PATTERN.matcher(normalizedIdentifier);
            if (matcher.find()) {
                String type = matcher.group(1);
                String id = matcher.group(2);
                switch (type) {
                    case "tracks":
                        return getTrack(id, normalizedIdentifier);
                    case "albums":
                        return getAlbum(id, normalizedIdentifier);
                    case "artists":
                        return getArtist(id, normalizedIdentifier);
                    case "playlists":
                        return getPlaylist(id, normalizedIdentifier);
                    case "user-playlists":
                        return getCommunityPlaylist(id, normalizedIdentifier);
                    default:
                        break;
                }
            }
        } catch (Exception e) {
            log.error("Error loading Amazon Music item: {}", identifier, e);
        }

        return null;
    }

    @Override
    @Nullable
    public AudioSearchResult loadSearch(@NotNull String query, @NotNull Set<AudioSearchResult.Type> types) {
        String apiUrl = getNormalizedApiUrl();
        if (apiUrl == null) {
            log.warn("Amazon Music API URL is not configured. Cannot perform LavaSearch.");
            return null;
        }

        try {
            List<AudioTrack> tracks = new ArrayList<>();
            List<AudioPlaylist> albums = new ArrayList<>();
            List<AudioPlaylist> artists = new ArrayList<>();
            List<AudioPlaylist> playlists = new ArrayList<>();

            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);

            if (types.contains(AudioSearchResult.Type.TRACK)) {
                AudioItem item = search(query);
                if (item instanceof AudioPlaylist) {
                    tracks.addAll(((AudioPlaylist) item).getTracks());
                }
            }

            if (types.contains(AudioSearchResult.Type.ALBUM)) {
                try {
                    String url = apiUrl + "/search/albums?query=" + encodedQuery + "&page=1";
                    String response = HttpHelper.get(url, Collections.emptyMap(), config.getAmazonMusicProxy());
                    JSONObject json = new JSONObject(response);
                    JSONArray arr = json.optJSONArray("data");
                    if (arr != null) {
                        for (int i = 0; i < arr.length(); i++) {
                            JSONObject a = arr.getJSONObject(i);
                            String name = a.optString("name", "Unknown Album");
                            albums.add(new AmazonMusicAudioPlaylist(name, Collections.emptyList(), null, false));
                        }
                    }
                } catch (Exception e) {
                    log.debug("Amazon Music LavaSearch albums error: {}", e.getMessage());
                }
            }

            if (types.contains(AudioSearchResult.Type.ARTIST)) {
                try {
                    String url = apiUrl + "/search/artists?query=" + encodedQuery + "&page=1";
                    String response = HttpHelper.get(url, Collections.emptyMap(), config.getAmazonMusicProxy());
                    JSONObject json = new JSONObject(response);
                    JSONArray arr = json.optJSONArray("data");
                    if (arr != null) {
                        for (int i = 0; i < arr.length(); i++) {
                            JSONObject a = arr.getJSONObject(i);
                            String name = a.optString("name", "Unknown Artist");
                            artists.add(new AmazonMusicAudioPlaylist(name, Collections.emptyList(), null, false));
                        }
                    }
                } catch (Exception e) {
                    log.debug("Amazon Music LavaSearch artists error: {}", e.getMessage());
                }
            }

            if (types.contains(AudioSearchResult.Type.PLAYLIST)) {
                try {
                    String url = apiUrl + "/search/playlists?query=" + encodedQuery + "&page=1";
                    String response = HttpHelper.get(url, Collections.emptyMap(), config.getAmazonMusicProxy());
                    JSONObject json = new JSONObject(response);
                    JSONArray arr = json.optJSONArray("data");
                    if (arr != null) {
                        for (int i = 0; i < arr.length(); i++) {
                            JSONObject p = arr.getJSONObject(i);
                            String name = p.optString("name", "Unknown Playlist");
                            playlists.add(new AmazonMusicAudioPlaylist(name, Collections.emptyList(), null, false));
                        }
                    }
                } catch (Exception e) {
                    log.debug("Amazon Music LavaSearch playlists error: {}", e.getMessage());
                }
            }

            if (tracks.isEmpty() && albums.isEmpty() && artists.isEmpty() && playlists.isEmpty()) {
                return null;
            }

            return new BasicAudioSearchResult(tracks, albums, artists, playlists, Collections.emptyList());
        } catch (Exception e) {
            log.error("Error performing LavaSearch for Amazon Music: {}", query, e);
            return null;
        }
    }

    private AudioItem search(String query) {
        String apiUrl = getNormalizedApiUrl();
        if (apiUrl == null) {
            log.warn("Amazon Music API URL is not configured. Please set 'plugins.allinone.amazonMusicApiUrl' in application.yml.");
            return AudioReference.NO_TRACK;
        }

        try {
            String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String url = apiUrl + "/search/songs?query=" + encoded + "&page=1&limit=10";
            String response = HttpHelper.get(url, Collections.emptyMap(), config.getAmazonMusicProxy());
            JSONObject json = new JSONObject(response);

            JSONArray dataArr = json.optJSONArray("data");
            if (dataArr != null && dataArr.length() > 0) {
                List<AudioTrack> tracks = new ArrayList<>();
                for (int i = 0; i < dataArr.length(); i++) {
                    AudioTrack track = parseSong(dataArr.getJSONObject(i), null);
                    if (track != null) {
                        tracks.add(track);
                    }
                }
                if (!tracks.isEmpty()) {
                    return new AmazonMusicAudioPlaylist("Amazon Music Search: " + query, tracks, null, true);
                }
            }
        } catch (Exception e) {
            log.error("Amazon Music search error for {}: {}", query, e.getMessage());
        }

        return AudioReference.NO_TRACK;
    }

    private AudioItem getTrack(String trackId, String originalUrl) {
        String apiUrl = getNormalizedApiUrl();
        if (apiUrl == null) {
            log.warn("Amazon Music API URL is not configured. Please set 'plugins.allinone.amazonMusicApiUrl' in application.yml.");
            return AudioReference.NO_TRACK;
        }

        try {
            String url = apiUrl + "/songs/" + URLEncoder.encode(trackId, StandardCharsets.UTF_8);
            String response = HttpHelper.get(url, Collections.emptyMap(), config.getAmazonMusicProxy());
            JSONObject json = new JSONObject(response);

            JSONObject trackObj = json.optJSONObject("data");
            if (trackObj != null) {
                AudioTrack track = parseSong(trackObj, originalUrl);
                if (track != null) {
                    return track;
                }
            }
        } catch (Exception e) {
            log.debug("Amazon Music getTrack by ID failed for {}: {}", trackId, e.getMessage());
        }

        // Fallback by URL if originalUrl is available
        if (originalUrl != null && !originalUrl.isBlank()) {
            try {
                String fallbackUrl = apiUrl + "/songs?url=" + URLEncoder.encode(originalUrl, StandardCharsets.UTF_8);
                String response = HttpHelper.get(fallbackUrl, Collections.emptyMap(), config.getAmazonMusicProxy());
                JSONObject json = new JSONObject(response);
                JSONObject trackObj = json.optJSONObject("data");
                if (trackObj != null) {
                    AudioTrack track = parseSong(trackObj, originalUrl);
                    if (track != null) {
                        return track;
                    }
                }
            } catch (Exception e) {
                log.debug("Amazon Music getTrack by URL fallback failed for {}: {}", originalUrl, e.getMessage());
            }
        }

        return AudioReference.NO_TRACK;
    }

    private AudioItem getAlbum(String albumId, String originalUrl) {
        String apiUrl = getNormalizedApiUrl();
        if (apiUrl == null) {
            log.warn("Amazon Music API URL is not configured. Please set 'plugins.allinone.amazonMusicApiUrl' in application.yml.");
            return AudioReference.NO_TRACK;
        }

        try {
            String url = apiUrl + "/albums/" + URLEncoder.encode(albumId, StandardCharsets.UTF_8);
            String response = HttpHelper.get(url, Collections.emptyMap(), config.getAmazonMusicProxy());
            JSONObject json = new JSONObject(response);

            JSONObject albumObj = json.optJSONObject("data");
            if (albumObj != null) {
                String albumName = albumObj.optString("name", "Amazon Music Album");
                JSONArray songsArr = albumObj.optJSONArray("songs");
                if (songsArr != null && songsArr.length() > 0) {
                    List<AudioTrack> tracks = new ArrayList<>();
                    for (int i = 0; i < songsArr.length(); i++) {
                        AudioTrack track = parseSong(songsArr.getJSONObject(i), null);
                        if (track != null) {
                            tracks.add(track);
                        }
                    }
                    if (!tracks.isEmpty()) {
                        return new AmazonMusicAudioPlaylist(albumName, tracks, null, false);
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Amazon Music getAlbum by ID failed for {}: {}", albumId, e.getMessage());
        }

        if (originalUrl != null && !originalUrl.isBlank()) {
            try {
                String fallbackUrl = apiUrl + "/albums?url=" + URLEncoder.encode(originalUrl, StandardCharsets.UTF_8);
                String response = HttpHelper.get(fallbackUrl, Collections.emptyMap(), config.getAmazonMusicProxy());
                JSONObject json = new JSONObject(response);
                JSONObject albumObj = json.optJSONObject("data");
                if (albumObj != null) {
                    String albumName = albumObj.optString("name", "Amazon Music Album");
                    JSONArray songsArr = albumObj.optJSONArray("songs");
                    if (songsArr != null && songsArr.length() > 0) {
                        List<AudioTrack> tracks = new ArrayList<>();
                        for (int i = 0; i < songsArr.length(); i++) {
                            AudioTrack track = parseSong(songsArr.getJSONObject(i), null);
                            if (track != null) {
                                tracks.add(track);
                            }
                        }
                        if (!tracks.isEmpty()) {
                            return new AmazonMusicAudioPlaylist(albumName, tracks, null, false);
                        }
                    }
                }
            } catch (Exception e) {
                log.debug("Amazon Music getAlbum by URL fallback failed for {}: {}", originalUrl, e.getMessage());
            }
        }

        return AudioReference.NO_TRACK;
    }

    private AudioItem getArtist(String artistId, String originalUrl) {
        String apiUrl = getNormalizedApiUrl();
        if (apiUrl == null) {
            log.warn("Amazon Music API URL is not configured. Please set 'plugins.allinone.amazonMusicApiUrl' in application.yml.");
            return AudioReference.NO_TRACK;
        }

        try {
            String url = apiUrl + "/artists/" + URLEncoder.encode(artistId, StandardCharsets.UTF_8);
            String response = HttpHelper.get(url, Collections.emptyMap(), config.getAmazonMusicProxy());
            JSONObject json = new JSONObject(response);

            JSONObject artistObj = json.optJSONObject("data");
            if (artistObj != null) {
                String artistName = artistObj.optString("name", "Amazon Music Artist");
                JSONArray songsArr = artistObj.optJSONArray("topSongs");
                if (songsArr == null) {
                    songsArr = artistObj.optJSONArray("songs");
                }
                if (songsArr != null && songsArr.length() > 0) {
                    List<AudioTrack> tracks = new ArrayList<>();
                    for (int i = 0; i < songsArr.length(); i++) {
                        AudioTrack track = parseSong(songsArr.getJSONObject(i), null);
                        if (track != null) {
                            tracks.add(track);
                        }
                    }
                    if (!tracks.isEmpty()) {
                        return new AmazonMusicAudioPlaylist(artistName + "'s Top Tracks", tracks, null, false);
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Amazon Music getArtist by ID failed for {}: {}", artistId, e.getMessage());
        }

        if (originalUrl != null && !originalUrl.isBlank()) {
            try {
                String fallbackUrl = apiUrl + "/artists?url=" + URLEncoder.encode(originalUrl, StandardCharsets.UTF_8);
                String response = HttpHelper.get(fallbackUrl, Collections.emptyMap(), config.getAmazonMusicProxy());
                JSONObject json = new JSONObject(response);
                JSONObject artistObj = json.optJSONObject("data");
                if (artistObj != null) {
                    String artistName = artistObj.optString("name", "Amazon Music Artist");
                    JSONArray songsArr = artistObj.optJSONArray("topSongs");
                    if (songsArr == null) {
                        songsArr = artistObj.optJSONArray("songs");
                    }
                    if (songsArr != null && songsArr.length() > 0) {
                        List<AudioTrack> tracks = new ArrayList<>();
                        for (int i = 0; i < songsArr.length(); i++) {
                            AudioTrack track = parseSong(songsArr.getJSONObject(i), null);
                            if (track != null) {
                                tracks.add(track);
                            }
                        }
                        if (!tracks.isEmpty()) {
                            return new AmazonMusicAudioPlaylist(artistName + "'s Top Tracks", tracks, null, false);
                        }
                    }
                }
            } catch (Exception e) {
                log.debug("Amazon Music getArtist by URL fallback failed for {}: {}", originalUrl, e.getMessage());
            }
        }

        return AudioReference.NO_TRACK;
    }

    private AudioItem getPlaylist(String playlistId, String originalUrl) {
        String apiUrl = getNormalizedApiUrl();
        if (apiUrl == null) {
            log.warn("Amazon Music API URL is not configured. Please set 'plugins.allinone.amazonMusicApiUrl' in application.yml.");
            return AudioReference.NO_TRACK;
        }

        try {
            String url = apiUrl + "/playlists/" + URLEncoder.encode(playlistId, StandardCharsets.UTF_8);
            String response = HttpHelper.get(url, Collections.emptyMap(), config.getAmazonMusicProxy());
            JSONObject json = new JSONObject(response);

            JSONObject playlistObj = json.optJSONObject("data");
            if (playlistObj != null) {
                String playlistName = playlistObj.optString("name", "Amazon Music Playlist");
                JSONArray songsArr = playlistObj.optJSONArray("songs");
                if (songsArr != null && songsArr.length() > 0) {
                    List<AudioTrack> tracks = new ArrayList<>();
                    for (int i = 0; i < songsArr.length(); i++) {
                        AudioTrack track = parseSong(songsArr.getJSONObject(i), null);
                        if (track != null) {
                            tracks.add(track);
                        }
                    }
                    if (!tracks.isEmpty()) {
                        return new AmazonMusicAudioPlaylist(playlistName, tracks, null, false);
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Amazon Music getPlaylist by ID failed for {}: {}", playlistId, e.getMessage());
        }

        if (originalUrl != null && !originalUrl.isBlank()) {
            try {
                String fallbackUrl = apiUrl + "/playlists?url=" + URLEncoder.encode(originalUrl, StandardCharsets.UTF_8);
                String response = HttpHelper.get(fallbackUrl, Collections.emptyMap(), config.getAmazonMusicProxy());
                JSONObject json = new JSONObject(response);
                JSONObject playlistObj = json.optJSONObject("data");
                if (playlistObj != null) {
                    String playlistName = playlistObj.optString("name", "Amazon Music Playlist");
                    JSONArray songsArr = playlistObj.optJSONArray("songs");
                    if (songsArr != null && songsArr.length() > 0) {
                        List<AudioTrack> tracks = new ArrayList<>();
                        for (int i = 0; i < songsArr.length(); i++) {
                            AudioTrack track = parseSong(songsArr.getJSONObject(i), null);
                            if (track != null) {
                                tracks.add(track);
                            }
                        }
                        if (!tracks.isEmpty()) {
                            return new AmazonMusicAudioPlaylist(playlistName, tracks, null, false);
                        }
                    }
                }
            } catch (Exception e) {
                log.debug("Amazon Music getPlaylist by URL fallback failed for {}: {}", originalUrl, e.getMessage());
            }
        }

        return AudioReference.NO_TRACK;
    }

    private AudioItem getCommunityPlaylist(String userPlaylistId, String originalUrl) {
        String apiUrl = getNormalizedApiUrl();
        if (apiUrl == null) {
            log.warn("Amazon Music API URL is not configured. Please set 'plugins.allinone.amazonMusicApiUrl' in application.yml.");
            return AudioReference.NO_TRACK;
        }

        try {
            String url = apiUrl + "/community-playlists/" + URLEncoder.encode(userPlaylistId, StandardCharsets.UTF_8);
            String response = HttpHelper.get(url, Collections.emptyMap(), config.getAmazonMusicProxy());
            JSONObject json = new JSONObject(response);

            JSONObject playlistObj = json.optJSONObject("data");
            if (playlistObj != null) {
                String playlistName = playlistObj.optString("name", "Amazon Music Community Playlist");
                JSONArray songsArr = playlistObj.optJSONArray("songs");
                if (songsArr != null && songsArr.length() > 0) {
                    List<AudioTrack> tracks = new ArrayList<>();
                    for (int i = 0; i < songsArr.length(); i++) {
                        AudioTrack track = parseSong(songsArr.getJSONObject(i), null);
                        if (track != null) {
                            tracks.add(track);
                        }
                    }
                    if (!tracks.isEmpty()) {
                        return new AmazonMusicAudioPlaylist(playlistName, tracks, null, false);
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Amazon Music getCommunityPlaylist by ID failed for {}: {}", userPlaylistId, e.getMessage());
        }

        if (originalUrl != null && !originalUrl.isBlank()) {
            try {
                String fallbackUrl = apiUrl + "/community-playlists?url=" + URLEncoder.encode(originalUrl, StandardCharsets.UTF_8);
                String response = HttpHelper.get(fallbackUrl, Collections.emptyMap(), config.getAmazonMusicProxy());
                JSONObject json = new JSONObject(response);
                JSONObject playlistObj = json.optJSONObject("data");
                if (playlistObj != null) {
                    String playlistName = playlistObj.optString("name", "Amazon Music Community Playlist");
                    JSONArray songsArr = playlistObj.optJSONArray("songs");
                    if (songsArr != null && songsArr.length() > 0) {
                        List<AudioTrack> tracks = new ArrayList<>();
                        for (int i = 0; i < songsArr.length(); i++) {
                            AudioTrack track = parseSong(songsArr.getJSONObject(i), null);
                            if (track != null) {
                                tracks.add(track);
                            }
                        }
                        if (!tracks.isEmpty()) {
                            return new AmazonMusicAudioPlaylist(playlistName, tracks, null, false);
                        }
                    }
                }
            } catch (Exception e) {
                log.debug("Amazon Music getCommunityPlaylist by URL fallback failed for {}: {}", originalUrl, e.getMessage());
            }
        }

        return AudioReference.NO_TRACK;
    }

    private AudioTrack parseSong(JSONObject song, String fallbackUri) {
        String id = song.optString("id", null);
        if (id == null || id.isBlank()) {
            return null;
        }

        String title = song.optString("title", song.optString("name", "Unknown Title"));

        String author = "Unknown Artist";
        String artistUrl = null;
        Object artistObj = song.opt("artist");
        if (artistObj instanceof JSONObject) {
            JSONObject a = (JSONObject) artistObj;
            author = a.optString("name", "Unknown Artist");
            artistUrl = a.optString("url", null);
        } else if (artistObj instanceof String) {
            author = (String) artistObj;
        }

        String albumName = null;
        String albumUrl = null;
        Object albumObj = song.opt("album");
        if (albumObj instanceof JSONObject) {
            JSONObject al = (JSONObject) albumObj;
            albumName = al.optString("name", null);
            albumUrl = al.optString("url", null);
        } else if (albumObj instanceof String) {
            albumName = (String) albumObj;
        }

        long durationSeconds = song.optLong("duration", 0);
        long durationMs = durationSeconds > 0 ? durationSeconds * 1000 : 0;

        String artworkUrl = song.optString("image", null);
        String isrc = song.optString("isrc", null);

        String uri = song.optString("url", null);
        if (uri == null || uri.isBlank()) {
            uri = fallbackUri;
        }
        if (uri == null || uri.isBlank()) {
            uri = "https://music.amazon.com/tracks/" + id;
        }

        AudioTrackInfo trackInfo = new AudioTrackInfo(
                title,
                author,
                durationMs,
                id,
                false,
                uri,
                artworkUrl,
                isrc
        );

        return new AmazonMusicAudioTrack(
                trackInfo,
                albumName,
                albumUrl,
                artistUrl,
                null,
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
        AmazonMusicAudioTrack amTrack = (AmazonMusicAudioTrack) track;
        DataFormatTools.writeNullableText(output, amTrack.getAlbumName());
        DataFormatTools.writeNullableText(output, amTrack.getAlbumUrl());
        DataFormatTools.writeNullableText(output, amTrack.getArtistUrl());
        DataFormatTools.writeNullableText(output, amTrack.getArtistArtworkUrl());
        DataFormatTools.writeNullableText(output, amTrack.getPreviewUrl());
        output.writeBoolean(amTrack.isPreview());
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

        return new AmazonMusicAudioTrack(
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
        } catch (Exception e) {
            log.error("Failed to close Amazon Music HttpInterfaceManager", e);
        }
    }
}
