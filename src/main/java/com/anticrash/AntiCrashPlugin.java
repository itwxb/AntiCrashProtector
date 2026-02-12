package com.anticrash;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * AntiCrashProtector 插件主类
 * 负责插件的生命周期管理、配置加载以及各模块的初始化
 */
public class AntiCrashPlugin extends JavaPlugin {

    // 当前插件代码要求的配置文件版本号，用于自动升级配置
    private static final int CURRENT_CONFIG_VERSION = 1;
    private static AntiCrashPlugin instance;
    private CommandInterceptor commandInterceptor;
    private PlayerMonitor playerMonitor;
    private LogManager logManager;

    /**
     * 获取插件单例实例
     * @return 当前运行的插件实例
     */
    public static AntiCrashPlugin getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;
        
        // 1. 初始化配置系统
        saveDefaultConfig();      // 如果文件不存在则释放默认配置
        updateConfigFile();       // 检查并升级旧版本的配置文件
        reloadConfig();           // 重新载入内存
        
        // 2. 初始化核心模块
        this.logManager = new LogManager(this); // 日志管理（独立线程）
        
        getLogger().info("正在启动 AntiCrashProtector v" + getDescription().getVersion() + "...");

        // 3. 注册指令执行器和补全器
        AntiCrashCommand cmdExecutor = new AntiCrashCommand(this);
        if (getCommand("anticrash") != null) {
            getCommand("anticrash").setExecutor(cmdExecutor);
            getCommand("anticrash").setTabCompleter(cmdExecutor);
        }

        // 4. 根据配置文件应用各项功能设置
        applyConfig(true);
        getLogger().info("AntiCrashProtector 启动成功！");
    }

    @Override
    public void onDisable() {
        if (playerMonitor != null) {
            playerMonitor.stopMonitoring();
        }
        if (commandInterceptor != null) {
            HandlerList.unregisterAll(commandInterceptor);
            commandInterceptor = null;
        }
        
        instance = null;
        getLogger().info("AntiCrashProtector 已停止");
    }

    public PlayerMonitor getPlayerMonitor() {
        return playerMonitor;
    }

    public CommandInterceptor getCommandInterceptor() {
        return commandInterceptor;
    }

    public LogManager getLogManager() {
        return logManager;
    }

    /**
     * 打印调试日志
     * 仅在 config.yml 中 debug-mode 为 true 时输出
     */
    public void debugLog(String message) {
        if (getConfig().getBoolean("debug-mode", false)) {
            getLogger().info("[DEBUG] " + message);
        }
    }

    public void applyConfig(boolean log) {
        boolean enabled = getConfig().getBoolean("enabled", true);
        debugLog("正在应用配置... 总开关: " + enabled);
        if (!enabled) {
            if (playerMonitor != null) {
                playerMonitor.stopMonitoring();
            }
            if (commandInterceptor != null) {
                HandlerList.unregisterAll(commandInterceptor);
                commandInterceptor = null;
            }
            if (log) {
                getLogger().info("AntiCrashProtector 已进入配置禁用模式");
            }
            return;
        }

        boolean commandProtectionEnabled = getConfig().getBoolean("command-protection.enabled", true);
        if (commandProtectionEnabled) {
            if (commandInterceptor == null) {
                commandInterceptor = new CommandInterceptor(this);
            } else {
                commandInterceptor.loadConfig(); // 关键：重载时刷新指令列表
            }
            if (log) {
                getLogger().info("[核心] 命令安全拦截模块已同步最新配置");
            }
        } else if (commandInterceptor != null) {
            HandlerList.unregisterAll(commandInterceptor);
            commandInterceptor = null;
        }

        if (playerMonitor == null) {
            playerMonitor = new PlayerMonitor(this);
        } else {
            playerMonitor.loadConfig(); // 重载时刷新监控频率
        }
        boolean monitoringEnabled = getConfig().getBoolean("monitoring.enabled", true);
        if (monitoringEnabled) {
            playerMonitor.startMonitoring();
            if (log) {
                getLogger().info("[核心] 玩家健康自动巡逻任务已开启 (周期性扫描模式)");
            }
        } else {
            playerMonitor.stopMonitoring();
            if (log) {
                getLogger().info("[核心] 玩家健康自动巡逻任务已关闭 (按需触发检查模式)");
            }
        }
    }
    
    /**
     * 获取带前缀的格式化消息
     */
    public String getPrefixedMessage(String key) {
        String prefix = getConfig().getString("messages.prefix", "&e[AntiCrash] &r");
        String msg = getConfig().getString("messages." + key, "Message not found: " + key);
        return ChatColor.translateAlternateColorCodes('&', prefix + msg);
    }

    /**
     * 获取不带前缀的格式化消息
     */
    public String getMessage(String key) {
        String msg = getConfig().getString("messages." + key, "Message not found: " + key);
        return ChatColor.translateAlternateColorCodes('&', msg);
    }

    public void updateConfigFile() {
        File configFile = new File(getDataFolder(), "config.yml");
        YamlConfiguration current = YamlConfiguration.loadConfiguration(configFile);

        InputStream defaultStream = getResource("config.yml");
        if (defaultStream == null) {
            return;
        }
        YamlConfiguration defaults = YamlConfiguration.loadConfiguration(new InputStreamReader(defaultStream, StandardCharsets.UTF_8));

        int fileVersion = current.getInt("config-version", 0);
        int latestVersion = defaults.getInt("config-version", CURRENT_CONFIG_VERSION);
        boolean changed = false;

        for (String key : defaults.getKeys(true)) {
            if (!current.contains(key)) {
                current.set(key, defaults.get(key));
                changed = true;
            }
        }
        if (fileVersion < latestVersion) {
            current.set("config-version", latestVersion);
            changed = true;
        }
        if (changed) {
            try {
                current.save(configFile);
                getLogger().info("配置文件已自动升级到版本 " + latestVersion);
            } catch (Exception e) {
                getLogger().warning("配置文件升级失败: " + e.getMessage());
            }
        }
    }
}
