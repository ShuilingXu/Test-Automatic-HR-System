# Automatic HR System

Automatic HR System 是一个自动化人力资源与智能面试管理系统，覆盖后台用户管理、组织架构、员工档案、工资核算、人事统计、招聘岗位、候选人报名、简历管理、AI 面试、视频面试、线下面审批和审计日志等流程。

## 技术栈

### 后端

- Java 17
- Spring Boot 3.2.5
- Spring Web
- Spring Security
- Spring Validation
- MyBatis-Plus 3.5.6
- Apache POI（员工与工资 Excel 导入导出）
- JWT：`jjwt 0.12.5`
- 数据库驱动：SQLite、MySQL、PostgreSQL
- 连接池：HikariCP
- 工具库：Lombok、Hutool
- 构建工具：Maven

### 前端

- Vue 3.4
- Vite 7
- Vue Router 4
- Element Plus 2.7
- ECharts 6
- ExcelJS
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
│   │       ├── hr/               # 部门、员工、工资、统计、Excel、仪表盘
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
- Node.js 20.19+
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
| `/admin/dashboard` | 可配置人事仪表盘 | `IT_ADMIN`、`HR_ADMIN`、`HR_USER` |
| `/admin/payroll` | 月度工资核算 | `IT_ADMIN`、`HR_ADMIN`、`HR_USER` |
| `/admin/statistics` | 人事统计分析 | `IT_ADMIN`、`HR_ADMIN`、`HR_USER` |
| `/interview/hr` | 面试后台 | `IT_ADMIN`、`HR_ADMIN`、`HR_USER` |
| `/user` | 面试者个人中心 | `INTERVIEWEE` |
| `/candidate/register` | 岗位报名 | `INTERVIEWEE` |
| `/interview/interviewee` | 面试者面试页面 | `INTERVIEWEE` |

## 业务逻辑使用方法

### 1. 登录系统

1. 使用默认后台账号登录 `/login`。
2. 登录成功后，后台用户进入 `/admin`，面试者进入 `/user`。
3. 前端会保存 JWT 到 `localStorage` 的 `autohr-access-token`，并在后续请求中自动添加 `Authorization: Bearer <token>`。旧版本的 `demo-token` 会在首次读取时自动迁移，退出登录时新旧 key 会一并清理。`localStorage` 中的 Bearer Token 会被同源 JavaScript 读取，因此任何 XSS 都可能导致登录凭据泄露；生产环境不得注入第三方脚本，必须启用下文的 CSP 响应头与 HTTPS，并保持默认 2 小时或更短的 Token 有效期。

### 站点外观设置

`IT_ADMIN` 可在 `/admin/content` 的“站点外观”区域维护 Logo 地址、站点标题、副标题和页脚文本。公开首页、登录页、后台导航、浏览器标题与 favicon 会读取 `GET /api/site-settings` 并同步更新；管理接口 `GET/POST /api/site-settings/admin` 仅允许 `IT_ADMIN`。Logo 仅接受站内绝对路径或 HTTPS URL。字段名 `footerHtml` 为兼容既有接口保留，前端始终按纯文本展示，不解析 HTML。

### 2. 维护组织和员工信息

1. 使用 `itadmin`、`hradmin` 或 `hruser` 登录。
2. 进入 `/admin`。
3. 在控制台中维护部门、员工和系统挂接信息。
4. 相关功能对应后端 `/api/hr/**` 接口。

#### 仪表盘、统计与工资

