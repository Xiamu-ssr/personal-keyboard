# 豆豆输入法

Android 输入法：离线拼音、个性语音、原地军师，以及热词、文风和人物记忆。键盘和管理页是同一个App，与豆豆Agent独立安装。

当前 **0.4.0-alpha.1 · 开发预览版**。App / PostgreSQL Cloud / 后台已有实现；企业短信和服务端模型凭据尚待配置，付费购买未开放。不会用演示回复冒充在线能力。

- [产品 SPEC](https://xiamu-ssr.github.io/personal-keyboard/) · [ECS镜像](https://106.14.73.99/keyboard-spec/)：基础域卡片和语音 / 军师两张域图。
- [下载 APK](https://xiamu-ssr.github.io/personal-keyboard/demo/doudou-keyboard.apk)：Android11+，arm64 / x86_64，debug签名；无供应商密钥。
- [Cloud后台](https://106.14.73.99/keyboard-api/admin/)：独立认证，用户 / 权益 / 套餐 / 任务 / 审计。
- [当前实现](spec/implementation.md) · [验收记录](spec/acceptance.md)。

## 三个目录

`app/` 是原生工程，内部 `app/app/` 为 Gradle应用模块；`cloud/` 是账号、账本、模型代理、任务和管理后台；`spec/` 是产品和架构对齐工件。

- 拼音：AOSP / libgooglepinyin离线整句、32K简拼、个人词频与快捷码。候选和动作共用一行。
- 语音：AudioRecord → Cloud → Qwen Message；实时文字、热词与近期上下文，基础规整和个人改写独立开关。
- 军师：缩矮 → 稳定布局 → 本地OCR / 可选当次图文理解 → 三条表达，支持更多和返回；不代发消息。
- 个性化：本机历史、热词 / 文风、人物 / 事件、带来源的增量学习候选；分批处理，不覆盖手动修改。
- 账户：手机号验证码；每个新账号一次性10,000字符＋20次军师，成功结算，失败与原任务重试不重复扣量。
- 同步：付费可开，设备加密包、UUID和向量时钟合并；免费只本地存储。密钥支持账号恢复，不宣称端到端加密。

App暂时内置 `https://106.14.73.99/keyboard-api`，使用正常系统证书验证。供应商密钥只配置在Cloud，预览版BYOK / 系统ASR路径已移除。补前文目前由本人滚动后补当前屏，最多六屏；尚无自动滚屏或可靠跨App认人。

## 构建

需要 Node22+、Python3、JDK17、Android SDK35、NDK27.0.12077973、CMake3.22.1。`ANDROID_SDK_ROOT` 指向SDK。

```sh
npm ci
npm run android:build
npm run android:test
npm run android:sanitize
npm run check
```

APK输出 `spec/demo/doudou-keyboard.apk`，摘要见 `artifact.json`。安装后启用并选择输入法；麦克风和主动截屏分别授权。云能力需登录，本地键盘无需登录。

## SPEC 与部署

`npm run spec:serve` 后打开 `http://127.0.0.1:8560`。大域内有子能力，域之间一来一回，数据卡片按时间自动上翻，可滚动查看旧记录。卡片是明确标识的模拟数据，提示词来自Cloud同一份源文件。

公开页面只提供真实APK截图、录屏和下载，不启动或连接个人Mac / ADB；旧在线Android桥接代码已移除。GitHub Pages与ECS发布同一份验收APK并校验摘要。

ECS 静态路由见 [Nginx 配置](spec/deploy/nginx-location.inc)，媒体使用明确白名单；旧在线演示路径固定返回 410。

Cloud通过独立API / PostgreSQL容器部署，宿主仅监听loopback8088，再经HTTPS反代。[运行说明](cloud/README.md) · [商业状态](spec/commercial.md) · [模型与成本](spec/models-and-cost.md) · [历史与同步](spec/memory-and-sync.md)。
