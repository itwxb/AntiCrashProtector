# 🛠️ AntiCrashProtector 构建指南

本文档旨在指导开发者如何从源码编译生成 `AntiCrashProtector` 插件。

## 📋 编译环境要求

在开始构建之前，请确保您的开发环境已安装以下工具：

1.  **JDK 21** 或更高版本 (本项目使用 Java 21 特性)。
2.  **Apache Maven 3.8.0** 或更高版本。
3.  网络连接（用于下载 Maven 依赖项，如 Paper-API、ProtocolLib）。

## 🚀 编译步骤

1.  **克隆/下载源码**：
    将项目源码下载到本地目录。

2.  **进入项目根目录**：
    在终端或命令行中进入包含 `pom.xml` 的文件夹。

3.  **执行构建命令**：
    运行以下 Maven 命令进行清理并打包：
    ```bash
    mvn clean package
    ```

4.  **获取构建产物**：
    编译完成后，您可以在 `target/` 目录下找到生成的 JAR 文件：
    *   `AntiCrashProtector-1.2.0.jar` (包含所有依赖的 Shaded 版本)

## 📂 项目结构说明

```
AntiCrashProtector/
├── src/main/java/
│   └── com/anticrash/
│       ├── AntiCrashPlugin.java           # 主类 - 生命周期管理
│       ├── PlayerMonitor.java             # 核心 - 玩家监控、诊断、修复
│       ├── CommandInterceptor.java        # 拦截器 - 高危命令保护
│       ├── AttributePacketInterceptor.java # 数据包拦截器 (v1.2.0 新增)
│       ├── AntiCrashCommand.java          # 命令处理器
│       └── LogManager.java                # 日志管理
├── src/main/resources/
│   ├── plugin.yml                         # 插件描述文件
│   └── config.yml                         # 默认配置文件
├── pom.xml                                # Maven 项目配置
└── README.md                              # 项目说明文档
```

## 📦 依赖说明

### 运行时依赖

| 依赖 | 类型 | 说明 |
|------|------|------|
| Paper-API 1.21.1 | 硬依赖 | Minecraft 服务器 API |
| ProtocolLib 5.3.0+ | 软依赖 | 数据包拦截功能需要 |

### 编译时依赖

所有依赖都会通过 Maven 自动下载，无需手动安装。

## � 开发建议

*   **IDE 选择**：推荐使用 IntelliJ IDEA 或 VS Code。
*   **代码风格**：请遵循现有的代码缩进和命名规范。
*   **热重载测试**：
    *   配置更改：使用 `/anticrash reload`
    *   核心逻辑更改：建议重启服务器或使用插件管理器（如 Plugman）重载

## 🛡️ 核心模块说明

### 三层防护体系 (v1.2.0)

```
┌─────────────────────────────────────────────────────────┐
│                    三层防护体系                          │
├─────────────────────────────────────────────────────────┤
│  第一层：AttributePacketInterceptor                     │
│  ├── 数据包级别拦截，防止 MMO 插件崩溃                   │
│  └── 需要 ProtocolLib                                   │
│                                                         │
│  第二层：PlayerMonitor.onPlayerQuit                     │
│  ├── 玩家断开前检查修复                                  │
│  └── 防止断开连接时崩溃                                  │
│                                                         │
│  第三层：PlayerMonitor 定时监控                         │
│  ├── 每 1 秒扫描所有玩家                                 │
│  └── 主动发现并修复损坏                                  │
└─────────────────────────────────────────────────────────┘
```

### 反射深度修复

插件使用 Java 反射直接修复 NMS 内部损坏的 `fastutil ObjectOpenHashSet`：

```java
// 通过反射访问私有字段
Field handleField = craftAttrInstance.getClass().getDeclaredField("handle");
handleField.setAccessible(true);

// 清空损坏的修饰符集合
Method clearMethod = modifiersObj.getClass().getMethod("clear");
clearMethod.invoke(modifiersObj);
```

## ⚠️ 注意事项

1.  **ProtocolLib 兼容**：数据包拦截功能需要服务器安装 ProtocolLib，否则该功能会自动跳过。
2.  **版本兼容**：本项目针对 Minecraft 1.21.x 开发，其他版本可能需要调整 NMS 反射代码。
3.  **性能考虑**：默认检查间隔为 20 ticks (1秒)，可根据服务器负载调整。

---

如果您在构建过程中遇到任何问题，请检查：
1. JDK 版本是否正确设置为 21
2. Maven 是否能正常访问网络下载依赖
3. ProtocolLib 版本是否与服务器版本兼容
