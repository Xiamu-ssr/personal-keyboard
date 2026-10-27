---
name: "豆豆输入法"
description: "沿用豆包输入习惯的原生 Android 界面：灰阶键盘、蓝灰语音底、青蓝声纹与克制的 App 点色。"
colors:
  app-accent: "#a9c4ff"
  app-tint: "#30394b"
  audio-start: "#14beca"
  audio-end: "#4c80ff"
  voice-backdrop-center: "#2a3843"
  slot-caring: "#e6b5a6"
  slot-direct: "#a7bce3"
  slot-playful: "#e3c77d"
  slot-considered: "#baaada"
  slot-witty: "#99c5d1"
  brand-background: "#3e6be6"
  red: "#ffb4ab"
  withdrawal-red: "#ff5a67"
  bg: "#222222"
  panel: "#2c2c2c"
  key: "#606060"
  ink: "#f4f4f4"
  sub: "#aaaaaa"
  accent: "#f1f0ee"
  surface: "#333333"
  selected: "#464646"
  keyboard: "#222222"
  special-key: "#333333"
  hero: "#383838"
  line: "#3a3a3a"
  ripple-white: "#ffffff24"
skins:
  default: dark
  selection: device-local
  light:
    bg: "#f6f7fb"
    panel: "#ffffff"
    key: "#ffffff"
    ink: "#22262e"
    sub: "#606773"
    accent: "#30343c"
    app-accent: "#355fbc"
    app-tint: "#e6edff"
    audio-start: "#007b8b"
    audio-end: "#3866d6"
    surface: "#e9ebf1"
    selected: "#d8dfec"
    keyboard: "#e3e5eb"
    special-key: "#cbd0da"
    hero: "#ebeff8"
    line: "#d2d7e1"
    red: "#b3261e"
    withdrawal-red: "#b32635"
typography:
  headline:
    fontFamily: "sans"
    fontSize: "28sp"
    fontWeight: 700
    fontFeature: "kern"
  title:
    fontFamily: "sans"
    fontSize: "22sp"
    fontWeight: 700
    fontFeature: "kern"
  title-palette:
    fontFamily: "sans"
    fontSize: "21sp"
    fontWeight: 700
    fontFeature: "kern"
  title-account:
    fontFamily: "sans"
    fontSize: "20sp"
    fontWeight: 700
    fontFeature: "kern"
  title-panel:
    fontFamily: "sans"
    fontSize: "18sp"
    fontWeight: 700
    fontFeature: "kern"
  body-reply:
    fontFamily: "sans"
    fontSize: "17sp"
    fontFeature: "kern"
  field:
    fontFamily: "sans"
    fontSize: "16sp"
  body:
    fontFamily: "sans"
    fontSize: "15sp"
    fontFeature: "kern"
  label:
    fontFamily: "sans"
    fontSize: "14sp"
    fontFeature: "kern"
  caption:
    fontFamily: "sans"
    fontSize: "13sp"
    fontFeature: "kern"
  detail:
    fontFamily: "sans"
    fontSize: "12sp"
    fontFeature: "kern"
  nav-label:
    fontFamily: "sans"
    fontSize: "11sp"
    fontFeature: "kern"
  key-letter:
    fontFamily: "sans"
    fontSize: "22sp"
    fontFeature: "kern"
  quota:
    fontFamily: "sans"
    fontSize: "26sp"
    fontWeight: 700
    fontFeature: "kern"
rounded:
  key: "7dp"
  control: "12dp"
  action: "14dp"
  card: "18dp"
  nav: "20dp"
  practice: "22dp"
  icon: "24dp"
spacing:
  "4": "4dp"
  "5": "5dp"
  "6": "6dp"
  "8": "8dp"
  "9": "9dp"
  "10": "10dp"
  "12": "12dp"
  "14": "14dp"
  "16": "16dp"
  "18": "18dp"
  "20": "20dp"
  key-x: "2.5dp"
  key-y: "3.5dp"
