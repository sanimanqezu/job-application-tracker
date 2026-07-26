export type ApplicationStatus =
  | "APPLIED"
  | "PHONE_SCREEN"
  | "INTERVIEW"
  | "TECHNICAL_TEST"
  | "OFFER"
  | "REJECTED"
  | "WITHDRAWN";

export type InterviewType = "PHONE" | "VIDEO" | "ON_SITE" | "TECHNICAL";

export interface User {
  username: string;
  email: string;
}

export interface JobApplication {
  id: number;
  company: string;
  role: string;
  location: string | null;
  jobUrl: string | null;
  status: ApplicationStatus;
  appliedDate: string;
  salaryRange: string | null;
  notes: string | null;
  updatedAt: string;
  interviewCount: number;
}

export interface Interview {
  id: number;
  applicationId: number;
  interviewDate: string;
  type: InterviewType;
  notes: string | null;
  completed: boolean;
}

export interface DashboardStats {
  totalApplications: number;
  byStatus: Record<ApplicationStatus, number>;
  thisMonthCount: number;
  activeCount: number;
}

export interface AuthResponse {
  token: string;
  username: string;
  email: string;
}

export interface JobApplicationRequest {
  company: string;
  role: string;
  location?: string;
  jobUrl?: string;
  status: ApplicationStatus;
  appliedDate: string;
  salaryRange?: string;
  notes?: string;
}

export interface InterviewRequest {
  interviewDate: string;
  type: InterviewType;
  notes?: string;
  completed: boolean;
}

export const STATUS_LABELS: Record<ApplicationStatus, string> = {
  APPLIED: "Applied",
  PHONE_SCREEN: "Phone Screen",
  INTERVIEW: "Interview",
  TECHNICAL_TEST: "Technical Test",
  OFFER: "Offer",
  REJECTED: "Rejected",
  WITHDRAWN: "Withdrawn",
};

export const STATUS_COLORS: Record<ApplicationStatus, string> = {
  APPLIED: "bg-blue-100 text-blue-800",
  PHONE_SCREEN: "bg-purple-100 text-purple-800",
  INTERVIEW: "bg-yellow-100 text-yellow-800",
  TECHNICAL_TEST: "bg-orange-100 text-orange-800",
  OFFER: "bg-green-100 text-green-800",
  REJECTED: "bg-red-100 text-red-800",
  WITHDRAWN: "bg-gray-100 text-gray-600",
};

export const KANBAN_STATUSES: ApplicationStatus[] = [
  "APPLIED",
  "PHONE_SCREEN",
  "INTERVIEW",
  "TECHNICAL_TEST",
  "OFFER",
  "REJECTED",
];

export const ALL_STATUSES: ApplicationStatus[] = [
  "APPLIED",
  "PHONE_SCREEN",
  "INTERVIEW",
  "TECHNICAL_TEST",
  "OFFER",
  "REJECTED",
  "WITHDRAWN",
];

// --- Discover (live job scanner) ---

export interface ScannedJob {
  id: number;
  source: string;
  company: string;
  title: string;
  location: string | null;
  url: string;
  stack: string | null;
  segment: string | null;
  city: string | null;
  junior: boolean;
  matchScore: number;
  matchedSkills: string | null;
  snippet: string | null;
  postedAt: string | null;
  firstSeenAt: string | null;
}

export interface HarvestStatus {
  enabled: boolean;
  companiesTotal: number;
  companiesScanned: number;
  companiesPending: number;
  jobsTotal: number;
}

export interface DiscoverStats {
  total: number;
  newForYou: number;
  companies: number;
}

export interface ScanSummary {
  companiesScanned: number;
  boardsWithOpenings: number;
  jobsDiscovered: number;
  jobsStored: number;
  newlyAdded: number;
}

export interface CompanyRow {
  name: string;
  city: string;
  segment: string;
  stack: string | null;
  size: string | null;
  fit: string | null;
  apply: string;
  note: string | null;
}
