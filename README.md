# CommandBlockerBungee & CommandBlockerVelocity

CommandBlocker blocks sensitive or unwanted commands at the proxy layer for BungeeCord and Velocity networks.

## Features

- Multi-platform support for BungeeCord and Velocity.
- Exact, wildcard, and regex command blocking.
- Alias protection for prefixed commands such as `minecraft:op`.
- Optional help-subcommand protection such as `/op help`.
- Allowed-command overrides.
- Per-server blocked command lists.
- Command attempt cooldowns and temporary timeouts.
- Optional SQLite or MySQL persistence.
- Optional Discord webhook alerts.
- Staff notifications with configurable click actions.
- Optional audit logs with daily rotation.
- Tab-complete whitelist mode.

## Requirements

- Java 21.
- Maven 3.8+ for building from source.
- BungeeCord-compatible proxy for `CommandBlockerBungee`.
- Velocity 3.3-compatible proxy for `CommandBlockerVelocity`.

## Build

Build and test both modules from the repository root:

```bash
mvn test
mvn package
```

The shaded JARs are generated at:

- `bungee/target/CommandBlockerBungee-2.4.0.jar`
- `velocity/target/CommandBlockerVelocity-2.4.0.jar`

## Installation

### BungeeCord

1. Build or download `CommandBlockerBungee-2.4.0.jar`.
2. Place it in the proxy `plugins` folder.
3. Restart the proxy.

### Velocity

1. Build or download `CommandBlockerVelocity-2.4.0.jar`.
2. Place it in the proxy `plugins` folder.
3. Restart the proxy.

## Commands

| Command | Aliases | Permission | Description |
|---|---|---|---|
| `/cblockerreload` | `/cbreload` | `commandblocker.reload` | Reloads configuration and runtime integrations. |
| `/cbstatus` | `/cbinfo` | `commandblocker.admin` | Shows current feature and runtime status. |

## Permissions

| Permission | Description | Default |
|---|---|---|
| `commandblocker.*` | Grants all CommandBlocker permissions. | OP |
| `commandblocker.bypass` | Full bypass for blocking and cooldown checks. | OP |
| `commandblocker.bypass.block` | Bypasses command blocking only. | OP |
| `commandblocker.bypass.cooldown` | Bypasses cooldown and timeout checks only. | OP |
| `commandblocker.reload` | Allows `/cblockerreload` and `/cbreload`. | OP |
| `commandblocker.notify` | Receives staff notifications. | OP |
| `commandblocker.admin` | Allows `/cbstatus` and `/cbinfo`. | OP |

## Configuration

The plugin creates `config.yml` on first startup.

Important sections:

- `blocked-commands`: global blocked command rules.
- `allowed-commands-settings`: commands that should always be allowed.
- `server-blocked-commands`: backend-server-specific rules.
- `cooldown`: attempt limits and timeout behavior.
- `database`: SQLite/MySQL persistence settings.
- `discord-webhook`: Discord alert delivery.
- `notification-actions`: clickable staff actions.
- `audit-log`: daily text log output and retention.
- `tab-complete-whitelist`: strict tab-complete filtering.

Command rules support:

```yaml
blocked-commands:
  - "op"
  - "wildcard:game*"
  - "regex:ban(ip|list)?"
```

## Testing Scope

The unit tests focus on the security-critical command matcher in both platform modules. They validate alias detection, Unicode invisible character stripping, wildcard/regex matching, server-specific rules, allowlist priority, invalid regex handling, and false-positive protection for regular command arguments.

Full proxy smoke testing still requires running the generated JAR on a real or staged BungeeCord/Velocity proxy because command event dispatch, permissions, and plugin lifecycle are owned by the proxy runtime.

## Statistics

This plugin uses bStats for anonymous usage statistics. You can opt out in the bStats configuration.

## License

This project is licensed under the MIT License.
