# Project Context

## Purpose
VcServer 是一个Android应用，通过该应用我们可以通过SSH连接到远程服务器并进行管理。

**主要目标：**
- 支持添加服务器,用户可输入IP/域名,SSH端口,支持密码登录与密钥登录，密码登录时输入用户名与密码SSH，密钥登陆时输入密钥  
- 服务器连接成功后，我们可以看到服务器的状态如CPU核心数、CPU占用率、内存信息、磁盘信息等  
- 支持通过代理连接服务器  
- 服务器连接成功后，我们可以进入终端执行cmd命令    
- 服务器添加成功后，保存至本地数据库，数据库可迁移并导入其他安卓手机    

## Tech Stack
- **编程语言：** [Java, Kotlin]
- **UI框架：** [Jetpack Compose]  
- **数据库：** [MySQL]  
- **开发环境:** [WIdnows 11,Android Studio Narwhal Feature Drop | 2025.1.2]
- **构建工具：** [Gradle 8.7]


## Project Conventions

### Code Style
- **命名规范：**
  - 类名：PascalCase（例如：`UserService`, `DatabaseConnection`）
  - 方法/函数名：camelCase（例如：`getUserById`, `processRequest`）
  - 常量：UPPER_SNAKE_CASE（例如：`MAX_RETRY_COUNT`, `API_TIMEOUT`）
  - 文件名：与类名保持一致或使用 kebab-case（例如：`user-service.ts`, `UserService.java`）
  
- **代码格式化：**
  - [使用的格式化工具，使用Prettier]
  - [缩进规则，使用Tab ]
  
- **注释规范：**
  - 公共 API 必须包含文档注释
  - 复杂业务逻辑需要解释性注释
  
- **项目结构：**
  ```
  如下所示，按模块划分
  src/
    ├── controllers/    # 控制器层
    ├── services/       # 业务逻辑层
    ├── models/         # 数据模型
    ├── repositories/   # 数据访问层
    └── utils/          # 工具类
  ```

### Testing Strategy
- **测试类型：**
  - 单元测试：覆盖核心业务逻辑，目标覆盖率 [待补充：例如 80%]
  - 集成测试：覆盖 API 端点和数据库交互
  
- **测试框架：** [JUnit]
  

### Git Workflow
- **分支策略：** [待补充：例如：Git Flow, GitHub Flow, Trunk-based Development]
  - `main/master`：生产环境代码
  - `develop`：开发主分支（如使用 Git Flow）
  - `feature/*`：功能分支
  - `fix/*`：Bug 修复分支
  - `hotfix/*`：紧急修复分支（如使用 Git Flow）

- **提交规范：** [待补充：例如：Conventional Commits]
  ```
  <type>(<scope>): <subject>
  
  <body>
  
  <footer>
  ```
  类型：`feat`, `fix`, `docs`, `style`, `refactor`, `test`, `chore`

- **代码审查：** [待补充：PR 审查要求、必须的审查者数量等]

- **发布流程：** [待补充：版本号规则、发布流程等]

## Important Constraints
- **技术约束：**
  - 支持Android版本10及以上