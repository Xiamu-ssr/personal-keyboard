# 截图与操作动画

公开 Spec 提供截图、实际 APK 录屏与下载；在线安卓试用已取消。页面不会启动或连接个人 Mac、手机或云端 Android。原 `/keyboard-spec/live/` 地址保持关闭。

## 现在看什么

- 打字与语音：15.5 秒，实际键帽拼音输入、候选上屏、工具格与语音面板。
- 军师读屏：8.25 秒，缩矮、截取本机练习页、本地 OCR、返回；原草稿仍在。
- 截图：首页、个人记忆、人物、设置，另有键盘和军师截图作为录屏封面。

录屏来自 Android 15，安装的就是公开发布的 `0.3.0-alpha.1` APK；只有本 App 练习区和测试资料，无真实账号或私聊。云端 ASR 和文字模型未配置，所以不展示伪造的语音转写或建议回复。截图与录屏都是静态媒体，不能当成在线操作。

视频为 H.264 / MP4，无音轨，支持网页播放；打开预览后静音循环，系统偏好减少动态效果时不自动播放，关闭窗口即暂停。`spec/media.json` 记录 APK 来源、尺寸、时长和文件摘要。

## APK 与发布

[下载 APK](./demo/doudou-keyboard.apk)，Android 11+，debug 自用预览签名。安装后在系统启用并选择豆豆输入法，再到 App 的练习区试用。

GitHub Pages 与 ECS 发布同一签名 APK、截图和录屏。Pages 工作流从同版本 Release 取文件并校验 `spec/release.json` 与 `spec/media.json`；不会重新生成另一套 APK 签名。

## 本地开发验收

```sh
npm ci
npm run android:build
npm run demo:setup
npm run spec:serve
```

打开 `http://127.0.0.1:8560`，在预览窗口选择已解锁并授权 ADB 的设备；只安装 / 投屏明确选定的设备，不自动启动模拟器。临时授权及停止恢复仅供本地验收。Node / scrcpy 服务只监听回环地址，不部署公网设备桥接。
