## Context
用户需要在服务器监控界面中能够进入交互式终端，执行命令并查看实时结果。这需要：
1. 建立交互式SSH Shell通道（而非单次命令执行）
2. 处理键盘输入和命令输出
3. 提供类似真实终端的用户体验

## Goals / Non-Goals
- **Goals:**
  - 支持从服务器监控界面进入终端
  - 支持键盘输入命令
  - 实时显示命令执行结果
  - 支持基本的终端交互（Enter执行、Backspace删除等）
  - 支持当前会话内的命令历史记录（上一条/下一条）
  - 支持常见命令的基础自动补全能力
  - 终端界面关闭时自动断开Shell通道
  - 支持基本的ANSI转义序列显示（颜色、格式等）

- **Non-Goals:**
  - 多标签页终端（后续实现）
  - 终端主题自定义（后续实现）
  - 文件传输功能（后续实现）

## Decisions
- **Decision: 使用 JSch ChannelShell 实现交互式终端**
  - 理由：ChannelShell提供交互式Shell会话，支持持续输入输出
  - 替代方案：使用ChannelExec多次执行命令（无法保持状态，不适合交互式操作）
  - 选择：ChannelShell，复用现有的SSH Session

- **Decision: 终端服务架构**
  - 创建独立的 `TerminalService` 接口和实现
  - 负责管理Shell通道的生命周期
  - 处理输入输出流的读写
  - 理由：职责分离，便于测试和维护

- **Decision: 输入输出处理**
  - 使用Kotlin Coroutines Flow处理异步输入输出
  - 输入：从UI收集用户输入，通过OutputStream发送到Shell
  - 输出：从Shell的InputStream读取，通过Flow发送到UI
  - 理由：响应式编程，符合Android最佳实践

- **Decision: 终端显示**
  - 使用Jetpack Compose的 `TextField` 或 `BasicTextField` 显示终端输出
  - 支持滚动查看历史输出
  - 使用等宽字体（Monospace）显示
  - 理由：Compose原生支持，易于实现

- **Decision: 键盘输入处理**
  - 使用Android软键盘输入
  - 支持Enter键执行命令
  - 支持Backspace删除字符
  - 特殊键（方向键、Tab等）后续实现
  - 理由：最小可行实现，满足基本需求

- **Decision: ANSI转义序列处理**
  - 首次实现支持基本的ANSI颜色代码（前景色、背景色）
  - 使用简单的解析器处理常见转义序列
  - 复杂序列（光标移动、清屏等）后续实现
  - 理由：平衡功能性和实现复杂度

## Risks / Trade-offs
- **风险：Shell通道可能断开**
  - 缓解：检测连接状态，断开时提示用户
  - 缓解：提供重新连接功能

- **风险：大量输出可能导致性能问题**
  - 缓解：限制输出缓冲区大小
  - 缓解：使用虚拟化列表显示历史输出

- **风险：ANSI转义序列解析复杂**
  - 缓解：首次实现只支持基本颜色，后续逐步扩展
  - 缓解：使用现有的ANSI解析库（如需要）

- **性能考虑：**
  - Shell通道保持连接，避免频繁建立连接
  - 输出流使用缓冲读取，避免阻塞UI线程

## Migration Plan
- 无需迁移，这是新功能
- 不影响现有的命令执行功能（SshCommandService继续用于监控功能）

## Open Questions
- 是否需要支持终端窗口大小调整？（首次实现不包含）
- 是否需要支持本地终端快捷键（Ctrl+C等）？（首次实现不包含，后续考虑）

