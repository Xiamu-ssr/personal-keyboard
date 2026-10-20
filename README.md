# 豆豆输入法

安卓个人输入法：离线拼音、语音整理、原地军师，以及热词、文风和人物记忆。和豆豆是两个独立安装的 App；当前资料保存在输入法本机。

当前版本 **0.1.0-alpha.1 · 自用预览版**。原生 App 已替代旧固定场景 APK。按本轮约定，付费 ASR 和实际文字模型账号稍后配置。

- [在线产品 Spec](https://106.14.73.99/keyboard-spec/)：交互式业务 / 技术域模型。
- [下载自用 APK](https://106.14.73.99/keyboard-spec/demo/doudou-keyboard.apk)：Android 11+，arm64 / x86_64，debug 签名；不含模型密钥。
- [当前实现与边界](spec/implementation.md) · [验收记录](spec/acceptance.md)

## 现在能做什么

- 正常打字：AOSP / libgooglepinyin 离线整句解码、候选选词、自定义热词、中英文与符号、长按退格、滑动空格移动光标。候选与动作共用一行。
- 语音：调用手机提供的 ASR，优先本地；系统在线识别需主动开启。转写可修正、整理、填入、撤销；无语音服务或模型时显示实际原因。
- 军师：缩矮键盘、等宿主布局稳定、主动截屏、本地中文 OCR、确认人物、修正识字 / 意图。文字模型接入后一次返回三个风格，支持更多和返回旧结果。
- 个性化：热词、文风、历史和可确认的修改反馈；人物图及分人物的事实、事件、偏好、推断，均可修订、删除和撤销。
- 数据：SQLite 版本与删除标记、保留期限、导出 / 导入；可选服务密钥使用 Android Keystore 加密保存。密码框禁用语音、军师和学习。

当前没有内置付费 ASR，没有自动跨 App 认人、自动滚屏或豆豆云同步。补前文先由本人滚动聊天，再点“补当前屏”；OCR 不会擅自把说话人或草稿认定为事实。模型自动归类 / 提取记忆留在后续模型联调阶段，现阶段提供可审阅反馈和人物资料管理。

## 构建与使用

需要 Node.js 22+、Python 3、JDK 17、Android SDK 35、NDK 27.0.12077973、CMake 3.22.1。用 `ANDROID_SDK_ROOT` 指向 SDK；Mac 会尝试常见路径。

```sh
npm ci
npm run android:build
npm run android:test
npm run android:sanitize
```

APK 输出到 `spec/demo/doudou-keyboard.apk`，摘要见相邻 `artifact.json`。`android:test` 在 macOS / Linux 编译真实 JNI 解码器，执行 JVM 核心测试和 Android lint；设备测试见验收文档。

安装后，在系统启用并选择豆豆输入法。App 首页有本机练习区。麦克风、主动截屏独立授权；模型设置支持 HTTPS 的 OpenAI / Anthropic 接口，填真实模型 ID，不会悄悄替换成演示回复。服务证书须受设备信任。

## 产品世界与真实 Android 画面

```sh
npm run spec:build
npm run demo:setup
npm run spec:serve
```

打开 `http://127.0.0.1:8560`。Spec 直接表达域、能力、数据归属和关系契约，详细文档从图中展开。Node / ws-scrcpy 只向选定设备传输真实 Android 画面与触摸，不自动启动模拟器；临时验收结束恢复原输入法和辅助功能状态。

- [产品](spec/product.md) · [交互](spec/interaction.md) · [架构](spec/architecture.md)
- [模型、购买与成本](spec/models-and-cost.md) · [研究来源](spec/research.md)
- [设备演示](spec/demo.md) · [第三方拼音源码与改动](android/app/src/main/cpp/vendor/aosp-pinyin/LOCAL-CHANGES.md)
