# SpeakFlow v1.0 · 正式版候选源码（Release Candidate）

> **定位**：个人优先的每日英语口语学习站。模块化单体，MySQL 表结构及服务端权限检查预留多用户扩展。  
> **状态**：功能实现完成；静态语法与数据校验通过，但本构建环境缺少 Maven、Docker 与外部依赖网络，**尚未完成 Maven/Node 正式编译、MySQL 集成测试和线上验收**。请勿将此源码直接作为已验证生产系统公开部署。

## 一、功能

- **每日课程**：系统按学习计划时区自动检查当天课程，默认06:30开始生成、07:30发布；支持管理员手动生成及任务记录查询。同日课程有数据库唯一约束与任务幂等保护。
- **内容结构**：150–220词英语文章、逐段中文译文、8个IPA词汇、5个重点表达、2个连读提示、6个口语练习。AI 输出通过服务端基础校验，**音标和翻译尚未接入权威词典人工校验**。
- **文章学习**：五步学习进度、双语对照、难词收藏、单句播放、全文播放、历史归档和统计。
- **主动输出**：句型替换、语音练习、文字提交、AI语法/自然度反馈和重复作答记录。
- **录音识别**：浏览器 MediaRecorder 上传音频 → 服务端调用 ASR → 取得英文转写 → AI评价。服务器不持久保存原始录音（仅保存转写与反馈）。
- **发音**：无云服务时浏览器 TTS；配置 AI 后可在设置中启用云端 TTS，音频按内容哈希缓存在服务端。
- **个人与多用户预留**：会话鉴权、CSRF 防护、计划成员关系、用户级进度与复习记录、资源归属校验。MVP不开放公共注册。
- **原始数据**：Day22–29 八篇历史原文及译文的初始化数据；已存在的数据不会被重新覆盖。

## 二、本地 Docker 一键启动

**要求**：Docker Engine / Docker Desktop + Compose，且可访问 Docker Hub、npm 与 Maven 仓库。

```bash
cp .env.example .env
# 编辑 .env：为 DB_PASSWORD、MYSQL_ROOT_PASSWORD、APP_ADMIN_PASSWORD 设置不同的强密码
# APP_ADMIN_PASSWORD 至少12字符
chmod 600 .env
docker compose up --build -d
```

访问：`http://localhost:5173`。登录账户使用 `.env` 中配置的 `APP_ADMIN_USERNAME`、`APP_ADMIN_PASSWORD`。

初次启动自动完成 Flyway V1/V2 迁移，导入8篇种子课程。服务端没有直接暴露公网端口，Web 端默认只监听宿主机 `127.0.0.1:5173`。

```bash
docker compose ps
docker compose logs -f server
docker compose down
```

> 无须 AI Key 也能登录、阅读、朗读（浏览器 TTS）、收藏、复习、提交文字回答。**不能**在未配置 AI 的情况下自动生成课程或获得真实 AI 评价。

## 三、开启 AI（可选）

修改 `.env`：

```dotenv
AI_PROVIDER=openai
AI_API_KEY=填入自己的API密钥
AI_MODEL=gpt-4.1-mini
AI_TTS_MODEL=tts-1
AI_ASR_MODEL=whisper-1
AI_TTS_VOICE=alloy
```

重启服务：`docker compose up -d --build server web`。

启用后：

1. 系统在每天06:30（学习计划默认 `Asia/Shanghai`）起尝试生成当天内容；07:30及以后发布；每10分钟轮询一次。可在“设置 → 每日 AI 课程生成”手动生成指定日期。系统如果检测到已有当天课程，会拒绝覆盖。
2. 云端 TTS 需要用户在“设置”中主动开启；首次播放调用接口并缓存音频，重复播放复用缓存。
3. 正式录音需要 `localhost` 或 HTTPS，获得浏览器麦克风许可后才能使用。录音直接转写并评估，不长期存放音频文件。
4. AI 错误会记录到生成任务中；失败任务可在设置界面手动重新尝试。

**提示**：本版本使用传统 Chat Completions JSON 输出接口。内容有效性校验不等于事实、音标、原创性或翻译质量的人工审核，请检查实际生成的文章。云端接口会产生费用，建议设置模型费用限额。

## 四、连接已有远程 MySQL（请先建独立空库）

严禁直接向含有其他业务数据的数据库执行未经审核的迁移。务必先备份，并创建专门的 `speakflow` 数据库与最小权限账号。**不要在 Web 应用中使用远程 MySQL root 账户。**

