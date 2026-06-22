package com.wish.commandblockerbungee;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.bstats.bungeecord.Metrics;

import com.wish.commandblockerbungee.commands.ReloadCommand;
import com.wish.commandblockerbungee.commands.StatusCommand;
import com.wish.commandblockerbungee.database.DatabaseManager;
import com.wish.commandblockerbungee.listeners.ChatListener;
import com.wish.commandblockerbungee.listeners.ConnectionListener;
import com.wish.commandblockerbungee.managers.ConfigManager;
import com.wish.commandblockerbungee.managers.CooldownManager;
import com.wish.commandblockerbungee.managers.WebhookManager;
import com.wish.commandblockerbungee.utils.FileLogger;

import net.kyori.adventure.platform.bungeecord.BungeeAudiences;
import net.md_5.bungee.api.plugin.Plugin;

public class CommandBlockerBungee extends Plugin {

    public static final String VERSION = "2.4.0";

    private ConfigManager configManager;
    private CooldownManager cooldownManager;
    private DatabaseManager databaseManager;
    private WebhookManager webhookManager;
    private BungeeAudiences adventure;
    private ExecutorService executorService;
    private FileLogger fileLogger;

    @Override
    public void onEnable() {
        // Initialize config first to read thread pool size
        this.configManager = new ConfigManager(this);
        this.configManager.loadConfiguration();

        // Initialize Thread Pool (configurable size to prevent thread exhaustion)
        this.executorService = Executors.newFixedThreadPool(configManager.getThreadPoolSize());

        // Initialize Adventure
        this.adventure = BungeeAudiences.create(this);

        adventure().sender(getProxy().getConsole()).sendMessage(configManager.parse(
                "<gold>\n"
                        + "  ____                                          _ ____  _            _\n"
                        + " / ___|___  _ __ ___  _ __ ___   __ _ _ __   __| | __ )| | ___   ___| | _____ _ __\n"
                        + "| |   / _ \\| '_ ` _ \\| '_ ` _ \\ / _` | '_ \\ / _` |  _ \\| |/ _ \\ / __| |/ / _ \\ '__|\n"
                        + "| |__| (_) | | | | | | | | | | | (_| | | | | (_| | |_) | | (_) | (__|   <  __/ |\n"
                        + " \\____\\___/|_| |_| |_|_| |_| |_|\\__,_|_| |_|\\__,_|____/|_|\\___/ \\___|_|\\_\\___|_|\n"
                        + "<yellow>                CommandBlockerBungee v" + VERSION + "\n"
                        + "<aqua>                                                          by wwishhdev\n"));

        this.databaseManager = new DatabaseManager(this, configManager, executorService);
        this.databaseManager.init();

        this.webhookManager = new WebhookManager(this, configManager, executorService);

        this.cooldownManager = new CooldownManager(this, configManager, databaseManager);

        this.fileLogger = new FileLogger(getDataFolder(), executorService, getLogger(), configManager.getAuditLogMaxFiles());

        // Listeners & Commands
        getProxy().getPluginManager().registerListener(this, new ChatListener(this, configManager, cooldownManager, webhookManager, fileLogger));
        getProxy().getPluginManager().registerListener(this, new ConnectionListener(cooldownManager));
        getProxy().getPluginManager().registerCommand(this, new ReloadCommand(this));
        getProxy().getPluginManager().registerCommand(this, new StatusCommand(this));

        // bStats
        new Metrics(this, 24030);

        getLogger().info("CommandBlockerBungee has been enabled successfully!");
    }

    @Override
    public void onDisable() {
        if (this.adventure != null) {
            this.adventure.close();
            this.adventure = null;
        }
        if (cooldownManager != null) {
            cooldownManager.clear();
        }
        if (webhookManager != null) {
            webhookManager.shutdown();
        }
        if (databaseManager != null) {
            databaseManager.close();
        }

        if (executorService != null) {
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
            }
        }

        getLogger().info("CommandBlockerBungee has been disabled!");
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public WebhookManager getWebhookManager() {
        return webhookManager;
    }

    public BungeeAudiences adventure() {
        if (this.adventure == null) {
            throw new IllegalStateException("Cannot retrieve audience provider while plugin is not enabled");
        }
        return this.adventure;
    }
}
