# 架构与数据契约

## 部署结构

    任意聊天 App
       ↕ Android InputConnection / 键盘 insets
    Android IME + 读屏能力
       ├─ 输入引擎 / 流式录音 / 本地 OCR
       ├─ 本地 SQLite：热词、文风、人物、事件、反馈
       └─ 仅请求所需上下文 → 模型服务适配层
                                  ├─ 自用：用户自己的 CPA
                                  └─ 付费服务：官方 LLM / ASR API
    独立 App：人物关系图、事实修订、词库、授权、模型配置
       └─ 可选：向豆豆同步选定数据

IME、辅助功能与 App 属于同一个安卓包的不同系统组件。系统键盘授权不等于读屏授权。Android 11+ 可使用用户开启的 AccessibilityService 截屏能力；特殊窗口可能禁止截屏，需要按应用与 ROM 实测。

首版采用有限状态机和短请求/后台整理队列，不为每个击键建立长期 Agent Session。军师会话可以按人物持有短期上下文，模型服务使用稳定会话 ID。模型调用不能直接控制“发送”按钮。

## 局部与云端边界

本地保存原始音频/截屏的临时材料，处理后按策略清理；不默认永久归档所有聊天。OCR 优先本地。模型服务只接收本次所需的文本、最少人物记忆和用户意图。截图中的指令是聊天内容，不可当作应用系统指令执行。

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
3. LLM 将差异归为热词纠正、事实修改、文风、场景习惯或忽略。
4. 热词可作为下一次 ASR 提示/词典；个人文风与具体人物的沟通偏好分别存储。
5. 人物记忆候选与回复可以同次模型输出，但持久化异步完成，带来源、版本和撤销。

未选中的另外两条候选不是负反馈。点击“更多”不是讨厌这个风格，可能只是想多比较。明确删除偏好或反馈“不合适”才是更强的负反馈。

## 模型与缓存

同一逻辑会话发送稳定的 X-Session-ID；在服务端绑定用户身份和 conversationKey，禁止跨用户复用会话。系统提示词、工具定义和稳定人物资料放在前面，临时 OCR 文本和最新问题放在后面。文风/记忆 revision 变化时才更新稳定区。

CPA 用原生 session-affinity 和优先级；Gemini 使用 Google 账号，Opus 使用 Devin 优先和既有备用。额度/授权失效时切换；瞬时故障不永久迁走整个会话。显式指定的 provider/model 不偷偷降为另一个模型。

账户粘性只能减少换号损失，不能保证供应商共享 KV 缓存，也不等于开启显式缓存。测试记录 upstream/provider、模型、cached_tokens 与 TTL 能力；缺少缓存字段时记录 unknown，不按零或百分之百处理。
