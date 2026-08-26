import { Alert, Box, Card, CardContent, Chip, CircularProgress, MenuItem, Stack, TextField, Typography } from "@mui/material";
import { useEffect, useMemo, useState } from "react";
import { api } from "./api";

type OverviewProvider = { provider: string; displayName: string; category: string; purpose: string; runtimes: string[]; credentialConfigured: boolean; operationalState: string; stateReason: string; policy: string };
type Overview = { providers: OverviewProvider[] };
type BillingSku = { sku: string; usageUnits: number; actualCostUsd: number; source: string; sourceUpdatedAt: string };
type BillingProvider = { provider: string; status: string; lastAttemptedAt?: string; lastSucceededAt?: string; errorMessage?: string; usageUnits: number; actualCostUsd: number; source?: string; skus: BillingSku[] };
type BillingOverview = { providers: BillingProvider[] };
type TokenSlice = { inputTokens: number; cachedInputTokens: number; outputTokens: number; totalTokens: number };
type TokenDaily = TokenSlice & { date: string };
type TokenBreakdown = TokenSlice & { feature: string; model: string; requests: number };
type OpenAiTokenOverview = TokenSlice & { from: string; to: string; daily: TokenDaily[]; breakdowns: TokenBreakdown[] };

const usd = new Intl.NumberFormat("en-US", { style: "currency", currency: "USD", minimumFractionDigits: 2, maximumFractionDigits: 4 });
const number = new Intl.NumberFormat("ko-KR", { maximumFractionDigits: 4 });
const tokens = new Intl.NumberFormat("ko-KR", { maximumFractionDigits: 0 });

export function ExternalProviderUsagePage() {
  const currentMonth = useMemo(() => new Date().toISOString().slice(0, 7), []);
  const [month, setMonth] = useState(currentMonth);
  const [overviewState, setOverviewState] = useState("ALL");
  const [overview, setOverview] = useState<Overview>();
  const [billing, setBilling] = useState<BillingOverview>();
  const [openAiTokens, setOpenAiTokens] = useState<OpenAiTokenOverview>();
  const [error, setError] = useState("");

  useEffect(() => {
    const [year, monthNumber] = month.split("-").map(Number);
    const nextMonth = new Date(Date.UTC(year, monthNumber, 1)).toISOString().slice(0, 10);
    const periodQuery = new URLSearchParams({ from: `${month}-01`, to: nextMonth });
    setOverview(undefined); setBilling(undefined); setOpenAiTokens(undefined); setError("");
    Promise.all([
      api<Overview>("/external-provider-usage/overview"),
      api<BillingOverview>(`/external-provider-usage/billing?${periodQuery}`),
      api<OpenAiTokenOverview>(`/external-provider-usage/openai-tokens?${periodQuery}`),
    ]).then(([catalog, actualBilling, tokenUsage]) => { setOverview(catalog); setBilling(actualBilling); setOpenAiTokens(tokenUsage); })
      .catch((cause: unknown) => setError(cause instanceof Error ? cause.message : "외부 API 현황을 불러오지 못했습니다."));
  }, [month]);

  if (error) return <Alert severity="error">{error}</Alert>;
  if (!overview || !billing || !openAiTokens) return <Box sx={{ p: 5, textAlign: "center" }}><CircularProgress /></Box>;
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
    <OpenAiTokenDashboard usage={openAiTokens} />
  </Stack>;
}

