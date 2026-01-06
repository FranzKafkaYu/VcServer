## ADDED Requirements

### Requirement: 服务器信息持久化
系统 SHALL 将服务器配置信息持久化存储到本地数据库。

#### Scenario: 保存服务器信息到数据库
- **WHEN** 用户成功添加服务器
- **THEN** 系统将服务器基本信息（名称、主机、端口、用户名、认证类型）保存到数据库
- **AND THEN** 系统将加密后的敏感信息（密码或私钥）保存到数据库
- **AND THEN** 系统记录创建时间和更新时间

#### Scenario: 从数据库加载服务器列表
- **WHEN** 用户打开应用或刷新服务器列表
- **THEN** 系统从本地数据库查询所有服务器记录
- **AND THEN** 系统解密敏感信息（如需要显示）
- **AND THEN** 系统将服务器列表返回给 UI 层

### Requirement: 敏感信息加密存储
系统 SHALL 使用 Android Keystore 加密存储敏感信息（密码和 SSH 私钥）。

#### Scenario: 加密存储密码
- **WHEN** 用户使用密码认证方式添加服务器
- **AND** 用户输入密码
- **THEN** 系统使用 Android Keystore 加密密码
- **AND THEN** 系统将加密后的密码存储到数据库
- **AND THEN** 系统不在内存中保留明文密码

#### Scenario: 加密存储 SSH 私钥
- **WHEN** 用户使用密钥认证方式添加服务器
- **AND** 用户输入 SSH 私钥
- **THEN** 系统使用 Android Keystore 加密私钥
- **AND THEN** 系统将加密后的私钥存储到数据库
- **AND THEN** 系统不在内存中保留明文私钥

#### Scenario: 解密读取敏感信息
- **WHEN** 系统需要读取加密的密码或私钥（例如：建立 SSH 连接时）
- **THEN** 系统从数据库读取加密数据
- **AND THEN** 系统使用 Android Keystore 解密数据
- **AND THEN** 系统返回解密后的数据用于认证

### Requirement: 服务器信息更新
系统 SHALL 支持更新已保存的服务器配置信息。

#### Scenario: 更新服务器信息
- **WHEN** 用户修改服务器配置信息
- **AND** 用户保存更改
- **THEN** 系统更新数据库中的服务器记录
- **AND THEN** 系统更新记录的更新时间戳
- **AND THEN** 如果敏感信息被修改，系统重新加密并存储

### Requirement: 服务器信息删除
系统 SHALL 支持从数据库中删除服务器配置信息。

#### Scenario: 删除服务器信息
- **WHEN** 用户确认删除服务器
- **THEN** 系统从数据库中删除服务器记录
- **AND THEN** 系统同时删除关联的加密敏感信息
- **AND THEN** 系统返回删除成功状态

### Requirement: 数据库初始化
系统 SHALL 在首次使用时初始化本地数据库。

#### Scenario: 数据库首次初始化
- **WHEN** 应用首次启动
- **AND** 本地数据库不存在
- **THEN** 系统创建数据库文件
- **AND THEN** 系统创建服务器表结构
- **AND THEN** 系统设置数据库版本为 1



