# 真实 Android 画面

同一仓库内构建 APK、生成 Spec。Node / scrcpy 传输真实 Android 画面和触控，不把 HTML 冒充安卓。当前 APK 没有内置模型账号。

## 公开页面

2026-10-21 按用户要求停用连接个人 Mac 的公网演示：关闭 launchd 自动启动和反向隧道，原 `/keyboard-spec/live/` 路由返回 410，删除 Mac 桥接启动入口。公开 Spec 的“APK 预览”仅显示原生截图和下载链接；打开 `/#demo` 也不会启动或连接任何 Android。

后续在线试用的目标是独立云端 Android：安装本仓库发布的同一 APK，网页只传画面和触控，会话结束清空测试数据。GitHub Pages 负责静态页面，不连接家庭电脑或个人手机。

选择托管平台时，必须实测系统输入法启用、JNI 中文候选、辅助功能截图 / OCR。语音还需要浏览器麦克风传入 Android；仅支持设备音频播放不等于支持录音。当前没有部署新的云端试用服务。

`demo/remote/` 保留实验性设备生命周期与会话边界代码及测试，未作为公网服务启动；Nginx 示例保留关闭旧地址的配置。

## 网页安卓方案区别

2026-10-21 核查 nanoMuse 源码：官网使用 [MobileGym](https://github.com/nano-muse/nanoMuse/tree/main/demo/mobilegym)，由 React 模拟手机和应用，并不是运行 Android APK。[MobileGym 本体](https://github.com/Purewhiter/mobilegym)适合展示聊天与 Agent 操作；不能用来验收 Android 输入法服务、JNI 拼音或系统截屏。

- [NekoDroid 支持范围](https://github.com/nishal21/NekoDroid/blob/main/docs/SUPPORTED_APKS.md)：目前是有限的单 DEX 测试 APK 执行器，不支持 JNI、完整 Android 系统服务或一般应用。补到可运行本输入法，相当于补 Android 运行环境，不适合作为演示基础。
- [Appetize](https://docs.appetize.io/platform/embedding-apps)：上传 APK，在其云端运行并嵌入网页。官方价格页目前免费档每月 30 分钟、单次 3 分钟；先用有限额度验证输入法兼容性，不承诺免费长期公开试用。[定价](https://appetize.io/pricing)
- [Google Android Emulator 容器脚本](https://github.com/google/android-emulator-container-scripts)：完整 Android + WebRTC，服务器需要 KVM，项目标记为实验性。
- [redroid](https://github.com/remote-android/redroid-doc)：Linux 容器运行 Android，依赖 Binder 等内核能力，可配网页投屏；公开演示应放在独立云主机，不与个人数据和正式业务混用。

现有 ECS 没有 `/dev/kvm`，当前也未提供 Binder 设备；这不是断言其内核无法支持，但本次不为演示修改共用生产服务器内核。

## 本机设备演示

```sh
npm ci
npm run android:build
npm run demo:setup
npm run spec:serve
```

打开 `http://127.0.0.1:8560`，在“APK 预览”选择已解锁并授权 ADB 的设备。只安装 / 投屏选定设备，不自动启动模拟器。勾选临时授权才启用输入法与主动截屏，停止时恢复原设置。厂商安装确认仍由本人正常完成。

固定官方 scrcpy 3.3.3 服务端（SHA-256 见依赖锁）与 Tango WebCodecs 包版本。本机 Spec 8560 和画面 8561 只监听 127.0.0.1；已关闭公网桥接控制器 8562 与 ECS 隧道 18562。

## GitHub Pages

Pages 可以托管静态 Spec 和编译的 APK，但不能运行 Android、Node 或 ADB。`workflow_dispatch` 的 Pages 流程从同版本 Release 取得已经验收的 APK 与八张测试截图，逐一校验仓库中的 SHA-256 清单再构建 Spec。Pages 与 ECS 使用同一份签名 APK，发布文件不包含模型配置与运行资料。仓库已依本人授权公开，当前两处页面都只提供静态预览与下载。
