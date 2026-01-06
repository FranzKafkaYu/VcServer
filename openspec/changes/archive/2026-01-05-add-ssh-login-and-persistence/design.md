## Context
VcServer 是一个 Android 应用，需要实现 SSH 连接远程服务器的核心功能。根据项目要求：
- 支持密码登录和密钥登录两种方式
- 需要将服务器信息持久化到本地数据库
- 数据库需要支持迁移和导入到其他设备
- 应用需要支持 Android 10 及以上版本

## Goals / Non-Goals
- **Goals:**
  - 实现服务器添加功能，用户可以输入服务器基本信息
  - 实现 SSH 连接和认证（密码和密钥两种方式）
  - 实现服务器信息的本地数据库持久化
  - 确保敏感信息（密码、密钥）的安全存储

- **Non-Goals:**
  - 不实现服务器状态监控（CPU、内存等）功能
  - 不实现终端命令执行功能
  - 不实现代理连接功能
  - 不实现数据库迁移和导入功能（这些将在后续变更中实现）

## Decisions

### Decision: 使用 JSch 作为 SSH 客户端库
**原因：**
- JSch 是 Java 平台成熟的 SSH2 客户端库，广泛使用
- 支持密码和密钥认证
- 纯 Java 实现，Android 兼容性好
- 社区活跃，文档完善

**替代方案考虑：**
- Apache MINA SSHD：功能更强大但体积较大，对于基础功能来说过于复杂
- SSHJ：API 更现代但社区相对较小

### Decision: 使用 Room 作为本地数据库框架
**原因：**
- Room 是 Android 官方推荐的数据库框架
- 提供类型安全的 SQL 查询
- 支持数据库迁移
- 与 LiveData/Flow 集成良好，适合 Jetpack Compose

**替代方案考虑：**
- 直接使用 SQLite：需要更多样板代码，迁移管理复杂
- Realm：功能强大但体积较大，对于简单需求来说过于复杂

### Decision: 使用 Android Keystore 存储敏感信息
**原因：**
- Android Keystore 提供硬件级别的安全存储
- 密钥材料不会离开设备的安全硬件
- 符合 Android 安全最佳实践

**实现方式：**
- 密码：使用 Android Keystore 加密后存储
- SSH 私钥：使用 Android Keystore 加密后存储

### Decision: 服务器信息数据模型设计
**数据模型字段：**
- `id`: 主键，自增
- `name`: 服务器名称（用户自定义）
- `host`: IP 地址或域名
- `port`: SSH 端口（默认 22）
- `username`: SSH 用户名
- `authType`: 认证类型（PASSWORD 或 KEY）
- `encryptedPassword`: 加密后的密码（authType 为 PASSWORD 时使用）
- `encryptedPrivateKey`: 加密后的私钥（authType 为 KEY 时使用）
- `keyPassphrase`: 密钥密码（可选，如果私钥有密码保护）
- `createdAt`: 创建时间
- `updatedAt`: 更新时间

## Risks / Trade-offs
- **风险：密钥和密码存储安全** → 使用 Android Keystore 加密存储，确保敏感信息安全
- **风险：SSH 连接超时或失败** → 实现适当的错误处理和用户提示
- **风险：数据库迁移兼容性** → 使用 Room 的迁移机制，确保后续版本升级时数据不丢失
- **权衡：功能范围** → 本次仅实现基础功能，高级功能（状态监控、终端执行）在后续变更中实现

## Migration Plan
- 首次实现，无需迁移
- 数据库 schema 版本从 1 开始
- 后续添加功能时，通过 Room Migration 升级数据库版本

## Open Questions
- [ ] SSH 连接超时时间设置为多少合适？（建议：30 秒）
- [ ] 是否需要支持 SSH 密钥的多种格式？（建议：先支持 OpenSSH 格式，后续扩展）
- [ ] 数据库备份和恢复的触发方式？（建议：在后续变更中实现）



