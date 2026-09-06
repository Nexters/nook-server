import { Alert, Box, Card, CardContent, CircularProgress, LinearProgress, MenuItem, Stack, TextField, Typography } from "@mui/material";
import { useEffect, useMemo, useState } from "react";
import { api } from "./api";

type Funnel = { signUps: number; activatedUsers: number; returnedUsers: number };
type Daily = { date: string; signUps: number; activatedUsers: number; activeUsers: number };
type Retention = { day: number; eligibleUsers: number; returnedUsers: number };
type Behavior = { eventName: string; users: number; events: number };
type ActiveUsers = { asOf: string; daily: number; weekly: number; monthly: number };
type UserAnalyticsOverview = {
  from: string;
  to: string;
  observedThrough: string;
  activationThreshold: number;
  firstEventAt?: string;
  funnel: Funnel;
  activeUsers: ActiveUsers;
  daily: Daily[];
  retention: Retention[];
  behaviors: Behavior[];
};

const count = new Intl.NumberFormat("ko-KR", { maximumFractionDigits: 0 });
const percent = new Intl.NumberFormat("ko-KR", { maximumFractionDigits: 1 });
const colors = { signUps: "#558EFF", activatedUsers: "#2BAE7F", activeUsers: "#FFA30E" };

export function UserAnalyticsPage() {
  const defaults = useMemo(() => defaultPeriod(), []);
  const [from, setFrom] = useState(defaults.from);
  const [to, setTo] = useState(defaults.to);
  const [activeDate, setActiveDate] = useState(defaults.to);
  const [threshold, setThreshold] = useState(3);
  const [overview, setOverview] = useState<UserAnalyticsOverview>();
  const [error, setError] = useState("");

  useEffect(() => {
    const controller = new AbortController();
    const query = new URLSearchParams({ from, to, activeDate, activationThreshold: String(threshold) });
    setOverview(undefined);
    setError("");
    api<UserAnalyticsOverview>(`/user-analytics?${query}`, { signal: controller.signal, cache: "no-store" })
      .then(setOverview)
      .catch((cause: unknown) => {
        if (cause instanceof DOMException && cause.name === "AbortError") return;
        setError(cause instanceof Error ? cause.message : "사용자 행동 분석을 불러오지 못했습니다.");
      });
    return () => controller.abort();
  }, [from, to, activeDate, threshold]);

  return <Stack spacing={3} sx={{ width: "100%", maxWidth: 1500 }}>
    <Box>
      <Typography variant="overline" color="primary.main" sx={{ fontWeight: 700, letterSpacing: ".08em" }}>USER JOURNEY</Typography>
      <Typography variant="h4">사용자 행동 분석</Typography>
      <Typography color="text.secondary">한 날짜의 활성 사용자와 기간별 가입·저장·재방문 흐름을 나누어 확인합니다.</Typography>
    </Box>
    <Card variant="outlined"><CardContent>
      <Stack direction={{ xs: "column", md: "row" }} sx={{ justifyContent: "space-between", alignItems: { md: "flex-start" }, gap: 2, mb: 2 }}>
        <Box>
          <Typography variant="h6">날짜 기준 활성 사용자</Typography>
          <Typography variant="body2" color="text.secondary">선택한 하루의 DAU와 그날을 끝으로 하는 7일 WAU·30일 MAU입니다.</Typography>
        </Box>
        <TextField label="활성 기준일" type="date" size="small" value={activeDate} onChange={(event) => setActiveDate(event.target.value)} slotProps={{ inputLabel: { shrink: true }, htmlInput: { max: defaults.to } }} />
      </Stack>
      {overview ? <Box sx={{ display: "grid", gridTemplateColumns: { xs: "1fr", md: "repeat(3, minmax(0, 1fr))" }, gap: 1.5 }}>
        <Metric label="일간 활성 사용자 · DAU" value={`${count.format(overview.activeUsers.daily)}명`} helper={overview.activeUsers.asOf} tone="primary.main" />
        <Metric label="주간 활성 사용자 · WAU" value={`${count.format(overview.activeUsers.weekly)}명`} helper="기준일 포함 최근 7일" tone="success.main" />
        <Metric label="월간 활성 사용자 · MAU" value={`${count.format(overview.activeUsers.monthly)}명`} helper="기준일 포함 최근 30일" tone="warning.main" />
      </Box> : !error && <Box sx={{ py: 4, textAlign: "center" }}><CircularProgress size={28} /></Box>}
    </CardContent></Card>
    <Box>
      <Stack direction={{ xs: "column", lg: "row" }} sx={{ justifyContent: "space-between", alignItems: { lg: "flex-end" }, gap: 2 }}>
        <Box>
          <Typography variant="h6">기간별 사용자 흐름</Typography>
          <Typography variant="body2" color="text.secondary">기간 안의 날짜별 DAU와 가입 cohort의 활성화·재방문을 조회합니다.</Typography>
        </Box>
      <Stack direction={{ xs: "column", md: "row" }} spacing={1.5} sx={{ alignItems: { md: "center" } }}>
        <TextField label="기간 시작일" type="date" size="small" value={from} onChange={(event) => setFrom(event.target.value)} slotProps={{ inputLabel: { shrink: true }, htmlInput: { max: to } }} />
        <TextField label="기간 종료일" type="date" size="small" value={to} onChange={(event) => setTo(event.target.value)} slotProps={{ inputLabel: { shrink: true }, htmlInput: { min: from, max: defaults.to } }} />
        <TextField select label="활성화 기준" size="small" value={threshold} onChange={(event) => setThreshold(Number(event.target.value))} sx={{ minWidth: 150 }}>
          {[1, 3, 5, 10].map((value) => <MenuItem value={value} key={value}>서로 다른 저장 {value}개</MenuItem>)}
        </TextField>
      </Stack>
      </Stack>
    </Box>
    {error ? <Alert severity="error">{error}</Alert> : !overview ? <Box sx={{ py: 8, textAlign: "center" }}><CircularProgress /></Box> : <Dashboard overview={overview} />}
  </Stack>;
}

