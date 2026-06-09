# Automatic HR System

Automatic HR System 是一个自动化人力资源与智能面试管理系统，覆盖后台用户管理、组织架构、员工档案、招聘岗位、候选人报名、简历管理、AI 面试、视频面试、线下面审批和审计日志等流程。

## 技术栈

### 后端

- Java 17
- Spring Boot 3.2.5
- Spring Web
- Spring Security
- Spring Validation
- MyBatis-Plus 3.5.6
- JWT：`jjwt 0.12.5`
- 数据库驱动：SQLite、MySQL、PostgreSQL
- 连接池：HikariCP
- 工具库：Lombok、Hutool
- 构建工具：Maven

### 前端

- Vue 3.4
- Vite 5
- Vue Router 4
- Element Plus 2.7
- Axios
- ESLint
- Prettier
- Sass

### 数据与文件

- 默认数据库：SQLite
- 默认开发库文件：`backend/autohr-dev.db`
- 数据表初始化脚本：`backend/src/main/resources/schema.sql`
- 简历上传目录：`backend/uploads/resumes`
- 面试录制上传目录：`backend/uploads/interview-recordings`

## 项目结构

```text
.
├── backend/                      # Spring Boot 后端服务
│   ├── src/main/java/com/autohr
│   │   ├── common/               # 通用响应、异常、文件下载等
│   │   ├── config/               # 安全、数据库、MyBatis 配置
│   │   └── modules/
│   │       ├── auth/             # 登录、注册、用户、审计日志
│   │       ├── hr/               # 部门、员工、系统挂接、仪表盘
│   │       ├── recruitment/      # 招聘岗位、候选人、简历
│   │       └── interview/        # 知识库、LLM、AI 面试、视频面试、审批
│   └── src/main/resources/
│       ├── application.yml       # 通用配置
│       ├── application-dev.yml   # 开发环境配置
│       └── schema.sql            # 数据库表结构初始化
├── frontend/                     # Vue 前端应用
│   ├── src/views/                # 页面视图
│   ├── src/router/               # 前端路由与角色守卫
│   ├── src/services/api.js       # Axios API 封装
│   └── src/styles/global.css     # 全局样式
├── pom.xml                       # 根 Maven 配置
└── README.md
```

## 快速启动

### 环境要求

- JDK 17+
- Maven 3.8+
- Node.js 18+
- npm 9+

### 启动后端

进入后端目录：

```bash
cd backend
```

启动 Spring Boot 服务：

```bash
mvn spring-boot:run
```

后端默认端口：`8080`。

首次启动时系统会根据 `schema.sql` 初始化表结构，并自动创建默认后台账号。

### 启动前端

进入前端目录：

```bash
cd frontend
```

安装依赖：

```bash
npm install
```

启动开发服务：

```bash
npm run dev
```

前端默认端口：`3000`。

开发环境中，Vite 会将 `/api` 请求代理到 `http://localhost:8080`。

### 构建前端

```bash
cd frontend
npm run build
```

Windows PowerShell 如果因为执行策略无法运行 `npm`，可改用：

```bash
npm.cmd run build
```

## 默认账号

系统启动时会自动创建以下账号，默认密码均为 `123456`：

| 用户名 | 角色 | 说明 |
| --- | --- | --- |
| `itadmin` | `IT_ADMIN` | IT 管理员，可管理 LLM 配置、用户、HR、招聘和面试后台 |
| `hradmin` | `HR_ADMIN` | HR 管理员，可管理用户、HR、招聘和面试流程 |
| `hruser` | `HR_USER` | HR 用户，可使用 HR、招聘和面试后台功能 |

面试者账号通过登录页注册生成，角色为 `INTERVIEWEE`。

## 角色权限

| 角色 | 主要权限 |
| --- | --- |
| `IT_ADMIN` | 系统用户管理、审计日志、HR 管理、招聘管理、面试管理、LLM 配置 |
| `HR_ADMIN` | 用户管理、审计日志、HR 管理、招聘管理、面试管理 |
| `HR_USER` | HR 管理、招聘管理、面试管理 |
| `INTERVIEWEE` | 个人资料维护、岗位报名、简历上传、AI 面试、视频面试 |

前端路由会根据登录用户角色限制页面访问；后端 Spring Security 会根据接口路径进行权限校验。

## 主要页面

