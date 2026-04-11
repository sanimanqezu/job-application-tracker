"use client";

import { useEffect, useState, useCallback } from "react";
import { useParams, useRouter } from "next/navigation";
import Link from "next/link";
import Navbar from "@/components/Navbar";
import StatusBadge from "@/components/StatusBadge";
import { applicationsApi, interviewsApi } from "@/lib/api";
import { useAuthStore } from "@/store/applicationStore";
import type {
  JobApplication,
  Interview,
  InterviewType,
  ApplicationStatus,
} from "@/types";
import { ALL_STATUSES, STATUS_LABELS } from "@/types";

const INTERVIEW_TYPE_LABELS: Record<InterviewType, string> = {
  PHONE: "Phone Call",
  VIDEO: "Video Call",
  ON_SITE: "On-Site",
  TECHNICAL: "Technical",
};

const INTERVIEW_TYPES: InterviewType[] = ["PHONE", "VIDEO", "ON_SITE", "TECHNICAL"];

export default function ApplicationDetailPage() {
  const { id } = useParams<{ id: string }>();
  const router = useRouter();
  const { token } = useAuthStore();

  const [app, setApp] = useState<JobApplication | null>(null);
  const [interviews, setInterviews] = useState<Interview[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  // Edit mode
  const [editing, setEditing] = useState(false);
  const [editForm, setEditForm] = useState<Partial<JobApplication>>({});
  const [saving, setSaving] = useState(false);

  // Add interview form
  const [showInterviewForm, setShowInterviewForm] = useState(false);
  const [interviewForm, setInterviewForm] = useState({
    interviewDate: "",
    type: "VIDEO" as InterviewType,
    notes: "",
    completed: false,
  });
  const [addingInterview, setAddingInterview] = useState(false);

  const appId = parseInt(id, 10);

  useEffect(() => {
    if (!token) {
      router.push("/login");
      return;
    }
    Promise.all([
      applicationsApi.getById(appId),
      applicationsApi.getInterviews(appId),
    ])
      .then(([appRes, interviewRes]) => {
        setApp(appRes.data);
        setEditForm(appRes.data);
        setInterviews(interviewRes.data);
      })
      .catch(() => setError("Failed to load application"))
      .finally(() => setLoading(false));
  }, [appId, token, router]);

  const handleSave = async () => {
    if (!app) return;
    setSaving(true);
    try {
      const res = await applicationsApi.update(appId, {
        company: editForm.company ?? app.company,
        role: editForm.role ?? app.role,
        location: editForm.location ?? "",
        jobUrl: editForm.jobUrl ?? "",
        status: (editForm.status ?? app.status) as ApplicationStatus,
        appliedDate: editForm.appliedDate ?? app.appliedDate,
        salaryRange: editForm.salaryRange ?? "",
        notes: editForm.notes ?? "",
      });
      setApp(res.data);
      setEditForm(res.data);
      setEditing(false);
    } catch {
      alert("Failed to save changes");
    } finally {
      setSaving(false);
    }
  };

  const handleAddInterview = async (e: React.FormEvent) => {
    e.preventDefault();
    setAddingInterview(true);
    try {
      const res = await applicationsApi.addInterview(appId, {
        interviewDate: new Date(interviewForm.interviewDate).toISOString(),
        type: interviewForm.type,
        notes: interviewForm.notes,
        completed: interviewForm.completed,
      });
      setInterviews((prev) => [...prev, res.data]);
      setShowInterviewForm(false);
      setInterviewForm({ interviewDate: "", type: "VIDEO", notes: "", completed: false });
    } catch {
      alert("Failed to add interview");
    } finally {
      setAddingInterview(false);
    }
  };

  const handleDeleteInterview = useCallback(async (interviewId: number) => {
    if (!confirm("Delete this interview?")) return;
    try {
      await interviewsApi.delete(interviewId);
      setInterviews((prev) => prev.filter((i) => i.id !== interviewId));
    } catch {
      alert("Failed to delete interview");
    }
  }, []);

  if (loading) {
    return (
      <>
        <Navbar />
        <div className="flex items-center justify-center h-96">
          <div className="animate-spin w-8 h-8 border-4 border-indigo-600 border-t-transparent rounded-full" />
        </div>
      </>
    );
  }

  if (error || !app) {
    return (
      <>
        <Navbar />
        <div className="max-w-4xl mx-auto px-4 py-8">
          <div className="p-4 bg-red-50 border border-red-200 rounded-xl text-red-700">
            {error || "Application not found"}
          </div>
        </div>
      </>
    );
  }

  return (
    <>
      <Navbar />
      <main className="max-w-4xl mx-auto px-4 sm:px-6 py-8 space-y-6">
        {/* Header */}
        <div className="flex items-start gap-4">
          <Link
            href="/applications"
            className="mt-1 text-gray-400 hover:text-gray-600 transition-colors"
          >
            <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M15 19l-7-7 7-7" />
            </svg>
          </Link>
          <div className="flex-1">
            <div className="flex items-center gap-3 flex-wrap">
              <h1 className="text-2xl font-bold text-gray-900">{app.company}</h1>
              <StatusBadge status={app.status} />
            </div>
            <p className="text-gray-500 mt-1">{app.role}</p>
          </div>
          <button
            onClick={() => setEditing(!editing)}
            className="px-4 py-2 text-sm font-medium border border-gray-300 text-gray-700 rounded-lg hover:bg-gray-50 transition-colors"
          >
            {editing ? "Cancel" : "Edit"}
          </button>
        </div>

        {/* Application Details Card */}
        <div className="bg-white rounded-2xl border border-gray-200 p-6">
          <h2 className="font-semibold text-gray-900 mb-5">Application Details</h2>

          {editing ? (
            <div className="space-y-4">
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <div>
                  <label className="block text-xs font-medium text-gray-500 mb-1">Company</label>
                  <input
                    value={editForm.company ?? ""}
                    onChange={(e) => setEditForm((p) => ({ ...p, company: e.target.value }))}
                    className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
                  />
                </div>
                <div>
                  <label className="block text-xs font-medium text-gray-500 mb-1">Role</label>
                  <input
                    value={editForm.role ?? ""}
                    onChange={(e) => setEditForm((p) => ({ ...p, role: e.target.value }))}
                    className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
                  />
                </div>
                <div>
                  <label className="block text-xs font-medium text-gray-500 mb-1">Location</label>
                  <input
                    value={editForm.location ?? ""}
                    onChange={(e) => setEditForm((p) => ({ ...p, location: e.target.value }))}
                    className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
                  />
                </div>
                <div>
                  <label className="block text-xs font-medium text-gray-500 mb-1">Status</label>
                  <select
                    value={editForm.status ?? "APPLIED"}
                    onChange={(e) => setEditForm((p) => ({ ...p, status: e.target.value as ApplicationStatus }))}
                    className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500 bg-white"
                  >
                    {ALL_STATUSES.map((s) => (
                      <option key={s} value={s}>{STATUS_LABELS[s]}</option>
                    ))}
                  </select>
                </div>
                <div>
                  <label className="block text-xs font-medium text-gray-500 mb-1">Applied Date</label>
                  <input
                    type="date"
                    value={editForm.appliedDate ?? ""}
                    onChange={(e) => setEditForm((p) => ({ ...p, appliedDate: e.target.value }))}
                    className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
                  />
                </div>
                <div>
                  <label className="block text-xs font-medium text-gray-500 mb-1">Salary Range</label>
                  <input
                    value={editForm.salaryRange ?? ""}
                    onChange={(e) => setEditForm((p) => ({ ...p, salaryRange: e.target.value }))}
                    className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
                  />
                </div>
              </div>
              <div>
                <label className="block text-xs font-medium text-gray-500 mb-1">Job URL</label>
                <input
                  value={editForm.jobUrl ?? ""}
                  onChange={(e) => setEditForm((p) => ({ ...p, jobUrl: e.target.value }))}
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
                  placeholder="https://..."
                />
              </div>
              <div>
                <label className="block text-xs font-medium text-gray-500 mb-1">Notes</label>
                <textarea
                  rows={4}
                  value={editForm.notes ?? ""}
                  onChange={(e) => setEditForm((p) => ({ ...p, notes: e.target.value }))}
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500 resize-none"
                />
              </div>
              <div className="flex gap-3">
                <button
                  onClick={handleSave}
                  disabled={saving}
                  className="px-5 py-2 bg-indigo-600 hover:bg-indigo-700 disabled:bg-indigo-400 text-white text-sm font-semibold rounded-lg transition-colors"
                >
                  {saving ? "Saving…" : "Save Changes"}
                </button>
                <button
                  onClick={() => { setEditing(false); setEditForm(app); }}
                  className="px-5 py-2 border border-gray-300 text-gray-700 text-sm font-semibold rounded-lg hover:bg-gray-50 transition-colors"
                >
                  Cancel
                </button>
              </div>
            </div>
          ) : (
            <dl className="grid grid-cols-1 sm:grid-cols-2 gap-x-8 gap-y-4">
              {[
                { label: "Company", value: app.company },
                { label: "Role", value: app.role },
                { label: "Location", value: app.location || "—" },
                { label: "Applied Date", value: new Date(app.appliedDate).toLocaleDateString() },
                { label: "Salary Range", value: app.salaryRange || "—" },
                { label: "Last Updated", value: new Date(app.updatedAt).toLocaleString() },
              ].map((field) => (
                <div key={field.label}>
                  <dt className="text-xs font-medium text-gray-500 uppercase tracking-wide">{field.label}</dt>
                  <dd className="mt-1 text-sm text-gray-900">{field.value}</dd>
                </div>
              ))}
              {app.jobUrl && (
                <div className="sm:col-span-2">
                  <dt className="text-xs font-medium text-gray-500 uppercase tracking-wide">Job URL</dt>
                  <dd className="mt-1">
                    <a
                      href={app.jobUrl}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="text-sm text-indigo-600 hover:underline break-all"
                    >
                      {app.jobUrl}
                    </a>
                  </dd>
                </div>
              )}
              {app.notes && (
                <div className="sm:col-span-2">
                  <dt className="text-xs font-medium text-gray-500 uppercase tracking-wide">Notes</dt>
                  <dd className="mt-1 text-sm text-gray-700 whitespace-pre-wrap">{app.notes}</dd>
                </div>
              )}
            </dl>
          )}
        </div>

        {/* Interviews Section */}
        <div className="bg-white rounded-2xl border border-gray-200 p-6">
          <div className="flex items-center justify-between mb-5">
            <h2 className="font-semibold text-gray-900">
              Interviews{" "}
              <span className="text-gray-400 font-normal text-sm">({interviews.length})</span>
            </h2>
            <button
              onClick={() => setShowInterviewForm(!showInterviewForm)}
              className="px-3 py-1.5 text-sm font-medium bg-indigo-600 text-white rounded-lg hover:bg-indigo-700 transition-colors"
            >
              + Add Interview
            </button>
          </div>

          {/* Add Interview Form */}
          {showInterviewForm && (
            <form
              onSubmit={handleAddInterview}
              className="mb-6 p-4 bg-indigo-50 rounded-xl border border-indigo-100 space-y-4"
            >
              <h3 className="text-sm font-semibold text-indigo-800">Schedule Interview</h3>
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <div>
                  <label className="block text-xs font-medium text-gray-600 mb-1">Date & Time</label>
                  <input
                    type="datetime-local"
                    required
                    value={interviewForm.interviewDate}
                    onChange={(e) => setInterviewForm((p) => ({ ...p, interviewDate: e.target.value }))}
                    className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
                  />
                </div>
                <div>
                  <label className="block text-xs font-medium text-gray-600 mb-1">Type</label>
                  <select
                    value={interviewForm.type}
                    onChange={(e) => setInterviewForm((p) => ({ ...p, type: e.target.value as InterviewType }))}
                    className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500 bg-white"
                  >
                    {INTERVIEW_TYPES.map((t) => (
                      <option key={t} value={t}>{INTERVIEW_TYPE_LABELS[t]}</option>
                    ))}
                  </select>
                </div>
              </div>
              <div>
                <label className="block text-xs font-medium text-gray-600 mb-1">Notes</label>
                <textarea
                  rows={2}
                  value={interviewForm.notes}
                  onChange={(e) => setInterviewForm((p) => ({ ...p, notes: e.target.value }))}
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500 resize-none"
                  placeholder="Interview notes…"
                />
              </div>
              <div className="flex items-center gap-2">
                <input
                  id="completed"
                  type="checkbox"
                  checked={interviewForm.completed}
                  onChange={(e) => setInterviewForm((p) => ({ ...p, completed: e.target.checked }))}
                  className="w-4 h-4 text-indigo-600 rounded"
                />
                <label htmlFor="completed" className="text-sm text-gray-700">Already completed</label>
              </div>
              <div className="flex gap-3">
                <button
                  type="submit"
                  disabled={addingInterview}
                  className="px-4 py-2 bg-indigo-600 hover:bg-indigo-700 disabled:bg-indigo-400 text-white text-sm font-semibold rounded-lg transition-colors"
                >
                  {addingInterview ? "Adding…" : "Add Interview"}
                </button>
                <button
                  type="button"
                  onClick={() => setShowInterviewForm(false)}
                  className="px-4 py-2 border border-gray-300 text-gray-700 text-sm font-semibold rounded-lg hover:bg-gray-50 transition-colors"
                >
                  Cancel
                </button>
              </div>
            </form>
          )}

          {/* Interviews list */}
          {interviews.length === 0 ? (
            <p className="text-sm text-gray-400 text-center py-8">
              No interviews scheduled yet
            </p>
          ) : (
            <div className="space-y-3">
              {interviews.map((interview) => (
                <div
                  key={interview.id}
                  className="flex items-start justify-between p-4 rounded-xl border border-gray-100 bg-gray-50"
                >
                  <div className="flex items-start gap-3">
                    <div
                      className={`mt-0.5 w-2.5 h-2.5 rounded-full flex-shrink-0 ${
                        interview.completed ? "bg-green-400" : "bg-yellow-400"
                      }`}
                    />
                    <div>
                      <p className="text-sm font-medium text-gray-900">
                        {INTERVIEW_TYPE_LABELS[interview.type]}
                      </p>
                      <p className="text-xs text-gray-500 mt-0.5">
                        {new Date(interview.interviewDate).toLocaleString()}
                      </p>
                      {interview.notes && (
                        <p className="text-xs text-gray-600 mt-1">{interview.notes}</p>
                      )}
                      <span
                        className={`inline-block mt-1.5 text-xs px-2 py-0.5 rounded-full ${
                          interview.completed
                            ? "bg-green-100 text-green-700"
                            : "bg-yellow-100 text-yellow-700"
                        }`}
                      >
                        {interview.completed ? "Completed" : "Upcoming"}
                      </span>
                    </div>
                  </div>
                  <button
                    onClick={() => handleDeleteInterview(interview.id)}
                    className="text-gray-400 hover:text-red-500 transition-colors ml-3"
                  >
                    <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                      <path strokeLinecap="round" strokeLinejoin="round" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
                    </svg>
                  </button>
                </div>
              ))}
            </div>
          )}
        </div>
      </main>
    </>
  );
}
