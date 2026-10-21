import {
  WebCodecsVideoDecoder,
  BitmapVideoFrameRenderer,
} from "@yume-chan/scrcpy-decoder-webcodecs";
const canvas = document.querySelector("canvas"),
  status = document.querySelector("#state");
const socketUrl = new URL(location.href);
socketUrl.protocol = location.protocol === "https:" ? "wss:" : "ws:";
socketUrl.hash = "";
socketUrl.search = "";
const ws = new WebSocket(socketUrl);
ws.binaryType = "arraybuffer";
let writer,
  decoder,
  width = 1,
  height = 1;
let frames = Promise.resolve();
ws.onmessage = (event) => {
  if (typeof event.data === "string") {
    const data = JSON.parse(event.data);
    if (data.type === "error") {
      status.textContent = data.message;
      return;
    }
    if (data.type === "metadata") {
      width = data.width;
      height = data.height;
      if (!WebCodecsVideoDecoder.isSupported) {
        status.textContent = "当前浏览器不支持视频解码，请用新版 Chrome / Edge";
        return;
      }
      decoder = new WebCodecsVideoDecoder({
        codec: data.codec,
        renderer: new BitmapVideoFrameRenderer(canvas),
      });
      decoder.sizeChanged(({ width: w, height: h }) => {
        width = w;
        height = h;
      });
      writer = decoder.writable.getWriter();
      status.textContent = "正在解码 Android 视频…";
    }
    return;
  }
  const bytes = new Uint8Array(event.data),
    view = new DataView(event.data);
  if (!writer || bytes.length < 9) return;
  const type = bytes[0],
    packet =
      type === 0
        ? { type: "configuration", data: bytes.slice(9) }
        : {
            type: "data",
            keyframe: type === 2,
            pts: view.getBigUint64(1),
            data: bytes.slice(9),
          };
  frames = frames
    .then(() => writer.write(packet))
    .then(() => {
      if (decoder.framesRendered > 0)
        status.textContent = "Android 实际画面 · 点按操作";
    })
    .catch(() => {
      status.textContent = "视频解码中断，可重新连接";
    });
};
ws.onerror = () => (status.textContent = "画面连接失败，可重新连接");
ws.onclose = () => {
  status.textContent = "画面已断开";
  decoder?.dispose();
};
const send = (value) => {
  if (ws.readyState === WebSocket.OPEN) ws.send(JSON.stringify(value));
};
function touch(event, action) {
  event.preventDefault();
  canvas.focus({ preventScroll: true });
  const rect = canvas.getBoundingClientRect();
  send({
    type: "touch",
    action,
    x: Math.round(((event.clientX - rect.left) / rect.width) * width),
    y: Math.round(((event.clientY - rect.top) / rect.height) * height),
  });
}
canvas.addEventListener("pointerdown", (event) => {
  canvas.setPointerCapture(event.pointerId);
  touch(event, 0);
});
canvas.addEventListener("pointermove", (event) => {
  if (event.buttons) touch(event, 2);
});
canvas.addEventListener("pointerup", (event) => touch(event, 1));
canvas.addEventListener("pointercancel", (event) => touch(event, 1));
canvas.addEventListener("keydown", (event) => {
  if (event.metaKey || event.ctrlKey || event.altKey) return;
  const named = {
    Backspace: 67,
    Enter: 66,
    Space: 62,
    ArrowLeft: 21,
    ArrowRight: 22,
    ArrowUp: 19,
    ArrowDown: 20,
    Escape: 4,
  };
  const code = /^Key[A-Z]$/.test(event.code)
    ? event.code.charCodeAt(3) - 65 + 29
    : /^Digit[0-9]$/.test(event.code)
      ? Number(event.code[5]) + 7
      : named[event.code];
  if (code !== undefined) {
    event.preventDefault();
    send({ type: "key", keyCode: code });
  }
});
for (const button of document.querySelectorAll("[data-key]"))
  button.onclick = () =>
    send({ type: "key", keyCode: Number(button.dataset.key) });
window.addEventListener("pagehide", () => ws.close());
