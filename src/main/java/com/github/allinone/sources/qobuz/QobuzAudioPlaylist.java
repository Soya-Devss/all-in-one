package com.github.allinone.sources.qobuz;

import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

import java.util.List;

public class QobuzAudioPlaylist implements AudioPlaylist {

    private final String name;
    private final List<AudioTrack> tracks;
    private final AudioTrack selectedTrack;
    private final boolean isSearchResult;

    // Extended metadata (mirrors NodeLink playlist pluginInfo).
    private final String playlistType;
    private final String identifier;
    private final String url;
    private final String artworkUrl;
    private final String author;
    private final Long totalTracks;

    public QobuzAudioPlaylist(String name, List<AudioTrack> tracks, AudioTrack selectedTrack, boolean isSearchResult) {
        this(name, tracks, selectedTrack, isSearchResult, null, null, null, null, null, null);
    }

    public QobuzAudioPlaylist(String name, List<AudioTrack> tracks, AudioTrack selectedTrack, boolean isSearchResult,
                              String playlistType, String identifier, String url, String artworkUrl, String author,
                              Long totalTracks) {
        this.name = name;
        this.tracks = tracks;
        this.selectedTrack = selectedTrack;
        this.isSearchResult = isSearchResult;
        this.playlistType = playlistType;
        this.identifier = identifier;
        this.url = url;
        this.artworkUrl = artworkUrl;
        this.author = author;
        this.totalTracks = totalTracks;
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

    public String getPlaylistType() {
        return playlistType;
    }

    public String getIdentifier() {
        return identifier;
    }

    public String getUrl() {
        return url;
    }

    public String getArtworkUrl() {
        return artworkUrl;
    }

    public String getAuthor() {
        return author;
    }

    public Long getTotalTracks() {
        return totalTracks;
    }
}
