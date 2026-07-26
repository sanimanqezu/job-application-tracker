// server.mjs — tiny zero-dependency local server for the job scanner UI.
//   node server.mjs      → http://localhost:5055
// Serves the Home page (table of live apply links), the data files, and a
// /api/scan endpoint that re-runs the scan on demand.
import { createServer } from "node:http";
import { readFile, stat } from "node:fs/promises";
import { spawn } from "node:child_process";
import { fileURLToPath } from "node:url";
import { dirname, join, extname } from "node:path";

const HERE = dirname(fileURLToPath(import.meta.url));
const PORT = process.env.PORT || 5055;
const TYPES = { ".html": "text/html", ".js": "text/javascript", ".css": "text/css", ".json": "application/json", ".svg": "image/svg+xml" };

async function serveFile(res, path, type) {
  try {
    const body = await readFile(path);
    res.writeHead(200, { "Content-Type": type });
    res.end(body);
  } catch {
    res.writeHead(404, { "Content-Type": "application/json" });
    res.end(JSON.stringify({ error: "not found", path }));
  }
}

function runScan(probe) {
  return new Promise((resolve) => {
    const args = ["scan.mjs"];
    if (probe) args.push("--probe");
    const child = spawn("node", args, { cwd: HERE });
    let log = "";
    child.stdout.on("data", (d) => (log += d));
    child.stderr.on("data", (d) => (log += d));
    child.on("close", (code) => resolve({ code, log }));
  });
}

const server = createServer(async (req, res) => {
  const url = new URL(req.url, `http://localhost:${PORT}`);
  const path = url.pathname;

  if (path === "/" || path === "/index.html") {
    return serveFile(res, join(HERE, "public", "index.html"), "text/html");
  }
  if (path === "/api/jobs") {
    try { await stat(join(HERE, "data", "jobs.json")); }
    catch { res.writeHead(200, { "Content-Type": "application/json" }); return res.end(JSON.stringify({ jobs: [], jobsKept: 0, scannedAt: null })); }
    return serveFile(res, join(HERE, "data", "jobs.json"), "application/json");
  }
  if (path === "/api/companies") {
    return serveFile(res, join(HERE, "data", "companies.json"), "application/json");
  }
  if (path === "/api/scan") {
    const probe = url.searchParams.get("probe") === "1";
    const { code, log } = await runScan(probe);
    res.writeHead(200, { "Content-Type": "application/json" });
    return res.end(JSON.stringify({ ok: code === 0, log }));
  }
  // static assets under /public
  if (path.startsWith("/public/")) {
    return serveFile(res, join(HERE, path), TYPES[extname(path)] || "application/octet-stream");
  }
  res.writeHead(404); res.end("Not found");
});

server.listen(PORT, () => {
  console.log(`\n  SA Job Scanner running →  http://localhost:${PORT}\n`);
  console.log(`  (data comes from data/jobs.json — run "node scan.mjs" first, or click Rescan in the UI)\n`);
});
