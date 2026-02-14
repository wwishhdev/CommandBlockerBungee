# Reporte de Auditoría de Seguridad: CommandBlocker

**Fecha:** 25 de Mayo de 2024
**Auditor:** Jules (AI Assistant)
**Objetivo:** Análisis exhaustivo de seguridad del código fuente (BungeeCord & Velocity).

## Resumen Ejecutivo

El proyecto `CommandBlocker` presenta una arquitectura sólida y moderna, utilizando las últimas versiones de librerías críticas como HikariCP y drivers SQL. La implementación de concurrencia para Webhooks y Base de Datos es correcta, evitando bloqueos del hilo principal.

Sin embargo, se ha detectado una vulnerabilidad de lógica potencial relacionada con la evasión de filtros (Bypass) mediante caracteres Unicode, y un problema de integridad en los logs.

## Hallazgos de Vulnerabilidades

### 1. Evasión de Bloqueo mediante Espacios Unicode (Medium)
**Ubicación:**
- `bungee/.../listeners/ChatListener.java` (Línea ~108)
- `velocity/.../listeners/ChatListener.java` (Línea ~108)

**Descripción:**
El método `isCommandBlocked` utiliza la expresión regular `\\s+` para separar el comando base de sus argumentos:
```java
String[] parts = cleanCommand.split("\\s+", 2);
```
En Java, `\\s` coincide con `[ \t\n\x0B\f\r]`, pero **no** coincide con otros espacios Unicode como el `\u00A0` (Non-Breaking Space). Si el servidor backend (Spigot/Paper) permite estos caracteres como separadores de argumentos, un usuario malintencionado puede evadir el bloqueo.

**Prueba de Concepto (Teórica):**
1. Comando bloqueado: `op`.
2. Atacante envía: `/op\u00A0me` (donde `\u00A0` es un espacio indivisible).
3. El plugin analiza `parts[0]` -> `"op\u00A0me"`.
4. `"op\u00A0me"` no es igual a `"op"`.
5. El plugin permite el comando.
6. El servidor backend recibe `/op\u00A0me`, lo interpreta como `/op me` y lo ejecuta.

**Recomendación:**
Utilizar la flag `(?U)` para habilitar el modo Unicode en la expresión regular, o definir explícitamente los separadores.
```java
// Opción 1: Regex Unicode
String[] parts = cleanCommand.split("(?U)\\s+", 2);
```

### 2. Alteración de Integridad en Logs y Notificaciones (Low)
**Ubicación:**
- `managers/ConfigManager.java` (Método `escape`)

**Descripción:**
El método `escape()` elimina todos los caracteres `&` del texto:
```java
return miniMessage.escapeTags(text.replace("&", ""));
```
Esto modifica el comando original antes de ser logueado o notificado.

**Impacto:**
- **Pérdida de Fidelidad:** Si un jugador escribe `/login user&pass`, los admins verán `/login userpass`.
- **Ofuscación:** Un atacante podría usar `&` estratégicamente para confundir a los sistemas de monitoreo de chat que buscan palabras clave exactas en los logs (si se basan en este output).

**Recomendación:**
No eliminar el caracter. Si el objetivo es prevenir códigos de color legacy, usar un método que solo elimine códigos de color válidos (`&[0-9a-fk-or]`) o simplemente escapar el ampersand si es necesario para MiniMessage.

### 3. Inyección de Markdown en Webhooks (Low / Cosmetic)
**Ubicación:**
- `managers/WebhookManager.java`

**Descripción:**
El nombre del jugador se sanitiza contra caracteres Markdown de Discord, pero el **comando** no.
```java
String safePlayer = req.playerName.replaceAll("([_`*~|])", "\\\\$1");
send(safePlayer, req.command); // req.command se envía crudo
```

**Impacto:**
Un usuario puede enviar comandos como `/op **spam**` para enviar mensajes con formato (negrita, cursiva, spoilers) al canal de Discord de administración. No afecta la seguridad del servidor, pero puede ser molesto.

**Recomendación:**
Aplicar la misma sanitización al comando:
```java
String safeCommand = req.command.replaceAll("([_`*~|])", "\\\\$1");
send(safePlayer, safeCommand);
```

## Buenas Prácticas Observadas

1.  **Protección contra SQL Injection:** Uso consistente de `PreparedStatement` y validación estricta (`Regex`) de prefijos de tablas.
2.  **Prevención de DoS:** La `ConcurrentLinkedQueue` en el `WebhookManager` está acotada a 100 elementos, previniendo agotamiento de memoria si Discord cae.
3.  **Concurrencia Correcta:** Las operaciones pesadas (I/O) se realizan en hilos separados (`CompletableFuture`), manteniendo el servidor fluido (sin lag).
4.  **Manejo de MiniMessage:** Se utiliza `miniMessage.escapeTags()` correctamente para prevenir inyección de componentes de chat interactivos.

## Estado de Dependencias
- Todas las dependencias principales (HikariCP, Drivers, Adventure) están actualizadas a versiones recientes y seguras.

---
**Conclusión:** El plugin es seguro para uso en producción, siempre y cuando se tenga en cuenta el riesgo potencial del bypass Unicode dependiendo de la configuración del servidor backend.
