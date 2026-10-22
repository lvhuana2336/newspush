# NewsPush 社交源后端（B 站版）

一个轻量的 B 站采集与分发服务：定时抓取指定 UP 主的最新视频（标题 + 简介），统一暴露给 Android App；内置设备自动领取 API Key、邀请码开关、抓取日志与限流。

单进程（FastAPI + SQLite），不依赖 Redis/Postgres，2 核 2G 的服务器可以支撑数百个账号。

## 目录

```
server/
├── app/
│   ├── main.py             API、限流、调度循环
│   ├── bili.py             B 站采集（WBI 签名 + 搜索兜底）
│   ├── db.py               SQLite 表结构与读写
│   ├── config.py           环境变量
│   ├── admin.py            命令行管理工具
│   └── seed_accounts.json  首批 50 个账号
├── deploy/
│   ├── newspush-api.service
│   ├── Caddyfile.example
│   ├── backup.sh
│   └── newspush-backup.{service,timer}
├── requirements.txt
└── .env.example
```

## 本地快速验证（Windows / macOS）

```bash
cd server
python -m venv .venv
# Windows: .venv\Scripts\activate
source .venv/bin/activate
pip install -r requirements.txt

# 可选：复制 .env.example 里的变量到当前 shell，或直接设置
set POLL_ENABLED=false
uvicorn app.main:app --host 127.0.0.1 --port 8000
```

打开 http://127.0.0.1:8000/docs 可以直接调接口。

## 配置项

所有配置通过环境变量传入，详见 `.env.example`。

| 变量 | 默认 | 说明 |
| --- | --- | --- |
| `DB_PATH` | `server/data/newspush.db` | SQLite 文件位置 |
| `POLL_ENABLED` | `true` | 是否启动后台抓取循环 |
| `POLL_TICK_SECONDS` | `30` | 调度检查间隔 |
| `BILI_MIN_INTERVAL_SECONDS` | `2.0` | B 站请求最小间隔 |
| `REGISTRATION_MODE` | `open` | `open` 或 `invite` |
| `INVITE_CODE` | 空 | 邀请码模式下的注册码 |
| `DEVICE_HOUR_LIMIT` / `DEVICE_DAY_LIMIT` | 60 / 500 | 单设备请求配额 |
| `REGISTER_IP_HOUR_LIMIT` / `REGISTER_IP_DAY_LIMIT` | 5 / 20 | 单 IP 注册配额 |
| `ADMIN_TOKEN` | 空 | 管理接口令牌；留空则禁用 HTTP 管理接口 |
| `BILI_SESSDATA` | 空 | 可选，专用小号的 SESSDATA，提高空间接口成功率 |
| `POST_RETENTION_DAYS` | 90 | 帖子保留天数 |

## 接口

```text
POST /v1/devices/register   设备首次启动领取 Key（可选邀请码）
GET  /v1/feed?cursor=&limit= 增量拉取
GET  /v1/accounts            已启用的账号列表
GET  /v1/health              健康检查

GET    /admin/accounts       账号与抓取状态（需 X-Admin-Token）
POST   /admin/accounts       添加/更新账号
PATCH  /admin/accounts/{uid} 启用/停用、调整间隔
POST   /admin/fetch-now      立即抓取（可选 uid）
GET    /admin/runs           最近抓取记录
GET    /admin/devices        设备列表
POST   /admin/devices/{id}/revoke  吊销设备 Key
```

注册请求：

```json
{"device_id": "本机随机UUID", "app_version": "1.1", "invite_code": ""}
```

注册响应（Key 只返回这一次）：

```json
{"api_key": "np_xxx", "schema_version": 1, "registration_mode": "open", "server_time": "..."}
```

`/v1/feed` 返回：

```json
{
  "items": [{
    "id": 1,
    "platform": "bilibili",
    "post_id": "BV1xx411c7mD",
    "account_uid": "456664753",
    "account_name": "央视新闻",
    "category": "国内",
    "title": "标题",
    "description": "简介",
    "url": "https://www.bilibili.com/video/BV1xx411c7mD",
    "cover_url": "https://i0.hdslb.com/...",
    "published_at": "2026-10-22T20:00:00+08:00",
    "published_at_ts": 1761134400,
    "stats": {"play": 10000, "like": 800}
  }],
  "next_cursor": 1
}
```

## 阿里云部署（Ubuntu 22.04，2C2G）

下面的命令假设：

- 服务器公网 IP 为 `<EIP>`，SSH 用户为 `root`（阿里云 Ubuntu 镜像也可能是 `ecs-user`，按控制台提示替换）
- 域名 `<api.example.com>` 已解析到 `<EIP>`（如果走 Cloudflare Tunnel，可以没有公网 80/443）

### 0. 先选访问方式

| 方式 | 适用 | 需要 |
| --- | --- | --- |
| 域名 + Caddy | 香港/海外 ECS，或已完成 ICP 备案的大陆 ECS | 域名、开放 80/443 |
| Cloudflare Tunnel | 大陆 ECS 暂无备案，或个人自用 | Cloudflare 账号、域名（可托管在 Cloudflare） |
| 公网 IP + 8000 端口 | 仅临时测试 | 开放 8000，HTTP 明文传输，不建议长期 |

