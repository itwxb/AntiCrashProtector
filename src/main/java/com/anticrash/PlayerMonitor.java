package com.anticrash;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * 玩家状态监控核心类
 * 负责定期检查在线玩家的数据完整性，并执行分级修复逻辑
 */
public class PlayerMonitor implements Listener {
    
    private static PlayerMonitor instance;
    private final AntiCrashPlugin plugin;
    private BukkitTask monitorTask;
    private long checkInterval;
    private final Map<UUID, Location> lastSafeLocations;

    /**
     * 修复场景：自动监控、手动指令、拦截指令前置检查
     */
    private enum RepairContext {
        AUTO,
        MANUAL,
        COMMAND
    }

    /**
     * 异常类型：用于分级处理和详细提示
     */
    private enum IssueType {
        LOCATION,   // 坐标异常 (NaN/越界)
        HEALTH,     // 生命值异常
        ATTRIBUTES, // 属性异常 (非法值)
        EFFECTS,    // 药水效果异常
        INVENTORY,  // 物品栏异常
        GENERAL     // 其他未知异常
    }

    private static class Diagnosis {
        private final EnumSet<IssueType> issues = EnumSet.noneOf(IssueType.class);
        private boolean severe; // 是否属于严重异常（需要传送修复）

        private boolean hasIssues() {
            return !issues.isEmpty();
        }
    }

    public PlayerMonitor(AntiCrashPlugin plugin) {
        this.plugin = plugin;
        instance = this;
        this.lastSafeLocations = new HashMap<>();
        // 注册监听器以清理内存
        Bukkit.getPluginManager().registerEvents(this, plugin);
        loadConfig();
    }

    public void loadConfig() {
        this.checkInterval = plugin.getConfig().getLong("monitoring.check-interval", 100L);
        if (isMonitoring()) {
            startMonitoring();
        }
    }

