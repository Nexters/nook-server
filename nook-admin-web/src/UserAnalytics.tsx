import { Accordion, AccordionDetails, AccordionSummary, Alert, Box, Button, Card, CardActionArea, CardContent, Chip, Collapse, MenuItem, Stack, TextField, Typography } from "@mui/material";
import ExpandMoreIcon from "@mui/icons-material/ExpandMore";
import RefreshIcon from "@mui/icons-material/Refresh";
import TuneIcon from "@mui/icons-material/Tune";
import { useMemo, useState } from "react";
import { useSearchParams } from "react-router-dom";
import { ActivityExplorer, Loading } from "./AnalyticsActivity";
import { ActivityTrend, EventComposition, JourneyFunnel, RetentionHeatmap, SaveHistogram } from "./AnalyticsCharts";
import { kstDate, kstTime, num, pairGrid, shiftDate, useAnalytics, type ActivityScope, type Overview } from "./analyticsUi";

export function UserAnalyticsPage() {
  const today = useMemo(() => kstDate(new Date()), []);
  const [params, setParams] = useSearchParams();
  const from = params.get("from") ?? shiftDate(today, -29);
  const to = params.get("to") ?? today;
  const activeDate = params.get("activeDate") ?? today;
  const observationEnd = params.get("observationEnd") ?? today;
  const threshold = Number(params.get("threshold") ?? 3);
  const [advanced, setAdvanced] = useState(false);
  const [revision, setRevision] = useState(0);
  const periodError = validatePeriod(from, to, observationEnd, today) || (![1, 3, 5, 10].includes(threshold) ? "활성화 기준을 선택해 주세요." : "");
  const activeValid = validDate(activeDate) && activeDate <= today;
  const active = useAnalytics<Overview>(activeValid ? `/user-analytics?${new URLSearchParams({ from: activeDate, to: activeDate, activeDate })}` : null, revision);
  const periodPath = `/user-analytics?${new URLSearchParams({ from, to, observationEnd, activationThreshold: String(threshold) })}`;
  const period = useAnalytics<Overview>(periodError ? null : periodPath, revision);
  function update(values: Record<string, string>) {
    setParams(previous => { const next = new URLSearchParams(previous); Object.entries(values).forEach(([key, value]) => next.set(key, value)); return next; }, { replace: true });
  }

  return <Stack className="user-analytics-page" spacing={2.5} sx={{ width: "100%", minWidth: 0, maxWidth: 1500, "& > *": { minWidth: 0 } }}>
    <Stack direction="row" sx={{ justifyContent: "space-between", gap: 2, alignItems: "center" }}>
      <Box><Typography variant="overline" color="primary.main">AUDIENCE & ACTIVITY</Typography><Typography variant="h4">사용자 행동</Typography><Typography variant="body2" color="text.secondary">활동을 한눈에, 궁금한 숫자는 눌러서 자세히.</Typography></Box>
      <Button startIcon={<RefreshIcon />} onClick={() => setRevision(v => v + 1)} sx={{ flexShrink: 0 }}>새로고침</Button>
    </Stack>
    <Box>
      <Stack direction="row" sx={{ justifyContent: "space-between", alignItems: "center", gap: 1, mb: 1.5 }}>
        <Box><Typography variant="h6">얼마나 활동하나요?</Typography><Typography variant="caption" color="text.secondary">서버에서 관측한 활동 회원 · 앱 실행 수가 아닙니다</Typography></Box>
        <DateInput label="활성 기준일" value={activeDate} max={today} onChange={value => update({ activeDate: value })} />
      </Stack>
      {!activeValid ? <Alert severity="warning">오늘까지의 올바른 기준일을 선택해 주세요.</Alert> : active.error ? <Alert severity="error">{active.error}</Alert> : !active.data ? <Loading /> : <ActiveAudience key={`${activeDate}:${revision}`} overview={active.data} />}
    </Box>
    <Box className="analytics-period-toolbar">
      <Box><Typography variant="h6">기간별 행동</Typography><Typography variant="caption" color="text.secondary">한국 시간(KST) · 아래 그래프에만 적용</Typography></Box>
      <Stack direction="row" sx={{ alignItems: "center", flexWrap: "wrap", gap: 1 }}>
        <Button size="small" variant={from === shiftDate(today, -6) && to === today ? "contained" : "text"} onClick={() => update({ from: shiftDate(today, -6), to: today, observationEnd: today })}>7일</Button>
        <Button size="small" variant={from === shiftDate(today, -29) && to === today ? "contained" : "text"} onClick={() => update({ from: shiftDate(today, -29), to: today, observationEnd: today })}>30일</Button>
        <DateInput label="기간 시작일" value={from} max={to} onChange={value => update({ from: value })} />
        <DateInput label="기간 종료일" value={to} min={from} max={today} onChange={value => update({ to: value })} />
        <Button size="small" startIcon={<TuneIcon />} aria-expanded={advanced} aria-controls="analytics-advanced" onClick={() => setAdvanced(v => !v)}>상세 설정</Button>
      </Stack>
    </Box>
    <Collapse in={advanced} id="analytics-advanced"><Stack direction={{ xs: "column", sm: "row" }} sx={{ gap: 2, alignItems: { sm: "center" }, pb: 1 }}>
      <DateInput label="후속 관측 종료일" value={observationEnd} min={to} max={today} onChange={value => update({ observationEnd: value })} />
      <TextField select size="small" label="가입자 활성화 기준" value={threshold} onChange={e => update({ threshold: e.target.value })} sx={{ minWidth: 180 }}>{[1, 3, 5, 10].map(n => <MenuItem value={n} key={n}>저장 대상 {n}개 이상</MenuItem>)}</TextField>
      <Typography variant="caption" color="text.secondary">기간 안에 가입·저장한 회원의 후속 행동을 관측 종료일까지 확인합니다.</Typography>
    </Stack></Collapse>
    {periodError ? <Alert severity="warning" action={<Button onClick={() => setAdvanced(true)}>설정 열기</Button>}>{periodError}</Alert> : period.error ? <Alert severity="error">{period.error}</Alert> : !period.data ? <Loading /> : <PeriodDashboard key={`${periodPath}:${revision}`} overview={period.data} />}
  </Stack>;
}

