# 豆豆输入法

Android 输入法：离线拼音、个性语音、原地军师，以及热词、文风和人物记忆。键盘和管理页是同一个App，与豆豆Agent独立安装。

当前 **0.7.3-alpha.1 · 开发预览版**。App / PostgreSQL Cloud / 后台已有实现；邀请注册、账号密码与恢复码；企业百炼模型已接入，付费购买未开放。不会用演示回复冒充在线能力。

- [产品 SPEC](https://xiamu-ssr.github.io/personal-keyboard/) · [ECS镜像](https://106.14.73.99/keyboard-spec/)：基础域卡片和语音 / 军师 / 记忆更新三张域图。
- [下载 APK](https://xiamu-ssr.github.io/personal-keyboard/demo/doudou-keyboard.apk)：Android11+，arm64 / x86_64，debug签名；无供应商密钥。
- [Cloud后台](https://106.14.88.147/keyboard-api/admin/)：独立认证，用户 / 邀请码 / 权益 / 套餐 / 任务 / 审计。
- [规格源](spec/spec.yaml) · [原生验证摘要](spec/verification.json)。

0.7.3 补齐弧线语音手势、分层的「帮我回」面板、一键补看较早聊天、首次使用引导，以及键盘内的剪贴板与输入历史。工具区保留剪贴板、输入历史、免按说话和键盘设置，隐私输入单独开关。产品审查采用 Product Design，并交由独立内测复审。真人跨 App 使用质量仍需要试用反馈。

## 三个目录

`app/` 是原生工程，内部 `app/app/` 为 Gradle应用模块；`cloud/` 是账号、账本、模型代理、任务和管理后台；`spec/` 是产品和架构对齐工件。

- 拼音：AOSP / libgooglepinyin离线整句、32K简拼、个人词频与快捷码。候选和动作共用一行。
- 语音：AudioRecord → Cloud → Qwen Message；长按空格说话，录音时收起字母；松手填入、左侧撤回、右侧换行，滑回底部恢复正常结束。工具区可开启免按说话；个人口吻整理可选。
- 军师：点「帮我回」自动截图并生成三条完整表达；点回复填入、长按修改，换组可回看，不代发消息。
- 个性化：历史与截图在手机，热词、场景文风与人物主本在 Cloud；只分批历史，每次推理携带完整旧记忆，校验后按版本提交。
- 账户：邀请码注册＋账号密码，恢复码找回；每个新账号一次性10,000字符＋20次军师，成功结算，失败与原任务重试不重复扣量。
- 同步：个人记忆与缓存为基础账户能力，完整历史与截图的加密云备份按付费权益开放；不宣称端到端加密。

App暂时内置 `https://106.14.88.147/keyboard-api`，使用正常系统证书验证。供应商密钥只配置在Cloud，预览版BYOK / 系统ASR路径已移除。「多看一屏」经授权手势翻到更早聊天，再合并生成，最多三屏；首次点击不自动翻页。

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

`npm run spec:serve` 后打开 `http://127.0.0.1:8560`。大域内有子能力，域之间一来一回，数据卡片按时间自动上翻，可滚动查看旧记录。卡片是明确标识的模拟数据。域、实体、传输约定与提示词均来自 `spec/spec.yaml`；简短详情在点击节点后展开，没有 Markdown 文档层。

公开页面只提供真实APK截图、录屏和下载，不启动或连接个人Mac / ADB；旧在线Android桥接代码已移除。GitHub Pages与ECS发布同一份验收APK并校验摘要。

ECS 静态路由见 [Nginx 配置](spec/deploy/nginx-location.inc)，媒体使用明确白名单；旧在线演示路径固定返回 410。

Cloud通过独立API / PostgreSQL容器部署，宿主仅监听loopback8088，再经HTTPS反代。[运行说明](cloud/README.md)。
