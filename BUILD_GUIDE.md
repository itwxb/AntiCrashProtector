# 🛠️ AntiCrashProtector 构建指南

本文档旨在指导开发者如何从源码编译生成 `AntiCrashProtector` 插件。

## 📋 编译环境要求

在开始构建之前，请确保您的开发环境已安装以下工具：

1.  **JDK 21** 或更高版本 (本项目使用 Java 21 特性)。
2.  **Apache Maven 3.8.0** 或更高版本。
3.  网络连接（用于下载 Maven 依赖项，如 Paper-API）。

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
    *   `AntiCrashProtector-1.1.2.jar` (包含所有依赖的 Shaded 版本)

## 📂 项目结构说明

*   `src/main/java`: 存放插件的核心 Java 源码。
*   `src/main/resources`: 存放 `plugin.yml` 和 `config.yml` 等资源文件。
*   `pom.xml`: Maven 项目配置文件，定义了依赖和构建流程。

## 🛠️ 开发建议

*   **IDE 选择**：推荐使用 IntelliJ IDEA 或 VS Code。
*   **代码风格**：请遵循现有的代码缩进和命名规范。
*   **热重载测试**：修改代码并打包后，可以使用 `/anticrash reload` 测试配置更改，但涉及核心监听器逻辑的更改建议重启服务器或使用插件管理器（如 Plugman）重载。

---
如果您在构建过程中遇到任何问题，请检查 JDK 版本是否正确设置为 21。
