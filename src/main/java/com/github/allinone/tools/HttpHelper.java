package com.github.allinone.tools;

import java.io.IOException;
import java.io.InputStream;
import java.net.Authenticator;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class HttpHelper {

    private static final HttpClient DEFAULT_CLIENT = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private static final Map<String, HttpClient> PROXY_CLIENTS = new ConcurrentHashMap<>();

    private static HttpClient getClient(String proxyUrl) {
        if (proxyUrl == null || proxyUrl.isBlank()) {
            return DEFAULT_CLIENT;
        }

        return PROXY_CLIENTS.computeIfAbsent(proxyUrl, url -> {
            try {
                URI uri = URI.create(url);
                String host = uri.getHost();
                int port = uri.getPort() != -1 ? uri.getPort() : 8080;
                String userInfo = uri.getUserInfo();

                HttpClient.Builder builder = HttpClient.newBuilder()
                        .version(HttpClient.Version.HTTP_1_1)
                        .followRedirects(HttpClient.Redirect.NORMAL)
                        .connectTimeout(Duration.ofSeconds(10))
                        .proxy(ProxySelector.of(new InetSocketAddress(host, port)));

                if (userInfo != null && userInfo.contains(":")) {
                    String[] parts = userInfo.split(":", 2);
                    builder.authenticator(new Authenticator() {
                        @Override
                        protected PasswordAuthentication getPasswordAuthentication() {
                            return new PasswordAuthentication(parts[0], parts[1].toCharArray());
                        }
                    });
                }

                return builder.build();
            } catch (Exception e) {
                return DEFAULT_CLIENT;
            }
        });
    }

    public static String get(String url, Map<String, String> headers) throws IOException, InterruptedException {
        return get(url, headers, null);
    }

    public static String get(String url, Map<String, String> headers, String proxyUrl) throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .GET();

        boolean hasUserAgent = false;
        if (headers != null) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                builder.header(entry.getKey(), entry.getValue());
                if ("user-agent".equalsIgnoreCase(entry.getKey())) {
                    hasUserAgent = true;
                }
            }
        }
        if (!hasUserAgent) {
            builder.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36");
        }

        HttpResponse<String> response = getClient(proxyUrl).send(builder.build(), HttpResponse.BodyHandlers.ofString());
        return response.body();
    }

    public static String post(String url, String body, Map<String, String> headers) throws IOException, InterruptedException {
        return post(url, body, headers, null);
    }

    public static String post(String url, String body, Map<String, String> headers, String proxyUrl) throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .POST(HttpRequest.BodyPublishers.ofString(body != null ? body : ""));

        boolean hasUserAgent = false;
        if (headers != null) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                builder.header(entry.getKey(), entry.getValue());
                if ("user-agent".equalsIgnoreCase(entry.getKey())) {
                    hasUserAgent = true;
                }
            }
        }
        if (!hasUserAgent) {
            builder.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36");
        }

        HttpResponse<String> response = getClient(proxyUrl).send(builder.build(), HttpResponse.BodyHandlers.ofString());
        return response.body();
    }

    public static HttpResponse<Void> head(String url, Map<String, String> headers) throws IOException, InterruptedException {
        return head(url, headers, null);
    }

    public static HttpResponse<Void> head(String url, Map<String, String> headers, String proxyUrl) throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .method("HEAD", HttpRequest.BodyPublishers.noBody());

        if (headers != null) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                builder.header(entry.getKey(), entry.getValue());
            }
        }

        return getClient(proxyUrl).send(builder.build(), HttpResponse.BodyHandlers.discarding());
    }

    public static InputStream getStream(String url, Map<String, String> headers) throws IOException, InterruptedException {
        return getStream(url, headers, null);
    }

    public static InputStream getStream(String url, Map<String, String> headers, String proxyUrl) throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .GET();

        if (headers != null) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                builder.header(entry.getKey(), entry.getValue());
            }
        }

        HttpResponse<InputStream> response = getClient(proxyUrl).send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
        return response.body();
    }
}
