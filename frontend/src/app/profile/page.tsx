"use client";

import { useCallback, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import Navbar from "@/components/Navbar";
import { profileApi } from "@/lib/api";
import { useAuthStore } from "@/store/applicationStore";
import CvUpload from "@/components/CvUpload";
import type { Profile } from "@/types";

export default function ProfilePage() {
  const router = useRouter();
  const { token } = useAuthStore();

  const [profile, setProfile] = useState<Profile | null>(null);
  const [desiredJob, setDesiredJob] = useState("");
  const [years, setYears] = useState(2);
  const [cv, setCv] = useState("");
  const [cvFileName, setCvFileName] = useState("");
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState<{ ok: boolean; text: string } | null>(null);

  useEffect(() => {
    if (!token) {
      router.push("/login");
      return;
    }
    profileApi
      .get()
      .then((r) => {
        setProfile(r.data);
        setDesiredJob(r.data.desiredJob ?? "");
        setYears(r.data.yearsExperience ?? 2);
        setCv(r.data.cv ?? "");
        setCvFileName(r.data.cvFileName ?? "");
      })
      .catch(() => setMessage({ ok: false, text: "Could not load your profile." }))
      .finally(() => setLoading(false));
  }, [token, router]);

  const save = useCallback(
    async (e: React.FormEvent) => {
      e.preventDefault();
      setMessage(null);
      if (!desiredJob.trim()) {
        setMessage({ ok: false, text: "Tell us the job you're looking for." });
        return;
      }
      if (cv.trim().length < 10) {
        setMessage({ ok: false, text: "Please paste your CV (at least a few lines)." });
        return;
      }
      setSaving(true);
      try {
        const r = await profileApi.update({
          desiredJob: desiredJob.trim(),
          cv: cv.trim(),
          cvFileName,
          yearsExperience: years,
        });
        setProfile(r.data);
        setCvFileName(r.data.cvFileName ?? "");
        setMessage({ ok: true, text: "Profile saved. Your Discover matches will use it." });
      } catch {
        setMessage({ ok: false, text: "Could not save. Please try again." });
      } finally {
        setSaving(false);
      }
    },
    [desiredJob, cv, cvFileName, years]
  );

  const inputClass =
    "w-full px-3.5 py-2.5 border border-gray-300 rounded-lg text-gray-900 placeholder-gray-400 focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-transparent transition";
  const labelClass = "block text-sm font-medium text-gray-700 mb-1.5";

  return (
    <>
      <Navbar />
      <main className="max-w-3xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
        <div className="mb-6">
          <h1 className="text-2xl font-bold text-gray-900">Your profile</h1>
          <p className="text-gray-500 mt-1">
            Your CV, desired job, and experience drive how jobs are ranked for you on{" "}
            <span className="font-semibold text-gray-700">Discover</span>.
          </p>
        </div>

        {message && (
          <div
            className={`mb-4 p-3 rounded-lg text-sm border ${
              message.ok
                ? "bg-green-50 border-green-200 text-green-800"
                : "bg-red-50 border-red-200 text-red-700"
            }`}
          >
            {message.text}
          </div>
        )}

        {loading ? (
          <div className="flex items-center justify-center h-56">
            <div className="animate-spin w-8 h-8 border-4 border-indigo-600 border-t-transparent rounded-full" />
          </div>
        ) : (
          <form onSubmit={save} className="bg-white rounded-2xl border border-gray-200 p-6 sm:p-8 space-y-5">
            {profile && (
              <div className="flex flex-wrap gap-x-8 gap-y-2 pb-5 border-b border-gray-100 text-sm">
                <div>
                  <div className="text-xs uppercase tracking-wide text-gray-400">Username</div>
                  <div className="font-medium text-gray-900">{profile.username}</div>
                </div>
                <div>
                  <div className="text-xs uppercase tracking-wide text-gray-400">Email</div>
                  <div className="font-medium text-gray-900">{profile.email}</div>
                </div>
                <div>
                  <div className="text-xs uppercase tracking-wide text-gray-400">Estimated category</div>
                  <div className="font-medium text-indigo-700">{profile.jobCategory ?? "—"}</div>
                </div>
              </div>
            )}

            <div>
              <label htmlFor="desiredJob" className={labelClass}>
                Job you&apos;re looking for
              </label>
              <input
                id="desiredJob"
                type="text"
                maxLength={150}
                value={desiredJob}
                onChange={(e) => setDesiredJob(e.target.value)}
                className={inputClass}
                placeholder="e.g. Registered Nurse, Software Developer, Electrician"
              />
              <p className="text-xs text-gray-400 mt-1">
                We re-estimate your category from this when you save.
              </p>
            </div>

            <div>
              <label htmlFor="years" className={labelClass}>
                Years of experience in that job
              </label>
              <input
                id="years"
                type="number"
                min={0}
                max={60}
                step={0.5}
                value={years}
                onChange={(e) => setYears(e.target.value === "" ? 0 : Number(e.target.value))}
                className={inputClass}
                placeholder="e.g. 2.5"
              />
            </div>

            <div>
              <label className={labelClass}>Your CV</label>
              <CvUpload
                value={cv}
                fileName={cvFileName}
                onChange={(t, name) => {
                  setCv(t);
                  setCvFileName(name);
                }}
              />
              {cv.trim().length > 0 && (
                <p className="text-xs text-gray-400 mt-1">
                  {cv.trim().length} characters of text on file
                </p>
              )}
            </div>

            <div className="flex justify-end">
              <button
                type="submit"
                disabled={saving}
                className="px-5 py-2.5 bg-indigo-600 hover:bg-indigo-700 disabled:bg-indigo-400 text-white font-semibold rounded-lg transition-colors"
              >
                {saving ? "Saving…" : "Save profile"}
              </button>
            </div>
          </form>
        )}
      </main>
    </>
  );
}
