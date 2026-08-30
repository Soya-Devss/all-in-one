package com.github.allinone.sources.gaana;

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

public class GaanaAudioSourceManager implements MirroringAudioSourceManager, AudioSearchManager {

    private static final Logger log = LoggerFactory.getLogger(GaanaAudioSourceManager.class);

    public static final String SEARCH_PREFIX_GN = "gnsearch:";
    public static final String SEARCH_PREFIX_GAANA = "gaanasearch:";

    private static final Pattern URL_PATTERN = Pattern.compile("https?://(?:www\\.)?gaana\\.com/(song|album|playlist|artist)/([\\w-]+)");

    private final AllInOneConfig config;
    private final MirroringAudioTrackResolver resolver;
    private final AudioPlayerManager audioPlayerManager;
    private final HttpInterfaceManager httpInterfaceManager;

    public GaanaAudioSourceManager(AllInOneConfig config, AudioPlayerManager audioPlayerManager) {
        this.config = config;
        this.audioPlayerManager = audioPlayerManager;
        this.resolver = new DefaultMirroringAudioTrackResolver(config.getProviders());
        this.httpInterfaceManager = HttpClientTools.createDefaultThreadLocalManager();
    }

    @Override
    @NotNull
    public String getSourceName() {
        return "gaana";
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
            if (identifier.startsWith(SEARCH_PREFIX_GN)) {
                return search(identifier.substring(SEARCH_PREFIX_GN.length()).trim());
            }
            if (identifier.startsWith(SEARCH_PREFIX_GAANA)) {
                return search(identifier.substring(SEARCH_PREFIX_GAANA.length()).trim());
            }

            Matcher matcher = URL_PATTERN.matcher(identifier);
            if (matcher.find()) {
                String type = matcher.group(1);
                String seokey = matcher.group(2);
                switch (type) {
                    case "song":
                        return getSong(seokey, identifier);
                    case "album":
                        return getAlbum(seokey);
                    case "playlist":
                        return getPlaylist(seokey);
                    case "artist":
                        return getArtist(seokey);
                    default:
                        break;
                }
            }
        } catch (Exception e) {
            log.error("Error loading Gaana item: {}", identifier, e);
        }

