# Change: 添加服务器连接和监控功能

## Why
用户需要能够连接到已保存的服务器，并查看服务器的实时状态信息（CPU、内存、磁盘等）。当前应用只支持添加和保存服务器配置，缺少实际的连接和监控能力。

## What Changes
- **新增服务器连接功能**：在服务器列表中添加连接按钮，用户可以点击连接已保存的服务器
- **新增服务器监控界面**：连接成功后进入新界面，展示服务器的实时状态信息
- **新增服务器状态获取**：通过 SSH 执行命令获取服务器的 CPU、内存、磁盘等信息
- **BREAKING**: 无

## Impact
- **受影响的能力：**
  - `server-management`：新增服务器连接功能
  - `server-monitoring`：新增服务器监控能力（新能力）

- **受影响的代码：**
  - `ServerListScreen.kt`：在服务器项中添加连接按钮
  - `ServerListViewModel.kt`：添加连接服务器的方法
  - 新增 `ServerMonitoringScreen.kt`：服务器监控界面
  - 新增 `ServerMonitoringViewModel.kt`：监控界面状态管理
  - 新增 `ServerMonitoringService.kt`：服务器状态获取服务
  - `NavGraph.kt`：添加服务器监控界面的导航路由
  - `SshAuthenticationService.kt`：可能需要扩展以支持保持连接和执行命令

- **依赖关系：**
  - 依赖现有的 SSH 认证服务
  - 依赖服务器管理服务获取服务器配置
  - 后续的终端执行功能将依赖此连接能力

