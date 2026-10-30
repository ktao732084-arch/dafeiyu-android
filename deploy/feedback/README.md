# 自建反馈服务运维

服务地址：`https://81.70.79.133/dafeiyu-feedback/`。普通浏览器直接打开会要求从 App 进入，因为身份由 App 初始请求头建立；用户不需要账号或手动配置。产品行为见 [问题反馈](../../docs/FEEDBACK.md)。

## 部署布局

| 项目 | 位置 |
| --- | --- |
| 服务程序和页面 | `/opt/dafeiyu-feedback/` |
| Python 虚拟环境 | `/opt/dafeiyu-feedback/venv`，Python 3.11 + `requirements.txt` |
| 私有数据库（包括截图） | `/var/lib/dafeiyu-feedback/feedback.sqlite3` |
| 每日快照 | `/var/lib/dafeiyu-feedback/backups/`，保留最近 7 份 |
| 会话签名密钥、数据库路径等 | `/etc/dafeiyu-feedback.env`，仅 root 可读；systemd 加载后传给专用用户 |
| 服务用户/服务名 | `dafeiyu-feedback` / `dafeiyu-feedback.service` |
| 反向代理 | `/etc/nginx/snippets/dafeiyu-feedback.conf` |
| Nginx 请求限制 | `/etc/nginx/conf.d/dafeiyu-feedback-rate.conf` |

只监听 `127.0.0.1:8790`。公网使用现有 443 和已有证书，`dash-bj-443.conf` 仅新增 feedback include，不改变根路径、更新文件或证书续期。服务采用独立用户、私有状态目录、只读系统目录和资源上限。

## 安装或更新

先运行测试，再把本目录的 `.py`、requirements、systemd 文件、Nginx 配置、`install.sh` 和 `web/` 上传到服务器一个新的临时目录，在该目录执行 `bash install.sh`。可复用被忽略的 `deploy/updates/release.local.json` 中 SSH 配置；不要把真实签名密钥或会话密钥提交到 Git。

脚本保存旧 Nginx 配置、服务单元与程序/页面到 `/root/dafeiyu-feedback-backup-<UTC时间>/`；首次生成会话密钥，重复部署保留密钥和数据库；先检查 Nginx，再启动本地服务并验证健康，最后平滑 reload。`feedback.env.example` 只作字段说明，不可原样部署占位密钥。

首次已于 2026-10-30 部署；验证见 [验收记录](../../docs/FEEDBACK_ACCEPTANCE.md)。App 的 `feedback_url` 已配置，`build.ps1 -UpdateUrl` 只修改更新地址、保留反馈地址。发布客户端前仍须走项目正常合并/发布流程。

## 查看和回复

使用开发机现有 SSH 配置，在仓库根执行：

```powershell
# 查看最近 100 条；指定 Receipt 则筛选其中一条
./deploy/feedback/manage.ps1 -Action list
./deploy/feedback/manage.ps1 -Action list -Receipt '<反馈编号>'

# 回复正文保存成 UTF-8 文件；脚本通过标准输入传入，不拼接成 shell 命令
./deploy/feedback/manage.ps1 -Action reply -Receipt '<反馈编号>' -ReplyFile './reply.txt'

# 标记已处理；用户仍可以看回复或补充说明
./deploy/feedback/manage.ps1 -Action close -Receipt '<反馈编号>'
./deploy/feedback/manage.ps1 -Action backup
```

回复会输出请求 UUID。网络中断后重试用同一个 `-RequestId <UUID>`，避免重复回复。工具通过 `runuser` 以服务用户读写数据库；不要直接以 root 写数据库而留下 root 所有的 WAL 文件。列表含用户反馈内容和联系方式，不要将输出提交到公开仓库。

目前没有网页管理后台、邮件/微信通知或移动端推送。用户在 App 内刷新/重新进入后看到回复；管理员可通过上述命令或让开发助手检查服务器处理反馈。没有向 GitHub 或第三方转发的流程。

## 限额和保存

- 单条正文/回复 2000 字，联系方式 100 字；截图 3 MB、1200 万像素以内，重新编码清除元数据。
- 同一安装身份或 IP：每小时最多 5 条、每天 20 条；全服务每天 300 条。重复请求编号和相同内容返回原回执，重启后仍生效；同编号不同内容拒绝。
- 用户补充每小时 20 条，每个反馈最多 60 条回复；页面显示当前身份最近 100 条反馈。
- 活跃库最多 10000 条反馈、截图合计 128 MiB；满额返回可重试错误，不静默删除旧反馈。当前需管理员维护，无自动业务数据清理。
- 日志不记录正文、Cookie、联系方式或 IP；数据库保存用于限流的 IP HMAC。用户输入作为文字展示，不作为 HTML 执行。

## 备份与恢复

定时器每天北京时间约 04:15（加最多 10 分钟随机延迟）使用 SQLite backup API 生成一致快照，原子落盘、权限 0600，保留 7 份。备份在同一台服务器，尚未配置异地备份。

```bash
systemctl list-timers dafeiyu-feedback-backup.timer
systemctl start dafeiyu-feedback-backup.service
```

恢复需先停止 `dafeiyu-feedback.service`，保留当前库及其 WAL/SHM 为故障现场，使用确认过的快照替换数据库并设置 `dafeiyu-feedback:dafeiyu-feedback` 所有权和 0600 权限，再启动并验证。不要只复制正在写入的 SQLite 主文件作为备份，也不要带着旧 WAL 启动恢复库。

## 健康、回滚与测试

```bash
systemctl status dafeiyu-feedback --no-pager
journalctl -u dafeiyu-feedback -n 30 --no-pager
curl --fail https://81.70.79.133/dafeiyu-feedback/health
curl --fail https://81.70.79.133/dafeiyu-updates/health.txt
```

回滚时先核对对应备份目录和部署后的其他改动，恢复旧服务程序/页面及该功能自己的配置，执行 `nginx -t` 后 reload，重启反馈服务。不要整体覆盖已有 HTTPS 配置而丢掉后续服务，也不要回滚或删除用户数据库。初次安装需要下线时，停止反馈服务与备份 timer，移除该功能的 include 后检查并 reload Nginx；保留数据库。

```bash
pip install -r deploy/feedback/requirements.txt
python -m unittest discover -s deploy/feedback -p 'test_*.py'
```

合成验收反馈明确标记为测试，不能当作真实用户问题或真实运营指标。`docs/FEEDBACK_ACCEPTANCE.md` 区分浏览器上传截图与 Android 系统图片选择器的测试边界。
