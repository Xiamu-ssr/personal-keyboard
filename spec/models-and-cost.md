# 模型、采购与成本

核验：2026-10-20。单价依地域、上下文区间及供应商更新；下表是选型基线，不是自动购买授权。实际调用前在控制台确认。

## 推荐组合

| 能力               | 自用首选                                               | 可采购替代                                 | 原因                                     |
| ------------------ | ------------------------------------------------------ | ------------------------------------------ | ---------------------------------------- |
| 中文流式语音       | 阿里云 paraformer-realtime-v2 或 Fun-ASR Realtime 对比 | Groq Whisper Large V3 Turbo 作为非流式对照 | 中文、热词、短句延迟须用本人语音评测     |
| 润色、三种回复     | 现有 CPA 中可用的 Gemini Flash                         | 阿里云 qwen3.7-flash                       | 先利用已有订阅；对外收费使用稳定官方 API |
| 差异分类、记忆抽取 | 小型文本模型，合并/批处理                              | qwen-flash / qwen3.7-flash                 | 短结构化输出，通常不需要 Opus            |
| 困难对话分析       | 用户主动选择 Opus，经 CPA 优先 Devin                   | 官方 Claude API                            | 按需，日常输入不强制昂贵推理             |
| OCR                | ML Kit 中文设备端模型，随 APK 打包                     | PaddleOCR 本地方案待设备评估               | 无逐次 API 费用，不要求 Play 商店安装    |
| TTS / 生图         | 首版不需要                                             | 后续按具体功能采购                         | 语音输入不是语音播报；人物图不是生图     |

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

- Paraformer 实时语音：15 × 0.864 = ¥12.96 / 月。
- 军师：9M 输入 × 0.2 + 1.8M 输出 × 0.8 = ¥3.24 / 月。
- 语音整理：0.9M 输入 × 0.2 + 0.36M 输出 × 0.8 = ¥0.468 / 月。
- 异步抽取：1.8M 输入 × 0.2 + 0.3M 输出 × 0.8 = ¥0.60 / 月。
- 合计约 ¥17.27 / 月；服务器、存储、流量、税费和失败重试不在此数中。

如果本人当月确有 Paraformer 10 小时免费额度，ASR 超额仅 5 小时，以上合计约 ¥8.63；不能把这种优惠作为长期毛利保证。价格计算器可调整使用量，均为估算，非实测账单。

## 去哪里购买，怎么选最划算

1. 国内中文使用优先开通阿里云百炼北京地域，取模型 API Key，按量付费或使用账户真实可见的免费额度。无需为输入法购买整套高价 Agent 订阅。
2. 已有 Google/Devin 自用订阅可通过自己的 CPA 做评测，不新增订阅就没有本项目新增固定成本；它仍受实际周/5h 配额限制。
3. 若有稳定跨境网络，Groq 官网的 Turbo 按标价更低，但短句最低计费和网络延迟可能抵消优势。先测，再替换。
4. 面向付费用户，使用官方可采购 API 或清晰约定的企业供应商；不把闲鱼学生/赠送权益、共享 Key、未知中转倍率当作单位经济依据。
5. 个人数据仍由键盘管理；模型供应商可替换，不把人物记忆锁在某个账号的云端聊天 Session。

## 官方来源

- [阿里云模型价格](https://help.aliyun.com/zh/model-studio/model-pricing)
- [阿里云 Fun-ASR](https://help.aliyun.com/zh/model-studio/fun-asr)
- [阿里云 Paraformer 实时识别](https://help.aliyun.com/zh/model-studio/paraformer-realtime-api)
- [百炼控制台](https://bailian.console.aliyun.com/)
- [Groq Speech to Text](https://console.groq.com/docs/speech-to-text)
- [Groq 控制台](https://console.groq.com/)
- [Gemini API 定价](https://ai.google.dev/gemini-api/docs/pricing)
- [ML Kit 中文识别](https://developers.google.com/ml-kit/vision/text-recognition/v2/android)
- [ML Kit 设备端费用说明](https://developers.google.com/ml-kit/migration)
- [Anthropic 定价](https://platform.claude.com/docs/en/about-claude/pricing)

不存在脱离准确率、短句时长、免费资格、网络与采购地域的“全世界最便宜”结论。本 Spec 提供可复核候选和可改参数的预算。
