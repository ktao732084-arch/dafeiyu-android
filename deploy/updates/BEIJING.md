# 北京公网 IP 更新入口

**最新发布：0.4.9.2 / code 15。客户端默认内置本入口，用户覆盖安装一次即可使用“关于 → 检查更新”。本次发布与验收见 [VALIDATION.md](../../VALIDATION.md)。下文记录最初以 0.4.7 建站时的检查过程。**

2026-10-29，在独立分支 `codex/lightweight-updates` 完成部署接入；未合并主分支或发布新的 App 版本。

## 服务器与地址

- 现有北京服务器：`81.70.79.133`，OpenCloudOS 9.4，Nginx 1.26.3。检查时磁盘剩余约 13 GB、可用内存约 2.6 GB，足够提供静态更新文件。
- 检查更新：`https://81.70.79.133/dafeiyu-updates/stable.json`。
- 健康检查：`https://81.70.79.133/dafeiyu-updates/health.txt`。
- 文件目录：`/srv/dafeiyu-updates`；只公开清单、健康检查和严格命名的 APK。目录索引、上传中间文件及其他路径不公开；不能通过 HTTP 请求写入文件。
- 公网 9443 实测超时；使用已开放的 443，不新增端口或修改防火墙。

## TLS 与现有服务

复用服务器已有、覆盖该 IP 的 Let's Encrypt 证书：`/etc/letsencrypt-campus/live/campus-ip/fullchain.pem`。没有生成自签名证书，没有关闭客户端 TLS 验证，也没有把服务器私钥下载到本地或放进仓库。

原 `/etc/nginx/conf.d/dash-bj-443.conf` 保留监听地址和仪表盘反代，仅替换两条证书路径并引入 `/etc/nginx/snippets/dafeiyu-updates.conf`。后者对应本目录 `beijing-locations.conf`。变更前后都通过 `nginx -t`，使用 reload 平滑加载，原根路径仍返回 302。

复用已启用的 `campus-cert-renew.timer`，每天 03:00、15:00 加随机延迟执行，已有 deploy hook 在续期后检查并 reload Nginx。部署时最近一次运行成功；不需要另外安装旧系统 Certbot 或创建第二套续期任务。IP 证书有效期短，后续不得删除这套证书、定时器或 reload hook；若拆除原校园服务，应先迁移证书生命周期。

备份：`/root/dafeiyu-updates-backup-20261029T022948Z/dash-bj-443.conf`。如需回滚入口，先核对没有其他后续变更，再恢复该配置，执行 `nginx -t && systemctl reload nginx`；无需改动校园服务配置或续期任务。恢复会同时恢复原自签名证书状态，不宜作为长期配置。

## 当前镜像基线与后续发布

镜像来源为已公开发布的 [v0.4.7 体验版](https://github.com/ktao732084-arch/dafeiyu-android/releases/tag/v0.4.7)，不是此开发分支的测试包。GitHub 将该版本标记为 prerelease；`stable.json` 是本项目原有清单文件名，不意味着将体验版重新宣布为正式稳定版。

- versionCode：11；包名：`com.dafeiyu.companion`。
- 大小：91,472,193 bytes。
- APK SHA-256：`b7c636bb54efc1b5842089c6149779d94990ef2bdf415246ba43bca55aab9f85`，与 GitHub 资产 digest 和本地发布校验表一致。
- 签名证书 SHA-256：`e3074b90e07ff4fced5e0e2e5834c928619721716c507853a7247b455c65d405`（公开指纹）。

使用 `prepare-update.py` 从原 APK 生成清单；APK 先上传为 `.part`，服务器核对完整大小和 SHA-256 后原子改名；从公网核验下载，再原子发布 `stable.json`。后续发布使用相同流程，保持 versionCode 单调递增，APK 先于清单公开。

当前线上 0.4.7 自身不包含此分支的更新功能。下一次客户端发布应在合入最新产品改动后使用 `build-beijing.ps1` 和原发布签名构建，并正常升级版本。用户需先手动覆盖安装一次带更新功能的版本；本次没有把基于 0.4.6 的开发包当作升级包发给用户。

## 验证

- Windows 系统信任库通过公网 IP HTTPS 校验；健康检查 200，原根路径 302，更新目录/锁文件/临时 JSON 404，POST 健康检查 405。
- 北京构建后直接读取 APK 内资产，确认真实地址已写入；再执行普通构建确认地址恢复为空，避免测试意外联网。
- 已从公网完整下载 91,472,193 bytes，耗时约 274 秒（单次观测，约 326 KiB/s），大小和 SHA-256 与原发布资产一致；发布清单后检查其返回 200、JSON 类型及禁长期缓存头。此带宽适合初期分发，不等同大规模 CDN。
- 发布脚本 7 项单元测试通过，包含公网 IP 与路径前缀；Android 14 ART 的 12 套核心检查通过，更新检查由 26 项增加到 30 项，覆盖 IP 地址和端口隔离。独立运行时检查 30 项通过。
- Android 14 / API 34 模拟器的独立网络测试通过：系统默认 TLS 信任与 IP 校验、真实清单、完整 91,472,193-byte APK 下载、大小/SHA-256、PackageManager 包名/版本解析及发布签名均匹配。全程未跳过证书校验；下载位于 `com.dafeiyu.updatechecks` 缓存，验证后删除，没有覆盖正式 App。
- 未运行实体手机系统覆盖安装，也未实测 Android 8 等旧系统对当前证书链的兼容性；相关发布验收仍需完成。

独立网络测试入口为 `UpdateNetworkInstrumentation`，不会在普通测试中联网。显式传入 `updateUrl`、`expectedSha` 和 `expectedSigner` 才执行；它使用独立测试 App 的缓存，验证系统 TLS、完整下载与真实 APK 元数据，不启动安装器或改动正式 App。

## 后续换域名

先保留旧 IP 服务，通过旧入口分发内置新域名的后续版本；等待用户迁移后再评估退役旧入口。客户端目前禁止跨源下载跳转，不能只把旧 IP 的 APK 地址 302 到新域名。

IP 证书申请与续期的官方参考：[Let's Encrypt / Certbot IP 证书说明](https://letsencrypt.org/2026/03/11/shorter-certs-certbot/)。本机复用现有自动化配置，没有为此重新申请证书。