示例由数据库管理员执行（替换占位符）：

```sql
CREATE DATABASE IF NOT EXISTS speakflow CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER 'speakflow_app'@'%' IDENTIFIED BY 'REPLACE_WITH_STRONG_PASSWORD';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, DROP, INDEX, REFERENCES
ON speakflow.* TO 'speakflow_app'@'%';
```

`DROP` 和其他 DDL 权限用于初次及后续 Flyway 迁移，部署稳定后可根据权限管理策略收紧。

```bash
cp .env.remote.example .env
# 按需填写 DB_HOST、DB_PORT、DB_NAME、DB_USERNAME、DB_PASSWORD、APP_ADMIN_PASSWORD
# 请优先启用数据库 TLS（DB_SSL_MODE=REQUIRED；需要数据库端支持）
chmod 600 .env
docker compose -f compose.remote.yaml up --build -d
```

该 Compose 文件不会启动本地 MySQL，直接使用 `.env` 指定的远程服务器。端口和地址均应在部署机器上设置，不在源码中放入真实数据库凭据。

### 数据库升级

- V1：创建用户、学习计划、课文、句段、词汇、表达、练习、步骤进度、口语作答和复习表。
- V2：增量添加生成任务、AI 音频缓存、连读提示、生成元数据与录音练习元数据。**不会重建 V1 表或删除学习记录**。
- 建议：升级前备份数据库；先在测试数据库运行 Flyway，确认后再执行生产迁移。

## 五、界面与 API

主要页面：今日学习、文章归档、阅读与词汇、跟读、句型替换、独立表达、表达复习、统计、设置。

新增接口（`/api/v1`）：

| 方法 | 地址 | 功能 |
| --- | --- | --- |
| GET | `/features` | AI / TTS / ASR 功能状态 |
| GET | `/plans` | 当前用户的学习计划 |
| GET | `/admin/generation-jobs` | 当前计划的生成任务记录 |
| POST | `/admin/generate-lesson` | 仅计划所有者，手动生成课程 |
| GET | `/lessons/{id}/audio?segmentId=...` | 可缓存的 TTS MP3，必须有课程访问权 |
| POST | `/exercises/{id}/audio-attempts` | 服务端语音转写与评价 |
| GET | `/exercises/{id}/attempts` | 历史作答（只可访问个人记录） |

生成请求体：`{"planId":1,"date":"2026-11-26"}`。正常情况下，首次生成当天唯一课程；已有课程返回 HTTP 409。

已有接口：登录、CSRF、课程列表及详情、步骤进度、单词收藏、复习评价、文字口语作答、统计。

### 项目结构

```text
speakflow-release/
├── compose.yaml                  # 本地 MySQL + Spring Boot + Nginx
├── compose.remote.yaml           # 外部 MySQL
├── .env.example / .env.remote.example
├── server/
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/speakflow/
│       │   ├── ai/               # 模型接入、ASR、TTS
│       │   ├── api/              # REST/登录/生成/音频
│       │   ├── config/           # Spring Security
│       │   └── service/          # 学习服务、生成与发布、音频缓存
│       └── resources/
│           ├── db/migration/     # Flyway V1、V2
│           └── seed/lessons.json # 8篇文章
├── web/src/                     # React、TypeScript、录音 Hook
└── offline-preview/             # 原有独立离线体验版
```

## 六、验证状态与未完事项

2026-11-25 本地验证：Java 21/Maven 已配置，前端生产构建和后端打包通过；10 个单元测试及 1 个显式启用的远程数据库集成测试通过。集成测试数据使用事务回滚；实际数据库已迁移至 V3。课程预览、历史8篇重复导入跳过、无效文本拒绝均通过 HTTP 联调。浏览器完整点击流程和真实 AI/麦克风服务未在本次验证。

还未实现：实时双向 AI 语音会话、专业音素级发音评分、词典音标的权威验证、多用户公开注册、权限管理后台。页面中应把语音转写和文字质量反馈与发音评分区分开。

### 隐私与安全

- 数据库账号和 API Key 都从环境变量读取；`.env` 被 `.gitignore` 排除。
- 远程 MySQL 使用专用账号及网络白名单；上线时开启 TLS。
- 容器默认仅监听本地回环地址；如需公网访问应使用 HTTPS 反向代理与额外的安全加固。
- 口语录音上传仅为转写，不持久保存原始录音；浏览器内建语音识别可能由浏览器厂商处理，请阅读相关隐私提示。
- **如果数据库管理员密码曾在聊天中分享，建议更换该密码**，并避免直接用于业务程序。

