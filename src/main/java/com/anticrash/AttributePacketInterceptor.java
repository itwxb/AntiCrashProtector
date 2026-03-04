package com.anticrash;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * 属性数据包拦截器
 * 在服务器发送属性更新包之前检查数据完整性，防止 fastutil 内部损坏导致的崩溃
 */
public class AttributePacketInterceptor {

    private final AntiCrashPlugin plugin;
    private final ProtocolManager protocolManager;
    private boolean registered = false;
    private final Set<UUID> recentlyChecked = ConcurrentHashMap.newKeySet();
    
    public AttributePacketInterceptor(AntiCrashPlugin plugin) {
        this.plugin = plugin;
        this.protocolManager = ProtocolLibrary.getProtocolManager();
    }
    
    public void register() {
        if (registered) return;
        
        try {
            protocolManager.addPacketListener(new PacketAdapter(
                plugin,
                ListenerPriority.LOWEST,
                PacketType.Play.Server.UPDATE_ATTRIBUTES
            ) {
                @Override
                public void onPacketSending(PacketEvent event) {
                    if (event.isCancelled()) return;
                    
                    Player player = event.getPlayer();
                    if (player == null || !player.isOnline()) return;
                    
                    if (recentlyChecked.contains(player.getUniqueId())) {
                        return;
                    }
                    
                    recentlyChecked.add(player.getUniqueId());
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        recentlyChecked.remove(player.getUniqueId());
                    }, 1L);
                    
                    if (checkAndRepairPlayerAttributes(player)) {
                        event.setCancelled(true);
                        logWarn("已拦截并发送损坏的属性包，已修复玩家属性: " + player.getName());
                    }
                }
            });
            
            registered = true;
            plugin.getLogger().info("[核心] 属性数据包拦截器已启用 - 可防止 MMO 插件导致的属性崩溃");
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "无法注册属性数据包拦截器", e);
        }
    }
    
    public void unregister() {
        if (!registered) return;
        
        try {
            protocolManager.removePacketListeners(plugin);
            registered = false;
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "注销属性数据包拦截器时出错", e);
        }
    }
    
    public boolean isRegistered() {
        return registered;
    }
    
    private boolean checkAndRepairPlayerAttributes(Player player) {
        boolean hasCorruption = false;
        
        for (Attribute attr : Attribute.values()) {
            try {
                AttributeInstance attrInstance = player.getAttribute(attr);
                if (attrInstance == null) continue;
                
                try {
                    Collection<AttributeModifier> modifiers = attrInstance.getModifiers();
                    if (modifiers != null) {
                        for (AttributeModifier modifier : modifiers) {
                            if (modifier == null) continue;
                            try {
                                modifier.getAmount();
                                modifier.getName();
                                modifier.getOperation();
                            } catch (NullPointerException npe) {
                                throw npe;
                            }
                        }
                    }
                } catch (NullPointerException npe) {
                    logWarn("属性包检查发现损坏的修饰符集合: 玩家=" + player.getName() + " 属性=" + attr.name());
                    hasCorruption = true;
                    repairAttributeDeep(attrInstance, attr, player);
                }
            } catch (Exception attrEx) {
                // 静默处理
            }
        }
        
        return hasCorruption;
    }
    
    private void repairAttributeDeep(AttributeInstance attrInstance, Attribute attr, Player player) {
        try {
            double defaultBase = attrInstance.getDefaultValue();
            if (Double.isFinite(defaultBase) && !Double.isNaN(defaultBase)) {
                attrInstance.setBaseValue(defaultBase);
            }
        } catch (Exception e) {
            // 忽略
        }
        
        try {
            Object craftAttrInstance = attrInstance;
            Field handleField = craftAttrInstance.getClass().getDeclaredField("handle");
            handleField.setAccessible(true);
            Object nmsAttributeInstance = handleField.get(craftAttrInstance);
            
            if (nmsAttributeInstance != null) {
                Field modifiersField = null;
                Class<?> clazz = nmsAttributeInstance.getClass();
                while (clazz != null && modifiersField == null) {
                    try {
                        modifiersField = clazz.getDeclaredField("modifiers");
                    } catch (NoSuchFieldException e) {
                        clazz = clazz.getSuperclass();
                    }
                }
                
                if (modifiersField != null) {
                    modifiersField.setAccessible(true);
                    Object modifiersObj = modifiersField.get(nmsAttributeInstance);
                    
                    if (modifiersObj != null) {
                        try {
                            Method clearMethod = modifiersObj.getClass().getMethod("clear");
                            clearMethod.invoke(modifiersObj);
                            logWarn("已通过反射清空损坏的修饰符集合: 玩家=" + player.getName() + " 属性=" + attr.name());
                        } catch (Exception clearEx) {
                            modifiersField.set(nmsAttributeInstance, null);
                            logWarn("已通过反射置空损坏的修饰符集合: 玩家=" + player.getName() + " 属性=" + attr.name());
                        }
                    }
                }
            }
        } catch (Exception reflectEx) {
            // 反射失败时静默处理
        }
    }
    
    private void logWarn(String message) {
        plugin.getLogger().warning(message);
        plugin.getLogManager().log("WARN", message);
    }
}
