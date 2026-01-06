# Change: 添加交互式终端功能

## Why
用户需要在服务器监控界面中能够直接执行命令并查看结果。当前系统只支持通过SSH执行单个命令并获取结果，无法进行交互式操作。添加交互式终端功能可以让用户像在本地终端一样操作远程服务器，提升使用体验和操作效率。

## What Changes
- 在服务器监控界面添加"进入终端"按钮
- 创建新的终端界面，支持键盘输入和实时输出显示
- 实现交互式SSH Shell通道（使用ChannelShell）
- 支持命令输入、执行和结果显示
- 支持基本的终端交互功能（Enter执行、Backspace删除等）
- 支持当前会话内的命令历史记录和浏览（上一条/下一条）
- 支持常见命令的基础自动补全能力

## Impact
- **Affected specs:**
  - `server-monitoring` - 需要添加进入终端的场景
  - 新增 `terminal` capability - 交互式终端功能
- **Affected code:**
  - `ServerMonitoringScreen.kt` - 添加终端入口按钮
  - 新增 `TerminalScreen.kt` - 终端界面
  - 新增 `TerminalViewModel.kt` - 终端状态管理
  - 新增 `TerminalService.kt` - 终端服务（处理Shell通道）
  - `NavGraph.kt` - 添加终端界面路由
  - `SshCommandService.kt` - 可能需要扩展或创建新的Shell服务

