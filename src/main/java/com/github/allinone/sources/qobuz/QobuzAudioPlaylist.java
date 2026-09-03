package com.github.allinone.sources.qobuz;

import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

import java.util.List;

public class QobuzAudioPlaylist implements AudioPlaylist {

    private final String name;
    private final List<AudioTrack> tracks;
    private final AudioTrack selectedTrack;
    private final boolean isSearchResult;

    public QobuzAudioPlaylist(String name, List<AudioTrack> tracks, AudioTrack selectedTrack, boolean isSearchResult) {
        this.name = name;
        this.tracks = tracks;
        this.selectedTrack = selectedTrack;
        this.isSearchResult = isSearchResult;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public List<AudioTrack> getTracks() {
        return tracks;
    }

    @Override
    public AudioTrack getSelectedTrack() {
        return selectedTrack;
    }

    @Override
    public boolean isSearchResult() {
        return isSearchResult;
    }
}
