# Personal Keyboard

安卓个人输入法：日常输入、个性化语音、原地军师和人物记忆。与豆豆分开安装、分开使用；后续可按用户授权同步选定的个人数据。

当前交付是可浏览的产品 Spec 与可安装的原生 Android 交互演示。不是已完成的生产输入法。

## 看 Spec

手机或外网查看：[ECS 在线 Spec](https://106.14.73.99/keyboard-spec/)。云端提供完整文档与成本计算器；真实 Android 画面仍只在本机连接设备后运行。

    npm ci
    npm run spec:build
    npm run spec:serve

打开 http://127.0.0.1:8560 。离线查看可直接打开 spec/index.html；真实 APK 画面需要本地 Node 服务、Android SDK、已授权的真机或独立模拟器。

Spec 包含产品/业务地图、状态流、原生 APK 体验、人物数据契约、模型与价格计算器、来源和验收状态。

## 真实 APK 演示

需要 Node.js 22+、JDK 17+、Android SDK（platform-tools、build-tools 35+ 和 android-35+）及 zip。Android 11+ 真机或已启动的独立模拟器需要解锁并允许本机 ADB；手机厂商仍可能要求本人确认安装。只看 Spec 不需要 Android SDK。

    npm run demo:build
    npm run demo:setup
    npm run spec:serve

在 Spec 的“真实 APK”中选择设备并启动。Node/scrcpy 只传输和控制 Android 画面，APK 实际运行在设备上，不是把 HTML 键盘包装成安卓画面。工具不会默认启动模拟器，也不会把手机序列号或截图上传到仓库。

演示 APK 实现系统 IME、键盘与结果面板切换、收缩后真实截屏、三种示例回复填入、保留草稿和人物资料页。演示回复使用固定场景；生产中文拼音引擎、流式 ASR、本地 OCR、模型调用、纠错学习与云同步见 Spec 的待实现项。

- [产品与业务](spec/product.md)
- [交互与高度变化](spec/interaction.md)
- [架构与数据](spec/architecture.md)
- [模型、购买与成本](spec/models-and-cost.md)
- [真实 APK 演示](spec/demo.md)
- [调研及截图观察](spec/research.md)
- [验收与实现边界](spec/acceptance.md)

价格核验日期：2026-10-20。无账号密钥内置在 APK 或 Spec 中。
