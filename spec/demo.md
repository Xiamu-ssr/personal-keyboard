# 真实 Android 画面

同一仓库内构建 APK、生成 Spec。Node / scrcpy 传输真实 Android 画面和触控，不把 HTML 冒充安卓。当前 APK 没有内置模型账号。

## 在线演示

Spec 的“真实 APK”嵌入 `/keyboard-spec/live/`。先输入私人演示口令，再点“启动 Android”：Mac 按需创建独立 Android 15 会话，1536 MB 内存、2 核、独立测试资料；不会连接其他手机。首次启动约一分钟。离开两分钟或运行满十五分钟，自动关闭模拟器与投屏进程；下一次清空本次测试虚拟机的数据重新开始。轻量 Node 服务与 SSH 通道保持在线，Mac 需开机联网。

ECS 只提供 HTTPS 转发，SSH 反向端口仅监听 ECS 回环；ADB、模拟器控制台和原始投屏端口均不公开。随机口令换取短期演示能力标识，API 使用请求头授权，画面仅通过带能力标识的命名路径提供且不记录访问日志；不依赖第三方 Cookie，支持 Pages 内嵌。同源检查、登录限速；只提供视频、触控和有限键码；浏览器不能选择设备、目标地址或 ADB 命令，没有 shell、文件浏览或任意网络代理。源码与示例配置中没有实际口令。

测试 App 里可以练习拼音、简拼、浏览 / 编辑测试资料、查看语音面板和主动截屏 OCR。不要输入真实账号、密钥、私人聊天。浏览器麦克风不会自动传入远端 Android，专用 ASR 与模型仍待配置；演示不伪造转写或回复。

部署入口 `demo/remote/host.mjs`，独立虚拟机生命周期 `emulator.mjs`，访问与视频边界 `server.mjs`。私有配置为忽略目录 `.runtime/remote-demo/config.json`，包含 `origin`、至少 20 字符的随机 `key` 和已有 `sshHost`。`nginx-location.example.inc` 仅用于现有 TLS server 中的命名路径。宿主停止时先结束模拟器，再关闭本次隧道；不操作其他服务。

## 本机设备演示

```sh
npm ci
npm run android:build
npm run demo:setup
npm run spec:serve
```

打开 `http://127.0.0.1:8560`，在“真实 APK”选择已解锁并授权 ADB 的设备。只安装 / 投屏选定设备，不自动启动模拟器。勾选临时授权才启用输入法与主动截屏，停止时恢复原设置。厂商安装确认仍由本人正常完成。

固定官方 scrcpy 3.3.3 服务端（SHA-256 见依赖锁）与 Tango WebCodecs 包版本。本机 Spec 8560、画面 8561、远程演示宿主 8562 都只监听 127.0.0.1，ECS 隧道 18562 也只监听 127.0.0.1。

## GitHub Pages

Pages 可以托管静态 Spec 和编译的 APK，但不能运行 Android、Node 或 ADB。`workflow_dispatch` 的 Pages 流程从同版本 Release 取得已经验收的 APK 与八张测试截图，逐一校验仓库中的 SHA-256 清单再构建 Spec。Pages 与 ECS 使用同一份签名 APK，发布文件不包含模型配置与运行资料。最初私有仓库因套餐返回 422；本人随后明确同意公开。历史检查未发现凭据或私人截图，已改为公开并开通 Pages；ECS 与 Pages 共用同一产品 Spec，远端 Android 仍通过 ECS 的隔离通道运行。
