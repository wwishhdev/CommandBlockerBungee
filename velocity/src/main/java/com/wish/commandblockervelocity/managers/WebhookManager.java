package com.wish.commandblockervelocity.managers;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.velocitypowered.api.scheduler.ScheduledTask;
import com.wish.commandblockervelocity.CommandBlockerVelocity;

public class WebhookManager {

    private final CommandBlockerVelocity plugin;
    private final ConfigManager config;
    private final HttpClient httpClient;
    private final ExecutorService executor;
    private final Queue<WebhookRequest> queue = new ConcurrentLinkedQueue<>();
    private final AtomicInteger queueSize = new AtomicInteger(0);
    private final Map<String, Long> playerLastWebhook = new ConcurrentHashMap<>();
    private static final int MAX_QUEUE_SIZE = 100;
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final long EVICTION_MULTIPLIER = 2;
    private final ScheduledTask processTask;
    private final ScheduledTask evictTask;

    public WebhookManager(CommandBlockerVelocity plugin, ConfigManager config, ExecutorService executor) {
        this.plugin = plugin;
        this.config = config;
        this.executor = executor;
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        // Process up to 4 queued webhooks per second
        this.processTask = plugin.getProxy().getScheduler().buildTask(plugin, this::processQueue)
                .repeat(1, TimeUnit.SECONDS)
                .schedule();
        // FIX: Evict stale rate-limit entries every minute
        this.evictTask = plugin.getProxy().getScheduler().buildTask(plugin, this::evictStaleEntries)
                .repeat(60, TimeUnit.SECONDS)
                .schedule();
    }

    public void sendWebhook(String playerName, String command, String serverName, String uuid) {
        if (!config.isWebhookEnabled() || config.getWebhookUrl().isEmpty()) return;

        String url = config.getWebhookUrl().toLowerCase();
        if (!url.startsWith("https://discord.com/api/webhooks/") && !url.startsWith("https://discordapp.com/api/webhooks/")) {
            plugin.getLogger().warn("Discord webhook URL is not a valid Discord webhook. Skipping.");
            return;
        }

        if (queueSize.get() >= MAX_QUEUE_SIZE) return;

        long now = System.currentTimeMillis();
        long rateLimitMs = config.getWebhookRateLimit() * 1000L;
        Long lastSent = playerLastWebhook.get(playerName);
        if (lastSent != null && (now - lastSent) < rateLimitMs) return;
        playerLastWebhook.put(playerName, now);

        queue.add(new WebhookRequest(playerName, command, serverName, uuid));
        queueSize.incrementAndGet();
    }

    private void processQueue() {
        if (queue.isEmpty()) return;
        for (int i = 0; i < 4; i++) {
            WebhookRequest req = queue.poll();
            if (req == null) break;
            queueSize.decrementAndGet();
            String safePlayer  = req.playerName.replaceAll("([_`*~|])", "\\\\$1");
            String safeCommand = req.command.replaceAll("([_`*~|])", "\\\\$1");
            send(safePlayer, safeCommand, req.serverName, req.uuid, 0);
        }
    }

    /**
     * Clears queued webhooks and rate-limit state so that config changes take effect immediately.
     */
    public void reload() {
        queue.clear();
        queueSize.set(0);
        playerLastWebhook.clear();
    }

    public void shutdown() {
        if (processTask != null) processTask.cancel();
        if (evictTask != null) evictTask.cancel();
    }

    private void evictStaleEntries() {
        long now = System.currentTimeMillis();
        long evictAfterMs = config.getWebhookRateLimit() * 1000L * EVICTION_MULTIPLIER;
        playerLastWebhook.entrySet().removeIf(entry -> (now - entry.getValue()) > evictAfterMs);
    }

    /**
     * NEW: Exponential-backoff retry on 429 / 5xx. Max 3 total attempts.
     */
    private void send(String playerName, String command, String serverName, String uuid, int attempt) {
        executor.execute(() -> {
            try {
                String processedCommand = command;
                String lowerCmd = command.toLowerCase().replaceAll("^/+", "");
                if (lowerCmd.startsWith("login") || lowerCmd.startsWith("register") || lowerCmd.startsWith("changepassword")
                        || lowerCmd.startsWith("l ") || lowerCmd.startsWith("log ") || lowerCmd.startsWith("reg ")
                        || lowerCmd.startsWith("passwd") || lowerCmd.startsWith("premium") || lowerCmd.startsWith("auth")) {
                    String[] parts = command.split("\\s+");
                    if (parts.length > 1) processedCommand = parts[0] + " *****";
                }

                String content = config.getWebhookContent()
                        .replace("{player}", playerName)
                        .replace("{command}", processedCommand)
                        .replace("{server}", serverName)
                        .replace("{uuid}", uuid)
                        .replace("{timestamp}", TIMESTAMP_FORMAT.format(LocalDateTime.now()));

                String jsonPayload = "{\"username\": \"" + escapeJson(config.getWebhookUsername())
                        + "\", \"avatar_url\": \"" + escapeJson(config.getWebhookAvatarUrl())
                        + "\", \"content\": \"" + escapeJson(content) + "\"}";

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(config.getWebhookUrl()))
                        .timeout(Duration.ofSeconds(10))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                        .build();

                HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
                int status = response.statusCode();

                if ((status == 429 || (status >= 500 && status < 600)) && attempt < 2) {
                    long delaySeconds = (long) Math.pow(2, attempt + 1);
                    plugin.getLogger().warn("Discord webhook returned " + status + ". Retrying in " + delaySeconds + "s (attempt " + (attempt + 1) + "/3).");
                    plugin.getProxy().getScheduler().buildTask(plugin, () -> send(playerName, command, serverName, uuid, attempt + 1))
                            .delay(delaySeconds, TimeUnit.SECONDS)
                            .schedule();
                } else if (status < 200 || status >= 300) {
                    plugin.getLogger().warn("Discord webhook returned non-2xx status: " + status + " (all retries exhausted).");
                }
            } catch (Exception e) {
                plugin.getLogger().warn("Failed to send Discord webhook: " + e.getMessage());
            }
        });
    }

    private static class WebhookRequest {
        final String playerName;
        final String command;
        final String serverName;
        final String uuid;
        WebhookRequest(String playerName, String command, String serverName, String uuid) {
            this.playerName = playerName;
            this.command = command;
            this.serverName = serverName;
            this.uuid = uuid;
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