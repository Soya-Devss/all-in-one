package com.github.allinone;

import com.github.allinone.sources.audiomack.AudiomackAudioSourceManager;
import com.github.allinone.sources.gaana.GaanaAudioSourceManager;
import com.github.allinone.sources.pandora.PandoraAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import dev.arbjerg.lavalink.api.AudioPlayerManagerConfiguration;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AllInOnePlugin implements AudioPlayerManagerConfiguration {

    private static final Logger log = LoggerFactory.getLogger(AllInOnePlugin.class);

    private final AllInOneConfig config;

    public AllInOnePlugin(AllInOneConfig config) {
        this.config = config;
        log.info("Initializing All-In-One Lavalink Plugin...");

        if (config.isAutoUpdate()) {
            Thread updateThread = new Thread(() -> UpdateService.checkAndUpdate(config), "AllInOne-AutoUpdate");
            updateThread.setDaemon(true);
            updateThread.start();
        }
    }

    @NotNull
    @Override
    public AudioPlayerManager configure(@NotNull AudioPlayerManager manager) {
        if (!config.isEnabled()) {
            log.info("All-In-One plugin is disabled in configuration.");
            return manager;
        }

        if (config.isAudiomack()) {
            log.info("Registering Audiomack audio source manager...");
            manager.registerSourceManager(new AudiomackAudioSourceManager(config.getProviders(), manager));
        }

        if (config.isGaana()) {
            log.info("Registering Gaana audio source manager...");
            manager.registerSourceManager(new GaanaAudioSourceManager(config.getProviders(), manager));
        }

        if (config.isPandora()) {
            log.info("Registering Pandora audio source manager...");
            manager.registerSourceManager(new PandoraAudioSourceManager(config.getProviders(), manager));
        }

        return manager;
    }
}
