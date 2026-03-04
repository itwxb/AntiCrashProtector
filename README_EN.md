# AntiCrashProtector

A high-performance, robust data protection and crash prevention plugin for Minecraft servers (1.21+).

---

## ✨ Key Features

*   🛡️ **Triple-Layer Protection System**: **v1.2.0 Refactored!** Packet interception + Player quit check + Periodic monitoring for comprehensive protection.
*   📦 **Attribute Packet Interception**: **v1.2.0 New!** Checks and repairs corrupted data before the server sends attribute update packets, preventing crashes caused by MMO plugins.
*   🚀 **Player Quit Pre-Check**: **v1.2.0 New!** Checks and repairs attributes before a player disconnects, preventing crashes during the disconnection process.
*   🔬 **Deep Attribute Modifier Repair**: Not only checks attribute values but also uses reflection to deeply repair `fastutil ObjectOpenHashSet` with `wrapped is null` corruption.
*   🛡️ **Fail-Safe Protection**: Uses a mainstream fail-safe mode where the plugin automatically uses safe default values if configuration keys are missing, ensuring uninterrupted defense.
*   🚀 **Command Protection**: Intercepts high-risk teleportation commands (tp, home, back, etc.), performing deep safety checks before execution.
*   🚑 **Player Data Monitor**: Periodically scans online players for data corruption (NaN coordinates, illegal attributes, invalid potion effects, and invalid vehicle states).
*   ⚖️ **Tiered Repair System**: Fixes minor issues (attributes/potions) in-place without disrupting the player. Severe issues (corrupted coordinates) safely teleport the player to spawn.
*   ⏲️ **High-Performance Load Distribution**: Uses a distributed tick strategy, checking only 5 players per tick with ~1% performance impact, suitable for 100+ player servers.
*   📝 **Black-Box Logging**: All interception behaviors and detection details are synchronized to both the console and the plugin's exclusive log file.
*   🌍 **Full Customization**: Supports complete customization of all messages, including diagnostic reports and repair suggestions.

---

## 🎯 Scope & Technical Boundaries

To ensure transparency and professional expectations, please note the following:

1.  **Primary Focus**: This plugin is specifically designed to intercept and repair **Main Thread Hangs or Packet-level NPE Crashes** caused by corrupted player data (Attributes, Coordinates, Potions, and Vehicles).
2.  **Out of Scope**: It cannot resolve crashes caused by plugin conflicts, mod logic errors, Out of Memory (OOM) errors, or network-level attacks (DDoS).
3.  **Compatibility**: Optimized for Paper/Purpur/Spigot 1.21.x. Requires ProtocolLib (soft dependency) for packet-level protection.
4.  **Repair Philosophy**: We prioritize "Data Integrity". If data cannot be safely repaired and `kick-if-unrepairable` is enabled, the player will be disconnected to prevent the corrupted data from repeatedly crashing the server.

---

## 📋 Commands & Permissions

| Command | Description | Permission |
| :--- | :--- | :--- |
| `/anticrash status` | View detailed plugin status and active protection checks | `anticrash.admin` |
| `/anticrash reload` | Reload configuration | `anticrash.admin` |
| `/anticrash safety` | Toggle command protection on/off | `anticrash.admin` |
| `/anticrash check` | Manually trigger a global player scan | `anticrash.admin` |
| `/anticrash repair` | Manually repair your own data state | `anticrash.admin` |

---

## ⚙️ Configuration (config.yml)

```yaml
# AntiCrashProtector Configuration
# Version: 1.2.1

# Global toggle
enabled: true
debug-mode: false

# Packet Interception Settings (Requires ProtocolLib)
# Checks and repairs corrupted data before sending attribute packets
# Core feature for preventing MMO plugin crashes
packet-interception:
  enabled: true

monitoring:
  enabled: true
  # Check interval (ticks), recommended: 20-100
  # 20 = check every second, 100 = check every 5 seconds
  check-interval: 20
  checks:
    location: true
    attributes: true
    effects: true
    inventory: true
    vehicle: true

# Repair thresholds
repair:
  thresholds:
    coordinate-max: 30000000.0
    y-min: -64
    y-max: 320
    health-max: 2048.0
    health-min: 0.1
    speed-max: 1.0
    speed-min: 0.0
    damage-max: 2048.0
    damage-min: 0.0
```

---

## 📥 Installation

1. **Install ProtocolLib** (Recommended): Download [ProtocolLib](https://www.spigotmc.org/resources/protocollib.1997/) and place it in the `plugins` folder.
2. Place `AntiCrashProtector-1.2.1.jar` in your server's `plugins` folder.
3. Restart the server.
4. Use `/anticrash status` to confirm all modules are active:
   - Packet Interception: Running
   - Auto Monitor: Running
   - Command Interception: Running

---

## 📋 Update Log

### v1.2.1 (2026-03-05)

**Hotfix: Teleport Crash Prevention**

- **🚀 Pre-teleport Attribute Check**: Added `PlayerTeleportEvent` listener to check and repair attributes before teleportation, preventing crashes caused by `/tpa`, `/tpaccept`, and other teleport commands.
- **🐛 Bug Fix**: Previously, `/tpaccept` only checked the executor, not the teleport recipient, causing crashes when the recipient had corrupted attributes.

### v1.2.0 (2026-03-04)

**Major Update: Triple-Layer Protection System Refactor**

- **🛡️ Attribute Packet Interception**: Added `AttributePacketInterceptor` module that checks and repairs corrupted data before the server sends attribute update packets, preventing MMO plugin crashes at the source.
- **🚀 Player Quit Pre-Check**: Checks and repairs attributes before a player disconnects, preventing crashes during the disconnection process.
- **🔬 Reflection Deep Repair**: Uses Java reflection to directly repair NMS internal corrupted `fastutil ObjectOpenHashSet`, clearing or nullifying corrupted modifier collections.
- **📊 Status Command Enhancement**: `/anticrash status` now displays the packet interceptor status.
- **⚡ Performance Optimization**: Check interval changed from 100 ticks to 20 ticks for faster detection and repair.
- **📦 ProtocolLib Integration**: Added ProtocolLib soft dependency for packet-level protection.

### v1.1.3 (2026-02-19)

- **Deep Attribute Modifier Check**: Added deep inspection of attribute modifier collection internal structure, proactively detecting `fastutil ObjectOpenHashSet` `wrapped is null` corruption.
- **Attribute Deep Repair**: Added `repairCorruptedAttributes()` method that automatically clears all modifiers and resets attributes to default values when corruption is detected.
- **Tiered Repair Optimization**: All attribute-related issues are repaired in-place without teleporting the player.
- **Exception Capture Enhancement**: Added multi-layer NPE capture to safely handle any internal exceptions during attribute checks.
- **Log Format Optimization**: Improved repair log output format with clear descriptions for easier troubleshooting.

### v1.1.2

- **Black-Box Logging**: Rebuilt the logging system. Detailed anomaly detection info is now synchronized to both the console and log files for precise troubleshooting.
- **Fail-Safe Mode**: Reverted to mainstream style using default values instead of strict configuration checks.
- **Full Attribute Control**: Min/Max thresholds for Health, Speed, and Damage are now fully exposed in `config.yml`.
- **Vehicle Integrity Check**: Added monitoring for invalid vehicle states to prevent server ticking exceptions.

---

Developed with ❤️ for the Minecraft Community.
