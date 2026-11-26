# SpeakFlow 单容器镜像

镜像 `speakflow:1.0`，目标 `linux/amd64`。前端静态资源由 Spring Boot 提供，容器仅需8080端口；数据库使用外部现有 MySQL/MariaDB，不包含在镜像中。无需 Node、Maven、Nginx（HTTPS 可由宿主机已有反向代理提供）。

把镜像 tar、compose.yaml 和 speakflow.env.example 上传到同一 Linux 目录后：

```bash
docker load -i speakflow-linux-amd64.tar
cp speakflow.env.example speakflow.env
chmod 600 speakflow.env
nano speakflow.env
docker compose up -d
docker compose logs --tail=100
curl -f http://127.0.0.1:8080/api/v1/health
```

填入真实数据库配置，保留现有 MariaDB 库使用的迁移目录。容器的 localhost 是容器自身，不是宿主机；数据库与 Docker 同机时需要使用可达的宿主机地址、Docker 网络别名或显式配置 host-gateway。用户名密码按 Compose env_file 规则填写；含 `$` 的密码用单引号包裹以禁止插值。不要使用 Linux systemd 的环境文件替代而不检查引号规则。

默认绑定宿主机127.0.0.1:8080，供现有反向代理使用。临时用服务器IP直接访问，可执行：

```bash
BIND_ADDRESS=0.0.0.0 HTTP_PORT=8080 docker compose up -d
```

此时访问 `http://服务器IP:8080`，并按需开放该端口。正式使用域名和HTTPS；远程HTTP页面无法使用浏览器麦克风。启用HTTPS后可设置 secure cookie。应用仅包含学习资料和接口，数据库密码不在镜像里。

启动会连接配置的数据库，自动运行 Flyway 增量迁移。连接现有库即可保留课程和进度，不要重新建库或切换迁移目录。先备份数据库；若 Windows 后端还在运行，迁移到服务器后停止旧后端，避免两处同时开启每日生成任务。APP_ADMIN_PASSWORD 只用于创建初始用户，不会重置已有密码。

所有学习数据保存于外部数据库。可选云端朗读缓存位于容器 `/tmp/speakflow-audio`，重建容器后会重新生成。本包不包含或启用 AI Key，也不代表 ChatGPT 每日任务已自动同步。

重新构建镜像（项目根目录，已安装 Node/npm、Java21、Maven）：

```bash
npm --prefix web ci
npm --prefix web run build
mvn -f server/pom.xml -Pdocker-image compile jib:buildTar
```

输出 `server/target/docker-image/jib-image.tar`。服务器ARM架构请将pom镜像profile中的architecture改为arm64再构建，不可直接使用amd64包。
