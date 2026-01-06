# VcServer

一个功能强大的 Android SSH 服务器管理应用，支持通过 SSH 连接到远程服务器并进行管理。

## 📱 应用简介

VcServer 是一个现代化的 Android 应用，允许用户通过 SSH 协议安全地连接到远程服务器，执行命令、监控服务器状态，并进行日常运维管理。应用采用 Material Design 3 设计规范，提供流畅的用户体验。

## ✨ 核心功能

### 🔐 服务器管理
- **添加服务器**：支持通过 IP/域名、SSH 端口添加服务器
- **多种认证方式**：
  - 密码认证（支持密码可见性切换）
  - SSH 密钥认证（支持带密码保护的私钥）
- **服务器列表管理**：
  - 查看所有已保存的服务器
  - 编辑服务器配置信息
  - 删除服务器（支持单个和批量删除）
  - 服务器排序功能
- **连接测试**：添加服务器前可测试连接是否成功

### 📊 服务器监控
- **实时状态监控**：
  - CPU 信息（核心数、使用率）
  - 内存信息（总内存、已用内存、可用内存、使用率）
  - 磁盘信息（挂载点、总容量、已用容量、使用率）
  - 系统信息（操作系统、系统版本、内核版本）
  - 运行时长（Uptime）
- **自动刷新**：可配置刷新间隔，自动更新服务器状态

### 💻 交互式终端
- **SSH 终端**：通过 SSH 连接执行命令
- **命令历史**：支持查看和执行历史命令
- **命令补全**：支持命令自动补全功能
- **ANSI 支持**：支持 ANSI 颜色代码显示
- **终端缓冲区**：管理终端输出内容

### ⚙️ 应用设置
- **主题设置**：
  - 浅色主题
  - 深色主题
  - 跟随系统
- **语言设置**：
  - 中文（简体）
  - English
  - 跟随系统
- **连接设置**：
  - 连接超时时间配置
  - 默认 SSH 端口配置
- **显示设置**：
  - 服务器监控刷新间隔配置
- **代理设置**：
  - 支持 HTTP 代理配置
  - 支持代理认证（用户名/密码）
- **关于页面**：显示应用版本信息、发布时间、作者和 GitHub 地址

### 🔒 安全特性
- **加密存储**：使用 Android Keystore 加密存储敏感信息（密码、SSH 私钥）
- **安全传输**：所有 SSH 连接使用加密协议
- **数据持久化**：服务器配置信息安全保存到本地数据库

## 🛠️ 技术栈

### 核心框架
- **编程语言**：Kotlin
- **UI 框架**：Jetpack Compose
- **架构模式**：MVVM (Model-View-ViewModel)
- **异步处理**：Kotlin Coroutines + Flow

### 主要依赖

#### UI 层
- `androidx.compose.ui:ui` - Compose UI 核心
- `androidx.compose.material3:material3` - Material Design 3
- `androidx.navigation:navigation-compose` - 导航组件
- `androidx.lifecycle:lifecycle-viewmodel-compose` - ViewModel 支持

#### 数据层
- `androidx.room:room-runtime` - Room 数据库
- `androidx.room:room-ktx` - Room Kotlin 扩展
- `androidx.datastore:datastore-preferences` - DataStore 偏好设置

#### 网络与安全
- `com.jcraft:jsch:0.1.55` - SSH 客户端库
- `androidx.security:security-crypto` - Android 安全加密库

#### 工具库
- `org.jetbrains.kotlinx:kotlinx-coroutines-android` - 协程支持

### 开发工具
- **构建工具**：Gradle 8.7
- **Kotlin 版本**：1.9.10
- **编译 SDK**：34
- **最低支持**：Android 10 (API 29)
- **目标 SDK**：34

## 📁 项目结构

```
app/src/main/java/com/vcserver/
├── data/                    # 数据层
│   ├── AppDatabase.kt      # Room 数据库
│   ├── converters/         # 类型转换器
│   └── dao/                # 数据访问对象
├── models/                  # 数据模型
│   ├── Server.kt           # 服务器模型
│   ├── AppSettings.kt      # 应用设置模型
│   ├── AuthType.kt         # 认证类型枚举
│   └── ServerStatus.kt     # 服务器状态模型
├── repositories/           # 数据仓库层
│   ├── ServerRepository.kt
│   ├── ServerRepositoryImpl.kt
│   ├── SettingsRepository.kt
│   └── SettingsRepositoryImpl.kt
├── services/                # 业务逻辑层
│   ├── ServerManagementService.kt
│   ├── ServerMonitoringService.kt
│   ├── SshAuthenticationService.kt
│   ├── SshCommandService.kt
│   ├── TerminalService.kt
│   └── SettingsService.kt
├── ui/                      # UI 层
│   ├── navigation/          # 导航配置
│   ├── screens/            # 界面组件
│   │   ├── ServerListScreen.kt
│   │   ├── AddServerScreen.kt
│   │   ├── ServerMonitoringScreen.kt
│   │   ├── TerminalScreen.kt
│   │   ├── SettingsScreen.kt
│   │   └── AboutScreen.kt
│   ├── theme/              # 主题配置
│   └── viewmodels/         # ViewModel
├── utils/                   # 工具类
│   ├── SecureStorage.kt    # 安全存储
│   ├── SessionManager.kt   # Session 管理
│   ├── LocaleHelper.kt     # 语言切换
│   ├── AnsiParser.kt       # ANSI 解析
│   ├── CommandHistory.kt   # 命令历史
│   └── TerminalBuffer.kt   # 终端缓冲区
└── MainActivity.kt          # 主 Activity
```