components:
  button-neutral:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.ink}"
    typography: "{typography.body}"
    rounded: "{rounded.control}"
    padding: "8dp 14dp"
  button-primary:
    backgroundColor: "{colors.accent}"
    textColor: "{colors.bg}"
    typography: "{typography.body}"
    rounded: "{rounded.action}"
    padding: "8dp 14dp"
  button-practice:
    backgroundColor: "{colors.app-tint}"
    textColor: "{colors.app-accent}"
    typography: "{typography.body}"
    rounded: "{rounded.action}"
    padding: "8dp 14dp"
    height: "50dp"
  field:
    backgroundColor: "{colors.panel}"
    textColor: "{colors.ink}"
    typography: "{typography.field}"
    rounded: "{rounded.control}"
    padding: "12dp 14dp"
  card:
    backgroundColor: "{colors.panel}"
    rounded: "{rounded.card}"
    padding: "16dp"
  nav-pill-selected:
    backgroundColor: "{colors.selected}"
    textColor: "{colors.accent}"
    rounded: "{rounded.nav}"
    width: "60dp"
    height: "32dp"
  chip-palette:
    backgroundColor: "{colors.panel}"
    textColor: "{colors.ink}"
    typography: "{typography.body-reply}"
    rounded: "{rounded.card}"
    padding: "10dp 14dp"
  key-letter:
    backgroundColor: "{colors.key}"
    textColor: "{colors.ink}"
    typography: "{typography.key-letter}"
    rounded: "{rounded.key}"
  speech-waveform:
    width: "34%"
    height: "10%"
  recording-waveform:
    width: "64dp"
    height: "36dp"
---

# Design System: 豆豆输入法

0.9.3：输入键位与灰阶不变。翻译采用双列语言胶囊，选中用既有蓝色；点选后直接替换输入框，底部固定「原文」还原。原文/译文不再重复显示成编辑表单，录音来源在工具内预览。录音先待机，显式开始；切栏使用3dp呼吸圆点，系统关闭动画时保持静态。四栏维持48dp触区，仅收窄选中项留白。六槽默认克制暧昧、Mean、接梗、共情、干练、边界。

0.9.6：延续灰阶键盘与大写字母键帽；普通回车、返回与删除不使用亮白主按钮。字母/数字/符号第三行共享两侧几何，删除固定在右；九宫格单独采用右上删除、右下高回车。四种页面平级，数字页左侧「符号」、符号页左侧「123」互切，底部箭头及顶栏×返回字母。分类放符号页底部，超长分类保留前后翻页；选符号自动回字母。表情与展开候选的返回箭头独立于分类滚动。

顶栏固定48dp：上18dp显示13sp次要色拼音，下30dp显示20sp候选，候选触达仍覆盖整条。字母键和空格的绝对位置固定。选词后的联想复用横向候选条与展开网格，左侧菜单保持可达，×恢复工作区；续词、标点、语义emoji并列，不新加说明或设置。保持既有亮暗皮肤。

人物使用50dp起的紧凑资料行，共享基础字段容器；自由记忆另列KV。评论练习使用帖子、作者标识和评论层级，点评论定位回复。积分示例共用「约需 Credit」表头，15sp单行标签与右对齐金额，48dp触达；金额保留一位，非零不足0.1显示「<0.1」。点行看精确费用、Token假设与实际费率，真实消费另列。

## Overview

**Creative North Star: "熟悉的灰阶键盘，克制的声音点色"**

深灰底、逐级提亮的石墨面、正文白与暖白动作构成共同语言。豆包输入法的熟悉键位与语音手势是已确认的参照；品牌通过精确的布局、清楚的动作和声音反馈出现。App 的浅蓝只标记练习入口、选项与个人资料中的重点；键盘保持上一版灰阶。

青到蓝的线性渐变用于真实声纹；口述面板保留既有蓝灰径向底色。两者分别是声音读数和原有底材，不把声纹配色扩展成通用按钮或卡片渐变。字体服从 Android 系统的 `sans` 与字号设置，卡片主要靠面色、间隔与圆角分层。

**Key Characteristics:**

- 灰阶键盘与暖白主动作，保留既有蓝灰语音底色。
- App 浅蓝点缀与六格表达身份色，各自限定用途。
- 系统 sans、原生涟漪、圆角容器和按实际组件取值的密度。
- 内容与明确动作优先，只记录已实现的组件。

这是从代码提取的记录，不是新方向或功能验收。范围为原生 Android Views 的键盘、Home、My 及其共用组件；无已选 mockcomp，方向 seed 为 `98724ec0eaa78a89`，模式仍归表面契约所有。记录依据为 [PRODUCT.md](PRODUCT.md)、[方向契约](.impeccable/surfaces/a-app-doudou-ime-app-keyboardservice-java-ac7b9973.md)、[reviewer verdict](.runtime/v08/finish-verdict.md) 与真实源码。

