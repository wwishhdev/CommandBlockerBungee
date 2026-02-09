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

                String jsonPayload = String.format("{"username": "%s", "avatar_url": "%s", "content": "%s"}",
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
        return text.replace("", "")
                .replace(""", """)
                .replace("\b", "\b")
                .replace("\f", "\f")
                .replace("
", "
")
                .replace("", "")
                .replace("	", "	");
    }
}