function BillingTable({ billing }: { billing: BillingOverview }) {
  return <Card variant="outlined"><CardContent><Typography variant="h6">공식 빌링 비용</Typography><Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>공급자 공식 API에서 매시간 동기화한 계정 기준 데이터입니다. 로컬 호출 기록이나 고정 단가로 추정하지 않습니다.</Typography><Box className="usage-table"><table><thead><tr>{["공급자 / SKU", "공급자 사용량", "실제 비용", "동기화 상태", "데이터 출처"].map((header) => <th key={header}>{header}</th>)}</tr></thead><tbody>{billing.providers.flatMap((item) => item.skus.length ? item.skus.map((sku, index) => <tr key={`${item.provider}:${sku.sku}`}><td><strong>{index === 0 ? item.provider : ""}</strong><div className="mono-text"><small>{sku.sku}</small></div></td><td>{number.format(sku.usageUnits)}</td><td><strong>{usd.format(sku.actualCostUsd)}</strong></td><td><Chip size="small" color={item.status === "SUCCEEDED" ? "success" : "default"} label={item.status} /><div><small>성공 {dateOrDash(item.lastSucceededAt)}</small></div>{item.errorMessage && <div><small>{item.errorMessage}</small></div>}</td><td><small>{sku.source}</small><div><small>관측 {dateOrDash(sku.sourceUpdatedAt)}</small></div></td></tr>) : [<tr key={item.provider}><td><strong>{item.provider}</strong></td><td>—</td><td>—</td><td><Chip size="small" label={item.status} /></td><td>{item.errorMessage ?? "공식 빌링 연동이 준비되지 않음"}</td></tr>])}</tbody></table></Box></CardContent></Card>;
}

function OpenAiTokenDashboard({ usage }: { usage: OpenAiTokenOverview }) {
  const uncachedInput = Math.max(0, usage.inputTokens - usage.cachedInputTokens);
  const compositionTotal = uncachedInput + usage.cachedInputTokens + usage.outputTokens;
  const maximumDaily = Math.max(1, ...usage.daily.map((item) => item.totalTokens));
  const cards = [
    ["전체 토큰", usage.totalTokens, "Responses API total_tokens"],
    ["입력 토큰", usage.inputTokens, "캐시 입력을 포함"],
    ["출력 토큰", usage.outputTokens, "모델 출력과 reasoning 포함"],
    ["캐시 입력", usage.cachedInputTokens, usage.inputTokens ? `입력의 ${Math.round(usage.cachedInputTokens / usage.inputTokens * 100)}%` : "입력의 0%"],
  ] as const;
  return <Card variant="outlined"><CardContent>
    <Typography variant="h6">OpenAI 토큰 오버뷰</Typography>
    <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>OpenAI Responses API 성공 응답의 공식 usage를 집계합니다. 비용 환산이나 실패 요청 추정은 포함하지 않습니다.</Typography>
    <Box sx={{ display: "grid", gridTemplateColumns: { xs: "1fr", sm: "repeat(2, 1fr)", lg: "repeat(4, 1fr)" }, gap: 1.5, mb: 3 }}>
      {cards.map(([label, value, helper]) => <Box key={label} sx={{ p: 2, border: 1, borderColor: "divider", borderRadius: 2 }}><Typography variant="body2" color="text.secondary">{label}</Typography><Typography variant="h5">{tokens.format(value)}</Typography><Typography variant="caption" color="text.secondary">{helper}</Typography></Box>)}
    </Box>
    <Typography variant="subtitle2" sx={{ mb: 1 }}>토큰 구성</Typography>
    <Stack direction="row" sx={{ height: 18, borderRadius: 8, overflow: "hidden", bgcolor: "action.hover", mb: 1 }}>
      <Box title={`비캐시 입력 ${tokens.format(uncachedInput)}`} sx={{ width: percent(uncachedInput, compositionTotal), bgcolor: "primary.main" }} />
      <Box title={`캐시 입력 ${tokens.format(usage.cachedInputTokens)}`} sx={{ width: percent(usage.cachedInputTokens, compositionTotal), bgcolor: "success.main" }} />
      <Box title={`출력 ${tokens.format(usage.outputTokens)}`} sx={{ width: percent(usage.outputTokens, compositionTotal), bgcolor: "warning.main" }} />
    </Stack>
    <Stack direction={{ xs: "column", sm: "row" }} spacing={2} sx={{ mb: 3 }}><Legend color="primary.main" label={`비캐시 입력 ${tokens.format(uncachedInput)}`} /><Legend color="success.main" label={`캐시 입력 ${tokens.format(usage.cachedInputTokens)}`} /><Legend color="warning.main" label={`출력 ${tokens.format(usage.outputTokens)}`} /></Stack>
    <Typography variant="subtitle2" sx={{ mb: 1 }}>일별 추이</Typography>
    {usage.daily.length === 0 ? <Typography color="text.secondary" sx={{ mb: 3 }}>선택한 기간에 기록된 토큰 사용량이 없습니다.</Typography> : <Stack spacing={1.2} sx={{ mb: 3 }}>{usage.daily.map((item) => {
      const cached = item.cachedInputTokens;
      const plainInput = Math.max(0, item.inputTokens - cached);
      return <Stack key={item.date} direction="row" spacing={1.5} sx={{ alignItems: "center" }}><Typography variant="caption" sx={{ width: 76 }}>{item.date.slice(5)}</Typography><Stack direction="row" sx={{ flex: 1, height: 12, borderRadius: 6, overflow: "hidden", bgcolor: "action.hover" }}><Box sx={{ width: percent(plainInput, maximumDaily), bgcolor: "primary.main" }} /><Box sx={{ width: percent(cached, maximumDaily), bgcolor: "success.main" }} /><Box sx={{ width: percent(item.outputTokens, maximumDaily), bgcolor: "warning.main" }} /></Stack><Typography variant="caption" sx={{ minWidth: 80, textAlign: "right" }}>{tokens.format(item.totalTokens)}</Typography></Stack>;
    })}</Stack>}
    <Typography variant="subtitle2" sx={{ mb: 1 }}>기능·모델별 사용량</Typography>
    <Box className="usage-table"><table><thead><tr>{["기능", "모델", "응답 수", "입력", "캐시 입력", "출력", "전체"].map((header) => <th key={header}>{header}</th>)}</tr></thead><tbody>{usage.breakdowns.length ? usage.breakdowns.map((item) => <tr key={`${item.feature}:${item.model}`}><td><strong>{featureLabel(item.feature)}</strong><div className="mono-text"><small>{item.feature}</small></div></td><td className="mono-text"><small>{item.model}</small></td><td>{tokens.format(item.requests)}</td><td>{tokens.format(item.inputTokens)}</td><td>{tokens.format(item.cachedInputTokens)}</td><td>{tokens.format(item.outputTokens)}</td><td><strong>{tokens.format(item.totalTokens)}</strong></td></tr>) : <tr><td colSpan={7}>선택한 기간에 기록된 토큰 사용량이 없습니다.</td></tr>}</tbody></table></Box>
  </CardContent></Card>;
}

