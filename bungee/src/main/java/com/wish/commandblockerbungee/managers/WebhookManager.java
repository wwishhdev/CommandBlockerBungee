package com.wish.commandblockerbungee.managers;

import com.wish.commandblockerbungee.CommandBlockerBungee;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

import java.util.concurrent.ExecutorService;

public class WebhookManager {

    private final CommandBlockerBungee plugin;
    private final ConfigManager config;
    private final HttpClient httpClient;
    private final ExecutorService executor;
    private final Queue<WebhookRequest> queue = new ConcurrentLinkedQueue<>();
    private static final int MAX_QUEUE_SIZE = 100; // Reduced to 100 for fail-fast

    public WebhookManager(CommandBlockerBungee plugin, ConfigManager config, ExecutorService executor) {
        this.plugin = plugin;
        this.config = config;
        this.executor = executor;
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        
        // Rate limit processor (Process max 4 webhooks per second)
        plugin.getProxy().getScheduler().schedule(plugin, this::processQueue, 1, 1, TimeUnit.SECONDS);
    }

    public void sendWebhook(String playerName, String command) {
        if (!config.isWebhookEnabled() || config.getWebhookUrl().isEmpty()) return;
        
        if (queue.size() >= MAX_QUEUE_SIZE) {
            // Drop request to prevent DoS/OOM
            return;
        }
        queue.add(new WebhookRequest(playerName, command));
    }

    private void processQueue() {
        if (queue.isEmpty()) return;

        // Process up to 4 requests per execution (approx 4/sec)
        for (int i = 0; i < 4; i++) {
            WebhookRequest req = queue.poll();
            if (req == null) break;
            
            // Sanitize markdown in player name
            String safePlayer = req.playerName.replaceAll("([_`*~|])", "\\\\$1");
            send(safePlayer, req.command);
        }
    }

    private void send(String playerName, String command) {
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
                plugin.getLogger().warning("Failed to send Discord webhook: " + e.getMessage());
            }
        }, executor);
    }

    private static class WebhookRequest {
        final String playerName;
        final String command;

        WebhookRequest(String playerName, String command) {
            this.playerName = playerName;
            this.command = command;
        }
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