package com.github.allinone;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "plugins.allinone")
public class AllInOneConfig {

    private boolean enabled = true;
    private boolean audiomack = true;
    private boolean gaana = true;
    private boolean pandora = true;
    private boolean qobuz = true;
    private boolean autoUpdate = true;
    private String repository = "Soya-Devss/all-in-one";
    private String gitBranch = "main";
    private String gaanaApiUrl = "https://gaana-api-2.vercel.app/api";
    private String gaanaProxy = null;
    private String pandoraProxy = null;
    private String qobuzUserToken = null;
    private String qobuzAppId = null;
    private String qobuzAppSecret = null;
    private String qobuzFormatId = "5";
    private String qobuzProxy = null;
    private int qobuzPlaylistLimit = 100;
    private int qobuzAlbumLimit = 100;
    private int qobuzArtistLimit = 100;

    private String[] providers = {
            "ytmsearch:\"%ISRC%\"",
            "ytmsearch:%QUERY%",
            "ytsearch:\"%ISRC%\"",
            "ytsearch:%QUERY%",
            "scsearch:%QUERY%"
    };

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isAudiomack() {
        return audiomack;
    }

    public void setAudiomack(boolean audiomack) {
        this.audiomack = audiomack;
    }

    public boolean isGaana() {
        return gaana;
    }

    public void setGaana(boolean gaana) {
        this.gaana = gaana;
    }

    public boolean isPandora() {
        return pandora;
    }

    public void setPandora(boolean pandora) {
        this.pandora = pandora;
    }

    public boolean isQobuz() {
        return qobuz;
    }

    public void setQobuz(boolean qobuz) {
        this.qobuz = qobuz;
    }

    public String getQobuzUserToken() {
        return qobuzUserToken;
    }

    public void setQobuzUserToken(String qobuzUserToken) {
        this.qobuzUserToken = qobuzUserToken;
    }

    public String getQobuzAppId() {
        return qobuzAppId;
    }

    public void setQobuzAppId(String qobuzAppId) {
        this.qobuzAppId = qobuzAppId;
    }

    public String getQobuzAppSecret() {
        return qobuzAppSecret;
    }

    public void setQobuzAppSecret(String qobuzAppSecret) {
        this.qobuzAppSecret = qobuzAppSecret;
    }

    public String getQobuzFormatId() {
        return qobuzFormatId;
    }

    public void setQobuzFormatId(String qobuzFormatId) {
        this.qobuzFormatId = qobuzFormatId;
    }

    public String getQobuzProxy() {
        return qobuzProxy;
    }

    public void setQobuzProxy(String qobuzProxy) {
        this.qobuzProxy = qobuzProxy;
    }

    public int getQobuzPlaylistLimit() {
        return qobuzPlaylistLimit;
    }

    public void setQobuzPlaylistLimit(int qobuzPlaylistLimit) {
        this.qobuzPlaylistLimit = qobuzPlaylistLimit;
    }

    public int getQobuzAlbumLimit() {
        return qobuzAlbumLimit;
    }

    public void setQobuzAlbumLimit(int qobuzAlbumLimit) {
        this.qobuzAlbumLimit = qobuzAlbumLimit;
    }

    public int getQobuzArtistLimit() {
        return qobuzArtistLimit;
    }

    public void setQobuzArtistLimit(int qobuzArtistLimit) {
        this.qobuzArtistLimit = qobuzArtistLimit;
    }

    public boolean isAutoUpdate() {
        return autoUpdate;
    }

    public void setAutoUpdate(boolean autoUpdate) {
        this.autoUpdate = autoUpdate;
    }

    public String getRepository() {
        return repository;
    }

    public void setRepository(String repository) {
        this.repository = repository;
    }

    public String getGitBranch() {
        return gitBranch;
    }

    public void setGitBranch(String gitBranch) {
        this.gitBranch = gitBranch;
    }

    public String getGaanaApiUrl() {
        return gaanaApiUrl;
    }

    public void setGaanaApiUrl(String gaanaApiUrl) {
        this.gaanaApiUrl = gaanaApiUrl;
    }

    public String getGaanaProxy() {
        return gaanaProxy;
    }

    public void setGaanaProxy(String gaanaProxy) {
        this.gaanaProxy = gaanaProxy;
    }

    public String getPandoraProxy() {
        return pandoraProxy;
    }

    public void setPandoraProxy(String pandoraProxy) {
        this.pandoraProxy = pandoraProxy;
    }

    public String[] getProviders() {
        return providers;
    }

    public void setProviders(String[] providers) {
        this.providers = providers;
    }
}
