import { Alert, Box, Button, Card, CardContent, Stack, Table, TableBody, TableCell, TableContainer, TableHead, TableRow, TextField, Typography } from "@mui/material";
import RefreshIcon from "@mui/icons-material/Refresh";
import { useState } from "react";
import { useSearchParams } from "react-router-dom";
import { Loading } from "./AnalyticsActivity";
import { kstDate, kstTime, num, shiftDate, useAnalytics } from "./analyticsUi";

type Window = { minutes: number; convertedUsers: number; provisionalRate: number | null; eligibleUsers: number; pendingUsers: number; eligibleConvertedUsers: number; completedRate: number | null };
type Conversion = {
  from: string; to: string; observedAt: string; clickedUsers: number; windows: Window[];
  completedDayUsers: number; pendingDayUsers: number; medianFirstSaveSeconds: number | null;
  distribution: { posts: string; users: number }[];
  extension: { enteredUsers: number; entryRate: number | null; savedUsers: number; saveRate: number | null; medianEntrySeconds: number | null; medianSaveSeconds: number | null } | null;
};
const rate = (value: number | null) => value === null ? "—" : `${value.toFixed(1)}%`;
const elapsed = (value: number | null) => value === null ? "—" : `${(value / 60).toFixed(1)}분`;

export function OnboardingConversionPage() {
  const today = kstDate(new Date());
  const [params, setParams] = useSearchParams();
  const from = params.get("from") ?? shiftDate(today, -29);
  const to = params.get("to") ?? today;
  const [revision, setRevision] = useState(0);
  const valid = validDate(from) && validDate(to) && from <= to && to <= today && Date.parse(to) - Date.parse(from) < 90 * 86400000;
  const result = useAnalytics<Conversion>(valid ? `/onboarding-conversion?${new URLSearchParams({ from, to })}` : null, revision);
  function update(key: string, value: string) {
    setParams(previous => { const next = new URLSearchParams(previous); next.set(key, value); return next; }, { replace: true });
  }
  return <Stack spacing={2.5} sx={{ width: "100%", minWidth: 0, maxWidth: 1500, "& > *": { minWidth: 0 } }}>
    <Stack direction="row" sx={{ justifyContent: "space-between", alignItems: "center", gap: 1 }}>
      <Box><Typography variant="h4">온보딩 전환</Typography><Typography variant="body2" color="text.secondary">Instagram CTA 클릭 이후 첫 저장까지의 흐름을 확인합니다.</Typography></Box>
      <Button startIcon={<RefreshIcon />} onClick={() => setRevision(v => v + 1)} sx={{ flexShrink: 0 }}>새로고침</Button>
    </Stack>
    <Stack direction={{ xs: "column", sm: "row" }} spacing={2} sx={{ alignItems: { sm: "center" } }}>
      <Typography variant="body2">최초 CTA 클릭일 · KST</Typography>
      <TextField type="date" size="small" label="시작일" value={from} onChange={e => update("from", e.target.value)} slotProps={{ inputLabel: { shrink: true }, htmlInput: { max: to } }} />
      <TextField type="date" size="small" label="종료일" value={to} onChange={e => update("to", e.target.value)} slotProps={{ inputLabel: { shrink: true }, htmlInput: { min: from, max: today } }} />
    </Stack>
    {!valid ? <Alert severity="warning">오늘까지의 올바른 기간을 최대 90일로 선택해 주세요.</Alert> : result.error ? <Alert severity="error">{result.error}</Alert> : !result.data ? <Loading /> : <ConversionReport data={result.data} />}
  </Stack>;
}