0.9 增量已另行经过原生验收：标准字号与 360dp / fontScale 1.3 的调色盘保存、重开、名称保护；明暗切换、练习草稿保留；亮色键盘与语音画面。当前原图与 APK 摘要见 `spec/release.json`，验收范围见 `spec/verification.json`；独立复核见 `.runtime/v09/final-review.md`。旧 OPPO 已升级，但本轮保持锁屏，不声称完成其交互验收。下文旧版提取记录保留其时间范围，不替代本次证据。

0.8.0 视觉参考 APK 的 SHA-256：`1314622f519c34f0c0c03f5465a05b1399c63ce5e6b2b27c305f1204fe61872e`。0.8.1 仅修复输入视图生命周期；当前 APK 摘要见 [artifact.json](spec/demo/artifact.json)。[capture manifest](.impeccable/review/capture-manifest.json) 记录标准约 411dp / fontScale 1.0 和 360dp / fontScale 1.3；本次只读核看其中 Home、typing、voice、My 与大字 Home。管理页样本含临时本地 UI fixture，不能证明真实学习数据；不重做设备验收。verdict 中唯一未关闭的 F1 属模型语义，不纳入视觉 token。

frontmatter 是实际 primitive 与组件引用的规范层；名称主要沿用 `Ui` 常量的小写形式，源码尚未命名的圆角、间距与字号用描述性键归档，并未回写成代码 token。尺寸保留 Android 的 dp/sp；百分数只在相应原生父容器内成立。sidecar 使用 schemaVersion 2，提供实际 elevation、motion、颜色元数据及十个独立组件样本，不另建重复的 primitive 数组。样本 CSS 的 px 仅按 density 1 / fontScale 1 表达 dp/sp；系统字形、原生涟漪和阴影仍以 Android 实现为准。无彩色八阶色板的源码证据，因此不合成新色阶；sidecar 的灰阶 strip 只引用现有颜色。

## Colors

灰阶承担结构与交互，App 浅蓝承担小面积强调，声纹和表达槽位保留各自的局部颜色。

0.9 起用 `Skin` 选择亮色或暗色，`Ui` 的颜色角色按当前皮肤取值。默认暗色维持原有配色；亮色值见 frontmatter `skins.light`。原生 Activity、输入法窗口、弹窗与系统栏使用同一选择。皮肤在「我的」直接切换，不跟账户或 Cloud 同步。换肤重建 Activity 时保留练习草稿、光标选择与本轮本机消息；录音服务独立存活。

### Primary

- **App 浅蓝**（`colors.app-accent`）：练习文字、文风场景、人物图中的「我」、轻振开关拇指与版本文字；也是 natural 表达的身份色。
- **App 蓝灰容器**（`colors.app-tint`）：练习按钮与自有练习聊天中的本人气泡。

### Secondary

- **声纹青 / 声纹蓝**（`colors.audio-start` / `colors.audio-end`）：两个麦克风读数组件共用的水平线性渐变端点。
- **语音底材**（`colors.voice-backdrop-center` → `colors.keyboard`）：仅复述 `DictationSurface.Backdrop` 的既有径向底色，中心在宽度 50% / 高度 60%，半径为较长边的 65%。

### Tertiary

- **六格身份色**：按槽位顺序取 `PaletteStore.COLORS`，Home 用圆点表达身份。名称和表达属性可变，颜色不代表情绪强弱；不把这组色铺满键盘。
- **品牌图形底蓝**（`colors.brand-background`）：只记录账户头像已有品牌资源的底色，不升级为通用交互色。
- **温和风险色 / 撤回红**（`colors.red` / `colors.withdrawal-red`）：前者来自共用删除等风险文字，后者仅用于语音撤回提示与选中撤回文字；不是另一套品牌强调色。

### Neutral

