package com.wish.commandblockerbungee.managers;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.wish.commandblockerbungee.CommandBlockerBungee;

public class WebhookManager {

    private final CommandBlockerBungee plugin;
    private final ConfigManager config;
    private final HttpClient httpClient;
    private final ExecutorService executor;
    private final Queue<WebhookRequest> queue = new ConcurrentLinkedQueue<>();
    private final AtomicInteger queueSize = new AtomicInteger(0);
    // Stores last-sent timestamp per player for rate-limiting
    private final Map<String, Long> playerLastWebhook = new ConcurrentHashMap<>();
    private static final int MAX_QUEUE_SIZE = 100;
    // How long (ms) a player entry stays in the rate-limit map after it expires.
    // Evict entries that are older than 2x the configured rate-limit window.
    private static final long EVICTION_MULTIPLIER = 2;

    public WebhookManager(CommandBlockerBungee plugin, ConfigManager config, ExecutorService executor) {
        this.plugin = plugin;
        this.config = config;
        this.executor = executor;
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        // Process up to 4 queued webhooks per second
        plugin.getProxy().getScheduler().schedule(plugin, this::processQueue, 1, 1, TimeUnit.SECONDS);
        // FIX: Evict stale rate-limit entries every minute to prevent unbounded map growth
        plugin.getProxy().getScheduler().schedule(plugin, this::evictStaleEntries, 60, 60, TimeUnit.SECONDS);
    }

    public void sendWebhook(String playerName, String command) {
        if (!config.isWebhookEnabled() || config.getWebhookUrl().isEmpty()) return;

        // Validate webhook URL to prevent SSRF
        String url = config.getWebhookUrl().toLowerCase();
        if (!url.startsWith("https://discord.com/api/webhooks/") && !url.startsWith("https://discordapp.com/api/webhooks/")) {
            plugin.getLogger().warning("Discord webhook URL is not a valid Discord webhook. Skipping.");
            return;
        }

        if (queueSize.get() >= MAX_QUEUE_SIZE) {
            return;
        }

        // Per-player rate limit
        long now = System.currentTimeMillis();
        long rateLimitMs = config.getWebhookRateLimit() * 1000L;
        Long lastSent = playerLastWebhook.get(playerName);
        if (lastSent != null && (now - lastSent) < rateLimitMs) {
            return;
        }
        playerLastWebhook.put(playerName, now);

        queue.add(new WebhookRequest(playerName, command));
        queueSize.incrementAndGet();
    }

    private void processQueue() {
        if (queue.isEmpty()) return;

        for (int i = 0; i < 4; i++) {
            WebhookRequest req = queue.poll();
            if (req == null) break;
            queueSize.decrementAndGet();

            // Sanitize Discord markdown in player name and command
            String safePlayer = req.playerName.replaceAll("([_`*~|])", "\\\\$1");
            String safeCommand = req.command.replaceAll("([_`*~|])", "\\\\$1");
            send(safePlayer, safeCommand, 0);
        }
    }

    /**
     * FIX: Remove playerLastWebhook map entries that are older than 2× the rate-limit
     * window. These entries serve no purpose once expired and would otherwise accumulate
     * indefinitely for every player that ever triggered a block.
     */
    private void evictStaleEntries() {
        long now = System.currentTimeMillis();
        long evictAfterMs = config.getWebhookRateLimit() * 1000L * EVICTION_MULTIPLIER;
        playerLastWebhook.entrySet().removeIf(entry -> (now - entry.getValue()) > evictAfterMs);
    }

    /**
     * NEW: Send with exponential-backoff retry on HTTP 429 (rate limited) or 5xx errors.
     * Max 3 attempts: immediate → 2 s → 4 s.
     */
    private void send(String playerName, String command, int attempt) {
        executor.execute(() -> {
            try {
                // Redact sensitive commands
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

                String jsonContent  = escapeJson(content);
                String jsonUsername = escapeJson(config.getWebhookUsername());
                String jsonAvatar   = escapeJson(config.getWebhookAvatarUrl());

                String jsonPayload = "{\"username\": \"" + jsonUsername + "\", \"avatar_url\": \"" + jsonAvatar + "\", \"content\": \"" + jsonContent + "\"}";

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(config.getWebhookUrl()))
                        .timeout(Duration.ofSeconds(10))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                        .build();

                HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
                int status = response.statusCode();

                if ((status == 429 || (status >= 500 && status < 600)) && attempt < 2) {
                    // Exponential backoff: 2^attempt seconds (2 s, 4 s)
                    long delaySeconds = (long) Math.pow(2, attempt + 1);
                    plugin.getLogger().warning("Discord webhook returned " + status + ". Retrying in " + delaySeconds + "s (attempt " + (attempt + 1) + "/3).");
                    plugin.getProxy().getScheduler().schedule(plugin, () -> send(playerName, command, attempt + 1), delaySeconds, TimeUnit.SECONDS);
                } else if (status < 200 || status >= 300) {
                    plugin.getLogger().warning("Discord webhook returned non-2xx status: " + status + " (all retries exhausted).");
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to send Discord webhook: " + e.getMessage());
            }
        });
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
                case '"':  sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\b': sb.append("\\b");  break;
                case '\f': sb.append("\\f");  break;
                case '\n': sb.append("\\n");  break;
                case '\r': sb.append("\\r");  break;
                case '\t': sb.append("\\t");  break;
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