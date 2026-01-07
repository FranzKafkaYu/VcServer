# Change: 添加数据库导出/导入功能

## Why
用户希望在多个设备之间同步服务器配置信息。当前系统只支持单设备本地存储，用户需要手动在每个设备上重新配置服务器信息。添加导出/导入功能可以让用户轻松地将服务器配置从一台设备迁移到另一台设备。

## What Changes
- 在设置界面添加数据库导出功能按钮
- 在设置界面添加数据库导入功能按钮
- 创建导出服务，将服务器信息导出为 JSON 文件
- 创建导入服务，从 JSON 文件导入服务器信息
- 处理文件权限（Android 存储权限）
- 处理导入时的数据冲突（覆盖或跳过）
- 添加导出/导入成功的提示信息

## Impact
- 影响的规范: `data-persistence`（添加导出/导入需求）
- 影响的代码:
  - `SettingsScreen.kt` - 添加导出/导入按钮 UI
  - `SettingsViewModel.kt` - 添加导出/导入状态管理
  - `SettingsService.kt` - 添加导出/导入方法（可能需要新的服务）
  - 新建 `DatabaseExportImportService.kt` - 实现导出/导入逻辑
  - `Server.kt` - 可能需要添加序列化支持
  - `AndroidManifest.xml` - 可能需要添加存储权限


