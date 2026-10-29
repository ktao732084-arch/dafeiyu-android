# 一次命令同步 GitHub 和国内更新站点

发布入口是仓库根目录 `release.ps1`。只使用现有 `gh` 登录、SSH 密钥、curl 和 Android SDK，不增加服务端常驻程序，也不把签名私钥或 GitHub Token 放到服务器。

## 本机配置（只做一次）

把 `release.local.example.json` 复制为同目录 `release.local.json`，填写本机 Build Tools、JDK、SSH 目标及密钥路径。当前开发机已配置好；此文件被 Git 忽略。证书公钥指纹、仓库及国内地址放在可审查的 `release.json`。

前置条件：`gh auth login` 已完成、SSH 主机密钥已核实并存入 known_hosts。脚本不会关闭 SSH 主机校验或 HTTPS 证书校验，不会收集签名密码。

## 每次发布

先按 AGENTS.md 完成构建、签名、Android/框架测试和主分支合并。`docs/test-reports/validation-result.json` 必须记录本次 APK 的版本、versionCode、SHA-256 和通过的测试；脚本会从指定源提交读取并核对它。发布说明写入一个文件，重跑时保留相同文件和字节（包括换行）。

```powershell
./release.ps1 -Apk build/dafeiyu-0.4.9.3.apk -Notes release-notes.txt -Commit <已合入main的提交SHA> -Publish
```

示例版本不代表应直接使用该编号；先核对线上版本再递增。`-Commit` 默认当前 HEAD，建议明确传入合并提交。脚本不会自动改版本、构建、合并分支或绕过测试。

不加 `-Publish` 时只准备本地文件并读取两端状态，输出计划。核验已发布版本用 `-VerifyOnly`：

```powershell
./release.ps1 -Apk build/dafeiyu-0.4.9.2.apk -Notes README-0.4.9.2.md -Commit v0.4.9.2 -VerifyOnly
```

## 命令自动完成的步骤

1. 从真实 APK 读取包名、四段版本、versionCode、最低系统版本；验证发布签名、内置更新地址和源提交的验收记录。
2. 生成 GitHub 下载文件名、校验表和国内 `stable.json`，放到 `build/release-sync/v版本/`。
3. 创建或恢复 GitHub 草稿，上传缺失资产并核对 GitHub 返回的 SHA-256。已完成的同名资产不得改变，已发布版本不会覆盖或删除。
4. 国内 APK 先上传 `.part`，中断后通过 SFTP 续传；服务器核验完整大小、SHA-256 后原子改名。版本清单此时仍是旧版。
5. 从真实 HTTPS 地址完整下载一次并核验；同一哈希的已验证本地缓存可复用。
6. 公开 GitHub Release，然后以服务器文件锁保护、原子替换国内版本清单。保留一份 `stable.previous.json`，它不在公开下载白名单内。
7. 最后核对公开的 GitHub 资产和国内清单，成功才输出 `Verified` 并写入 `receipt.json`。

GitHub 与服务器不支持跨系统的原子事务。若 GitHub 已公开而国内提交失败，命令返回失败；修复网络后用相同输入重跑，会补齐国内清单。不会删除已经公开的 Release 来伪装回滚。若线上已被另一流程推进到更高版本，脚本拒绝回退，先检查并发布更高的新版本。

## 失败处理

- 网络中断：原命令重跑；本机锁由操作系统释放，服务器写锁随 SSH 进程退出释放。
- 同版本不同哈希、不同提交或不同说明：停止，检查传错的文件；不要使用覆盖参数绕过。
- 上传残片损坏：旧清单保持不变。检查脚本指出的、仅属于本次版本的 `.part` 文件，清理残片后再重跑；不要删正式 APK 或 stable.json。
- 只看到 GitHub Release、没有命令最终成功：不能宣布“双端发布完成”。用 `-VerifyOnly` 确认或用 `-Publish` 补齐。

日常发布默认保留仓库现有的体验版（prerelease）标记。更改发布渠道或更新域名属于独立配置变更；APK 内置地址必须与 `release.json` 一致。

## 验证与边界

自动测试覆盖上传截断/损坏、不可变文件、版本回退、同号冲突、原子替换失败、并发时重新检查版本、公开 GitHub 后中断恢复、重复执行无改写、只读核验和测试产物绑定。CI 会持续运行这些测试。真实服务器的临时目录已验证 Linux 文件锁和原子提交，不涉及线上清单。

2026-10-29 验证结果：18 项同步测试、7 项已有发布准备测试通过。真实服务器临时目录中的首次 SFTP 上传、部分上传续传、SHA-256 校验、锁互斥、原子提交及重复执行均通过。现有 `0.4.9.2` 的 `-VerifyOnly` 完整 HTTPS 下载核验通过；随后 `-Publish` 重跑输出 `Both destinations already match; no remote writes` 和最终 `Verified`。

没有为验证脚本创建假 Release 或升级 App 版本。两端发生故障时的恢复步骤采用受控测试，没有主动破坏线上服务。详细机器可读记录见 `docs/test-reports/release-sync-validation.json`。

GitHub CLI 行为参考：[创建草稿](https://cli.github.com/manual/gh_release_create)、[上传资产](https://cli.github.com/manual/gh_release_upload)、[公开草稿](https://cli.github.com/manual/gh_release_edit)。
