# 对标与截图观察

核验日期：2026-10-21。区分官方截图/产品说明、读过的代码和实际装机验证。第三方图片只用于观察，不作为自有产品截图发布。

## 交互参考

用户本轮补充了七张实际 Typeless 截图，覆盖首页、历史、词典、账户及三种键区状态。早期曾据此制作界面对稿；原生 App 接替后移除旧稿：保留键位比例、行内复用、词条来源和底栏层级，新增军师缩矮/展开与人物记忆。参考原图不进入仓库或公开页面，重绘一律使用演示内容。截图可证明可见布局，不证明真实触摸尺寸、ASR 质量或纠错学习实现。

- [Typeless 安卓商店截图](https://play.google.com/store/apps/details?id=com.typeless.mobile)：语音是主任务，关键动作集中在键区内。参考录音与整理状态，不为每项能力加一个常驻导航条。
- [Typeoff 官方页面](https://www.typeoff.ai/) 与 [上手说明](https://www.typeoff.ai/docs/getting-started)：切入输入法后围绕一次说话/整理，支持跨 App。移动端完整拼音引擎不能仅凭营销截图推定成熟。
- [Replr 商店截图](https://apps.apple.com/au/app/replr-ai-texting-keyboard/id6775636883)：回复直接在键盘区域呈现，选中后填入消息框；可参考三条候选密度和返回路径。
- [SharkeyBoard](https://sharkeyboard.us/)：按人物/角色保存上下文，三条回复；公开流程仍依赖复制消息。官网的多专家、长期记忆属于产品声明，未实测其准确性。
- [WittyKeys](https://wittykeys.com/)：通知、主动截屏和联系人识别有不同权限来源，值得把入口与授权拆清楚。
- [Lovekey](https://apps.apple.com/cn/app/id6478108100)：国内已有键盘回复、人设与亲密度选择。不能声称没有竞争，需求规模和付费意愿仍需验证。

本项目采用一条候选/动作行、语音与军师接管键区、捕获时缩矮/结果时展开。键盘几何先沿用成熟布局，再验证拇指误触率，不能从宣传海报推导按键命中面积。

本轮实际查看了 Typeless 的 Google Play 截图页，以及 Typeoff 官方网页。Typeless 的截图用于观察录音主区域与少量动作的层级；Typeoff 网页不等同于其完整移动输入法。Replr 的回复区域设计来自商店说明/截图资源，未装机验证。未把这些参考当作按键尺寸、中文候选或纠错质量的实测依据。

追加的两张豆包输入法截图用于键帽与工具菜单：字母键边界清楚、底行有独立标点与中英切换，工具以大块图标和短名称排列。原生键盘采用这种按需展开的工具宫格，避免把所有功能常驻在候选行。

## 可复用项目

- [Fcitx5 Android](https://github.com/fcitx5-android/fcitx5-android)：已有拼音/双拼等引擎，是中文底座候选；具体许可证按所用前端、引擎与插件逐个核对。
- [Trime](https://github.com/osfans/trime)：Android Rime 前端，支持多方案，GPL-3.0。键盘布局自由度和维护成本需做对比。
- [VoiceFlow Keyboard Android](https://github.com/yutungh/voiceflow-keyboard-android)：语音键盘与 LLM 整理的原型参考，不能据此称为成熟中文键盘。
- [ReplyWise](https://github.com/raja21068/ReplyWise-Local)：读取了按联系人检索与偏好学习实现。选择语气/长度可写回提示词；不能照搬“没选中就是负反馈”的计分。
- [VoiceInk](https://github.com/Beingpax/VoiceInk)：自动学习词典的桌面参考；辅助功能观察范围与安卓 IME 不同。
- [Tango / ya-webadb](https://github.com/yume-chan/ya-webadb) 与 [scrcpy](https://github.com/Genymobile/scrcpy)：Android 15 画面与触控，分别为 MIT / Apache-2.0；依赖锁固定版本与服务端 SHA-256，只提供视频、点按和键码。旧 ws-scrcpy 因系统接口失效产生黑屏，已迁移移除，不维持两套投屏协议。

FlorisBoard 官方中文文档说明其音码算法不足，不推荐日常拼音。不能仅因某个 AI 键盘基于它，就直接选为本项目中文底座。

## 首轮产品实验

拿同一批经本人授权的聊天，比较同模型“无人物记忆”与“有记忆”，隐藏版本名称再评。观察可直接使用的比例、修改量、等待时间、错认人物、错误承诺和重复使用。图谱规模、生成条数、模型自评不算用户价值证据。

## 2026-10-21 实际视频与本版视觉

查看 [Typeless 官方 Android 发布视频](https://www.youtube.com/watch?v=0Zb8AieP7I4)，实际跳转观察移动端录音区与转写呈现。视频中浅色主题与用户提供的深色截图不是同一个配色：本版以用户深色截图为准，采用中性炭灰 / 白色、居中大麦克风胶囊、两侧次要动作、说完后修正 / 整理 / 填入。保留豆包参考的键帽与工具格，不复制 Typeless 品牌或宣传素材。

麦克风、录音音量条与识别等待使用不同状态：音量条只消费真实 RMS 回调；等待是旋转进度，离开即取消，系统关闭动效时不旋转。未声称还原了视频每一帧或测得其动画时长。

[Appetize](https://docs.appetize.io/platform/embedding-apps) 可云端运行 APK 并嵌入网页，但[官方明确不支持麦克风输入](https://support.appetize.io/does-appetize.io-support-audio-including-sound-or-microphone)；系统输入法和截屏仍须实测。浏览器原生 Android 项目并非都能运行任意 APK：[NekoDroid](https://github.com/nishal21/NekoDroid/blob/main/docs/SUPPORTED_APKS.md) 目前只支持有限测试程序，尚不支持 JNI 与完整 Android 用户空间。本项目有 JNI 输入引擎和系统 IME，不能拿它替代完整 Android。此前 Mac + scrcpy / Tango 公网演示已依用户要求关闭。曾研究独立云主机上的 [Cuttlefish](https://source.android.com/docs/devices/cuttlefish/get-started)，其官方 WebRTC 界面可控制完整 Android，并列出麦克风扩展路径；本输入法端到端兼容性未验证。用户已取消在线试用，当前只发布实际 APK 截图、操作录屏与下载，详见设备演示文档。

2026-10-21 补查 [nanoMuse 的演示源码](https://github.com/nano-muse/nanoMuse/tree/main/demo/mobilegym)：使用 [MobileGym](https://github.com/Purewhiter/mobilegym)，用 React 复现手机和应用，模拟手机全部在浏览器运行，不需要 Android 设备或虚拟机。适合展示 Agent 的手机操作；本项目的系统输入法服务与 JNI 拼音仍须用完整 Android 验证。

## 2026-10-21 · 个性化机制核验

以下核对官方帮助与源码，未装机复现第三方自动学习。本节只说明公开证据；[我们的推荐](design-options.md)另列。

| 来源 | 公开确认 | 没有确认的事 |
| --- | --- | --- |
| [Typeless 词典](https://www.typeless.com/help/quickstart/history-and-dictionary) | 听写后纠正词，偏好拼写自动加入词典；历史在设备上，可选保留期限 | 未披露底层监听算法；不能推成定期扫描全部历史 |
| [Typeless 个性化](https://www.typeless.com/help/quickstart/personalization) | 声称逐渐学习措辞、正式程度、长短等抽象风格；可关闭学习 | 没公开更新周期、训练方式或五小时规则；说明不等于 Android 功能完全一致的实测 |
| [Typeoff AI Enhancement](https://www.typeoff.ai/docs/guides/ai-enhancement) / [Features](https://www.typeoff.ai/docs/features) | 可选 ASR 后 AI 整理、按上下文 / 预设调整表达；纠词学习有 Ask / Auto / Off。云端流式，离线 Whisper 仅桌面 | 说话中的自我纠正与上屏后的纠词学习是两件事；未公开最终发送捕获机制或移动端全部细节 |
| [豆包输入法隐私政策](https://lf3-cdn-tos.draftstatic.com/obj/ies-hotsoon-draft/wave_ime/ime_privacy_policy.html) / [官方 Android 商店说明](https://play.google.com/store/apps/details?id=com.bytedance.android.doubaoime) | 政策明确本地个人词库；云端语音可结合实体词、人名、部分输入 / 上屏文字与场景，登录后可跨端同步词库；Android 提供上下文联想、简拼与长句纠错 | 政策不是纠词算法源码，没有承诺所有 App 的最终发送文本可捕获；不能混同个性化与体验改进训练 |
| [Wispr Flow 词典](https://docs.wisprflow.ai/articles/4052411709-Teach-Flow-your-words-with-the-dictionary) | 桌面端可选纠词学习，过滤普通词、URL、路径等；不是每个改字都入词典 | 平台有差异，不能把桌面监听能力套到安卓；词典偏好与字面替换是不同机制 |
| [Wispr Flow Styles](https://docs.wisprflow.ai/articles/2368263928-how-to-setup-flow-styles) | 按应用场景选择格式风格 | 不是自动从全部历史学习完整人格的证据 |
| [VoiceInk AutoLearnService](https://github.com/Beingpax/VoiceInk/blob/main/VoiceInk/Features/Dictionary/AutoLearn/AutoLearnService.swift) / [快照比较](https://github.com/Beingpax/VoiceInk/blob/main/VoiceInk/Features/Dictionary/AutoLearn/FinalSnapshotDiffEngine.swift) | 读取的源码包含插入范围 / 锚点、前后快照差异、候选队列与模型审核；候选入队后可触发审核，也支持手动 | 这套是桌面实现，不证明安卓能拿到所有发送前改稿 |

推荐事件驱动与空闲合批，有工程先例：[LangMem 延迟处理](https://langchain-ai.github.io/langmem/guides/delayed_processing/)展示把连续活动合并后再反思；它的示例延迟不是我们产品的既定更新周期。

这次补查的结论：词典、上下文识别、按场景整理与纠词学习已有多个产品实现，应作为基础能力。公开资料不足以还原这些闭源产品的完整算法；我们的最小历史、修订指针和 App / Cloud / LLM 分工是工程建议，不冒充竞品内部实现。VoiceInk 源码提供了本机候选队列 → 模型审核 → 本机词典应用的具体先例，不需要据此假设训练了个人模型。

### 最终发送文本的边界

[Android InputConnection](https://developer.android.com/reference/android/view/inputmethod/InputConnection#performEditorAction(int)) 的读取可能为空 / 截断，编辑动作返回值也不是聊天消息回执。因此不能声称跨 App 百分百抓到最终发送内容。本轮采用来源、实际填入基线和最后有效修改稿；以本人表达和纠词作为学习信号，不要求发送回执。近期表达优先本人修改稿，聊天事实依据有说话人归属的截图内容。对编辑的观察不等于为每次按键保存快照。

### OCR 与多模态

[ML Kit OCR](https://developers.google.com/ml-kit/vision/text-recognition/v2)可本地识别文字并返回文字块、位置等，原图可以不上传；但聊天人物、引用与关系理解还需额外规则或模型。不能说 OCR 必然丢失所有布局。

[Qwen3-VL 官方项目](https://github.com/QwenLM/Qwen3-VL)提供图文理解、视觉定位与 OCR 等能力，适合直接处理聊天画面。**所以推荐军师一期直接多模态，避免维护额外的聊天 OCR 解析层**；这是工程判断，尚无本项目同样本准确率 / 成本对照，不宣称一定更准或更便宜。

直接多模态会上传本次截图；本机历史和云端推理是不同数据用途。截图与文字均可能被误读，人物记忆必须能追溯来源，不能只信模型自报置信度。

## 2026-10-22 · 本轮方案对齐

竞品机制为研究事实，本产品不照搬所有选项：自动学习直接启用。已选择 Cloud 个人记忆主本、手机缓存；App 提交历史变化，Cloud 用受限结构化 LLM 任务核验落库，手机同步完整变更记录。这是本产品的架构选择，不是竞品内部实现。个人热词可接入离线候选，文风和人物用于语音整理与军师；接入效果尚待 APK 验证。