- `/admin/dashboard` 汇总员工、部门、开放岗位、本月入离职和平均税前工资，并允许每个后台用户保存自己的卡片与图表配置。
- `/admin/statistics` 按月展示薪资、招聘、离职和部门分布统计。
- 仪表盘响应内嵌当月完整统计结果，前端不会再额外请求一次统计接口；停用部门、空名称部门及无员工部门不参与部门均值。
- `/admin/employees` 支持下载 Excel 模板并批量导入员工；导入结果会逐行返回成功数、失败数和错误原因。
- `/admin/payroll` 维护月度绩效、加班、社保与专项附加扣除，生成累计预扣法工资结果，并支持 Excel 批量导入和个税申报格式导出。
- 新建或调薪时可显式指定 `effectiveMonth`；未指定时使用员工 `hire_date` 所在月。回算早于首条调薪流水的历史月份时使用最早流水的调整前薪资，再回退员工当前基础薪资。
- 工资结果可以锁定，锁定后禁止改写相关月度输入；只有 `HR_ADMIN` 或 `IT_ADMIN` 可以解锁。导入、生成、锁定、解锁、删除和导出操作都会写入审计日志。
- PostgreSQL、SQLite 和 MySQL 均使用原子 upsert 生成工资单；冲突更新带 `locked=0` 守卫，并发生成或生成期间锁定不会覆盖已锁定结果。

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
5. `INTERVIEWER` 用于生成追问和面试官评价，`SCORER` 用于评分。评分器必须只返回包含 `score`（0-100 整数）和 `reason`（非空评分理由）的严格 JSON；缺少理由的模型输出会被标记为可重试失败。

API Key 使用 AES-GCM 加密后落库；`CONFIG_ENCRYPTION_KEY` 未配置时复用 `JWT_SECRET` 派生密钥，因此上线后必须稳定保存所选密钥。LLM Endpoint 默认仅允许可解析到公网地址的 `http/https` URL，以阻止回环和内网 SSRF；仅当模型确实位于可信 VPC 时设置 `LLM_ALLOW_PRIVATE_ADDRESSES=true`。如果未正确配置 LLM，AI 面试相关功能可能无法正常生成问题或评分。

### 8. 发起面试流程

1. HR 在招聘后台选择候选人并发起面试，或进入 `/interview/hr` 的面试流程模块。
2. 先在“流程模板”中创建可复用流程：按顺序添加 AI 面试或视频面试阶段，并为每个阶段指定展示名称；每个 AI 阶段必须选择自己的知识库。
3. 支持重复类型阶段，例如“技术 AI 一面 -> 技术 AI 二面 -> 用人经理视频一面 -> 终面视频二面”。流程可只包含 AI、只包含视频，或两者组合。
4. 在候选人详情、招聘控制台或面试流程入口中选择一个启用模板后发起面试。系统会将模板阶段复制为该候选人的独立快照，后续修改模板不会影响已发起的流程。
5. 系统创建 `interview_process` 和对应的 `interview_process_stage` 记录，并记录当前阶段、阶段状态、总状态、AI 分数阈值、防作弊切屏次数等信息。

#### 流程模板与审批

- 模板入口：`/interview/hr/templates`。已被使用的模板不能删除，可停用以阻止后续发起。
- 仅 `HR_ADMIN` 可以新建、编辑或删除流程模板；`HR_USER` 可以读取启用模板并在发起面试时选择。
- AI 阶段沿用原有的题目生成、追问、评分和人工审批逻辑；题目、评分和录制按流程阶段隔离，因此同一流程中的多轮 AI 面试不会相互混用。
- 视频阶段沿用原有的 WebRTC、录制、转写、总结和人工审批逻辑；每个人类视频面试都有独立会话和录制。
- HR 审批通过后，系统自动进入模板中的下一个阶段；最后一个阶段通过后，候选人进入通过状态。任一阶段不通过会结束该流程。

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
3. 面试进行期间，前端每 30 秒调用独立的 `/api/interview/interviewee/heartbeat/{processId}` 上报在线状态；心跳不会增加切屏次数，服务端对同一流程设置 20 秒最小写入间隔以避免高频数据库更新。
4. 系统累计切屏次数。
5. 达到阈值后，流程会转入 HR 人工审批。

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

如果结束后 10 分钟仍未完整收到双方录像，系统会将会话标记为“录像缺失”并进入待审批，不阻止 HR 作出决定；迟到录像补齐后仍会自动触发合并和概要生成。

视频会话若持续无候选人心跳、加入、SDP/ICE 或录像上传活动，会由后端自动请求结束并进入上述录像合并/缺失兜底流程；这也覆盖创建后双方都未加入便关闭页面的会话。默认非活跃超时为 30 分钟，可用 `INTERVIEW_VIDEO_INACTIVE_TIMEOUT_MINUTES` 调整；扫描周期由 `INTERVIEW_VIDEO_INACTIVE_SCAN_INTERVAL_MS` 控制。

