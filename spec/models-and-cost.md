# 模型、采购与成本

接口补查：2026-10-21；其余价格基线：2026-10-20。单价依地域、上下文区间及供应商更新；下表是选型基线，不是自动购买授权。实际调用前在控制台确认。

## 推荐组合

首版只要 **两种远程能力：一个 ASR + 一个文本模型**。可选个性改写、三种回复、差异分类和记忆抽取共用文本模型，不必各申请一个。中文 OCR 随设备端模型运行，不需购买远程 OCR；TTS、生图暂不购买。自用文本可先复用现有 CPA，新增采购主要是 ASR。已在本机受限配置中保存两模型测试凭据；凭据不进入仓库或 APK。

| 能力               | 自用首选                                               | 可采购替代                                 | 原因                                     |
| ------------------ | ------------------------------------------------------ | ------------------------------------------ | ---------------------------------------- |
| 中文流式语音       | 阿里云 Qwen-Audio 3.1 Message，Fun-ASR Realtime 对照 | Groq Whisper Large V3 Turbo 作为非流式对照 | 中文、热词、短句延迟须用本人语音评测     |
| 润色、三种回复     | 现有 CPA 中可用的 Gemini Flash                         | 阿里云 qwen3.7-flash                       | 先利用已有订阅；对外收费使用稳定官方 API |
| 差异分类、记忆抽取 | 小型文本模型，合并/批处理                              | qwen-flash / qwen3.7-flash                 | 处理短结构化输出                         |
| OCR                | ML Kit 中文设备端模型，随 APK 打包                     | PaddleOCR 本地方案待设备评估               | 无逐次 API 费用，不要求 Play 商店安装    |
| TTS / 生图         | 首版不需要                                             | 后续按具体功能采购                         | 语音输入不是语音播报；人物图不是生图     |

## 2026-10-21 选型更新

优先试 **`qwen-audio-3.1-asr-flash-message`（百炼原生实时接口）**，Fun-ASR Realtime 作对照。阿里云当前选型页已将 Message 版列为输入法 / 短语音首选：实时中间结果、句末整理、即时热词、近几轮上下文，适合个性化输入。这里更新的是采购建议，APK 仍使用设备系统识别，没有偷偷切换线上模型。

Message 版可启用 `disfluency_removal_enabled` 过滤语气词并润色，支持保留方言，且可传即时热词；这些是官方接口参数。基础整理不等于已经学会用户文风，也不保证每次改口都正确理解。

Fun-ASR Realtime 同样是现代语音大模型，具有实时转写、热词、标点 / 数字规整和噪声鲁棒性；当前官方 `fun-asr-realtime` 接口也支持近期上下文，不能把它与 Message 简单分成“有上下文”与“没上下文”。北京原价 ¥0.00033/秒，即 ¥1.188/小时，每月 15 小时约 ¥17.82。它的定时长成本保留作计算器基线。

### 用户列出的模型如何比较

| 模型 / 服务 | 对输入法有意义的差别 | 当前可比较的单价 |
| --- | --- | --- |
| Qwen-Audio-3.1-ASR-Flash | 新一代短音频模型，支持上下文、方言及原生整理；ZenMux 这一普通 ID 不等于 Message 实时接口 | ZenMux 输入 US$0.12 / 百万 token，输出 US$0.41 / 百万 token |
| Qwen3-ASR-Flash | 较早一代，多语言、抗噪；适合整句录完后的识别对照 | ZenMux US$0.126 / 小时 |
| Doubao-Seed-ASR-2.0 | 中文 / 方言 / 专有名词、语义顺滑；ZenMux 列出的用途是录音文件转写 | ZenMux US$0.11772 / 小时 |
| GPT Transcribe | 文件与已提交语音轮次，支持上下文、关键词和多语言提示；连续低延迟识别另有 GPT Live Transcribe | ZenMux US$0.27 / 小时 |
| Grok Voice STT 1.0 | 词级时间戳、可选说话人分离、多声道；本项目尚无中文近场对照结果 | ZenMux 约 US$0.10 / 小时 |
| MiMo-V2.5-ASR | 开源，中英混说、方言、噪声和重叠语音是官方重点；开源模型不代表手机可直接离线流畅运行 | ZenMux US$0.72 / 小时 |
| Fun-ASR Realtime | 持续上传麦克风音频，边说边出字；支持热词、上下文、断句与噪声阈值 | 百炼北京 ¥1.188 / 小时 |
| Qwen-Audio-3.1-ASR-Flash-Message | 输入法 / 语音消息专用实时形态；句末整理、即时热词和上下文增强 | 百炼北京输入 ¥6 / 百万 token，输出 ¥4.5 / 百万 token |

