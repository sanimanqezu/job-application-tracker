import { create } from "zustand";
import type { ApplicationStatus, JobApplication, User } from "@/types";
import { applicationsApi } from "@/lib/api";

interface AuthState {
  user: User | null;
  token: string | null;
  setAuth: (user: User, token: string) => void;
  clearAuth: () => void;
}

interface ApplicationState {
  applications: JobApplication[];
  loading: boolean;
  error: string | null;
  fetchApplications: (status?: ApplicationStatus) => Promise<void>;
  addApplication: (app: JobApplication) => void;
  updateApplication: (app: JobApplication) => void;
  removeApplication: (id: number) => void;
}

export const useAuthStore = create<AuthState>((set) => ({
  user:
    typeof window !== "undefined"
      ? (() => {
          try {
            const stored = localStorage.getItem("user");
            return stored ? JSON.parse(stored) : null;
          } catch {
            return null;
          }
        })()
      : null,
  token:
    typeof window !== "undefined" ? localStorage.getItem("jwt_token") : null,

  setAuth: (user, token) => {
    if (typeof window !== "undefined") {
      localStorage.setItem("jwt_token", token);
      localStorage.setItem("user", JSON.stringify(user));
    }
    set({ user, token });
  },

  clearAuth: () => {
    if (typeof window !== "undefined") {
      localStorage.removeItem("jwt_token");
      localStorage.removeItem("user");
    }
    set({ user: null, token: null });
  },
}));

export const useApplicationStore = create<ApplicationState>((set) => ({
  applications: [],
  loading: false,
  error: null,

  fetchApplications: async (status?: ApplicationStatus) => {
    set({ loading: true, error: null });
    try {
      const res = await applicationsApi.getAll(status);
      set({ applications: res.data, loading: false });
    } catch (err: unknown) {
      const message =
        err instanceof Error ? err.message : "Failed to fetch applications";
      set({ error: message, loading: false });
    }
  },

  addApplication: (app) =>
    set((state) => ({ applications: [app, ...state.applications] })),

  updateApplication: (app) =>
    set((state) => ({
      applications: state.applications.map((a) => (a.id === app.id ? app : a)),
    })),

  removeApplication: (id) =>
    set((state) => ({
      applications: state.applications.filter((a) => a.id !== id),
    })),
}));
