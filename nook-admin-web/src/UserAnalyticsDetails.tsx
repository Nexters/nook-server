import { Accordion, AccordionDetails, AccordionSummary, Alert, Box, Card, CardContent, LinearProgress, MenuItem, Stack, TextField, Typography } from "@mui/material";
import ExpandMoreIcon from "@mui/icons-material/ExpandMore";
import { useState, type ReactNode } from "react";

type Behavior = { eventName: string; users: number; events: number };
type Retention = { day: number; eligibleUsers: number; returnedUsers: number };
type Member = { memberId: number; uniquePostSaves: number; uniquePlaceSaves: number; activeDays: number; lastEventAt: string; events: Behavior[] };
export type AnalyticsReport = {
  coverage: { firstEventAt: string | null; lastEventAt: string | null };
  summary: { activeUsers: number; signUps: number; usersWithoutSignUp: number; events: number; legacyEvents: number; savingUsers: number; uniqueSavedTargets: number };
  events: Behavior[];
  saveDistribution: { label: string; users: number }[];
  saveReach: { threshold: number; users: number; totalUsers: number }[];
  savedContentReturn: { savingUsers: number; returnedUsers: number; revisitedTargets: number };
  cohorts: { activatedOn: string; users: number; retention: Retention[] }[];
  dailyEvents: { date: string; events: Behavior[] }[];
  members: Member[];
};
const number = new Intl.NumberFormat("ko-KR");
const grid = { display: "grid", gridTemplateColumns: { xs: "1fr", md: "repeat(2, minmax(0, 1fr))" }, gap: 2 };
const labels: Record<string, string> = { sign_up: "신규 가입", post_save: "게시물 저장", place_save: "장소 저장", archive_view: "아카이브 조회", post_view: "게시물 상세 조회", place_view: "장소 상세 조회", map_view: "지도 조회 (선택 아님)" };
function rate(value: number, total: number) { return total ? `${(value / total * 100).toFixed(1)}%` : "—"; }
function progress(value: number, total: number) { return total ? value / total * 100 : 0; }
function time(value: string) { return new Date(value).toLocaleString("ko-KR", { timeZone: "Asia/Seoul" }); }
function date(value: string) { return new Date(Date.parse(value) + 9 * 3_600_000).toISOString().slice(0, 10); }

