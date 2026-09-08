import { useEffect, useState } from "react";
import { api } from "./api";

export type Behavior = { eventName: string; users: number; events: number };
export type Retention = { day: number; eligibleUsers: number; returnedUsers: number };
export type Daily = { date: string; signUps: number; activatedUsers: number; activeUsers: number };
export type AnalyticsReport = {
  coverage: { firstEventAt: string | null; lastEventAt: string | null };
  summary: { activeUsers: number; signUps: number; usersWithoutSignUp: number; events: number; legacyEvents: number; savingUsers: number; uniqueSavedTargets: number };
  events: Behavior[];
  saveDistribution: { label: string; users: number }[];
  saveReach: { threshold: number; users: number; totalUsers: number }[];
  savedContentReturn: { savingUsers: number; returnedUsers: number; revisitedTargets: number };
  cohorts: { activatedOn: string; users: number; retention: Retention[] }[];
};
export type Overview = {
  from: string; to: string; observedThrough: string; activationThreshold: number;
  activeUsers: { asOf: string; daily: number; weekly: number; monthly: number };
  daily: Daily[]; retention: Retention[]; behaviors: Behavior[];
  funnel: { signUps: number; activatedUsers: number; returnedUsers: number };
  report?: AnalyticsReport;
};
export type ActivityScope = { title: string; from: string; to: string; eventName?: string };
export const eventLabels: Record<string, string> = {
  sign_up: "신규 가입", post_save: "게시물 저장", place_save: "장소 저장",
  archive_view: "아카이브 조회", post_view: "게시물 조회", place_view: "장소 조회", map_view: "지도 조회",
};
export const eventColors: Record<string, string> = {
  sign_up: "#738295", post_save: "#2BAE7F", place_save: "#558EFF",
  archive_view: "#738295", post_view: "#4E6AF3", place_view: "#558EFF", map_view: "#FFA30E",
};
export const num = new Intl.NumberFormat("ko-KR");
export const percent = (value: number, total: number) => total ? `${(value / total * 100).toFixed(1)}%` : "—";
export const kstDate = (value: Date) => new Date(value.getTime() + 9 * 3600000).toISOString().slice(0, 10);
export const kstTime = (value: string) => new Date(value).toLocaleString("ko-KR", { timeZone: "Asia/Seoul" });
export const shiftDate = (value: string, days: number) => new Date(Date.parse(value) + days * 86400000).toISOString().slice(0, 10);
export const pairGrid = { display: "grid", gridTemplateColumns: { xs: "1fr", lg: "repeat(2, minmax(0, 1fr))" }, gap: 2 };

export function useAnalytics<T>(path: string | null, revision = 0): { data?: T; error?: string } {
  const [state, setState] = useState<{ key: string | null; revision: number; data?: T; error?: string }>({ key: null, revision });
  useEffect(() => {
    const controller = new AbortController();
    setState({ key: path, revision });
    if (path) api<T>(path, { signal: controller.signal, cache: "no-store" })
      .then(data => { if (!controller.signal.aborted) setState({ key: path, revision, data }); })
      .catch((cause: unknown) => { if (!controller.signal.aborted) setState({ key: path, revision, error: cause instanceof Error ? cause.message : "조회에 실패했습니다." }); });
    return () => controller.abort();
  }, [path, revision]);
  return state.key === path && state.revision === revision ? state : {};
}