大陆 ECS 用域名对外提供 80/443 服务需要 ICP 备案；备案没下来时优先用 Cloudflare Tunnel，不要硬开 80/443。

### 1. 安全组

阿里云控制台 → 实例 → 安全组 → 配置规则 → 入方向：

- 22/tcp：来源填**你自己的公网 IP**，不要 `0.0.0.0/0`
- 80/tcp、443/tcp：只用 Caddy + 域名时开放，来源 `0.0.0.0/0`
- 8000/tcp：仅临时测试时开放，测完关闭

### 2. SSH 登录与系统初始化

```bash
ssh root@<EIP>

timedatectl set-timezone Asia/Shanghai
apt update && apt upgrade -y
apt install -y python3 python3-venv python3-pip sqlite3 curl ufw

# 2G swap（2G 内存的机器建议加）
fallocate -l 2G /swapfile
chmod 600 /swapfile
mkswap /swapfile
swapon /swapfile
grep -q '^/swapfile' /etc/fstab || echo '/swapfile none swap sw 0 0' >> /etc/fstab

# 防火墙：先放行 SSH，再启用，避免把自己关在门外
ufw allow from <your-ip> to any port 22 proto tcp
ufw allow 80/tcp
ufw allow 443/tcp
ufw --force enable
ufw status
```

### 3. 创建用户与目录

```bash
useradd --system --create-home --home-dir /opt/newspush --shell /usr/sbin/nologin newspush
mkdir -p /opt/newspush/server /etc/newspush /var/backups/newspush
chown -R newspush:newspush /opt/newspush /var/backups/newspush
```

### 4. 上传代码

在**你自己的电脑**上执行（PowerShell）：

```powershell
scp -r D:\code\news\server\app D:\code\news\server\deploy `
  D:\code\news\server\requirements.txt D:\code\news\server\.env.example `
  root@<EIP>:/opt/newspush/server/
```

服务器上修正权限：

```bash
chown -R newspush:newspush /opt/newspush/server
ls /opt/newspush/server
```

如果服务器能访问 GitHub，也可以：

```bash
cd /tmp && git clone --depth 1 https://github.com/lvhuana2336/newspush.git
cp -r /tmp/newspush/server/* /opt/newspush/server/
chown -R newspush:newspush /opt/newspush/server
```

### 5. Python 环境

```bash
python3 -m venv /opt/newspush/venv
chown -R newspush:newspush /opt/newspush/venv
/opt/newspush/venv/bin/pip install -U pip -i https://mirrors.aliyun.com/pypi/simple/
/opt/newspush/venv/bin/pip install -r /opt/newspush/server/requirements.txt -i https://mirrors.aliyun.com/pypi/simple/
/opt/newspush/venv/bin/python -c "import fastapi, uvicorn, httpx; print('deps ok')"
```

### 6. 环境变量

```bash
cp /opt/newspush/server/.env.example /etc/newspush/newspush.env
nano /etc/newspush/newspush.env
```

至少修改：

```ini
DB_PATH=/opt/newspush/server/data/newspush.db
POLL_ENABLED=true
REGISTRATION_MODE=open
ADMIN_TOKEN=<用 openssl rand -hex 32 生成>
```

生成随机值的命令：

```bash
openssl rand -hex 32   # ADMIN_TOKEN
openssl rand -hex 4    # INVITE_CODE（开启邀请码时用）
```

权限：

```bash
chmod 600 /etc/newspush/newspush.env
chown root:root /etc/newspush/newspush.env
```

### 7. 注册为系统服务

```bash
cp /opt/newspush/server/deploy/newspush-api.service /etc/systemd/system/
systemctl daemon-reload
systemctl enable --now newspush-api
systemctl status newspush-api --no-pager
journalctl -u newspush-api -n 50 --no-pager
```

本机自检：

```bash
curl -s http://127.0.0.1:8000/v1/health | python3 -m json.tool
```

应看到 `"status": "ok"`、`"accounts": 50`。首次启动会自动导入 `app/seed_accounts.json`。

### 8. HTTPS（域名 + Caddy）

先确保 `<api.example.com>` 的 A 记录指向 `<EIP>`，然后：

```bash
apt install -y caddy
cp /opt/newspush/server/deploy/Caddyfile.example /etc/caddy/Caddyfile
sed -i 's/api\.example\.com/<api.example.com>/' /etc/caddy/Caddyfile
systemctl reload caddy
journalctl -u caddy -n 30 --no-pager
```

Caddy 会自动申请 Let's Encrypt 证书。验证：

```bash
curl -s https://<api.example.com>/v1/health | python3 -m json.tool
```

#### 备案未完成时的 Cloudflare Tunnel（可选）

```bash
# 安装 cloudflared（若 GitHub 下载失败，用 Cloudflare 官方 apt 源）
curl -L --output /usr/local/bin/cloudflared \
  https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-linux-amd64
chmod +x /usr/local/bin/cloudflared

cloudflared tunnel login
cloudflared tunnel create newspush
cloudflared tunnel route dns newspush <api.example.com>
```