export function AnalyticsDetails({ report, daily, observedThrough, children }: { report: AnalyticsReport; daily: { date: string }[]; observedThrough: string; children?: ReactNode }) {
  const { summary, savedContentReturn: revisit } = report;
  const first = report.coverage.firstEventAt;
  const stats = [
    ["기간 고유 활동 회원", `${number.format(summary.activeUsers)}명`, "일별 DAU 합계가 아닌 중복 없는 회원 수"],
    ["기간 신규 가입", `${number.format(summary.signUps)}명`, `기간 내 가입 기록 없는 활동 회원 ${summary.usersWithoutSignUp}명`],
    ["저장한 회원", `${number.format(summary.savingUsers)}명`, `회원별 서로 다른 저장 대상 합계 ${summary.uniqueSavedTargets}개`],
    ["적재된 이벤트", `${number.format(summary.events)}건`, "기간 중 가입·저장·탐색 이벤트 전체"],
  ];
  return <>
    <Alert severity="info" variant="outlined">{first ? `전체 최초 기록 ${time(first)} · 최종 기록 ${time(report.coverage.lastEventAt!)}. 연속 수집을 보장하지 않습니다. 최초 기록 이전과 최초 기록일 일부는 미관측입니다.` : "전체 수집 기록이 없습니다. 실제 무활동과 미수집은 구별할 수 없습니다."}</Alert>
    {summary.legacyEvents > 0 && <Alert severity="warning">이전 중복 제거 정책으로 저장된 기록 {number.format(summary.legacyEvents)}건이 포함됩니다. 과거 반복 행동은 복원할 수 없어 이벤트 건수는 실제 호출량보다 적을 수 있습니다.</Alert>}
    {summary.events === 0 && <Alert severity="warning">선택 기간에 적재된 이벤트가 없습니다. 실제 무활동과 수집 누락은 이 데이터만으로 구별할 수 없습니다.</Alert>}
    <Box sx={{ ...grid, gridTemplateColumns: { xs: "1fr", sm: "repeat(2, minmax(0, 1fr))", xl: "repeat(4, minmax(0, 1fr))" } }}>
      {stats.map(([label, value, helper]) => <Card variant="outlined" key={label}><CardContent>
        <Typography variant="body2" color="text.secondary">{label}</Typography><Typography variant="h4" sx={{ my: 1 }}>{value}</Typography><Typography variant="caption" color="text.secondary">{helper}</Typography>
      </CardContent></Card>)}
    </Box>
    {children}
    <EventBreakdown events={report.events} />
    <EventTrend report={report} dates={daily.map((day) => day.date)} />
    <Box sx={grid}>
      <Card variant="outlined"><CardContent>
        <Typography variant="h6">회원별 저장 개수 분포</Typography><Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>기간 활동 회원 전체 · 서로 다른 게시물·장소 저장 대상 수. 현재 보관함 개수가 아닙니다.</Typography>
        <Stack spacing={2}>{report.saveDistribution.map((bucket) => <Box key={bucket.label}>
          <Typography variant="body2">{bucket.label} · {bucket.users}명 ({rate(bucket.users, summary.activeUsers)})</Typography>
          <LinearProgress variant="determinate" value={progress(bucket.users, summary.activeUsers)} sx={{ height: 18, mt: .5, borderRadius: 1 }} />
        </Box>)}</Stack>
      </CardContent></Card>
      <Card variant="outlined"><CardContent>
        <Typography variant="h6">저장 기준 달성률</Typography><Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>가입 시점과 관계없이 기간 활동 회원 중 N개 이상 저장한 비율입니다.</Typography>
        <Stack spacing={2}>{report.saveReach.map((reach) => <Box key={reach.threshold}>
          <Stack direction="row" sx={{ justifyContent: "space-between" }}><Typography>{reach.threshold}개 이상</Typography><Typography>{reach.users} / {reach.totalUsers}명 · {rate(reach.users, reach.totalUsers)}</Typography></Stack>
          <LinearProgress color="success" variant="determinate" value={progress(reach.users, reach.totalUsers)} sx={{ height: 18, mt: .5, borderRadius: 1 }} />
        </Box>)}</Stack>
      </CardContent></Card>
    </Box>
    <Card variant="outlined"><CardContent>
      <Typography variant="h6">저장했던 콘텐츠를 다시 봤나요?</Typography>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>기간 내 직접 저장한 동일 게시물·장소를 다음 날 이후 상세 조회한 회원 · {observedThrough}까지 관측. 다른 콘텐츠 조회와 당일 재조회는 제외합니다.</Typography>
      <Typography variant="h4">{rate(revisit.returnedUsers, revisit.savingUsers)} <Typography component="span" variant="body1">· 저장 회원 {revisit.savingUsers}명 중 {revisit.returnedUsers}명</Typography></Typography>
      <LinearProgress color="success" variant="determinate" value={progress(revisit.returnedUsers, revisit.savingUsers)} sx={{ my: 2, height: 12, borderRadius: 1 }} />
      <Typography variant="body2">회원별 다시 본 저장 대상 합계 {revisit.revisitedTargets}개 · 마지막 날 저장자는 재방문 기회가 부족할 수 있습니다.</Typography>
    </CardContent></Card>
    <CohortHeatmap cohorts={report.cohorts} />
    <Card variant="outlined"><CardContent>
      <Typography variant="h6">회원별 활동 살펴보기</Typography><Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>기간 저장 대상 수순 상위 {report.members.length}명 / 전체 {summary.activeUsers}명 (최대 100명). 펼치면 해당 회원의 활동 상세를 볼 수 있습니다.</Typography>
      {report.members.length === 0 ? <Typography color="text.secondary">표시할 회원이 없습니다.</Typography> : <Box sx={{ maxHeight: 470, overflowY: "auto" }}>{report.members.map((member) => <MemberActivity key={member.memberId} member={member} />)}</Box>}
    </CardContent></Card>
  </>;
}

