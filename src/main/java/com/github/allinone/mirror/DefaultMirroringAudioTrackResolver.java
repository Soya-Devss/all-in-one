package com.github.allinone.mirror;

import com.sedmelluq.discord.lavaplayer.track.AudioItem;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultMirroringAudioTrackResolver implements MirroringAudioTrackResolver {

    private static final Logger log = LoggerFactory.getLogger(DefaultMirroringAudioTrackResolver.class);

    private String[] providers = {
            "ytmsearch:\"" + MirroringAudioSourceManager.ISRC_PATTERN + "\"",
            "ytmsearch:" + MirroringAudioSourceManager.QUERY_PATTERN,
            "ytsearch:\"" + MirroringAudioSourceManager.ISRC_PATTERN + "\"",
            "ytsearch:" + MirroringAudioSourceManager.QUERY_PATTERN,
            "scsearch:" + MirroringAudioSourceManager.QUERY_PATTERN
    };

    public DefaultMirroringAudioTrackResolver(String[] customProviders) {
        if (customProviders != null && customProviders.length > 0) {
            this.providers = customProviders;
        }
    }

    @Override
    public AudioItem apply(MirroringAudioTrack mirroringAudioTrack) {
        for (String providerTemplate : providers) {
            String query = providerTemplate;

            if (query.contains(MirroringAudioSourceManager.ISRC_PATTERN)) {
                String isrc = mirroringAudioTrack.getInfo().isrc;
                if (isrc != null && !isrc.isBlank()) {
                    query = query.replace(MirroringAudioSourceManager.ISRC_PATTERN, isrc.replace("-", ""));
                } else {
                    continue;
                }
            }

            query = query.replace(MirroringAudioSourceManager.QUERY_PATTERN, getSearchQuery(mirroringAudioTrack));

            try {
                AudioItem item = mirroringAudioTrack.loadItem(query);
                if (item instanceof AudioPlaylist) {
                    AudioPlaylist playlist = (AudioPlaylist) item;
                    if (!playlist.getTracks().isEmpty()) {
                        return playlist;
                    }
                } else if (item != AudioReference.NO_TRACK && item != null) {
                    return item;
                }
            } catch (Exception e) {
                log.warn("Failed to load mirror query: {}", query, e);
            }
        }
        return AudioReference.NO_TRACK;
    }

    private String getSearchQuery(MirroringAudioTrack mirroringAudioTrack) {
        String title = mirroringAudioTrack.getInfo().title;
        String author = mirroringAudioTrack.getInfo().author;
        if (author != null && !author.isBlank() && !author.equalsIgnoreCase("unknown")) {
            return title + " " + author;
        }
        return title;
    }
}
