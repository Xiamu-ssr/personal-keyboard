/* HTML design review only. Every conversation, result and metric is a fixture.
 * No media, network, persistence, device bridge or sending capability here. */
(function () {
  const el = (id) => document.getElementById(id);
  const root = el("design-review");
  const screen = el("dr-screen");
  const esc = (s) =>
    String(s)
      .replaceAll("&", "&amp;")
      .replaceAll("<", "&lt;")
      .replaceAll(">", "&gt;")
      .replaceAll('"', "&quot;")
      .replaceAll("'", "&#39;");
  const paths = {
    keyboard:
      '<rect x="2.5" y="5" width="19" height="14" rx="3"/><path d="M6 9h.01M10 9h.01M14 9h.01M18 9h.01M6 12h.01M10 12h.01M14 12h.01M18 12h.01M7 16h10"/>',
    mic: '<rect x="8" y="2" width="8" height="13" rx="4"/><path d="M5 10v2a7 7 0 0 0 14 0v-2M12 19v3M8 22h8"/>',
    spark:
      '<path d="m12 3 2.8 6.2L21 12l-6.2 2.8L12 21l-2.8-6.2L3 12l6.2-2.8L12 3Z"/><path d="m20 2 1 2 2 1-2 1-1 2-1-2-2-1 2-1 1-2Z"/>',
    home: '<path d="m3 10 9-7 9 7v10a1 1 0 0 1-1 1h-5v-7H9v7H4a1 1 0 0 1-1-1V10Z"/>',
    history: '<path d="M3 11a9 9 0 1 1 2.4 7M3 5v6h6M12 7v5l3 2"/>',
    book: '<path d="M5 3h14v16H5a2 2 0 0 1 0-4h14M5 3a2 2 0 0 0-2 2v12a4 4 0 0 0 4 4h12M7 7h8"/>',
    people:
      '<circle cx="12" cy="11" r="3"/><circle cx="4" cy="4" r="2"/><circle cx="21" cy="5" r="2"/><circle cx="4" cy="21" r="2"/><circle cx="20" cy="20" r="2"/><path d="m6 5 4 4m4 0 5-3m-9 7-5 6m9-6 5 5"/>',
    settings:
      '<path d="m10 2-.6 3-2.1 1.2L4.5 5.3l-2 3.4 2.2 2.1v2.4l-2.2 2.1 2 3.4 2.8-.9L9.4 19l.6 3h4l.6-3 2.1-1.2 2.8.9 2-3.4-2.2-2.1v-2.4l2.2-2.1-2-3.4-2.8.9L14.6 5 14 2h-4Z"/><circle cx="12" cy="12" r="3"/>',
    back: '<path d="m15 5-7 7 7 7"/>',
    chevron: '<path d="m9 5 7 7-7 7"/>',
    down: '<path d="m5 9 7 7 7-7"/>',
    close: '<path d="m6 6 12 12M18 6 6 18"/>',
    plus: '<path d="M12 4v16M4 12h16"/>',
    search: '<circle cx="10" cy="10" r="7"/><path d="m15.5 15.5 5 5"/>',
    more: '<circle cx="5" cy="12" r="1"/><circle cx="12" cy="12" r="1"/><circle cx="19" cy="12" r="1"/>',
    tiles:
      '<rect x="3" y="3" width="7" height="7" rx="2"/><rect x="14" y="3" width="7" height="7" rx="2"/><rect x="3" y="14" width="7" height="7" rx="2"/><rect x="14" y="14" width="7" height="7" rx="2"/>',
    phrase:
      '<path d="M4 3h16a1 1 0 0 1 1 1v13a1 1 0 0 1-1 1h-8l-6 4v-4H4a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1Z"/><path d="M7 8h10M7 12h7"/>',
    shift: '<path d="m3 11 9-9 9 9h-5v10H8V11H3Z"/>',
    delete:
      '<path d="m2 12 6-8h12a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H8l-6-8Z"/><path d="m11 8 7 8m0-8-7 8"/>',
    enter: '<path d="M20 4v8a3 3 0 0 1-3 3H4m5-5-5 5 5 5"/>',
    globe:
      '<circle cx="12" cy="12" r="10"/><ellipse cx="12" cy="12" rx="4" ry="10"/><path d="M2 12h20M4 6h16M4 18h16"/>',
    lock: '<rect x="5" y="10" width="14" height="11" rx="2"/><path d="M8 10V6a4 4 0 0 1 8 0v4M12 14v3"/>',
    cloud:
      '<path d="M6 18a5 5 0 0 1-1-10 7 7 0 0 1 13-1 5.5 5.5 0 0 1 0 11M12 12v9m-3-3 3 3 3-3"/>',
    edit: '<path d="m15 3 6 6M4 14 16 2a2 2 0 0 1 3 0l3 3a2 2 0 0 1 0 3L10 20l-8 2 2-8Z"/>',
    undo: '<path d="M4 10h10a6 6 0 0 1 0 12M9 5l-5 5 5 5"/>',
    check: '<path d="m5 12 4 4L20 5"/>',
    user: '<circle cx="12" cy="8" r="4"/><path d="M4 22v-3a8 8 0 0 1 16 0v3"/>',
    source: '<path d="M6 3h9l4 4v14H5V3h1Zm8 0v5h5M8 12h8M8 16h6"/>',
    wifi: '<path d="M2 8a16 16 0 0 1 20 0M5 12a11 11 0 0 1 14 0m-10 4a5 5 0 0 1 6 0m-3 4h.01"/>',
  };
  const icon = (name) =>
    `<svg viewBox="0 0 24 24" aria-hidden="true">${paths[name] || paths.spark}</svg>`;
  const btn = (action, content, label, cls = "", extra = "") =>
    `<button type="button" data-pk-action="${action}" class="${cls}" ${label ? `aria-label="${esc(label)}"` : ""} ${extra}>${content}</button>`;
  const ibtn = (action, name, label, cls = "", extra = "") =>
    btn(action, icon(name), label, cls, extra);
  const pages = [
    ["typing", "日常键盘", "keyboard", "键盘"],
    ["voice", "个性语音", "mic"],
    ["advisor", "原地军师", "spark"],
    ["home", "首页", "home", "App 管理"],
    ["history", "历史记录", "history"],
    ["dictionary", "热词与文风", "book"],
    ["people", "人物关系", "people"],
    ["settings", "设置", "settings"],
  ];
  const replyData = [
    {
      name: "自然",
      initial: "这周想先歇一歇，下周找个安静的地方一起吃饭？",
      more: [
        "这周先缓一缓，下周一起找家安静的小馆子？",
        "想先休息两天，下周找个安静的地方聊聊吧。",
      ],
    },
    {
      name: "温和",
      initial: "好想和你聊聊，不过这周想休息一下。我们下周再约，好吗？",
      more: [
        "我也很想见你，这周想先充充电。下周一起找个安静的地方？",
        "谢谢你惦记着我，等我这周缓一缓，我们下周再见，好吗？",
      ],
    },
    {
      name: "简洁",
      initial: "这周先休息，下周再约个安静的地方吧。",
      more: [
        "这周歇一下，下周再约？找家安静的店。",
        "下周再约吧，这周想先休息。",
      ],
    },
  ];
  const workReplies = [
    {
      name: "自然",
      initial: "我先把手头的事情收好，确认有空了再约你看方案。",
      more: [
        "等手头忙完，我再和你约看方案的时间。",
        "我先处理完眼前的事，空下来一起看。",
      ],
    },
    {
      name: "温和",
      initial: "谢谢你等我，我先把手头的事情处理好，再和你约个时间。",
      more: [
        "我也想好好看看方案，等这边忙完再约你，可以吗？",
        "谢谢理解，等我处理完手头的事再来找你。",
      ],
    },
    {
      name: "简洁",
      initial: "忙完再约时间看方案。",
      more: ["手头处理好后，再约你。", "稍后确认时间，再一起看。"],
    },
  ];
  const replies = () => (s.contact === "林然" ? replyData : workReplies);
  const historyFixtures = [
    {
      time: "20:54",
      raw: "这周想先休息一下，呃，我们下周再约，找个安静的地方聊聊。",
      text: "这周想先休息一下，我们下周再约，找个安静的地方聊聊。",
      meta: "按个人口吻整理",
    },
    {
      time: "19:12",
      raw: "把 personal keyboard 的键区间距再看一下。",
      text: "把 Personal Keyboard 的键区间距再看一下。",
      meta: "使用了 1 个热词",
    },
    {
      time: "10:28",
      raw: "豆包的词库和个人文风可以互相反哺。",
      text: "豆豆的词库和个人文风可以互相反哺。",
      meta: "豆包 → 豆豆",
    },
  ];
  const initialWords = () => [
    { text: "豆豆", kind: "auto", from: "语音修改 · 豆包 → 豆豆" },
    { text: "Personal Keyboard", kind: "manual", from: "手动添加" },
    { text: "Paraformer", kind: "auto", from: "输入修改 · 帕拉 → Paraformer" },
    { text: "林然", kind: "auto", from: "语音修改 · 临然 → 林然" },
    { text: "ZenMux", kind: "manual", from: "手动添加" },
    { text: "Typeoff", kind: "auto", from: "输入修改" },
  ];
  const fresh = () => ({
    page: "typing",
    phase: "idle",
    contact: "林然",
    drafts: { 林然: "", 陈珂: "" },
    composition: "",
    letters: false,
    caps: false,
    symbols: false,
    previous: null,
    variants: [0, 0, 0],
    style: null,
    voiceText: "这周想先休息一下，我们下周再约，找个安静的地方聊聊。",
    intents: { 林然: "这周想休息，下周再约", 陈珂: "忙完手头的事，再确认时间" },
    personalStyle: "自然直接，少客套；不替我许诺，不改变原意。保留专有名词。",
    words: initialWords(),
    filter: "all",
    search: "",
    memoryTab: "words",
    cloud: false,
    learning: true,
    sync: false,
    sheet: null,
    toast: "",
    undo: null,
    preferences: {
      林然: "喜欢安静、可以慢慢聊天的地方。",
      陈珂: "先看结论，再看方案的细节。",
      妈妈: "更喜欢通话，消息慢慢回就好。",
      叶青: "沟通时直说需要帮助的地方。",
    },
    epoch: 0,
  });
  let s = fresh(),
    timers = [],
    visible = false,
    guides = false;
  const notes = {
    typing: {
      title: "一行解决入口",
      copy: "按键与工具块参考豆包：饱满键帽、清楚边界，底行保留标点和中英切换。空闲行放工具、说话与军师；开始拼音后让给候选词。工具宫格按需打开。",
      tokens: [
        ["48", "候选 / 动作行"],
        ["6", "水平键隙"],
        ["9", "垂直键隙"],
        ["44", "工具命中区"],
      ],
      phases: [
        ["idle", "空闲"],
        ["composing", "拼音候选"],
        ["tools", "工具面板"],
      ],
    },
    voice: {
      title: "说完，再成为你的话",
      copy: "参考大面积录音按钮。转写和个人整理分两步；整理结果可先改、再填入。热词帮助听对，文风帮助说得像你。",
      tokens: [
        ["144 × 66", "录音按钮"],
        ["326", "键区高度"],
        ["16", "结果字号"],
        ["44", "操作命中区"],
      ],
      phases: [
        ["idle", "待录音"],
        ["recording", "录音中"],
        ["ready", "整理完成"],
      ],
    },
    advisor: {
      title: "先让出空间，再给回复",
      copy: "点军师后自动缩矮，模拟宿主重排、当前屏读取与理解，再展开三条建议。每种风格一条，“更多”只展开所选风格。草稿一直保留。",
      tokens: [
        ["92", "读屏条高度"],
        ["326", "结果区高度"],
        ["3", "风格各一条"],
        ["0", "自动发送"],
      ],
      phases: [
        ["capture", "缩矮读屏"],
        ["results", "三条回复"],
        ["error", "失败恢复"],
      ],
    },
    home: {
      title: "回顾使用，不堆推销",
      copy: "借鉴统计卡与悬浮底栏。首页只留试用入口、使用概览和最近记忆；第一版自用，不加入会员广告和无意义打卡。",
      tokens: [
        ["16", "页面侧距"],
        ["20", "卡片圆角"],
        ["62", "底栏高度"],
        ["4", "管理入口"],
      ],
    },
    history: {
      title: "留得住，也删得掉",
      copy: "转写与整理结果放在同一条历史中，点开比较。历史保存在本机，云同步单独开关；记录去留可在设置管理。",
      tokens: [
        ["16", "页面侧距"],
        ["14", "记录字号"],
        ["18", "列表圆角"],
        ["本机", "默认保存"],
      ],
    },
    dictionary: {
      title: "热词与文风，相互反哺",
      copy: "参考所有 / 自动 / 手动筛选与悬浮添加。自动词显示修改来源，可修订、删除与撤销；文风在同一记忆页查看。",
      tokens: [
        ["44", "添加命中区"],
        ["60", "词条行高"],
        ["3", "筛选条件"],
        ["来源", "学习可追溯"],
      ],
    },
    people: {
      title: "关系图是导航，事实可修订",
      copy: "图只提供关系概览，点击人物看事实与来源；明确区分已经发生和计划。人物记忆与热词分开，按联系人隔离。",
      tokens: [
        ["237", "图区域高度"],
        ["64", "人物命中区"],
        ["来源", "每条记忆"],
        ["撤销", "修订可回退"],
      ],
    },
    settings: {
      title: "同一个 App 的设置",
      copy: "模型、学习、数据和豆豆同步在这里。语音只配 ASR，回复 / 润色 / 抽取共用文本模型。密钥只用演示占位，页面不会连接服务。",
      tokens: [
        ["2", "远程能力"],
        ["本地", "中文 OCR"],
        ["开关", "学习 / 同步"],
        ["隔离", "个人与人物"],
      ],
    },
  };
  function clearTimers() {
    timers.forEach(clearTimeout);
    timers = [];
    s.epoch++;
  }
  function later(fn, ms) {
    const epoch = s.epoch;
    timers.push(
      setTimeout(() => {
        if (epoch === s.epoch && visible) fn();
      }, ms),
    );
  }
  function draft() {
    return s.drafts[s.contact] || "";
  }
  function announce(text) {
    el("dr-announcement").textContent = text;
  }
  function setDraft(text, remember = true) {
    if (remember) s.previous = { contact: s.contact, text: draft() };
    s.drafts[s.contact] = text;
    const input = el("pk-draft");
    if (input) input.value = text;
  }
  function goto(page, phase) {
    clearTimers();
    s.page = page;
    s.phase = phase || (page === "advisor" ? "results" : "idle");
    s.sheet = null;
    s.toast = "";
    s.search = "";
    if (page === "dictionary") s.memoryTab = "words";
    if (!["typing", "voice", "advisor"].includes(page))
      screen.querySelector(".pk-app-body")?.scrollTo(0, 0);
    if (page === "people") s.memoryTab = "people";
    if (page !== "typing") s.composition = "";
    updateHash();
    render();
  }
  function updateHash() {
    if (!visible) return;
    const u = new URL(location.href);
    u.hash = "design/" + s.page;
    history.replaceState(null, "", u);
  }
  function pageTitle() {
    return pages.find(([id]) => id === s.page)?.[1] || "界面对稿";
  }
  function activate(which, page) {
    clearTimers();
    visible = which === "design";
    if (!el("inspector").hidden) el("close-inspector").click();
    el("world-main").hidden = visible;
    root.hidden = !visible;
    el("show-world").setAttribute("aria-pressed", String(!visible));
    el("show-design").setAttribute("aria-pressed", String(visible));
    document.body.dataset.section = visible ? "design" : "world";
    if (visible) {
      if (page && pages.some(([id]) => id === page)) {
        s.page = page;
        s.phase = page === "advisor" ? "results" : "idle";
      }
      updateHash();
      render();
    } else {
      const u = new URL(location.href);
      u.hash = "";
      history.replaceState(null, "", u);
      window.dispatchEvent(new Event("resize"));
    }
  }
  function renderNav() {
    el("dr-pages").innerHTML = pages
      .map(
        ([id, name, ic, group], i) =>
          `${group ? `<span class="dr-group">${group}</span>` : ""}<button data-dr-page="${id}" ${s.page === id ? 'aria-current="page"' : ""}>${icon(ic)}<span>${name}</span><small>${String(i + 1).padStart(2, "0")}</small></button>`,
      )
      .join("");
  }
  function renderNotes() {
    const n = notes[s.page];
    el("dr-note-title").textContent = n.title;
    el("dr-note-copy").textContent = n.copy;
    el("dr-tokens").innerHTML = n.tokens
      .map(
        ([v, k]) =>
          `<div><strong>${v}</strong><small>${k} · ${/高度|边距|侧距|间距|键隙|命中|字号|按钮|圆角|动作行/.test(k) ? "CSS px" : "约定"}</small></div>`,
      )
      .join("");
    el("dr-state-controls").innerHTML = (n.phases || [])
      .map(
        ([id, name]) =>
          `<button data-dr-phase="${id}" aria-pressed="${s.phase === id}">${name}</button>`,
      )
      .join("");
    el("dr-scene-title").textContent = pageTitle();
  }
  function status() {
    return `<div class="pk-status"><span>21:03</span><span class="pk-status-right">${icon("wifi")} 5G <span class="pk-battery">84</span></span></div>`;
  }
  function systembar() {
    return '<div class="pk-systembar"><span></span></div>';
  }
  function chat() {
    const lin = s.contact === "林然";
    return `<section class="pk-host ${s.phase === "capture" ? "expanded" : ""}"><div class="pk-chat-head">${ibtn("app-home", "back", "打开输入法首页")}<button class="pk-chat-person" data-pk-action="contacts"><span class="pk-avatar">${s.contact.slice(-1)}</span><span><strong>${s.contact}</strong><small>${lin ? "朋友" : "同事"} · 演示聊天</small></span></button>${ibtn("contacts", "more", "切换演示联系人")}</div><div class="pk-messages"><p class="pk-date">今天 20:42</p><div class="pk-message pk-chat-extra"><span class="pk-avatar">${s.contact.slice(-1)}</span><div>${lin ? "上次那家小店还挺不错的。" : "新方案我已经发到群里了。"}</div></div><div class="pk-message mine pk-chat-extra"><div>${lin ? "是呀，安安静静地聊了一晚上。" : "收到了，我空下来好好看一下。"}</div></div><div class="pk-message"><span class="pk-avatar">${s.contact.slice(-1)}</span><div>${lin ? "最近忙完了吗？周末一起吃个饭？" : "明天的方案有空一起看一下吗？"}</div></div><div class="pk-message mine"><div>${lin ? "最近有点累，想找个安静的地方。" : "我先把手上的事情收一下。"}</div></div><div class="pk-message"><span class="pk-avatar">${s.contact.slice(-1)}</span><div>${lin ? "好呀，时间你来定，不着急。" : "好，不急。方便的时候再约。"}</div></div></div><div class="pk-composer">${ibtn("composer-help", "plus", "演示聊天说明")}<textarea id="pk-draft" rows="1" aria-label="聊天草稿" placeholder="消息">${esc(draft())}</textarea><span class="pk-no-send" aria-label="草稿由用户自行发送">自行<br>发送</span></div></section>`;
  }
  function head(title, right = "", back = true) {
    return `<div class="pk-ime-head">${back ? ibtn("keyboard", "back", "返回键盘") : btn("app-home", '<span class="pk-mark">p.</span>', "打开输入法首页", "pk-mark")}<span class="pk-mode-title">${title}</span>${right}</div>`;
  }
  function keys() {
    const row = (str, cls = "") =>
      `<div class="pk-key-row ${cls}">${[...str].map((k) => btn("key", s.caps || !s.letters ? k.toUpperCase() : k, `输入 ${k}`, "", `data-key="${k}"`)).join("")}</div>`;
    return `<div class="pk-keys">${s.symbols ? row("1234567890") : row("qwertyuiop")}${s.symbols ? row("@#￥%&*()", "offset") : row("asdfghjkl", "offset")}<div class="pk-key-row">${ibtn("shift", "shift", "切换大小写", "pk-key-special", `aria-pressed="${s.caps}"`)}${[...(s.symbols ? "，。？！：；/" : "zxcvbnm")].map((k) => btn("key", s.caps || !s.letters ? k.toUpperCase() : k, `输入 ${k}`, "", `data-key="${k}"`)).join("")}${ibtn("backspace", "delete", "退格", "pk-key-special")}</div><div class="pk-key-row">${btn("symbols", s.symbols ? "ABC" : "123", "数字与符号", "pk-key-num")}${btn("punctuation", "，", "输入逗号", "pk-key-punctuation")}${btn("space", s.letters ? "space" : "空格", "空格 / 选择候选", "pk-space")}${btn("language", s.letters ? "EN" : "中", "切换拼音和英文", "pk-key-language")}${btn("newline", "换行", "换行", "pk-enter")}</div></div>`;
  }
  function candidates() {
    if (s.composition === "nihao") return ["你好", "你好呀", "你好吗"];
    if (s.composition === "mingtian") return ["明天", "明天见", "明天再说"];
    return [s.composition];
  }
  function keyboardFoot() {
    return `<div class="pk-ime-foot">${ibtn("language", "globe", "切换拼音和英文")}<span>${s.letters ? "EN" : "简体拼音"}</span>${ibtn("app-settings", "settings", "输入法设置")}</div>`;
  }
  function tools() {
    const items = [
      ["voice", "mic", "语音输入"],
      ["advisor", "spark", "原地军师"],
      ["phrases", "phrase", "常用语"],
      ["app-dictionary", "book", "热词文风"],
      ["app-history", "history", "历史记录"],
      ["app-people", "people", "人物记忆"],
      ["switch-language", "globe", "中英切换"],
      ["app-settings", "settings", "设置"],
    ];
    return `<section class="pk-ime" data-ui="tools">${head("工具", ibtn("app-settings", "settings", "输入法设置"))}<div class="pk-tools-grid">${items.map(([a, ic, name]) => btn(a, icon(ic) + `<span>${name}</span>`, name, "pk-tool-tile")).join("")}</div>${keyboardFoot()}</section>`;
  }
  function keyboard() {
    if (s.phase === "tools") return tools();
    if (s.phase === "collapsed")
      return `<section class="pk-ime collapsed" data-ui="collapsed">${head("键盘已收起", btn("keyboard", "恢复键盘", "恢复键盘", "pk-restore"))}</section>`;
    const composing = !!s.composition;
    return `<section class="pk-ime" data-ui="typing"><div class="pk-ime-head">${
      composing
        ? `<div class="pk-candidates">${candidates()
            .map((w) =>
              btn(
                "candidate",
                esc(w),
                `选择候选 ${w}`,
                "",
                `data-word="${esc(w)}"`,
              ),
            )
            .join("")}</div>${ibtn("voice", "mic", "语音输入")}`
        : `${ibtn("tools", "tiles", "打开工具面板", "pk-tool-menu")}${btn("voice", icon("mic") + "<span>点击说话</span>", "语音输入", "pk-voice-shortcut")}<span class="pk-head-space"></span>`
    }${ibtn("advisor", "spark", "军师")}${composing ? "" : ibtn("collapse", "down", "收起键盘", "pk-collapse")}</div>${composing ? `<div class="pk-composition">${esc(s.composition === "nihao" ? "ni hao" : s.composition)}</div>` : ""}${keys()}${keyboardFoot()}</section>`;
  }
  function wave() {
    return `<div class="pk-wave" aria-hidden="true">${[10, 21, 32, 17, 27, 13, 32, 20, 9].map((h, i) => `<i style="--h:${h}px;--d:${i * -0.14}s"></i>`).join("")}</div>`;
  }
  function voice() {
    let body = "";
    if (s.phase === "recording")
      body = `${wave()}<div class="pk-transcript">这周想先休息一下……<br>我们下周再约。</div>${btn("stop-recording", '<span class="pk-stop-square"></span>说完了', "停止录音", "pk-stop-recording")}`;
    else if (s.phase === "ready")
      body = `<span class="pk-voice-meta">已按热词和你的口吻整理</span><div class="pk-voice-edit"><textarea id="pk-voice-text" aria-label="整理后的语音">${esc(s.voiceText)}</textarea></div><div class="pk-voice-actions">${ibtn("record", "mic", "重新录音")}${btn("insert-voice", "填入草稿", "填入语音草稿", "pk-voice-insert")}${ibtn("voice-original", "source", "查看原始转写")}</div>`;
    else if (s.phase === "transcribing")
      body = `${wave()}<p>整理成你的表达…</p>${btn("cancel-voice", "取消", "取消整理", "pk-soft-button")}`;
    else
      body = `<p>点击说话</p>${btn("record", icon("mic"), "开始录音演示", "pk-mic-button")}<div class="pk-voice-actions">${ibtn("app-dictionary", "book", "热词与文风")}${btn("newline", "换行", "换行", "pk-voice-insert")}${ibtn("backspace", "delete", "退格")}</div>`;
    return `<section class="pk-ime" data-ui="voice">${head("个性语音", ibtn("app-dictionary", "book", "热词与文风"))}<div class="pk-voice-body">${body}</div></section>`;
  }
  function replyCard(text, i, variant = null) {
    const chosen = s.style === i && variant === null;
    return `<div class="pk-reply ${chosen ? "pk-picked" : ""}">${btn("reply", `<strong>${replies()[i].name}${chosen ? " · 已填入" : ""}</strong><span>${esc(text)}</span>`, `填入${replies()[i].name}回复`, "pk-reply-select", `data-style="${i}" ${variant !== null ? `data-variant="${variant}"` : ""}`)}${variant === null ? btn("more-replies", "更多", `更多${replies()[i].name}回复`, "pk-reply-more", `data-style="${i}"`) : ""}</div>`;
  }
  function advisor() {
    if (s.phase === "capture")
      return `<section class="pk-ime compact" data-ui="capture">${head("原地军师", btn("cancel-advisor", "取消", "取消读屏"))}<div class="pk-capture-bar"><i class="pk-capture-dot"></i><span id="pk-capture-label">${esc(s.captureLabel || "等待聊天展开…")}</span>${btn("finish-capture", "看结果", "立即看演示结果", "pk-capture-done")}</div></section>`;
    let body = "";
    if (s.phase === "error")
      body = `<div class="pk-advisor-error">这次没有读到可用聊天。<br><small>草稿还在，可以重试或返回键盘。</small><br>${btn("advisor", "重新读取", "重试读取")}</div>`;
    else if (s.phase === "more")
      body = `<div class="pk-more-head">${ibtn("back-replies", "back", "返回三条原建议")}<span>${replies()[s.moreStyle].name} · 换种表达</span><small>只换这一种风格</small></div>${replies()
        [s.moreStyle].more.map((text, n) => replyCard(text, s.moreStyle, n))
        .join("")}`;
    else
      body = `<div class="pk-intent"><span>我的意思</span><input id="pk-intent" aria-label="我的回复意图" value="${esc(s.intents[s.contact])}"></div>${replies()
        .map((r, i) => replyCard(s.variants[i] ? r.more[0] : r.initial, i))
        .join("")}`;
    return `<section class="pk-ime" data-ui="advisor">${head("原地军师", ibtn("supplement", "plus", "补读前文"))}<div class="pk-advisor-body">${body}</div><div class="pk-advisor-foot"><span>建议只填草稿，发送由你决定</span>${s.previous ? btn("undo-insert", `${icon("undo")} 撤销`, "撤销填入") : btn("app-people", "人物记忆 ↗", "查看人物记忆")}</div></section>`;
  }
  function nav(active) {
    return `<nav class="pk-bottom-nav" aria-label="输入法 App 导航">${[
      ["home", "首页", "home"],
      ["history", "历史", "history"],
      ["dictionary", "记忆", "book"],
      ["settings", "设置", "user"],
    ]
      .map(([id, name, ic]) =>
        btn(
          "app-" + id,
          icon(ic) + `<span>${name}</span>`,
          name,
          "",
          active === id ? 'aria-current="page"' : "",
        ),
      )
      .join("")}</nav>`;
  }
  function title(name, action = null, ic = "more", label = "") {
    return `<div class="pk-app-title"><h2>${name}</h2>${action ? ibtn(action, ic, label) : ""}</div>`;
  }
  function home() {
    return `<div class="pk-app-title"><h2 class="pk-title-logo"><b>p.</b>Personal Keyboard</h2>${ibtn("app-settings", "settings", "设置")}</div><section class="pk-card pk-hero"><span class="pk-label">你的表达，越用越懂</span><span class="pk-hero-art" aria-hidden="true">p.</span><h3>想说的话，<br>顺手就说出来。</h3><p>打字、说话，或在原地找一点灵感。</p><br>${btn("try-keyboard", `${icon("keyboard")} 试试键盘`, "试试键盘", "pk-soft-button")}</section><div class="pk-card pk-stats"><div><strong>12.8k <small>字</small></strong><p>已整理的表达</p></div><div><strong>6 <small>小时 18 分</small></strong><p>节省的输入时间</p></div><div><strong>186 <small>字 / 分钟</small></strong><p>平均语音速度</p></div><div><strong>28 <small>次</small></strong><p>军师帮你想回复</p></div></div><div class="pk-section-head"><h3>最近学会</h3>${btn("app-dictionary", `查看全部 ${icon("chevron")}`, "查看全部热词")}</div><div class="pk-card pk-memory-summary"><span>${icon("spark")}</span><div><strong>“豆豆”，这次记住了</strong><p>来自你的一次语音修改</p></div>${ibtn("app-dictionary", "chevron", "查看词条")}</div><div class="pk-card"><span class="pk-label">最近两周 · 表达慢慢变顺</span><div class="pk-grid-dots">${Array.from({ length: 28 }, (_, i) => `<i class="${[0, 2, 5, 6, 8, 9, 12, 13, 14, 15, 17, 19, 21, 24, 26, 27].includes(i) ? "filled" : ""}"></i>`).join("")}</div></div>`;
  }
  function toggle(action, checked, label) {
    return btn(
      action,
      "<i></i>",
      label,
      "pk-switch",
      `role="switch" aria-checked="${checked}"`,
    );
  }
  function historyPage() {
    return `${title("历史记录", "history-settings", "more", "历史设置")}<div class="pk-card pk-sync-card">${icon("cloud")}<div><strong>云同步</strong><p>${s.cloud ? "已开启 · 演示状态" : "关闭 · 历史留在本机"}</p></div>${toggle("toggle-cloud", s.cloud, "历史云同步")}</div><div class="pk-list"><button data-pk-action="history-settings">${icon("history")}<strong>保留历史</strong><span>30 天</span>${icon("chevron")}</button><div>${icon("lock")}<div><strong>记录由你掌握</strong><p>原始音频默认不留存，可以随时删除文字记录。</p></div></div></div><h3 class="pk-history-date">今天 · 10 月 20 日</h3><div class="pk-list">${[
      ["20:54", "这周想先休息一下，我们下周再约。", "按个人口吻整理"],
      ["19:12", "把 Personal Keyboard 的键区间距再看一下。", "使用了 1 个热词"],
      ["10:28", "豆豆的词库和个人文风可以互相反哺。", "豆包 → 豆豆"],
    ]
      .map(
        ([time, text, meta], i) =>
          `<button class="pk-history-item" data-pk-action="history-detail" data-index="${i}"><small>${time}</small><p>${text}</p><span class="pk-history-meta">${meta} ↗</span></button>`,
      )
      .join("")}</div>`;
  }
  function memoryTabs() {
    return `<div class="pk-filter pk-memory-tabs">${[
      ["words", "热词"],
      ["style", "文风"],
      ["people", "人物"],
    ]
      .map(([id, name]) =>
        btn(
          "memory-tab",
          name,
          name,
          "",
          `data-memory="${id}" aria-pressed="${s.memoryTab === id}"`,
        ),
      )
      .join("")}</div>`;
  }
  function dictionary() {
    let content = "";
    if (s.memoryTab === "style")
      content = `<div class="pk-card"><span class="pk-label">我的文风</span><h3 style="font-size:22px;margin:13px 0">自然、直接，留一点温度。</h3><p style="font-size:12px;color:#b4bfb0;line-height:1.9">${esc(s.personalStyle)}</p><div class="pk-style-tags"><span>少用感叹号</span><span>不堆敬语</span><span>保留语气</span></div>${btn("style-edit", `${icon("edit")} 修改偏好`, "修改个人文风", "pk-soft-button")}</div><div class="pk-card pk-card-outline"><span class="pk-label">学习来自可核实的修改</span><p style="font-size:12px;color:#a7b5a0;margin:12px 0">“麻烦您方便的话” → “有空的话”</p><span class="pk-source">${icon("source")} 输入修改 · 你已确认</span></div>`;
    else if (s.memoryTab === "people") content = peopleBody();
    else {
      const words = s.words.filter(
        (w) =>
          (s.filter === "all" || w.kind === s.filter) &&
          w.text.toLowerCase().includes(s.search.toLowerCase()),
      );
      content = `<div class="pk-filter">${[
        ["all", "所有"],
        ["auto", "自动添加"],
        ["manual", "手动添加"],
      ]
        .map(([id, name]) =>
          btn(
            "word-filter",
            name,
            name,
            "",
            `data-filter="${id}" aria-pressed="${s.filter === id}"`,
          ),
        )
        .join(
          "",
        )}</div>${s.searchOpen ? `<input id="pk-word-search" class="pk-search-field" aria-label="搜索热词" value="${esc(s.search)}" placeholder="搜索词条">` : ""}<div id="pk-word-list" class="pk-list">${wordRows(words)}</div><p style="font-size:10px;color:#80917c;line-height:1.8;padding:0 6px">热词用于识别和输入。自动添加保留来源；<br>点词条可查看、修改或移除。</p>`;
    }
    return `${title("个人记忆", "word-search", "search", "搜索词库")}${memoryTabs()}${content}`;
  }
  function wordRows(words) {
    return words.length
      ? words
          .map(
            (w) =>
              `<button data-pk-action="word-detail" data-word="${esc(w.text)}">${icon(w.kind === "auto" ? "spark" : "edit").replace("<svg ", `<svg class="${w.kind === "auto" ? "pk-word-star" : "pk-word-manual"}" `)}<strong>${esc(w.text)}</strong></button>`,
          )
          .join("")
      : '<div class="pk-empty">没有匹配词条。可以换个词，或手动添加。</div>';
  }
  function peopleBody() {
    return `<div class="pk-graph"><svg viewBox="0 0 350 237" preserveAspectRatio="none" aria-hidden="true"><line x1="175" y1="118" x2="75" y2="57"/><line x1="175" y1="118" x2="280" y2="65"/><line x1="175" y1="118" x2="278" y2="183"/><line x1="175" y1="118" x2="77" y2="190"/></svg><span class="pk-node self" style="left:50%;top:50%"><i>我</i>自己</span>${[
      ["林然", 21, 23],
      ["陈珂", 80, 27],
      ["妈妈", 80, 77],
      ["叶青", 22, 80],
    ]
      .map(([name, x, y]) =>
        btn(
          "person",
          `<i>${name.slice(-1)}</i>${name}`,
          `查看${name}`,
          "pk-node",
          `data-person="${name}" style="left:${x}%;top:${y}%"`,
        ),
      )
      .join(
        "",
      )}</div><div class="pk-section-head"><h3>人物与最近事件</h3><span style="font-size:10px;color:#94a08e">按联系人隔离</span></div><div class="pk-list">${[
      ["林然", "朋友 · 喜欢安静的地方"],
      ["陈珂", "同事 · 偏好先看结论"],
      ["妈妈", "家人 · 周日通话（计划）"],
    ]
      .map(
        ([name, note]) =>
          `<button data-pk-action="person" data-person="${name}"><span class="pk-avatar">${name.slice(-1)}</span><div><strong>${name}</strong><p>${note}</p></div>${icon("chevron")}</button>`,
      )
      .join("")}</div>`;
  }
  function peoplePage() {
    return `${title("个人记忆", "app-settings", "settings", "管理记忆")}${memoryTabs()}${peopleBody()}`;
  }
  function personSheet() {
    const name = s.person || "林然";
    const lin = name === "林然";
    return `<div class="pk-sheet-head">${ibtn("close-sheet", "back", "返回人物关系")}<h3>人物记忆</h3>${ibtn("close-sheet", "close", "关闭人物详情")}</div><div class="pk-person-hero"><span class="pk-avatar">${name.slice(-1)}</span><div><h2>${name}</h2><p>${lin ? "朋友" : "演示联系人"} · 记忆仅用于这个人</p></div></div><section class="pk-card pk-memory-detail"><h3>沟通偏好</h3><p>${esc(s.preferences[name])}</p><span class="pk-source">${icon("source")} ${lin ? "10 月 18 日 · 已读聊天，对方陈述" : "用户主动补充 · 演示来源"}</span>${btn("edit-person", `${icon("edit")} 修订这条`, "修订人物偏好")}</section><section class="pk-card pk-memory-detail"><h3>最近事件</h3><p><span class="pk-badge">计划</span>${lin ? "下周找个安静的地方吃饭。" : name === "妈妈" ? "周日通个电话。" : "处理好手头的事，再确认见面时间。"}</p><span class="pk-source">${icon("source")} 10 月 20 日 · 本人草稿，尚未确认发生</span></section><p>只修改这位联系人的记忆；不会把草稿当作已经发生。</p>`;
  }
  function settings() {
    const row = (action, ic, name, val) =>
      `<button data-pk-action="${action}">${icon(ic)}<strong>${name}</strong><span>${val}</span>${icon("chevron")}</button>`;
    return `${title("设置")}<div class="pk-card pk-settings-lead"><span class="pk-avatar">p.</span><div><strong>我的输入法</strong><p>本机个人空间</p></div></div><p class="pk-settings-label">模型服务</p><div class="pk-list">${row("model-asr", "mic", "语音识别", "待连接")}${row("model-text", "spark", "文本模型", "待连接")}</div><p class="pk-settings-label">越用越懂你</p><div class="pk-list"><div>${icon("spark")}<strong>从可信修改中学习</strong>${toggle("toggle-learning", s.learning, "从可信修改中学习")}</div>${row("app-dictionary", "book", "热词与文风", s.words.length + " 个热词")}${row("app-people", "people", "人物与事件", "4 位人物")}</div><p class="pk-settings-label">数据与连接</p><div class="pk-list"><div>${icon("cloud")}<div><strong>同步给豆豆</strong><p>按热词、文风和人物分别选择</p></div>${toggle("toggle-sync", s.sync, "同步给豆豆")}</div>${row("data-settings", "lock", "数据管理", "本机优先")}${row("history-settings", "history", "历史保留", "30 天")}</div><p style="text-align:center;font-size:10px;color:#788673;margin-top:23px">Personal Keyboard · 设计预览</p>`;
  }
  function app() {
    let body = "";
    let active = s.page;
    if (s.page === "home") body = home();
    if (s.page === "history") body = historyPage();
    if (s.page === "dictionary") body = dictionary();
    if (s.page === "people") {
      body = peoplePage();
      active = "dictionary";
    }
    if (s.page === "settings") body = settings();
    return `<div class="pk-app-body">${body}</div>${s.page === "dictionary" && s.memoryTab === "words" ? `<div class="pk-fab">${ibtn("add-word", "plus", "添加热词")}</div>` : ""}${nav(active)}`;
  }
  function sheet() {
    if (!s.sheet) return "";
    let titleText = "",
      body = "";
    if (s.sheet === "person") body = personSheet();
    if (s.sheet === "phrases") {
      titleText = "常用语";
      body = `<div class="pk-list">${["收到，我稍后看一下。", "我先确认一下，稍后回复你。", "谢谢，辛苦啦。"].map((text) => btn("insert-phrase", `<strong>${text}</strong>`, text, "", `data-text="${text}"`)).join("")}</div><p>点击填入当前草稿，发送由你决定。</p>`;
    }
    if (s.sheet === "contacts") {
      titleText = "切换演示聊天";
      body = `<div class="pk-list">${["林然", "陈珂"].map((name) => btn("switch-contact", `<span class="pk-avatar">${name.slice(-1)}</span><strong>${name}</strong>${name === s.contact ? icon("check") : ""}`, `切到${name}`, "", `data-person="${name}"`)).join("")}</div><p>演示联系人变化会取消旧任务；每位联系人保留自己的草稿。</p>`;
    }
    if (s.sheet === "add-word" || s.sheet === "edit-word") {
      titleText = s.sheet === "add-word" ? "添加热词" : "修改热词";
      body = `<form class="pk-form" id="pk-word-form"><label>词条<input id="pk-word-value" aria-label="热词内容" value="${esc(s.editWord?.text || "")}" maxlength="60" required autocomplete="off" placeholder="名字、产品或专有名词"></label><button type="submit">保存词条</button></form><p>保存在本机词库，下次识别和输入时优先考虑。</p>`;
    }
    if (s.sheet === "word-detail") {
      const w = s.words.find((w) => w.text === s.word);
      if (!w) return "";
      titleText = "词条详情";
      body = `<h3 style="font-size:27px;font-weight:500;margin:20px 0">${esc(w.text)}</h3><div class="pk-card pk-card-outline"><span class="pk-label">${w.kind === "auto" ? "自动添加" : "手动添加"}</span><p style="font-size:13px;margin:12px 0;color:#d9e6d1">${esc(w.from)}</p><span class="pk-source">${icon("source")} 演示记录 · 可修订</span></div><div class="pk-form">${btn("edit-word", "修改词条", "修改词条")}${btn("delete-word", "移除词条", "移除词条")}</div>`;
    }
    if (s.sheet === "edit-person") {
      titleText = "修订沟通偏好";
      body = `<form id="pk-person-form" class="pk-form"><label>${esc(s.person || "林然")}<textarea id="pk-person-value" aria-label="人物偏好" rows="3" required maxlength="200">${esc(s.preferences[s.person || "林然"])}</textarea></label><button type="submit">保存修订</button></form><p>你的修订优先于自动抽取，保留来源并支持撤销。</p>`;
    }
    if (s.sheet === "model-asr" || s.sheet === "model-text") {
      const asr = s.sheet === "model-asr";
      titleText = asr ? "语音识别" : "文本模型";
      body = `<div class="pk-form"><label>服务<select aria-label="演示模型服务"><option>${asr ? "阿里云百炼 · Paraformer Realtime" : "自定义 · 现有 CPA"}</option><option>${asr ? "ZenMux · Qwen ASR（录完转写）" : "阿里云百炼 · Qwen Flash"}</option></select></label><label>API Key<input aria-label="演示密钥占位" placeholder="尚未填写" value="" disabled></label><button type="button" data-pk-action="model-demo">了解接入</button></div><p>界面对稿不接收真实密钥。连接与测试留到实际 App；ASR 和文本分别配置。</p>`;
    }
    if (s.sheet === "style") {
      titleText = "个人文风";
      body = `<div class="pk-form"><label>你的偏好<textarea id="pk-style-value" aria-label="个人文风演示" rows="4">${esc(s.personalStyle)}</textarea></label>${btn("style-save", "保存偏好", "保存文风偏好")}</div><p>演示只保留本页状态，刷新后恢复。</p>`;
    }
    if (s.sheet === "history-detail") {
      titleText = "转写与整理";
      const item = historyFixtures[s.historyIndex || 0];
      body = `<div class="pk-card"><span class="pk-label">原始转写 · 演示</span><p style="font-size:14px;line-height:1.9;margin-top:10px">${esc(item.raw)}</p></div><div class="pk-card"><span class="pk-label">个人整理</span><p style="font-size:14px;line-height:1.9;margin-top:10px">${esc(item.text)}</p></div><p>对比转写与整理，保留原意。此处均为演示数据。</p>`;
    }
    if (s.sheet === "history-settings") {
      titleText = "历史与数据";
      body =
        '<div class="pk-list"><div><strong>保留文字</strong><span>30 天</span></div><div><strong>保留原始音频</strong><span>关闭</span></div><div><strong>原始截图</strong><span>处理后清理</span></div></div><p>这是本次设计采用的默认值，可在真机开发时继续调整。关系事实独立保存，不跟随音频永久留存。</p>';
    }
    if (s.sheet === "sync") {
      titleText = "与豆豆同步";
      body = `<div class="pk-list"><div><strong>热词与文风</strong><span>可选择</span></div><div><strong>人物与事件</strong><span>逐人授权</span></div><div><strong>原始截图与所有击键</strong><span>不上传</span></div></div><div class="pk-form">${btn("confirm-sync", "开启选定数据同步", "确认开启演示同步")}</div><p>当前仅切换演示状态，不连接豆豆，不传输任何数据。</p>`;
    }
    if (titleText)
      body =
        `<div class="pk-sheet-head"><h3>${titleText}</h3>${ibtn("close-sheet", "close", "关闭面板")}</div>` +
        body;
    return `<div class="pk-sheet-shade"><section class="pk-sheet" role="dialog" aria-modal="true" aria-label="${esc(titleText || "人物记忆")}">${body}</section></div>`;
  }
  function render() {
    const previousScroll = screen.querySelector(".pk-app-body")?.scrollTop || 0;
    renderNav();
    renderNotes();
    const isKeyboard = ["typing", "voice", "advisor"].includes(s.page);
    screen.classList.toggle("show-guides", guides);
    screen.dataset.page = s.page;
    screen.dataset.phase = s.phase;
    screen.innerHTML =
      status() +
      (isKeyboard
        ? chat() +
          (s.page === "typing"
            ? keyboard()
            : s.page === "voice"
              ? voice()
              : advisor())
        : app()) +
      systembar() +
      sheet() +
      (s.toast
        ? `<div class="pk-toast" role="status" ${isKeyboard ? 'style="bottom:355px"' : ""}><span>${esc(s.toast)}</span>${s.undo ? btn("undo-data", "撤销", "撤销数据修改") : isKeyboard && s.previous ? btn("undo-insert", "撤销", "撤销这次填入") : ibtn("dismiss-toast", "close", "关闭提示")}</div>`
        : "") +
      '<div class="pk-measure"><span>16 px · 内容边距</span></div>';
    const appBody = screen.querySelector(".pk-app-body");
    if (appBody) appBody.scrollTop = previousScroll;
    // The background is inert while the local sheet is open; this is not a real permission panel.
    if (s.sheet) {
      for (const n of screen.children)
        if (
          !n.classList.contains("pk-sheet-shade") &&
          !n.classList.contains("pk-toast")
        )
          n.inert = true;
      screen
        .querySelector(".pk-sheet button,.pk-sheet input")
        ?.focus({ preventScroll: true });
    }
    sizePhone();
  }
  function sizePhone() {
    if (!visible) return;
    const [w, h] = el("dr-size").value.split(",").map(Number);
    const area = el("dr-phone-area");
    const frameW = w + 12,
      frameH = h + 12;
    let scale = 1;
    if (el("dr-zoom").value === "fit") {
      const heightLimit =
        window.innerWidth > 760
          ? Math.max(500, window.innerHeight - 230)
          : Infinity;
      scale = Math.min(
        1,
        (area.clientWidth - (window.innerWidth > 760 ? 32 : 16)) / frameW,
        (heightLimit - 12) / frameH,
      );
    }
    const space = el("dr-phone-space"),
      phone = el("dr-phone");
    phone.style.width = frameW + "px";
    phone.style.height = frameH + "px";
    phone.style.transform = `scale(${scale})`;
    space.style.width = frameW * scale + "px";
    space.style.height = frameH * scale + "px";
    el("dr-scale-label").textContent = Math.round(scale * 100) + "%";
    el("dr-dimensions").textContent = `${w} × ${h} CSS px`;
  }
  function beginCapture(supplement = false) {
    clearTimers();
    s.page = "advisor";
    s.phase = "capture";
    s.captureLabel = supplement ? "补读前文 · 保留当前草稿…" : "等待聊天展开…";
    s.sheet = null;
    s.toast = "";
    s.style = null;
    updateHash();
    render();
    later(() => {
      s.captureLabel = supplement ? "读取前文 · 第 1 / 2 屏…" : "读取当前屏…";
      render();
    }, 500);
    later(() => {
      s.captureLabel = "识别对话，结合人物记忆…";
      render();
    }, 1050);
    later(() => {
      s.phase = "results";
      render();
      announce("演示结果已展开：三种风格各一条，原草稿保留。");
    }, 1850);
  }
  function notice(text) {
    s.toast = text;
    s.undo = null;
    render();
  }
  function act(action, target) {
    if (action.startsWith("app-")) {
      goto(action.slice(4));
      return;
    }
    if (action === "tools") {
      s.phase = "tools";
      render();
      return;
    }
    if (action === "collapse") {
      s.phase = "collapsed";
      render();
      return;
    }
    if (action === "switch-language") {
      s.letters = !s.letters;
      s.phase = "idle";
      s.composition = "";
      render();
      return;
    }
    if (action === "punctuation") {
      setDraft(draft() + (s.letters ? "," : "，"), false);
      render();
      return;
    }
    if (action === "phrases") {
      s.sheet = "phrases";
      render();
      return;
    }
    if (action === "insert-phrase") {
      setDraft(draft() + target.dataset.text);
      s.sheet = null;
      s.phase = "idle";
      s.toast = "常用语已填入，尚未发送";
      render();
      return;
    }
    if (action === "try-keyboard" || action === "keyboard") {
      goto("typing");
      return;
    }
    if (action === "voice") {
      goto("voice");
      return;
    }
    if (action === "record") {
      clearTimers();
      s.phase = "recording";
      render();
      announce("录音界面演示，不启用真实麦克风。");
      return;
    }
    if (action === "stop-recording") {
      clearTimers();
      s.phase = "transcribing";
      render();
      later(() => {
        s.phase = "ready";
        render();
      }, 650);
      return;
    }
    if (action === "cancel-voice") {
      clearTimers();
      s.phase = "idle";
      render();
      return;
    }
    if (action === "voice-original") {
      s.historyIndex = 0;
      s.sheet = "history-detail";
      render();
      return;
    }
    if (action === "insert-voice") {
      setDraft(draft() + s.voiceText);
      s.page = "typing";
      s.phase = "idle";
      s.composition = "";
      updateHash();
      s.toast = "已填入草稿，尚未发送";
      render();
      return;
    }
    if (action === "advisor") {
      beginCapture();
      return;
    }
    if (action === "supplement") {
      beginCapture(true);
      return;
    }
    if (action === "finish-capture") {
      clearTimers();
      s.phase = "results";
      render();
      return;
    }
    if (action === "cancel-advisor") {
      goto("typing");
      return;
    }
    if (action === "more-replies") {
      s.moreStyle = Number(target.dataset.style);
      s.phase = "more";
      render();
      return;
    }
    if (action === "back-replies") {
      s.phase = "results";
      render();
      return;
    }
    if (action === "reply") {
      const i = Number(target.dataset.style);
      const v = target.dataset.variant;
      s.style = i;
      setDraft(
        v !== undefined
          ? replies()[i].more[Number(v)]
          : s.variants[i]
            ? replies()[i].more[0]
            : replies()[i].initial,
      );
      s.toast = "已填入草稿，尚未发送";
      s.undo = null;
      render();
      return;
    }
    if (action === "undo-insert") {
      if (s.previous && s.previous.contact === s.contact) {
        s.drafts[s.contact] = s.previous.text;
        s.previous = null;
        s.style = null;
        s.toast = "已恢复填入前的草稿";
      }
      render();
      return;
    }
    if (action === "key") {
      if (s.letters || s.symbols) {
        setDraft(
          draft() +
            (s.caps ? target.dataset.key.toUpperCase() : target.dataset.key),
          false,
        );
      } else {
        s.composition += target.dataset.key;
        s.phase = "composing";
      }
      render();
      return;
    }
    if (action === "candidate") {
      setDraft(draft() + target.dataset.word, false);
      s.composition = "";
      s.phase = "idle";
      render();
      return;
    }
    if (action === "space") {
      if (s.composition) {
        setDraft(draft() + candidates()[0], false);
        s.composition = "";
        s.phase = "idle";
      } else setDraft(draft() + " ", false);
      render();
      return;
    }
    if (action === "backspace") {
      if (s.composition) s.composition = s.composition.slice(0, -1);
      else setDraft([...draft()].slice(0, -1).join(""), false);
      render();
      return;
    }
    if (action === "newline") {
      setDraft(draft() + "\n", false);
      render();
      return;
    }
    if (action === "shift") {
      s.caps = !s.caps;
      render();
      return;
    }
    if (action === "symbols") {
      s.symbols = !s.symbols;
      s.composition = "";
      render();
      return;
    }
    if (action === "language") {
      s.letters = !s.letters;
      s.composition = "";
      render();
      return;
    }
    if (action === "switch-contact") {
      clearTimers();
      s.contact = target.dataset.person;
      s.page = "typing";
      s.phase = "idle";
      s.composition = "";
      s.previous = null;
      s.style = null;
      s.variants = [0, 0, 0];
      s.sheet = null;
      updateHash();
      render();
      announce("已切换演示联系人，旧任务取消，草稿按联系人保留。");
      return;
    }
    if (action === "contacts") {
      s.sheet = "contacts";
      render();
      return;
    }
    if (action === "composer-help") {
      notice("演示聊天只用于对稿，文字不会发出");
      return;
    }
    if (action === "memory-tab") {
      s.memoryTab = target.dataset.memory;
      s.page = s.memoryTab === "people" ? "people" : "dictionary";
      s.search = "";
      updateHash();
      render();
      return;
    }
    if (action === "word-filter") {
      s.filter = target.dataset.filter;
      render();
      return;
    }
    if (action === "word-search") {
      s.memoryTab = "words";
      s.searchOpen = !s.searchOpen;
      render();
      el("pk-word-search")?.focus({ preventScroll: true });
      return;
    }
    if (action === "add-word") {
      s.editWord = null;
      s.sheet = "add-word";
      render();
      return;
    }
    if (action === "word-detail") {
      s.word = target.dataset.word;
      s.sheet = "word-detail";
      render();
      return;
    }
    if (action === "edit-word") {
      s.editWord = { ...s.words.find((w) => w.text === s.word) };
      s.sheet = "edit-word";
      render();
      return;
    }
    if (action === "delete-word") {
      const removed = s.words.find((w) => w.text === s.word),
        index = s.words.indexOf(removed);
      s.words = s.words.filter((w) => w !== removed);
      s.undo = () => s.words.splice(index, 0, removed);
      s.sheet = null;
      s.toast = "已移除词条";
      render();
      return;
    }
    if (action === "person") {
      s.person = target.dataset.person;
      s.sheet = "person";
      render();
      return;
    }
    if (action === "edit-person") {
      s.sheet = "edit-person";
      render();
      return;
    }
    if (action === "close-sheet") {
      s.sheet = null;
      render();
      return;
    }
    if (action === "toggle-cloud") {
      s.cloud = !s.cloud;
      render();
      announce("已切换演示云同步，不传输数据。");
      return;
    }
    if (action === "toggle-learning") {
      s.learning = !s.learning;
      render();
      return;
    }
    if (action === "toggle-sync") {
      if (s.sync) {
        s.sync = false;
        render();
      } else {
        s.sheet = "sync";
        render();
      }
      return;
    }
    if (action === "confirm-sync") {
      s.sync = true;
      s.sheet = null;
      notice("已开启演示同步状态，未连接豆豆");
      return;
    }
    if (action === "style-edit") {
      s.sheet = "style";
      render();
      return;
    }
    if (action === "style-save") {
      s.personalStyle = el("pk-style-value").value.trim() || s.personalStyle;
      s.sheet = null;
      notice("文风保存交互已演示，刷新会恢复");
      return;
    }
    if (action === "data-settings") {
      s.sheet = "history-settings";
      render();
      return;
    }
    if (
      [
        "model-asr",
        "model-text",
        "history-settings",
        "history-detail",
      ].includes(action)
    ) {
      if (action === "history-detail")
        s.historyIndex = Number(target.dataset.index || 0);
      s.sheet = action;
      render();
      return;
    }
    if (action === "model-demo") {
      s.sheet = null;
      notice("接入与购买方式见 Spec 的模型、采购与成本");
      return;
    }
    if (action === "dismiss-toast") {
      s.toast = "";
      render();
      return;
    }
    if (action === "undo-data") {
      if (s.undo) s.undo();
      s.undo = null;
      s.toast = "已撤销";
      render();
      return;
    }
  }
  root.addEventListener("click", (e) => {
    const page = e.target.closest("[data-dr-page]");
    if (page) {
      goto(page.dataset.drPage);
      return;
    }
    const phase = e.target.closest("[data-dr-phase]");
    if (phase) {
      clearTimers();
      s.phase = phase.dataset.drPhase;
      s.composition =
        s.page === "typing" && s.phase === "composing" ? "nihao" : "";
      s.sheet = null;
      s.toast = "";
      s.style = null;
      render();
      return;
    }
    const target = e.target.closest("[data-pk-action]");
    if (target) act(target.dataset.pkAction, target);
  });
  root.addEventListener("input", (e) => {
    if (e.target.id === "pk-draft") {
      s.drafts[s.contact] = e.target.value;
      s.style = null;
      s.previous = null;
    }
    if (e.target.id === "pk-voice-text") s.voiceText = e.target.value;
    if (e.target.id === "pk-intent") s.intents[s.contact] = e.target.value;
    if (e.target.id === "pk-word-search") {
      s.search = e.target.value;
      el("pk-word-list").innerHTML = wordRows(
        s.words.filter(
          (w) =>
            (s.filter === "all" || w.kind === s.filter) &&
            w.text.toLowerCase().includes(s.search.toLowerCase()),
        ),
      );
    }
  });
  root.addEventListener("submit", (e) => {
    if (!["pk-word-form", "pk-person-form"].includes(e.target.id)) return;
    e.preventDefault();
    if (e.target.id === "pk-word-form") {
      const value = el("pk-word-value").value.trim();
      if (!value) return;
      const oldWords = s.words.map((w) => ({ ...w }));
      if (s.editWord)
        s.words = s.words.filter((w) => w.text !== s.editWord.text);
      if (!s.words.some((w) => w.text === value))
        s.words.unshift({
          text: value,
          kind: "manual",
          from: s.editWord ? "用户修订" : "手动添加",
        });
      s.undo = () => {
        s.words = oldWords;
      };
      s.sheet = null;
      s.filter = "all";
      s.search = "";
      s.toast = "词条已保存";
    } else {
      const name = s.person || "林然";
      const old = s.preferences[name];
      s.preferences[name] = el("pk-person-value").value.trim() || old;
      s.undo = () => {
        s.preferences[name] = old;
      };
      s.sheet = null;
      s.toast = "人物偏好已修订";
    }
    render();
  });
  root.addEventListener("keydown", (e) => {
    if (s.sheet) {
      if (e.key === "Escape") {
        e.preventDefault();
        s.sheet = null;
        render();
        return;
      }
      if (e.key === "Tab") {
        const nodes = [
          ...screen.querySelectorAll(
            ".pk-sheet button,.pk-sheet input,.pk-sheet textarea,.pk-sheet select",
          ),
        ].filter((n) => !n.disabled && n.getClientRects().length);
        if (!nodes.length) return;
        const first = nodes[0],
          last = nodes.at(-1);
        if (e.shiftKey && document.activeElement === first) {
          e.preventDefault();
          last.focus();
        } else if (!e.shiftKey && document.activeElement === last) {
          e.preventDefault();
          first.focus();
        }
      }
    }
  });
  el("show-design").addEventListener("click", () => activate("design"));
  el("show-world").addEventListener("click", () => activate("world"));
  el("dr-reset").addEventListener("click", () => {
    const page = s.page;
    clearTimers();
    s = fresh();
    s.page = page;
    s.phase = page === "advisor" ? "results" : "idle";
    render();
    announce("演示数据已重置。");
  });
  el("dr-guides").addEventListener("click", () => {
    guides = !guides;
    el("dr-guides").setAttribute("aria-pressed", String(guides));
    screen.classList.toggle("show-guides", guides);
  });
  ["dr-size", "dr-zoom"].forEach((id) =>
    el(id).addEventListener("change", sizePhone),
  );
  new ResizeObserver(sizePhone).observe(el("dr-phone-area"));
  window.addEventListener("resize", sizePhone);
  window.addEventListener("hashchange", () => {
    const m = location.hash.match(/^#design(?:\/([\w-]+))?$/);
    if (m) activate("design", m[1]);
  });
  document.addEventListener("visibilitychange", () => {
    if (document.hidden) {
      clearTimers();
      if (s.phase === "capture") s.phase = "error";
      if (s.phase === "transcribing") s.phase = "idle";
    } else if (visible) render();
  });
  const match = location.hash.match(/^#design(?:\/([\w-]+))?$/);
  if (match) activate("design", match[1]);
})();