function EventBreakdown({ events }: { events: Behavior[] }) {
  const [measure, setMeasure] = useState<"users" | "events">("users");
  const maximum = Math.max(1, ...events.map((event) => event[measure]));
  return <Card variant="outlined"><CardContent>
    <Stack direction={{ xs: "column", sm: "row" }} sx={{ justifyContent: "space-between", gap: 2, mb: 2 }}>
      <Box><Typography variant="h6">전체 회원은 무엇을 했나요?</Typography><Typography variant="body2" color="text.secondary">가입자 퍼널과 무관한 전체 회원 활동입니다. 이벤트 간 회원 수는 중복될 수 있습니다.</Typography></Box>
      <TextField select size="small" label="비교 기준" value={measure} onChange={(event) => setMeasure(event.target.value as "users" | "events")} sx={{ minWidth: 140 }}><MenuItem value="users">고유 회원 수</MenuItem><MenuItem value="events">적재 이벤트 수</MenuItem></TextField>
    </Stack>
    <Stack spacing={1.5}>{events.map((event) => <Box key={event.eventName}>
      <Stack direction="row" sx={{ justifyContent: "space-between", gap: 1 }}><Typography variant="body2">{labels[event.eventName] ?? event.eventName}</Typography><Typography variant="body2">{event.users}명 · {event.events}건</Typography></Stack>
      <LinearProgress variant="determinate" value={progress(event[measure], maximum)} sx={{ mt: .5, height: 12, borderRadius: 1 }} />
    </Box>)}</Stack>
    <Typography variant="caption" color="text.secondary">API의 성공한 관측 지점에서 적재한 기록입니다. 자동 새로고침·페이지 추가 로딩 등은 별도 호출로 집계될 수 있습니다.</Typography>
  </CardContent></Card>;
}

function EventTrend({ report, dates }: { report: AnalyticsReport; dates: string[] }) {
  const [eventName, setEventName] = useState("post_save");
  const [selected, setSelected] = useState("");
  const first = report.coverage.firstEventAt ? date(report.coverage.firstEventAt) : undefined;
  const selectedDate = dates.includes(selected) ? selected : dates.at(-1);
  const days = new Map(report.dailyEvents.map((day) => [day.date, day.events.find((event) => event.eventName === eventName)]));
  const maximum = Math.max(1, ...report.dailyEvents.map((day) => day.events.find((event) => event.eventName === eventName)?.events ?? 0));
  const selectedEvent = selectedDate ? days.get(selectedDate) : undefined;
  return <Card variant="outlined"><CardContent>
    <Stack direction={{ xs: "column", sm: "row" }} sx={{ justifyContent: "space-between", gap: 2, mb: 2 }}>
      <Box><Typography variant="h6">이벤트별 일간 추이</Typography><Typography variant="body2" color="text.secondary">막대 높이는 적재 건수입니다. 날짜를 선택하면 고유 회원 수도 확인할 수 있습니다.</Typography></Box>
      <TextField select label="이벤트" size="small" value={eventName} onChange={(event) => setEventName(event.target.value)} sx={{ minWidth: 185 }}>{report.events.map((event) => <MenuItem key={event.eventName} value={event.eventName}>{labels[event.eventName]}</MenuItem>)}</TextField>
    </Stack>
    <Typography variant="caption" color="text.secondary">최대 {maximum}건</Typography>
    <Box sx={{ overflowX: "auto" }}><Box sx={{ display: "flex", height: 160, alignItems: "end", gap: .5, minWidth: Math.max(400, dates.length * 9), borderBottom: 1, borderColor: "divider" }}>
      {dates.map((day) => {
        const observed = first && day >= first;
        const value = days.get(day)?.events ?? 0;
        return <Box key={day} component="button" type="button" aria-label={`${day} ${observed ? value + "건" : "미관측"}`} title={`${day}: ${observed ? value + "건" : "미관측"}`} onClick={() => setSelected(day)} sx={{ flex: 1, height: observed ? `${Math.max(2, progress(value, maximum))}%` : "100%", minWidth: 5, p: 0, border: 0, bgcolor: observed ? "primary.main" : "action.hover", cursor: "pointer", opacity: selectedDate === day ? 1 : .6, "&:focus-visible": { outline: "2px solid", outlineColor: "text.primary" } }} />;
      })}
    </Box></Box>
    <Stack direction="row" sx={{ justifyContent: "space-between", mb: 2 }}><Typography variant="caption">{dates[0]}</Typography><Typography variant="caption">{dates.at(-1)}</Typography></Stack>
    <Stack direction={{ xs: "column", sm: "row" }} spacing={2} sx={{ alignItems: { sm: "center" } }}>
      <TextField select size="small" label="이벤트 수치 날짜" value={selectedDate ?? ""} onChange={(event) => setSelected(event.target.value)} sx={{ minWidth: 180 }}>{dates.map((day) => <MenuItem value={day} key={day}>{day}</MenuItem>)}</TextField>
      <Typography variant="body2">{selectedDate && first && selectedDate >= first ? `${labels[eventName]} · ${selectedEvent?.users ?? 0}명 / ${selectedEvent?.events ?? 0}건` : "최초 기록 이전 · 미관측 (회색 구간)"}</Typography>
    </Stack>
  </CardContent></Card>;
}

