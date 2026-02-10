package com.wish.commandblockervelocity.managers;

import com.wish.commandblockervelocity.CommandBlockerVelocity;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

public class WebhookManager {

    private final CommandBlockerVelocity plugin;
    private final ConfigManager config;
    private final HttpClient httpClient;

    public WebhookManager(CommandBlockerVelocity plugin, ConfigManager config) {
        this.plugin = plugin;
        this.config = config;
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public void sendWebhook(String playerName, String command) {
        if (!config.isWebhookEnabled() || config.getWebhookUrl().isEmpty()) return;

        CompletableFuture.runAsync(() -> {
            try {
                String content = config.getWebhookContent()
                        .replace("{player}", playerName)
                        .replace("{command}", command);
                
                // Escape JSON strings manually to avoid dependency
                String jsonContent = escapeJson(content);
                String jsonUsername = escapeJson(config.getWebhookUsername());
                String jsonAvatar = escapeJson(config.getWebhookAvatarUrl());

                String jsonPayload = String.format("{\"username\": \"%s\", \"avatar_url\": \"%s\", \"content\": \"%s\"}",
                        jsonUsername, jsonAvatar, jsonContent);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(config.getWebhookUrl()))
                        .timeout(Duration.ofSeconds(10))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                        .build();

                httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            } catch (Exception e) {
                plugin.getLogger().warn("Failed to send Discord webhook: " + e.getMessage());
            }
        });
    }

    private String escapeJson(String text) {
        if (text == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\b':
                    sb.append("\\b");
                    break;
                case '\f':
                    sb.append("\\f");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    if (c < ' ') {
                        String t = "000" + Integer.toHexString(c);
                        sb.append("\\u").append(t.substring(t.length() - 4));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }
}