## ChatGPT 每日课程导入

登录后点击左侧「导入今日课程」，选择计划，粘贴文本或选择 `.json`/`.md`/`.txt` 文件，点击「解析并预览」，检查完整双语句段和其他字段，再点击确认。解析不写入数据库，编辑输入会清除旧预览。支持单个 JSON、JSON 数组、`{"lessons":[...]}` 和包裹整个 JSON 的 Markdown 代码块；每批最多100篇、输入最多200万字符。任意自由格式文本不能可靠解析时会拒绝，不会改写、翻译或补写原文。没有新增 AI 调用。

可以直接选择 `server/src/main/resources/seed/lessons.json` 导入历史8篇课程；已存在的日期跳过。当前目录没有 DOCX 文件，未实现直接 DOCX 导入。未来有 DOCX 时应先提取并核对原文，再整理为此 JSON 格式。

每篇字段为 date（YYYY-MM-DD）、topic、level、content。历史平铺格式也支持。content 示例：

```json
{"date":"2026-11-25","topic":"Work","level":"B1","content":{"title":"Original lesson title","segments":[{"en":"Original English paragraph.","zh":"对应的原中文段落。"}],"words":[{"word":"original","ipa":"/əˈrɪdʒənəl/","meaning":"原始的"}],"expressions":[{"text":"work on","meaning":"从事","example":"I work on a project."}],"pronunciationTips":[{"target":"work on","ipa":"","explanation":"保留原连读提示。","example":"work on"}],"exercises":[{"type":"OPEN_SPEAKING","question":"What are you working on?","questionZh":"你正在做什么？","reference":"","hint":""}],"studySteps":["阅读原文","按照原任务要求复述"]}}
```

原文字符串不裁剪或改写；原始课程对象保存至 `sf_lesson.import_source_json`，原步骤保存至 `study_steps_json` 并在阅读页显示。segments 至少一组完整双语；其他数组可省略或为空，不强制文章长度或词汇/练习数量。练习类型支持 COMPREHENSION、SUBSTITUTION、OPEN_SPEAKING。发音提示可用 pronunciationTips 或历史 tips 字段。界面提供下载模板。

唯一键继续使用 plan_id + lesson_date，导入按计划加行锁、整批事务写入；已存在日期一律 SKIPPED_EXISTING，包括草稿，本版不提供草稿替换。禁止默默覆盖已发布课程。实际导入成功返回 IMPORTED，立即发布；同批重复日期在预览时拒绝。保留现有五阶段进度，原始学习步骤另行显示。

受认证接口（使用现有登录 Session 和 CSRF）：
- POST /api/v1/lessons/import/preview：`{"source":"原始JSON或JSON代码块"}`，返回标准化预览及提示，不写库。
- POST /api/v1/lessons/import：`{"planId":1,"source":"与预览相同的原始内容"}`，仅计划所有者可写入，返回逐篇结果。
- 旧 POST /api/v1/admin/import-lesson 及 import-chatgpt-lesson.ps1 保留，接收完整标准化单篇结构。

V3 仅新增两个可空 JSON 字段；MySQL 和 MariaDB 各自迁移目录均已补齐。运行 start-server.ps1 会按私有配置连接数据库并执行迁移。凭据仍在 `%LOCALAPPDATA%\SpeakFlow\config\database.env`，不写入源码。

自动化边界：本功能提供接收接口，不代表 ChatGPT 任务可直接推送 localhost。未来桥接需要可获取原任务输出的执行环境，先存原文、预览校验、调用导入、核验结果；失败保留源文件重试。应用内每日自行生成已保留（配置 AI_PROVIDER/openai 与 API Key 后运行），它会生成新文章，不等同于同步 ChatGPT 原文。本次未启用该生成方案或新增周期任务。

本地验证：npm run build；mvn test/package。运行中的 Windows JAR 可能被占用，打包前停止该项目的 Java 进程，再使用 start-server.ps1 重启。请勿结束其他 Java 程序。
数据库集成测试默认跳过。只有获得目标数据库授权并在进程中配置数据库环境变量后，设置 SPEAKFLOW_DB_TEST=1，再执行 mvn -Dtest=LessonImportDatabaseTest test；测试专用用户、计划和课程在测试事务中回滚。不要为未授权数据库启用此开关。
#   d a i l y - E G  
 