function ActiveAudience({ overview }: { overview: Overview }) {
  const [window, setWindow] = useState<1 | 7 | 30 | null>(null);
  const asOf = overview.activeUsers.asOf;
  const first = overview.report?.coverage.firstEventAt;
  const firstDate = first ? kstDate(new Date(first)) : undefined;
  const scope: ActivityScope | null = window ? { title: `${window === 1 ? "DAU" : window === 7 ? "WAU" : "MAU"} 활동 회원`, from: shiftDate(asOf, 1 - window), to: asOf } : null;
  return <>
    <Box className="analytics-kpi-grid">
      {([["DAU", "일간 활성", "daily", 1], ["WAU", "주간 활성", "weekly", 7], ["MAU", "월간 활성", "monthly", 30]] as const).map(([label, name, key, days]) => {
        const start = shiftDate(asOf, 1 - days);
        const unseen = !firstDate || asOf < firstDate;
        return <Card key={key} variant="outlined" className={`analytics-kpi ${window === days ? "is-selected" : ""}`}>
          <CardActionArea onClick={() => setWindow(value => value === days ? null : days)} aria-expanded={window === days} aria-controls="active-audience-details" aria-label={`${label} ${overview.activeUsers[key]}명 활동 회원 ${window === days ? "접기" : "펼치기"}`}>
            <CardContent><Stack direction="row" sx={{ justifyContent: "space-between", gap: 1 }}><Typography variant="subtitle2">{name}</Typography><Typography variant="caption" className="mono-text" color="primary.main">{label}</Typography></Stack>
              <Typography className="analytics-kpi-value">{unseen ? "—" : num.format(overview.activeUsers[key])}<Typography component="span" variant="body2" color="text.secondary">{unseen ? "미관측" : "명"}</Typography></Typography>
              <Typography variant="caption" color="text.secondary" sx={{ display: { xs: "none", sm: "block" } }}>{days === 1 ? asOf : `${start} ~ ${asOf}`}{firstDate && start <= firstDate ? " · 관측 일부" : ""}</Typography>
              <Typography variant="caption" color="text.secondary" sx={{ display: { xs: "block", sm: "none" } }}>{days === 1 ? "기준일 하루" : `최근 ${days}일`}{firstDate && start <= firstDate ? " · 일부 관측" : ""}</Typography>
              <Typography variant="caption" color="primary.main" sx={{ display: "block", mt: 1 }}>{window === days ? "상세 접기 ↑" : "회원 보기 ↓"}</Typography>
            </CardContent>
          </CardActionArea>
        </Card>;
      })}
    </Box>
    <Collapse in={Boolean(scope)} unmountOnExit id="active-audience-details">{scope && <ActivityExplorer key={JSON.stringify(scope)} scope={scope} onClose={() => setWindow(null)} />}</Collapse>
  </>;
}

