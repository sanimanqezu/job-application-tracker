// discover.mjs — figure out where a company posts its jobs, with no
// pre-configured token, by (1) reading the ATS out of its careers URL, then
// (2) probing the public ATS providers with tokens derived from its name/domain,
// then (3) trying a few generic public job endpoints on its own domain.

import { CLIENTS, fetchBoard } from "./ats.mjs";

// 1) Detect a known ATS straight from a URL (careers link or ats hint).
const URL_PATTERNS = [
  [/(?:job-boards?|boards(?:-api)?)\.greenhouse\.io\/([a-z0-9_-]+)/i, "greenhouse"],
  [/jobs\.(?:eu\.)?lever\.co\/([a-z0-9_-]+)/i, "lever"],
  [/([a-z0-9_-]+)\.recruitee\.com/i, "recruitee"],
  [/(?:careers|jobs)\.smartrecruiters\.com\/([a-z0-9_-]+)/i, "smartrecruiters"],
  [/apply\.workable\.com\/([a-z0-9_-]+)/i, "workable"],
  [/([a-z0-9_-]+)\.workable\.com/i, "workable"],
  [/jobs\.ashbyhq\.com\/([a-z0-9_-]+)/i, "ashby"],
  [/([a-z0-9_-]+)\.breezy\.hr/i, "breezy"],
];

export function detectFromUrl(url) {
  if (!url) return null;
  for (const [re, type] of URL_PATTERNS) {
    const m = url.match(re);
    if (m && m[1] && !["www", "apply", "jobs", "careers", "boards"].includes(m[1].toLowerCase())) {
      return { type, token: m[1] };
    }
  }
  return null;
}

// 2) Build candidate tokens from a company name + domain.
const STOP = new Set(["the", "pty", "ltd", "group", "sa", "africa", "software", "technologies", "technology", "solutions", "systems", "digital", "holdings", "co", "inc"]);

export function candidateTokens(company) {
  const out = new Set();
  const name = (company.name || "").toLowerCase().replace(/[^a-z0-9 ]/g, " ").trim();
  const words = name.split(/\s+/).filter((w) => w && !STOP.has(w));
  if (words.length) {
    out.add(words.join(""));       // monkeyandriver
    out.add(words.join("-"));      // monkey-and-river
    out.add(words[0]);             // monkey
  }
  // whole name compact (keeps meaningful stopwords like "and")
  const compact = name.replace(/\s+/g, "");
  if (compact) out.add(compact);
  // from domain root
  const dom = domainRoot(company);
  if (dom) { out.add(dom); out.add(dom + "careers"); out.add(dom + "-careers"); }
  return [...out].filter((t) => t && t.length >= 2).slice(0, 6);
}

export function domainRoot(company) {
  const src = company.domain || company.apply || company.careers || "";
  const m = src.match(/https?:\/\/(?:www\.)?([a-z0-9-]+)\./i);
  return m ? m[1].toLowerCase() : null;
}

// Providers to probe when we have to guess (breezy/workable 404 loudly on
// unknown tokens, which is fine — we just move on).
const PROBE_PROVIDERS = ["greenhouse", "lever", "recruitee", "smartrecruiters", "workable", "ashby", "breezy"];

// 3) Generic public endpoints some sites expose directly.
function genericEndpoints(company) {
  const dom = (company.domain || company.apply || company.careers || "").match(/https?:\/\/[^/]+/i);
  if (!dom) return [];
  const base = dom[0].replace(/\/$/, "");
  return [
    `${base}/wp-json/wp/v2/jobs?per_page=50`, // WP job plugins
    `${base}/api/jobs`,
    `${base}/jobs.json`,
    `${base}/careers.json`,
  ];
}

async function tryGeneric(url) {
  try {
    const r = await fetch(url, { headers: { Accept: "application/json" }, signal: AbortSignal.timeout(9000) });
    if (!r.ok) return [];
    const ct = r.headers.get("content-type") || "";
    if (!ct.includes("json")) return [];
    const d = await r.json();
    const arr = Array.isArray(d) ? d : d.jobs || d.data || d.positions || [];
    if (!Array.isArray(arr) || !arr.length) return [];
    return arr
      .map((j) => ({
        title: j.title?.rendered || j.title || j.name || j.position || "",
        location: j.location || j.city || "",
        url: j.link || j.url || j.apply_url || url,
        postedAt: j.date || j.published_at || null,
      }))
      .filter((j) => j.title);
  } catch {
    return [];
  }
}

// Resolve a company to a list of { source, ok, jobs } discovery results.
// mode: "known" = only use detectable/declared ATS (fast, no guessing);
//       "probe" = also guess tokens + try generic endpoints (thorough).
export async function discover(company, mode = "known") {
  const found = [];
  const seenBoards = new Set();

  const addBoard = async (type, token, label) => {
    const key = `${type}:${token}`;
    if (seenBoards.has(key)) return;
    seenBoards.add(key);
    const res = await fetchBoard({ type, token });
    if (res.ok && res.jobs.length) {
      found.push({ source: label || `${type}:${token}`, type, token, jobs: res.jobs });
    }
    return res;
  };

  // (a) declared ats config on the company record
  if (company.ats?.type && company.ats?.token) {
    await addBoard(company.ats.type, company.ats.token, `${company.ats.type} (declared)`);
  }
  // (b) detect from the careers/apply URL
  for (const u of [company.apply, company.careers, company.ats?.url]) {
    const det = detectFromUrl(u);
    if (det) await addBoard(det.type, det.token, `${det.type} (from url)`);
  }

  if (mode === "known") return found;

  // (c) probe guessed tokens across providers (stop at first hit per provider)
  const tokens = candidateTokens(company);
  for (const type of PROBE_PROVIDERS) {
    if (found.some((f) => f.type === type)) continue;
    for (const token of tokens) {
      const res = await fetchBoard({ type, token });
      if (res.ok && res.jobs.length) {
        found.push({ source: `${type}:${token} (probed)`, type, token, jobs: res.jobs });
        break;
      }
    }
  }

  // (d) generic public endpoints on the company's own domain
  for (const url of genericEndpoints(company)) {
    const jobs = await tryGeneric(url);
    if (jobs.length) found.push({ source: `generic:${url}`, type: "generic", token: null, jobs });
  }

  return found;
}