- **深灰底**（`colors.bg` / `colors.keyboard`）：App 与键盘各有源码角色，数值相同但不合并其语义。
- **石墨容器**（`colors.panel` / `colors.surface` / `colors.hero`）：普通卡片、控件面、练习与工具较亮面；`colors.special-key` 和 surface 同值，单独保留键盘角色。
- **按键灰与选中灰**（`colors.key` / `colors.selected`）：字母及空格键、导航选中胶囊等。
- **正文白、辅助灰、暖白动作**（`colors.ink` / `colors.sub` / `colors.accent`）：分别承担主文字、次要信息、主动作与少量标签。
- **分隔线与涟漪白**（`colors.line` / `colors.ripple-white`）：原生分隔与交互反馈；后者由 Android ARGB 转为 CSS RGBA 顺序，alpha 未改。

**The Grayscale Keyboard Rule.** 普通键盘与键盘内的录音、帮我回动作保持灰阶；既有蓝灰语音底色和真实声纹是已授权的局部例外。

**The Scoped Accent Rule.** App 浅蓝、表达身份色与声纹端点按上述用途使用；不从局部颜色推导全屏品牌底色或新增渐变组件。

## Typography

**Body / UI Font:** Android 主题声明的 `sans`。没有项目自带的标题字体、中文字体文件或独立等宽体系；具体中西文字形由系统回退决定，不声称固定为某一字体。原生任务界面的标题不是另设的装饰性 display 字体。

### Hierarchy

| frontmatter role | 实际用途 | 源码依据 |
| --- | --- | --- |
| `headline` | App 页标题，明确 BOLD | `MainActivity.java:158–160` |
| `title` / `title-palette` | Home 练习标题 / 调色盘标题，明确 BOLD | `HomeScreen.java:25–36` |
| `title-account` / `quota` | 账户名称 / 额度数值，明确 BOLD；额度可自动缩到 18sp | `AccountSummary.java:27–28,62–66` |
| `title-panel` | 帮我回面板标题，明确 BOLD | `AdvisorController.java:277–278` |
| `body-reply` | 回复、录音正文、表达槽位名称 | `AdvisorController.java:354`；`RecordingPanel.java:73`；`HomeScreen.java:58` |
| `field` / `body` | 编辑框 / 共用按钮、设置行与正文 | `Ui.java:70–100,186` |
| `label` / `caption` / `detail` | 工具与场景标签 / 状态 / 次要资料 | `KeyboardMenu.java:82`；`HomeScreen.java:95`；`RecordingPanel.java:81`；`Ui.java:189` |
| `nav-label` / `key-letter` | 底部导航文字 / 单字母键 | `Ui.java:222`；`KeyboardView.java:253` |

`Ui.text` 启用 kern 并去掉额外 font padding；`Ui.edit` 不经过这段 helper，不虚构相同设置。未明确设置的字重、行高与字距保持平台默认，不补写 400、固定 line-height 或新比例。已观察到的行距是乘数 1 加额外 dp：文风预览和军师正文加 3dp，录音正文加 5dp；这些是组件参数，不是全局行高。字母键、特殊键和多字键分别按源码选用 22sp、16sp 与 14sp，并非统一标题字号。

**The Native Metrics Rule.** 普通文字保持 sp 与主题 sans，显式粗体仅记录代码中的 BOLD；不把 Canvas 的 dp 文字或未设置的默认值编成字体规范。

## Layout

App 外壳为纵向页面：页头左右 20dp、上下 10dp，滚动内容 padding 为上 4dp / 右 20dp / 下 10dp / 左 20dp；`Screen.add` 在区块后加 14dp。共用卡片 padding 引用 frontmatter；Home 双预览将其覆写为 12dp，账户摘要为 20dp。这是已有的混合间距集合，不推导统一的 4dp 或 8dp 基础网格。

Home 的练习动作按等权重并排，间隔 10dp。表达槽位是两行三列，列与行间隔 8dp；每行高度为 `72dp × max(1, fontScale)`，标签至多两行。文风与人物预览等宽、间隔 8dp，人物图预览高 120dp。这里记录实际组件构成，首屏策略继续由表面契约管理。

键盘名义内容高度是 `KeyboardView.HEIGHT_DP`（286dp），工具与录音沿用此高度；48dp 顶栏下是四个等权重键行，宽度以键权重分配，不记录固定列宽。每键左右 margin 引用 `spacing.key-x`，上下引用 `spacing.key-y`。最终可见高度还受根容器 padding、底部系统 inset 与模式影响，不能把名义高度当每张截图中键区的像素高度。帮我回结果面板名义高度为 340dp。

