package com.github.allinone.mirror;

import com.sedmelluq.discord.lavaplayer.track.AudioItem;

public interface MirroringAudioTrackResolver {
    AudioItem apply(MirroringAudioTrack mirroringAudioTrack);
}
