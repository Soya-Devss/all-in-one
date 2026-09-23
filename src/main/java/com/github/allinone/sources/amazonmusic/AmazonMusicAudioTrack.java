package com.github.allinone.sources.amazonmusic;

import com.github.allinone.mirror.MirroringAudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;

public class AmazonMusicAudioTrack extends MirroringAudioTrack {

    private final AmazonMusicAudioSourceManager amazonMusicSourceManager;

    public AmazonMusicAudioTrack(
            AudioTrackInfo trackInfo,
            String albumName,
            String albumUrl,
            String artistUrl,
            String artistArtworkUrl,
            String previewUrl,
            boolean isPreview,
            AmazonMusicAudioSourceManager sourceManager
    ) {
        super(trackInfo, albumName, albumUrl, artistUrl, artistArtworkUrl, previewUrl, isPreview, sourceManager);
        this.amazonMusicSourceManager = sourceManager;
    }

    @Override
    protected AudioTrack makeShallowClone() {
        return new AmazonMusicAudioTrack(
                this.trackInfo,
                this.albumName,
                this.albumUrl,
                this.artistUrl,
                this.artistArtworkUrl,
                this.previewUrl,
                this.isPreview,
                this.amazonMusicSourceManager
        );
    }
}
