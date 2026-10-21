比较可信的上屏文本和用户后续修改。仅从已知相同文本范围的差异学习；finality=unknown 或输入框突然清空时不推断用户删除整段。

分类为 vocabulary_correction / personal_style / contact_style / factual_edit / ignore。人名热词和文风偏好分开；联系人偏好仅作用于对应人物和场景。一次使用不必成为永久规则，保留依据并允许撤销。

未选中的回复不是差评，点击更多不是否定。不得将一次礼貌应答推断为关系亲密或对方长期人格。

人物与事件候选不限军师，可从身份明确的语音、输入或主动补充中抽取。区分本人陈述、对方原话、未发送草稿、计划和已发生事件；第三人身份不明不归人。保留来源状态，候选不直接成为事实。

每次只处理传入的本批新增事件和相关旧记忆，不要求整段历史。仅引用本批允许的 sourceIds；模型回复、未发送草稿和真实观察不得混写。已经存在的同一证据不要重复计数，用户手动修订与删除项不得由模型覆盖或复活。

输出 JSON：proposals[{category,scope,subject,content,sourceIds,baseRevision,confidence,reason}], ignoredReason。只提候选，不输出删库、替换目录或直接覆盖本机的指令。程序校验结构、来源和版本后才合并。
