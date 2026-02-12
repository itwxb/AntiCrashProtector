package com.anticrash;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/**
 * 指令拦截器
 * 负责在玩家执行高危指令前进行安全检查，并提供延迟安全执行机制
 */
public class CommandInterceptor implements Listener {

    private final AntiCrashPlugin plugin;
    private final Set<String> protectedCommands;
    private final Set<UUID> processingPlayers;
    private long delayTicks;
    private long cooldownMs;
    private final Map<UUID, Long> lastCommandTime;
    
    /**
     * 安全检查结果封装
     */
    private static class SafetyResult {
        private final boolean allow;
        private final boolean messaged;

        private SafetyResult(boolean allow, boolean messaged) {
            this.allow = allow;
            this.messaged = messaged;
        }

        private static SafetyResult allow() {
            return new SafetyResult(true, false);
        }

        private static SafetyResult blocked(boolean messaged) {
            return new SafetyResult(false, messaged);
        }
    }

    public CommandInterceptor(AntiCrashPlugin plugin) {
        this.plugin = plugin;
        this.processingPlayers = new HashSet<>();
        this.protectedCommands = new HashSet<>();
        this.lastCommandTime = new HashMap<>();
        
        loadConfig();
        
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void loadConfig() {
        protectedCommands.clear();
        List<String> configCommands = plugin.getConfig().getStringList("command-protection.protected-commands");
        for (String cmd : configCommands) {
            // 规范化指令格式，确保包含斜杠且为小写
            String normalized = cmd.startsWith("/") ? cmd.toLowerCase() : "/" + cmd.toLowerCase();
            protectedCommands.add(normalized);
        }
        
        // 现在这些自定义设置也可以热重载了！
        this.delayTicks = plugin.getConfig().getLong("command-protection.delay-ticks", 2L);
        this.cooldownMs = plugin.getConfig().getLong("command-protection.cooldown", 1000L);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // 清理缓存，防止内存泄漏
        UUID uuid = event.getPlayer().getUniqueId();
        processingPlayers.remove(uuid);
        lastCommandTime.remove(uuid);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCommandPreprocess(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        String message = event.getMessage();
        
        // 提取指令主名称（例如 /tp x y z -> /tp）
        String[] parts = message.split(" ");
        if (parts.length == 0) return;
        String command = parts[0].toLowerCase();

        // 0. 检查是否正在处理中（白名单机制，防止无限递归）
        if (processingPlayers.contains(player.getUniqueId())) {
            return;
        }

        // 1. 检查该指令是否在保护名单中
        if (isProtectedCommand(command)) {
            plugin.debugLog("检测到高危指令: " + command + " (玩家: " + player.getName() + ")");
            
            // 权限绕过检查：拥有绕过权限的玩家不触发保护
            if (player.hasPermission("anticrash.bypass.protection")) {
                return;
            }
            
            // 频率限制（冷却检查）：防止指令注入攻击
            long now = System.currentTimeMillis();
            if (lastCommandTime.containsKey(player.getUniqueId())) {
                long lastTime = lastCommandTime.get(player.getUniqueId());
                if (now - lastTime < cooldownMs) {
                    event.setCancelled(true);
                    return;
                }
            }
            lastCommandTime.put(player.getUniqueId(), now);

            // 2. 执行深度安全检查 (包含坐标、生命值、属性等)
            SafetyResult result = performSafetyCheck(player);
            if (!result.allow) {
                if (!result.messaged) {
                    player.sendMessage(plugin.getPrefixedMessage("command-blocked"));
                }
                event.setCancelled(true);
                return;
            }

            // 3. 拦截原事件，转入安全执行流程（带临时抗性保护和延迟执行）
            event.setCancelled(true);
            player.sendMessage(plugin.getPrefixedMessage("command-processing"));
            executeCommandSafely(player, message);
        }
    }

    private boolean isProtectedCommand(String command) {
        return protectedCommands.contains(command);
    }

    private SafetyResult performSafetyCheck(Player player) {
        try {
            if (player == null || !player.isOnline()) return SafetyResult.blocked(false);
            
            // 玩家数据完整性检查 + 必要修复
            if (PlayerMonitor.getInstance() != null) {
                boolean allow = PlayerMonitor.getInstance().diagnoseAndRepairForCommand(player);
                return allow ? SafetyResult.allow() : SafetyResult.blocked(true);
            }

            Location loc = player.getLocation();
            if (!isValidDouble(loc.getX()) || !isValidDouble(loc.getY()) || !isValidDouble(loc.getZ())) {
                logWarn("指令前置检查发现坐标异常(NaN): 玩家=" + player.getName() + " X=" + loc.getX() + " Y=" + loc.getY() + " Z=" + loc.getZ());
                return SafetyResult.blocked(false);
            }
            
            int minY = plugin.getConfig().getInt("repair.thresholds.y-min", -64);
            int maxY = plugin.getConfig().getInt("repair.thresholds.y-max", 320);
            if (loc.getY() < minY || loc.getY() > maxY) {
                logWarn("指令前置检查发现坐标越界: 玩家=" + player.getName() + " Y=" + loc.getY() + " 范围=[" + minY + "," + maxY + "]");
                return SafetyResult.blocked(false);
            }

            return SafetyResult.allow();
        } catch (Exception e) {
            logWarn("安全检查发生内部异常: 玩家=" + player.getName() + " 异常=" + e.getClass().getSimpleName() + " 信息=" + e.getMessage());
            return SafetyResult.blocked(false);
        }
    }

    private void logWarn(String message) {
        plugin.getLogger().warning(message);
        plugin.getLogManager().log("WARN", message);
    }

    private void logError(String message, Throwable e) {
        plugin.getLogger().log(Level.SEVERE, message, e);
        plugin.getLogManager().log("ERROR", message);
    }
    
    private boolean isValidDouble(double d) {
        return Double.isFinite(d) && !Double.isNaN(d);
    }

    private void executeCommandSafely(Player player, String commandLine) {
        // 步骤1：应用临时保护效果
        try {
            player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 60, 255, true, false));
        } catch (Exception e) {
            // 忽略药水效果错误，不影响核心功能
        }

        // 步骤2：延迟执行
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) return;

                try {
                    // 关键：加入白名单
                    processingPlayers.add(player.getUniqueId());
                    
                    // 执行命令
                    if (commandLine.startsWith("/")) {
                        player.performCommand(commandLine.substring(1));
                    } else {
                        player.performCommand(commandLine);
                    }
                    
                    plugin.debugLog("指令安全执行完成: " + player.getName() + " -> " + commandLine);
                    
                } catch (Exception e) {
                    String errorMsg = "命令安全执行失败: 玩家=" + player.getName() + " 指令=" + commandLine + " 异常=" + e.getMessage();
                    logError(errorMsg, e);
                    player.sendMessage(plugin.getPrefixedMessage("command-error"));
                    
                    if (plugin.getConfig().getBoolean("repair.safe-teleport", true)) {
                        player.teleport(player.getWorld().getSpawnLocation());
                    }
                } finally {
                    // 步骤3：立即移除白名单
                    processingPlayers.remove(player.getUniqueId());
                }
            }
        }.runTaskLater(plugin, delayTicks);
    }
}
