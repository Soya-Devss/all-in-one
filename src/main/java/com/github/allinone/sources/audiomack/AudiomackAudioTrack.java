package com.github.allinone.sources.audiomack;

import com.github.allinone.mirror.MirroringAudioSourceManager;
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

public class AudiomackAudioTrack extends MirroringAudioTrack {

    private static final Logger log = LoggerFactory.getLogger(AudiomackAudioTrack.class);

    private final AudiomackAudioSourceManager audiomackSourceManager;

    public AudiomackAudioTrack(AudioTrackInfo trackInfo, String albumName, String albumUrl, String artistUrl, String artistArtworkUrl, String previewUrl, boolean isPreview, AudiomackAudioSourceManager sourceManager) {
        super(trackInfo, albumName, albumUrl, artistUrl, artistArtworkUrl, previewUrl, isPreview, sourceManager);
        this.audiomackSourceManager = sourceManager;
    }

    @Override
    public void process(LocalAudioTrackExecutor executor) throws Exception {
        try {
            String streamUrl = this.audiomackSourceManager.getPlaybackStreamUrl(this.getIdentifier(), this.trackInfo.uri);
            if (streamUrl != null && !streamUrl.isBlank()) {
                URI streamUri = URI.create(streamUrl);
                try (HttpInterface httpInterface = this.audiomackSourceManager.getHttpInterface()) {
                    try (PersistentHttpStream stream = new PersistentHttpStream(httpInterface, streamUri, this.trackInfo.length)) {
                        if (streamUrl.contains(".mp3")) {
                            processDelegate(new Mp3AudioTrack(this.trackInfo, stream), executor);
                        } else {
                            processDelegate(new MpegAudioTrack(this.trackInfo, stream), executor);
                        }
                        return;
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Direct Audiomack playback failed for {}: {}. Falling back to mirror.", this.trackInfo.title, e.getMessage());
        }

        super.process(executor);
    }

    @Override
    protected AudioTrack makeShallowClone() {
        return new AudiomackAudioTrack(
                this.trackInfo,
                this.albumName,
                this.albumUrl,
                this.artistUrl,
                this.artistArtworkUrl,
                this.previewUrl,
                this.isPreview,
                this.audiomackSourceManager
        );
    }
}