`Ui.content` 合并 system bars 与 IME inset；键盘在服务层单独处理底部 inset。没有按窗口宽度切换导航 rail、平板列数或网页媒体查询的实现，因此 sidecar 的 breakpoints 留空；manifest 的两种截图宽度只是测试条件，不是代码断点。

**The Measured Gutter Rule.** 共用布局优先复用源码中的间隔；方向契约的 8dp 沟槽不是所有容器的实测统一值，帮我回 sheet 的 6dp inset 作为差异保留。

## Elevation & Depth

App 共用卡片、字段和按钮主要通过灰阶面色与间隔建立层次，未显式设置自定义投影。键盘文字键、图标键与空格键明确使用平台 elevation（0.8dp）；这是原生 Z 值，不换算成自创 CSS box-shadow。系统对话框、菜单及 ripple 的绘制服从当前 Android 主题，不在此臆测阴影参数。

sidecar 的 `extensions.shadows` 记录该原生 elevation，`extensions.motion` 记录口述进入淡入（110ms）和弧形动作选中缩放（90ms）。前者受系统动画开关保护；后者的当前实现未显式检查该开关，不能据此宣称全部动作适配了 Remove animations。没有源码定义的统一 easing。

**The Tonal Surface Rule.** 卡片依靠已实现的面色与间隔分层；键盘只保留源码明确设置的原生轻 elevation，不推广新的阴影配方。

## Shapes

圆角按角色取值：键帽较紧，字段和普通按钮适中，卡片与导航更圆，练习容器单独放宽。frontmatter 的 `rounded` 为已使用值，不做统一化。纯色背景来自 `GradientDrawable`，不等于渐变；只有指定 shader 才形成渐变。

共用分隔线高 0.5dp，颜色引用 `colors.line`，会经过 `Ui.dp` 取整。帮我回入口胶囊视觉高 34dp、圆角 17dp、灰色 1dp 描边，外部点击框为 72×48dp；录音入口视觉高 40dp。两者的视觉高度和点击布局分别记录，不能只看截图推断命中区。

图标以现有 Android vector drawable 与 `Ui.Glyph` 的 Canvas 几何为准；后者虽名为 Glyph，实际不是字符图标字体。共用 Canvas 图标使用 24dp 坐标系、1.8 单位圆头圆角描边；品牌资源仍是现有单一对话与声音图形。正文中残存的字符箭头另列为未规范化项。

## Components

### Buttons

克制的纯色面配原生反馈。`button-neutral` 对应 `Ui.button`；`button-primary` 对应 `Ui.primary` 的暖白面、深灰字；`button-practice` 对应 Home 浅蓝字与蓝灰面。普通按钮的代码是 **minHeight 44dp**，不是固定 44dp，也不是所有按钮均已达到 48dp；录音底栏、工具栏等实际调用可分配 48dp。不为原生触摸界面发明 hover 色或新 focus ring；sidecar 的 hover 保持原色，focus / active 只用既有 ripple 白模拟反馈。

### Chips

`chip-palette` 是已实现的可编辑表达槽位，不是新增通用筛选标签。点色块为 14×14dp、圆角 8dp，点到文字间隔 8dp，默认背景为 panel，名称使用 `body-reply`。槽位顺序与颜色映射来自 `PaletteStore`，不是情绪强弱或心理诊断。

每格以编辑图标表明可修改，打开独立「编辑表达」页：有标签的名称字段、参考搭配和九项属性的当前值。按项展开，只有「表达手法 · 可组合」默认展开。可选按钮至少 48dp 高，普通宽度四列，窄屏或大字体两列。「不加手法」与其他手法互斥；未指定项随本人文风，顶部「全部随本人」重置全部属性。参考搭配只改属性，保留用户名称。契约来自 `cloud/contracts/palette.json`，Android 与 Cloud 共用；名称只作标签。保存期间保留编辑内容，失败不关闭页面；未修改时保存禁用并变淡。

### Cards / Containers

`card` 对应 `Ui.card`；Home 练习容器改用 hero 与 practice 圆角，双预览与账户卡只覆写已注明的 padding。My 用账户摘要、轻振与保留期、权限、关于等实际分组；设置行的标题使用 body，辅助文字用 detail，行最小高 58dp，行内垂直 padding 为 10dp。

### Inputs / Fields

