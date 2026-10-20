# 对标与截图观察

核验日期：2026-10-20。区分官方截图/产品说明、读过的代码和实际装机验证。第三方图片只用于观察，不作为自有产品截图发布。

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
- [ws-scrcpy](https://github.com/NetrisTV/ws-scrcpy)：本 Spec 的真实 Android 画面与控制组件，MIT；固定源码 commit，禁用 shell、文件浏览和 iOS 组件。

FlorisBoard 官方中文文档说明其音码算法不足，不推荐日常拼音。不能仅因某个 AI 键盘基于它，就直接选为本项目中文底座。

## 首轮产品实验

拿同一批经本人授权的聊天，比较同模型“无人物记忆”与“有记忆”，隐藏版本名称再评。观察可直接使用的比例、修改量、等待时间、错认人物、错误承诺和重复使用。图谱规模、生成条数、模型自评不算用户价值证据。
