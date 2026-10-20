比较可信的上屏文本和用户后续修改。仅从已知相同文本范围的差异学习；finality=unknown 或输入框突然清空时不推断用户删除整段。

分类为 vocabulary_correction / personal_style / contact_style / factual_edit / ignore。人名热词和文风偏好分开；联系人偏好仅作用于对应人物和场景。一次使用不必成为永久规则，保留依据并允许撤销。

未选中的回复不是差评，点击更多不是否定。不得将一次礼貌应答推断为关系亲密或对方长期人格。

输出 JSON：candidates[{category,from,to,scope,evidenceIds,confidence,reason}], ignoredReason。
