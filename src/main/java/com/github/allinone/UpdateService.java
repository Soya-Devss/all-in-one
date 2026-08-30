package com.github.allinone;

import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;

public class UpdateService {

    private static final Logger log = LoggerFactory.getLogger(UpdateService.class);

    public static void checkAndUpdate(AllInOneConfig config) {
        if (!config.isAutoUpdate()) {
            return;
        }

        try {
            pullGitRepository(config.getGitBranch());
        } catch (Exception e) {
            log.debug("Git pull check skipped: {}", e.getMessage());
        }

        try {
            checkGitHubRelease(config.getRepository());
        } catch (Exception e) {
            log.debug("Release check skipped: {}", e.getMessage());
        }
    }

    private static void pullGitRepository(String branch) {
        File currentDir = new File(".");
        File gitDir = new File(currentDir, ".git");
        File parentGitDir = new File(currentDir, "../.git");

        File targetWorkingDir = null;
        if (gitDir.exists() && gitDir.isDirectory()) {
            targetWorkingDir = currentDir;
        } else if (parentGitDir.exists() && parentGitDir.isDirectory()) {
            targetWorkingDir = new File("..");
        }

        if (targetWorkingDir == null) {
            return;
        }

        try {
            log.info("Checking git updates on branch '{}' in {}...", branch, targetWorkingDir.getCanonicalPath());
            ProcessBuilder pb = new ProcessBuilder("git", "pull", "origin", branch);
            pb.directory(targetWorkingDir);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.info("[Git] {}", line);
                }
            }

            int exitCode = process.waitFor();
            log.info("Git pull completed with exit code {}.", exitCode);
        } catch (Exception e) {
            log.warn("Failed to execute git pull: {}", e.getMessage());
        }
    }

    private static void checkGitHubRelease(String repo) {
        if (repo == null || repo.isBlank()) {
            return;
        }

        try {
            HttpClient client = HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.github.com/repos/" + repo + "/releases/latest"))
                    .header("User-Agent", "All-In-One-Lavalink-Plugin")
                    .header("Accept", "application/vnd.github.v3+json")
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return;
            }

            JSONObject release = new JSONObject(response.body());
            JSONArray assets = release.optJSONArray("assets");
            if (assets == null) {
                return;
            }

            for (int i = 0; i < assets.length(); i++) {
                JSONObject asset = assets.getJSONObject(i);
                String name = asset.optString("name", "");
                String downloadUrl = asset.optString("browser_download_url", "");

                if (name.endsWith(".jar") && !downloadUrl.isBlank()) {
                    File pluginsDir = new File("plugins");
                    if (!pluginsDir.exists()) {
                        pluginsDir.mkdirs();
                    }

                    File targetJar = new File(pluginsDir, name);
                    if (!targetJar.exists()) {
                        log.info("Downloading latest plugin release: {}...", name);
                        HttpRequest downloadReq = HttpRequest.newBuilder()
                                .uri(URI.create(downloadUrl))
                                .header("User-Agent", "All-In-One-Lavalink-Plugin")
                                .GET()
                                .build();

                        HttpResponse<InputStream> downloadResp = client.send(downloadReq, HttpResponse.BodyHandlers.ofInputStream());
                        if (downloadResp.statusCode() == 200) {
                            Path tempFile = Files.createTempFile("all-in-one-download-", ".jar");
                            try (InputStream in = downloadResp.body()) {
                                Files.copy(in, tempFile, StandardCopyOption.REPLACE_EXISTING);
                            }
                            Files.move(tempFile, targetJar.toPath(), StandardCopyOption.REPLACE_EXISTING);
                            log.info("Plugin successfully updated to {}!", name);
                        }
                    }
                    break;
                }
            }
        } catch (Exception e) {
            log.debug("GitHub release update check encountered: {}", e.getMessage());
        }
    }
}