## 🚀 快速开始

### 环境要求
- Android Studio Narwhal Feature Drop | 2025.1.2 或更高版本
- JDK 17
- Android SDK 29+
- Gradle 8.7

### 构建步骤

1. **克隆项目**
   ```bash
   git clone https://github.com/FranzKafkaYu/VcServer.git
   cd VcServer
   ```

2. **打开项目**
   - 使用 Android Studio 打开项目
   - 等待 Gradle 同步完成

3. **运行应用**
   - 连接 Android 设备或启动模拟器（Android 10+）
   - 点击运行按钮或使用快捷键 `Shift+F10`

### 配置说明

应用首次运行时会自动创建本地数据库和设置存储。所有敏感信息（密码、私钥）都会使用 Android Keystore 进行加密存储。

## 📋 已实现功能清单

### ✅ 核心功能
- [x] SSH 服务器添加和管理
- [x] 密码和密钥两种认证方式
- [x] 服务器状态实时监控
- [x] 交互式 SSH 终端
- [x] 命令历史和自动补全
- [x] 服务器配置持久化存储
- [x] 敏感信息加密存储

### ✅ 用户体验
- [x] Material Design 3 UI
- [x] 浅色/深色主题切换
- [x] 中英文语言切换
- [x] 服务器列表排序
- [x] 批量删除服务器
- [x] 应用设置管理
- [x] 关于页面

### ✅ 技术特性
- [x] MVVM 架构
- [x] Room 数据库
- [x] DataStore 偏好设置
- [x] Kotlin Coroutines
- [x] Jetpack Compose
- [x] Navigation Compose
- [x] Android Keystore 加密

## 🔜 To-Do 列表

### 功能增强
- [ ] **代理连接支持**：实现通过代理服务器连接 SSH（UI 已实现，待集成到 SSH 连接逻辑）
- [ ] **设置应用到功能**：
  - [ ] 在服务器监控中应用刷新间隔设置
  - [ ] 在 SSH 连接中应用连接超时设置
  - [ ] 在添加服务器时应用默认端口设置
- [ ] **数据库迁移功能**：支持将数据库导出和导入到其他设备
- [ ] **服务器分组**：支持将服务器按组分类管理
- [ ] **快速连接**：支持最近连接的服务器快速访问
- [ ] **SSH 隧道**：支持 SSH 端口转发功能
- [ ] **文件传输**：支持 SFTP 文件上传和下载

### 测试与质量
- [ ] **单元测试**：
  - [ ] SettingsRepository 单元测试
  - [ ] SettingsService 单元测试
  - [ ] SettingsViewModel 单元测试
  - [ ] ServerRepository 单元测试
  - [ ] SecureStorage 单元测试
- [ ] **集成测试**：
  - [ ] SSH 连接测试（使用 Mock 服务器）
  - [ ] 服务器管理流程测试
- [ ] **UI 测试**：关键界面的 UI 自动化测试

### 性能优化
- [ ] **连接池管理**：优化 SSH 连接复用
- [ ] **数据缓存**：服务器状态数据缓存机制
- [ ] **后台任务**：服务器监控后台任务优化

### 文档与维护
- [ ] **用户文档**：编写用户使用手册
- [ ] **开发者文档**：API 文档和架构文档
- [ ] **代码覆盖率**：提升单元测试覆盖率至 80%+
- [ ] **性能分析**：应用性能分析和优化

### 国际化
- [ ] **更多语言支持**：添加更多语言资源文件
- [ ] **RTL 支持**：支持从右到左的语言布局

## 🔐 安全说明

- 所有敏感信息（密码、SSH 私钥）使用 Android Keystore 进行加密存储
- SSH 连接使用标准加密协议
- 应用不收集或上传任何用户数据
- 所有数据存储在设备本地

## 📄 许可证

[待添加许可证信息]

## 👥 贡献

欢迎提交 Issue 和 Pull Request！

## 📞 联系方式

- **GitHub**: [https://github.com/vcserver/vcserver](https://github.com/vcserver/vcserver)
- **作者**: FranzKafkaYu

---

**注意**：本项目使用 OpenSpec 进行规范驱动开发。有关项目规范和开发流程，请参考 `openspec/` 目录下的文档。

