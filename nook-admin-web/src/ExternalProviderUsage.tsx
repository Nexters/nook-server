import { Alert, Box, Card, CardContent, Chip, CircularProgress, MenuItem, Stack, TextField, Typography } from "@mui/material";
import { useEffect, useMemo, useState } from "react";
import type React from "react";
import { api } from "./api";

type Provider = { provider: string; calls: number; failures: number; units: number; estimatedCostUsd?: number; estimatedCostKrw?: number; pricingStatus: string };
type Event = { id: number; provider: string; operation: string; sku: string; status: string; durationMs: number; httpStatus?: number; failureType?: string; estimatedCostUsd?: number; estimatedCostKrw?: number; occurredAt: string };
type Summary = { totalCalls: number; failedCalls: number; estimatedCostUsd?: number; estimatedCostKrw?: number; unpricedCalls: number; providers: Provider[]; recentEvents: Event[] };
type OverviewProvider = { provider: string; displayName: string; category: string; purpose: string; runtimes: string[]; credentialConfigured: boolean; operationalState: string; stateReason: string; policy: string; calls: number; failures: number; estimatedCostUsd?: number; estimatedCostKrw?: number; pricingStatus: string; lastCalledAt?: string; lastFailureAt?: string };
type Overview = { providers: OverviewProvider[] };
const krw = new Intl.NumberFormat("ko-KR", { style: "currency", currency: "KRW", maximumFractionDigits: 0 });
const usd = new Intl.NumberFormat("en-US", { style: "currency", currency: "USD", minimumFractionDigits: 2, maximumFractionDigits: 4 });
const fmt = new Intl.NumberFormat("ko-KR");

