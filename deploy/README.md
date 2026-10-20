# 静态 Spec 部署

云端只发布 `npm run spec:build` 生成的 `spec/index.html`。文档、样式、脚本和价格计算均已嵌入，不需要常驻 Node 服务。

- 入口：`/keyboard-spec/`，通过现有 HTTPS Nginx 提供。
- 独立目录：`/var/www/personal-keyboard-spec/releases/<version>/index.html`；`current` 原子切换到已校验版本。
- Nginx 只允许读取页面入口，其他子路径返回 404；不发布整个仓库、运行目录或设备接口。
- 云端会隐藏本机设备操作并显示原生验收状态；没有访问手机或启动模拟器的云端接口。
- 首次配置路由前保存原 Nginx 配置，`nginx -t` 通过后 reload。仅更新静态页面时保存旧版本指向并原子切换 `current`，无需修改配置或 reload。公网核对 HTTPS、页面 SHA-256 与手机宽度交互。
- 保留原版本以便回退；回退只切换此站点的 `current`。不要覆盖其他项目的 Nginx 配置。

本机真机验收与云端 Spec 部署是独立状态。域模型、关系高亮及网页交互示意在浏览器运行；把页面放上线，不代表 APK 的截屏、回复填入或模型已完成验收。
