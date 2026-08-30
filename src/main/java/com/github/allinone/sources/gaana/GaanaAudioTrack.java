package com.github.allinone.sources.gaana;

import com.github.allinone.mirror.MirroringAudioTrack;
import com.sedmelluq.discord.lavaplayer.container.mpeg.MpegAudioTrack;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.tools.io.PersistentHttpStream;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.track.playback.LocalAudioTrackExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;

public class GaanaAudioTrack extends MirroringAudioTrack {

    private static final Logger log = LoggerFactory.getLogger(GaanaAudioTrack.class);

    private final GaanaAudioSourceManager gaanaSourceManager;

    public GaanaAudioTrack(AudioTrackInfo trackInfo, String albumName, String albumUrl, String artistUrl, String artistArtworkUrl, String previewUrl, boolean isPreview, GaanaAudioSourceManager sourceManager) {
        super(trackInfo, albumName, albumUrl, artistUrl, artistArtworkUrl, previewUrl, isPreview, sourceManager);
        this.gaanaSourceManager = sourceManager;
    }

    @Override
    public void process(LocalAudioTrackExecutor executor) throws Exception {
        try {
            String directUrl = this.gaanaSourceManager.getPlaybackStreamUrl(this.getIdentifier());
            if (directUrl != null && !directUrl.isBlank()) {
                URI uri = URI.create(directUrl);
                try (HttpInterface httpInterface = this.gaanaSourceManager.getHttpInterface()) {
                    try (PersistentHttpStream stream = new PersistentHttpStream(httpInterface, uri, this.trackInfo.length)) {
                        processDelegate(new MpegAudioTrack(this.trackInfo, stream), executor);
                        return;
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Direct Gaana playback failed for {}: {}. Falling back to mirror.", this.trackInfo.title, e.getMessage());
        }

        super.process(executor);
    }

    @Override
    protected AudioTrack makeShallowClone() {
        return new GaanaAudioTrack(
                this.trackInfo,
                this.albumName,
                this.albumUrl,
                this.artistUrl,
                this.artistArtworkUrl,
                this.previewUrl,
                this.isPreview,
                this.gaanaSourceManager
        );
    }
}
