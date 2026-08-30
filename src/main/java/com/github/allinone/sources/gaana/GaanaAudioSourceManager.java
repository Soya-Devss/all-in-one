package com.github.allinone.sources.gaana;

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
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GaanaAudioSourceManager implements MirroringAudioSourceManager {

    private static final Logger log = LoggerFactory.getLogger(GaanaAudioSourceManager.class);

    private static final String API_URL = "https://gaana.com/apiv2";
    private static final String STREAM_URL_API = "https://gaana.com/api/stream-url";
    private static final byte[] CRYPTO_KEY = "gy1t#b@jl(b$wtme".getBytes(StandardCharsets.UTF_8);
    private static final String HLS_BASE_URL = "https://vodhlsgaana-ebw.akamaized.net/";
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    public static final String SEARCH_PREFIX_GN = "gnsearch:";
    public static final String SEARCH_PREFIX_GAANA = "gaanasearch:";

    private static final Pattern URL_PATTERN = Pattern.compile("https?://(?:www\\.)?gaana\\.com/(song|album|playlist|artist)/([\\w-]+)");

    private final MirroringAudioTrackResolver resolver;
    private final AudioPlayerManager audioPlayerManager;
    private final HttpInterfaceManager httpInterfaceManager;

    public GaanaAudioSourceManager(String[] customProviders, AudioPlayerManager audioPlayerManager) {
        this.audioPlayerManager = audioPlayerManager;
        this.resolver = new DefaultMirroringAudioTrackResolver(customProviders);
        this.httpInterfaceManager = HttpClientTools.createDefaultThreadLocalManager();
    }

    @Override
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

    private Map<String, String> getHeaders(String refererSuffix) {
        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", USER_AGENT);
        headers.put("Accept", "application/json, text/plain, */*");
        headers.put("Origin", "https://gaana.com");
        headers.put("Referer", "https://gaana.com/" + (refererSuffix != null ? refererSuffix : ""));
        return headers;
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

    private AudioItem search(String query) throws Exception {
        String url = API_URL + "?country=IN&page=0&type=search&keyword=" + URLEncoder.encode(query, StandardCharsets.UTF_8) + "&secType=track";
        String response = HttpHelper.post(url, "", getHeaders("search/" + URLEncoder.encode(query, StandardCharsets.UTF_8)));
        JSONObject json = new JSONObject(response);

        List<AudioTrack> tracks = new ArrayList<>();
        JSONArray groups = json.optJSONArray("gr");
        if (groups != null) {
            for (int i = 0; i < groups.length(); i++) {
                JSONObject group = groups.getJSONObject(i);
                if ("Track".equalsIgnoreCase(group.optString("ty"))) {
                    JSONArray items = group.optJSONArray("gd");
                    if (items != null) {
                        for (int j = 0; j < items.length(); j++) {
                            AudioTrack track = parseTrack(items.getJSONObject(j), null);
                            if (track != null) {
                                tracks.add(track);
                            }
                        }
                    }
                    break;
                }
            }
        }

        if (tracks.isEmpty()) {
            return AudioReference.NO_TRACK;
        }

        return new GaanaAudioPlaylist("Gaana Search: " + query, tracks, null, true);
    }

    private AudioItem getSong(String seokey, String originalUrl) throws Exception {
        String url = API_URL + "?type=songDetail&seokey=" + URLEncoder.encode(seokey, StandardCharsets.UTF_8);
        String response = HttpHelper.post(url, "", getHeaders("song/" + URLEncoder.encode(seokey, StandardCharsets.UTF_8)));
        JSONObject json = new JSONObject(response);

        JSONArray tracksArr = json.optJSONArray("tracks");
        if (tracksArr != null && !tracksArr.isEmpty()) {
            AudioTrack track = parseTrack(tracksArr.getJSONObject(0), originalUrl);
            if (track != null) {
                return track;
            }
        }

        return AudioReference.NO_TRACK;
    }

    private AudioItem getAlbum(String seokey) throws Exception {
        String url = API_URL + "?type=albumDetail&seokey=" + URLEncoder.encode(seokey, StandardCharsets.UTF_8);
        String response = HttpHelper.post(url, "", getHeaders("album/" + URLEncoder.encode(seokey, StandardCharsets.UTF_8)));
        JSONObject json = new JSONObject(response);

        JSONObject albumObj = json.optJSONObject("album");
        String title = albumObj != null ? albumObj.optString("title", "Unknown Album") : "Gaana Album";
        List<AudioTrack> tracks = new ArrayList<>();

        JSONArray tracksArr = json.optJSONArray("tracks");
        if (tracksArr != null) {
            for (int i = 0; i < tracksArr.length(); i++) {
                AudioTrack track = parseTrack(tracksArr.getJSONObject(i), null);
                if (track != null) {
                    tracks.add(track);
                }
            }
        }

        return new GaanaAudioPlaylist(title, tracks, null, false);
    }

    private AudioItem getPlaylist(String seokey) throws Exception {
        String url = API_URL + "?type=playlistDetail&seokey=" + URLEncoder.encode(seokey, StandardCharsets.UTF_8);
        String response = HttpHelper.post(url, "", getHeaders("playlist/" + URLEncoder.encode(seokey, StandardCharsets.UTF_8)));
        JSONObject json = new JSONObject(response);

        JSONObject playlistObj = json.optJSONObject("playlist");
        String title = playlistObj != null ? playlistObj.optString("title", "Unknown Playlist") : "Gaana Playlist";
        List<AudioTrack> tracks = new ArrayList<>();

        JSONArray tracksArr = json.optJSONArray("tracks");
        if (tracksArr != null) {
            for (int i = 0; i < tracksArr.length(); i++) {
                AudioTrack track = parseTrack(tracksArr.getJSONObject(i), null);
                if (track != null) {
                    tracks.add(track);
                }
            }
        }

        return new GaanaAudioPlaylist(title, tracks, null, false);
    }

    private AudioItem getArtist(String seokey) throws Exception {
        String url = API_URL + "?type=artistDetail&seokey=" + URLEncoder.encode(seokey, StandardCharsets.UTF_8);
        String response = HttpHelper.post(url, "", getHeaders("artist/" + URLEncoder.encode(seokey, StandardCharsets.UTF_8)));
        JSONObject json = new JSONObject(response);

        JSONArray artistsArr = json.optJSONArray("artist");
        if (artistsArr == null || artistsArr.isEmpty()) {
            return AudioReference.NO_TRACK;
        }

        JSONObject artistObj = artistsArr.getJSONObject(0);
        String artistId = artistObj.optString("artist_id", null);
        String artistName = artistObj.optString("name", "Unknown Artist");
        if (artistId == null) {
            return AudioReference.NO_TRACK;
        }

        String tracksUrl = API_URL + "?type=artistTrackList&id=" + artistId + "&language=&order=0&page=0&sortBy=popularity";
        String tracksResponse = HttpHelper.post(tracksUrl, "", getHeaders("artist/" + URLEncoder.encode(seokey, StandardCharsets.UTF_8)));
        JSONObject tracksJson = new JSONObject(tracksResponse);

        List<AudioTrack> tracks = new ArrayList<>();
        JSONArray tracksArr = tracksJson.optJSONArray("tracks");
        if (tracksArr != null) {
            for (int i = 0; i < tracksArr.length(); i++) {
                AudioTrack track = parseTrack(tracksArr.getJSONObject(i), null);
                if (track != null) {
                    tracks.add(track);
                }
            }
        }

        return new GaanaAudioPlaylist(artistName + "'s Top Tracks", tracks, null, false);
    }

    private AudioTrack parseTrack(JSONObject item, String fallbackUri) {
        String id = item.optString("track_id", item.optString("id", null));
        String title = item.optString("track_title", item.optString("ti", item.optString("name", null)));
        if (title == null) {
            return null;
        }

        String seokey = item.optString("seokey", item.optString("seo", ""));
        String identifier = id != null && !id.isBlank() ? id : seokey;
        if (identifier.isBlank()) {
            return null;
        }

        String author = "Unknown Artist";
        if (item.has("artist")) {
            Object artistObj = item.get("artist");
            if (artistObj instanceof JSONArray) {
                JSONArray arr = (JSONArray) artistObj;
                List<String> names = new ArrayList<>();
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject a = arr.optJSONObject(i);
                    if (a != null && a.has("name")) {
                        names.add(a.getString("name"));
                    }
                }
                if (!names.isEmpty()) {
                    author = String.join(", ", names);
                }
            } else if (artistObj instanceof JSONObject) {
                author = ((JSONObject) artistObj).optString("name", "Unknown Artist");
            }
        } else if (item.has("sti")) {
            author = item.optString("sti", "Unknown Artist");
        }

        long duration = item.optLong("duration", 0) * 1000;
        String artworkUrl = item.optString("artwork_large", item.optString("atw", item.optString("aw", null)));
        String isrc = item.optString("isrc", null);

        String uri = fallbackUri;
        if (uri == null || uri.isBlank()) {
            uri = !seokey.isBlank() ? "https://gaana.com/song/" + seokey : "https://gaana.com/song/" + identifier;
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
                item.optString("album_title", null),
                null,
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

            Map<String, String> headers = getHeaders(null);
            headers.put("Content-Type", "application/x-www-form-urlencoded");

            String body = "quality=high&track_id=" + trackId + "&stream_format=mp4";
            String response = HttpHelper.post(STREAM_URL_API, body, headers);
            JSONObject json = new JSONObject(response);

            if (!"success".equalsIgnoreCase(json.optString("api_status"))) {
                return null;
            }

            JSONObject dataNode = json.optJSONObject("data");
            if (dataNode == null) {
                return null;
            }

            String streamPath = dataNode.optString("stream_path", null);
            if (streamPath == null || streamPath.length() < 17) {
                return null;
            }

            return decryptStreamPath(streamPath);
        } catch (Exception e) {
            log.debug("Direct stream fetch failed for Gaana track {}: {}", trackId, e.getMessage());
        }
        return null;
    }

    private String decryptStreamPath(String encryptedData) {
        try {
            int offset = Character.getNumericValue(encryptedData.charAt(0));
            if (offset < 0 || offset + 16 > encryptedData.length()) {
                return null;
            }

            byte[] iv = encryptedData.substring(offset, offset + 16).getBytes(StandardCharsets.UTF_8);
            String ciphertextB64 = encryptedData.substring(offset + 16);
            byte[] ciphertext = Base64.getDecoder().decode(ciphertextB64 + "==");

            Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(CRYPTO_KEY, "AES"), new IvParameterSpec(iv));
            byte[] decrypted = cipher.doFinal(ciphertext);

            String rawText = new String(decrypted, StandardCharsets.UTF_8).replace("\0", "").trim();
            StringBuilder clean = new StringBuilder();
            for (char c : rawText.toCharArray()) {
                if (c >= 32 && c <= 126) {
                    clean.append(c);
                }
            }
            String path = clean.toString();

            if (path.contains("/hls/")) {
                int start = path.indexOf("hls/");
                int end = path.lastIndexOf(".m3u8");
                if (end > start) {
                    return HLS_BASE_URL + path.substring(start, end + 5);
                }
            } else if (path.startsWith("http")) {
                return path;
            }
        } catch (Exception e) {
            log.debug("Gaana stream path decryption failed: {}", e.getMessage());
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
