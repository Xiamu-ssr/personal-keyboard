# 验收 · 0.1.0-alpha.1

本次以原生 App 替代 HTML 底稿与固定场景 APK。正式业务 / 架构 Spec 保留。以下为实际执行结果，未接入模型的能力单独列出。

## 自动检查

- 21 项 JVM 核心测试通过：真实 JNI 拼音解码、整句、部分选词、个人词移除与排序、Unicode 修改、撤销、未知发送状态、过期任务、人物确认、回复返回缓存及上下文上限。
- 原生边界检查通过：1,014 组重复字母与 1,000 组固定随机输入，经 AddressSanitizer / UndefinedBehaviorSanitizer 检查。
- Android debug APK、设备测试 APK 构建通过，Android lint 无错误。
- 产品世界和本地宿主 6 项 Node 测试通过。

## OPPO PHW110 真机

9 项设备测试通过：真实点键输入“你好”“我想你”、退格和多行换行；禁止个性学习输入框关闭军师；主动缩矮 → 系统截屏 → 本机中文 OCR → 打开识字修正 → 返回和取消，原草稿保持不变。

同时验证 SQLite 重开持久化、旧版本写入拒绝、分人物删除与恢复、备份导入重新映射、非法备份回滚、历史保留期限，以及本地中文 OCR 的图片释放。

测试截图仅含本机练习内容。真实系统截屏的识字结果包含练习区文字，不以预写回复充数。截图和原始设备日志保留在本机忽略目录；公开交付仅使用检查后的两张 App 截图。

测试结束恢复原默认输入法、已启用输入法列表和辅助功能设置，并核对与事前样本相等。保留新 App；移除测试包和临时 APK。未启动模拟器。

## 尚未验证 / 后续配置

- 付费 ASR 与真实文字模型账号、口音 / 热词准确率、润色和建议质量、模型自动分类与人物记忆抽取。
- 微信 / QQ / Messenger 等真实聊天内容下的兼容性；单台 OPPO 的练习区验收不能代表所有应用。
- 自动翻页、说话人识别、双拼、表情 / 手写 / 滑行输入，以及豆豆资料同步。
- 浏览器实时投屏组件曾完成编译；本轮设备验收使用 Android instrumentation 与真机截图，未把它说成浏览器视频投屏已验收。

前文当前由本人滚动，再主动补读；没有配置模型时生成入口会明确提示。日常词库与资料管理可独立使用。

## 重现

```sh
npm run android:test
npm run android:sanitize
cd android
./gradlew :app:assembleDebugAndroidTest
# ANDROID_SERIAL 由本人设置为明确选定的设备；不遍历全部设备。
adb -s "$ANDROID_SERIAL" install -r app/build/outputs/apk/debug/app-debug.apk
adb -s "$ANDROID_SERIAL" install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s "$ANDROID_SERIAL" shell am instrument -w app.doudou.ime.test/androidx.test.runner.AndroidJUnitRunner
```

设备测试要求本人明确选定的、已授权设备，并在运行前记录 / 临时配置输入法及主动截屏，结束后恢复。`NativeFlowTest` 只操作本 App 练习区；辅助功能重连仅作用于已启用的本 App 服务，保留其他服务。不要对不明设备自动授权或运行。
