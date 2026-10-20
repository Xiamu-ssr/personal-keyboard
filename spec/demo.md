# 真实 APK 演示

## 什么是真的

浏览器中的 Android 画面来自真机或模拟器运行的原生 APK，使用开源 ws-scrcpy 传视频和触摸。Node 是本地宿主，不是安卓操作系统。默认不开虚拟机，不把网页动画当 APK。

本仓库提供可构建的系统输入法演示 APK：实际 InputMethodService、InputConnection 上屏、原生高度变化、授权后的系统截屏、示例结果与人物页。三条回复和“试说一句”是固定演示数据，未冒充真实模型、ASR 或 OCR。

## 运行

环境：Node.js 22+、JDK 17+、zip、Android SDK platform-tools / build-tools 35+ / android-35+。用 ANDROID_SDK_ROOT 指向 SDK；已安装 SDK 的 Mac 会尝试常见安装目录。只读 Spec 不需要 SDK。

    npm ci
    npm run spec:build
    npm run demo:build
    npm run demo:setup
    npm run spec:serve

打开 http://127.0.0.1:8560/#demo 。设备需开启 ADB 并已授权这台电脑。默认从页面选择设备，安装/启动只针对选中的序列号；不对所有已连接设备执行操作。

若需要独立模拟器，请先自行启动一个资源受限的专用 AVD，再在页面选择它。建议 1.5–2GB 内存、2 CPU；内存压力高时改用真机，不能为了演示挤垮 Mac。服务停止后关闭自己启动的模拟器，不停止别人的构建或设备。

演示 APK 包名 app.personalkeyboard.demo。初次需在 Android 设置启用该输入法。读屏服务开启后，军师才会执行系统截图；未开启时明确要求授权，保留草稿并允许返回，不播放假的截图成功动画。

真机测试前记下原输入法与辅助功能状态，结束后恢复。普通真机的锁屏、系统批准和无法操作的限制要如实反馈，不绕过设备认证。

截至 2026-10-20：原生 APK 已构建并通过签名检查，ws-scrcpy 已编译；OPPO 真机安装因锁屏与系统安装确认未完成，未把这轮标为原生界面/视频/触摸验收通过。原输入法与读屏没有被切换。解锁后在本页重试。

## 组件与范围

- ws-scrcpy 固定 commit cd6cea6ebde17af6685c2b325183fc24bef9d52b；安装时跳过第三方生命周期脚本及可选 Appium。
- 仅编译 Android、WebCodecs/H264 视频和触摸；禁用浏览器 shell、文件浏览、DevTools 与 iOS。
- HTTP 服务限定本机；Spec 页面 8560，Android 画面 8561。不是公开远程设备服务，不部署到公网。
- 设备 tracker 限定页面选择的设备，不启动其他已连接设备的投屏。已有投屏服务时要求先在原工具停止；演示结束只清理本次新建的 8886 转发与匹配的投屏进程。
- APK 无互联网权限，不包含用户模型 Key；截屏保存在应用私有缓存，重新截屏替换，退出清理。
- 浏览器画面可观察键盘高度、真实原生布局与上屏，不能证明普通微信/各 ROM 的生产适配或模型效果。

静态打开 Spec 时仍可阅读全部文档；真实设备入口会说明需要 Node 服务，而不是显示一个假的在线状态。
