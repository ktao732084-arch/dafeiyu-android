# 自建反馈验收（2026-10-30）

对象：PR #27 的 `feature/feedback`，已整合 `main` 的 `14cdba6`；将兔小巢替换为自己的服务器，保留 App 内页面、安装身份、截图入口及返回逻辑。客户端构建沿用 `0.4.9.5 / code19` 作为本地验收包，未发布新 APK、未覆盖更新清单、未安装到用户正式 App。

## 已完成

| 检查 | 结果 |
| --- | --- |
| 产品编译、DEX、APK 签名 | 通过 |
| 服务端 Python/SQLite/HTTP 测试 | 19 项通过；覆盖身份隔离、Cookie 校验、重启后数据与去重、并发去重、限流、图片重新编码、回复、备份恢复与 7 份保留 |
| Android ART 产品 DEX 核心测试 | Android 15 / API 35，25 套通过，含 FeedbackTest 31 项 |
| Robolectric UsabilityUiTest | Android 14 框架，10 项通过，含反馈入口及安装身份复用 |
| 原生 WebView + 实际公网 HTTPS | 独立包 `com.dafeiyu.feedbackchecks`，10 项通过：原生请求头建立会话、提交中文文字、重新打开仍可查看、HttpOnly 凭据不可被网页脚本读取；未读取或清空用户正式包数据 |
| 浏览器实际 HTTPS | 发送合成文字与一张测试截图 → 查看回执和图片 → SSH 开发者回复 → 用户刷新看到回复 → 用户补充说明，全流程通过 |
| 第二个安装身份 | 不能列出第一身份的反馈；读取其截图返回 404 |
| 服务器重启/重复部署 | 同一会话仍能读取先前反馈、回复与截图；配置保留密钥和数据库 |
| 私有管理命令 | SSH 列表、标记已处理通过；测试内容已标记为合成验收并关闭 |
| 备份 | 私有 SQLite 快照已生成，权限 0600；每日 timer 已启用，保留 7 份 |
| 原更新服务 | `/dafeiyu-updates/health.txt` 仍正常；未修改 APK、版本清单或原根路径规则 |

## 服务器状态

- 公开入口：`https://81.70.79.133/dafeiyu-feedback/`；健康接口 `/dafeiyu-feedback/health` 返回 200。
- 后端仅绑定 `127.0.0.1:8790`，由既有 Nginx 443 提供 HTTPS，`nginx -t` 通过。
- systemd 服务已 active，备份 timer 已 enabled。备份按北京时间约 04:15 执行，加最多 10 分钟随机延迟。
- 首次部署备份：`/root/dafeiyu-feedback-backup-20261030T043511Z/`；启用备份任务的部署备份：`/root/dafeiyu-feedback-backup-20261030T045934Z/`。配置回滚要核对后续变更，不覆盖其他服务。
- 会话密钥仅在服务器 `/etc/dafeiyu-feedback.env`；仓库和 APK 不含管理员凭据。

## 产物与边界

本地预览 APK：`../../outputs/dafeiyu-feedback-preview.apk`（相对于仓库根）；SHA-256：`869c1f38869e6c5c608e0937fafddea8a3e7fe7da504295b70b9855bcfced733`。

截图与详细原始运行日志保留在开发工作区 outputs/build，未把真实用户聊天或 Key 上传到仓库。测试反馈为明确标记的合成内容，不代表真实用户报告。

截图上传端到端验证在浏览器完成；Android 原生检查验证了 WebView 会话、文本提交及重开，尚未对各厂商系统图片选择器逐一实测。未测实体手机、弱网长时间运行、厂商 WebView 差异或大量并发。没有配置网页管理后台、邮件/微信通知、推送通知或异地备份。服务端上线不等同于用户已获得新的 App 版本。