WebRTC 默认使用以下 STUN：

```text
stun:stun.l.google.com:19302
stun:stun.cloudflare.com:3478
```

公网或复杂 NAT 环境建议配置 TURN 服务。

#### 本地 coturn 转发服务

项目提供了 Docker 方式的本地 coturn 配置，用于 WebRTC TURN 中继转发测试。

1. 安装 Docker Desktop 或 Docker Engine。
2. 修改 `.env` 中 TURN 配置。后端只向已登录用户签发短期 HMAC-SHA1 凭据，不再向浏览器下发长期 TURN 密码。浏览器和 coturn 在同一台机器时可用：

```properties
INTERVIEW_TURN_URLS=turn:127.0.0.1:3478?transport=udp,turn:127.0.0.1:3478?transport=tcp
INTERVIEW_TURN_SHARED_SECRET=replace-with-a-long-random-secret
INTERVIEW_TURN_CREDENTIAL_TTL_SECONDS=3600
TURN_HOST=127.0.0.1
TURN_REALM=autohr.local
TURN_MIN_PORT=49160
TURN_MAX_PORT=49200
```

coturn 必须使用同一份共享密钥启用 REST 鉴权；删除静态 `user=` 配置，并启用：

```properties
use-auth-secret
static-auth-secret=replace-with-a-long-random-secret
realm=autohr.local
```

Docker Compose 会把 `.env` 中的 `INTERVIEW_TURN_SHARED_SECRET` 作为 `static-auth-secret` 传给 coturn；两者必须完全一致。真实密钥只保存在服务器配置中，不应提交到 Git；修改后需要同时重启后端和 coturn。

如果使用手机或局域网其他机器访问前端，`127.0.0.1` 必须改为运行 coturn 的电脑局域网 IP，例如 `192.168.1.20`：

```properties
INTERVIEW_TURN_URLS=turn:192.168.1.20:3478?transport=udp,turn:192.168.1.20:3478?transport=tcp
TURN_HOST=192.168.1.20
```

3. 启动 coturn：

```bash
# Windows
start-coturn.bat

# Linux/macOS
./start-coturn.sh
```

4. 重启后端，使 `/api/interview/ice-servers` 返回新的 TURN 配置。

5. 停止 coturn：

```bash
# Windows
stop-coturn.bat

# Linux/macOS
./stop-coturn.sh
```

本地 Docker 配置会映射 `3478/tcp`、`3478/udp` 和 `49160-49200/udp` 中继端口。`TURN_EXTERNAL_IP` 必须填写客户端可访问的主机地址；容器内网地址默认在启动时探测，固定容器网络时可用 `TURN_DOCKER_PRIVATE_IP` 覆盖。该本地配置未提供证书，因此不映射 `5349` TLS 端口。Windows 或防火墙环境下需允许 Docker/WSL 访问已映射端口；生产环境如需 TURN TLS，必须另行配置证书后再开放 TLS 端口。

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

`.env` 使用 Java Properties 语法，由 Spring Boot 和系统配置服务统一解析；生产 systemd 单元还会通过安装目录下的 `.env` 将同一文件导入进程（默认路径为 `/opt/auto-hr/.env`）。真实进程环境变量的优先级高于 `.env` 文件，适合由容器或 systemd 临时覆盖配置。通过系统配置页面保存时会自动转义空格、反斜杠、`=`、`:`、`#` 和 `!`，因此密码、Token 和 Endpoint 中的特殊字符可在重启后原样生效。手工编辑时如需字面反斜杠，请写成 `\\`。