function PeriodDashboard({ overview }: { overview: Overview }) {
  const [scope, setScope] = useState<ActivityScope | null>(null);
  const report = overview.report;
  if (!report) return <Alert severity="warning">상세 분석을 제공하는 서버 버전이 필요합니다.</Alert>;
  const saves = report.events.filter(e => e.eventName.endsWith("_save")).reduce((sum, e) => sum + e.events, 0);
  function select(next: ActivityScope) { setScope(previous => JSON.stringify(previous) === JSON.stringify(next) ? null : next); }
  return <>
    <Box className="analytics-period-stats">
      <Button onClick={() => select({ title: "기간 활동 회원", from: overview.from, to: overview.to })}>고유 활동 <strong>{num.format(report.summary.activeUsers)}명</strong> ↘</Button>
      <Button onClick={() => select({ title: "신규 가입 회원", from: overview.from, to: overview.to, eventName: "sign_up" })}>신규 가입 <strong>{num.format(report.summary.signUps)}명</strong> ↘</Button>
      <Typography variant="body2">저장 <strong>{num.format(saves)}건</strong></Typography>
      <Typography variant="body2">전체 이벤트 <strong>{num.format(report.summary.events)}건</strong></Typography>
      {report.summary.legacyEvents > 0 && <Chip size="small" label="이전 중복 제거 기록 포함" variant="outlined" />}
    </Box>
    {!report.summary.events && <Alert severity="info">선택 기간에 수집된 기록이 없습니다. 실제 무활동과 미수집은 구분할 수 없습니다.</Alert>}
    <Box sx={{ display: "grid", gridTemplateColumns: { xs: "1fr", lg: "minmax(0, 1.65fr) minmax(0, 1fr)" }, gap: 2 }}>
      <ActivityTrend overview={overview} selectedDate={scope && scope.from === scope.to && !scope.eventName ? scope.from : undefined} onSelect={select} />
      <EventComposition overview={overview} onSelect={select} />
    </Box>
    <Collapse in={Boolean(scope)} unmountOnExit>{scope && <ActivityExplorer key={JSON.stringify(scope)} scope={scope} onClose={() => setScope(null)} />}</Collapse>
    <Box sx={pairGrid}><SaveHistogram overview={overview} /><JourneyFunnel overview={overview} /></Box>
    <RetentionHeatmap overview={overview} />
    <MeasurementNotes overview={overview} />
  </>;
}

function MeasurementNotes({ overview }: { overview: Overview }) {
  const report = overview.report!;
  return <Accordion variant="outlined" disableGutters elevation={0} sx={{ "&:before": { display: "none" } }}>
    <AccordionSummary expandIcon={<ExpandMoreIcon />} aria-controls="analytics-measurement-notes"><Typography variant="body2" color="text.secondary">집계 기준 · 수집 범위 확인</Typography></AccordionSummary>
    <AccordionDetails id="analytics-measurement-notes"><Stack spacing={1}>
      <Typography variant="body2">앱 최초 실행(first_open)과 실제 지도 장소 선택(map_place_select)은 미수집입니다. 지도 조회는 지도 API 요청이며 실제 화면 방문이나 장소 선택과 다를 수 있습니다.</Typography>
      <Typography variant="body2">DAU·WAU·MAU는 기준일 포함 1·7·30일 고유 회원입니다. 가입을 포함한 수집 이벤트 기준이며, 기간 고유 회원은 DAU의 합계가 아닙니다. 오늘 수치는 진행 중입니다.</Typography>
      <Typography variant="body2">활동 기간: {overview.from} ~ {overview.to} / 가입자 활성화·재방문 관측: {overview.observedThrough}까지. 활성화는 서로 다른 저장 대상 {overview.activationThreshold}개 이상입니다.</Typography>
      <Typography variant="body2">저장 분포는 기간 내 회원별 서로 다른 대상 수이며 현재 보관 수와 다릅니다. 같은 대상 재조회는 저장 다음 날 이후만 계산합니다.</Typography>
      <Typography variant="body2">이전 중복 제거 기록 {num.format(report.summary.legacyEvents)}건 포함. 과거 반복 행동은 복원할 수 없고 새 조회도 자동 호출·재시도와 실제 방문을 구분하지 못합니다.</Typography>
      <Typography variant="body2">{report.coverage.firstEventAt ? `최초 기록 ${kstTime(report.coverage.firstEventAt)} / 최종 기록 ${kstTime(report.coverage.lastEventAt!)}` : "전체 수집 기록 없음"}. 최초 기록 이전은 미관측이며 기록 사이의 연속 수집은 보장하지 않습니다.</Typography>
      <Typography variant="body2">재방문은 D1·7·30 바로 그날의 행동입니다. 미도래 날짜는 분모에서 제외합니다. 신규 가입 기록이 없는 회원을 모두 기존 회원이라고 단정할 수는 없습니다.</Typography>
    </Stack></AccordionDetails>
  </Accordion>;
}

function DateInput({ label, value, min, max, onChange }: { label: string; value: string; min?: string; max?: string; onChange: (value: string) => void }) {
  return <TextField label={label} type="date" size="small" value={value} onChange={e => onChange(e.target.value)} slotProps={{ inputLabel: { shrink: true }, htmlInput: { min, max } }} sx={{ minWidth: 135 }} />;
}
function validDate(value: string) { return /^\d{4}-\d{2}-\d{2}$/.test(value) && Number.isFinite(Date.parse(value)) && new Date(value).toISOString().slice(0, 10) === value; }
function validatePeriod(from: string, to: string, observationEnd: string, today: string) {
  if (![from, to, observationEnd].every(validDate)) return "날짜를 모두 올바르게 선택해 주세요.";
  if (from > to) return "시작일은 종료일 이전이어야 합니다.";
  if (to > today || observationEnd > today) return "오늘 이후 날짜는 조회할 수 없습니다.";
  if (observationEnd < to) return "후속 관측 종료일은 기간 종료일 이후여야 합니다.";
  if ((Date.parse(to) - Date.parse(from)) / 86400000 >= 90) return "조회 기간은 최대 90일입니다.";
  if ((Date.parse(observationEnd) - Date.parse(from)) / 86400000 >= 365) return "후속 관측 범위는 최대 365일입니다.";
  return "";
}
