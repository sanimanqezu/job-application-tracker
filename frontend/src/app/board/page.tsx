"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import Navbar from "@/components/Navbar";
import StatusBadge from "@/components/StatusBadge";
import { useAuthStore, useApplicationStore } from "@/store/applicationStore";
import type { ApplicationStatus, JobApplication } from "@/types";
import { KANBAN_STATUSES, STATUS_LABELS } from "@/types";

const COLUMN_COLORS: Record<ApplicationStatus, string> = {
  APPLIED: "border-t-blue-400",
  PHONE_SCREEN: "border-t-purple-400",
  INTERVIEW: "border-t-yellow-400",
  TECHNICAL_TEST: "border-t-orange-400",
  OFFER: "border-t-green-400",
  REJECTED: "border-t-red-400",
  WITHDRAWN: "border-t-gray-400",
};

const COLUMN_COUNT_COLORS: Record<ApplicationStatus, string> = {
  APPLIED: "bg-blue-100 text-blue-700",
  PHONE_SCREEN: "bg-purple-100 text-purple-700",
  INTERVIEW: "bg-yellow-100 text-yellow-700",
  TECHNICAL_TEST: "bg-orange-100 text-orange-700",
  OFFER: "bg-green-100 text-green-700",
  REJECTED: "bg-red-100 text-red-700",
  WITHDRAWN: "bg-gray-100 text-gray-600",
};

function KanbanCard({ app }: { app: JobApplication }) {
  return (
    <Link href={`/applications/${app.id}`}>
      <div className="bg-white rounded-xl border border-gray-200 p-4 hover:shadow-md hover:border-indigo-200 transition-all cursor-pointer group">
        <p className="font-semibold text-gray-900 text-sm group-hover:text-indigo-700 transition-colors truncate">
          {app.company}
        </p>
        <p className="text-xs text-gray-500 mt-0.5 truncate">{app.role}</p>

        {app.location && (
          <p className="flex items-center gap-1 text-xs text-gray-400 mt-2">
            <svg className="w-3 h-3 flex-shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M17.657 16.657L13.414 20.9a1.998 1.998 0 01-2.827 0l-4.244-4.243a8 8 0 1111.314 0z" />
              <path strokeLinecap="round" strokeLinejoin="round" d="M15 11a3 3 0 11-6 0 3 3 0 016 0z" />
            </svg>
            <span className="truncate">{app.location}</span>
          </p>
        )}

        <div className="flex items-center justify-between mt-3 pt-3 border-t border-gray-100">
          <span className="text-xs text-gray-400">
            {new Date(app.appliedDate).toLocaleDateString("en-ZA", {
              day: "numeric",
              month: "short",
            })}
          </span>
          {app.interviewCount > 0 && (
            <span className="flex items-center gap-1 text-xs text-gray-500">
              <svg className="w-3 h-3" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                <path strokeLinecap="round" strokeLinejoin="round" d="M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z" />
              </svg>
              {app.interviewCount}
            </span>
          )}
        </div>
      </div>
    </Link>
  );
}

function KanbanColumn({
  status,
  apps,
}: {
  status: ApplicationStatus;
  apps: JobApplication[];
}) {
  return (
    <div
      className={`flex flex-col bg-gray-50 rounded-2xl border-t-4 border border-gray-200 ${COLUMN_COLORS[status]} min-w-[260px] w-full`}
    >
      {/* Column header */}
      <div className="px-4 py-3 flex items-center justify-between">
        <span className="text-sm font-semibold text-gray-700">
          {STATUS_LABELS[status]}
        </span>
        <span
          className={`text-xs font-bold px-2 py-0.5 rounded-full ${COLUMN_COUNT_COLORS[status]}`}
        >
          {apps.length}
        </span>
      </div>

      {/* Cards */}
      <div className="flex-1 px-3 pb-4 space-y-2 overflow-y-auto max-h-[600px]">
        {apps.length === 0 ? (
          <div className="flex items-center justify-center h-20 text-xs text-gray-400 border-2 border-dashed border-gray-200 rounded-xl">
            No applications
          </div>
        ) : (
          apps.map((app) => <KanbanCard key={app.id} app={app} />)
        )}
      </div>
    </div>
  );
}

export default function BoardPage() {
  const router = useRouter();
  const { token } = useAuthStore();
  const { applications, loading, error, fetchApplications } = useApplicationStore();

  useEffect(() => {
    if (!token) {
      router.push("/login");
      return;
    }
    fetchApplications();
  }, [token, router, fetchApplications]);

  // Group by status
  const grouped = KANBAN_STATUSES.reduce<Record<ApplicationStatus, JobApplication[]>>(
    (acc, status) => {
      acc[status] = applications.filter((app) => app.status === status);
      return acc;
    },
    {} as Record<ApplicationStatus, JobApplication[]>
  );

  return (
    <>
      <Navbar />
      <main className="px-4 sm:px-6 lg:px-8 py-8">
        {/* Header */}
        <div className="flex items-center justify-between mb-6 max-w-full">
          <div>
            <h1 className="text-2xl font-bold text-gray-900">Kanban Board</h1>
            <p className="text-gray-500 mt-1">
              Visual overview of your job search pipeline
            </p>
          </div>
          <Link
            href="/applications/new"
            className="px-4 py-2 bg-indigo-600 text-white text-sm font-semibold rounded-lg hover:bg-indigo-700 transition-colors flex-shrink-0"
          >
            + New Application
          </Link>
        </div>

        {error && (
          <div className="p-4 bg-red-50 border border-red-200 rounded-xl text-red-700 mb-4">
            {error}
          </div>
        )}

        {loading ? (
          <div className="flex items-center justify-center h-64">
            <div className="animate-spin w-8 h-8 border-4 border-indigo-600 border-t-transparent rounded-full" />
          </div>
        ) : (
          /* Board layout: horizontal scroll on small screens */
          <div className="flex gap-4 overflow-x-auto pb-4">
            {KANBAN_STATUSES.map((status) => (
              <div key={status} className="flex-shrink-0 w-[260px] lg:flex-1 lg:w-auto">
                <KanbanColumn status={status} apps={grouped[status] ?? []} />
              </div>
            ))}
          </div>
        )}

        {/* Legend */}
        {!loading && (
          <div className="mt-6 flex flex-wrap gap-3 items-center">
            <span className="text-xs text-gray-400">Legend:</span>
            {KANBAN_STATUSES.map((s) => (
              <StatusBadge key={s} status={s} />
            ))}
          </div>
        )}
      </main>
    </>
  );
}
