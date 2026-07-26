# SA Job Scanner

A local tool that scans South African tech employers' **applicant-tracking systems**
and shows you a table of **live openings with direct apply links** on a Home page.
Click a row → it opens that company's own application page, where you upload your CV
and submit. No LinkedIn, no Pnet, no middlemen.

Zero dependencies — it uses Node's built-in `fetch` and `http`. Node 18+ (you have 22).

## Run it

```bash
cd scanner
node scan.mjs        # fetch live jobs → writes data/jobs.json
node server.mjs      # serve the UI → http://localhost:5055
```

Open http://localhost:5055 and you'll see the **Active Apply Links** table.
Use **↻ Rescan** in the UI to refresh any time (tick **deep probe** for a wider sweep).

## How it works

- `data/companies.json` — the master directory of ~265 SA companies + recruiters
  (name, city, stack, careers URL, hiring signal). This is also your "give me every
  company" list.
- `scan.mjs` — for every company it discovers where the jobs live and pulls them:
  1. **From the careers URL** — if it already points at Greenhouse / Lever / Recruitee /
     Workable / SmartRecruiters / Breezy, the token is read straight out of the link.
  2. **Deep probe** (`--probe`) — for companies on their own domain, it *guesses* ATS
     tokens from the company name/domain and probes each provider, then tries common
     public job endpoints (`/wp-json/wp/v2/jobs`, `/api/jobs`, `/jobs.json`, `/careers.json`).
- Results are filtered to South-Africa + software roles, junior/grad flagged and sorted
  first, then written to `data/jobs.json`.
- `server.mjs` — serves the Home page and the `/api/jobs`, `/api/companies`, `/api/scan`
  endpoints.

## Commands

```bash
node scan.mjs           # fast: only ATS boards detectable from the careers URL
node scan.mjs --probe   # thorough: also guess tokens + try generic endpoints (slower)
node scan.mjs --all     # keep every job (skip the SA + dev-role filter)
```

## Supported ATS providers (public JSON)

Greenhouse · Lever · Recruitee · Workable · SmartRecruiters · Ashby · Breezy.
Companies on Workday / SuccessFactors / custom portals can't be pulled live (no public
API) — they still appear in the **Company directory** tab with a working careers link.

## Extending

Add a company to `data/companies.json`:

```json
{"name":"Acme","city":"Sandton","segment":"Small startups & product teams",
 "stack":"Java, React","size":"10-50","fit":"unk","apply":"https://acme.co.za/careers"}
```

Re-run `node scan.mjs`. If Acme uses a supported ATS, its jobs show up automatically.