| 路径 | 页面 | 角色 |
| --- | --- | --- |
| `/` | 首页 | 公开 |
| `/login` | 登录/注册入口 | 公开 |
| `/admin` | 管理控制台 | `IT_ADMIN`、`HR_ADMIN`、`HR_USER` |
| `/interview/hr` | 面试后台 | `IT_ADMIN`、`HR_ADMIN`、`HR_USER` |
| `/user` | 面试者个人中心 | `INTERVIEWEE` |
| `/candidate/register` | 岗位报名 | `INTERVIEWEE` |
| `/interview/interviewee` | 面试者面试页面 | `INTERVIEWEE` |

## 业务逻辑使用方法

### 1. 登录系统

1. 使用默认后台账号登录 `/login`。
2. 登录成功后，后台用户进入 `/admin`，面试者进入 `/user`。
3. 前端会保存 JWT 到 `localStorage` 的 `demo-token`，并在后续请求中自动添加 `Authorization: Bearer <token>`。

### 2. 维护组织和员工信息

1. 使用 `itadmin`、`hradmin` 或 `hruser` 登录。
2. 进入 `/admin`。
3. 在控制台中维护部门、员工和系统挂接信息。
4. 相关功能对应后端 `/api/hr/**` 接口。

### 3. 创建招聘岗位

1. 后台用户进入 `/admin`。
2. 在招聘后台中新增岗位，填写岗位编码、岗位名称、部门、地点、人数、职责、要求、薪资范围、发布日期等信息。
3. 岗位状态为开放后，面试者可在报名页面看到该岗位。
4. 相关功能对应后端 `/api/recruitment/admin/jobs` 接口。

### 4. 面试者注册与报名

1. 面试者在 `/login` 注册账号。
2. 登录后进入 `/user` 完善个人资料。
3. 进入 `/candidate/register` 查看开放岗位。
4. 选择岗位后填写报名信息并提交。
5. 上传简历文件。
6. 报名记录可在个人中心查看。

### 5. 简历筛选

1. HR 在 `/admin` 的招聘后台查看候选人列表。
2. 可查看候选人信息和简历。
3. 可执行简历拒绝或发起面试。
4. 简历拒绝后候选人状态会更新，不再进入后续面试流程。

### 6. 配置 AI 面试知识库

1. HR 进入 `/interview/hr`。
2. 在知识库模块中创建知识库。
3. 为知识库添加知识点和知识内容。
4. 在岗位权重模块中为招聘岗位绑定知识库权重。
5. AI 面试会根据岗位关联的知识库生成问题和评分依据。

### 7. 配置 LLM 模型

1. 使用 `itadmin` 登录。
2. 进入 `/interview/hr` 的 LLM 配置模块。
3. 分别配置模型角色：`INTERVIEWER` 和 `SCORER`。
4. 填写模型名称、Base URL、API Key、提示词模板和评分规则。
5. `INTERVIEWER` 用于生成追问和面试官评价，`SCORER` 用于评分。

如果未正确配置 LLM，AI 面试相关功能可能无法正常生成问题或评分。

### 8. 发起面试流程

1. HR 在招聘后台选择候选人并发起面试，或进入 `/interview/hr` 的面试流程模块。
2. 选择候选人、岗位和面试参数。
3. 系统创建 `interview_process` 记录。
4. 面试流程会记录当前阶段、阶段状态、总状态、AI 分数阈值、防作弊切屏次数等信息。

### 9. 面试者参加 AI 面试

1. 面试者进入 `/interview/interviewee`。
2. 加载自己的面试流程。
3. 进入 AI 答题全屏模式。
4. 系统生成题目，面试者提交回答。
5. 后端调用 LLM 进行面试官评价和评分。
6. 系统记录每轮问答、分数、平均分和评价。
7. 当平均分达到阈值时进入 HR 人工审批；当达到最大轮数仍未达标时可自动拒绝。

### 10. 防作弊处理

1. 面试者 AI 面试页面会监听切屏等事件。
2. 前端通过 `/api/interview/interviewee/anti-cheat-event` 上报防作弊事件。
3. 系统累计切屏次数。
4. 达到阈值后，流程会转入 HR 人工审批。

### 11. HR 审批 AI 面试结果

1. HR 进入 `/interview/hr` 的面试流程模块。
2. 查看候选人的 AI 面试记录、平均分、面试官评价和防作弊状态。
3. 如果允许通过，审批后生成视频面试任务。
4. 如果不通过，流程结束。

### 12. 视频面试

1. HR 创建视频面试会话。
2. 面试者在 `/interview/interviewee` 加入视频面。
3. HR 在 `/interview/hr` 加入视频面。
4. 系统通过 WebRTC 交换 Offer、Answer 和 ICE Candidate。
5. 面试结束后上传录制文件。
6. HR 审批视频面试结果。

WebRTC 默认使用以下 STUN：

