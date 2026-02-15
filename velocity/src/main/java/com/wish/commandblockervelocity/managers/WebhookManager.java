package com.wish.commandblockervelocity.managers;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.wish.commandblockervelocity.CommandBlockerVelocity;

public class WebhookManager {

    private final CommandBlockerVelocity plugin;
    private final ConfigManager config;
    private final HttpClient httpClient;
    private final ExecutorService executor;
    private final Queue<WebhookRequest> queue = new ConcurrentLinkedQueue<>();
    private final AtomicInteger queueSize = new AtomicInteger(0);
    private static final int MAX_QUEUE_SIZE = 100;

    public WebhookManager(CommandBlockerVelocity plugin, ConfigManager config, ExecutorService executor) {
        this.plugin = plugin;
        this.config = config;
        this.executor = executor;
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        
        plugin.getProxy().getScheduler().buildTask(plugin, this::processQueue)
                .repeat(1, TimeUnit.SECONDS)
                .schedule();
    }

    public void sendWebhook(String playerName, String command) {
        if (!config.isWebhookEnabled() || config.getWebhookUrl().isEmpty()) return;

        // Validate webhook URL to prevent SSRF
        String url = config.getWebhookUrl().toLowerCase();
        if (!url.startsWith("https://discord.com/api/webhooks/") && !url.startsWith("https://discordapp.com/api/webhooks/")) {
            plugin.getLogger().warn("Discord webhook URL is not a valid Discord webhook. Skipping.");
            return;
        }
        
        if (queueSize.get() >= MAX_QUEUE_SIZE) {
            return;
        }
        queue.add(new WebhookRequest(playerName, command));
        queueSize.incrementAndGet();
    }

    private void processQueue() {
        if (queue.isEmpty()) return;

        for (int i = 0; i < 4; i++) {
            WebhookRequest req = queue.poll();
            if (req == null) break;
            queueSize.decrementAndGet();
            
            // Sanitize markdown in player name and command
            String safePlayer = req.playerName.replaceAll("([_`*~|])", "\\\\$1");
            String safeCommand = req.command.replaceAll("([_`*~|])", "\\\\$1");
            send(safePlayer, safeCommand);
        }
    }

    private void send(String playerName, String command) {
        CompletableFuture.runAsync(() -> {
            try {
                // Redact sensitive commands (handle with and without leading slash)
                String processedCommand = command;
                String lowerCmd = command.toLowerCase().replaceAll("^/+", "");
                if (lowerCmd.startsWith("login") || lowerCmd.startsWith("register") || lowerCmd.startsWith("changepassword")
                        || lowerCmd.startsWith("l ") || lowerCmd.startsWith("log ") || lowerCmd.startsWith("reg ")
                        || lowerCmd.startsWith("passwd") || lowerCmd.startsWith("premium") || lowerCmd.startsWith("auth")) {
                    String[] parts = command.split("\\s+");
                    if (parts.length > 1) {
                        processedCommand = parts[0] + " *****";
                    }
                }

                String content = config.getWebhookContent()
                        .replace("{player}", playerName)
                        .replace("{command}", processedCommand);
                
                String jsonContent = escapeJson(content);
                String jsonUsername = escapeJson(config.getWebhookUsername());
                String jsonAvatar = escapeJson(config.getWebhookAvatarUrl());

                // Use concatenation instead of String.format to avoid issues with % characters
                String jsonPayload = "{\"username\": \"" + jsonUsername + "\", \"avatar_url\": \"" + jsonAvatar + "\", \"content\": \"" + jsonContent + "\"}";

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