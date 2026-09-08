import { Box, Button, Card, CardContent, Chip, Stack, ToggleButton, ToggleButtonGroup, Typography } from "@mui/material";
import { useId, useState } from "react";
import { eventColors, eventLabels, kstDate, num, percent, type ActivityScope, type Overview } from "./analyticsUi";

export function ActivityTrend({ overview, selectedDate, onSelect }: { overview: Overview; selectedDate?: string; onSelect: (scope: ActivityScope) => void }) {
  const [hoverDate, setHoverDate] = useState<string>();
  const gradientId = useId().replace(/:/g, "");
  const { daily } = overview;
  const first = overview.report?.coverage.firstEventAt;
  const firstDate = first ? kstDate(new Date(first)) : undefined;
  const selected = daily.find(day => day.date === (hoverDate ?? selectedDate)) ?? daily.at(-1);
  const width = 800, height = 250, left = 40, right = 18, top = 20, bottom = 34;
  const max = Math.max(1, ...daily.map(day => day.activeUsers));
  const ceiling = max <= 4 ? 4 : Math.ceil(max / 4) * 4;
  const x = (i: number) => daily.length === 1 ? width / 2 : left + i * (width - left - right) / (daily.length - 1);
  const y = (n: number) => height - bottom - n / ceiling * (height - top - bottom);
  const observed = daily.map((day, i) => ({ ...day, i })).filter(day => firstDate && day.date >= firstDate);
  const line = observed.map((day, i) => `${i ? "L" : "M"}${x(day.i)},${y(day.activeUsers)}`).join(" ");
  const area = observed.length > 1 ? `${line} L${x(observed.at(-1)!.i)},${y(0)} L${x(observed[0].i)},${y(0)} Z` : "";
  const selectedObserved = selected && firstDate && selected.date >= firstDate;
  return <Card variant="outlined" className="analytics-chart"><CardContent>
    <Stack direction="row" sx={{ justifyContent: "space-between", alignItems: "start", gap: 1 }}>
      <Box><Typography variant="h6">활동은 늘고 있나요?</Typography><Typography variant="caption" color="text.secondary">일별 활성 회원 · 날짜를 누르면 아래에 상세가 펼쳐집니다</Typography></Box>
      <Chip size="small" label="DAU" color="primary" variant="outlined" />
    </Stack>
    <Stack direction="row" sx={{ alignItems: "baseline", gap: 1, mt: 2, mb: 1 }} aria-live="polite">
      <Typography variant="h4">{selectedObserved ? `${num.format(selected.activeUsers)}명` : "미관측"}</Typography>
      <Typography variant="caption" color="text.secondary">{selected?.date}{selectedObserved ? ` · 신규 가입 ${selected.signUps}명` : " · 최초 기록 이전"}</Typography>
    </Stack>
    <Box sx={{ overflowX: "auto" }}><Box component="svg" role="group" aria-label="일별 활성 회원 추이. 날짜를 선택하면 회원 상세 표시" viewBox={`0 0 ${width} ${height}`} sx={{ display: "block", width: "100%", minWidth: 520 }}>
      <defs><linearGradient id={gradientId} x1="0" y1="0" x2="0" y2="1"><stop offset="0%" stopColor="#558EFF" stopOpacity=".25" /><stop offset="100%" stopColor="#558EFF" stopOpacity=".02" /></linearGradient></defs>
      {[0, 1, 2, 3, 4].map(tick => <g key={tick}><line x1={left} x2={width - right} y1={y(ceiling * tick / 4)} y2={y(ceiling * tick / 4)} stroke="#E4E6E9" strokeDasharray="3 4" /><text x={left - 10} y={y(ceiling * tick / 4) + 4} textAnchor="end" fill="#67707D" fontSize="11">{ceiling * tick / 4}</text></g>)}
      <path d={area} fill={`url(#${gradientId})`} /><path d={line} fill="none" stroke="#558EFF" strokeWidth="3" strokeLinejoin="round" />
      {observed.map(day => <circle key={day.date} cx={x(day.i)} cy={y(day.activeUsers)} r={day.date === selected?.date ? 5 : 3} fill="#558EFF" stroke="white" strokeWidth="2" />)}
      {daily.map((day, i) => <g key={day.date} role="button" tabIndex={0} aria-label={`${day.date} ${firstDate && day.date >= firstDate ? "DAU " + day.activeUsers + "명 활동 상세" : "미관측"}`}
        aria-pressed={day.date === selectedDate} className="analytics-chart-hit"
        onMouseEnter={() => setHoverDate(day.date)} onMouseLeave={() => setHoverDate(undefined)}
        onFocus={() => setHoverDate(day.date)} onBlur={() => setHoverDate(undefined)}
        onClick={() => onSelect({ title: `${day.date} 활동 회원`, from: day.date, to: day.date })}
        onKeyDown={e => { if (e.key === "Enter" || e.key === " ") { e.preventDefault(); onSelect({ title: `${day.date} 활동 회원`, from: day.date, to: day.date }); } }}>
        <rect x={x(i) - Math.max(4, (width - left - right) / Math.max(2, daily.length) / 2)} y={top} width={Math.max(8, (width - left - right) / Math.max(2, daily.length))} height={height - top - bottom} fill={day.date === selectedDate ? "rgba(85,142,255,.07)" : "transparent"} />
        {(i === 0 || i === daily.length - 1 || i % Math.max(1, Math.ceil(daily.length / 6)) === 0) && <text x={x(i)} y={height - 10} textAnchor="middle" fill="#67707D" fontSize="11">{day.date.slice(5)}</text>}
      </g>)}
    </Box></Box>
    <Typography variant="caption" color="text.secondary">빈 구간은 최초 기록 이전 · 오늘 수치는 진행 중</Typography>
  </CardContent></Card>;
}

