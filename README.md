# 豆豆输入法

安卓输入法：离线拼音、个性语音、原地军师，以及热词、文风和人物记忆。和豆豆是两个独立安装的 App；本地资料为主，商业目标由产品 Cloud 提供账号、权益、模型代理与付费云同步。

当前版本 **0.3.0-alpha.1 · 自用预览版**。原生界面参照 Typeless 的层级与豆包的键帽 / 工具格重做，代码按业务域拆分。新版使用 Typeless 参考的炭灰与白色主题、麦克风胶囊及真实音量反馈。Qwen Message 和 Fun-ASR 实时接口已通过独立短音频测试；APK 尚未接入云 ASR 或实际文字模型账号。

- [在线产品 Spec](https://xiamu-ssr.github.io/personal-keyboard/) · [ECS 镜像](https://106.14.73.99/keyboard-spec/)：基础域卡片与语音 / 军师两张业务图，提示词、权益和数据流可按步骤展开。
- [下载自用 APK](https://xiamu-ssr.github.io/personal-keyboard/demo/doudou-keyboard.apk)：Android 11+，arm64 / x86_64，debug 签名；不含模型密钥。
- [截图与操作动画](https://xiamu-ssr.github.io/personal-keyboard/#demo)：实际 APK 录制的拼音输入、工具 / 语音面板，以及军师截屏识字；首页、记忆、人物和设置截图。
- [当前实现与边界](spec/implementation.md) · [验收记录](spec/acceptance.md)

## 仓库结构与商业目标

- `app/`：原生 Android 工程；内部 `app/app/` 是 Gradle 应用模块。
- `cloud/`：账户 / 权益 / 模型 / 提示词 / 同步 / 运营后台的服务边界。
- `spec/`：基础域、两张业务图和各步骤约定。域颜色贯穿卡片与图；提示词来自 `cloud/prompts/`。

默认云 ASR 选 Qwen Audio 3.1 Message，文字模型待定。商业流量统一 App → Cloud → 供应商，App 只持产品登录凭证。新账号一次性 10,000 个转写字符＋20 次军师；免费只本地存储，付费可提供产品云同步。价格、额度与功能在 `cloud/config/plans.json` 版本化配置，Spec 读取同一份数据。

当前 Cloud **只有可测试的参考权益领域、API 目标契约与容器骨架**，没有生产登录、持久账本、模型代理、支付、云同步或运营后台。`/healthz` 明确返回 `productionReady=false`。这些是商业目标，不能从当前预览 APK 购买或使用。[商业约定](spec/commercial.md) · [Cloud 实现边界](cloud/README.md)。

## 现在的 APK 能做什么

- 正常打字：AOSP / libgooglepinyin 离线整句解码、32K 简拼词条、可学习的候选排序、自定义快捷码 / 热词、中英文与符号、长按退格、滑动空格移动光标。候选与动作共用一行。
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

安装后，在系统启用并选择豆豆输入法。App 首页有本机练习区。麦克风、主动截屏独立授权；模型设置支持 HTTPS 的 OpenAI / Anthropic 接口，填真实模型 ID，不会悄悄替换成演示回复。服务证书须受设备信任。0.1 升级至 0.2 后，资料和凭据存储标识保持不变；因组件归域迁移，需在系统重新选择输入法并重新开启主动截屏。

## Spec 与真实 Android 画面

```sh
npm run spec:build
npm run demo:setup
npm run spec:serve
```

打开 `http://127.0.0.1:8560`。Spec 分基础域卡片和两张业务图，详细约定、提示词与代码归属从对应域或步骤展开。本地可选择已授权设备运行同一 APK，停止后恢复临时设置。公开页面提供原生截图、使用录屏和 APK 下载，在线安卓试用已取消。GitHub Pages 与 ECS 校验并发布同一份已验收的 Release APK；录屏校验清单见 `spec/media.json`。

- [产品](spec/product.md) · [交互](spec/interaction.md) · [架构](spec/architecture.md)
- [模型、购买与成本](spec/models-and-cost.md) · [研究来源](spec/research.md)
- [本地历史、增量记忆与付费云同步](spec/memory-and-sync.md)
- [设备演示](spec/demo.md) · [第三方拼音源码与改动](app/app/src/main/cpp/vendor/aosp-pinyin/LOCAL-CHANGES.md)
