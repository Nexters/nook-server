import { Alert, Box, Button, Card, CardContent, Chip, MenuItem, Stack, TextField, Typography } from "@mui/material";
import { useEffect, useMemo, useState } from "react";
import { api } from "./api";

type ProviderCost = { provider: string; callCount: number; estimatedCostKrw: number; monthlyBudgetKrw?: number; budgetUsagePercent?: number; budgetMode?: string; status: string };
type Dashboard = { from: string; to: string; totalCallCount: number; totalEstimatedCostKrw: number; providers: ProviderCost[] };
type Price = { provider: string; sku: string; unitPriceKrw: number; unitSize: number; freeMonthlyUnits: number; sourceCurrency: string; sourceUnitPrice: number; managed: boolean; enabled: boolean };
type Budget = { provider: string; monthlyBudgetKrw: number; mode: "ALERT_ONLY" | "BLOCK"; enabled: boolean };
type Policies = { prices: Price[]; budgets: Budget[] };
type Usage = { provider: string; sku: string; feature: string; callCount: number; totalUnits: number; estimatedCostKrw: number };

const won = new Intl.NumberFormat("ko-KR", { style: "currency", currency: "KRW", maximumFractionDigits: 0 });

export function ExternalApiCostsPage() {
  const [month, setMonth] = useState(new Date().toISOString().slice(0, 7));
  const [dashboard, setDashboard] = useState<Dashboard>();
  const [policies, setPolicies] = useState<Policies>({ prices: [], budgets: [] });
  const [usage, setUsage] = useState<Usage[]>([]);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);
  const range = useMemo(() => {
    const from = new Date(`${month}-01T00:00:00Z`);
    const to = new Date(Date.UTC(from.getUTCFullYear(), from.getUTCMonth() + 1, 1));
    return { from: from.toISOString(), to: to.toISOString() };
  }, [month]);

  const load = async () => {
    setLoading(true); setError("");
    try {
      const [nextDashboard, nextPolicies, nextUsage] = await Promise.all([
        api<Dashboard>(`/external-api-costs/dashboard?month=${month}`),
        api<Policies>("/external-api-costs/policies"),
        api<Usage[]>(`/external-api-costs/usage?from=${encodeURIComponent(range.from)}&to=${encodeURIComponent(range.to)}`),
      ]);
      setDashboard(nextDashboard); setPolicies(nextPolicies); setUsage(nextUsage);
    } catch (cause) { setError(cause instanceof Error ? cause.message : "비용 정보를 불러오지 못했습니다."); }
    finally { setLoading(false); }
  };
  useEffect(() => { void load(); }, [month]);

  return <Stack spacing={3} sx={{ p: { xs: 2, md: 3 } }}>
    <Stack direction={{ xs: "column", sm: "row" }} spacing={2} sx={{ justifyContent: "space-between", alignItems: { sm: "center" } }}>
      <Box><Typography variant="h4">외부 API 비용</Typography><Typography color="text.secondary">호출량, 예상 비용과 provider별 월 예산을 관리합니다.</Typography></Box>
      <TextField type="month" label="조회 월" value={month} onChange={(event) => setMonth(event.target.value)} size="small" />
    </Stack>
    {error && <Alert severity="error" action={<Button onClick={() => void load()}>다시 시도</Button>}>{error}</Alert>}
    <Stack direction={{ xs: "column", md: "row" }} spacing={2}>
      <Summary label="전체 호출" value={`${dashboard?.totalCallCount.toLocaleString() ?? "-"}회`} />
      <Summary label="예상 비용" value={dashboard ? won.format(dashboard.totalEstimatedCostKrw) : "-"} />
      <Summary label="활성 단가" value={`${policies.prices.filter((price) => price.enabled).length}개`} />
    </Stack>
    <Typography variant="h6">Provider 예산 현황</Typography>
    <Stack direction={{ xs: "column", lg: "row" }} spacing={2} sx={{ flexWrap: "wrap" }}>
      {dashboard?.providers.map((provider) => <ProviderCard key={provider.provider} provider={provider} budget={policies.budgets.find((item) => item.provider === provider.provider)} onSaved={load} />)}
      {!loading && dashboard?.providers.length === 0 && <Alert severity="info">이번 달 사용량과 설정된 예산이 없습니다.</Alert>}
    </Stack>
    <Typography variant="h6">SKU별 사용량</Typography>
    <Card variant="outlined"><CardContent><Stack spacing={1.5}>{usage.map((item) => <Stack key={`${item.provider}-${item.sku}-${item.feature}`} direction={{ xs: "column", md: "row" }} sx={{ justifyContent: "space-between", gap: 1, py: 1, borderBottom: "1px solid", borderColor: "divider" }}><Box><Typography variant="subtitle2">{item.provider} · {item.sku}</Typography><Typography variant="body2" color="text.secondary">{item.feature}</Typography></Box><Typography>{item.callCount.toLocaleString()}회 · {won.format(item.estimatedCostKrw)}</Typography></Stack>)}</Stack></CardContent></Card>
    <Typography variant="h6">단가 정책</Typography>
    <Stack spacing={2}>{policies.prices.map((price) => <PriceCard key={`${price.provider}-${price.sku}`} price={price} onSaved={load} />)}</Stack>
  </Stack>;
}

