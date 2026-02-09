package com.wish.commandblockervelocity;

import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import com.wish.commandblockervelocity.commands.ReloadCommand;
import com.wish.commandblockervelocity.listeners.ChatListener;
import com.wish.commandblockervelocity.listeners.ConnectionListener;
import com.wish.commandblockervelocity.managers.ConfigManager;
import com.wish.commandblockervelocity.managers.CooldownManager;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bstats.velocity.Metrics;
import org.slf4j.Logger;

import java.nio.file.Path;

@Plugin(
        id = "commandblockervelocity",
        name = "CommandBlockerVelocity",
        version = "2.0.0",
        description = "A plugin to block commands in Velocity",
        authors = {"wwishhdev"}
)
public class CommandBlockerVelocity {

    private final ProxyServer proxy;
    private final Logger logger;
    private final Path dataDirectory;
    private final Metrics.Factory metricsFactory;

    private ConfigManager configManager;
    private CooldownManager cooldownManager;

    @Inject
    public CommandBlockerVelocity(ProxyServer proxy, Logger logger, @DataDirectory Path dataDirectory, Metrics.Factory metricsFactory) {
        this.proxy = proxy;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
        this.metricsFactory = metricsFactory;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        // ASCII Art using Legacy serializer for simplicity in logger or just raw string if logger supports it.
        // Slf4j logger handles strings.
        logger.info("\n" +
                " ██████╗ ██████╗ ███╗   ███╗███╗   ███╗ █████╗ ███╗   ██╗██████╗ ██████╗ ██╗      ██████╗  ██████╗██╗  ██╗███████╗██████╗ \n" +
                "██╔════╝██╔═══██╗████╗ ████║████╗ ████║██╔══██╗████╗  ██║██╔══██╗██╔══██╗██║     ██╔═══██╗██╔════╝██║ ██╔╝██╔════╝██╔══██╗\n" +
                "██║     ██║   ██║██╔████╔██║██╔████╔██║███████║██╔██╗ ██║██║  ██║██████╔╝██║     ██║   ██║██║     █████╔╝ █████╗  ██████╔╝\n" +
                "██║     ██║   ██║██║╚██╔╝██║██║╚██╔╝██║██╔══██║██║╚██╗██║██║  ██║██╔══██╗██║     ██║   ██║██║     ██╔═██╗ ██╔══╝  ██╔══██╗\n" +
                "╚██████╗╚██████╔╝██║ ╚═╝ ██║██║ ╚═╝ ██║██║  ██║██║ ╚████║██████╔╝██████╔╝███████╗╚██████╔╝╚██████╗██║  ██╗███████╗██║  ██║\n" +
                " ╚═════╝ ╚═════╝ ╚═╝     ╚═╝╚═╝     ╚═╝╚═╝  ╚═╝╚═╝  ╚═══╝╚═════╝ ╚═════╝ ╚══════╝ ╚═════╝  ╚═════╝╚═╝  ╚═╝╚══════╝╚═╝  ╚═╝\n" +
                "                CommandBlockerVelocity v2.0.0 \u2764\n" +
                "                                                          by wwishhdev\n");

        // Managers
        this.configManager = new ConfigManager(this, dataDirectory);
        this.configManager.loadConfiguration();

        this.cooldownManager = new CooldownManager(this, configManager);

        // Listeners
        proxy.getEventManager().register(this, new ChatListener(this, configManager, cooldownManager));
        proxy.getEventManager().register(this, new ConnectionListener(cooldownManager));

        // Commands
        CommandManager commandManager = proxy.getCommandManager();
        CommandMeta commandMeta = commandManager.metaBuilder("cblockerreload")
                .aliases("commandblocker.reload", "cbreload")
                .plugin(this)
                .build();

        commandManager.register(commandMeta, new ReloadCommand(this));

        // bStats
        metricsFactory.make(this, 24030);

        logger.info("CommandBlockerVelocity has been enabled successfully!");
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
}