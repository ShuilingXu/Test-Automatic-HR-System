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

如果结束后 10 分钟仍未完整收到双方录像，系统会将会话标记为“录像缺失”并进入待审批，不阻止 HR 作出决定；迟到录像补齐后仍会自动触发合并和概要生成。

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

本地 Docker 配置会映射 `3478/tcp`、`3478/udp`、`5349/tcp`、`5349/udp` 和 `49160-49200/udp` 中继端口。Windows 或防火墙环境下需允许 Docker/WSL 访问这些端口。生产环境应使用公网 IP/域名、长随机共享密钥和短期凭据，并按需配置 TLS。

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

`.env` 使用 Java Properties 语法，由 Spring Boot 和系统配置服务统一解析；systemd 不再重复解析该文件。通过系统配置页面保存时会自动转义空格、反斜杠、`=`、`:`、`#` 和 `!`，因此密码、Token 和 Endpoint 中的特殊字符可在重启后原样生效。手工编辑时如需字面反斜杠，请写成 `\\`。

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
| `JWT_EXPIRATION` | `86400000` | Token 有效期，单位毫秒 |
| `REDIS_HOST` | `127.0.0.1` | 验证码、验证状态和限流使用的 Redis 地址 |
| `REDIS_PORT` | `6379` | Redis 端口 |
| `REDIS_PASSWORD` | 空 | Redis 密码，只保存在服务器 `.env` 中 |
| `REDIS_SSL_ENABLED` | `false` | 是否使用 Redis TLS |
| `AUTH_TRUST_FORWARDED_HEADERS` | `false` | 仅当可信反向代理覆盖 `X-Forwarded-For` 时启用 |

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
模板阶段和已创建的流程阶段；任何错误都会显式回滚全部目标库改动，源 SQLite 文件不会被改写。

### 一键发行包与 systemd

GitHub Actions 在 `main` 分支推送时会按顺序执行后端测试、前端构建、把前端静态文件嵌入 Spring Boot JAR，并上传 `auto-hr-release.zip`。每次 `main` 构建都会创建或更新 `build-<运行编号>` GitHub 预发布版；推送 `v*` 标签会创建正式 Release，Release 发布不依赖部署 Secrets，不会因未配置服务器而跳过。若在仓库 Secrets 配置 `DEPLOY_HOST`、`DEPLOY_USER`、`DEPLOY_SSH_KEY`、`DEPLOY_WEB_ROOT` 和可选的 `DEPLOY_PORT`，`main` 推送会自动发布到服务器；`DEPLOY_WEB_ROOT` 应为 OpenResty/Nginx 的站点静态目录，例如 1Panel OpenResty 的 `/opt/1panel/www/sites/hr.zroevn.cn/index`。首次部署还必须配置 `DEPLOY_INITIAL_ENV`，其内容为完整的 `.env` 文件，至少包含 `JWT_SECRET`、`DB_TYPE`、`DB_URL`、`DB_USERNAME`、`DB_PASSWORD`、`REDIS_HOST`、`REDIS_PORT`、`REDIS_PASSWORD` 和 `INTERVIEW_TURN_SHARED_SECRET`；已有服务器上的 `.env` 不会被覆盖。未配置部署 Secrets 时只跳过远端部署，构建产物和 GitHub 预发布版仍会正常生成。

自动部署会先把新后端写入同文件系统的暂存 JAR，等待旧 systemd 进程完全停止后再原子替换，避免运行中的 JVM 读取到被覆盖的归档。新版本无法启动或 60 秒内未通过健康检查时，流水线会恢复上一版 JAR 并重新启动服务，同时将本次部署标记为失败。

本地也可生成同一发行包：

```bash
bash scripts/package-release.sh
```

解压后保留已有 `.env` 与 `uploads/`，将 `auto-hr.service` 安装到 `/etc/systemd/system/auto-hr.service`，并执行：

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now auto-hr
sudo systemctl status auto-hr
```

默认服务监听 `127.0.0.1` 所在主机的 `8081` 端口，适合由 OpenResty/Nginx 反向代理；服务以生产 profile 启动、开机自启，并在异常退出时自动重启。

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
- 默认账号保留初始密码 `123456`，首次登录必须按强密码规则完成改密；可在用户管理中按需禁用账号。
- 视频面试依赖浏览器摄像头、麦克风和 WebRTC 能力。
- 公网视频面试建议配置 TURN，否则部分网络环境可能无法建立连接。
- 文件上传目录位于后端运行目录下的 `uploads`，迁移部署时需要一并备份。
- 前端 API 基础路径固定为 `/api`，开发环境通过 Vite 代理到后端。

## S3 兼容对象存储

可选归档功能支持 AWS S3、MinIO 以及提供 S3 兼容接口的 OSS Endpoint。文件始终会
保留在本地，以供视频处理、转写和受权限保护的下载使用；归档连接或鉴权失败只会记录
告警，不会删除本地上传文件，也不会中断面试流程。

在系统配置的“对象存储”页签或 `.env` 中配置 `S3_ENABLED`、外网
`S3_ENDPOINT`、`S3_REGION`、`S3_BUCKET`、`S3_ACCESS_KEY_ID`、
`S3_SECRET_ACCESS_KEY`、`S3_SESSION_TOKEN`、`S3_PREFIX` 和
`S3_PATH_STYLE_ACCESS`。开启 `S3_INTERNAL_ENDPOINT_ENABLED` 后还必须配置
`S3_INTERNAL_ENDPOINT`：服务端上传通过该内网/VPC 地址发送，浏览器访问始终签发
到外网 `S3_ENDPOINT`；关闭该开关时上传和访问均使用外网 Endpoint。归档对象不可用
时下载自动回退至本地文件。该身份需要对指定 Bucket 与前缀拥有
`s3:PutObject`、`s3:GetObject` 和 `s3:HeadObject` 权限。短信、语音转写和 S3 均使用独立凭据。
