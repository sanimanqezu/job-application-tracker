// scan.mjs — run the discovery over every company and write data/jobs.json.
//
//   node scan.mjs            # fast: only known/declared/detectable ATS boards
//   node scan.mjs --probe    # thorough: also guess tokens + probe generic endpoints
//   node scan.mjs --all      # keep every job (skip the SA + dev-role filter)
//   node scan.mjs --probe --all
//
import { readFile, writeFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";
import { discover } from "./lib/discover.mjs";

const HERE = dirname(fileURLToPath(import.meta.url));
const args = new Set(process.argv.slice(2));
const MODE = args.has("--probe") ? "probe" : "known";
const KEEP_ALL = args.has("--all");
const CONCURRENCY = MODE === "probe" ? 6 : 10;

// --- filters --------------------------------------------------------------
const SA = /(south africa|johannesburg|pretoria|cape town|durban|sandton|centurion|midrand|rosebank|bryanston|randburg|roodepoort|gauteng|tshwane|\bza\b|remote)/i;
// A real software role: must name an engineering discipline or a concrete tech.
const DEV = /(developer|engineer(ing)?|software|full[\s-]?stack|back[\s-]?end|front[\s-]?end|web dev|\bjava\b|\.net|\bc#\b|react|node|python|golang|\bgo\b|typescript|\bqa\b|test automation|programmer|devops|\bsre\b|mobile|android|\bios\b|data engineer|data scien|machine learning|\bml\b|cloud engineer)/i;
// Seniority signal — word-bounded so "intern" doesn't match "International".
const JUNIOR = /\b(junior|jnr|graduate|grad|intern|internship|entry[- ]?level|trainee|associate|0[\s-]?2\s*years?)\b/i;

const norm = (s) => (s || "").toLowerCase().replace(/\s+/g, " ").trim();

// --- tiny concurrency pool ------------------------------------------------
async function pool(items, size, worker) {
  const results = [];
  let i = 0;
  const runners = Array.from({ length: size }, async () => {
    while (i < items.length) {
      const idx = i++;
      results[idx] = await worker(items[idx], idx);
    }
  });
  await Promise.all(runners);
  return results;
}

async function main() {
  const companies = JSON.parse(await readFile(join(HERE, "data", "companies.json"), "utf8"));
  const withDomain = companies.filter((c) => c.apply || c.careers || c.domain);
  console.log(`Scanning ${withDomain.length} companies  [mode=${MODE}${KEEP_ALL ? " keep-all" : ""}]…\n`);

  let scanned = 0;
  const allJobs = [];
  const boardsHit = [];

  await pool(withDomain, CONCURRENCY, async (c) => {
    const boards = await discover(c, MODE);
    scanned++;
    if (boards.length) {
      const total = boards.reduce((n, b) => n + b.jobs.length, 0);
      boardsHit.push({ company: c.name, source: boards.map((b) => b.source).join(", "), count: total });
      process.stdout.write(`  ✓ ${c.name.padEnd(28)} ${total} open  (${boards.map((b) => b.source).join(", ")})\n`);
      for (const b of boards) {
        for (const j of b.jobs) {
          allJobs.push({
            company: c.name,
            city: c.city,
            segment: c.segment,
            stack: c.stack,
            fit: c.fit,
            title: j.title,
            location: j.location,
            url: j.url,
            postedAt: j.postedAt,
            junior: JUNIOR.test(j.title),
            source: b.source,
          });
        }
      }
    }
    if (scanned % 25 === 0) process.stdout.write(`  … ${scanned}/${withDomain.length} scanned\n`);
  });

  // dedupe by company+title+url
  const seen = new Set();
  let jobs = allJobs.filter((j) => {
    const k = `${norm(j.company)}|${norm(j.title)}|${j.url}`;
    if (seen.has(k)) return false;
    seen.add(k);
    return true;
  });

  const rawCount = jobs.length;
  if (!KEEP_ALL) {
    jobs = jobs.filter((j) => {
      const loc = `${j.location} ${j.company}`;
      return SA.test(loc) && DEV.test(j.title);
    });
  }

  // sort: junior first, then most recent
  jobs.sort((a, b) => (b.junior - a.junior) || (new Date(b.postedAt || 0) - new Date(a.postedAt || 0)));

  const out = {
    scannedAt: new Date().toISOString(),
    mode: MODE,
    companiesScanned: withDomain.length,
    boardsWithOpenings: boardsHit.length,
    jobsFound: rawCount,
    jobsKept: jobs.length,
    juniorJobs: jobs.filter((j) => j.junior).length,
    jobs,
  };
  await writeFile(join(HERE, "data", "jobs.json"), JSON.stringify(out, null, 2));

  console.log(`\n─────────────────────────────────────────────`);
  console.log(`Boards with openings : ${boardsHit.length}`);
  console.log(`Jobs discovered      : ${rawCount}`);
  console.log(`Jobs kept (SA + dev) : ${jobs.length}   (${out.juniorJobs} junior/grad)`);
  console.log(`Written              : data/jobs.json`);
  console.log(`Run the UI           : node server.mjs   → http://localhost:5055`);
}

main().catch((e) => { console.error(e); process.exit(1); });
