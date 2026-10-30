# App 内问题反馈：服务器中转

用户在首页「关于 → 问题反馈」里写下问题，点「发送」即可。App 把内容发到北京更新服务器上的一个小中转服务，由它在 GitHub 上建一个 Issue。GitHub 会照常给仓库主人发邮件通知，所以邮箱里也能收到。

```
App ──HTTPS POST──▶ Nginx（81.70.79.133:443 /dafeiyu-feedback/submit）──▶ 127.0.0.1:8790 中转 ──▶ GitHub Issue（私有仓库）──▶ 邮件通知
```

## 为什么要中转

直接从 App 发 Issue 或发邮件，需要把 GitHub Token 或邮箱密码打进安装包，任何人都能从 APK 里取出来滥用。中转服务把 Token 只放在服务器上，App 里只有一个公开地址。

## 会发送什么

只发送用户写的内容、选填的联系方式，以及 App 版本、Android 版本、手机型号（反馈页面上会写明）。不附带聊天记录、记忆、Key 或模型地址。

建议把反馈收进一个**私有**仓库（例如 `dafeiyu-feedback`）。用户可能写到个人情况，公开仓库的 Issue 所有人都能看到。

## 限制

- **大小**：正文不超过 2000 字，联系方式不超过 100 字，请求体不超过 16 KB。
- **频率**：手机端每小时最多 5 条。服务器端每个 IP 每小时 5 条、每天 20 条，全站每天 300 条。超出返回 429，App 会提示稍后再试并保留草稿。
- **重试去重**：每条反馈带随机 id，重试不会重复建 Issue。
- **防注入**：内容放在代码块里，不会渲染链接、图片或 @ 提醒。
- **日志**：服务只记录条数和结果，不记录反馈正文。

## 部署（一次性）

### 1. 建仓库和 Token

1. 在 GitHub 新建私有仓库，例如 `ktao732084-arch/dafeiyu-feedback`。
2. 打开 Settings → Developer settings → Fine-grained tokens，新建 Token：
   - Repository access 只选这个反馈仓库。
   - Permissions 只给 **Issues: Read and write**。
   - 有效期按自己习惯，到期前换新。
3. 确认自己在这个仓库上的通知是 Watching（新建仓库默认就是）。这样新 Issue 会发邮件给你。

### 2. 上传文件

在本地仓库根目录执行（`release.local.json` 里的 `ssh_identity` 就是这把 Key）：

```powershell
scp -i <ssh_identity> deploy/feedback/feedback_relay.py deploy/feedback/dafeiyu-feedback.service deploy/feedback/feedback-location.conf root@81.70.79.133:/tmp/
```

### 3. 在服务器上安装

```bash
install -d -m 755 /opt/dafeiyu-feedback
install -m 644 /tmp/feedback_relay.py /opt/dafeiyu-feedback/feedback_relay.py
install -m 644 /tmp/dafeiyu-feedback.service /etc/systemd/system/dafeiyu-feedback.service
install -m 644 /tmp/feedback-location.conf /etc/nginx/snippets/dafeiyu-feedback.conf
```

写入 Token。这一步只在服务器上做，不要提交到仓库：

```bash
umask 077
cat > /etc/dafeiyu-feedback.env <<'EOF'
FEEDBACK_GITHUB_TOKEN=github_pat_在这里粘贴
FEEDBACK_REPO=ktao732084-arch/dafeiyu-feedback
FEEDBACK_PORT=8790
EOF
```

启动服务：

```bash
python3 --version
systemctl daemon-reload
systemctl enable --now dafeiyu-feedback
systemctl status dafeiyu-feedback --no-pager
```

`python3 --version` 需要 3.8 或更高。

### 4. 接入 Nginx

在 `/etc/nginx/conf.d/dash-bj-443.conf` 的 443 server 块里，紧挨着已有的 `include /etc/nginx/snippets/dafeiyu-updates.conf;`，加一行：

```nginx
include /etc/nginx/snippets/dafeiyu-feedback.conf;
```

改之前先备份这个文件。然后检查配置并平滑加载：

```bash
nginx -t && systemctl reload nginx
```

### 5. 验证

1. 发一条测试反馈：

   ```bash
   curl -sS https://81.70.79.133/dafeiyu-feedback/submit -H 'Content-Type: application/json' \
     -d '{"id":"00000000-0000-4000-8000-000000000001","text":"部署测试，请关闭","app_version":"test","android":"","device":"curl"}'
   ```

   应返回 `{"ok": true, "receipt": "#1"}`，反馈仓库里出现对应 Issue，邮箱收到通知。
2. 用 GET 访问同一地址应返回 403 或 405。
3. 查看服务日志：`journalctl -u dafeiyu-feedback -n 20`。日志里只有 `filed #1` 这类记录，没有正文。

## 维护

- **换 Token**：修改 `/etc/dafeiyu-feedback.env`，然后执行 `systemctl restart dafeiyu-feedback`。
- **暂停接收**：执行 `systemctl stop dafeiyu-feedback`。之后 App 发送会收到 502，提示稍后再试，并保留草稿。
- **地址**：App 读取 `assets/update-channel.json` 的 `feedback_url`。`build.ps1 -UpdateUrl` 只改更新地址，保留反馈地址。换域名时，两个地址一起改。
- **发布顺序**：先部署并验证中转服务，再发布带反馈入口的 App 版本。否则用户点发送只会看到“暂时不可用”。
