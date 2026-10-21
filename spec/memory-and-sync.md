# 历史、纠错、记忆与同步

目标契约；已发布 0.4 尚未完整实现三版本历史、证据关联和自动补丁，见 [当前实现](implementation.md)。本机是主本，Cloud 执行固定短任务，不开长期 Agent / 通用 harness。

## 数据分层

**历史保存“发生了什么”，记忆保存“以后该参考什么”。两者都在本机，整理不等于删除历史。**

- `UtteranceRecord`：recordId、来源、rawAsrText、可选 rewrittenText、insertedText、lastObservedText、editorSession、范围、修订、观察终态、时间。三个版本不覆盖彼此。军师另留本次图文模型读出的文字上下文、人物归属 / 不确定性、意图与三条建议，图片随任务销毁。
- `EditEvidence`：evidenceId、recordId、baseRevision、observedRevision、before / after、多段 diff、范围归属与捕获状态。只看本次上屏范围；不是所有击键记录，也不是服务器发送回执。
- `Memory`：id、类别（热词 / 文风 / 人物 / 事件）、subject、内容、有效期、sourceRefs、revision、manual / learned、撤销修订。
- `LearningReceipt`：batchId、pipelineVersion、cutoff、sourceSpans、appliedPatchId、处理状态。水位不是简单时间戳，来源修订改变后作为新证据处理。

输入框清空前只要已观察到有效改稿，就保留；清空可能是发送 / 清除 / 切换，不再把空串与原文比较。不能观察到的最后修改明确缺失，不猜测。差异计算和学习准入见 [本轮决定](decisions.md)。

## 每约五小时如何整理

1. App 冻结本次增量集合和截止修订，只取未处理来源；同时检索本批相关旧热词、文风和对应人物，绝不整库上传。
2. 按供应商 tokenizer 的实际输入 token 组批。初始每批总输入预算 6K token（提示词、旧记忆与证据都算），另留 2K 输出预算；预算不足就减少事件。单条长历史切成带 recordId / revision / offset 的片段，证据边界不丢。tokenizer 与预算属于模型适配层，不以“字符数约等于 token”保证不超限。
3. `batchId = hash(account, sourceSpans, revisions, pipelineVersion)`；Cloud 的 Job 与每批唯一键持久化。超长增量连续多批排队，并限制每轮总预算；失败只重试原批，后台被杀可继续。
4. Cloud 组装学习提示词：来源白名单 + 增量片段 + 相关旧记忆 + 分类规则。文字模型只返回 `candidates[{category,subject,operation,content,sourceRefs,baseRevision}]`；每条必须引用本批具体片段，不许自由补写人物信息。
5. 固定合并器按类别和明确身份去重。代码核对 sourceRefs、来源修订、字段大小、人物范围、有效期、手动保护与冲突；语义不确定项作为候选，不会靠模型的 confidence 强行通过。
6. Cloud 返回 `MemoryPatch{patchId,batchId,pipelineVersion,baseMemoryRevision,operations,processedSourceSpans}`。包含满足规则的热词 / 文风 / 事实更新，以及需要本人确认的候选；返回的是受限操作，不是目录或整库快照。
7. App 重新核对本地来源仍存在、未被手改 / 删除，记忆 baseRevision 一致。事务内提交可撤销补丁与 receipt，才推进处理水位；冲突保留候选或基于当前修订重算，不能覆盖手动数据。网络重试与再次下载同一 patchId 不重复强化来源。
8. 下一次录音选取适用热词和少量近期文字，供 ASR 理解；开启个人改写才把文风交给文字模型。军师按当前人物取关系记忆。跨设备同步 source UUID、处理 receipt 和删除修订，相同来源不算两次独立证据。

文字结果和差异先留本地；用户允许记忆分析时，才上传必要增量。免费用户没有历史托管权益，但可执行允许的模型分析。付费备份和模型分析分别授权，备份开启不意味着自动解密整库喂给模型。

## 冲突、删除和存储

删除来源后，下一次整理 / 同步使依赖该来源的自动记忆重新核验或撤销，不能永远留在派生资料里。历史保存期限由用户设置，不能为了“节约上下文”自动删源记录；到期删除保留必要墓碑，停止继续使用来源。

付费云同步不暴露坚果云 / 七牛等技术配置。App 上传设备加密的资料包，Cloud 只按账号、权益、容量和清单版本存取。全局 UUID 和向量时钟区分后续修订与并发修改；并发冲突保留，删除与手改优先，绝不整库覆盖。

密钥由设备 Keystore 与 Cloud 主密钥封装；换机恢复需要近期短信验证，这是账号可恢复的加密备份，**不是端到端加密**。截图不进备份。当前 0.4 Cloud 的任务恢复文字缓存最长保留 24 小时，付费套餐和到期策略仍未开放。