function ConversionReport({ data }: { data: Conversion }) {
  const primary = data.windows.find(window => window.minutes === 10);
  return <>
    <Typography variant="caption" color="text.secondary">관측 시각 {kstTime(data.observedAt)} · 조회 기간 이후 저장도 CTA부터 최대 24시간까지 관측합니다.</Typography>
    {data.clickedUsers === 0 && <Alert severity="info">선택 기간에 최초 CTA 클릭 기록이 없습니다. 클릭이 없었던 경우와 앱에서 아직 수집하지 않는 경우를 구분할 수 없습니다.</Alert>}
    <Box sx={{ display: "grid", gridTemplateColumns: { xs: "1fr", md: "repeat(3, minmax(0, 1fr))" }, gap: 2 }}>
      <Metric label="Instagram CTA 클릭 사용자" value={`${num.format(data.clickedUsers)}명`} detail="사용자별 최초 온보딩 클릭 기준" />
      <Metric label="10분 이내 첫 저장률 · 잠정" value={rate(primary?.provisionalRate ?? null)} detail={`${primary?.convertedUsers ?? 0}명 / 전체 클릭 ${data.clickedUsers}명 · 관측 중 ${primary?.pendingUsers ?? 0}명`} />
      <Metric label="10분 관측 완료 집단 전환율" value={rate(primary?.completedRate ?? null)} detail={`${primary?.eligibleConvertedUsers ?? 0}명 / 관측 완료 ${primary?.eligibleUsers ?? 0}명`} />
    </Box>
    <Card variant="outlined"><CardContent><Typography variant="h6">클릭 후 시간별 첫 저장</Typography>
      <Typography variant="body2" color="text.secondary">잠정값은 전체 클릭자 기준입니다. 관측 완료율은 각 시간이 지난 사용자만 분모·분자에 포함합니다.</Typography>
      <TableContainer><Table size="small" aria-label="시간별 첫 저장 전환율"><TableHead><TableRow><TableCell>시간</TableCell><TableCell>저장 / 클릭</TableCell><TableCell>잠정 전환율</TableCell><TableCell>관측 완료 저장 / 대상</TableCell><TableCell>관측 완료율</TableCell><TableCell>관측 중</TableCell></TableRow></TableHead>
        <TableBody>{data.windows.map(window => <TableRow key={window.minutes}><TableCell>{window.minutes}분</TableCell><TableCell>{window.convertedUsers} / {data.clickedUsers}명</TableCell><TableCell>{rate(window.provisionalRate)}</TableCell><TableCell>{window.eligibleConvertedUsers} / {window.eligibleUsers}명</TableCell><TableCell>{rate(window.completedRate)}</TableCell><TableCell>{window.pendingUsers}명</TableCell></TableRow>)}</TableBody>
      </Table></TableContainer>
    </CardContent></Card>
    <Box sx={{ display: "grid", gridTemplateColumns: { xs: "1fr", md: "repeat(2, minmax(0, 1fr))" }, gap: 2 }}>
      <Metric label="첫 저장까지 소요 시간 중앙값" value={elapsed(data.medianFirstSaveSeconds)} detail="24시간 관측 완료 집단 중 24시간 내 전환자 기준" />
      <Card variant="outlined"><CardContent><Typography variant="h6">24시간 내 저장 개수</Typography><Typography variant="body2" color="text.secondary">관측 완료 {data.completedDayUsers}명 · 관측 중 {data.pendingDayUsers}명은 제외</Typography>
        <Stack spacing={1} sx={{ mt: 2 }}>{data.distribution.map(bucket => <Stack key={bucket.posts} direction="row" sx={{ justifyContent: "space-between" }}><Typography>{bucket.posts === "3+" ? "3개 이상" : `${bucket.posts}개`}</Typography><Typography>{num.format(bucket.users)}명</Typography></Stack>)}</Stack>
      </CardContent></Card>
    </Box>
    <Card variant="outlined"><CardContent><Typography variant="h6">Share Extension 진입</Typography>
      {data.extension ? <Stack spacing={1} sx={{ mt: 1 }}><Typography>진입 {data.extension.enteredUsers}명 · 진입률 {rate(data.extension.entryRate)}</Typography><Typography>진입 후 저장 {data.extension.savedUsers}명 · 저장률 {rate(data.extension.saveRate)}</Typography><Typography>클릭 → 진입 중앙값 {elapsed(data.extension.medianEntrySeconds)} · 진입 → 저장 중앙값 {elapsed(data.extension.medianSaveSeconds)}</Typography><Typography variant="caption" color="text.secondary">24시간 관측 완료 집단 기준. 진입·저장 모두 CTA 후 24시간 이내이며 앱 수집 지원 범위에 영향을 받습니다.</Typography></Stack> : <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>24시간 관측 완료 집단에 진입 기록이 없습니다. 클라이언트 수집 지원 여부 확인이 필요하며, 0%로 해석하지 않습니다.</Typography>}
    </CardContent></Card>
    <Alert severity="info">CTA와 저장의 시간적 연관성을 측정합니다. Instagram 내부 조회·공유 행동이나 온보딩의 인과 효과를 의미하지 않습니다.</Alert>
    <Typography variant="body2" color="text.secondary">반복 CTA는 최초 클릭 기준으로 집계합니다. 저장은 보관함 저장 성공이며 파싱 완료가 아닙니다. 24시간 분포는 서로 다른 게시물 수로, 삭제 후 같은 게시물 재저장을 중복 집계하지 않습니다. 지연 수신과 저장 이벤트 수집 누락에 따라 수치는 달라질 수 있습니다.</Typography>
  </>;
}

function Metric({ label, value, detail }: { label: string; value: string; detail: string }) {
  return <Card variant="outlined"><CardContent><Typography variant="subtitle2">{label}</Typography><Typography variant="h4" sx={{ my: 1 }}>{value}</Typography><Typography variant="body2" color="text.secondary">{detail}</Typography></CardContent></Card>;
}

function validDate(value: string) {
  return /^\d{4}-\d{2}-\d{2}$/.test(value) && Number.isFinite(Date.parse(value)) && new Date(value).toISOString().slice(0, 10) === value;
}
