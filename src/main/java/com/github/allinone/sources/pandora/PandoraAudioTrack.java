package com.github.allinone.sources.pandora;

import com.github.allinone.mirror.MirroringAudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;

public class PandoraAudioTrack extends MirroringAudioTrack {

    private final PandoraAudioSourceManager pandoraSourceManager;

    public PandoraAudioTrack(AudioTrackInfo trackInfo, String albumName, String albumUrl, String artistUrl, String artistArtworkUrl, String previewUrl, boolean isPreview, PandoraAudioSourceManager sourceManager) {
        super(trackInfo, albumName, albumUrl, artistUrl, artistArtworkUrl, previewUrl, isPreview, sourceManager);
        this.pandoraSourceManager = sourceManager;
    }

    @Override
    protected AudioTrack makeShallowClone() {
        return new PandoraAudioTrack(
                this.trackInfo,
                this.albumName,
                this.albumUrl,
                this.artistUrl,
                this.artistArtworkUrl,
                this.previewUrl,
                this.isPreview,
                this.pandoraSourceManager
        );
    }
}