function Dashboard({ overview }: { overview: UserAnalyticsOverview }) {
  const activationRate = ratio(overview.funnel.activatedUsers, overview.funnel.signUps);
  const returnRate = ratio(overview.funnel.returnedUsers, overview.funnel.activatedUsers);
  const hasEvents = overview.daily.some((day) => day.signUps + day.activatedUsers + day.activeUsers > 0);
  return <>
    <Alert severity="info" variant="outlined">
      신규 유입은 앱 최초 실행이 아닌 신규 가입 기준입니다. 조회 행동은 사용자·대상·날짜별로 중복 제거됩니다.
      {overview.firstEventAt && ` 선택 기간 첫 이벤트: ${new Date(overview.firstEventAt).toLocaleString("ko-KR")}`}
    </Alert>
    <Box sx={{ display: "grid", gridTemplateColumns: { xs: "1fr", sm: "repeat(2, minmax(0, 1fr))", xl: "repeat(4, minmax(0, 1fr))" }, gap: 1.5 }}>
      <Metric label="신규 가입" value={`${count.format(overview.funnel.signUps)}명`} helper={`${overview.from} ~ ${overview.to}`} tone="primary.main" />
      <Metric label={`저장 ${overview.activationThreshold}개 활성화`} value={`${count.format(overview.funnel.activatedUsers)}명`} helper={`가입자의 ${percent.format(activationRate)}%`} tone="success.main" />
      <Metric label="재방문 사용자" value={`${count.format(overview.funnel.returnedUsers)}명`} helper="활성화 다음 날 이후" tone="warning.main" />
      <Metric label="재방문 전환율" value={`${percent.format(returnRate)}%`} helper={`관측 기준 ${overview.observedThrough}`} tone="info.main" />
    </Box>
    {!hasEvents && <Alert severity="warning">선택한 기간에 수집된 사용자 행동 이벤트가 없습니다. DDL 적용 및 배포 이후 데이터부터 표시됩니다.</Alert>}
    <Box sx={{ display: "grid", gridTemplateColumns: { xs: "1fr", xl: "minmax(0, 1.15fr) minmax(0, .85fr)" }, gap: 2 }}>
      <FunnelChart funnel={overview.funnel} threshold={overview.activationThreshold} />
      <RetentionChart retention={overview.retention} />
    </Box>
    <DailyChart daily={overview.daily} />
    <BehaviorChart behaviors={overview.behaviors} />
  </>;
}

