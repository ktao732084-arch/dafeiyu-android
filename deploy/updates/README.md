# 轻量国内更新站点

当前域名尚未确定。默认 `assets/update-channel.json` 的地址为空，App不会自动访问占位域名；手动检查会提示更新服务未配置。此目录是部署模板，不是已经上线的服务。

服务器只需通过HTTPS提供两个路径：

```text
/stable.json
/releases/<versionCode>-<sha256>.apk
```

安装包必须实际保存在这个站点，不能302到GitHub等其他域名。客户端仅允许同域、同端口HTTPS跳转。首版不使用CDN跨域地址、动态后端、数据库、强制更新或静默安装。

## 配置客户端

确定真实域名后，在正式签名构建时传入版本文件地址：

```powershell
./build.ps1 -Sdk 'D:/Android/Sdk' -Jdk 'C:/Program Files/Java/jdk-17' -KeyStore 'D:/Keys/release.keystore' -UpdateUrl 'https://你的真实域名/stable.json'
```

地址只写入本次构建的 `fish-assets/update-channel.json`。不传 `-UpdateUrl` 会恢复为空，不沿用上一次构建地址。正式发布仍须遵循AGENTS.md的版本递增和签名连续性要求。

## 准备发布文件

使用实际正式APK的签名证书指纹。这里需要的是公开SHA-256指纹，不是私钥或密码：

```powershell
python tools/prepare-update.py --apk build/dafeiyu-新版本.apk --build-tools 'D:/Android/Sdk/build-tools/36.0.0' --base-url 'https://你的真实域名' --expected-cert-sha256 '正式签名证书的64位SHA256指纹' --notes release-notes.txt --out build/update-site
```

脚本先使用SDK的aapt读取包名、版本和最低系统版本，再使用apksigner验证签名；版本信息中的大小、SHA-256和文件名从实际APK生成。它拒绝错误证书、错误包名、超过512MiB的包，以及覆盖同目录内更高或相同的versionCode。发布操作用本地锁串行化。

生成文件可检查：

```json
{
  "schema": 1,
  "package_name": "com.dafeiyu.companion",
  "version_code": 11,
  "version_name": "0.4.7",
  "min_sdk": 26,
  "size": 123456,
  "sha256": "由脚本从实际APK计算",
  "apk_url": "https://你的真实域名/releases/版本号-校验值.apk",
  "release_notes": "用户可读的更新说明"
}
```

上面只说明字段，不能直接作为线上版本文件。

## 部署顺序

1. 在已有HTTPS站点中配置 `nginx.conf.example` 对应路径。
2. 先上传新APK到 `releases/`。文件名不可变，不覆盖旧APK。
3. 从公网下载一次新APK，核对大小与SHA-256。
4. 上传新的版本文件为 `stable.json.tmp`，最后在服务器上原子重命名为 `stable.json`。
5. 用一台安装旧版本的测试设备手动检查，确认提醒、下载和系统确认安装流程。

不要先公开stable.json后传APK。不要把不同版本的并行发布覆盖到同一个stable.json；同一次发布按顺序操作。Nginx的版本文件禁用长期缓存，APK可使用长期缓存。不需要把签名私钥或模型API Key上传到更新服务器。

## 用户体验与限制

- 首页“关于”分组提供“检查更新”；支持自动检查开关和手动检查。
- 打开App或桌宠亮屏运行时每天最多自动尝试一次；失败也限频。完全退出后不保证后台唤醒，下次启动再检查。
- 同一新版本每天最多自动提醒一次，可稍后或忽略该版本。手动检查不受忽略和每日间隔限制。
- 用户确认后才下载，显示进度，可取消、失败后重试；没有断点续传。App进程被系统结束后，下次重新下载，完整且通过哈希的缓存可复用。
- 校验大小、SHA-256、包名、versionCode、最低SDK和当前签名证书；正式签名不得换成开发测试签名。暂不自动接受签名轮换。
- 没有安装权限时引导至系统设置，返回后点击安装；系统仍要求用户确认。不会卸载旧App或清空记忆。
- 旧版没有检测逻辑，用户必须先手动覆盖安装一次带更新功能且配置了真实地址的正式版本。

尚未配置域名、TLS、线上文件和正式新版本，因此不能宣称国内更新服务已上线。
