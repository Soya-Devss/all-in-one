package com.github.allinone;

import com.github.allinone.sources.audiomack.AudiomackAudioSourceManager;
import com.github.allinone.sources.gaana.GaanaAudioSourceManager;
import com.github.allinone.sources.pandora.PandoraAudioSourceManager;
import com.github.allinone.sources.qobuz.QobuzAudioSourceManager;
import com.github.topi314.lavasearch.SearchManager;
import com.github.topi314.lavasearch.api.SearchManagerConfiguration;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import dev.arbjerg.lavalink.api.AudioPlayerManagerConfiguration;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AllInOnePlugin implements AudioPlayerManagerConfiguration, SearchManagerConfiguration {

    private static final Logger log = LoggerFactory.getLogger(AllInOnePlugin.class);

    private final AllInOneConfig config;
    private AudiomackAudioSourceManager audiomack;
    private GaanaAudioSourceManager gaana;
    private PandoraAudioSourceManager pandora;
    private QobuzAudioSourceManager qobuz;

    public AllInOnePlugin(AllInOneConfig config) {
        this.config = config;
        log.info("Initializing All-In-One Lavalink Plugin...");

        // Directly start auto-update check in the background
        Thread updateThread = new Thread(() -> UpdateService.checkAndUpdate(config), "AllInOne-AutoUpdate");
        updateThread.setDaemon(true);
        updateThread.start();
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
            this.audiomack = new AudiomackAudioSourceManager(config.getProviders(), manager);
            manager.registerSourceManager(this.audiomack);
        }

        if (config.isGaana()) {
            log.info("Registering Gaana audio source manager...");
            this.gaana = new GaanaAudioSourceManager(config, manager);
            manager.registerSourceManager(this.gaana);
        }

        if (config.isPandora()) {
            log.info("Registering Pandora audio source manager...");
            this.pandora = new PandoraAudioSourceManager(config, manager);
            manager.registerSourceManager(this.pandora);
        }

        if (config.isQobuz()) {
            log.info("Registering Qobuz audio source manager...");
            this.qobuz = new QobuzAudioSourceManager(config, manager);
            manager.registerSourceManager(this.qobuz);
        }

        return manager;
    }

    @NotNull
    @Override
    public SearchManager configure(@NotNull SearchManager manager) {
        if (!config.isEnabled()) {
            return manager;
        }

        if (this.audiomack != null) {
            manager.registerSearchManager(this.audiomack);
        }
        if (this.gaana != null) {
            manager.registerSearchManager(this.gaana);
        }
        if (this.pandora != null) {
            manager.registerSearchManager(this.pandora);
        }
        if (this.qobuz != null) {
            manager.registerSearchManager(this.qobuz);
        }

        return manager;
    }
}

