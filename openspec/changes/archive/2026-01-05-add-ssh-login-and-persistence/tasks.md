## 1. 项目依赖配置
- [x] 1.1 在 `build.gradle` 中添加 JSch 依赖
- [x] 1.2 在 `build.gradle` 中添加 Room 相关依赖（room-runtime, room-ktx, room-compiler）
- [x] 1.3 在 `build.gradle` 中添加 AndroidX Security Crypto 依赖（用于加密存储）

## 2. 数据模型和数据库
- [x] 2.1 创建 `Server` 实体类（包含 id, name, host, port, username, authType, encryptedPassword, encryptedPrivateKey, keyPassphrase, createdAt, updatedAt）
- [x] 2.2 创建 `ServerDao` 接口（定义 CRUD 操作）
- [x] 2.3 创建 `AppDatabase` 类（Room 数据库实例，版本 1）
- [x] 2.4 创建 `DatabaseModule`（依赖注入配置，如使用 Dagger/Hilt）- 注：使用直接初始化方式，未使用依赖注入框架

## 3. Repository 层
- [x] 3.1 创建 `ServerRepository` 接口
- [x] 3.2 实现 `ServerRepositoryImpl`（封装数据库操作）
- [x] 3.3 实现服务器列表查询方法
- [x] 3.4 实现服务器添加方法
- [x] 3.5 实现服务器更新方法
- [x] 3.6 实现服务器删除方法

## 4. 安全存储
- [x] 4.1 创建 `SecureStorage` 工具类（封装 Android Keystore 操作）
- [x] 4.2 实现密码加密存储方法
- [x] 4.3 实现密码解密读取方法
- [x] 4.4 实现私钥加密存储方法
- [x] 4.5 实现私钥解密读取方法

## 5. SSH 认证服务
- [x] 5.1 创建 `SshAuthenticationService` 接口
- [x] 5.2 实现 `SshAuthenticationServiceImpl`（使用 JSch）
- [x] 5.3 实现密码认证方法
- [x] 5.4 实现密钥认证方法
- [x] 5.5 实现连接测试方法（验证服务器信息是否正确）
- [x] 5.6 实现连接关闭方法

## 6. 服务器管理服务
- [x] 6.1 创建 `ServerManagementService` 接口
- [x] 6.2 实现 `ServerManagementServiceImpl`（整合 Repository 和 SSH 服务）
- [x] 6.3 实现添加服务器方法（包含输入验证、加密存储、连接测试）
- [x] 6.4 实现更新服务器方法
- [x] 6.5 实现删除服务器方法
- [x] 6.6 实现获取服务器列表方法

## 7. UI 层 - 添加服务器界面
- [x] 7.1 创建 `AddServerScreen` Composable
- [x] 7.2 实现服务器基本信息输入（名称、主机、端口、用户名）
- [x] 7.3 实现认证方式选择（密码/密钥）
- [x] 7.4 实现密码输入字段（认证方式为密码时显示）
- [x] 7.5 实现密钥输入字段（认证方式为密钥时显示）
- [x] 7.6 实现表单验证逻辑
- [x] 7.7 实现保存按钮和连接测试按钮
- [x] 7.8 实现错误提示和加载状态

## 8. UI 层 - 服务器列表界面
- [x] 8.1 创建 `ServerListScreen` Composable
- [x] 8.2 实现服务器列表显示
- [x] 8.3 实现添加服务器按钮（导航到添加界面）
- [x] 8.4 实现服务器项点击事件（后续用于连接）
- [x] 8.5 实现服务器项长按菜单（编辑、删除）- 注：已实现删除功能，编辑功能待后续实现

## 9. ViewModel 层
- [x] 9.1 创建 `AddServerViewModel`（管理添加服务器状态和逻辑）
- [x] 9.2 创建 `ServerListViewModel`（管理服务器列表状态和逻辑）
- [x] 9.3 实现状态管理（UI 状态、错误状态、加载状态）
- [x] 9.4 实现业务逻辑调用（调用 Service 层方法）

## 10. 导航配置
- [x] 10.1 配置 Navigation Compose 路由
- [x] 10.2 实现服务器列表到添加服务器的导航
- [x] 10.3 实现添加服务器完成后的返回导航

## 11. 测试
- [ ] 11.1 编写 `ServerRepository` 单元测试
- [ ] 11.2 编写 `SecureStorage` 单元测试
- [ ] 11.3 编写 `SshAuthenticationService` 单元测试（使用 Mock 服务器）
- [ ] 11.4 编写 `ServerManagementService` 单元测试
- [ ] 11.5 编写 `AddServerViewModel` 单元测试
- [ ] 11.6 编写 `ServerListViewModel` 单元测试

## 12. 错误处理
- [x] 12.1 定义错误类型枚举（网络错误、认证错误、数据库错误等）
- [x] 12.2 实现错误消息本地化
- [x] 12.3 在 UI 层实现错误提示显示

## 13. 文档和代码审查
- [x] 13.1 为公共 API 添加 KDoc 注释
- [x] 13.2 检查代码风格符合项目规范
- [x] 13.3 运行 lint 检查并修复问题

