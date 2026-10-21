# 真实 Android 画面

同一仓库内构建 APK、生成 Spec。Node / scrcpy 传输真实 Android 画面和触控，不把 HTML 冒充安卓。当前 APK 没有内置模型账号。

## 在线演示

点开 Spec 的“真实 APK”即可启动，不需要口令或第二次点击；也可直接打开 `/#demo`。普通 Spec 页面不启动模拟器。Mac 创建独立 Android 15 会话，1536 MB 内存、2 核，只含测试资料；不会连接其他手机。首次启动约一分钟。关闭演示回收资源；离开两分钟或运行满十五分钟也会关闭，下次清空测试设备数据重新开始。Mac 需开机联网。

ECS 只提供 HTTPS 转发，SSH 反向端口仅监听 ECS 回环；ADB、模拟器控制台和原始投屏端口均不公开。浏览器自动取得短期会话，API 使用请求头、视频使用专用路径，不依赖第三方 Cookie。一次只允许一位访客使用设备，其他访客不能查看、接管或结束这次体验。同源检查、会话限速与生命周期上限保留；只提供视频、触控和有限键码，没有 shell、文件浏览或任意网络代理。

测试 App 里可以练习拼音、简拼、浏览 / 编辑测试资料、查看语音面板和主动截屏 OCR。不要输入真实账号、密钥、私人聊天。浏览器麦克风不会自动传入远端 Android，专用 ASR 与模型仍待配置；演示不伪造转写或回复。

部署入口 `demo/remote/host.mjs`，独立虚拟机生命周期 `emulator.mjs`，访问与视频边界 `server.mjs`。忽略目录 `.runtime/remote-demo/config.json` 只需 `origin` 和已有 `sshHost`；不再配置演示口令。`nginx-location.example.inc` 仅用于现有 TLS server 的命名路径。宿主停止时先结束模拟器，再关闭本次隧道；不操作其他服务。

## 网页安卓方案区别

2026-10-21 核查 nanoMuse 源码：官网使用 [MobileGym](https://github.com/nano-muse/nanoMuse/tree/main/demo/mobilegym)，由 React 模拟手机和应用，并不是运行 Android APK。[MobileGym 本体](https://github.com/Purewhiter/mobilegym)适合展示聊天与 Agent 操作；不能用来验收 Android 输入法服务、JNI 拼音或系统截屏。

[NekoDroid](https://github.com/nishal21/NekoDroid)尝试用 WebAssembly 在浏览器执行 APK，但当前明确不支持 JNI 和完整 Android 用户空间。[Google 的网页安卓方案](https://github.com/google/android-emulator-container-scripts)则在服务器运行完整 Android，再把画面传入浏览器，宿主需要 KVM。我们目前的真实 APK 演示同属远端 Android 画面传输，宿主是 Mac；ECS 没有 KVM，当前也未提供 Binder 设备，未在这台共用服务器改内核或加载模块。

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
