import axios from "axios";
import type {
  AuthResponse,
  DashboardStats,
  Interview,
  InterviewRequest,
  JobApplication,
  JobApplicationRequest,
  ApplicationStatus,
} from "@/types";

const api = axios.create({
  baseURL: process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080",
  headers: { "Content-Type": "application/json" },
});

// Attach JWT token to every request
api.interceptors.request.use((config) => {
  if (typeof window !== "undefined") {
    const token = localStorage.getItem("jwt_token");
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
  }
  return config;
});

// Redirect to login on 401
api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401 && typeof window !== "undefined") {
      localStorage.removeItem("jwt_token");
      localStorage.removeItem("user");
      window.location.href = "/login";
    }
    return Promise.reject(error);
  }
);

// Auth
export const authApi = {
  register: (username: string, email: string, password: string) =>
    api.post<AuthResponse>("/api/auth/register", { username, email, password }),

  login: (username: string, password: string) =>
    api.post<AuthResponse>("/api/auth/login", { username, password }),
};

// Applications
export const applicationsApi = {
  getAll: (status?: ApplicationStatus) =>
    api.get<JobApplication[]>("/api/applications", {
      params: status ? { status } : undefined,
    }),

  getById: (id: number) => api.get<JobApplication>(`/api/applications/${id}`),

  create: (data: JobApplicationRequest) =>
    api.post<JobApplication>("/api/applications", data),

  update: (id: number, data: JobApplicationRequest) =>
    api.put<JobApplication>(`/api/applications/${id}`, data),

  updateStatus: (id: number, status: ApplicationStatus) =>
    api.patch<JobApplication>(`/api/applications/${id}/status`, { status }),

  delete: (id: number) => api.delete(`/api/applications/${id}`),

  getInterviews: (applicationId: number) =>
    api.get<Interview[]>(`/api/applications/${applicationId}/interviews`),

  addInterview: (applicationId: number, data: InterviewRequest) =>
    api.post<Interview>(`/api/applications/${applicationId}/interviews`, data),
};

// Interviews
export const interviewsApi = {
  delete: (id: number) => api.delete(`/api/interviews/${id}`),
};

// Dashboard
export const dashboardApi = {
  getStats: () => api.get<DashboardStats>("/api/dashboard"),
};

export default api;
