"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import Navbar from "@/components/Navbar";
import { discoverApi } from "@/lib/api";
import { useAuthStore } from "@/store/applicationStore";
import type { ScannedJob, DiscoverStats, ScanSummary, HarvestStatus } from "@/types";

function matchColor(score: number): string {
  if (score >= 65) return "bg-green-100 text-green-800";
  if (score >= 45) return "bg-lime-100 text-lime-800";
  if (score >= 25) return "bg-amber-100 text-amber-800";
  return "bg-gray-100 text-gray-500";
}

function hostOf(url: string): string {
  try {
    return new URL(url).hostname.replace(/^www\./, "");
  } catch {
    return "";
  }
}

export default function DiscoverPage() {
  const router = useRouter();
  const { token } = useAuthStore();

  const [jobs, setJobs] = useState<ScannedJob[]>([]);
  const [stats, setStats] = useState<DiscoverStats | null>(null);
  const [loading, setLoading] = useState(false);
  const [scanning, setScanning] = useState(false);
  const [deep, setDeep] = useState(false);
  const [message, setMessage] = useState<string | null>(null);

  const [q, setQ] = useState("");
  const [segment, setSegment] = useState("");
  const [juniorOnly, setJuniorOnly] = useState(false);
  const [minScore, setMinScore] = useState(0);
  const [harvest, setHarvestState] = useState<HarvestStatus | null>(null);

  const loadJobs = useCallback(async () => {
    setLoading(true);
    try {
      const [jobsRes, statsRes] = await Promise.all([
        discoverApi.list({
          onlyNew: true, // always hide links you've already applied to
          q: q || undefined,
          junior: juniorOnly || undefined,
          segment: segment || undefined,
          minScore: minScore || undefined,
        }),
        discoverApi.stats(),
      ]);
      setJobs(jobsRes.data);
      setStats(statsRes.data);
    } catch {
      setMessage("Could not load jobs. Is the backend running?");
    } finally {
      setLoading(false);
    }
  }, [q, juniorOnly, segment, minScore]);

  useEffect(() => {
    if (!token) {
      router.push("/login");
      return;
    }
    loadJobs();
  }, [token, router, loadJobs]);

  // Poll the background harvester's progress (also shows the company count climbing).
  useEffect(() => {
    let alive = true;
    const load = () =>
      discoverApi.harvest().then((r) => { if (alive) setHarvestState(r.data); }).catch(() => {});
    load();
    const t = setInterval(load, 10000);
    return () => { alive = false; clearInterval(t); };
  }, []);

  const rescan = useCallback(async () => {
    setScanning(true);
    setMessage(deep ? "Deep scan running — probing every company's ATS. This takes a minute…" : "Scanning…");
    try {
      const res = await discoverApi.scan(deep);
      const s: ScanSummary = res.data;
      setMessage(
        `Scan complete — ${s.boardsWithOpenings} boards, ${s.jobsDiscovered} openings found, ${s.newlyAdded} added.`
      );
      await loadJobs();
    } catch {
      setMessage("Scan failed. Check the backend logs.");
    } finally {
      setScanning(false);
    }
  }, [deep, loadJobs]);

  const applyTo = useCallback(async (job: ScannedJob) => {
    // Open the company's application page immediately (avoids popup blockers),
    // then record it so it drops out of "new" and lands on the board.
    window.open(job.url, "_blank", "noopener");
    try {
      await discoverApi.apply(job.id);
      setJobs((prev) => prev.filter((j) => j.id !== job.id));
      setStats((prev) => (prev ? { ...prev, newForYou: Math.max(0, prev.newForYou - 1) } : prev));
    } catch {
      setMessage("Opened the link, but couldn't record it. It may already be in your applications.");
    }
  }, []);

  const segments = useMemo(
    () => Array.from(new Set(jobs.map((j) => j.segment).filter(Boolean))) as string[],
    [jobs]
  );

  return (
    <>
      <Navbar />
      <main className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
        {/* Header */}
        <div className="flex flex-col lg:flex-row lg:items-end lg:justify-between gap-4 mb-6">
          <div>
            <h1 className="text-2xl font-bold text-gray-900">Discover Jobs</h1>
            <p className="text-gray-500 mt-1">
              Openings ranked by how well they fit <span className="font-semibold text-gray-700">your CV</span> (read from the job description).
              Senior roles, roles needing more than 2 years, and matches under 15% are filtered out. Click{" "}
              <span className="font-semibold text-gray-700">Apply</span> to open the company&apos;s page and add it to your board.
            </p>
            {harvest && (
              <p className="text-xs text-gray-400 mt-1">
                <span className={harvest.enabled ? "text-green-600" : "text-gray-400"}>
                  ● Harvester {harvest.enabled ? "running" : "paused"}
                </span>{" "}
                · scanned {harvest.companiesScanned}/{harvest.companiesTotal} companies · {harvest.jobsTotal} jobs collected
              </p>
            )}
          </div>
          <div className="flex items-center gap-4">
            {stats && (
              <div className="flex gap-5 text-right">
                <div>
                  <div className="text-2xl font-bold text-indigo-600 tabular-nums">{stats.newForYou}</div>
                  <div className="text-xs uppercase tracking-wide text-gray-400">New for you</div>
                </div>
                <div>
                  <div className="text-2xl font-bold text-gray-900 tabular-nums">{stats.companies}</div>
                  <div className="text-xs uppercase tracking-wide text-gray-400">Companies</div>
                </div>
              </div>
            )}
            <div className="flex flex-col items-end gap-1">
              <button
                onClick={rescan}
                disabled={scanning}
                className="px-4 py-2 bg-indigo-600 text-white text-sm font-semibold rounded-lg hover:bg-indigo-700 transition-colors disabled:opacity-50 flex items-center gap-2"
              >
                {scanning && <span className="animate-spin w-3.5 h-3.5 border-2 border-white border-t-transparent rounded-full" />}
                {scanning ? "Scanning…" : "↻ Rescan live"}
              </button>
              <label className="flex items-center gap-1.5 text-xs text-gray-500 cursor-pointer">
                <input type="checkbox" checked={deep} onChange={(e) => setDeep(e.target.checked)} />
                deep probe (slower, finds more)
              </label>
            </div>
          </div>
        </div>

        {/* Toolbar */}
        <div className="flex flex-wrap items-center gap-3 mb-4">
          <input
            type="search"
            value={q}
            onChange={(e) => setQ(e.target.value)}
            placeholder="Search role, company, or stack — “java”, “react”, “spring”…"
            className="flex-1 min-w-[240px] px-3 py-2 border border-gray-300 rounded-lg text-sm bg-white text-gray-700 focus:outline-none focus:ring-2 focus:ring-indigo-500"
          />
          <select
            value={segment}
            onChange={(e) => setSegment(e.target.value)}
            className="px-3 py-2 border border-gray-300 rounded-lg text-sm bg-white text-gray-700 focus:outline-none focus:ring-2 focus:ring-indigo-500"
          >
            <option value="">All segments</option>
            {segments.map((s) => (
              <option key={s} value={s}>
                {s}
              </option>
            ))}
          </select>
          <select
            value={minScore}
            onChange={(e) => setMinScore(Number(e.target.value))}
            className="px-3 py-2 border border-gray-300 rounded-lg text-sm bg-white text-gray-700 focus:outline-none focus:ring-2 focus:ring-indigo-500"
          >
            <option value={0}>Any match</option>
            <option value={25}>25%+ match</option>
            <option value={45}>45%+ match</option>
            <option value={65}>65%+ match (best fit)</option>
          </select>
          <label className="flex items-center gap-1.5 text-sm text-gray-600 cursor-pointer">
            <input type="checkbox" checked={juniorOnly} onChange={(e) => setJuniorOnly(e.target.checked)} />
            Junior / grad only
          </label>
          <span className="text-sm text-gray-400 tabular-nums">{jobs.length} openings · applied ones hidden</span>
        </div>

        {message && (
          <div className="p-3 bg-indigo-50 border border-indigo-200 rounded-lg text-indigo-800 text-sm mb-4">
            {message}
          </div>
        )}

        {/* Live openings table */}
        {loading ? (
          <div className="flex items-center justify-center h-56">
            <div className="animate-spin w-8 h-8 border-4 border-indigo-600 border-t-transparent rounded-full" />
          </div>
        ) : jobs.length === 0 ? (
          <div className="bg-white rounded-2xl border border-gray-200 p-14 text-center">
            <p className="text-gray-500 font-medium">No matching openings right now.</p>
            <p className="text-gray-400 text-sm mt-1">
              The harvester is still working through the companies. Hit{" "}
              <span className="font-semibold">↻ Rescan live</span> (tick <span className="font-semibold">deep probe</span> for a wider sweep),
              or loosen the filters above. Genuinely junior roles requiring ≤2 years are rare, so this list stays selective.
            </p>
          </div>
        ) : (
          <div className="bg-white rounded-2xl border border-gray-200 overflow-hidden">
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="bg-gray-50 border-b border-gray-200">
                    <th className="px-4 py-3 text-left font-semibold text-gray-600 w-20">Apply</th>
                    <th className="px-4 py-3 text-left font-semibold text-gray-600 w-16">Match</th>
                    <th className="px-4 py-3 text-left font-semibold text-gray-600">Role &amp; matched skills</th>
                    <th className="px-4 py-3 text-left font-semibold text-gray-600">Company</th>
                    <th className="px-4 py-3 text-left font-semibold text-gray-600 hidden md:table-cell">Location</th>
                    <th className="px-4 py-3 text-left font-semibold text-gray-600">Level</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-100">
                  {jobs.map((j) => (
                    <tr key={j.id} className="hover:bg-indigo-50/40 transition-colors">
                      <td className="px-4 py-3">
                        <button
                          onClick={() => applyTo(j)}
                          className="px-3 py-1.5 bg-indigo-600 text-white text-xs font-semibold rounded-md hover:bg-indigo-700 transition-colors whitespace-nowrap"
                        >
                          Apply →
                        </button>
                      </td>
                      <td className="px-4 py-3">
                        <span className={`text-xs font-bold px-2 py-1 rounded-md tabular-nums ${matchColor(j.matchScore)}`}>
                          {j.matchScore}%
                        </span>
                      </td>
                      <td className="px-4 py-3">
                        <div className="font-medium text-gray-900">{j.title}</div>
                        {j.matchedSkills && (
                          <div className="text-xs text-indigo-600 mt-0.5">{j.matchedSkills}</div>
                        )}
                        <div className="text-xs text-gray-400">{hostOf(j.url)}</div>
                      </td>
                      <td className="px-4 py-3 text-gray-600">{j.company}</td>
                      <td className="px-4 py-3 text-gray-500 hidden md:table-cell">{j.location || j.city || "—"}</td>
                      <td className="px-4 py-3">
                        {j.junior ? (
                          <span className="text-xs font-semibold px-2 py-0.5 rounded-full bg-green-100 text-green-800">Junior</span>
                        ) : (
                          <span className="text-gray-300">—</span>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        )}
      </main>
    </>
  );
}
