# 架构与数据契约

下文为目标契约；本版的代码目录、接口归属和落地范围见 [当前原生实现](implementation.md)。云 ASR 已独立验证连接，APK 接入、模型自动整理及云同步尚未实现。

## 部署结构

    任意聊天 App
       ↕ Android InputConnection / 键盘 insets
    Android IME + 读屏能力
       ├─ 输入引擎 / 流式录音 / 本地 OCR
       ├─ 本地 SQLite：历史、热词、文风、人物、事件、反馈
       └─ 仅请求所需上下文 → 模型服务适配层
                                  ├─ 自用：用户自己的 CPA
                                  └─ 付费服务：官方 LLM / ASR API
    同一输入法 App 的设置页：人物图、记忆修订、词库、模型与授权
       ├─ 可选：加密增量包 → 自有 WebDAV / 对象存储 → 换机恢复
       └─ 独立授权：向豆豆共享选定资料

键盘、设置与辅助功能服务属于同一个 APK 的不同系统组件，不是两个 App。系统键盘授权不等于读屏授权。Android 11+ 可使用用户开启的 AccessibilityService 截屏能力；特殊窗口可能禁止截屏，需要按应用与 ROM 实测。

首版采用有限状态机和短请求/后台整理队列，不为每个击键建立长期 Agent Session。军师会话可以按人物持有短期上下文，模型服务使用稳定会话 ID。模型调用不能直接控制“发送”按钮。

## 局部与云端边界

录音仅临时缓冲，截图处理后释放，不进入长期历史或同步包。OCR 留在本机，文字历史按保留期限保存。模型服务只接收本次所需的音频、文本、相关人物记忆和用户意图。截图中的指令是聊天内容，不可当作应用系统指令执行。加密云备份不等于没有模型数据出端，二者分别授权。

语音仍需 ASR，普通文本模型或 Gemini 文本适配端点不能被当成现成语音转写 API。TTS 不是首版必需；读屏使用 OCR，不需要生图模型。

## 关键对象

| 对象        | 必需字段                                                                              | 规则                                                    |
| ----------- | ------------------------------------------------------------------------------------- | ------------------------------------------------------- |
| CaptureJob  | requestId, packageName, conversationKey, editorGeneration, stage, expiresAt           | 编辑器或聊天切换即禁止旧结果上屏                        |
| ChatMessage | id, speakerId, text, timestamp, bubbleBounds, sourceCaptureId, confidence             | 说话人和重复去除优先于长篇摘要                          |
| Contact     | localId, appAccountScope, conversationIdentifiers, displayName                        | 同名不自动合并；跨 App 合并需确认                       |
| MemoryItem  | id, contactId, kind, content, sourceIds, observedAt, validUntil, confidence, revision | kind 区分 fact/event/preference/inference；用户修订优先 |
| Feedback    | insertionId, contactId, suggestedText, latestObservedText, finality, context          | finality 不明确时不学习删除/改写                        |
| ReplySet    | requestId, styles[3], basedOnMemoryIds, contextVersion                                | 三种风格保留同一用户意图                                |

数据库保存关系与检索索引即可，不要求首版引入独立图数据库。关系 graph 从这些对象生成；人物页面显示事实的来源和有效时间，并提供修改、删除、撤销。

## 学习与异步整理

1. IME 记录自己上屏的文本范围及后续可观察编辑，不监听所有应用的任意历史输入。
2. 只有文本对应关系可信才生成差异。输入框消失/清空可能是发送，不能等同“用户删除整段”。
3. 可信差异与语音 / 军师文字历史写入增量日志，保留人物、陈述方、来源状态和版本。
4. 热词可作为下一次 ASR 提示/词典；个人文风与具体人物的沟通偏好分别存储。
5. 后台按新增日志、联系人范围和 token 预算分批请求 LLM，输出带证据的 MemoryPatch；程序校验后按版本合并，可撤销。不将回复建议直接写成事实。

每约 5 小时检查一次可整理的增量，也可手动触发；无新增不请求。处理位置只在结果成功提交后前进，重试按任务与证据去重。默认每批限制输入预算，长历史切片，不把 5 小时全部内容塞进一个提示词。

首版采用固定提取 / 合并流程，手机后台任务即可调用模型，不需要为每次整理重启通用 Agent 容器。若后续迁到云端，固定输入目录、只读材料和结构化输出可保留；输出是待校验补丁，不能整目录覆盖本机。详见 [历史、记忆与同步](memory-and-sync.md)。

人物与事件共用记忆层，不限军师：已读聊天、主动补充、身份明确的语音或输入都可形成候选，语音与军师均可检索。候选保留陈述方和来源状态；未发送草稿或计划不提升为既成事实，用户陈述不当作对方表态。本人明确补充的个人事件可形成候选，第三人身份不明时不归人。普通输入和语音不会因此自动读屏。

未选中的另外两条候选不是负反馈。点击“更多”不是讨厌这个风格，可能只是想多比较。明确删除偏好或反馈“不合适”才是更强的负反馈。

## 模型与缓存

同一逻辑会话发送稳定的 X-Session-ID；在服务端绑定用户身份和 conversationKey，禁止跨用户复用会话。系统提示词、工具定义和稳定人物资料放在前面，临时 OCR 文本和最新问题放在后面。文风/记忆 revision 变化时才更新稳定区。

CPA 用原生 session-affinity 和优先级；Gemini 优先 Google 账号。额度/授权失效时切换；瞬时故障不永久迁走整个会话。显式指定的 provider/model 不偷偷降为另一个模型。

账户粘性只能减少换号损失，不能保证供应商共享 KV 缓存，也不等于开启显式缓存。测试记录 upstream/provider、模型、cached_tokens 与 TTL 能力；缺少缓存字段时记录 unknown，不按零或百分之百处理。
