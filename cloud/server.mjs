import http from "node:http";
import fs from "node:fs/promises";
import { fileURLToPath } from "node:url";
import path from "node:path";

const catalog = JSON.parse(
  await fs.readFile(new URL("./config/plans.json", import.meta.url), "utf8"),
);
export function createCloudServer() {
  return http.createServer((req, res) => {
    const route = new URL(req.url, "http://cloud.local").pathname;
    res.setHeader("Content-Type", "application/json; charset=utf-8");
    res.setHeader("X-Content-Type-Options", "nosniff");
    res.setHeader("Cache-Control", "no-store");
    if (req.method === "GET" && route === "/healthz") {
      res.end(
        JSON.stringify({
          status: "ok",
          productionReady: false,
          pending: [
            "identity-provider",
            "transactional-database",
            "model-proxy",
            "payments",
            "managed-storage",
            "admin-console",
          ],
        }),
      );
    } else if (req.method === "GET" && route === "/v1/catalog") {
      res.end(
        JSON.stringify({
          plans: catalog.plans.filter((p) => p.status === "published"),
          metering: catalog.metering,
        }),
      );
    } else {
      res.statusCode = 404;
      res.end(
        JSON.stringify({
          error: "NOT_IMPLEMENTED",
          message: "商业 API 以 contracts/http.yaml 为目标契约，当前未上线。",
        }),
      );
    }
  });
}
if (
  process.argv[1] &&
  path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)
) {
  createCloudServer().listen(
    Number(process.env.PORT || 8080),
    process.env.HOST || "127.0.0.1",
    () =>
      console.log(
        "Cloud contract scaffold ready; commercial endpoints are not enabled.",
      ),
  );
}