`field` 对应 `Ui.edit`：panel 面、ink 正文、sub hint、control 圆角，minHeight 52dp；纵向 padding 大于按钮。没有另外实现 focus 描边、错误描边或 disabled token，不合成这些变体。密码选择、光标与选择区域遵循 Android 原生 EditText 及主题。

### Navigation

App 底部四个入口等权重，单个外部区域高 58dp，容器左右 12dp / 上下 8dp；`nav-pill-selected` 仅描述其中的 60×32dp 选中胶囊。胶囊内为 24dp 图标，标签另在其下方 5dp；选中图标用 accent、标签用 ink，未选中均为 sub。导航选中状态没有改为整块蓝色。

### Keyboard and voice meters

字母、空格为 key 面，功能键为 special-key 面，回车为 ink 面配 bg 图形。帮我回与录音入口保持灰阶；面板中的加载、取消与集合标签沿用真实组件，不能用六格身份色改造整片键盘。

`speech-waveform` 位于口述父容器的左 33% / 上 13%，尺寸由 frontmatter 指定。20 根圆头柱按容器等分，柱宽取 3dp 与步长一半的较小者，静音基线 4dp；RMS 归一化后以 0.24 系数趋近测量值，仅在听取、可见且系统允许动画时持续请求动画帧。`recording-waveform` 保存 13 个实际音量样本，以 2dp 圆头线绘制，横向步长为宽度的 1/15，半高为 `1dp + value × height × 0.33`。不生成随机「正在说话」峰值；sidecar 为静音样本，不伪造实时输入。

### Extraction evidence and uncanonized drift

以下路径均相对项目根；行号为本次读取快照，符号名可用于后续定位。

| 记录 | 来源 | 取值依据 |
| --- | --- | --- |
| 共用颜色与角色同值项 | `app/app/src/main/java/app/doudou/ime/design/Ui.java:11–27` | 17 个显式颜色常量，BG/KEYBOARD 与 SURFACE/SPECIAL_KEY 同值但角色独立 |
| ripple-white | `app/app/src/main/java/app/doudou/ime/design/Ui.java:150–154` | ARGB `0x24ffffff`，转换为 frontmatter 的 CSS alpha 顺序 |
| sans / App accent / 系统栏 | `app/app/src/main/res/values/styles.xml:2–10` | 主题 sans 与静态深色；不声称 Material 3 或 Dynamic Color 已实现 |
| 共用文本、按钮、字段、卡片 | `app/app/src/main/java/app/doudou/ime/design/Ui.java:40–109` | sp、kern、font padding、minHeight、padding 与圆角 |
| 设置行与导航 | `app/app/src/main/java/app/doudou/ime/design/Ui.java:157–229` | 分隔线、行高、5/8/10dp 间距、导航胶囊与标签 |
| Home 与 practice 变体 | `app/app/src/main/java/app/doudou/ime/home/HomeScreen.java:23–143` | 22/21/17sp、2×3 槽位、fontScale 行高、预览 padding、练习高度 |
| 六格颜色 | `app/app/src/main/java/app/doudou/ime/advisor/PaletteStore.java:9–15` | IDS 顺序与六个实际 hex；natural 与 APP_ACCENT 同值 |
| My 轻振 / 账户 / 品牌底 | `app/app/src/main/java/app/doudou/ime/settings/SettingsScreen.java:36–48`；`app/app/src/main/java/app/doudou/ime/account/AccountSummary.java:15–79`；`app/app/src/main/res/values/brand.xml:3` | 开关 tint、账户卡 padding、标题与额度、品牌资源背景 |
| App 外壳与区块间距 | `app/app/src/main/java/app/doudou/ime/app/MainActivity.java:128–215`；`app/app/src/main/java/app/doudou/ime/app/Screen.java:30–38` | 页边距、标题、底部导航与 14dp 区块 gap |
| 键盘几何、灰阶、elevation | `app/app/src/main/java/app/doudou/ime/input/KeyboardView.java:19,93–217,235–257,356–368`；`app/app/src/main/java/app/doudou/ime/voice/VoiceSpaceKey.java:16–29` | 名义高度、顶栏与权重、2.5/3.5dp margin、7dp 圆角、0.8dp Z |
| 工具网格与禁用状态 | `app/app/src/main/java/app/doudou/ime/input/KeyboardMenu.java:22–87` | 48dp 返回/隐私布局、14/10dp padding、9dp gap、6dp margin，隐私下工具 alpha 0.4 |
| 语音背景、位置与撤回色 | `app/app/src/main/java/app/doudou/ime/voice/DictationSurface.java:39,110–144`；`app/app/src/main/java/app/doudou/ime/voice/ArcAction.java:22–45` | 既有径向底材、比例定位、撤回红、选中缩放 1.025 / 90ms |
| 两个声纹的真实几何 | `app/app/src/main/java/app/doudou/ime/voice/SpeechWaveform.java:26–59`；`app/app/src/main/java/app/doudou/ime/recording/RecordingWaveform.java:18–37` | 共用渐变端点，20/13 根、读数归一化、平滑与圆头线 |
| 口述进入与录音 footer | `app/app/src/main/java/app/doudou/ime/input/KeyboardView.java:397–418`；`app/app/src/main/java/app/doudou/ime/recording/RecordingPanel.java:44–91` | 110ms 淡入、64×36dp 声纹、footer padding 与 48dp 动作 |
| sheet 差异 | `app/app/src/main/java/app/doudou/ime/advisor/AdvisorController.java:437–450` | 局部硬编码面/线色、顶角与真实 6dp layer inset |