function CohortHeatmap({ cohorts }: { cohorts: AnalyticsReport["cohorts"] }) {
  return <Card variant="outlined"><CardContent>
    <Typography variant="h6">활성화일별 재방문 히트맵</Typography><Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>선택 기간 가입자 기준 · 활성화한 날짜별로 D1 / D7 / D30 바로 그날 재방문한 비율입니다.</Typography>
    {cohorts.length === 0 ? <Alert severity="info">아직 활성화 기준을 달성한 가입자가 없습니다. 전체 회원의 저장 분포와는 별개입니다.</Alert> : <Box sx={{ overflowX: "auto" }}><Box component="table" sx={{ width: "100%", minWidth: 500, borderSpacing: 6 }}>
      <thead><tr>{["활성화일", "회원", "D1", "D7", "D30"].map((label) => <th key={label}>{label}</th>)}</tr></thead>
      <tbody>{cohorts.map((cohort) => <tr key={cohort.activatedOn}><th scope="row">{cohort.activatedOn}</th><td style={{ textAlign: "center" }}>{cohort.users}명</td>{cohort.retention.map((cell) => <Box component="td" key={cell.day} sx={{ textAlign: "center", p: 1.5, borderRadius: 1, bgcolor: cell.eligibleUsers ? `rgba(85, 142, 255, ${.08 + progress(cell.returnedUsers, cell.eligibleUsers) / 150})` : "action.hover" }}>
        <Typography variant="subtitle2">{cell.eligibleUsers ? rate(cell.returnedUsers, cell.eligibleUsers) : "관측 대기"}</Typography><Typography variant="caption">{cell.eligibleUsers ? `${cell.returnedUsers} / ${cell.eligibleUsers}명` : "해당 날짜 미도래"}</Typography>
      </Box>)}</tr>)}</tbody>
    </Box></Box>}
  </CardContent></Card>;
}

function MemberActivity({ member }: { member: Member }) {
  return <Accordion disableGutters elevation={0} sx={{ borderBottom: 1, borderColor: "divider" }}>
    <AccordionSummary expandIcon={<ExpandMoreIcon />} aria-controls={`analytics-member-${member.memberId}`} id={`analytics-member-summary-${member.memberId}`}>
      <Box sx={{ width: "100%" }}><Typography variant="subtitle2">게시물 {member.uniquePostSaves}개 · 장소 {member.uniquePlaceSaves}개 저장 / {member.activeDays}일 활동</Typography><Typography variant="caption" color="text.secondary" className="mono-text">회원 #{member.memberId}</Typography></Box>
    </AccordionSummary>
    <AccordionDetails id={`analytics-member-${member.memberId}`}>
      <Typography variant="body2" sx={{ mb: 1 }}>기간 내 마지막 활동 {time(member.lastEventAt)}</Typography>
      <Stack spacing={.5}>{member.events.map((event) => <Typography key={event.eventName} variant="body2">{labels[event.eventName]} · {event.events}건</Typography>)}</Stack>
    </AccordionDetails>
  </Accordion>;
}