function Legend({ color, label }: { color: string; label: string }) { return <Stack direction="row" spacing={0.7} sx={{ alignItems: "center" }}><Box sx={{ width: 10, height: 10, borderRadius: "50%", bgcolor: color }} /><Typography variant="caption">{label}</Typography></Stack>; }
function percent(value: number, total: number) { return total > 0 ? `${value / total * 100}%` : "0%"; }
function featureLabel(feature: string) { return ({ post_content_inference: "게시물 내용 추론", post_title_inference: "게시물 제목 추론", place_clues: "장소 단서 추출", place_candidate_selection: "장소 후보 선택", place_tags: "장소 태그 추론", cover_title_extraction: "표지 제목 추출", image_text_extraction: "이미지 텍스트 추출", post_title_selection: "최종 제목 선택" } as Record<string, string>)[feature] ?? feature; }

function stateLabel(state: string) { return ({ ACTIVE: "사용 중", FALLBACK: "Fallback", STANDBY: "대기", DISABLED: "비활성", MISCONFIGURED: "설정 누락" } as Record<string, string>)[state] ?? state; }
function stateColor(state: string): "success" | "info" | "warning" | "default" | "error" { const colors: Record<string, "success" | "info" | "warning" | "default" | "error"> = { ACTIVE: "success", FALLBACK: "info", STANDBY: "warning", DISABLED: "default", MISCONFIGURED: "error" }; return colors[state] ?? "default"; }
function dateOrDash(value?: string) { return value ? new Date(value).toLocaleString("ko-KR") : "—"; }
