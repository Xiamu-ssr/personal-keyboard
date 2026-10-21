# 模型与采购

核对日期：2026-10-21。默认 ASR 已选 **Qwen Audio 3.1 Message**，模型 ID 为 `qwen-audio-3.1-asr-flash-message`，通过百炼原生实时接口接入。

## 为什么选它

它针对输入法 / 语音消息，具备流式中间结果、即时热词、近期上下文和可选句末整理。按这组能力与产品需求的匹配程度，优先选 Message。Fun-ASR Realtime 也具备现代语音大模型、热词、上下文与抗噪能力，不能把两者简单分成“先进 LLM”与“传统 ASR”。

目前没有同一批本人语音、街边噪声与旁人干扰的完整评测，因此不宣称 Message 在所有场景更准确。“上下文”帮助识别正在说的词，不等于用户文风；开启个人改写时仍由后接文字模型处理。

## 首版只采购两类能力

| 能力 | 决定 | 处理位置 |
| --- | --- | --- |
| 实时 ASR | Qwen Message，百炼北京地域 | Cloud 代理原生 WebSocket；供应商密钥只在服务端 |
| 个人改写、军师、记忆抽取 | 一个文字模型共用，具体型号待联调与预算确定 | Cloud 组装对应提示词，限制输入 / 输出和成本 |
| 中文 OCR | ML Kit 设备端识别 | App，本地截图识字，无逐次云 OCR 费 |
| TTS / 生图 | 首版不采购 | 当前功能不需要 |

商业服务不把个人 Google / Devin / CPA 订阅当作稳定 API 权益；不让普通用户填写供应商地址和密钥。供应商、模型 ID 与路由在 `cloud/config/providers.json` 中配置；对外只暴露产品能力。当前 APK 尚未接入这套商业 Cloud，也未接入百炼 ASR。

## Message 与 Fun-ASR 的边界

| 项目 | Qwen Message | Fun-ASR Realtime |
| --- | --- | --- |
| 本项目角色 | 默认 ASR | 已完成对照接口验证；不作为并行生产路由 |
| 形态 | 持续上传音频，返回中间 / 最终结果，面向输入法短语音 | 持续上传音频，返回中间 / 最终结果 |
| 基础整理 | 可通过原生参数控制去口头禅 / 句末润色 | 标点、数字规整等按接口能力配置 |
| 北京原价基线 | 输入 ¥6 / 百万 token，输出 ¥4.5 / 百万 token | ¥0.00033 / 秒，即 ¥1.188 / 小时 |

Message 按 token 计费，不能直接用音频分钟数套一个固定单价。Fun 每天 30 分钟、每月 15 小时的语音成本基线为 ¥17.82；它只用于比较，不代表默认 Message 的月账单。试用额度有期限与账号资格，不能作为商业服务长期毛利依据。

ZenMux 的 `qwen/qwen-audio-3.1-asr-flash` 不是 Message 原生实时接口的同一 ID。`audio/transcriptions` 提交一段音频并流式返回文字，也不等同于麦克风连续上传；未确认的热词 / 上下文参数透传不能作为接入承诺。

## 已做的接口验证

2026-10-21，同一段阿里云公开示例音频，16kHz 单声道 PCM，约 3.835 秒。两模型均收到 `task-finished`，识别出“hello world，这里是阿里巴巴语音实验室。”。

| 模型 | 非空中间结果 | 最终用量 | 按北京标价估算 |
| --- | --- | --- | --- |
| Qwen Message | 10 次 | 输入 149、输出 8 token | ¥0.00093 |
| Fun-ASR Realtime | 5 次 | 4 秒 | ¥0.00132 |

Message 携带了上下文、热词、VAD 和去口头禅参数；这段样本不证明参数的准确率增益。第一次连接在上传音频前 TLS 断开，单次重试成功。合计标价估算 ¥0.00225，实际费用仍以最终账单为准。未追加循环压测、未开通新付费包。

## 怎么采购和控制成本

百炼北京地域 → 对应工作空间 → 限制模型权限的 API Key → 服务端密钥配置。已有本机受限测试配置不进入 Git 或 APK。生产环境应另建服务凭据，设置余额 / 用量告警、任务时长和并发限制。

先按量验证真实使用与供应商账单，再比较同一地域、同一模型可抵扣的资源包；不先囤套餐。手机号短信另计，服务器、存储、网络、支付费用、失败调用与记忆更新也应计入毛利。后台要同时记录用户权益单位（字符 / 次）与供应商成本单位（token / 秒），两者不混用。

文字模型的成本预算独立于用户套餐：限制 OCR 字数、上下文、每批记忆规模、输出长度与重试次数。套餐金额和付费额度尚未决定，配置保持草稿，不能售卖。

## 官方来源

- [Qwen Audio 3.1 Message](https://help.aliyun.com/zh/model-studio/qwen-audio-3-1-asr-flash-message)
- [Message 客户端参数](https://help.aliyun.com/zh/model-studio/qwen-asr-message-client-events)
- [Fun-ASR Realtime](https://help.aliyun.com/zh/model-studio/fun-asr-realtime)
- [模型价格](https://help.aliyun.com/zh/model-studio/model-pricing)
- [百炼资源包与节省计划](https://help.aliyun.com/zh/model-studio/savings-plan-and-resource-package)
- [ZenMux 转写接口](https://zenmux.ai/docs/api/openai/create-audio-transcriptions.html)
- [ML Kit 中文识别](https://developers.google.com/ml-kit/vision/text-recognition/v2/android)
