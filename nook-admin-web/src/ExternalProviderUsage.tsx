import { Alert, Box, Card, CardContent, Chip, CircularProgress, MenuItem, Stack, TextField, Typography } from "@mui/material";
import { useEffect, useMemo, useState } from "react";
import { api } from "./api";

type OverviewProvider = { provider: string; displayName: string; category: string; purpose: string; runtimes: string[]; credentialConfigured: boolean; operationalState: string; stateReason: string; policy: string };
type Overview = { providers: OverviewProvider[] };
type BillingSku = { sku: string; usageUnits: number; actualCostUsd: number; source: string; sourceUpdatedAt: string };
type BillingProvider = { provider: string; status: string; lastAttemptedAt?: string; lastSucceededAt?: string; errorMessage?: string; usageUnits: number; actualCostUsd: number; source?: string; skus: BillingSku[] };
type BillingOverview = { providers: BillingProvider[] };

const usd = new Intl.NumberFormat("en-US", { style: "currency", currency: "USD", minimumFractionDigits: 2, maximumFractionDigits: 4 });
const number = new Intl.NumberFormat("ko-KR", { maximumFractionDigits: 4 });

export function ExternalProviderUsagePage() {
  const currentMonth = useMemo(() => new Date().toISOString().slice(0, 7), []);
  const [month, setMonth] = useState(currentMonth);
  const [overviewState, setOverviewState] = useState("ALL");
  const [overview, setOverview] = useState<Overview>();
  const [billing, setBilling] = useState<BillingOverview>();
  const [error, setError] = useState("");

  useEffect(() => {
    const from = new Date(`${month}-01T00:00:00+09:00`);
    const to = new Date(from);
    to.setMonth(to.getMonth() + 1);
    const billingQuery = new URLSearchParams({ from: `${month}-01`, to: to.toISOString().slice(0, 10) });
    setOverview(undefined); setBilling(undefined); setError("");
    Promise.all([
      api<Overview>("/external-provider-usage/overview"),
      api<BillingOverview>(`/external-provider-usage/billing?${billingQuery}`),
    ]).then(([catalog, actualBilling]) => { setOverview(catalog); setBilling(actualBilling); })
      .catch((cause: unknown) => setError(cause instanceof Error ? cause.message : "외부 API 현황을 불러오지 못했습니다."));
  }, [month]);

  if (error) return <Alert severity="error">{error}</Alert>;
  if (!overview || !billing) return <Box sx={{ p: 5, textAlign: "center" }}><CircularProgress /></Box>;
  const visibleOverview = overview.providers.filter((item) => overviewState === "ALL" || item.operationalState === overviewState);

  return <Stack spacing={3} sx={{ maxWidth: 1500 }}>
    <Stack direction={{ xs: "column", md: "row" }} sx={{ justifyContent: "space-between", gap: 2 }}>
      <Box><Typography variant="h4">외부 API 현황</Typography><Typography color="text.secondary">연동 상태와 호출 정책, 공급자 공식 빌링 API의 실제 비용을 확인합니다.</Typography></Box>
      <TextField type="month" size="small" value={month} onChange={(event) => setMonth(event.target.value)} />
    </Stack>
    <Card variant="outlined"><CardContent>
      <Stack direction={{ xs: "column", md: "row" }} sx={{ justifyContent: "space-between", gap: 2, mb: 2 }}>
        <Box><Typography variant="h6">연동·운영 오버뷰</Typography><Typography variant="body2" color="text.secondary">호출 이력과 무관하게 현재 runtime 설정과 credential 상태를 표시합니다.</Typography></Box>
        <TextField select size="small" label="운영 상태" value={overviewState} onChange={(event) => setOverviewState(event.target.value)} sx={{ minWidth: 170 }}><MenuItem value="ALL">전체</MenuItem>{["ACTIVE", "FALLBACK", "STANDBY", "DISABLED", "MISCONFIGURED"].map((value) => <MenuItem key={value} value={value}>{stateLabel(value)}</MenuItem>)}</TextField>
      </Stack>
      <Box className="usage-table"><table><thead><tr>{["외부 API", "용도", "운영 상태", "호출 정책"].map((header) => <th key={header}>{header}</th>)}</tr></thead><tbody>{visibleOverview.map((item) => <tr key={item.provider}><td><strong>{item.displayName}</strong><div className="mono-text"><small>{item.provider}</small></div><small>{item.runtimes.join(" · ")}</small></td><td><Chip size="small" variant="outlined" label={item.category} /><div>{item.purpose}</div></td><td><Chip size="small" label={stateLabel(item.operationalState)} color={stateColor(item.operationalState)} /><div><small>{item.stateReason}</small></div>{!item.credentialConfigured && <div><small>credential 누락</small></div>}</td><td><small>{item.policy}</small></td></tr>)}</tbody></table></Box>
    </CardContent></Card>
    <BillingTable billing={billing} />
  </Stack>;
}

function BillingTable({ billing }: { billing: BillingOverview }) {
  return <Card variant="outlined"><CardContent><Typography variant="h6">공식 빌링 비용</Typography><Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>공급자 공식 API에서 매시간 동기화한 계정 기준 데이터입니다. 로컬 호출 기록이나 고정 단가로 추정하지 않습니다.</Typography><Box className="usage-table"><table><thead><tr>{["공급자 / SKU", "공급자 사용량", "실제 비용", "동기화 상태", "데이터 출처"].map((header) => <th key={header}>{header}</th>)}</tr></thead><tbody>{billing.providers.flatMap((item) => item.skus.length ? item.skus.map((sku, index) => <tr key={`${item.provider}:${sku.sku}`}><td><strong>{index === 0 ? item.provider : ""}</strong><div className="mono-text"><small>{sku.sku}</small></div></td><td>{number.format(sku.usageUnits)}</td><td><strong>{usd.format(sku.actualCostUsd)}</strong></td><td><Chip size="small" color={item.status === "SUCCEEDED" ? "success" : "default"} label={item.status} /><div><small>성공 {dateOrDash(item.lastSucceededAt)}</small></div>{item.errorMessage && <div><small>{item.errorMessage}</small></div>}</td><td><small>{sku.source}</small><div><small>관측 {dateOrDash(sku.sourceUpdatedAt)}</small></div></td></tr>) : [<tr key={item.provider}><td><strong>{item.provider}</strong></td><td>—</td><td>—</td><td><Chip size="small" label={item.status} /></td><td>{item.errorMessage ?? "공식 빌링 연동이 준비되지 않음"}</td></tr>])}</tbody></table></Box></CardContent></Card>;
}

function stateLabel(state: string) { return ({ ACTIVE: "사용 중", FALLBACK: "Fallback", STANDBY: "대기", DISABLED: "비활성", MISCONFIGURED: "설정 누락" } as Record<string, string>)[state] ?? state; }
function stateColor(state: string): "success" | "info" | "warning" | "default" | "error" { const colors: Record<string, "success" | "info" | "warning" | "default" | "error"> = { ACTIVE: "success", FALLBACK: "info", STANDBY: "warning", DISABLED: "default", MISCONFIGURED: "error" }; return colors[state] ?? "default"; }
function dateOrDash(value?: string) { return value ? new Date(value).toLocaleString("ko-KR") : "—"; }