创建 `/etc/cloudflared/config.yml`：

```yaml
tunnel: newspush
credentials-file: /root/.cloudflared/<tunnel-id>.json
ingress:
  - hostname: <api.example.com>
    service: http://127.0.0.1:8000
  - service: http_status:404
```

```bash
cloudflared service install
systemctl enable --now cloudflared
```

这种方式不需要开放 80/443，也不需要 ICP 备案。

### 9. 端到端验证

```bash
# 1) 注册一台测试设备
curl -s -X POST https://<api.example.com>/v1/devices/register \
  -H "Content-Type: application/json" \
  -d '{"device_id":"dev-test-000001","app_version":"1.1"}' | python3 -m json.tool

# 2) 保存返回的 api_key，然后拉取 feed（首次可能为空）
curl -s "https://<api.example.com>/v1/feed?limit=5" \
  -H "Authorization: Bearer <api_key>" | python3 -m json.tool

# 3) 立即触发抓取（管理令牌）
curl -s -X POST https://<api.example.com>/admin/fetch-now \
  -H "X-Admin-Token: <ADMIN_TOKEN>" | python3 -m json.tool

# 4) 等 1~2 分钟后再拉 feed
curl -s "https://<api.example.com>/v1/feed?limit=5" \
  -H "Authorization: Bearer <api_key>" | python3 -m json.tool
```

管理端可以看每个账号的抓取状态与错误：

```bash
curl -s https://<api.example.com>/admin/accounts \
  -H "X-Admin-Token: <ADMIN_TOKEN>" | python3 -m json.tool
curl -s "https://<api.example.com>/admin/runs?limit=20" \
  -H "X-Admin-Token: <ADMIN_TOKEN>" | python3 -m json.tool
```

### 10. 开启邀请码

编辑 `/etc/newspush/newspush.env`：

```ini
REGISTRATION_MODE=invite
INVITE_CODE=<8位随机码>
```

```bash
systemctl restart newspush-api
```

此时注册必须带 `invite_code`，否则返回 `403`（`code=INVITE_REQUIRED`）。已在用的设备 Key 不受影响。

### 11. 备份

```bash
cp /opt/newspush/server/deploy/backup.sh /usr/local/bin/newspush-backup.sh
chmod +x /usr/local/bin/newspush-backup.sh
cp /opt/newspush/server/deploy/newspush-backup.service /etc/systemd/system/
cp /opt/newspush/server/deploy/newspush-backup.timer /etc/systemd/system/
systemctl daemon-reload
systemctl enable --now newspush-backup.timer
systemctl list-timers newspush-backup.timer
```

备份存放在 `/var/backups/newspush/`，保留 14 天。

### 12. 日志与监控

```bash
journalctl -u newspush-api -f          # 实时日志
journalctl -u newspush-api --since "1 hour ago"
df -h                                   # 磁盘
free -m                                 # 内存
```

建议每天检查一次 `/v1/health`；如果 `last_fetch_at` 长时间不更新，看 `/admin/runs` 里的错误。

### 13. 更新代码

本地修改并推送后，在服务器上：

```bash
cd /tmp && rm -rf newspush-update && git clone --depth 1 https://github.com/lvhuana2336/newspush.git newspush-update
rsync -a --exclude data --exclude __pycache__ /tmp/newspush-update/server/ /opt/newspush/server/
chown -R newspush:newspush /opt/newspush/server
systemctl restart newspush-api
```

或者直接用 `scp` 覆盖 `server/app` 与 `server/deploy`，然后重启服务。

## 容量与限流

- 50 个账号、10~20 分钟一轮，约 3~5 次/分钟请求，2C2G 非常宽裕。
- 全局 B 站请求间隔默认 2 秒（`BILI_MIN_INTERVAL_SECONDS`），不要低于 1 秒。
- 单账号失败会指数退避，最多 6 小时；连续失败可在 `/admin/accounts` 看到 `last_error`。
- 建议账号数超过 300 时把个人 UP 主的间隔调到 30 分钟，或增加第二条出口 IP。

## 常见问题

**B 站返回 `-352` / `412`**

空间接口风控较严。服务会自动回退到「按账号名搜索视频并按 mid 过滤」。如果某个账号持续返回 0 条，配置 `BILI_SESSDATA`（专用小号）后重启；仍不行就在 `/admin/accounts` 把它停用。

**注册一直返回 429**

注册限流按 IP 计算，默认 5 次/小时、20 次/天；本地反复测试可以临时把 `REGISTER_IP_HOUR_LIMIT` 调大，或重启服务清空内存计数。

**`ADMIN_TOKEN` 留空后管理接口 503**

这是有意设计：默认关闭 HTTP 管理接口。用 SSH 执行 `python -m app.admin ...` 仍然可以管理。

**SQLite `database is locked`**

本项目单进程运行，正常不会出现。若出现，检查是否有另一个进程占用了同一个 DB（比如同时跑了两个 uvicorn worker）。服务必须使用 `--workers 1`。
