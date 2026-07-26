// ats.mjs — clients for public applicant-tracking-system job-board APIs.
// Each client takes a { token } and returns a normalized array of jobs:
//   { title, location, url, postedAt }
// All are defensive: unknown shapes degrade to [] rather than throwing.

const UA = { "User-Agent": "sa-job-scanner/1.0 (local)", Accept: "application/json" };

async function getJSON(url) {
  const r = await fetch(url, { headers: UA, signal: AbortSignal.timeout(15000) });
  if (!r.ok) throw new Error(`HTTP ${r.status}`);
  return r.json();
}

// --- Greenhouse -----------------------------------------------------------
// boards-api.greenhouse.io/v1/boards/{token}/jobs?content=true
async function greenhouse(token) {
  const d = await getJSON(`https://boards-api.greenhouse.io/v1/boards/${token}/jobs?content=true`);
  return (d.jobs || []).map((j) => ({
    title: j.title,
    location: j.location?.name || "",
    url: j.absolute_url,
    postedAt: j.updated_at || j.first_published || null,
  }));
}

// --- Lever ---------------------------------------------------------------
// api.lever.co (or api.eu.lever.co) /v0/postings/{token}?mode=json
async function lever(token) {
  for (const host of ["api.lever.co", "api.eu.lever.co"]) {
    try {
      const d = await getJSON(`https://${host}/v0/postings/${token}?mode=json`);
      if (Array.isArray(d)) {
        return d.map((p) => ({
          title: p.text,
          location: p.categories?.location || "",
          url: p.hostedUrl || p.applyUrl,
          postedAt: p.createdAt ? new Date(p.createdAt).toISOString() : null,
        }));
      }
    } catch { /* try next host */ }
  }
  return [];
}

// --- Recruitee -----------------------------------------------------------
// {token}.recruitee.com/api/offers/
async function recruitee(token) {
  const d = await getJSON(`https://${token}.recruitee.com/api/offers/`);
  return (d.offers || []).map((o) => ({
    title: o.title,
    location: [o.city, o.country_code].filter(Boolean).join(", ") || o.location || "",
    url: o.careers_url || o.careers_apply_url,
    postedAt: o.published_at || null,
  }));
}

// --- SmartRecruiters -----------------------------------------------------
// api.smartrecruiters.com/v1/companies/{token}/postings
async function smartrecruiters(token) {
  const d = await getJSON(`https://api.smartrecruiters.com/v1/companies/${token}/postings?limit=100`);
  return (d.content || []).map((p) => ({
    title: p.name,
    location: [p.location?.city, p.location?.country].filter(Boolean).join(", "),
    url: `https://jobs.smartrecruiters.com/${token}/${p.id}`,
    postedAt: p.releasedDate || null,
  }));
}

// --- Workable ------------------------------------------------------------
// apply.workable.com/api/v1/widget/accounts/{token}?details=true
async function workable(token) {
  const d = await getJSON(`https://apply.workable.com/api/v1/widget/accounts/${token}?details=true`);
  return (d.jobs || []).map((j) => ({
    title: j.title,
    location: j.location?.location_str || [j.location?.city, j.location?.country].filter(Boolean).join(", ") || "",
    url: j.url || j.shortlink || `https://apply.workable.com/${token}/j/${j.shortcode}/`,
    postedAt: j.published_on || null,
  }));
}

// --- Ashby ---------------------------------------------------------------
// api.ashbyhq.com/posting-api/job-board/{token}
async function ashby(token) {
  const d = await getJSON(`https://api.ashbyhq.com/posting-api/job-board/${token}?includeCompensation=false`);
  return (d.jobs || []).map((j) => ({
    title: j.title,
    location: j.location || j.locationName || "",
    url: j.applyUrl || j.jobUrl,
    postedAt: j.publishedAt || null,
  }));
}

// --- Breezy --------------------------------------------------------------
// {token}.breezy.hr/json
async function breezy(token) {
  const d = await getJSON(`https://${token}.breezy.hr/json`);
  const arr = Array.isArray(d) ? d : d.positions || [];
  return arr.map((p) => ({
    title: p.name || p.title,
    location: p.location?.name || [p.location?.city, p.location?.country?.name].filter(Boolean).join(", ") || "",
    url: p.url || (p._id ? `https://${token}.breezy.hr/p/${p._id}` : `https://${token}.breezy.hr/`),
    postedAt: p.published_date || p.creation_date || null,
  }));
}

export const CLIENTS = { greenhouse, lever, recruitee, smartrecruiters, workable, ashby, breezy };

// Fetch one company's ATS board; never throws.
export async function fetchBoard({ type, token }) {
  const client = CLIENTS[type];
  if (!client) return { ok: false, error: `unknown ats: ${type}`, jobs: [] };
  try {
    const jobs = await client(token);
    return { ok: true, jobs };
  } catch (e) {
    return { ok: false, error: e.message, jobs: [] };
  }
}