按秒报价乘 3,600 换算；按 token 的模型不强套一个每小时价格，需要确认音频编码与一次真实 `usage`。价格不代表识别排名。

Message 北京区有 100 万 token 的试用额度，官方标注 90 天有效期；Fun-ASR 的 10 小时试用同样不是每月永久赠送。以账号实际资格和余量为准。

### 模型能力与网关接口分别核对

ZenMux 的 `POST /api/v1/audio/transcriptions` 是 JSON 提交音频 base64 或 URL，`stream: true` 流式返回文字；这不等于 WebSocket 连续上传麦克风。公开参数目前不能证明 Message 版的热词、上下文和句末整理能完整透传。停止录音后出结果可先用 ZenMux 对照；实时个性化输入优先验证百炼 Message 原生接口。

朋友所说“泛型 ASR”可能是 Fun-ASR，但没有确切模型 ID，不能认定。噪声鲁棒、过滤无效声音与只听本人仍是不同问题；旁人清晰说话不一定会被过滤。

用本人相同的 30–50 条安静、风扇、街边、旁人说话、中英混说、热词和口述改口录音，对比错字、漏字、首字 / 句末延迟、原意保留与修改量，再确定默认模型。下述公共样本仅证明接口连通，不能宣称绝对排名。

### 2026-10-21 独立接口验证

同一段阿里云公开示例音频，16kHz 单声道 PCM，约 3.835 秒。两个原生 WebSocket 请求均收到 `task-finished`，识别出 “hello world，这里是阿里巴巴语音实验室。”（大小写略有差别）。

| 模型 | 非空中间结果 | 最终用量 | 按北京标价估算 |
| --- | --- | --- | --- |
| Qwen Message | 10 次 | 输入 149、输出 8 token | ¥0.00093 |
| Fun-ASR Realtime | 5 次 | 4 秒 | ¥0.00132 |

Message 请求同时携带上下文、即时热词、近场 VAD 和去口头禅 / 润色参数，成功返回。此样本不证明这些参数对准确率的增益，也未验证噪声、本人文风或手机端延迟。第一次 Message 连接在上传音频前发生 TLS 断开，单次重试成功；没有忽略这次连接失败。

合计按标价 ¥0.00225，实际费用取决于免费抵扣与最终账单。本次仅测试上述短音频，没有自动循环请求。APK 仍未接入这两个云 ASR。

