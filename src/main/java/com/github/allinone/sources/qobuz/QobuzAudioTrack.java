package com.github.allinone.sources.qobuz;

import com.github.allinone.mirror.MirroringAudioTrack;
import com.sedmelluq.discord.lavaplayer.container.mp3.Mp3AudioTrack;
import com.sedmelluq.discord.lavaplayer.container.mpeg.MpegAudioTrack;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.tools.io.PersistentHttpStream;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.track.playback.LocalAudioTrackExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;

public class QobuzAudioTrack extends MirroringAudioTrack {

    private static final Logger log = LoggerFactory.getLogger(QobuzAudioTrack.class);

    private final QobuzAudioSourceManager qobuzSourceManager;

    public QobuzAudioTrack(
            AudioTrackInfo trackInfo,
            String albumName,
            String albumUrl,
            String artistUrl,
            String artistArtworkUrl,
            String previewUrl,
            boolean isPreview,
            QobuzAudioSourceManager sourceManager
    ) {
        super(trackInfo, albumName, albumUrl, artistUrl, artistArtworkUrl, previewUrl, isPreview, sourceManager);
        this.qobuzSourceManager = sourceManager;
    }

    @Override
    public void process(LocalAudioTrackExecutor executor) throws Exception {
        try {
            String directUrl = this.qobuzSourceManager.getPlaybackStreamUrl(this.getIdentifier());
            if (directUrl != null && !directUrl.isBlank()) {
                URI uri = URI.create(directUrl);
                try (HttpInterface httpInterface = this.qobuzSourceManager.getHttpInterface()) {
                    try (PersistentHttpStream stream = new PersistentHttpStream(httpInterface, uri, this.trackInfo.length)) {
                        if (directUrl.contains(".mp3") || "5".equals(this.qobuzSourceManager.getConfig().getQobuzFormatId())) {
                            processDelegate(new Mp3AudioTrack(this.trackInfo, stream), executor);
                        } else {
                            processDelegate(new MpegAudioTrack(this.trackInfo, stream), executor);
                        }
                        return;
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Direct Qobuz playback failed for {}: {}. Falling back to mirror.", this.trackInfo.title, e.getMessage());
        }

        super.process(executor);
    }

    @Override
    protected AudioTrack makeShallowClone() {
        return new QobuzAudioTrack(
                this.trackInfo,
                this.albumName,
                this.albumUrl,
                this.artistUrl,
                this.artistArtworkUrl,
                this.previewUrl,
                this.isPreview,
                this.qobuzSourceManager
        );
    }
}