常用变量：

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `DB_TYPE` | `sqlite` | 数据库类型，支持 `sqlite`、`mysql`、`pgsql` |
| `DB_URL` | 空 | 主数据库 JDBC URL |
| `DB_USERNAME` | 空 | 数据库用户名 |
| `DB_PASSWORD` | 空 | 数据库密码 |
| `SQLITE_FALLBACK_URL` | `jdbc:sqlite:autohr-dev.db` | SQLite 默认或回退连接 |
| `MIGRATION_ENABLED` | `true` | 是否执行表结构迁移 |
| `JWT_SECRET` | 无（必填） | JWT 签名密钥，必须使用长度不少于 32 个字符的随机值 |
| `CONFIG_ENCRYPTION_KEY` | `JWT_SECRET` | LLM API Key 等配置密文的独立加密密钥；设置后必须稳定保管 |
| `JWT_EXPIRATION` | `7200000` | Token 有效期，单位毫秒；默认 2 小时 |
| `REDIS_HOST` | `127.0.0.1` | 验证码、验证状态和限流使用的 Redis 地址 |
| `REDIS_PORT` | `6379` | Redis 端口 |
| `REDIS_PASSWORD` | 空 | Redis 密码，只保存在服务器 `.env` 中 |
| `REDIS_SSL_ENABLED` | `false` | 是否使用 Redis TLS |
| `AUTH_TRUST_FORWARDED_HEADERS` | `false` | 仅当可信反向代理覆盖 `X-Forwarded-For` 时启用 |
| `LLM_ALLOW_PRIVATE_ADDRESSES` | `false` | 仅可信 VPC/内网模型 Endpoint 需要开启 |

### SQLite 开发配置示例

```properties
DB_TYPE=sqlite
SQLITE_FALLBACK_URL=jdbc:sqlite:autohr-dev.db
JWT_SECRET=generate-a-unique-secret-with-at-least-32-characters
```

### Redis 认证安全状态

图形验证码、短信/邮件验证码以及登录和验证码发送限流均存储在 Redis 中。Redis 不可用时不会回退到进程内存，以免多实例状态不一致。生产 `.env` 必须配置 `REDIS_HOST`、`REDIS_PORT` 和 `REDIS_PASSWORD`；`REDIS_PASSWORD` 不得提交到 Git，也不会在系统配置页面暴露。仅当 OpenResty 或其他可信反向代理会覆盖 `X-Forwarded-For` 时，才设置 `AUTH_TRUST_FORWARDED_HEADERS=true`。

新注册、找回密码、首次强制改密和管理员重置密码均要求至少 8 位且同时包含字母和数字。保留的默认账号初始密码仍为 `123456`，首次登录只能进入强制改密页面。

### MySQL 配置示例

```properties
DB_TYPE=mysql
DB_URL=jdbc:mysql://localhost:3306/autohr?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=true
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

开发环境可在 MySQL 或 PostgreSQL 连接失败时回退到 SQLite；生产环境必须显式配置 `DB_TYPE`，且非 SQLite 数据库必须提供 `DB_URL`。

全库 22 张业务表统一使用数据库生成主键，不保留手工主键例外：SQLite 使用 `INTEGER PRIMARY KEY AUTOINCREMENT`，MySQL 使用 `AUTO_INCREMENT`，PostgreSQL 使用 Identity/Sequence。应用启动迁移会为既有 MySQL/PostgreSQL 表补齐生成器并将序列推进到现有最大主键之后，业务代码不再计算或手工写入下一主键。

应用还会建立以下业务唯一约束，防止并发请求产生重复报名、重复面试流程或重复待入职员工：

- `recruitment_candidate(job_id, interviewee_user_id)`
- `interview_process(recruitment_candidate_id)`
- `hr_employee(source_candidate_id)`

升级既有数据库前可先执行只读预检：

```sql
SELECT job_id, interviewee_user_id, COUNT(*)
FROM recruitment_candidate
WHERE interviewee_user_id IS NOT NULL
GROUP BY job_id, interviewee_user_id
HAVING COUNT(*) > 1;

SELECT recruitment_candidate_id, COUNT(*)
FROM interview_process
WHERE recruitment_candidate_id IS NOT NULL
GROUP BY recruitment_candidate_id
HAVING COUNT(*) > 1;

SELECT source_candidate_id, COUNT(*)
FROM hr_employee
WHERE source_candidate_id IS NOT NULL
GROUP BY source_candidate_id
HAVING COUNT(*) > 1;
```

任一查询返回记录时，启动迁移会拒绝建立唯一索引并终止启动，不会自动删除或合并历史数据。确认并处理重复记录后再重新启动服务。

### 生产 PostgreSQL 与邮件

生产环境使用 PostgreSQL 时请设置 `DB_FALLBACK_ENABLED=false`。这样 PostgreSQL 不可用时服务会拒绝启动，而不会意外将新数据写入 SQLite。将 `.env.example` 复制为 `.env` 后，至少配置：

```properties
DB_TYPE=pgsql
DB_URL=jdbc:postgresql://127.0.0.1:5432/hrsystem
DB_USERNAME=hrsystem
DB_PASSWORD=change-this-password
DB_FALLBACK_ENABLED=false
JWT_SECRET=generate-a-unique-secret-with-at-least-32-characters

