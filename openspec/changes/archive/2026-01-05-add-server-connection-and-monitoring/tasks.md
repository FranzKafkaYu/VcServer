## 1. 数据模型
- [x] 1.1 创建 `ServerStatus.kt` 数据类，包含 CPU、内存、磁盘等信息
- [x] 1.2 定义 CPU 信息结构（核心数、使用率等）
- [x] 1.3 定义内存信息结构（总内存、已用内存、可用内存等）
- [x] 1.4 定义磁盘信息结构（总容量、已用容量、可用容量、挂载点等）

## 2. SSH 命令执行服务
- [x] 2.1 扩展 `SshAuthenticationService` 或创建新的 `SshCommandService` 接口
- [x] 2.2 实现通过 SSH Session 执行命令的方法
- [x] 2.3 实现命令执行结果解析（字符串输出）
- [x] 2.4 添加命令执行超时处理

## 3. 服务器监控服务
- [x] 3.1 创建 `ServerMonitoringService` 接口
- [x] 3.2 实现 `connectToServer(server: Server): Result<Session>` 方法
- [x] 3.3 实现 `getServerStatus(session: Session): Result<ServerStatus>` 方法
- [x] 3.4 实现解析 CPU 信息的逻辑（从 `/proc/cpuinfo` 和 `/proc/stat`）
- [x] 3.5 实现解析内存信息的逻辑（从 `free -h` 命令）
- [x] 3.6 实现解析磁盘信息的逻辑（从 `df -h` 命令）
- [x] 3.7 实现 `disconnect(session: Session)` 方法
- [x] 3.8 添加错误处理和异常捕获

## 4. 服务器列表 UI 更新
- [x] 4.1 在 `ServerItem.kt` 中添加连接按钮
- [x] 4.2 更新 `ServerListViewModel.kt`，添加 `connectToServer(server: Server)` 方法
- [x] 4.3 添加连接状态管理（连接中、已连接、连接失败）
- [x] 4.4 更新 UI 以显示连接状态

## 5. 服务器监控界面
- [x] 5.1 创建 `ServerMonitoringScreen.kt`
- [x] 5.2 创建 `ServerMonitoringViewModel.kt`
- [x] 5.3 实现界面布局（显示服务器名称、连接状态）
- [x] 5.4 实现 CPU 信息显示区域
- [x] 5.5 实现内存信息显示区域
- [x] 5.6 实现磁盘信息显示区域
- [x] 5.7 添加刷新按钮
- [x] 5.8 添加断开连接按钮
- [x] 5.9 添加加载状态指示器
- [x] 5.10 添加错误提示显示

## 6. 导航集成
- [x] 6.1 在 `NavGraph.kt` 中添加服务器监控界面路由
- [x] 6.2 更新 `ServerListScreen` 的连接按钮点击处理，导航到监控界面
- [x] 6.3 实现返回导航逻辑

## 7. 错误处理
- [x] 7.1 处理连接失败的情况（网络错误、认证失败等）
- [x] 7.2 处理命令执行失败的情况
- [x] 7.3 处理连接断开的情况
- [x] 7.4 添加用户友好的错误提示

## 8. 测试
- [ ] 8.1 单元测试：服务器状态解析逻辑
- [ ] 8.2 单元测试：SSH 命令执行服务
- [ ] 8.3 集成测试：完整的连接和状态获取流程