export function EventComposition({ overview, onSelect }: { overview: Overview; onSelect: (scope: ActivityScope) => void }) {
  const [measure, setMeasure] = useState<"users" | "events">("users");
  const events = [...(overview.report?.events ?? [])].sort((a,b) => b[measure] - a[measure]);
  const max = Math.max(1, ...events.map(event => event[measure]));
  return <Card variant="outlined" className="analytics-chart"><CardContent>
    <Stack direction="row" sx={{ justifyContent: "space-between", gap: 1, alignItems: "center", mb: 2 }}>
      <Typography variant="h6">무엇을 했나요?</Typography>
      <ToggleButtonGroup size="small" exclusive value={measure} onChange={(_, value) => value && setMeasure(value)} aria-label="행동 차트 단위"><ToggleButton value="users">회원</ToggleButton><ToggleButton value="events">건수</ToggleButton></ToggleButtonGroup>
    </Stack>
    <Stack spacing={.75}>{events.map(event => <Box component="button" type="button" className="analytics-event-bar" key={event.eventName} aria-label={`${eventLabels[event.eventName]} ${event.users}명 ${event.events}건 활동 상세`} onClick={() => onSelect({ title: eventLabels[event.eventName], from: overview.from, to: overview.to, eventName: event.eventName })}>
      <Stack direction="row" sx={{ justifyContent: "space-between", gap: 1 }}><Typography variant="body2">{eventLabels[event.eventName]}</Typography><Typography variant="subtitle2">{num.format(event[measure])}{measure === "users" ? "명" : "건"}</Typography></Stack>
      <Box sx={{ height: 7, bgcolor: "action.hover", borderRadius: 1, mt: .75 }}><Box sx={{ width: `${event[measure] / max * 100}%`, height: "100%", bgcolor: eventColors[event.eventName], borderRadius: 1 }} /></Box>
    </Box>)}</Stack>
    <Typography variant="caption" color="text.secondary">막대를 눌러 회원 확인 · 행동 간 회원 중복 포함</Typography>
  </CardContent></Card>;
}

export function SaveHistogram({ overview }: { overview: Overview }) {
  const report = overview.report!;
  const max = Math.max(1, ...report.saveDistribution.map(bucket => bucket.users));
  const saveEvents = report.events.filter(event => event.eventName.endsWith("_save"));
  return <Card variant="outlined" className="analytics-chart"><CardContent>
    <Stack direction="row" sx={{ justifyContent: "space-between", gap: 1 }}><Typography variant="h6">얼마나 저장하나요?</Typography><Typography variant="caption" color="text.secondary">{report.summary.savingUsers}명 저장</Typography></Stack>
    <Stack direction="row" spacing={2} sx={{ my: 1.5 }}>{saveEvents.map(event => <Typography key={event.eventName} variant="body2"><Box component="span" sx={{ color: eventColors[event.eventName] }}>●</Box> {eventLabels[event.eventName]} <strong>{event.events}건</strong></Typography>)}</Stack>
    <Box role="img" aria-label={`회원별 저장 개수 분포: ${report.saveDistribution.map(b => b.label + " " + b.users + "명").join(", ")}`} sx={{ display: "flex", alignItems: "end", gap: 1.5, height: 160, pt: 3 }}>
      {report.saveDistribution.map((bucket, i) => <Box key={bucket.label} sx={{ flex: 1, minWidth: 0, textAlign: "center" }}>
        <Typography variant="subtitle2">{bucket.users}명</Typography><Box sx={{ mt: .5, mx: "auto", width: "75%", height: bucket.users / max * 100, minHeight: 1, borderRadius: "5px 5px 0 0", bgcolor: i === 0 ? "divider" : "success.main", opacity: i === 0 ? 1 : .45 + i * .13 }} />
        <Typography variant="caption" color="text.secondary">{bucket.label}</Typography>
      </Box>)}
    </Box>
    <Stack direction="row" sx={{ flexWrap: "wrap", gap: .75, mt: 2 }}>{report.saveReach.map(reach => <Chip key={reach.threshold} size="small" variant="outlined" label={`${reach.threshold}개+ ${percent(reach.users, reach.totalUsers)}`} />)}</Stack>
    <Typography variant="caption" color="text.secondary">기간 활동 회원 기준 · 중복 없는 저장 대상 수</Typography>
  </CardContent></Card>;
}