SMTP_HOST=smtp.office365.com
SMTP_PORT=587
SMTP_USERNAME=your-office365-mailbox@example.com
SMTP_PASSWORD=your-smtp-password
SMTP_FROM=your-office365-mailbox@example.com
SMTP_SSL_ENABLED=false
SMTP_STARTTLS_ENABLED=true
```

候选人注册页可选择使用手机号或邮箱接收验证码，两个联系方式互斥；短信验证码需要另行配置阿里云短信变量。

### SQLite 迁移到 PostgreSQL

迁移主机先安装 PostgreSQL 客户端并为迁移器创建独立 Python 环境：

```bash
sudo apt-get install -y postgresql-client python3-venv
python3 -m venv .venv-migrate
.venv-migrate/bin/pip install 'psycopg[binary]'
```

在已经由应用创建好 PostgreSQL 表结构后，先用只读检查确认源数据和目标表：

```bash
python3 scripts/migrate-sqlite-to-postgres.py autohr.db --dsn "$POSTGRES_DSN" --dry-run
```

迁移器默认拒绝覆盖含有数据的 PostgreSQL 目标。确认需要以 SQLite 数据替换目标后，显式执行：

```bash
python3 scripts/migrate-sqlite-to-postgres.py autohr.db --dsn "$POSTGRES_DSN" --force-overwrite
```

覆盖迁移要求系统已安装 PostgreSQL 客户端工具 `pg_dump`。开始覆盖前，脚本会把 PostgreSQL
完整备份到 `backups/postgres-migration/`，默认保留 5 天并自动清理过期备份；可用
`--backup-dir` 和 `--backup-retention-days` 调整。迁移在单个数据库事务中执行，包含流程模板、
模板阶段、薪资月表和仪表盘配置等 `schema.sql` 中的全部 29 张业务表。脚本启动时会校验迁移表清单与
`schema.sql` 一致，写入后逐表核对 SQLite 与 PostgreSQL 行数；任何错误都会显式回滚全部目标库改动，
源 SQLite 文件不会被改写。

### 一键发行包与 systemd

根目录和发行包使用同一个 `deploy-ubuntu.sh`/`deploy.sh`。它会安装缺失的 Ubuntu 运行依赖、准备本机 Redis、检查 FFmpeg 与 Tesseract、配置本机 coturn、创建低权限 `autohr` 用户、安装 systemd 服务，并在 60 秒健康检查失败时恢复上一版后端、前端、service 和 `.env`。默认安装目录为 `/opt/auto-hr`，服务监听 `127.0.0.1:8081`；可用 `--install-dir`、`--server-address`、`--server-port` 和 `--spring-profile` 调整，不需要修改脚本源码（也可通过对应的 `AUTO_HR_INSTALL_DIR`、`AUTO_HR_SERVER_ADDRESS`、`AUTO_HR_SERVER_PORT`、`AUTO_HR_SPRING_PROFILE` 环境变量设置）。公网访问应由 OpenResty/Nginx 终止 TLS 并反向代理 `/api`。

在 Ubuntu 源码目录直接运行即可构建、打包并安装：

```bash
bash deploy-ubuntu.sh --web-root /var/www/auto-hr
```

首次运行未传 `--env` 时，脚本从 `.env.example` 初始化本地 SQLite，并生成 JWT、Redis 和 TURN 密钥，适合立即验证。生产 PostgreSQL 可先准备完整环境文件后传入；后续升级不传 `--env` 会保留服务器现有配置。外部托管 TURN 时传 `--skip-coturn`，已由镜像预装依赖时传 `--skip-dependencies`。

GitHub Actions 在 `main` 分支推送时会按顺序执行后端测试、前端构建、把前端静态文件嵌入 Spring Boot JAR，并上传 `auto-hr-release.zip`。每次 `main` 构建都会创建或更新 `build-<运行编号>` GitHub 预发布版；推送 `v*` 标签会创建正式 Release。远程部署不由 GitHub Actions 自动执行，需要下载发行包后在目标服务器上手动运行包内的 `deploy.sh`。

本地也可生成同一发行包：

```bash
bash scripts/package-release.sh
```

解压发行包后，其中唯一的部署入口仍是 `deploy.sh`：

```bash
sudo ./deploy.sh --env /path/to/production.env --web-root /var/www/auto-hr
sudo systemctl status auto-hr
```

`--web-root` 可省略，此时前端继续由已嵌入 JAR 的静态资源提供；指定后脚本会在后端健康后再同步到站点目录，失败会还原先前的站点文件。生产服务以 production profile 启动、开机自启，并在异常退出时自动重启。

生产 OpenResty/Nginx 必须在 HTTPS 站点响应中设置 CSP。`frontend/index.html` 的 meta 仅作为静态页面兜底，不能替代响应头；建议在站点 `server` 块加入以下与后端一致的策略，并确认没有其他配置覆盖它：

```nginx
add_header Content-Security-Policy "default-src 'self'; base-uri 'self'; object-src 'none'; frame-ancestors 'none'; form-action 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data: blob: https:; font-src 'self' data:; media-src 'self' data: blob: https:; connect-src 'self' https: wss:; worker-src 'self' blob:; child-src 'self' blob:" always;
```

该策略不允许第三方脚本。生产站点、API、Logo、对象存储下载地址和 WebSocket 必须使用 TLS；确需引入新外部来源时，应逐项审查后只放行精确域名，不要放宽 `script-src`。

## 面试相关配置

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `INTERVIEW_DISABLE_DEVTOOLS_SHORTCUTS` | `true` | 是否在面试页面禁用常见开发者工具快捷键 |
| `INTERVIEW_STUN_URLS` | `stun:stun.l.google.com:19302,stun:stun.cloudflare.com:3478` | WebRTC STUN 地址，逗号分隔 |
| `INTERVIEW_TURN_URLS` | 空 | WebRTC TURN 地址，逗号分隔 |
| `INTERVIEW_TURN_SHARED_SECRET` | 空 | coturn REST 鉴权共享密钥；必须与 coturn 的 `static-auth-secret` 一致 |
| `INTERVIEW_TURN_CREDENTIAL_TTL_SECONDS` | `3600` | 服务端签发的 TURN HMAC 临时凭据有效期，限制为 60 至 86400 秒 |

## API 模块概览

| 模块 | 前缀 | 说明 |
| --- | --- | --- |
| 认证与用户 | `/api/auth` | 登录、注册、当前用户、用户管理、审计日志 |
| HR 管理 | `/api/hr` | 仪表盘、统计、部门、员工、员工 Excel 导入 |
| 工资管理 | `/api/hr/payroll` | 月度输入、累计预扣计算、锁定、Excel 导入导出 |
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

统计图表使用 ECharts；浏览器端 xlsx 模板生成选择 ExcelJS。ExcelJS 能直接设置工号、身份证号和手机号列的文本格式，并提供稳定的单元格样式与列序控制，更适合本项目对导入模板格式的要求。

```bash
cd frontend
npm install
npm test
npm run dev
npm run build
npm run lint
npm run format
```

## 注意事项

- 生产环境必须修改 `JWT_SECRET`，不要使用默认开发密钥。
- 默认账号保留初始密码 `123456`，首次登录必须按强密码规则完成改密；可在用户管理中按需禁用账号。
- 默认情况下，保留默认密码的账号会在每次启动时继续被标记为必须改密。仅在临时运维例外时，可在部署 `.env` 设置 `AUTH_BOOTSTRAP_DEFAULT_PASSWORD_EXEMPT_USERNAMES=itadmin`；该设置会让该账号保留 `123456` 且不进入改密页。恢复强制改密时删除该变量并重启服务。
- 视频面试依赖浏览器摄像头、麦克风和 WebRTC 能力。
- 公网视频面试建议配置 TURN，否则部分网络环境可能无法建立连接。
- 文件上传目录位于后端运行目录下的 `uploads`，迁移部署时需要一并备份。
- 前端 API 基础路径固定为 `/api`，开发环境通过 Vite 代理到后端。

## Consistency And Operations

- List endpoints return `{ items, total, page, pageSize }`. The web client keeps existing views compatible by exposing `items` as response data and retaining pagination metadata separately.
- AI questions are durable background tasks. Question generation retries automatically with bounded exponential backoff; failed generation never becomes an answerable error question. AI answers use a database lease: a retry with identical content is idempotent, while a different answer for the same question is rejected.
- Process templates use a version field for optimistic concurrency. A stale save or delete is rejected and the editor must refresh before trying again. A completed final interview changes the candidate state to `OFFER_PENDING`.
- PostgreSQL and MySQL deployments add restrictive foreign keys for user, employee, candidate, process, stage, AI-record, and video-session relationships. Migration first checks for orphaned rows and stops if any are found. The sole audited legacy exception is PostgreSQL `hr_employee.source_candidate_id`: before its foreign key is added, invalid nullable references are archived to `database_migration_orphan_archive`, cleared in one transaction, and retained for five days. The archive supports an optional JSON snapshot for auditable manual data repair. No other orphaned relationship is repaired automatically. SQLite keeps its existing schema because adding foreign keys to existing SQLite tables requires a table rebuild.
- LLM debug logging is disabled by default. When explicitly enabled, only provider/model metadata, lengths, and SHA-256 digests are written under `logs/`; daily metadata logs older than three days are removed. Prompt, answer, and provider-response text are not persisted.
- LLM API keys are encrypted at rest. On startup, legacy plaintext values are automatically rewritten as ciphertext. Rotating `CONFIG_ENCRYPTION_KEY` requires re-saving the LLM secrets with the old key available first.
- Every main-branch build publishes a prerelease package. The workflow retains the latest five prereleases across all release pages and permanently retains formal `v*` releases.

## S3 兼容对象存储

可选归档功能支持 AWS S3、MinIO 以及提供 S3 兼容接口的 OSS Endpoint。文件始终会
保留在本地，以供视频处理、转写和受权限保护的下载使用；归档连接或鉴权失败只会记录
告警，不会删除本地上传文件，也不会中断面试流程。

在系统配置的“对象存储”页签或 `.env` 中配置 `S3_ENABLED`、外网
`S3_ENDPOINT`、`S3_REGION`、`S3_BUCKET`、`S3_ACCESS_KEY_ID`、
`S3_SECRET_ACCESS_KEY`、`S3_SESSION_TOKEN`、`S3_PREFIX` 和
`S3_PATH_STYLE_ACCESS`。安全兼容开关为 `S3_ALLOW_HTTP_ENDPOINTS` 和
`S3_ALLOW_PRIVATE_ENDPOINTS`。开启 `S3_INTERNAL_ENDPOINT_ENABLED` 后还必须配置
`S3_INTERNAL_ENDPOINT`：服务端上传通过该内网/VPC 地址发送，浏览器访问始终签发
到外网 `S3_ENDPOINT`；关闭该开关时上传和访问均使用外网 Endpoint。Endpoint 默认
必须使用 HTTPS 且不能解析到私网、环回或链路本地地址。可信 MinIO/VPC 部署可显式
开启 `S3_ALLOW_PRIVATE_ENDPOINTS`；只有无法提供 TLS 的可信 Endpoint 才应同时开启
`S3_ALLOW_HTTP_ENDPOINTS`，因为 HTTP 会以明文传输对象存储凭据和数据。仅开启内网
上传不会自动放行 HTTP 或私网地址。归档对象不可用时下载自动回退至本地文件。该身份
需要对指定 Bucket 与前缀拥有 `s3:PutObject`、`s3:GetObject`、`s3:HeadObject` 和
`s3:DeleteObject` 权限；候选人删除或替换简历后会尽力删除旧 S3 对象，本地回退文件
同步清理。短信、语音转写和 S3 均使用独立凭据。

归档文件下载先由已登录用户通过 Bearer 请求获取五分钟有效的预签名 URL，再由浏览器直接打开该 URL；浏览器不会将 JWT 发送到对象存储。对象尚未归档或对象存储不可用时，前端必须回退到原有的同源、带 Bearer 的文件下载接口。
