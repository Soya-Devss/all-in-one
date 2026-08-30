package com.github.allinone.mirror;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;

public interface MirroringAudioSourceManager extends AudioSourceManager {

    String ISRC_PATTERN = "%ISRC%";
    String QUERY_PATTERN = "%QUERY%";

    MirroringAudioTrackResolver getResolver();

    AudioPlayerManager getAudioPlayerManager();
}