function Summary({ label, value }: { label: string; value: string }) { return <Card variant="outlined" sx={{ flex: 1 }}><CardContent><Typography color="text.secondary" variant="body2">{label}</Typography><Typography variant="h5" sx={{ mt: 1 }}>{value}</Typography></CardContent></Card>; }

function ProviderCard({ provider, budget, onSaved }: { provider: ProviderCost; budget?: Budget; onSaved: () => Promise<void> }) {
  const [amount, setAmount] = useState(budget?.monthlyBudgetKrw ?? 0); const [mode, setMode] = useState<Budget["mode"]>(budget?.mode ?? "ALERT_ONLY"); const [saving, setSaving] = useState(false);
  useEffect(() => { setAmount(budget?.monthlyBudgetKrw ?? 0); setMode(budget?.mode ?? "ALERT_ONLY"); }, [budget]);
  const save = async () => { setSaving(true); try { await api(`/external-api-costs/budgets/${provider.provider}`, { method: "PUT", body: JSON.stringify({ monthlyBudgetKrw: amount, mode, enabled: true }) }); await onSaved(); } finally { setSaving(false); } };
  const color = provider.status === "EXCEEDED" ? "error" : provider.status === "CRITICAL" ? "warning" : provider.status === "WARNING" ? "info" : "success";
  return <Card variant="outlined" sx={{ flex: "1 1 320px" }}><CardContent><Stack spacing={2}><Stack direction="row" sx={{ justifyContent: "space-between" }}><Typography variant="h6">{provider.provider}</Typography><Chip size="small" color={color} label={provider.status} /></Stack><Typography>{provider.callCount.toLocaleString()}회 · {won.format(provider.estimatedCostKrw)}</Typography><Typography variant="body2" color="text.secondary">예산 사용률 {provider.budgetUsagePercent?.toFixed(1) ?? "-"}%</Typography><TextField type="number" label="월 예산(원)" value={amount} onChange={(event) => setAmount(Number(event.target.value))} slotProps={{ htmlInput: { min: 0 } }} /><TextField select label="정책" value={mode} onChange={(event) => setMode(event.target.value as Budget["mode"])}><MenuItem value="ALERT_ONLY">알림만</MenuItem><MenuItem value="BLOCK">초과 호출 차단</MenuItem></TextField><Button variant="contained" disabled={saving} onClick={() => void save()}>{saving ? "저장 중..." : "예산 저장"}</Button></Stack></CardContent></Card>;
}

function PriceCard({ price, onSaved }: { price: Price; onSaved: () => Promise<void> }) {
  const [unitPrice, setUnitPrice] = useState(price.unitPriceKrw); const [unitSize, setUnitSize] = useState(price.unitSize); const [enabled, setEnabled] = useState(price.enabled); const [saving, setSaving] = useState(false);
  useEffect(() => { setUnitPrice(price.unitPriceKrw); setUnitSize(price.unitSize); setEnabled(price.enabled); }, [price]);
  const save = async () => { setSaving(true); try { await api(`/external-api-costs/prices/${price.provider}/${price.sku}`, { method: "PUT", body: JSON.stringify({ unitPriceKrw: unitPrice, unitSize, enabled }) }); await onSaved(); } finally { setSaving(false); } };
  return <Card variant="outlined"><CardContent><Stack direction={{ xs: "column", md: "row" }} spacing={2} sx={{ alignItems: { md: "center" } }}><Box sx={{ flex: 1 }}><Stack direction="row" spacing={1} sx={{ alignItems: "center" }}><Typography variant="subtitle1">{price.provider} · {price.sku}</Typography>{price.managed && <Chip size="small" label="공식 기본값" />}</Stack><Typography variant="body2" color="text.secondary">원본 {price.sourceCurrency} {price.sourceUnitPrice} · 무료 {price.freeMonthlyUnits.toLocaleString()} units</Typography></Box><TextField size="small" type="number" label="원화 단가" value={unitPrice} onChange={(event) => setUnitPrice(Number(event.target.value))} slotProps={{ htmlInput: { min: 0 } }} /><TextField size="small" type="number" label="단위 크기" value={unitSize} onChange={(event) => setUnitSize(Number(event.target.value))} slotProps={{ htmlInput: { min: 0.000001 } }} /><Button variant={enabled ? "outlined" : "text"} onClick={() => setEnabled((value) => !value)}>{enabled ? "활성" : "비활성"}</Button><Button variant="contained" disabled={saving || unitSize <= 0 || unitPrice < 0} onClick={() => void save()}>{saving ? "저장 중..." : "단가 저장"}</Button></Stack></CardContent></Card>;
}
