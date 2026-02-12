package com.anticrash;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Level;

public class LogManager {
    
    private final AntiCrashPlugin plugin;
    private File logDir;
    private File currentLogFile;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss");
    private final Object lock = new Object();

    public LogManager(AntiCrashPlugin plugin) {
        this.plugin = plugin;
        init();
    }

    private void init() {
        // 创建 logs 文件夹: plugins/AntiCrashProtector/logs
        logDir = new File(plugin.getDataFolder(), "logs");
        if (!logDir.exists()) {
            logDir.mkdirs();
        }
        
        // 创建今天的日志文件: 2023-10-27.log
        checkLogFile();
    }

    private void checkLogFile() {
        String dateStr = dateFormat.format(new Date());
        File targetFile = new File(logDir, dateStr + ".log");
        
        // 如果文件名变了（过了一天），或者文件不存在
        if (currentLogFile == null || !currentLogFile.getName().equals(targetFile.getName())) {
            currentLogFile = targetFile;
            if (!currentLogFile.exists()) {
                try {
                    currentLogFile.createNewFile();
                } catch (IOException e) {
                    plugin.getLogger().log(Level.SEVERE, "无法创建日志文件", e);
                }
            }
        }
    }

    public void log(String message) {
        log("INFO", message);
    }

    public void log(String level, String message) {
        // 异步写入文件，避免阻塞主线程
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            synchronized (lock) {
                try {
                    // 每次写入前检查日期，确保跨天也能生成新文件
                    checkLogFile();
                    
                    try (FileWriter fw = new FileWriter(currentLogFile, true);
                         PrintWriter pw = new PrintWriter(fw)) {
                        
                        String timestamp = timeFormat.format(new Date());
                        pw.println("[" + timestamp + "] [" + level + "] " + message);
                    }
                } catch (IOException e) {
                    plugin.getLogger().log(Level.WARNING, "写入日志文件失败", e);
                }
            }
        });
    }
}