function Metric({ label, value, helper, tone }: { label: string; value: string; helper: string; tone: string }) {
  return <Card variant="outlined"><CardContent>
    <Box sx={{ width: 34, height: 5, borderRadius: 4, bgcolor: tone, mb: 2 }} />
    <Typography variant="body2" color="text.secondary">{label}</Typography>
    <Typography variant="h4" sx={{ mt: .5 }}>{value}</Typography>
    <Typography variant="caption" color="text.secondary">{helper}</Typography>
  </CardContent></Card>;
}

function FunnelChart({ funnel, threshold }: { funnel: Funnel; threshold: number }) {
  const steps = [
    { label: "신규 가입", value: funnel.signUps, color: colors.signUps },
    { label: `저장 ${threshold}개 활성화`, value: funnel.activatedUsers, color: colors.activatedUsers },
    { label: "다음 날 이후 재방문", value: funnel.returnedUsers, color: colors.activeUsers },
  ];
  const maximum = Math.max(1, funnel.signUps);
  return <Card variant="outlined"><CardContent>
    <Typography variant="h6">가입 → 활성화 → 재방문</Typography>
    <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>선택 기간에 가입한 cohort가 서로 다른 저장 대상을 쌓고 다시 행동한 비율입니다.</Typography>
    <Stack spacing={2.5}>{steps.map((step, index) => <Box key={step.label}>
      <Stack direction="row" sx={{ justifyContent: "space-between", alignItems: "baseline", mb: .8 }}>
        <Typography variant="subtitle2">{index + 1}. {step.label}</Typography>
        <Box sx={{ textAlign: "right" }}><Typography component="span" variant="h6">{count.format(step.value)}명</Typography>{index > 0 && <Typography component="span" variant="caption" color="text.secondary"> · {percent.format(ratio(step.value, steps[index - 1].value))}%</Typography>}</Box>
      </Stack>
      <Box sx={{ height: 34, borderRadius: 1.5, bgcolor: "action.hover", overflow: "hidden" }}><Box sx={{ width: `${Math.max(step.value ? 5 : 0, ratio(step.value, maximum))}%`, height: "100%", borderRadius: 1.5, bgcolor: step.color, transition: "width .25s ease" }} /></Box>
    </Box>)}</Stack>
  </CardContent></Card>;
}

function RetentionChart({ retention }: { retention: Retention[] }) {
  return <Card variant="outlined"><CardContent sx={{ height: "100%" }}>
    <Typography variant="h6">Exact-day 재방문</Typography>
    <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>활성화일을 D0으로 두고 해당 날짜에 다시 행동한 비율입니다.</Typography>
    <Stack spacing={2}>{retention.map((item) => {
      const value = ratio(item.returnedUsers, item.eligibleUsers);
      return <Box key={item.day} sx={{ p: 2, border: 1, borderColor: "divider", borderRadius: 2 }}>
        <Stack direction="row" sx={{ justifyContent: "space-between", alignItems: "center", mb: 1 }}><Typography variant="subtitle1">D{item.day}</Typography><Typography variant="h5">{item.eligibleUsers ? `${percent.format(value)}%` : "—"}</Typography></Stack>
        <LinearProgress variant="determinate" value={value} color={item.day === 1 ? "primary" : item.day === 7 ? "success" : "warning"} sx={{ height: 9, borderRadius: 5 }} />
        <Typography variant="caption" color="text.secondary">관측 가능 {count.format(item.eligibleUsers)}명 중 {count.format(item.returnedUsers)}명</Typography>
      </Box>;
    })}</Stack>
  </CardContent></Card>;
}

