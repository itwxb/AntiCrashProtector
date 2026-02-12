# AntiCrashProtector

[**中文文档**](./介绍文档.md) | [**English Documentation**](./README_EN.md) | [**Build Guide**](./BUILD_GUIDE.md)

---

### 🚀 简介 / Introduction

**AntiCrashProtector** 是一款专为 Minecraft 服务器设计的轻量级安全防护插件。它能有效拦截非法数据包攻击、修复异常玩家属性、防止由于坐标溢出或非法药水等级导致的服务端崩溃（Crash）。

特别地，它主要解决玩家在传送或移动过程中，由于核心属性（坐标、生命值、速度等）异常错误导致的服务端崩溃或心跳停止（Ticking Exception）问题。

---

**AntiCrashProtector** is a lightweight security plugin for Minecraft servers. It efficiently intercepts malicious packets, repairs abnormal player attributes, and prevents server crashes caused by coordinate overflows or illegal potion levels.

Specifically, it addresses server crashes and Ticking Exceptions caused by core attribute errors (coordinates, health, speed, etc.) during player teleportation or movement.

---

### ✨ 核心特性 / Key Features

*   🛡️ **黑匣子日志 (Black-Box Logging)**: 同步记录异常详情到控制台与专用文件，精准排障。
*   ⚙️ **Fail-Safe 模式**: 智能默认值保底，无视配置缺失，确保核心防护永不失效。
*   ⚡ **轻量高效 (Lightweight)**: 极低的系统资源占用，不影响服务器 TPS。
*   🛠️ **分级修复 (Tiered Repair)**: 智能识别异常程度，轻则原地修复，重则安全传送。
*   📦 **开箱即用 (Out-of-the-Box)**: 默认配置已适配绝大多数生存/战争服务器。

---

### 📋 快速开始 / Quick Start

1.  下载最新的 [Release](https://github.com/YourUsername/AntiCrashProtector/releases) JAR 文件。
2.  将其放入服务器的 `plugins` 文件夹。
3.  重启服务器或使用 `/anticrash reload` 加载配置。

---

### 🛠️ 开发与构建 / Development

如果您想自行编译本项目，请参考 [构建指南](./BUILD_GUIDE.md)。

---

### 💖 鸣谢 / Credits

Developed with ❤️ for the Minecraft Community.