```text
stun:stun.l.google.com:19302
stun:stun.cloudflare.com:3478
```

公网或复杂 NAT 环境建议配置 TURN 服务。

### 13. 线下面试审批

1. 视频面试通过后，流程进入线下面试阶段。
2. HR 在线下面试完成后录入通过或不通过结果。
3. 通过后流程完成，不通过则流程结束。

### 14. 审计日志

系统会记录关键管理动作和面试动作，例如：

- 创建/修改/删除招聘岗位
- 删除候选人
- 简历拒绝
- 发起面试流程
- AI 审批
- 视频面试加入、录制上传和审批
- 线下面试审批
- 用户资料修改和密码重置

后台可在 `/admin` 查看审计日志。

## 数据库配置

系统通过环境变量或 `.env` 文件读取数据库配置。`application.yml` 会加载当前目录和上级目录的 `.env`：

```yaml
spring:
  config:
    import:
      - optional:file:.env[.properties]
      - optional:file:../.env[.properties]
```

常用变量：

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `DB_TYPE` | `sqlite` | 数据库类型，支持 `sqlite`、`mysql`、`pgsql` |
| `DB_URL` | 空 | 主数据库 JDBC URL |
| `DB_USERNAME` | 空 | 数据库用户名 |
| `DB_PASSWORD` | 空 | 数据库密码 |
| `SQLITE_FALLBACK_URL` | `jdbc:sqlite:autohr-dev.db` | SQLite 默认或回退连接 |
| `MIGRATION_ENABLED` | `true` | 是否执行表结构迁移 |
| `JWT_SECRET` | 本地默认值 | JWT 签名密钥，生产环境必须修改 |
| `JWT_EXPIRATION` | `86400000` | Token 有效期，单位毫秒 |

### SQLite 开发配置示例

```properties
DB_TYPE=sqlite
SQLITE_FALLBACK_URL=jdbc:sqlite:autohr-dev.db
JWT_SECRET=replace-with-a-secure-secret-at-least-32-chars
```

### MySQL 配置示例

```properties
DB_TYPE=mysql
DB_URL=jdbc:mysql://localhost:3306/autohr?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true
DB_USERNAME=root
DB_PASSWORD=your_password
JWT_SECRET=replace-with-a-secure-secret-at-least-32-chars
```

### PostgreSQL 配置示例

```properties
DB_TYPE=pgsql
DB_URL=jdbc:postgresql://localhost:5432/autohr
DB_USERNAME=postgres
DB_PASSWORD=your_password
JWT_SECRET=replace-with-a-secure-secret-at-least-32-chars
```

如果 MySQL 或 PostgreSQL 连接失败，系统会尝试回退到 SQLite。

## 面试相关配置

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `INTERVIEW_DISABLE_DEVTOOLS_SHORTCUTS` | `true` | 是否在面试页面禁用常见开发者工具快捷键 |
| `INTERVIEW_STUN_URLS` | `stun:stun.l.google.com:19302,stun:stun.cloudflare.com:3478` | WebRTC STUN 地址，逗号分隔 |
| `INTERVIEW_TURN_URLS` | 空 | WebRTC TURN 地址，逗号分隔 |
| `INTERVIEW_TURN_USERNAME` | 空 | TURN 用户名 |
| `INTERVIEW_TURN_CREDENTIAL` | 空 | TURN 密码或凭据 |

## API 模块概览

| 模块 | 前缀 | 说明 |
| --- | --- | --- |
| 认证与用户 | `/api/auth` | 登录、注册、当前用户、用户管理、审计日志 |
| HR 管理 | `/api/hr` | 仪表盘、部门、员工、系统挂接 |
| 招聘管理 | `/api/recruitment` | 岗位、候选人、简历上传与下载 |
| 面试管理 | `/api/interview` | 知识库、岗位权重、LLM、AI 面试、视频面试、审批 |

## 常用开发命令

### 后端

```bash
cd backend
mvn spring-boot:run
mvn test
mvn package
```

### 前端

```bash
cd frontend
npm install
npm run dev
npm run build
npm run lint
npm run format
```

## 注意事项

- 生产环境必须修改 `JWT_SECRET`，不要使用默认开发密钥。
- 默认账号密码仅适合本地演示，部署前应修改密码或删除默认账号。
- 视频面试依赖浏览器摄像头、麦克风和 WebRTC 能力。
- 公网视频面试建议配置 TURN，否则部分网络环境可能无法建立连接。
- 文件上传目录位于后端运行目录下的 `uploads`，迁移部署时需要一并备份。
- 前端 API 基础路径固定为 `/api`，开发环境通过 Vite 代理到后端。
