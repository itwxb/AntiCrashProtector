# AntiCrashProtector

A high-performance, robust data protection and crash prevention plugin for Minecraft servers (1.21+).

---

## ✨ Key Features

*   🛡️ **Fail-Safe Protection**: **v1.1.2 Optimized!** Uses a mainstream fail-safe mode where the plugin automatically uses safe default values if configuration keys are missing, ensuring uninterrupted defense.
*   🚀 **Command Protection**: Intercepts high-risk teleportation commands (tp, home, back, etc.), performing deep safety checks before execution.
*   🚑 **Player Data Monitor**: Periodically scans online players for data corruption (NaN coordinates, illegal attributes, invalid potion effects, and **v1.1.2 New!** invalid vehicle states).
*   ⚖️ **Tiered Repair System**: Fixes minor issues (attributes/potions) in-place without disrupting the player. Severe issues (corrupted coordinates) safely teleport the player to spawn.
*   📝 **Black-Box Logging**: All interception behaviors and detection details (including abnormal coordinates, illegal attribute values, potion levels, etc.) are synchronized to both the console and the plugin's exclusive log file. Server owners can precisely troubleshoot the root cause just by checking the logs.
*   🛠️ **Customizable Thresholds**: Fully configurable min/max bounds for health, movement speed, and attack damage to prevent overflow-related crashes.

---

## 🎯 Scope & Technical Boundaries

To ensure transparency and professional expectations, please note the following:

1.  **Primary Focus**: This plugin is specifically designed to intercept and repair **Main Thread Hangs or Packet-level NPE Crashes** caused by corrupted player data (Attributes, Coordinates, Potions, and Vehicles).
2.  **Out of Scope**: It cannot resolve crashes caused by plugin conflicts, mod logic errors (e.g., ticking errors within a mod's custom entity), Out of Memory (OOM) errors, or network-level attacks (DDoS).
3.  **Compatibility**: Optimized for Paper/Purpur/Spigot 1.21.x. As the Minecraft attribute system evolves, we recommend staying on the latest version for the best protection.
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
# Version: 1.1.2

# Global toggle
enabled: true
debug-mode: false

monitoring:
  enabled: true
  checks:
    location: true
    attributes: true
    effects: true
    inventory: true
    vehicle: true     # Check for invalid vehicle states

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

## 📋 Update Log

### v1.1.2
- **Black-Box Logging**: Rebuilt the logging system. Detailed anomaly detection info (coordinates, attributes, potions, etc.) is now synchronized to both the console and log files for precise troubleshooting.
- **Fail-Safe Mode**: Reverted to mainstream style using default values instead of strict configuration checks. More robust for end-users and cleaner code.
- **Full Attribute Control**: Min/Max thresholds for Health, Speed, and Damage are now fully exposed in `config.yml`.
- **Vehicle Integrity Check**: Added monitoring for invalid vehicle states to prevent server ticking exceptions.
- **Optimized Architecture**: Cleaned up redundant configuration validation logic for better performance.

### v1.1.1
- **Hot-Reload Support**: All configuration changes now take effect immediately with `/anticrash reload`.
- **Command Defense+**: Added protection for `tpaccept`, `tpahere`, and `tpyes`.
- **Death Logic Fix**: Fixed a bug where dying players were incorrectly flagged as "corrupted".

### v1.1.0
- **Tiered Repair**: Introduced in-place repairs for minor data anomalies.
- **Config Auto-Migration**: Automatically handles config updates between versions.

---
Developed with ❤️ for the Minecraft Community.