**未规范化且未修复：** 通用按钮/图标 helper 仍有 44dp 最小值，部分调用另限 36dp 宽或录音入口 40dp 高，故不写成「所有命中区已统一 48dp」；帮我回 sheet 使用局部 `#303030` 面、`#505050` 线，入口用 `#535353` 描边，且 layer inset 为 6dp，不把它们增订成通用 surface/outline/gutter token。`RelationshipGraph.java:93` 用 dp 设置 Canvas 文字，不能继承为字体缩放规则。`Ui.cell`、`AccountSummary`、Home 练习仍有字符 `›`，`Ui.eyebrow` 仍有 0.04em helper；字符图标和 eyebrow 不进入组件或字距规范。这些是代码中携带的差异或 craft-floor 缺陷，不重开已关闭的视觉 verdict、不补造规范替它们辩护，也不在本次改代码。

## Do's and Don'ts

### Do:

- **Do** 沿用灰阶键盘、既有蓝灰口述底色与青蓝真实声纹。
- **Do** 将 App 浅蓝、槽位身份色、品牌图形底色按各自已实现的用途使用。
- **Do** 从 frontmatter 取实际 dp/sp 值，按组件区分 padding、圆角、视觉尺寸与点击布局。
- **Do** 保持系统 sans 与原生 inset、涟漪和可访问性标签；以源码与 manifest 中的原生截图复核外观。

### Don't:

- **Don't** 新增尚未实现的键盘模式、控件变体、字体、色阶、统一间距基数或网页断点。
- **Don't** 将声纹渐变用于普通键盘按钮、卡片或新的全屏品牌底色。
- **Don't** 把字符箭头、eyebrow helper、局部硬编码、dp Canvas 文字或较小点击布局立为未来界面的惯例。
- **Don't** 用视觉 token 文档、UI fixture 或 sidecar 浏览器样本宣称 F1 模型语义、真实学习或真机体验已验收。

## 0.9.1 workspace and controls

The same 48dp toolbar holds menu, Input/Recording/Help reply/Translation segments, and collapse. Each segment is a real 48dp radio target; only the visual pill is inset. No extra header row. Input/voice geometry stays unchanged. Translation uses an independent editable local draft and an explicit primary Replace/Fill action after success, with secondary Copy/Retranslate actions, with cyan-blue progress. Recorder stays alive while switching tools or hiding the keyboard; a cyan dot in its tab preserves recording awareness.

Ordered palette dimensions share a compact bank of vertical native range controls with three visible detents, tactile circular thumbs and per-field inheritance; rhetoric and structure remain categorical controls. All controls are exposed directly rather than nine accordion rows. Use the shared App blue only for selected/active states. Emoji uses the official AndroidX offline picker with bundled rendering, real categories, variants and account-scoped recents.

Account shows one Credit balance. The bill shows completed usage, localized dates and a separate on-demand pricing sheet. Tariffs belong in that sheet, never in the everyday typing surface.

Four-tab labels stay visible; only the selected item expands its icon and pill. Native radio semantics, full accessible names and tooltips remain. Each fader displays all three actual level names beside its detents. Existing typing keys and voice gesture geometry are preserved.
