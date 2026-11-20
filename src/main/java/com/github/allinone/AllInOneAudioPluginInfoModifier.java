package com.github.allinone;

import com.github.allinone.sources.qobuz.QobuzAudioPlaylist;
import dev.arbjerg.lavalink.api.AudioPluginInfoModifier;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import kotlinx.serialization.json.JsonElementKt;
import kotlinx.serialization.json.JsonNull;
import kotlinx.serialization.json.JsonObject;
import kotlinx.serialization.json.JsonPrimitive;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Exposes extended playlist metadata (matching NodeLink playlist pluginInfo) to
 * Lavalink /loadtracks responses: { type, identifier, url, artworkUrl, author, totalTracks }.
 */
@Service
public class AllInOneAudioPluginInfoModifier implements AudioPluginInfoModifier {

    @Nullable
    @Override
    public JsonObject modifyAudioPlaylistPluginInfo(AudioPlaylist playlist) {
        if (!(playlist instanceof QobuzAudioPlaylist)) {
            return null;
        }

        QobuzAudioPlaylist qobuzPlaylist = (QobuzAudioPlaylist) playlist;
        if (qobuzPlaylist.getPlaylistType() == null) {
            return null;
        }

        Map<String, JsonPrimitive> fields = new LinkedHashMap<>();
        fields.put("type", JsonElementKt.JsonPrimitive(qobuzPlaylist.getPlaylistType()));
        if (qobuzPlaylist.getIdentifier() != null) {
            fields.put("identifier", JsonElementKt.JsonPrimitive(qobuzPlaylist.getIdentifier()));
        }
        if (qobuzPlaylist.getUrl() != null) {
            fields.put("url", JsonElementKt.JsonPrimitive(qobuzPlaylist.getUrl()));
        }
        fields.put("artworkUrl", qobuzPlaylist.getArtworkUrl() != null
                ? JsonElementKt.JsonPrimitive(qobuzPlaylist.getArtworkUrl())
                : (JsonPrimitive) JsonNull.INSTANCE);
        fields.put("author", qobuzPlaylist.getAuthor() != null
                ? JsonElementKt.JsonPrimitive(qobuzPlaylist.getAuthor())
                : (JsonPrimitive) JsonNull.INSTANCE);
        if (qobuzPlaylist.getTotalTracks() != null) {
            fields.put("totalTracks", JsonElementKt.JsonPrimitive((Number) qobuzPlaylist.getTotalTracks()));
        }

        return new JsonObject(fields);
    }
}
