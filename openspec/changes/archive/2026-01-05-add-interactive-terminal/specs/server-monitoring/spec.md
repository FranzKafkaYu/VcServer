## MODIFIED Requirements
### Requirement: 服务器监控界面
系统 SHALL 提供界面显示服务器的实时状态信息，并提供进入交互式终端的入口。

#### Scenario: 显示服务器状态
- **WHEN** 用户成功连接到服务器
- **AND** 系统进入服务器监控界面
- **THEN** 系统显示服务器名称和连接状态
- **AND THEN** 系统自动获取并显示服务器状态信息（CPU、内存、磁盘）
- **AND THEN** 系统显示加载状态指示器（获取信息时）
- **AND THEN** 系统显示"进入终端"按钮

#### Scenario: 进入交互式终端
- **WHEN** 用户在服务器监控界面点击"进入终端"按钮
- **THEN** 系统导航到交互式终端界面
- **AND THEN** 系统复用现有的SSH连接建立Shell通道
- **AND THEN** 系统显示终端界面，用户可以输入命令

