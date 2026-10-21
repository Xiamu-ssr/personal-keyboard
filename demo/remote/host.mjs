import fs from "node:fs/promises";
import path from "node:path";
import { spawn } from "node:child_process";
import { root } from "../tools.mjs";
import { createRemoteDemo } from "./server.mjs";
const config = JSON.parse(
  await fs.readFile(
    path.join(root, ".runtime/remote-demo/config.json"),
    "utf8",
  ),
);
const app = createRemoteDemo(config);
app.server.listen(8562, "127.0.0.1");
let tunnel = null,
  retry = null,
  closing = false;
function connect() {
  tunnel = spawn(
    "/usr/bin/ssh",
    [
      "-N",
      "-T",
      "-o",
      "BatchMode=yes",
      "-o",
      "ExitOnForwardFailure=yes",
      "-o",
      "ServerAliveInterval=30",
      "-o",
      "ServerAliveCountMax=3",
      "-R",
      "127.0.0.1:18562:127.0.0.1:8562",
      config.sshHost,
    ],
    { stdio: "ignore" },
  );
  tunnel.once("error", () => {});
  tunnel.once("exit", () => {
    if (!closing) retry = setTimeout(connect, 30_000);
  });
}
connect();
async function close() {
  if (closing) return;
  closing = true;
  clearTimeout(retry);
  tunnel?.kill("SIGTERM");
  await app.close();
  process.exit(0);
}
process.once("SIGINT", close);
process.once("SIGTERM", close);
