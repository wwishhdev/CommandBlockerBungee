# CommandBlockerBungee

A BungeeCord plugin to block commands across your network.

## Features

- Block specific commands
- Alias detection system
- Cooldown system
- Staff notifications
- Configurable messages
- Permission system

## Permissions

- `commandblocker.bypass` - Allows using blocked commands
- `commandblocker.reload` - Allows reloading the plugin
- `commandblocker.notify` - Receives notifications when someone tries to use blocked commands
- `commandblocker.*` - Grants all permissions

## Configuration

See [config.yml](src/main/resources/config.yml) for detailed configuration options.

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Note

This plugin uses bStats to collect anonymous usage statistics. You can opt-out by disabling it in the bStats configuration.
