import { Alert, Box, Card, CardContent, Chip, CircularProgress, MenuItem, Stack, TextField, Typography } from "@mui/material";
import { useEffect, useMemo, useState } from "react";
import type React from "react";
import { api } from "./api";

type Provider = { provider: string; calls: number; failures: number; units: number; estimatedCostUsd?: number; estimatedCostKrw?: number; pricingStatus: string };
type Event = { id: number; provider: string; operation: string; sku: string; status: string; durationMs: number; httpStatus?: number; failureType?: string; estimatedCostUsd?: number; estimatedCostKrw?: number; occurredAt: string };
type Summary = { totalCalls: number; failedCalls: number; estimatedCostUsd?: number; estimatedCostKrw?: number; unpricedCalls: number; providers: Provider[]; recentEvents: Event[] };
const krw = new Intl.NumberFormat("ko-KR", { style: "currency", currency: "KRW", maximumFractionDigits: 0 });
const usd = new Intl.NumberFormat("en-US", { style: "currency", currency: "USD", minimumFractionDigits: 2, maximumFractionDigits: 4 });
const fmt = new Intl.NumberFormat("ko-KR");

export function ExternalProviderUsagePage() {
  const currentMonth = useMemo(() => new Date().toISOString().slice(0, 7), []);
  const [month, setMonth] = useState(currentMonth); const [provider, setProvider] = useState("");
  const [data, setData] = useState<Summary>(); const [error, setError] = useState("");
  useEffect(() => {
    const from = new Date(`${month}-01T00:00:00+09:00`); const to = new Date(from); to.setMonth(to.getMonth() + 1);
    const query = new URLSearchParams({ from: from.toISOString(), to: to.toISOString(), limit: "100" }); if (provider) query.set("provider", provider);
    setData(undefined); setError(""); api<Summary>(`/external-provider-usage?${query}`).then(setData).catch((cause: unknown) => setError(cause instanceof Error ? cause.message : "사용량을 불러오지 못했습니다."));
  }, [month, provider]);
  if (error) return <Alert severity="error">{error}</Alert>;
  if (!data) return <Box sx={{ p: 5, textAlign: "center" }}><CircularProgress /></Box>;
  const providerOptions = [...new Set(data.providers.map((item) => item.provider))];
  return <Stack spacing={3} sx={{ maxWidth: 1400 }}><Stack direction={{ xs: "column", md: "row" }} sx={{ justifyContent: "space-between", gap: 2 }}><Box><Typography variant="h4">외부 API 사용량</Typography><Typography color="text.secondary">실제 물리 HTTP 요청, 실패율과 공식 단가 기반 예상 비용을 함께 봅니다.</Typography></Box><Stack direction="row" spacing={1}><TextField type="month" size="small" value={month} onChange={(e) => setMonth(e.target.value)} /><TextField select size="small" label="Provider" value={provider} onChange={(e) => setProvider(e.target.value)} sx={{ minWidth: 190 }}><MenuItem value="">전체</MenuItem>{providerOptions.map((value) => <MenuItem key={value} value={value}>{value}</MenuItem>)}</TextField></Stack></Stack>
    {data.unpricedCalls > 0 && <Alert severity="warning">공개 단가를 확정할 수 없거나 환율이 설정되지 않은 호출이 {fmt.format(data.unpricedCalls)}건 있습니다. 비용을 0원으로 간주하지 않습니다.</Alert>}
    <Stack direction={{ xs: "column", md: "row" }} spacing={2}>{[["총 호출", fmt.format(data.totalCalls)], ["실패", `${fmt.format(data.failedCalls)}건 (${data.totalCalls ? (data.failedCalls / data.totalCalls * 100).toFixed(1) : "0.0"}%)`], ["예상 비용", data.estimatedCostUsd == null ? "미산정" : `${usd.format(data.estimatedCostUsd)}${data.estimatedCostKrw == null ? "" : ` · ${krw.format(data.estimatedCostKrw)}`}`], ["가격 미확정", `${fmt.format(data.unpricedCalls)}건`]].map(([label, value]) => <Card variant="outlined" sx={{ flex: 1 }} key={label}><CardContent><Typography color="text.secondary">{label}</Typography><Typography variant="h5" sx={{ mt: 1 }}>{value}</Typography></CardContent></Card>)}</Stack>
    <UsageTable title="Provider별 집계" headers={["Provider", "호출", "실패", "사용량", "예상 비용", "가격 상태"]}>{data.providers.map((item) => <tr key={item.provider}><td className="mono-text">{item.provider}</td><td>{fmt.format(item.calls)}</td><td>{fmt.format(item.failures)}</td><td>{fmt.format(item.units)}</td><td>{item.estimatedCostUsd == null ? "—" : usd.format(item.estimatedCostUsd)}</td><td><Chip size="small" label={item.pricingStatus} color={item.estimatedCostUsd != null ? "success" : "warning"} /></td></tr>)}</UsageTable>
    <UsageTable title="최근 물리 요청" headers={["시각", "Provider", "Operation / SKU", "결과", "지연", "비용"]}>{data.recentEvents.map((event) => <tr key={event.id}><td>{new Date(event.occurredAt).toLocaleString("ko-KR")}</td><td className="mono-text">{event.provider}</td><td><div className="mono-text">{event.operation}</div><small>{event.sku}</small></td><td><Chip size="small" label={event.httpStatus ? `${event.status} · ${event.httpStatus}` : event.status} color={event.status === "SUCCEEDED" ? "success" : "error"} />{event.failureType && <div><small>{event.failureType}</small></div>}</td><td>{fmt.format(event.durationMs)}ms</td><td>{event.estimatedCostUsd == null ? "—" : usd.format(event.estimatedCostUsd)}</td></tr>)}</UsageTable>
  </Stack>;
}

function UsageTable({ title, headers, children }: { title: string; headers: string[]; children: React.ReactNode }) { return <Card variant="outlined"><CardContent><Typography variant="h6" sx={{ mb: 2 }}>{title}</Typography><Box className="usage-table"><table><thead><tr>{headers.map((header) => <th key={header}>{header}</th>)}</tr></thead><tbody>{children}</tbody></table></Box></CardContent></Card>; }
