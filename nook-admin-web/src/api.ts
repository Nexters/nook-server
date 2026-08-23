const apiBase = (import.meta.env.VITE_ADMIN_API_BASE_URL ?? "").replace(/\/$/, "");
type Envelope<T> = { success?: T; error?: { reason?: string } };

export async function api<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${apiBase}/api/admin/v1${path}`, { credentials: "include", ...init, headers: { "Content-Type": "application/json", ...init?.headers } });
  const envelope = (await response.json().catch(() => ({}))) as Envelope<T>;
  if (!response.ok || envelope.success === undefined) throw new Error(envelope.error?.reason ?? `요청에 실패했습니다. (${response.status})`);
  return envelope.success;
}
