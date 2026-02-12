package com.anticrash;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 插件指令处理器
 * 处理 /anticrash 及其子指令，如 reload, status, check, safety, repair
 */
public class AntiCrashCommand implements CommandExecutor, TabCompleter {

    private final AntiCrashPlugin plugin;

    public AntiCrashCommand(AntiCrashPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // 权限检查：只有拥有 anticrash.admin 权限的玩家或控制台可以执行
        if (!sender.hasPermission("anticrash.admin")) {
            sender.sendMessage(plugin.getPrefixedMessage("no-permission"));
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        // 指令分支处理
        switch (args[0].toLowerCase()) {
            case "reload":
                plugin.updateConfigFile(); // 升级配置
                plugin.reloadConfig();     // 重新读取文件
                plugin.applyConfig(true);  // 应用新设置
                sender.sendMessage(plugin.getPrefixedMessage("reload-success"));
                // Reload 成功后，自动显示状态，方便确认配置是否生效
                sendStatus(sender);
                break;
            case "status":
                sendStatus(sender);
                break;
            case "check":
                if (plugin.getPlayerMonitor() != null) {
                    sender.sendMessage(plugin.getPrefixedMessage("manual-check-start"));
                    plugin.getPlayerMonitor().checkAllPlayers();
                    sender.sendMessage(plugin.getPrefixedMessage("manual-check-finish"));
                } else {
                    sender.sendMessage(plugin.getPrefixedMessage("monitor-not-enabled"));
                }
                break;
            case "safety":
                toggleSafety(sender); // 快速切换指令保护开关
                break;
            case "repair":
                handleManualRepair(sender); // 手动触发自我修复
                break;
            default:
                sendHelp(sender);
                break;
        }

        return true;
    }

    private void handleManualRepair(CommandSender sender) {
        if (sender instanceof Player) {
            Player p = (Player) sender;
            if (plugin.getPlayerMonitor() != null) {
                plugin.getPlayerMonitor().checkSinglePlayer(p, true);
            } else {
                sender.sendMessage(plugin.getPrefixedMessage("monitor-not-enabled"));
            }
        } else {
            sender.sendMessage(plugin.getPrefixedMessage("only-player"));
        }
    }

    private void toggleSafety(CommandSender sender) {
        boolean current = plugin.getConfig().getBoolean("command-protection.enabled");
        boolean newState = !current;
        plugin.getConfig().set("command-protection.enabled", newState);
        plugin.saveConfig();
        
        String stateText = newState ? ChatColor.GREEN + "开启" : ChatColor.RED + "关闭";
        sender.sendMessage(plugin.getPrefixedMessage("safety-toggle").replace("{state}", stateText));
        sender.sendMessage(plugin.getPrefixedMessage("safety-reboot"));
    }

    private void sendStatus(CommandSender sender) {
        sender.sendMessage(ChatColor.GREEN + "=== AntiCrashProtector 状态详情 ===");
        sender.sendMessage(ChatColor.GRAY + "插件版本: " + ChatColor.WHITE + plugin.getDescription().getVersion());
        
        // 总开关
        boolean isEnabled = plugin.getConfig().getBoolean("enabled", true);
        boolean debugMode = plugin.getConfig().getBoolean("debug-mode", false);
        sender.sendMessage(ChatColor.GRAY + "总开关: " + (isEnabled ? ChatColor.GREEN + "开启" : ChatColor.RED + "关闭") + 
                          ChatColor.GRAY + " | 调试模式: " + (debugMode ? ChatColor.GREEN + "开启" : ChatColor.YELLOW + "关闭"));

        // 指令拦截模块
        boolean cmdProtectEnabled = plugin.getConfig().getBoolean("command-protection.enabled", true);
        boolean isCommandInterceptorRunning = plugin.getCommandInterceptor() != null;
        
        String cmdStatusText;
        if (!cmdProtectEnabled) {
            cmdStatusText = ChatColor.RED + "未运行 (配置已关闭)";
        } else if (isCommandInterceptorRunning) {
            cmdStatusText = ChatColor.GREEN + "运行中";
        } else {
            cmdStatusText = ChatColor.YELLOW + "异常 (配置开启但未运行)";
        }
        sender.sendMessage(ChatColor.GRAY + "指令拦截: " + cmdStatusText);
        
        if (isCommandInterceptorRunning) {
            long cooldown = plugin.getConfig().getLong("command-protection.cooldown", 1000);
            long delay = plugin.getConfig().getLong("command-protection.delay-ticks", 20);
            List<String> cmds = plugin.getConfig().getStringList("command-protection.protected-commands");
            
            sender.sendMessage(ChatColor.DARK_GRAY + "  - 冷却时间: " + ChatColor.AQUA + cooldown + "ms");
            sender.sendMessage(ChatColor.DARK_GRAY + "  - 延迟执行: " + ChatColor.AQUA + delay + " ticks");
            sender.sendMessage(ChatColor.DARK_GRAY + "  - 保护指令 (" + cmds.size() + "个): " + ChatColor.WHITE + String.join(", ", cmds));
        }

        // 自动监控模块
        boolean monitorEnabled = plugin.getConfig().getBoolean("monitoring.enabled", true);
        boolean isMonitoringRunning = plugin.getPlayerMonitor() != null && plugin.getPlayerMonitor().isMonitoring();
        
        String monitorStatusText;
        if (!monitorEnabled) {
            monitorStatusText = ChatColor.RED + "未运行 (配置已关闭)";
        } else if (isMonitoringRunning) {
            monitorStatusText = ChatColor.GREEN + "运行中";
        } else {
            monitorStatusText = ChatColor.YELLOW + "异常 (配置开启但未运行)";
        }
        sender.sendMessage(ChatColor.GRAY + "自动监控: " + monitorStatusText);
        
        if (isMonitoringRunning) {
             long interval = plugin.getConfig().getLong("monitoring.check-interval", 100);
             sender.sendMessage(ChatColor.DARK_GRAY + "  - 扫描频率: " + ChatColor.AQUA + interval + " ticks/次");
             sender.sendMessage(ChatColor.DARK_GRAY + "  - 监控检查项: " + getCheckList());
             
             // 阈值显示
             int minY = plugin.getConfig().getInt("repair.thresholds.y-min", -64);
             int maxY = plugin.getConfig().getInt("repair.thresholds.y-max", 320);
             sender.sendMessage(ChatColor.DARK_GRAY + "  - 坐标阈值: " + ChatColor.AQUA + "Y[" + minY + " ~ " + maxY + "]");

             double minHealth = plugin.getConfig().getDouble("repair.thresholds.health-min", 0.1);
             double maxHealth = plugin.getConfig().getDouble("repair.thresholds.health-max", 2048.0);
             sender.sendMessage(ChatColor.DARK_GRAY + "  - 生命阈值: " + ChatColor.AQUA + minHealth + " ~ " + maxHealth);

             double speedMin = plugin.getConfig().getDouble("repair.thresholds.speed-min", 0.0);
             double speedMax = plugin.getConfig().getDouble("repair.thresholds.speed-max", 1.0);
             sender.sendMessage(ChatColor.DARK_GRAY + "  - 速度范围: " + ChatColor.AQUA + speedMin + " ~ " + speedMax);

             double damageMin = plugin.getConfig().getDouble("repair.thresholds.damage-min", 0.0);
             double damageMax = plugin.getConfig().getDouble("repair.thresholds.damage-max", 2048.0);
             sender.sendMessage(ChatColor.DARK_GRAY + "  - 伤害范围: " + ChatColor.AQUA + damageMin + " ~ " + damageMax);
             
             // 修复策略
             boolean autoRepair = plugin.getConfig().getBoolean("repair.auto-repair", true);
             boolean safeTeleport = plugin.getConfig().getBoolean("repair.safe-teleport", true);
             boolean blockOnSevere = plugin.getConfig().getBoolean("repair.block-command-on-severe", true);
             boolean kickUnrepairable = plugin.getConfig().getBoolean("repair.kick-if-unrepairable", false);

             sender.sendMessage(ChatColor.GRAY + "修复策略:");
             sender.sendMessage(ChatColor.DARK_GRAY + "  - 自动修复: " + (autoRepair ? ChatColor.GREEN + "开启" : ChatColor.RED + "关闭 (仅报告)"));
             sender.sendMessage(ChatColor.DARK_GRAY + "  - 安全传送: " + (safeTeleport ? ChatColor.GREEN + "开启" : ChatColor.RED + "关闭"));
             
             if (safeTeleport) {
                 boolean tpSevereOnly = plugin.getConfig().getBoolean("repair.teleport-on-severe-only", true);
                 boolean preferSafeLoc = plugin.getConfig().getBoolean("repair.prefer-last-safe-location", true);
                 sender.sendMessage(ChatColor.DARK_GRAY + "    - 仅严重异常时传送: " + (tpSevereOnly ? ChatColor.GREEN + "是" : ChatColor.YELLOW + "否 (始终传送)"));
                 sender.sendMessage(ChatColor.DARK_GRAY + "    - 优先回退安全位置: " + (preferSafeLoc ? ChatColor.GREEN + "是" : ChatColor.YELLOW + "否 (传送到出生点)"));
             }

             sender.sendMessage(ChatColor.DARK_GRAY + "  - 严重异常阻止指令: " + (blockOnSevere ? ChatColor.GREEN + "开启" : ChatColor.RED + "关闭"));
             sender.sendMessage(ChatColor.DARK_GRAY + "  - 无法修复时踢出: " + (kickUnrepairable ? ChatColor.GREEN + "是" : ChatColor.RED + "否"));
        }
        sender.sendMessage(ChatColor.GREEN + "==================================");
    }

    private String getCheckList() {
        List<String> checks = new ArrayList<>();
        // 生命值检查是硬编码在核心逻辑中的，始终开启
        checks.add("生命值");
        if (plugin.getConfig().getBoolean("monitoring.checks.location", true)) checks.add("坐标");
        if (plugin.getConfig().getBoolean("monitoring.checks.attributes", true)) checks.add("核心属性(全)");
        if (plugin.getConfig().getBoolean("monitoring.checks.effects", true)) checks.add("药水效果");
        if (plugin.getConfig().getBoolean("monitoring.checks.inventory", true)) checks.add("物品栏");
        if (plugin.getConfig().getBoolean("monitoring.checks.vehicle", true)) checks.add("载具");
        return String.join(", ", checks);
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== AntiCrashProtector 帮助 ===");
        sender.sendMessage(ChatColor.YELLOW + "/anticrash reload " + ChatColor.WHITE + "- 重载配置文件");
        sender.sendMessage(ChatColor.YELLOW + "/anticrash status " + ChatColor.WHITE + "- 查看插件状态");
        sender.sendMessage(ChatColor.YELLOW + "/anticrash check " + ChatColor.WHITE + "- 手动检查所有玩家");
        sender.sendMessage(ChatColor.YELLOW + "/anticrash safety " + ChatColor.WHITE + "- 切换安全模式");
        sender.sendMessage(ChatColor.YELLOW + "/anticrash repair " + ChatColor.WHITE + "- 尝试自我修复");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> subCommands = Arrays.asList("reload", "status", "check", "safety", "repair");
            return subCommands.stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return new ArrayList<>();
    }
}
