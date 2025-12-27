# Windows 构建指南 - tina-android-tree-sitter

本文档详细说明了在 Windows 环境下构建 `tina-android-tree-sitter` 模块所需的配置和常见问题解决方案。

## 目录

- [环境要求](#环境要求)
- [构建步骤](#构建步骤)
- [常见问题及解决方案](#常见问题及解决方案)
  - [Cargo/Rust 权限问题](#1-cargorust-权限问题)
  - [Node.js 版本兼容性问题](#2-nodejs-版本兼容性问题)
  - [tree-sitter CLI 配置问题](#3-tree-sitter-cli-配置问题)
  - [CMake 路径转义问题](#4-cmake-路径转义问题)
  - [npm 依赖问题](#5-npm-依赖问题)

---

## 环境要求

### 必需软件

| 软件 | 版本要求 | 说明 |
|------|----------|------|
| **Node.js** | v20.x LTS (推荐) | ⚠️ **不要使用 v22 或 v24**，存在兼容性问题 |
| **Rust/Cargo** | 最新稳定版 | 用于编译 tree-sitter CLI |
| **Android SDK** | API 21+ | 包含 NDK 和 CMake |
| **Visual Studio** | 2019/2022 | 需要 C++ 构建工具 |
| **Python** | 3.x | node-gyp 依赖 |

### 推荐工具

- **nvm-windows**: 用于管理多个 Node.js 版本
  ```powershell
  winget install --id CoreyButler.NVMforWindows
  ```

---

## 构建步骤

### 1. 安装 Node.js v20 LTS

```powershell
# 使用 nvm-windows
nvm install 20
nvm use 20

# 验证版本
node --version  # 应显示 v20.x.x
```

### 2. 配置 Cargo 权限

确保当前用户对 Cargo 目录有完全控制权限：

```powershell
# 假设 Cargo 安装在 D:\Programs\Rust\cargo
icacls "D:\Programs\Rust\cargo" /grant %USERNAME%:(OI)(CI)F /T
```

### 3. 安装 grammar 依赖

某些 grammar（如 cpp）依赖其他 grammar：

```powershell
cd external\tina-android-tree-sitter\grammars\cpp
npm install
```

### 4. 运行构建

```powershell
.\gradlew.bat assembleDebugAllAbi
```

---

## 常见问题及解决方案

### 1. Cargo/Rust 权限问题

**错误信息：**
```
warning: failed to write cache, path: D:\Programs\Rust\cargo\bin\registry\...
error: failed to open `...\clap_lex-0.7.0.crate`
Caused by: 拒绝访问。 (os error 5)
```

**原因：** Cargo 注册表目录权限不足

**解决方案：**
```powershell
# 方法 1: 使用 icacls 授予权限
icacls "D:\Programs\Rust\cargo" /grant %USERNAME%:(OI)(CI)F /T

# 方法 2: 以管理员身份运行 PowerShell
# 右键点击 PowerShell -> 以管理员身份运行
```

---

### 2. Node.js 版本兼容性问题

**错误信息：**
```
Error: EISDIR: illegal operation on a directory, lstat 'C:'
```

**原因：** Node.js v22 和 v24 在 Windows 上存在模块路径解析 bug

**解决方案：** 降级到 Node.js v20 LTS

```powershell
# 安装 nvm-windows
winget install --id CoreyButler.NVMforWindows

# 重启终端后
nvm install 20
nvm use 20

# 验证
node --version
```

**相关 Issue：**
- https://github.com/nodejs/node/issues/52797

---

### 3. tree-sitter CLI 配置问题

**错误信息：**
```
Failed to locate a package.json file that has a "tree-sitter" section
```

**原因：** tree-sitter CLI v0.22.x 需要在 `package.json` 中有 `tree-sitter` 配置部分，而较新的 grammar 使用 `tree-sitter.json` 文件（v0.24+ 格式）

**解决方案：** 在 grammar 的 `package.json` 中添加 `tree-sitter` 配置：

```json
{
  "name": "tree-sitter-xxx",
  "tree-sitter": [
    {
      "scope": "source.xxx",
      "file-types": ["xxx"]
    }
  ]
}
```

**示例 - make grammar：**
```json
{
  "tree-sitter": [
    {
      "scope": "source.mk",
      "file-types": ["makefile", "Makefile", "MAKEFILE", "GNUmakefile", "mk", "mak", "dsp"]
    }
  ]
}
```

**示例 - bash grammar：**
```json
{
  "tree-sitter": [
    {
      "scope": "source.bash",
      "file-types": ["sh", "bash", ".bashrc", ".bash_profile", "ebuild", "eclass"],
      "injection-regex": "^(shell|bash|sh)$",
      "first-line-regex": "^#!.*\\b(sh|bash|dash)\\b.*$"
    }
  ]
}
```

---

### 4. CMake 路径转义问题

**错误信息：**
```
CMake Error at CMakeLists.txt:25 (set):
  Syntax error in cmake code...
  Invalid character escape '\U'.
```

**原因：** Windows 路径使用反斜杠（如 `C:\Users\...`），CMake 将 `\U` 解释为转义字符

**解决方案：** 已在 `DynamicModulePlugin.kt` 中修复，使用 `invariantSeparatorsPath` 确保路径使用正斜杠：

```kotlin
// 修复前
set(PROJECT_DIR ${rootDir.absolutePath})

// 修复后
val rootDirPath = rootDir.invariantSeparatorsPath
set(PROJECT_DIR "${rootDirPath}")
```

如果遇到此问题，请删除 `grammar-modules` 目录并重新构建：

```powershell
Remove-Item -Recurse -Force external\tina-android-tree-sitter\grammar-modules
.\gradlew.bat assembleDebugAllAbi
```

---

### 5. npm 依赖问题

**错误信息：**
```
Error: Cannot find module 'tree-sitter-c/grammar'
```

**原因：** 某些 grammar（如 cpp）继承自其他 grammar，需要安装 npm 依赖

**解决方案：**
```powershell
cd external\tina-android-tree-sitter\grammars\cpp
npm install
```

**依赖关系：**
- `tree-sitter-cpp` 依赖 `tree-sitter-c`

---

## 环境变量配置

### PATH 环境变量

确保以下路径在 PATH 中（使用绝对路径，不要使用相对路径）：

```
D:\Programs\nodejs\              # Node.js 安装目录
D:\Programs\nodejs\node_global   # npm 全局包目录
D:\Programs\Rust\cargo\bin       # Cargo bin 目录
```

### CARGO_HOME

如果 Cargo 不在默认位置，设置 `CARGO_HOME` 环境变量：

```powershell
$env:CARGO_HOME = "D:\Programs\Rust\cargo"
```

---

## 构建流程概述

```
┌─────────────────────────────────────────────────────────────┐
│                    Gradle Build Process                      │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  1. buildTreeSitter                                          │
│     └── 使用 Cargo 编译 tree-sitter CLI                      │
│                                                              │
│  2. generateTreeSitterGrammar                                │
│     └── 对每个 grammar 运行 tree-sitter generate             │
│         └── 调用 Node.js 处理 grammar.js                     │
│                                                              │
│  3. DynamicModulePlugin                                      │
│     └── 生成 grammar-modules/*/src/main/cpp/CMakeLists.txt   │
│                                                              │
│  4. configureCMake                                           │
│     └── 使用 Android NDK 配置 CMake                          │
│                                                              │
│  5. buildCMake                                               │
│     └── 编译原生库 (.so 文件)                                │
│                                                              │
│  6. assembleDebug                                            │
│     └── 打包 APK                                             │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

---

## 故障排除清单

如果构建失败，请按以下顺序检查：

- [ ] Node.js 版本是否为 v20.x？
- [ ] Cargo 目录权限是否正确？
- [ ] PATH 环境变量是否使用绝对路径？
- [ ] 是否需要安装 npm 依赖（cpp grammar）？
- [ ] grammar-modules 目录是否需要清理重建？

---

## 相关文件

| 文件 | 说明 |
|------|------|
| `build-logic/ats/src/main/java/.../DynamicModulePlugin.kt` | 生成 CMakeLists.txt 的插件 |
| `build-logic/ats/src/main/java/.../GenerateTreeSitterGrammarTask.kt` | 运行 tree-sitter generate 的任务 |
| `grammars/grammars.json` | grammar 配置列表 |
| `grammars/*/package.json` | 各 grammar 的 npm 配置 |

---

## 更新日志

### 2024-12-27
- 修复 CMake 路径转义问题（`DynamicModulePlugin.kt`）
- 添加 make/bash/yaml grammar 的 `tree-sitter` 配置
- 记录 Node.js v22/v24 兼容性问题