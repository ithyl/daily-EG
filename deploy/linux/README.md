# Linux 普通部署

此包包含已构建 JAR 和 web 静态文件，不含数据库密码、API Key、node_modules 或数据库数据。以下首次安装命令以 Ubuntu 24.04 + systemd 为例。其他发行版需调整安装 Java 21/Nginx 的命令。

## 1. 上传和解压

Windows PowerShell（替换用户名和地址）：

```powershell
scp .\speakflow-linux-*.tar.gz deploy@SERVER_IP:/tmp/
```

Linux（将 FILE 替换为上传的包名，解压到新的空目录）：

```bash
mkdir -p ~/speakflow-release
tar -xzf /tmp/FILE.tar.gz -C ~/speakflow-release
cd ~/speakflow-release
sha256sum -c SHA256SUMS
sudo apt update
sudo apt install -y openjdk-21-jre-headless nginx
java -version
```

## 2. 安装文件

以下路径用于首次部署；已有部署应先备份文件和数据库，避免覆盖已有配置。服务器无需 Maven 和 Node。

```bash
id speakflow >/dev/null 2>&1 || sudo useradd --system --home-dir /var/lib/speakflow --shell /usr/sbin/nologin speakflow
sudo install -d -m 755 /opt/speakflow /opt/speakflow/web
sudo install -m 644 server.jar /opt/speakflow/server.jar
sudo cp -R web/. /opt/speakflow/web/
sudo chmod -R a+rX /opt/speakflow/web
sudo install -d -m 700 /etc/speakflow
sudo install -m 600 deploy/speakflow.env.example /etc/speakflow/speakflow.env
sudo nano /etc/speakflow/speakflow.env
```

填入真实 DB_HOST、DB_PORT、DB_USERNAME、DB_PASSWORD。密码留在双引号内，特殊的反斜杠和双引号需转义；这是 systemd 环境文件，不是 shell 脚本。不要上传本机含密码的配置到代码仓库。

连接现有 MariaDB speakflow 库：必须保留 `SPRING_FLYWAY_LOCATIONS=classpath:db/migration-mariadb`。之前服务器不支持 TLS，仅在继续使用已授权的该服务器时设置 `DB_SSL_MODE=DISABLED`。若数据库与应用同机，请使用 localhost 和实际本地端口，不必绕行公网映射端口。

新 MySQL 8 库：预先建空库并授权，使用 `classpath:db/migration`。不要对已经迁移的库切换迁移目录，否则校验可能失败。已有 speakflow 库无需重新导入，课程和学习进度仍从该库读取。新库初始账号密码至少12字符；旧库设置 APP_ADMIN_PASSWORD 不会重置已有账号密码。

## 3. 启动后端

启动会自动执行 Flyway 未执行的迁移和初始数据检查；生产数据库先备份。若 Windows 上也连接同一库，部署期间停止其后端，避免未来同时开启两份课程生成定时器。

```bash
sudo install -m 644 deploy/speakflow.service /etc/systemd/system/speakflow.service
sudo systemctl daemon-reload
sudo systemctl enable --now speakflow
sudo systemctl status speakflow --no-pager
sudo journalctl -u speakflow -n 100 --no-pager
curl -f http://127.0.0.1:8080/api/v1/health
```

## 4. 配置 Nginx

```bash
sudo install -m 644 deploy/nginx.conf /etc/nginx/conf.d/speakflow.conf
sudo nano /etc/nginx/conf.d/speakflow.conf
sudo nginx -t
sudo systemctl enable --now nginx
sudo systemctl reload nginx
```

将 server_name 改为实际域名或服务器 IP；不要覆盖已有站点配置。使用 IP 时如仍显示默认页，检查请求 Host 是否匹配及默认站点的冲突。DNS 指向服务器后，可用以下命令验证反向代理（替换域名）：

```bash
curl -f -H 'Host: YOUR_DOMAIN' http://127.0.0.1/api/v1/health
```

安全组/防火墙对浏览器开放80和443；后端只绑定127.0.0.1，不需要开放8080或5173。配置域名 HTTPS 证书后开启环境文件中的 secure cookie 并重启后端。浏览器录音需要 HTTPS（远程 IP 的普通 HTTP 不满足要求）。证书配置取决于域名和已有反向代理，因此本包不自动修改证书。

## 5. 验收与更新

浏览器访问域名，验证登录、课程列表、学习进度、导入预览及重复跳过。AI_PROVIDER=local 不调用 AI；部署本身不会自动接通 ChatGPT 每日任务。

更新前备份旧 JAR、web、配置和数据库。停止 speakflow，替换 JAR 和前端文件后启动；不要重写环境配置。若仅代码故障可还原旧程序，但数据库已执行的迁移不能仅靠还原 JAR 回退，应使用备份与经过验证的恢复方案。

```bash
sudo systemctl stop speakflow
# 备份并替换 /opt/speakflow/server.jar 和 /opt/speakflow/web
sudo systemctl start speakflow
sudo journalctl -u speakflow -f
```

本部署包仅在 Windows 上验证过应用构建、包内容和校验和，尚未在你的 Linux 服务器执行安装、nginx -t 或 systemd 验收。
