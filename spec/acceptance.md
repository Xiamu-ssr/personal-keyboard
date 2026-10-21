# 验收 · 0.2.0-alpha.1 · 2026-10-21

本次为原生界面与代码域重构。用户指定改用安卓虚拟机，未操作 OPPO。上版的真机通过记录不算作本版证据。

## 当前自动检查

- **24 项 JVM 核心测试通过**：真实 JNI 拼音、整句 / 部分选词、简拼召回、可改变排序的选择频率、个人快捷码移除；Unicode 差异、撤销、未知发送状态、过期任务、人物确认、回复返回与上下文上限。
- **2,014 组原生边界输入通过** AddressSanitizer / UndefinedBehaviorSanitizer 检查；重复字母与固定随机输入均未触发边界错误。
- debug APK、测试 APK 构建成功；Android lint 无错误。
- **6 项 Node 检查通过**：产品世界契约、成本换算和本地设备操作边界。

## Android 15 / API 35 虚拟机

**12 项设备测试全部通过**，由真实 Android instrumentation 操作原生界面：

- 点键输出“你好”“我想你”、`lll` 展开并选择“六六六”、退格与多行换行。
- 八项工具入口；语音面板真实权限错误、可返回，不伪造转写。
- 禁止个性学习的编辑器阻止军师；不清空草稿。
- 正常键盘 847px → 捕获条 275px → 系统截屏 → 本机中文 OCR → 修正 / 返回 / 取消。实际内容高度 284dp → 76dp；识字包含本机练习区文字。
- 原生管理页切换、资料修订与持久化、检索空结果、删除及撤销、人物记忆展示。
- SQLite 重开、版本冲突、分人物删除恢复、备份重新映射、非法备份回滚、历史保留，以及 OCR 后图片释放。

截图只有 App 练习区与带有“本机验收示例”的资料，无聊天记录、账号或模型密钥。画面来自实际 APK，不是 HTML 机壳或预写的模型回答。测试资料已移出，原始证据在本机忽略目录。

交付 APK 与虚拟机安装包 SHA-256 相同：`e225dcdaf55d06b1a2c6ac59f2ea1fd7081d8410acc4bbbff4b22fe53611792c`。

本轮独立虚拟机配置 1,536MB 客体内存、2 核。验收后正常关闭，核对本轮 qemu 进程结束及两个端口释放。真实手机和其他任务的模拟器未作更改。

## 尚未验证

- 本版 OPPO 与微信 / QQ / Messenger、其他 ROM 的实际兼容性。
- 云端专用 ASR、本人语音准确率与延迟、真实模型润色及回复质量。
- 自动差异分类 / 人物记忆抽取、自动翻页、说话人识别和豆豆同步。
- 英语自动纠错、多语言预测、双拼、滑行、手写；当前文字输入以基础中英文为范围。
- 浏览器视频投屏此前可构建，本轮通过 instrumentation 验收，没有重测其视频链路。

## 重现

```sh
npm run android:test
npm run android:sanitize
npm run android:build
cd android
./gradlew :app:assembleDebugAndroidTest
# ANDROID_SERIAL 必须指向本人明确选定的隔离设备。
adb -s "$ANDROID_SERIAL" install -r app/build/outputs/apk/debug/app-debug.apk
adb -s "$ANDROID_SERIAL" install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s "$ANDROID_SERIAL" shell am instrument -w app.doudou.ime.test/androidx.test.runner.AndroidJUnitRunner
```

运行前在选定设备记录并临时配置输入法与主动截屏。真机结束后恢复原设置；临时虚拟机关闭后核对进程与端口。测试包只操作自己的练习区和测试资料，不遍历其他应用的聊天。
