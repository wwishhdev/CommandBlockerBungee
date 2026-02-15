package com.wish.commandblockervelocity;

import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.bstats.velocity.Metrics;
import org.slf4j.Logger;

import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import com.wish.commandblockervelocity.commands.ReloadCommand;
import com.wish.commandblockervelocity.database.DatabaseManager;
import com.wish.commandblockervelocity.listeners.ChatListener;
import com.wish.commandblockervelocity.listeners.ConnectionListener;
import com.wish.commandblockervelocity.managers.ConfigManager;
import com.wish.commandblockervelocity.managers.CooldownManager;
import com.wish.commandblockervelocity.managers.WebhookManager;

@Plugin(
        id = "commandblockervelocity",
        name = "CommandBlockerVelocity",
        version = "2.1.3",
        description = "A plugin to block commands in Velocity",
        authors = {"wwishhdev"}
)
public class CommandBlockerVelocity {

    private final ProxyServer proxy;
    private final Logger logger;
    private final Path dataDirectory;
    private final Metrics.Factory metricsFactory;
    private ExecutorService executorService;

    private ConfigManager configManager;
    private CooldownManager cooldownManager;
    private DatabaseManager databaseManager;
    private WebhookManager webhookManager;

    @Inject
    public CommandBlockerVelocity(ProxyServer proxy, Logger logger, @DataDirectory Path dataDirectory, Metrics.Factory metricsFactory) {
        this.proxy = proxy;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
        this.metricsFactory = metricsFactory;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        // Initialize Thread Pool (Fixed size to prevent thread exhaustion)
        this.executorService = Executors.newFixedThreadPool(16);

        // ASCII Art using Legacy serializer for simplicity in logger or just raw string if logger supports it.
        // Slf4j logger handles strings.
        logger.info("\n" +
                " ██████╗ ██████╗ ███╗   ███╗███╗   ███╗ █████╗ ███╗   ██╗██████╗ ██████╗ ██╗      ██████╗  ██████╗██╗  ██╗███████╗██████╗ \n" +
                "██╔════╝██╔═══██╗████╗ ████║████╗ ████║██╔══██╗████╗  ██║██╔══██╗██╔══██╗██║     ██╔═══██╗██╔════╝██║ ██╔╝██╔════╝██╔══██╗\n" +
                "██║     ██║   ██║██╔████╔██║██╔████╔██║███████║██╔██╗ ██║██║  ██║██████╔╝██║     ██║   ██║██║     █████╔╝ █████╗  ██████╔╝\n" +
                "██║     ██║   ██║██║╚██╔╝██║██║╚██╔╝██║██╔══██║██║╚██╗██║██║  ██║██╔══██╗██║     ██║   ██║██║     ██╔═██╗ ██╔══╝  ██╔══██╗\n" +
                "╚██████╗╚██████╔╝██║ ╚═╝ ██║██║ ╚═╝ ██║██║  ██║██║ ╚████║██████╔╝██████╔╝███████╗╚██████╔╝╚██████╗██║  ██╗███████╗██║  ██║\n" +
                " ╚═════╝ ╚═════╝ ╚═╝     ╚═╝╚═╝     ╚═╝╚═╝  ╚═╝╚═╝  ╚═══╝╚═════╝ ╚═════╝ ╚══════╝ ╚═════╝  ╚═════╝╚═╝  ╚═╝╚══════╝╚═╝  ╚═╝\n" +
                "                CommandBlockerVelocity v2.1.3 \u2764\n" +
                "                                                          by wwishhdev\n");

        // Managers
        this.configManager = new ConfigManager(this, dataDirectory);
        this.configManager.loadConfiguration();
        
        this.databaseManager = new DatabaseManager(this, configManager, dataDirectory, executorService);
        this.databaseManager.init();
        
        this.webhookManager = new WebhookManager(this, configManager, executorService);

        this.cooldownManager = new CooldownManager(this, configManager, databaseManager);

        // Listeners
        proxy.getEventManager().register(this, new ChatListener(this, configManager, cooldownManager, webhookManager));
        proxy.getEventManager().register(this, new ConnectionListener(cooldownManager));

        // Commands
        CommandManager commandManager = proxy.getCommandManager();
        CommandMeta commandMeta = commandManager.metaBuilder("cblockerreload")
                .aliases("cbreload")
                .plugin(this)
                .build();

        commandManager.register(commandMeta, new ReloadCommand(this));

        // bStats
        metricsFactory.make(this, 24030);

        logger.info("CommandBlockerVelocity has been enabled successfully!");
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        if (cooldownManager != null) {
            cooldownManager.clear();
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

        logger.info("CommandBlockerVelocity has been disabled!");
    }
    
    public ProxyServer getProxy() {
        return proxy;
    }

    public Logger getLogger() {
        return logger;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }
    
    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }
    
    // Add close logic for velocity if possible (ProxyShutdownEvent) but simple works for now
}