export function JourneyFunnel({ overview }: { overview: Overview }) {
  const { funnel, report } = overview;
  const values = [funnel.signUps, funnel.activatedUsers, funnel.returnedUsers];
  const labels = ["가입", `저장 ${overview.activationThreshold}개+`, "다음 날 이후 재방문"];
  return <Card variant="outlined" className="analytics-chart"><CardContent>
    <Typography variant="h6">가입 후 어디까지 도달했나요?</Typography>
    <Typography variant="caption" color="text.secondary">선택 기간 가입자 · {overview.observedThrough}까지 관측</Typography>
    <Box sx={{ display: "grid", gridTemplateColumns: "repeat(3, minmax(0, 1fr))", gap: 1, mt: 2.5 }}>
      {values.map((value, i) => <Box key={labels[i]} sx={{ minWidth: 0 }}>
        <Typography variant="caption" color="text.secondary">{labels[i]}</Typography><Typography variant="h4">{value}<Typography component="span" variant="body2">명</Typography></Typography>
        <Box sx={{ height: 80, display: "flex", alignItems: "end", borderBottom: 1, borderColor: "divider", mt: 1 }}><Box sx={{ width: "100%", height: funnel.signUps ? `${value / funnel.signUps * 100}%` : 0, bgcolor: ["primary.main", "success.main", "warning.main"][i], opacity: .8, borderRadius: "6px 6px 0 0" }} /></Box>
        <Typography variant="caption" color="text.secondary">{i > 0 ? `이전 단계의 ${percent(value, values[i-1])}` : funnel.signUps ? "가입자 100% 기준" : "가입 기록 없음"}</Typography>
      </Box>)}
    </Box>
    <Box sx={{ mt: 2, pt: 1.5, borderTop: 1, borderColor: "divider" }}><Typography variant="body2">저장했던 콘텐츠 재조회 <strong>{percent(report!.savedContentReturn.returnedUsers, report!.savedContentReturn.savingUsers)}</strong></Typography><Typography variant="caption" color="text.secondary">전체 저장 회원 {report!.savedContentReturn.savingUsers}명 중 {report!.savedContentReturn.returnedUsers}명 · 동일 대상의 후일 조회</Typography></Box>
  </CardContent></Card>;
}

export function RetentionHeatmap({ overview }: { overview: Overview }) {
  const [all, setAll] = useState(false);
  const cohorts = overview.report!.cohorts;
  return <Card variant="outlined" className="analytics-chart"><CardContent>
    <Stack direction="row" sx={{ alignItems: "center", justifyContent: "space-between", gap: 1 }}><Typography variant="h6">며칠 뒤 다시 오나요?</Typography><Typography variant="caption" color="text.secondary">활성화일 기준</Typography></Stack>
    <Stack direction="row" spacing={2} sx={{ my: 2 }}>{overview.retention.map(item => <Box key={item.day} sx={{ flex: 1 }}><Typography variant="caption" color="text.secondary">D{item.day}</Typography><Typography variant="h5">{percent(item.returnedUsers, item.eligibleUsers)}</Typography><Typography variant="caption">{item.eligibleUsers ? `${item.returnedUsers}/${item.eligibleUsers}명` : "관측 대상 없음 / 대기"}</Typography></Box>)}</Stack>
    {cohorts.length === 0 ? <Box sx={{ py: 3, textAlign: "center", bgcolor: "action.hover", borderRadius: 2 }}><Typography variant="body2" color="text.secondary">아직 활성화된 가입자가 없습니다.</Typography></Box> : <Box sx={{ overflowX: "auto" }}><Box component="table" className="analytics-heatmap"><thead><tr><th>활성화일</th><th>회원</th><th>D1</th><th>D7</th><th>D30</th></tr></thead><tbody>{(all ? cohorts : cohorts.slice(-5)).map(cohort => <tr key={cohort.activatedOn}><th scope="row">{cohort.activatedOn.slice(5)}</th><td>{cohort.users}</td>{cohort.retention.map(cell => <td key={cell.day} style={{ background: cell.eligibleUsers ? `rgba(85,142,255,${.06 + cell.returnedUsers / cell.eligibleUsers * .5})` : "#F4F5F7" }}>
      {cell.eligibleUsers ? percent(cell.returnedUsers, cell.eligibleUsers) : "대기"}<small>{cell.eligibleUsers ? `${cell.returnedUsers}/${cell.eligibleUsers}명` : "미도래"}</small>
    </td>)}</tr>)}</tbody></Box></Box>}
    {cohorts.length > 5 && <Button size="small" onClick={() => setAll(v => !v)}>{all ? "최근 5일만" : `전체 ${cohorts.length}일 펼치기`}</Button>}
    <Typography variant="caption" color="text.secondary" sx={{ display: "block", mt: 1 }}>D1·7·30 바로 그날의 재방문율 · 미도래 날짜는 0%가 아닌 대기</Typography>
  </CardContent></Card>;
}