来源：[阿里云选型页](https://help.aliyun.com/zh/model-studio/asr-model)、[Message 客户端参数](https://help.aliyun.com/zh/model-studio/qwen-asr-message-client-events)、[百炼价格](https://help.aliyun.com/zh/model-studio/model-pricing)、[ZenMux 模型](https://zenmux.ai/models)、[ZenMux 转写接口](https://zenmux.ai/docs/api/openai/create-audio-transcriptions.html)、[OpenAI 转写工作流](https://developers.openai.com/api/docs/guides/transcription)、[MiMo 官方项目](https://github.com/XiaomiMiMo/MiMo-V2.5-ASR)。

## 转写与语句整理

ASR 已不只是逐字转写。Paraformer 官方接口支持自动标点、数字等书面化（ITN）、定制热词，以及可选的语气词过滤。它们帮助得到干净的转写，但不等于已经按个人文风重写整句话。

本产品将两步分开：ASR 识别与基础规整 → 可选文本模型结合适用文风整理。“按我的口吻整理”可关闭；ASR 热词 / 近期上下文主要辅助识别，不等于个人文风。比如把“明天……不对，后天再去”整理成“后天再去”，仍须验证原意保留，不把它冒充为 ASR 已保证的能力。用户可信修改反哺两者。

当前 APK 使用设备实际提供的系统 ASR，保留转写 / 整理的独立流程；专用云端 ASR 与真实文字模型账号尚未配置。供应商支持某能力不等于本项目已联调。

## 可以直接购买的价格

| 官方服务 / 模型                  | 核验单价                                        | 换算与注意                                                           |
| -------------------------------- | ----------------------------------------------- | -------------------------------------------------------------------- |
| 百炼北京 paraformer-realtime-v2  | ¥0.00024 / 秒                                   | ¥0.864 / 小时；价格页列每月 10 小时免费额度，资格以账户控制台为准    |
| 百炼北京 fun-asr-realtime        | ¥0.00033 / 秒                                   | ¥1.188 / 小时；10 小时试用，页面标注 90 天有效期，不写成永久每月赠送 |
| 百炼北京 fun-asr 文件识别        | ¥0.00022 / 秒                                   | ¥0.792 / 小时；文件识别与实时接口不要混淆                            |
| Groq whisper-large-v3-turbo      | US$0.04 / 小时                                  | 每请求至少计 10 秒，短句频繁调用会抬高实际每小时成本；跨境网络另测   |
| 百炼北京 qwen3.7-flash，输入≤32K | 输入 ¥0.2 / 百万 token，输出 ¥0.8 / 百万 token  | 思维链输出计费；本项目短上下文优先关闭额外思考                       |
| 百炼北京 qwen-flash，输入≤128K   | 输入 ¥0.15 / 百万 token，输出 ¥1.5 / 百万 token | 可作为分类/抽取成本对照，效果仍须评测                                |
| ML Kit 设备端中文 OCR            | 无逐次服务费用                                  | 占用 APK 包体、设备 CPU 和电量；不是云 OCR 的免费套餐                |

短上下文预算里，Qwen3.7 Flash 比 Qwen3.5 Flash 的输出标价更低；回复效果仍需用本人聊天对比。超过 32K 后按官方下一档计算，此处不外推。

## 日常预算示例

假设每天语音 30 分钟，每月 30 天，共 15 小时；军师每天 100 次，每次总输入 3,000、输出 600 token；每次语音整理输入 500、输出 200 token，每天 60 次；分类/抽取每天 100 次，每次输入 600、输出 100 token。都按 qwen3.7-flash、无缓存、无免费额度、无额外推理 token 估算：

- Fun-ASR 实时语音：15 × 1.188 = ¥17.82 / 月。
- 军师：9M 输入 × 0.2 + 1.8M 输出 × 0.8 = ¥3.24 / 月。
- 语音整理：0.9M 输入 × 0.2 + 0.36M 输出 × 0.8 = ¥0.468 / 月。
- 异步抽取：1.8M 输入 × 0.2 + 0.3M 输出 × 0.8 = ¥0.60 / 月。
- 合计约 ¥22.13 / 月；服务器、存储、流量、税费和失败重试不在此数中。

如果本次确有 Fun-ASR 10 小时有效试用，ASR 超额仅 5 小时，以上合计约 ¥10.25；不能把有限期试用作为长期毛利保证。切换为 Paraformer 则原价合计约 ¥17.27。价格计算器可调整使用量，均为估算，非实测账单。

## 去哪里购买，怎么选最划算

1. 国内中文使用优先开通阿里云百炼北京地域，取模型 API Key，按量付费或使用账户真实可见的免费额度。无需为输入法购买整套高价 Agent 订阅。
2. 已有 Google/Devin 自用订阅可通过自己的 CPA 做评测，不新增订阅就没有本项目新增固定成本；它仍受实际周/5h 配额限制。
3. 若有稳定跨境网络，Groq 官网的 Turbo 按标价更低，但短句最低计费和网络延迟可能抵消优势。先测，再替换。
4. 面向付费用户，使用官方可采购 API 或清晰约定的企业供应商；不把闲鱼学生/赠送权益、共享 Key、未知中转倍率当作单位经济依据。
5. 个人数据仍由键盘管理；模型供应商可替换，不把人物记忆锁在某个账号的云端聊天 Session。

### 非流式备选与自部署

ZenMux 的 Qwen3-ASR-Flash 作为录完一句再转写的便宜对照；中文实时体验优先验证上文 Qwen-Audio 3.1 Message。开源 Qwen3-ASR 0.6B / 1.7B 可自部署，不能直接当作云端 Flash 的同一服务，轻量服务器延迟与并发尚未实测。

### ZenMux 有 ASR，但要分清流式形态

官方已提供 `POST https://zenmux.ai/api/v1/audio/transcriptions`，文档示例模型为 `qwen/qwen3-asr-flash`，JSON 内提交音频 base64 或 URL。`stream: true` 流式返回文字，不等于 WebSocket 持续上传麦克风音频。可用来验证“录完一句再转写”，实时边说边出字优先测百炼 Message 原生接口。

该模型当前公开标价 US$0.000035 / 秒，即 US$0.126 / 小时、15 小时约 US$1.89。ZenMux 另列 `qwen/qwen-audio-3.1-asr-flash`，描述包含原生润色；它按 token 标价，不能把价格套用到按秒模型。是否适合个人口吻、能否保留原意仍需本人语音评测。

已确认公开接口与模型上架；未登录你的订阅账单做付费请求，不能承诺当前会员已包含 ASR，也不能把“平台支持”写成“本 APK 已接入”。实际试用前核对该模型在你的账号中扣订阅还是余额。

### 阿里云怎么开通

开通[百炼](https://bailian.console.aliyun.com/) → 选择华北 2（北京）→ 创建该地域 API Key → 在模型广场核对 `fun-asr-realtime`、试用余量与用完策略 → 首先用短音频测试。它按音频时长结算，直接按量使用，不必先买一个 ASR 月费会员。实时协议由服务适配层处理，不是把文本模型 ID 换成 ASR 就自动支持。

Fun-ASR 实时每天 30 分钟、每月 15 小时，原价 ¥17.82；当期确有足额 10 小时试用时，超出 5 小时为 ¥5.94。试用有期限。以上只算 ASR，不含整理与回复。

### 套餐要按同一产品比较

| 方案                        | 当前规则                                                                       | 自用取舍                                                                   |
| --------------------------- | ------------------------------------------------------------------------------ | -------------------------------------------------------------------------- |
| 百炼千问语音模型节省计划    | 20 元 9.8 折、100 元 9.6 折；这两档有效期 6 个月，包含北京 Paraformer 实时模型 | 20 元约抵 ¥20.41 原价，按 ¥0.864/h 约 23.62 小时；折扣很小，暂不必囤       |
| 百炼入门型 AI 通用节省计划  | 新客例：10 元抵 20 元、27 元抵 60 元；季包按月各发 20 元，当月未用完清零       | 有资格且预计能用完才考虑；不能写成 60 元一次到账，也不假定已有账号有资格   |
| 智能语音交互 NLS 实时资源包 | 30 小时 100 元，约 ¥3.33/h，资源包有效期 1 年                                  | 是另一个产品与接口，不作为百炼 Paraformer 的抵扣包；小额单价也未优于本基线 |

稳定大用量再根据每月账单比较。大档位的最高折扣不代表单人应买大量额度；旧 NLS 的试用、并发、AppKey / Token 也不要套到百炼 API Key 上。

## 官方来源

- [阿里云模型价格](https://help.aliyun.com/zh/model-studio/model-pricing)
- [百炼节省计划与资源包](https://help.aliyun.com/zh/model-studio/savings-plan-and-resource-package)
- [智能语音交互 NLS 计费](https://help.aliyun.com/zh/isi/product-overview/billing-10)
- [Qwen-ASR API 与上下文](https://help.aliyun.com/zh/model-studio/qwen-asr-api-reference/)
- [Qwen3-ASR 开源实现](https://github.com/QwenLM/Qwen3-ASR)
- [ZenMux 转写接口](https://zenmux.ai/docs/api/openai/create-audio-transcriptions.html)
- [ZenMux Qwen3 ASR Flash 标价](https://zenmux.ai/qwen/qwen3-asr-flash)
- [ZenMux Qwen-Audio 3.1 ASR Flash](https://zenmux.ai/qwen/qwen-audio-3.1-asr-flash)
- [阿里云 Fun-ASR](https://help.aliyun.com/zh/model-studio/fun-asr)
- [阿里云 Paraformer 实时识别参数](https://help.aliyun.com/zh/model-studio/paraformer-real-time-speech-recognition-java-sdk)
- [阿里云识别准确率与热词](https://help.aliyun.com/zh/model-studio/improve-asr-accuracy)
- [百炼控制台](https://bailian.console.aliyun.com/)
- [Groq Speech to Text](https://console.groq.com/docs/speech-to-text)
- [Groq 控制台](https://console.groq.com/)
- [Gemini API 定价](https://ai.google.dev/gemini-api/docs/pricing)
- [ML Kit 中文识别](https://developers.google.com/ml-kit/vision/text-recognition/v2/android)
- [ML Kit 设备端费用说明](https://developers.google.com/ml-kit/migration)

不存在脱离准确率、短句时长、免费资格、网络与采购地域的“全世界最便宜”结论。本 Spec 提供可复核候选和可改参数的预算。
