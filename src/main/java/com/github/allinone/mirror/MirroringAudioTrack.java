package com.github.allinone.mirror;

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioItem;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioReference;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.track.InternalAudioTrack;
import com.sedmelluq.discord.lavaplayer.track.playback.LocalAudioTrackExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;

public abstract class MirroringAudioTrack extends ExtendedAudioTrack {

    private static final Logger log = LoggerFactory.getLogger(MirroringAudioTrack.class);

    protected final MirroringAudioSourceManager sourceManager;

    public MirroringAudioTrack(AudioTrackInfo trackInfo, String albumName, String albumUrl, String artistUrl, String artistArtworkUrl, String previewUrl, boolean isPreview, MirroringAudioSourceManager sourceManager) {
        super(trackInfo, albumName, albumUrl, artistUrl, artistArtworkUrl, previewUrl, isPreview);
        this.sourceManager = sourceManager;
    }

    @Override
    public void process(LocalAudioTrackExecutor executor) throws Exception {
        AudioItem track = this.sourceManager.getResolver().apply(this);

        if (track instanceof AudioPlaylist) {
            AudioPlaylist playlist = (AudioPlaylist) track;
            if (playlist.getTracks().isEmpty()) {
                throw new FriendlyException("No mirror found for track", FriendlyException.Severity.COMMON, null);
            }
            track = playlist.getTracks().get(0);
        }

        if (track instanceof InternalAudioTrack) {
            InternalAudioTrack internalTrack = (InternalAudioTrack) track;
            internalTrack.setUserData(this.getUserData());
            processDelegate(internalTrack, executor);
            return;
        }

        throw new FriendlyException("No mirror found for track: " + trackInfo.title, FriendlyException.Severity.COMMON, null);
    }

    @Override
    public AudioSourceManager getSourceManager() {
        return this.sourceManager;
    }

    public AudioItem loadItem(String query) {
        CompletableFuture<AudioItem> future = new CompletableFuture<>();
        this.sourceManager.getAudioPlayerManager().loadItem(query, new AudioLoadResultHandler() {
            @Override
            public void trackLoaded(AudioTrack track) {
                future.complete(track);
            }

            @Override
            public void playlistLoaded(AudioPlaylist playlist) {
                future.complete(playlist);
            }

            @Override
            public void noMatches() {
                future.complete(AudioReference.NO_TRACK);
            }

            @Override
            public void loadFailed(FriendlyException exception) {
                future.completeExceptionally(exception);
            }
        });
        return future.join();
    }
}