function DailyChart({ daily }: { daily: Daily[] }) {
  const width = 920;
  const height = 250;
  const padding = 30;
  const maximum = Math.max(1, ...daily.flatMap((item) => [item.signUps, item.activatedUsers, item.activeUsers]));
  const points = (key: keyof Pick<Daily, "signUps" | "activatedUsers" | "activeUsers">) => daily.map((item, index) => {
    const x = padding + index * ((width - padding * 2) / Math.max(1, daily.length - 1));
    const y = height - padding - item[key] / maximum * (height - padding * 2);
    return `${x},${y}`;
  }).join(" ");
  const labelStep = Math.max(1, Math.ceil(daily.length / 7));
  return <Card variant="outlined"><CardContent>
    <Stack direction={{ xs: "column", md: "row" }} sx={{ justifyContent: "space-between", gap: 1, mb: 2 }}>
      <Box><Typography variant="h6">일별 사용자 흐름</Typography><Typography variant="body2" color="text.secondary">가입, 최초 활성화, 분석 대상 행동 사용자 추이를 비교합니다.</Typography></Box>
      <Stack direction="row" spacing={2}>{Object.entries(colors).map(([key, color]) => <Stack direction="row" spacing={.7} sx={{ alignItems: "center" }} key={key}><Box sx={{ width: 10, height: 10, borderRadius: "50%", bgcolor: color }} /><Typography variant="caption">{{ signUps: "가입", activatedUsers: "활성화", activeUsers: "행동 사용자" }[key as keyof typeof colors]}</Typography></Stack>)}</Stack>
    </Stack>
    <Box sx={{ overflowX: "auto" }}>
      <Box component="svg" role="img" aria-label="가입, 활성화, 행동 사용자의 일별 추이" viewBox={`0 0 ${width} ${height}`} sx={{ display: "block", width: "100%", minWidth: 680, height: 270 }}>
        {[0, .5, 1].map((ratioValue) => <line key={ratioValue} x1={padding} y1={padding + ratioValue * (height - padding * 2)} x2={width - padding} y2={padding + ratioValue * (height - padding * 2)} stroke="#E4E6E9" strokeWidth="1" />)}
        {(Object.keys(colors) as Array<keyof typeof colors>).map((key) => <polyline key={key} points={points(key)} fill="none" stroke={colors[key]} strokeWidth="4" strokeLinecap="round" strokeLinejoin="round" />)}
        {daily.map((item, index) => index % labelStep === 0 && <text key={item.date} x={padding + index * ((width - padding * 2) / Math.max(1, daily.length - 1))} y={height - 5} textAnchor="middle" fill="#848B96" fontSize="11">{item.date.slice(5)}</text>)}
      </Box>
    </Box>
  </CardContent></Card>;
}

function BehaviorChart({ behaviors }: { behaviors: Behavior[] }) {
  const maximum = Math.max(1, ...behaviors.map((item) => item.users));
  return <Card variant="outlined"><CardContent>
    <Typography variant="h6">재방문 후 행동</Typography>
    <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>활성화 다음 날 이후 어떤 행동으로 다시 돌아왔는지 보여줍니다.</Typography>
    {behaviors.length === 0 ? <Alert severity="info">선택 기간에 활성화 이후 재방문 행동이 없습니다.</Alert> : <Box sx={{ display: "grid", gridTemplateColumns: { xs: "1fr", lg: "repeat(2, minmax(0, 1fr))" }, gap: 1.5 }}>{behaviors.map((item) => <Box key={item.eventName} sx={{ p: 2, border: 1, borderColor: "divider", borderRadius: 2 }}>
      <Stack direction="row" sx={{ justifyContent: "space-between", alignItems: "baseline", mb: 1 }}><Box><Typography variant="subtitle1">{behaviorLabel(item.eventName)}</Typography><Typography variant="caption" className="mono-text" color="text.secondary">{item.eventName}</Typography></Box><Typography variant="h6">{count.format(item.users)}명</Typography></Stack>
      <LinearProgress variant="determinate" value={ratio(item.users, maximum)} sx={{ height: 9, borderRadius: 5 }} />
      <Typography variant="caption" color="text.secondary">중복 제거 이벤트 {count.format(item.events)}건</Typography>
    </Box>)}</Box>}
  </CardContent></Card>;
}

function ratio(value: number, total: number) { return total > 0 ? Math.min(100, Math.max(0, value / total * 100)) : 0; }
function behaviorLabel(eventName: string) { return ({ archive_view: "아카이브 다시 보기", post_view: "게시물 다시 보기", place_view: "장소 다시 보기", map_view: "지도 다시 보기", post_save: "게시물 추가 저장", place_save: "장소 추가 저장" } as Record<string, string>)[eventName] ?? eventName; }
function localDate(date: Date) { const copy = new Date(date.getTime() - date.getTimezoneOffset() * 60_000); return copy.toISOString().slice(0, 10); }
function defaultPeriod() { const to = new Date(); const from = new Date(to); from.setDate(from.getDate() - 29); return { from: localDate(from), to: localDate(to) }; }