        return null;
    }

    @Override
    @Nullable
    public AudioSearchResult loadSearch(@NotNull String query, @NotNull Set<AudioSearchResult.Type> types) {
        try {
            List<AudioTrack> tracks = new ArrayList<>();
            List<AudioPlaylist> albums = new ArrayList<>();
            List<AudioPlaylist> artists = new ArrayList<>();
            List<AudioPlaylist> playlists = new ArrayList<>();

            if (types.contains(AudioSearchResult.Type.TRACK)) {
                AudioItem item = search(query);
                if (item instanceof AudioPlaylist) {
                    tracks.addAll(((AudioPlaylist) item).getTracks());
                }
            }

            if (types.contains(AudioSearchResult.Type.ALBUM)) {
                try {
                    String url = config.getGaanaApiUrl() + "/search/albums?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8) + "&limit=10";
                    String response = HttpHelper.get(url, Collections.emptyMap(), config.getGaanaProxy());
                    JSONObject json = new JSONObject(response);
                    JSONArray arr = json.optJSONArray("data");
                    if (arr != null) {
                        for (int i = 0; i < arr.length(); i++) {
                            JSONObject a = arr.getJSONObject(i);
                            String title = a.optString("title", "Album");
                            albums.add(new GaanaAudioPlaylist(title, Collections.emptyList(), null, false));
                        }
                    }
                } catch (Exception ignored) {
                }
            }

            if (types.contains(AudioSearchResult.Type.PLAYLIST)) {
                try {
                    String url = config.getGaanaApiUrl() + "/search/playlists?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8) + "&limit=10";
                    String response = HttpHelper.get(url, Collections.emptyMap(), config.getGaanaProxy());
                    JSONObject json = new JSONObject(response);
                    JSONArray arr = json.optJSONArray("data");
                    if (arr != null) {
                        for (int i = 0; i < arr.length(); i++) {
                            JSONObject p = arr.getJSONObject(i);
                            String title = p.optString("title", "Playlist");
                            playlists.add(new GaanaAudioPlaylist(title, Collections.emptyList(), null, false));
                        }
                    }
                } catch (Exception ignored) {
                }
            }

            if (types.contains(AudioSearchResult.Type.ARTIST)) {
                try {
                    String url = config.getGaanaApiUrl() + "/search/artists?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8) + "&limit=10";
                    String response = HttpHelper.get(url, Collections.emptyMap(), config.getGaanaProxy());
                    JSONObject json = new JSONObject(response);
                    JSONArray arr = json.optJSONArray("data");
                    if (arr != null) {
                        for (int i = 0; i < arr.length(); i++) {
                            JSONObject art = arr.getJSONObject(i);
                            String name = art.optString("name", "Artist");
                            artists.add(new GaanaAudioPlaylist(name, Collections.emptyList(), null, false));
                        }
                    }
                } catch (Exception ignored) {
                }
            }

            if (tracks.isEmpty() && albums.isEmpty() && artists.isEmpty() && playlists.isEmpty()) {
                return null;
            }

            return new BasicAudioSearchResult(tracks, albums, artists, playlists, Collections.emptyList());
        } catch (Exception e) {
            log.error("Error performing LavaSearch for Gaana: {}", query, e);
            return null;
        }
    }

    private AudioItem search(String query) {
        try {
            String url = config.getGaanaApiUrl() + "/search/songs?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8) + "&limit=15";
            String response = HttpHelper.get(url, Collections.emptyMap(), config.getGaanaProxy());
            JSONObject json = new JSONObject(response);

            JSONArray songs = json.optJSONArray("data");
            if (songs == null) {
                songs = json.optJSONArray("songs");
            }
            if (songs == null && json.has("data") && json.getJSONObject("data").has("songs")) {
                songs = json.getJSONObject("data").getJSONArray("songs");
            }

            if (songs == null || songs.isEmpty()) {
                return AudioReference.NO_TRACK;
            }

            List<AudioTrack> tracks = new ArrayList<>();
            for (int i = 0; i < songs.length(); i++) {
                AudioTrack track = parseSong(songs.getJSONObject(i), null);
                if (track != null) {
                    tracks.add(track);
                }
            }

            if (tracks.isEmpty()) {
                return AudioReference.NO_TRACK;
            }

            return new GaanaAudioPlaylist("Gaana Search: " + query, tracks, null, true);
        } catch (Exception e) {
            log.error("Gaana search error for query {}: {}", query, e.getMessage());
            return AudioReference.NO_TRACK;
        }
    }

    private AudioItem getSong(String seokey, String originalUrl) {
        try {
            String url = config.getGaanaApiUrl() + "/songs?seokey=" + URLEncoder.encode(seokey, StandardCharsets.UTF_8);
            String response = HttpHelper.get(url, Collections.emptyMap(), config.getGaanaProxy());
            JSONObject json = new JSONObject(response);
            JSONObject song = json.has("data") ? json.optJSONObject("data") : json;
            if (song != null) {
                AudioTrack track = parseSong(song, originalUrl);
                if (track != null) {
                    return track;
                }
            }
        } catch (Exception e) {
            log.error("Gaana getSong error for {}: {}", seokey, e.getMessage());
        }

        return AudioReference.NO_TRACK;
    }

    private AudioItem getAlbum(String seokey) {
        try {
            String url = config.getGaanaApiUrl() + "/albums?seokey=" + URLEncoder.encode(seokey, StandardCharsets.UTF_8);
            String response = HttpHelper.get(url, Collections.emptyMap(), config.getGaanaProxy());
            JSONObject json = new JSONObject(response);
            JSONObject data = json.has("data") ? json.optJSONObject("data") : json;
            if (data != null) {
                String title = data.optString("title", "Gaana Album");
                JSONArray tracksArr = data.optJSONArray("tracks");
                if (tracksArr == null) {
                    tracksArr = data.optJSONArray("songs");
                }
                if (tracksArr != null) {
                    List<AudioTrack> tracks = new ArrayList<>();
                    for (int i = 0; i < tracksArr.length(); i++) {
                        AudioTrack track = parseSong(tracksArr.getJSONObject(i), null);
                        if (track != null) {
                            tracks.add(track);
                        }
                    }
                    if (!tracks.isEmpty()) {
                        return new GaanaAudioPlaylist(title, tracks, null, false);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Gaana getAlbum error for {}: {}", seokey, e.getMessage());
        }

        return AudioReference.NO_TRACK;
    }

    private AudioItem getPlaylist(String seokey) {
        try {
            String url = config.getGaanaApiUrl() + "/playlists?seokey=" + URLEncoder.encode(seokey, StandardCharsets.UTF_8);
            String response = HttpHelper.get(url, Collections.emptyMap(), config.getGaanaProxy());
            JSONObject json = new JSONObject(response);

            JSONObject playlistObj = json.optJSONObject("playlist");
            if (playlistObj == null) {
                playlistObj = json.has("data") ? json.optJSONObject("data") : json;
            }

            if (playlistObj != null) {
                String title = playlistObj.optString("title", "Gaana Playlist");
                JSONArray tracksArr = playlistObj.optJSONArray("tracks");
                if (tracksArr == null) {
                    tracksArr = playlistObj.optJSONArray("songs");
                }
                if (tracksArr != null) {
                    List<AudioTrack> tracks = new ArrayList<>();
                    for (int i = 0; i < tracksArr.length(); i++) {
                        AudioTrack track = parseSong(tracksArr.getJSONObject(i), null);
                        if (track != null) {
                            tracks.add(track);
                        }
                    }
                    if (!tracks.isEmpty()) {
                        return new GaanaAudioPlaylist(title, tracks, null, false);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Gaana getPlaylist error for {}: {}", seokey, e.getMessage());
        }

        return AudioReference.NO_TRACK;
    }

    private AudioItem getArtist(String seokey) {
        try {
            String url = config.getGaanaApiUrl() + "/artists?seokey=" + URLEncoder.encode(seokey, StandardCharsets.UTF_8);
            String response = HttpHelper.get(url, Collections.emptyMap(), config.getGaanaProxy());
            JSONObject json = new JSONObject(response);

            JSONObject artistObj = json.optJSONObject("artist");
            if (artistObj == null) {
                artistObj = json.has("data") ? json.optJSONObject("data") : json;
            }

            if (artistObj != null) {
                String name = artistObj.optString("name", "Gaana Artist");
                JSONArray tracksArr = artistObj.optJSONArray("top_tracks");
                if (tracksArr == null) {
                    tracksArr = artistObj.optJSONArray("top_songs");
                }
                if (tracksArr != null) {
                    List<AudioTrack> tracks = new ArrayList<>();
                    for (int i = 0; i < tracksArr.length(); i++) {
                        AudioTrack track = parseSong(tracksArr.getJSONObject(i), null);
                        if (track != null) {
                            tracks.add(track);
                        }
                    }
                    if (!tracks.isEmpty()) {
                        return new GaanaAudioPlaylist(name + "'s Top Tracks", tracks, null, false);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Gaana getArtist error for {}: {}", seokey, e.getMessage());
        }

        return AudioReference.NO_TRACK;
    }

    private AudioTrack parseSong(JSONObject song, String fallbackUri) {
        String id = song.optString("track_id", song.optString("id", null));
        String seokey = song.optString("seokey", "");
        String identifier = id != null && !id.isBlank() ? id : seokey;
        if (identifier.isBlank()) {
            return null;
        }

        String title = song.optString("title", song.optString("name", "Unknown Title"));
        String author = song.optString("artists", song.optString("artist", "Unknown Artist"));
        long duration = song.optLong("duration", 0) * 1000;
        String artworkUrl = song.optString("artworkUrl", song.optString("artwork", null));
        String isrc = song.optString("isrc", null);

        String uri = fallbackUri;
        if (uri == null || uri.isBlank()) {
            uri = song.optString("song_url", "https://gaana.com/song/" + seokey);
        }

        AudioTrackInfo trackInfo = new AudioTrackInfo(
                title,
                author,
                duration,
                identifier,
                false,
                uri,
                artworkUrl,
                isrc
        );

        return new GaanaAudioTrack(
                trackInfo,
                song.optString("album", null),
                song.optString("album_url", null),
                null,
                artworkUrl,
                null,
                false,
                this
        );
    }

    public String getPlaybackStreamUrl(String trackId) {
        try {
            if (!trackId.matches("\\d+")) {
                return null;
            }

            String url = config.getGaanaApiUrl() + "/stream/" + trackId + "?quality=high";
            String response = HttpHelper.get(url, Collections.emptyMap(), config.getGaanaProxy());
            JSONObject json = new JSONObject(response);

            String hlsUrl = json.optString("hlsUrl", null);
            if (hlsUrl != null && !hlsUrl.isBlank()) {
                return hlsUrl;
            }

            String streamUrl = json.optString("url", null);
            if (streamUrl != null && !streamUrl.isBlank()) {
                return streamUrl;
            }
        } catch (Exception e) {
            log.debug("Stream URL fetch failed for Gaana track {}: {}", trackId, e.getMessage());
        }
        return null;
    }

    @Override
    public boolean isTrackEncodable(AudioTrack track) {
        return true;
    }

    @Override
    public void encodeTrack(AudioTrack track, DataOutput output) throws IOException {
        GaanaAudioTrack gaanaTrack = (GaanaAudioTrack) track;
        DataFormatTools.writeNullableText(output, gaanaTrack.getAlbumName());
        DataFormatTools.writeNullableText(output, gaanaTrack.getAlbumUrl());
        DataFormatTools.writeNullableText(output, gaanaTrack.getArtistUrl());
        DataFormatTools.writeNullableText(output, gaanaTrack.getArtistArtworkUrl());
        DataFormatTools.writeNullableText(output, gaanaTrack.getPreviewUrl());
        output.writeBoolean(gaanaTrack.isPreview());
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

        return new GaanaAudioTrack(
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
