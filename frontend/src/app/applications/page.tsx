"use client";

import { useEffect, useState, useCallback } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import Navbar from "@/components/Navbar";
import StatusBadge from "@/components/StatusBadge";
import { applicationsApi } from "@/lib/api";
import { useAuthStore, useApplicationStore } from "@/store/applicationStore";
import type { ApplicationStatus } from "@/types";
import { ALL_STATUSES, STATUS_LABELS } from "@/types";

export default function ApplicationsPage() {
  const router = useRouter();
  const { token } = useAuthStore();
  const { applications, loading, error, fetchApplications, updateApplication, removeApplication } =
    useApplicationStore();
  const [statusFilter, setStatusFilter] = useState<ApplicationStatus | "">("");
  const [deletingId, setDeletingId] = useState<number | null>(null);

  useEffect(() => {
    if (!token) {
      router.push("/login");
      return;
    }
    fetchApplications(statusFilter ? statusFilter : undefined);
  }, [token, router, fetchApplications, statusFilter]);

  const handleStatusChange = useCallback(
    async (id: number, newStatus: ApplicationStatus) => {
      try {
        const res = await applicationsApi.updateStatus(id, newStatus);
        updateApplication(res.data);
      } catch {
        alert("Failed to update status");
      }
    },
    [updateApplication]
  );

  const handleDelete = useCallback(
    async (id: number) => {
      if (!confirm("Delete this application?")) return;
      setDeletingId(id);
      try {
        await applicationsApi.delete(id);
        removeApplication(id);
      } catch {
        alert("Failed to delete application");
      } finally {
        setDeletingId(null);
      }
    },
    [removeApplication]
  );

  return (
    <>
      <Navbar />
      <main className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
        {/* Header */}
        <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 mb-6">
          <div>
            <h1 className="text-2xl font-bold text-gray-900">Applications</h1>
            <p className="text-gray-500 mt-1">
              {applications.length} application{applications.length !== 1 ? "s" : ""}
            </p>
          </div>
          <div className="flex items-center gap-3">
            <select
              value={statusFilter}
              onChange={(e) => setStatusFilter(e.target.value as ApplicationStatus | "")}
              className="px-3 py-2 border border-gray-300 rounded-lg text-sm bg-white text-gray-700 focus:outline-none focus:ring-2 focus:ring-indigo-500"
            >
              <option value="">All Statuses</option>
              {ALL_STATUSES.map((s) => (
                <option key={s} value={s}>
                  {STATUS_LABELS[s]}
                </option>
              ))}
            </select>
            <Link
              href="/applications/new"
              className="px-4 py-2 bg-indigo-600 text-white text-sm font-semibold rounded-lg hover:bg-indigo-700 transition-colors"
            >
              + New
            </Link>
          </div>
        </div>

        {/* Error */}
        {error && (
          <div className="p-4 bg-red-50 border border-red-200 rounded-xl text-red-700 mb-4">
            {error}
          </div>
        )}

        {/* Loading */}
        {loading ? (
          <div className="flex items-center justify-center h-64">
            <div className="animate-spin w-8 h-8 border-4 border-indigo-600 border-t-transparent rounded-full" />
          </div>
        ) : applications.length === 0 ? (
          <div className="bg-white rounded-2xl border border-gray-200 p-16 text-center">
            <svg
              className="w-12 h-12 text-gray-300 mx-auto mb-4"
              fill="none"
              viewBox="0 0 24 24"
              stroke="currentColor"
              strokeWidth={1.5}
            >
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2"
              />
            </svg>
            <p className="text-gray-500 font-medium">No applications yet</p>
            <p className="text-gray-400 text-sm mt-1">Click &quot;+ New&quot; to add your first one</p>
          </div>
        ) : (
          /* Table */
          <div className="bg-white rounded-2xl border border-gray-200 overflow-hidden">
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="bg-gray-50 border-b border-gray-200">
                    <th className="px-4 py-3 text-left font-semibold text-gray-600">Company</th>
                    <th className="px-4 py-3 text-left font-semibold text-gray-600">Role</th>
                    <th className="px-4 py-3 text-left font-semibold text-gray-600 hidden sm:table-cell">Location</th>
                    <th className="px-4 py-3 text-left font-semibold text-gray-600">Status</th>
                    <th className="px-4 py-3 text-left font-semibold text-gray-600 hidden md:table-cell">Applied</th>
                    <th className="px-4 py-3 text-left font-semibold text-gray-600 hidden lg:table-cell">Interviews</th>
                    <th className="px-4 py-3 text-right font-semibold text-gray-600">Actions</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-100">
                  {applications.map((app) => (
                    <tr key={app.id} className="hover:bg-gray-50 transition-colors group">
                      <td className="px-4 py-3 font-medium text-gray-900">
                        <Link
                          href={`/applications/${app.id}`}
                          className="hover:text-indigo-600 transition-colors"
                        >
                          {app.company}
                        </Link>
                      </td>
                      <td className="px-4 py-3 text-gray-600">{app.role}</td>
                      <td className="px-4 py-3 text-gray-500 hidden sm:table-cell">
                        {app.location || "—"}
                      </td>
                      <td className="px-4 py-3">
                        <select
                          value={app.status}
                          onChange={(e) =>
                            handleStatusChange(app.id, e.target.value as ApplicationStatus)
                          }
                          className="text-xs border-0 bg-transparent focus:outline-none cursor-pointer"
                        >
                          {ALL_STATUSES.map((s) => (
                            <option key={s} value={s}>
                              {STATUS_LABELS[s]}
                            </option>
                          ))}
                        </select>
                        <StatusBadge status={app.status} className="pointer-events-none" />
                      </td>
                      <td className="px-4 py-3 text-gray-500 hidden md:table-cell">
                        {new Date(app.appliedDate).toLocaleDateString()}
                      </td>
                      <td className="px-4 py-3 hidden lg:table-cell">
                        <span className="inline-flex items-center gap-1 text-gray-500">
                          <svg className="w-3.5 h-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                            <path strokeLinecap="round" strokeLinejoin="round" d="M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z" />
                          </svg>
                          {app.interviewCount}
                        </span>
                      </td>
                      <td className="px-4 py-3 text-right">
                        <div className="flex items-center justify-end gap-2">
                          <Link
                            href={`/applications/${app.id}`}
                            className="text-indigo-600 hover:text-indigo-800 text-xs font-medium"
                          >
                            View
                          </Link>
                          <button
                            onClick={() => handleDelete(app.id)}
                            disabled={deletingId === app.id}
                            className="text-red-500 hover:text-red-700 text-xs font-medium disabled:opacity-40"
                          >
                            {deletingId === app.id ? "…" : "Delete"}
                          </button>
                        </div>
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
