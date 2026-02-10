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
- **Staff Notifications**:
  - Alert specific permission groups when players try to use blocked commands.
  - Notifications for cooldown violations.
- **Highly Configurable**:
  - Custom messages with **MiniMessage** support (gradients, rgb, etc.) and legacy '&' color codes.
  - Toggles for almost every feature.

## 📥 Installation

### BungeeCord
1. Download `CommandBlockerBungee-2.1.0.jar`.
2. Place the jar file in your `plugins` folder.
3. Restart your proxy.

### Velocity
1. Download `CommandBlockerVelocity-2.1.0.jar`.
2. Place the jar file in your `plugins` folder.
3. Restart your proxy.

## ⚙️ Configuration

The `config.yml` is generated automatically. Here is a brief overview:

```yaml
blocked-commands:
  - "op"
  - "pl"
  - "plugins"

alias-detection:
  enabled: true
  block-plugin-prefix: true  # Blocks minecraft:op, etc.
  block-help-subcommand: true # Blocks /op help

cooldown:
  enabled: true
  max-attempts: 3
  timeout-duration: 300 # Seconds
  reset-after: 600      # Seconds

notifications:
  enabled: true
  permission: "commandblocker.notify"
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