export function ExternalProviderUsagePage() {
  const currentMonth = useMemo(() => new Date().toISOString().slice(0, 7), []);
  const [month, setMonth] = useState(currentMonth); const [provider, setProvider] = useState("");
  const [overviewState, setOverviewState] = useState("ALL");
  const [data, setData] = useState<Summary>(); const [overview, setOverview] = useState<Overview>(); const [error, setError] = useState("");
  useEffect(() => {
    const from = new Date(`${month}-01T00:00:00+09:00`); const to = new Date(from); to.setMonth(to.getMonth() + 1);
    const query = new URLSearchParams({ from: from.toISOString(), to: to.toISOString(), limit: "100" }); if (provider) query.set("provider", provider);
    setData(undefined); setOverview(undefined); setError("");
    Promise.all([api<Summary>(`/external-provider-usage?${query}`), api<Overview>(`/external-provider-usage/overview?${query}`)])
      .then(([usage, catalog]) => { setData(usage); setOverview(catalog); })
      .catch((cause: unknown) => setError(cause instanceof Error ? cause.message : "외부 API 현황을 불러오지 못했습니다."));
  }, [month, provider]);
  if (error) return <Alert severity="error">{error}</Alert>;
  if (!data || !overview) return <Box sx={{ p: 5, textAlign: "center" }}><CircularProgress /></Box>;
  const providerOptions = overview.providers.map((item) => item.provider);
  const visibleOverview = overview.providers.filter((item) =>
    (!provider || item.provider === provider) && (overviewState === "ALL" || item.operationalState === overviewState));
  return <Stack spacing={3} sx={{ maxWidth: 1500 }}><Stack direction={{ xs: "column", md: "row" }} sx={{ justifyContent: "space-between", gap: 2 }}><Box><Typography variant="h4">외부 API 현황</Typography><Typography color="text.secondary">연동된 API, 실제 호출 정책과 기간별 사용량·비용을 함께 봅니다. 호출 이력이 없어도 표시됩니다.</Typography></Box><Stack direction="row" spacing={1}><TextField type="month" size="small" value={month} onChange={(e) => setMonth(e.target.value)} /><TextField select size="small" label="Provider" value={provider} onChange={(e) => setProvider(e.target.value)} sx={{ minWidth: 190 }}><MenuItem value="">전체</MenuItem>{providerOptions.map((value) => <MenuItem key={value} value={value}>{value}</MenuItem>)}</TextField></Stack></Stack>
    <Card variant="outlined"><CardContent><Stack direction={{ xs: "column", md: "row" }} sx={{ justifyContent: "space-between", gap: 2, mb: 2 }}><Box><Typography variant="h6">연동·운영 오버뷰</Typography><Typography variant="body2" color="text.secondary">상태는 현재 runtime 설정과 credential 설정 여부를 기준으로 계산합니다.</Typography></Box><TextField select size="small" label="운영 상태" value={overviewState} onChange={(e) => setOverviewState(e.target.value)} sx={{ minWidth: 170 }}><MenuItem value="ALL">전체</MenuItem>{["ACTIVE", "FALLBACK", "STANDBY", "DISABLED", "MISCONFIGURED"].map((value) => <MenuItem key={value} value={value}>{stateLabel(value)}</MenuItem>)}</TextField></Stack><Box className="usage-table"><table><thead><tr>{["외부 API", "용도", "운영 상태", "호출 정책", "이번 달", "최근 상태"].map((header) => <th key={header}>{header}</th>)}</tr></thead><tbody>{visibleOverview.map((item) => <tr key={item.provider}><td><strong>{item.displayName}</strong><div className="mono-text"><small>{item.provider}</small></div><small>{item.runtimes.join(" · ")}</small></td><td><Chip size="small" variant="outlined" label={item.category} /><div>{item.purpose}</div></td><td><Chip size="small" label={stateLabel(item.operationalState)} color={stateColor(item.operationalState)} /><div><small>{item.stateReason}</small></div>{!item.credentialConfigured && <div><small>credential 누락</small></div>}</td><td><small>{item.policy}</small></td><td><strong>{fmt.format(item.calls)}회</strong><div><small>실패 {fmt.format(item.failures)} · {item.estimatedCostUsd == null ? "비용 —" : usd.format(item.estimatedCostUsd)}</small></div><div><small>{item.pricingStatus}</small></div></td><td><small>최근 호출 {dateOrDash(item.lastCalledAt)}</small><br /><small>최근 오류 {dateOrDash(item.lastFailureAt)}</small></td></tr>)}</tbody></table></Box></CardContent></Card>
    {data.unpricedCalls > 0 && <Alert severity="warning">공개 단가를 확정할 수 없거나 환율이 설정되지 않은 호출이 {fmt.format(data.unpricedCalls)}건 있습니다. 비용을 0원으로 간주하지 않습니다.</Alert>}
    <Stack direction={{ xs: "column", md: "row" }} spacing={2}>{[["총 호출", fmt.format(data.totalCalls)], ["실패", `${fmt.format(data.failedCalls)}건 (${data.totalCalls ? (data.failedCalls / data.totalCalls * 100).toFixed(1) : "0.0"}%)`], ["예상 비용", data.estimatedCostUsd == null ? "미산정" : `${usd.format(data.estimatedCostUsd)}${data.estimatedCostKrw == null ? "" : ` · ${krw.format(data.estimatedCostKrw)}`}`], ["가격 미확정", `${fmt.format(data.unpricedCalls)}건`]].map(([label, value]) => <Card variant="outlined" sx={{ flex: 1 }} key={label}><CardContent><Typography color="text.secondary">{label}</Typography><Typography variant="h5" sx={{ mt: 1 }}>{value}</Typography></CardContent></Card>)}</Stack>
    <UsageTable title="Provider별 집계" headers={["Provider", "호출", "실패", "사용량", "예상 비용", "가격 상태"]}>{data.providers.map((item) => <tr key={item.provider}><td className="mono-text">{item.provider}</td><td>{fmt.format(item.calls)}</td><td>{fmt.format(item.failures)}</td><td>{fmt.format(item.units)}</td><td>{item.estimatedCostUsd == null ? "—" : usd.format(item.estimatedCostUsd)}</td><td><Chip size="small" label={item.pricingStatus} color={item.estimatedCostUsd != null ? "success" : "warning"} /></td></tr>)}</UsageTable>
    <UsageTable title="최근 물리 요청" headers={["시각", "Provider", "Operation / SKU", "결과", "지연", "비용"]}>{data.recentEvents.map((event) => <tr key={event.id}><td>{new Date(event.occurredAt).toLocaleString("ko-KR")}</td><td className="mono-text">{event.provider}</td><td><div className="mono-text">{event.operation}</div><small>{event.sku}</small></td><td><Chip size="small" label={event.httpStatus ? `${event.status} · ${event.httpStatus}` : event.status} color={event.status === "SUCCEEDED" ? "success" : "error"} />{event.failureType && <div><small>{event.failureType}</small></div>}</td><td>{fmt.format(event.durationMs)}ms</td><td>{event.estimatedCostUsd == null ? "—" : usd.format(event.estimatedCostUsd)}</td></tr>)}</UsageTable>
  </Stack>;
}

function stateLabel(state: string) { return ({ ACTIVE: "사용 중", FALLBACK: "Fallback", STANDBY: "대기", DISABLED: "비활성", MISCONFIGURED: "설정 누락" } as Record<string, string>)[state] ?? state; }
function stateColor(state: string): "success" | "info" | "warning" | "default" | "error" { const colors: Record<string, "success" | "info" | "warning" | "default" | "error"> = { ACTIVE: "success", FALLBACK: "info", STANDBY: "warning", DISABLED: "default", MISCONFIGURED: "error" }; return colors[state] ?? "default"; }
function dateOrDash(value?: string) { return value ? new Date(value).toLocaleString("ko-KR") : "—"; }

function UsageTable({ title, headers, children }: { title: string; headers: string[]; children: React.ReactNode }) { return <Card variant="outlined"><CardContent><Typography variant="h6" sx={{ mb: 2 }}>{title}</Typography><Box className="usage-table"><table><thead><tr>{headers.map((header) => <th key={header}>{header}</th>)}</tr></thead><tbody>{children}</tbody></table></Box></CardContent></Card>; }
