# CommandBlockerBungee & Velocity

An advanced and efficient plugin to block commands across your entire network, supporting both **BungeeCord** and **Velocity**.

## 🚀 Features

- **Multi-Platform Support**: Works seamlessly on both BungeeCord and Velocity.
- **Advanced Blocking System**:
  - Block specific commands (with case-insensitivity).
  - **Alias Detection**: specific checks for `minecraft:op` or `plugins:pl` syntax.
  - **Subcommand Blocking**: Option to block specific subcommands like `/op help`.
- **Smart Cooldowns**:
  - Prevent command spamming with a configurable cooldown system.
  - Timeout players who exceed maximum attempts.
  - Auto-reset cooldowns after a set period.
- **Database Support**:
  - **SQLite**: No setup required, works out of the box.
  - **MySQL**: High-performance support for networks that need data persistence across restarts.
- **Discord Integration**:
  - Send real-time alerts to a Discord channel via Webhooks when players try to use blocked commands.
  - Highly customizable messages and embed styles.
- **Interactive Staff Notifications**:
  - Alerts are not just text; they are clickable!
  - Staff can **Kick**, **Ban**, or **Temp-Ban** offenders directly from the chat notification.
- **Highly Configurable**:
  - Custom messages with **MiniMessage** support (gradients, rgb, etc.) and legacy '&' color codes.
  - Toggles for almost every feature.

## 📥 Installation

### BungeeCord
1. Download `CommandBlockerBungee-2.1.3.jar`.
2. Place the jar file in your `plugins` folder.
3. Restart your proxy.

### Velocity
1. Download `CommandBlockerVelocity-2.1.3.jar`.
2. Place the jar file in your `plugins` folder.
3. Restart your proxy.

## ⚙️ Configuration

The `config.yml` is generated automatically. Here is a brief overview of the new capabilities:

```yaml
blocked-commands:
  - "op"
  - "pl"
  - "plugins"

# ... (Basic blocking settings) ...

# NEW: Database Support
database:
  enabled: true
  type: "sqlite" # or "mysql"
  # Connection details for MySQL...

# NEW: Discord Integration
discord-webhook:
  enabled: true
  url: "YOUR_WEBHOOK_URL"
  username: "CommandBlocker"
  content: "**{player}** tried to use blocked command: `{command}`"

# NEW: Interactive Actions in Chat
notification-actions:
  enabled: true
  actions:
    - label: " [KICK]"
      command: "/kick {player} Blocked Commands"
```

## 🔒 Permissions

| Permission | Description | Default |
|---|---|---|
| `commandblocker.admin` | Grants all permissions | OP |
| `commandblocker.bypass` | Bypass command blocking | OP |
| `commandblocker.reload` | Access to `/cbreload` | OP |
| `commandblocker.notify` | Receive staff notifications | OP |

## 🛠 Commands

- `/cbreload` (or `/commandblocker.reload`): Reloads the configuration file without restarting the server.

## 📊 Statistics

This plugin uses **bStats** to collect anonymous usage statistics. This helps me understand how the plugin is used and improve it. You can opt-out by disabling it in the bStats configuration.

## 🤝 Support

If you have any issues or suggestions, please join our [Discord server](https://discord.gg/m8V9pns6dB) or open an issue on the repository.

## ⚖️ License

This project is licensed under the **MIT License**.
