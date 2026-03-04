---
name: "build-checker"
description: "打包前检查 - 核对版本号和文档一致性。当用户说'打包'、'mvn package'或'编译'时调用。"
---

# 打包前检查

## 触发条件

当用户说以下关键词时，自动进入打包检查模式：
- "打包"
- "mvn package"
- "编译"
- "构建"

## 检查流程

### 第一步：版本号一致性检查

读取以下文件中的版本号并对比：

| 文件 | 版本号位置 |
|------|-----------|
| `plugin.yml` | `version: x.x.x` |
| `pom.xml` | `<version>x.x.x</version>` |
| `config.yml` | `# 版本: x.x.x` |
| `README.md` | 更新日志标题 |
| `README_EN.md` | Update Log 标题 |
| `介绍文档.md` | 更新日志标题、下载链接 |
| `BUILD_GUIDE.md` | 构建产物文件名 |

**检查逻辑：**
```
读取 plugin.yml 版本号 → 作为基准版本
↓
读取 pom.xml 版本号 → 对比是否一致
↓
扫描所有 .md 文件中的版本号 → 对比是否一致
↓
输出不一致的文件列表
```

### 第二步：文档完整性检查

| 检查项 | 检查内容 |
|--------|---------|
| **新功能文档** | 新增的 Java 类是否有对应的文档说明 |
| **配置文档** | `config.yml` 中的新配置项是否在文档中说明 |
| **依赖文档** | `pom.xml` 中的新依赖是否在文档中说明 |
| **更新日志** | 是否有对应版本的更新日志 |

### 第三步：输出检查报告

```
## 打包前检查报告

### ✅ 版本号一致
- plugin.yml: 1.2.0
- pom.xml: 1.2.0
- 所有文档版本号正确

### ⚠️ 版本号不一致
- [文件名] 版本号为 x.x.x，应为 y.y.y

### ✅ 文档完整
- 所有新功能已有文档说明

### ⚠️ 文档缺失
- [功能名] 缺少文档说明
- [配置项] 未在文档中解释

### 建议
- 请更新 [文件名] 中的版本号
- 请补充 [功能] 的文档说明
```

### 第四步：自动修复（可选）

如果发现版本号不一致，询问用户是否自动修复：

```
发现版本号不一致，是否自动修复？
- plugin.yml: 1.2.0 (基准)
- pom.xml: 1.1.3 → 需要更新为 1.2.0

[是] 自动更新所有版本号
[否] 手动处理
```

### 第五步：执行打包

检查通过后，执行打包命令：

```bash
mvn clean package
```

**可选参数：**

| 参数 | 说明 | 示例 |
|------|------|------|
| `-q` | 安静模式，减少输出 | `mvn clean package -q` |
| `-DskipTests` | 跳过测试，加快打包 | `mvn clean package -DskipTests` |
| 组合使用 | 安静 + 跳过测试 | `mvn clean package -q -DskipTests` |

打包完成后，验证输出文件：

```
target/AntiCrashProtector-{version}.jar
```

## 版本号位置详情

### plugin.yml
```yaml
name: AntiCrashProtector
version: 1.2.0    # ← 检查这里
main: com.anticrash.AntiCrashPlugin
```

### pom.xml
```xml
<groupId>com.anticrash</groupId>
<artifactId>AntiCrashProtector</artifactId>
<version>1.2.0</version>  <!-- ← 检查这里 -->
```

### config.yml
```yaml
# AntiCrashProtector 配置文件
# 版本: 1.2.0    # ← 检查这里
```

### README.md
```markdown
## 📋 更新日志

### v1.2.0 (2026-03-04)  <!-- ← 检查这里 -->
```

### 介绍文档.md
```markdown
**[立即下载]** (附件: AntiCrashProtector-1.2.0.jar)  <!-- ← 检查这里 -->
```

### BUILD_GUIDE.md
```markdown
*   `AntiCrashProtector-1.2.0.jar`  <!-- ← 检查这里 -->
```

## 注意事项

1. **基准版本号**：以 `plugin.yml` 中的版本号为基准
2. **自动修复**：只修复版本号，不修改其他内容
3. **打包前检查**：在执行 `mvn package` 前必须完成检查
4. **打包后验证**：确认生成的 jar 文件名版本号正确

## 检查清单

```
□ plugin.yml 版本号正确
□ pom.xml 版本号正确
□ config.yml 版本号正确
□ README.md 版本号正确
□ README_EN.md 版本号正确
□ 介绍文档.md 版本号正确
□ BUILD_GUIDE.md 版本号正确
□ 更新日志已添加
□ 新功能已有文档说明
□ 新配置项已有文档说明
□ 新依赖已有文档说明
```
