package com.wish.commandblockervelocity.managers;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.wish.commandblockervelocity.CommandBlockerVelocity;

public class ProxyMessagingManager {

    private static final String MESSAGE_COMMAND = "msg";
    private static final String REPLY_COMMAND = "reply";

    private final CommandBlockerVelocity plugin;
    private final ConfigManager config;
    private final Map<UUID, UUID> lastReplyTargets = new ConcurrentHashMap<>();
    private final List<String> registeredAliases = new ArrayList<>();

    public ProxyMessagingManager(CommandBlockerVelocity plugin, ConfigManager config) {
        this.plugin = plugin;
        this.config = config;
    }

    public void reload() {
        unregister();
        register();
    }

    public void register() {
        if (!config.isProxyMessagingEnabled()) return;

        registerMessageCommand();
        registerReplyCommand();
    }

    public void unregister() {
        CommandManager commandManager = plugin.getProxy().getCommandManager();
        for (String alias : registeredAliases) {
            commandManager.unregister(alias);
        }
        registeredAliases.clear();
    }

    private void registerMessageCommand() {
        List<String> aliases = normalizeAliases(config.getProxyMessagingAliases());
        registerCommand(MESSAGE_COMMAND, aliases, this::createMessageCommand);
    }

    private BrigadierCommand createMessageCommand(String alias) {
        return new BrigadierCommand(BrigadierCommand.literalArgumentBuilder(alias)
                .then(BrigadierCommand.requiredArgumentBuilder("player", StringArgumentType.word())
                        .suggests((context, builder) -> {
                            suggestOnlinePlayers(context.getSource(), builder);
                            return builder.buildFuture();
                        })
                        .then(BrigadierCommand.requiredArgumentBuilder("message", StringArgumentType.greedyString())
                                .executes(context -> {
                                    if (!(context.getSource() instanceof Player)) {
                                        return 0;
                                    }
                                    Player sender = (Player) context.getSource();
                                    String targetName = StringArgumentType.getString(context, "player");
                                    String message = StringArgumentType.getString(context, "message");
                                    sendPrivateMessage(sender, targetName, message);
                                    return 1;
                                }))));
    }

    private void registerReplyCommand() {
        List<String> aliases = normalizeAliases(config.getProxyMessagingReplyAliases());
        registerCommand(REPLY_COMMAND, aliases, this::createReplyCommand);
    }

    private BrigadierCommand createReplyCommand(String alias) {
        return new BrigadierCommand(BrigadierCommand.literalArgumentBuilder(alias)
                .then(BrigadierCommand.requiredArgumentBuilder("message", StringArgumentType.greedyString())
                        .executes(context -> {
                            if (!(context.getSource() instanceof Player)) {
                                return 0;
                            }
                            Player sender = (Player) context.getSource();
                            String message = StringArgumentType.getString(context, "message");
                            sendReply(sender, message);
                            return 1;
                        })));
    }

    private void registerCommand(String commandName, List<String> aliases, java.util.function.Function<String, BrigadierCommand> commandFactory) {
        CommandManager commandManager = plugin.getProxy().getCommandManager();
        List<String> allAliases = new ArrayList<>();
        allAliases.add(commandName);
        allAliases.addAll(aliases);

        for (String alias : allAliases) {
            if (commandManager.hasCommand(alias)) {
                plugin.getLogger().warn("Proxy messaging alias '{}' is already registered. Skipping it.", alias);
                continue;
            }

            CommandMeta meta = commandManager.metaBuilder(alias)
                    .plugin(plugin)
                    .build();
            commandManager.register(meta, commandFactory.apply(alias));
            registeredAliases.add(alias);
        }
    }

    private void sendPrivateMessage(Player sender, String targetName, String message) {
        if (!hasPermission(sender, config.getProxyMessagingPermission())) {
            sender.sendMessage(config.parse(config.getProxyMessagingNoPermissionRaw()));
            return;
        }

        if (targetName == null || targetName.isBlank() || message == null || message.isBlank()) {
            sender.sendMessage(config.parse(config.getProxyMessagingUsageRaw()));
            return;
        }

        Optional<Player> targetOptional = plugin.getProxy().getPlayer(targetName);
        if (targetOptional.isEmpty()) {
            sender.sendMessage(config.parse(config.getProxyMessagingPlayerNotFoundRaw()));
            return;
        }

        Player target = targetOptional.get();
        if (target.getUniqueId().equals(sender.getUniqueId())) {
            sender.sendMessage(config.parse(config.getProxyMessagingSelfMessageRaw()));
            return;
        }

        deliverMessage(sender, target, message);
    }

    private void sendReply(Player sender, String message) {
        if (!hasPermission(sender, config.getProxyMessagingReplyPermission())) {
            sender.sendMessage(config.parse(config.getProxyMessagingNoPermissionRaw()));
            return;
        }

        if (message == null || message.isBlank()) {
            sender.sendMessage(config.parse(config.getProxyMessagingReplyUsageRaw()));
            return;
        }

        UUID targetUuid = lastReplyTargets.get(sender.getUniqueId());
        if (targetUuid == null) {
            sender.sendMessage(config.parse(config.getProxyMessagingNoReplyTargetRaw()));
            return;
        }

        Optional<Player> targetOptional = plugin.getProxy().getPlayer(targetUuid);
        if (targetOptional.isEmpty()) {
            sender.sendMessage(config.parse(config.getProxyMessagingPlayerNotFoundRaw()));
            return;
        }

        deliverMessage(sender, targetOptional.get(), message);
    }

    private void deliverMessage(Player sender, Player target, String message) {
        lastReplyTargets.put(sender.getUniqueId(), target.getUniqueId());
        lastReplyTargets.put(target.getUniqueId(), sender.getUniqueId());

        String safeSender = config.escape(sender.getUsername());
        String safeTarget = config.escape(target.getUsername());
        String safeMessage = config.escape(message);

        sender.sendMessage(config.parse(config.getProxyMessagingSentMessageRaw()
                .replace("{target}", safeTarget)
                .replace("{player}", safeSender)
                .replace("{message}", safeMessage)));
        target.sendMessage(config.parse(config.getProxyMessagingReceivedMessageRaw()
                .replace("{target}", safeTarget)
                .replace("{player}", safeSender)
                .replace("{message}", safeMessage)));
    }

    private void suggestOnlinePlayers(CommandSource source, com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
        String remaining = builder.getRemainingLowerCase();
        plugin.getProxy().getAllPlayers().stream()
                .filter(player -> !(source instanceof Player)
                        || !player.getUniqueId().equals(((Player) source).getUniqueId()))
                .map(Player::getUsername)
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(remaining))
                .forEach(builder::suggest);
    }

    private boolean hasPermission(Player player, String permission) {
        return permission == null || permission.isBlank() || player.hasPermission(permission);
    }

    private List<String> normalizeAliases(List<String> aliases) {
        List<String> normalized = new ArrayList<>();
        for (String alias : aliases) {
            if (alias == null) continue;
            String cleanAlias = alias.trim().toLowerCase(Locale.ROOT).replaceAll("^/+", "");
            if (!cleanAlias.isEmpty() && !normalized.contains(cleanAlias)) {
                normalized.add(cleanAlias);
            }
        }
        return normalized;
    }
}