    @EventHandler
    public void onPlayerJoin(org.bukkit.event.player.PlayerJoinEvent event) {
        // 玩家进服时立即检查，防止“登录即崩”的死循环
        // 延迟 1 tick 执行，确保玩家数据已完全加载
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (event.getPlayer().isOnline()) {
                checkSinglePlayer(event.getPlayer(), false);
            }
        });
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // 玩家退出时清理缓存，防止内存泄漏
        lastSafeLocations.remove(event.getPlayer().getUniqueId());
    }

    public static PlayerMonitor getInstance() {
        return instance;
    }

    /**
     * 启动监控任务：采用分摊负载算法，每 tick 只检查部分玩家
     */
    public void startMonitoring() {
        stopMonitoring();
        
        monitorTask = new BukkitRunnable() {
            private Iterator<? extends Player> playerIterator;

            @Override
            public void run() {
                if (!plugin.getConfig().getBoolean("monitoring.enabled", true)) {
                    return;
                }

                // 每 tick 检查 5 个玩家，确保高并发下不影响 TPS
                int checkedCount = 0;
                
                if (playerIterator == null || !playerIterator.hasNext()) {
                    playerIterator = Bukkit.getOnlinePlayers().iterator();
                }

                while (playerIterator.hasNext() && checkedCount < 5) {
                    Player player = playerIterator.next();
                    if (player != null && player.isOnline()) {
                        checkSinglePlayer(player, false);
                    }
                    checkedCount++;
                }
            }
        }.runTaskTimer(plugin, 20L, checkInterval); 
    }

    public void stopMonitoring() {
        if (monitorTask != null && !monitorTask.isCancelled()) {
            monitorTask.cancel();
            monitorTask = null;
        }
    }

    public boolean isMonitoring() {
        return monitorTask != null && !monitorTask.isCancelled();
    }

    public void checkAllPlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            checkSinglePlayer(player, true);
        }
    }

    /**
     * 检查单个玩家状态
     * @param player 目标玩家
     * @param isManual 是否为手动触发
     */
    public void checkSinglePlayer(Player player, boolean isManual) {
        RepairContext context = isManual ? RepairContext.MANUAL : RepairContext.AUTO;
        Diagnosis diagnosis = diagnosePlayer(player);
        if (!diagnosis.hasIssues()) {
            if (context == RepairContext.MANUAL && player != null) {
                player.sendMessage(plugin.getPrefixedMessage("diagnose-manual-ok"));
            }
            return;
        }
        if (isManual) {
            String itemText = String.join("、", buildIssueLabels(diagnosis));
            logWarn("手动检查发现玩家数据异常: 玩家=" + player.getName() + " 项目=" + itemText);
        }

        // 检查自动修复开关
        if (context == RepairContext.AUTO && !plugin.getConfig().getBoolean("repair.auto-repair", true)) {
            String itemText = String.join("、", buildIssueLabels(diagnosis));
            logWarn("检测到玩家异常但自动修复已关闭: 玩家=" + player.getName() + " 项目=" + itemText);
            return;
        }

        handleCorruptedPlayer(player, diagnosis, context);
    }

    public boolean diagnoseAndRepairForCommand(Player player) {
        Diagnosis diagnosis = diagnosePlayer(player);
        if (!diagnosis.hasIssues()) {
            return true;
        }
        return handleCorruptedPlayer(player, diagnosis, RepairContext.COMMAND);
    }

    public boolean isPlayerDataCorrupted(Player player) {
        return diagnosePlayer(player).hasIssues();
    }
    
    private boolean isValidDouble(double d) {
        return Double.isFinite(d) && !Double.isNaN(d);
    }

    private void logWarn(String message) {
        plugin.getLogger().warning(message);
        plugin.getLogManager().log("WARN", message);
    }

    private void logError(String message, Throwable e) {
        plugin.getLogger().log(Level.SEVERE, message, e);
        plugin.getLogManager().log("ERROR", message);
    }

    private Diagnosis diagnosePlayer(Player player) {
        Diagnosis diagnosis = new Diagnosis();
        try {
            if (player == null || !player.isOnline()) return diagnosis;

            Location loc = player.getLocation();
            int minY = plugin.getConfig().getInt("repair.thresholds.y-min", -64);
            int maxY = plugin.getConfig().getInt("repair.thresholds.y-max", 320);

            // 核心坐标检查与安全坐标缓存
            if (plugin.getConfig().getBoolean("monitoring.checks.location", true)) {
                double coordinateMax = plugin.getConfig().getDouble("repair.thresholds.coordinate-max", 30000000.0);
                String worldName = loc.getWorld() != null ? loc.getWorld().getName() : "unknown";
                if (!isValidDouble(loc.getX()) || !isValidDouble(loc.getY()) || !isValidDouble(loc.getZ())) {
                    logWarn("发现玩家坐标异常(NaN/Infinite): 玩家=" + player.getName() + " 世界=" + worldName + " X=" + loc.getX() + " Y=" + loc.getY() + " Z=" + loc.getZ());
                    diagnosis.issues.add(IssueType.LOCATION);
                    diagnosis.severe = true;
                } else if (Math.abs(loc.getX()) > coordinateMax || Math.abs(loc.getZ()) > coordinateMax) {
                    // 防止坐标溢出导致区块加载崩溃
                    logWarn("发现玩家坐标超出世界边界: 玩家=" + player.getName() + " 世界=" + worldName + " X=" + loc.getX() + " Z=" + loc.getZ() + " 最大=" + coordinateMax);
                    diagnosis.issues.add(IssueType.LOCATION);
                    diagnosis.severe = true;
                } else if (loc.getY() < minY || loc.getY() > maxY) {
                    logWarn("发现玩家坐标越界: 玩家=" + player.getName() + " 世界=" + worldName + " Y=" + loc.getY() + " 范围=[" + minY + "," + maxY + "]");
                    diagnosis.issues.add(IssueType.LOCATION);
                    diagnosis.severe = true;
                } else {
                    cacheSafeLocation(player, loc);
                }
            }

            // 生命值检查
            // 如果玩家已死亡（生命值 <= 0），则跳过检查，避免误判
            if (player.isDead() || player.getHealth() <= 0.0) {
                return diagnosis;
            }

            double health = player.getHealth();
            double minHealth = plugin.getConfig().getDouble("repair.thresholds.health-min", 0.5);
            double maxHealth = plugin.getConfig().getDouble("repair.thresholds.health-max", 1024.0);
            if (!isValidDouble(health) || health < minHealth || health > maxHealth) {
                logWarn("发现玩家生命值异常: 玩家=" + player.getName() + " Health=" + health + " 范围=[" + minHealth + "," + maxHealth + "]");
                diagnosis.issues.add(IssueType.HEALTH);
            }

            // 属性检查 (Core Attribute Integrity Check)
            // 针对 1.21+ 常见的 ClientboundUpdateAttributesPacket NPE 崩溃
            // 遍历所有注册属性并拦截 NaN/Infinity 非法值，同时检测修饰符集合内部损坏
            if (plugin.getConfig().getBoolean("monitoring.checks.attributes", true)) {
                for (Attribute attr : Attribute.values()) {
                    try {
                        AttributeInstance attrInstance = player.getAttribute(attr);
                        if (attrInstance != null) {
                            double val = attrInstance.getValue();
                            double base = attrInstance.getBaseValue();
                            
                            // 核心有效性校验：拦截所有可能导致发包崩溃的非有限数值
                            // 属性值异常只需修复，不传送
                            if (!isValidDouble(val) || !isValidDouble(base)) {
                                logWarn("检测到高危非法属性值 (NaN/Inf): 玩家=" + player.getName() + " 属性=" + attr.name() + " Value=" + val + " Base=" + base);
                                diagnosis.issues.add(IssueType.ATTRIBUTES);
                            }
                            
                            // 深度检查：尝试遍历修饰符集合，检测内部结构损坏
                            // 这是针对 fastutil ObjectOpenHashSet "wrapped is null" 崩溃的关键修复
                            try {
                                Collection<AttributeModifier> modifiers = attrInstance.getModifiers();
                                if (modifiers != null) {
                                    for (AttributeModifier modifier : modifiers) {
                                        if (modifier == null) continue;
                                        // 尝试访问修饰符属性，触发潜在的内部异常
                                        double amount = modifier.getAmount();
                                        if (!isValidDouble(amount)) {
                                            logWarn("检测到属性修饰符数值异常: 玩家=" + player.getName() + " 属性=" + attr.name() + " Amount=" + amount);
                                            diagnosis.issues.add(IssueType.ATTRIBUTES);
                                        }
                                    }
                                }
                            } catch (NullPointerException npe) {
                                // 捕获 fastutil ObjectOpenHashSet 内部 "wrapped is null" 异常
                                logWarn("检测到属性修饰符集合内部结构损坏 (NPE): 玩家=" + player.getName() + " 属性=" + attr.name() + " 异常=" + npe.getMessage());
                                diagnosis.issues.add(IssueType.ATTRIBUTES);
                            } catch (Exception modifierEx) {
                                logWarn("属性修饰符检查异常: 玩家=" + player.getName() + " 属性=" + attr.name() + " 异常=" + modifierEx.getClass().getSimpleName() + " 信息=" + modifierEx.getMessage());
                                diagnosis.issues.add(IssueType.ATTRIBUTES);
                            }
                            
                            // 针对核心属性的业务范围约束，防止数值溢出引发的逻辑异常
                            double speedMax = plugin.getConfig().getDouble("repair.thresholds.speed-max", 1.0);
                            double speedMin = plugin.getConfig().getDouble("repair.thresholds.speed-min", 0.0);
                            double damageMax = plugin.getConfig().getDouble("repair.thresholds.damage-max", 2048.0);
                            double damageMin = plugin.getConfig().getDouble("repair.thresholds.damage-min", 0.0);
                            double healthMaxAttr = plugin.getConfig().getDouble("repair.thresholds.health-max", 1024.0);
                            double healthMinAttr = plugin.getConfig().getDouble("repair.thresholds.health-min", 0.5);

                            if (attr == Attribute.GENERIC_MOVEMENT_SPEED && (val > speedMax || val < speedMin)) {
                                logWarn("发现移动速度异常: 玩家=" + player.getName() + " Value=" + val + " 范围=[" + speedMin + "," + speedMax + "] Base=" + base);
                                diagnosis.issues.add(IssueType.ATTRIBUTES);
                            }
                            if (attr == Attribute.GENERIC_MAX_HEALTH && (val > healthMaxAttr || val < healthMinAttr)) {
                                logWarn("发现最大生命值异常: 玩家=" + player.getName() + " Value=" + val + " 范围=[" + healthMinAttr + "," + healthMaxAttr + "] Base=" + base);
                                diagnosis.issues.add(IssueType.ATTRIBUTES);
                            }
                            if (attr == Attribute.GENERIC_ATTACK_DAMAGE && (val > damageMax || val < damageMin)) {
                                logWarn("发现攻击伤害异常: 玩家=" + player.getName() + " Value=" + val + " 范围=[" + damageMin + "," + damageMax + "] Base=" + base);
                                diagnosis.issues.add(IssueType.ATTRIBUTES);
                            }
                        }
                    } catch (NullPointerException npe) {
                        // 捕获属性实例本身的 NPE（如 getValue() 时内部集合损坏）
                        logWarn("属性实例访问时发生 NPE: 玩家=" + player.getName() + " 属性=" + attr.name() + " 信息=" + npe.getMessage());
                        diagnosis.issues.add(IssueType.ATTRIBUTES);
                    } catch (Exception attrEx) {
                        // 部分属性在某些版本可能不支持，静默处理
                    }
                }
            }

            // 药水效果检查
            if (plugin.getConfig().getBoolean("monitoring.checks.effects", true)) {
                for (org.bukkit.potion.PotionEffect effect : player.getActivePotionEffects()) {
                    if (effect.getDuration() < 0 || effect.getAmplifier() < -1 || effect.getAmplifier() > 255) {
                        logWarn("发现玩家药水效果异常: 玩家=" + player.getName() + " Effect=" + effect.getType().getName() + " Amplifier=" + effect.getAmplifier() + " Duration=" + effect.getDuration());
                        diagnosis.issues.add(IssueType.EFFECTS);
                    }
                }
            }

            // 物品栏检查
            if (plugin.getConfig().getBoolean("monitoring.checks.inventory", true)) {
                if (player.getInventory() == null) {
                    logWarn("发现玩家物品栏丢失: 玩家=" + player.getName());
                    diagnosis.issues.add(IssueType.INVENTORY);
                }
            }

            // 载具状态检查
            if (plugin.getConfig().getBoolean("monitoring.checks.vehicle", true)) {
                if (player.isInsideVehicle() && (player.getVehicle() == null || !player.getVehicle().isValid())) {
                     String vehicleType = player.getVehicle() != null ? player.getVehicle().getType().name() : "null";
                     logWarn("发现异常载具状态: 玩家=" + player.getName() + " Vehicle=" + vehicleType);
                     diagnosis.issues.add(IssueType.GENERAL);
                     diagnosis.severe = true;
                }
            }
        } catch (Exception e) {
            logError("玩家数据诊断异常: 玩家=" + (player != null ? player.getName() : "unknown") + " 异常=" + e.getClass().getSimpleName() + " 信息=" + e.getMessage(), e);
            diagnosis.issues.add(IssueType.GENERAL);
            diagnosis.severe = true;
        }
        return diagnosis;
    }

    private void cacheSafeLocation(Player player, Location loc) {
        if (player == null || loc == null) return;
        lastSafeLocations.put(player.getUniqueId(), loc.clone());
    }

    private Location getLastSafeLocation(Player player) {
        if (player == null) return null;
        Location loc = lastSafeLocations.get(player.getUniqueId());
        return loc == null ? null : loc.clone();
    }

    private boolean handleCorruptedPlayer(Player player, Diagnosis diagnosis, RepairContext context) {
        try {
            String msg = "正在修复玩家数据: " + player.getName();
            logWarn(msg);

            boolean isCommand = context == RepairContext.COMMAND;
            boolean blockOnSevere = plugin.getConfig().getBoolean("repair.block-command-on-severe", true);
            boolean teleportOnSevereOnly = plugin.getConfig().getBoolean("repair.teleport-on-severe-only", true);
            boolean allowContinue = !diagnosis.severe || !blockOnSevere;
            boolean teleported = false;

            // 基础修复：只在需要时触发
            if (diagnosis.issues.contains(IssueType.HEALTH) || diagnosis.issues.contains(IssueType.ATTRIBUTES)) {
                player.setHealth(20.0);
                player.setFoodLevel(20);
                player.setFireTicks(0);
                player.setFallDistance(0);
            }

            // 属性深度修复：清除损坏的修饰符并重置属性
            if (diagnosis.issues.contains(IssueType.ATTRIBUTES)) {
                repairCorruptedAttributes(player);
            }

            if (diagnosis.issues.contains(IssueType.EFFECTS)) {
                player.getActivePotionEffects().forEach(effect -> player.removePotionEffect(effect.getType()));
            }

            // 严重异常情况下才进行传送
            if (plugin.getConfig().getBoolean("repair.safe-teleport", true)) {
                if (!teleportOnSevereOnly || diagnosis.severe) {
                    Location target = null;
                    boolean preferLastSafe = plugin.getConfig().getBoolean("repair.prefer-last-safe-location", true);
                    if (preferLastSafe) {
                        target = getLastSafeLocation(player);
                    }
                    if (target == null) {
                        target = player.getWorld().getSpawnLocation();
                    }
                    if (target != null) {
                        player.teleport(target);
                        teleported = true;
                    }
                }
            }

            sendDiagnosisMessage(player, diagnosis, teleported, isCommand, allowContinue);
            logDiagnosis(player, diagnosis, teleported, isCommand, allowContinue);

            if (isCommand && !allowContinue) {
                return false;
            }
            return true;
        } catch (Exception e) {
            logError("无法修复玩家数据: " + player.getName(), e);
            
            if (plugin.getConfig().getBoolean("repair.kick-if-unrepairable", true)) {
                // 使用 Scheduler 确保在主线程执行 kick
                Bukkit.getScheduler().runTask(plugin, () -> {
                    String kickLog = "修复失败，正在踢出玩家: " + player.getName();
                    logWarn(kickLog);
                    player.kickPlayer(plugin.getPrefixedMessage("kick-message"));
                });
            }
            return false;
        }
    }

    /**
     * 深度修复损坏的属性
     * 清除所有修饰符并重置为基础值，解决 fastutil 内部集合损坏问题
     */
    private void repairCorruptedAttributes(Player player) {
        for (Attribute attr : Attribute.values()) {
            try {
                AttributeInstance attrInstance = player.getAttribute(attr);
                if (attrInstance == null) continue;

                // 尝试清除所有修饰符
                try {
                    Collection<AttributeModifier> modifiers = attrInstance.getModifiers();
                    if (modifiers != null && !modifiers.isEmpty()) {
                        // 复制一份列表避免 ConcurrentModificationException
                        List<AttributeModifier> toRemove = new ArrayList<>(modifiers);
                        for (AttributeModifier modifier : toRemove) {
                            try {
                                attrInstance.removeModifier(modifier);
                            } catch (Exception removeEx) {
                                // 单个移除失败不影响继续
                            }
                        }
                        logWarn("已清除属性修饰符: 玩家=" + player.getName() + " 属性=" + attr.name() + " 数量=" + toRemove.size());
                    }
                } catch (NullPointerException npe) {
                    // 如果 getModifiers() 抛出 NPE，说明内部集合已损坏
                    // 尝试通过设置 baseValue 来触发内部重建
                    logWarn("属性修饰符集合已损坏，尝试强制重建: 玩家=" + player.getName() + " 属性=" + attr.name());
                }

                // 重置基础值到默认
                try {
                    double defaultBase = attrInstance.getDefaultValue();
                    if (isValidDouble(defaultBase)) {
                        attrInstance.setBaseValue(defaultBase);
                    }
                } catch (Exception baseEx) {
                    // 忽略
                }
            } catch (Exception attrEx) {
                // 某些属性可能不支持，静默处理
            }
        }
    }

    private void sendDiagnosisMessage(Player player, Diagnosis diagnosis, boolean teleported, boolean isCommand, boolean allowContinue) {
        if (player == null) return;
        List<String> items = buildIssueLabels(diagnosis);
        String itemText = String.join("、", items);

        player.sendMessage(plugin.getPrefixedMessage("diagnose-header"));
        player.sendMessage(formatMessage("diagnose-items", "{items}", itemText));

        if (teleported) {
            player.sendMessage(plugin.getMessage("diagnose-action-teleport"));
        } else {
            player.sendMessage(plugin.getMessage("diagnose-action-place"));
        }

        if (isCommand) {
            if (allowContinue) {
                player.sendMessage(plugin.getMessage("diagnose-continue"));
            } else {
                player.sendMessage(plugin.getMessage("diagnose-action-blocked"));
            }
        }

        String advice = buildAdviceText(diagnosis);
        if (!advice.isEmpty()) {
            player.sendMessage(formatMessage("diagnose-advice", "{advice}", advice));
        }
    }

    private void logDiagnosis(Player player, Diagnosis diagnosis, boolean teleported, boolean isCommand, boolean allowContinue) {
        String itemText = String.join("、", buildIssueLabels(diagnosis));
        String action = teleported ? "已传送到安全位置" : "原地修复完成";
        String context = isCommand ? "指令触发" : "自动监控";
        String result = allowContinue ? "允许继续" : "已阻止后续操作";
        logWarn("修复完成: 玩家=" + player.getName() + " 异常=" + itemText + " 处理=" + action + " 触发方式=" + context + " 后续=" + result);
    }

    private String formatMessage(String key, String placeholder, String value) {
        String msg = plugin.getMessage(key);
        return msg.replace(placeholder, value);
    }

    private List<String> buildIssueLabels(Diagnosis diagnosis) {
        List<String> items = new ArrayList<>();
        for (IssueType issue : diagnosis.issues) {
            String label = getIssueLabel(issue);
            if (label != null && !label.isEmpty()) {
                items.add(label);
            }
        }
        if (items.isEmpty()) {
            items.add(getIssueLabel(IssueType.GENERAL));
        }
        return items;
    }

    private String buildAdviceText(Diagnosis diagnosis) {
        List<String> adviceList = new ArrayList<>();
        for (IssueType issue : diagnosis.issues) {
            String advice = getIssueAdvice(issue);
            if (advice != null && !advice.isEmpty()) {
                adviceList.add(advice);
            }
        }
        return String.join("；", adviceList);
    }

    private String getIssueLabel(IssueType issue) {
        switch (issue) {
            case LOCATION:
                return plugin.getMessage("issue-labels.location");
            case HEALTH:
                return plugin.getMessage("issue-labels.health");
            case ATTRIBUTES:
                return plugin.getMessage("issue-labels.attributes");
            case EFFECTS:
                return plugin.getMessage("issue-labels.effects");
            case INVENTORY:
                return plugin.getMessage("issue-labels.inventory");
            case GENERAL:
            default:
                return plugin.getMessage("issue-labels.general");
        }
    }

    private String getIssueAdvice(IssueType issue) {
        switch (issue) {
            case LOCATION:
                return plugin.getMessage("issue-advice.location");
            case HEALTH:
                return plugin.getMessage("issue-advice.health");
            case ATTRIBUTES:
                return plugin.getMessage("issue-advice.attributes");
            case EFFECTS:
                return plugin.getMessage("issue-advice.effects");
            case INVENTORY:
                return plugin.getMessage("issue-advice.inventory");
            case GENERAL:
            default:
                return plugin.getMessage("issue-advice.general");
        }
    }
}
