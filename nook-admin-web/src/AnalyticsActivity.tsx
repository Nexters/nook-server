import { Accordion, AccordionDetails, AccordionSummary, Alert, Box, Button, Chip, CircularProgress, IconButton, Link, MenuItem, Stack, TextField, Typography } from "@mui/material";
import ExpandMoreIcon from "@mui/icons-material/ExpandMore";
import CloseIcon from "@mui/icons-material/Close";
import { useId, useState } from "react";
import { eventColors, eventLabels, kstTime, num, useAnalytics, type ActivityScope } from "./analyticsUi";

type Page<T> = { items: T[]; total: number; page: number; size: number };
type Member = { memberId: number; nickname: string | null; events: number; lastEventAt: string };
type Event = { eventId: string; eventName: string; occurredAt: string; targetType: string | null; targetId: number | null; legacy: boolean };

export function ActivityExplorer({ scope, onClose }: { scope: ActivityScope; onClose: () => void }) {
  const [page, setPage] = useState(0);
  const [eventName, setEventName] = useState(scope.eventName ?? "all");
  const [revision, setRevision] = useState(0);
  const query = new URLSearchParams({ from: scope.from, to: scope.to, page: String(page), size: "20" });
  if (eventName !== "all") query.set("eventName", eventName);
  const result = useAnalytics<Page<Member>>(`/user-analytics/activity/members?${query}`, revision);
  return <Box className="analytics-explorer" role="region" aria-label={`${scope.title} 활동 상세`}>
    <Stack direction="row" sx={{ justifyContent: "space-between", alignItems: "start", gap: 1 }}>
      <Box><Typography variant="overline" color="primary.main">ACTIVITY DETAILS</Typography><Typography variant="h6">{scope.title}</Typography>
        <Typography variant="caption" color="text.secondary">{scope.from} ~ {scope.to} · KST · 회원을 펼쳐 이벤트 확인</Typography></Box>
      <IconButton size="small" aria-label="활동 상세 닫기" onClick={onClose}><CloseIcon /></IconButton>
    </Stack>
    <Stack direction="row" sx={{ alignItems: "center", justifyContent: "space-between", gap: 1, my: 2 }}>
      <TextField select label="상세 이벤트 필터" size="small" value={eventName} onChange={e => { setEventName(e.target.value); setPage(0); }} sx={{ minWidth: 170 }}>
        <MenuItem value="all">전체 이벤트</MenuItem>{Object.entries(eventLabels).map(([key, label]) => <MenuItem key={key} value={key}>{label}</MenuItem>)}
      </TextField>
      <Typography variant="subtitle2" aria-live="polite">{result.data ? `총 ${num.format(result.data.total)}명` : result.error ? "조회 실패" : "조회 중"}</Typography>
    </Stack>
    {result.error ? <Alert severity="error" action={<Button onClick={() => setRevision(v => v + 1)}>재시도</Button>}>{result.error}</Alert> : !result.data ? <Loading /> : result.data.total === 0 ? <Alert severity="info">이 조건에 수집된 활동 회원이 없습니다.</Alert> : <>
      <Box sx={{ maxHeight: 560, overflowY: "auto" }}>{result.data.items.map(member => <MemberRow key={member.memberId} member={member} scope={{ ...scope, eventName: eventName === "all" ? undefined : eventName }} />)}</Box>
      <Pager page={page} total={result.data.total} size={result.data.size} onPage={setPage} />
      <Typography variant="caption" color="text.secondary">최근 활동순 · 기록이 추가되면 상단 지표와 조회 시점에 따라 차이가 날 수 있습니다.</Typography>
    </>}
  </Box>;
}

function MemberRow({ member, scope }: { member: Member; scope: ActivityScope }) {
  const [expanded, setExpanded] = useState(false);
  const id = useId();
  return <Accordion expanded={expanded} onChange={(_, value) => setExpanded(value)} disableGutters elevation={0} sx={{ bgcolor: "transparent", "&:before": { display: "none" }, borderBottom: 1, borderColor: "divider" }}>
    <AccordionSummary expandIcon={<ExpandMoreIcon />} aria-controls={`${id}-activity`} id={`${id}-heading`}>
      <Stack direction="row" sx={{ alignItems: "center", gap: 1.5, width: "100%", minWidth: 0 }}>
        <Box className="analytics-avatar">{(member.nickname || "#").slice(0, 1)}</Box>
        <Box sx={{ flex: 1, minWidth: 0 }}><Typography variant="subtitle2" sx={{ overflowWrap: "anywhere" }}>{member.nickname ?? "이름 없는 회원"} <Typography component="span" variant="caption" color="text.secondary">#{member.memberId}</Typography></Typography>
          <Typography variant="caption" color="text.secondary">{kstTime(member.lastEventAt)}</Typography></Box>
        <Chip size="small" label={`${num.format(member.events)}건`} />
      </Stack>
    </AccordionSummary>
    <AccordionDetails id={`${id}-activity`}>{expanded && <MemberTimeline memberId={member.memberId} scope={scope} />}</AccordionDetails>
  </Accordion>;
}

function MemberTimeline({ memberId, scope }: { memberId: number; scope: ActivityScope }) {
  const [page, setPage] = useState(0);
  const [revision, setRevision] = useState(0);
  const query = new URLSearchParams({ from: scope.from, to: scope.to, memberId: String(memberId), page: String(page), size: "20" });
  if (scope.eventName) query.set("eventName", scope.eventName);
  const result = useAnalytics<Page<Event>>(`/user-analytics/activity/events?${query}`, revision);
  if (result.error) return <Alert severity="error" action={<Button onClick={() => setRevision(v => v + 1)}>재시도</Button>}>{result.error}</Alert>;
  if (!result.data) return <Loading />;
  return <Box>
    <Box component="ol" className="analytics-timeline">{result.data.items.map(event => <Box component="li" key={event.eventId} sx={{ "--event-color": eventColors[event.eventName] ?? "#738295" }}>
      <Stack direction={{ xs: "column", sm: "row" }} sx={{ justifyContent: "space-between", gap: .5 }}>
        <Typography variant="subtitle2">{eventLabels[event.eventName] ?? event.eventName}</Typography>
        <Typography component="time" variant="caption" dateTime={event.occurredAt} color="text.secondary">{kstTime(event.occurredAt)}</Typography>
      </Stack>
      <Stack direction="row" sx={{ flexWrap: "wrap", alignItems: "center", gap: 1, mt: .5 }}>
        <EventTarget event={event} />{event.legacy && <Chip size="small" variant="outlined" label="이전 중복 제거 기록" />}
      </Stack>
    </Box>)}</Box>
    {result.data.total === 0 && <Typography variant="body2">이 조건에 기록된 이벤트가 없습니다.</Typography>}
    <Pager page={page} total={result.data.total} size={result.data.size} onPage={setPage} />
  </Box>;
}

function EventTarget({ event }: { event: Event }) {
  if (event.targetId == null) return <Typography variant="caption" color="text.secondary">대상 정보 없음</Typography>;
  const label = `${({ post: "게시물", place: "장소", group: "아카이브" } as Record<string, string>)[event.targetType ?? ""] ?? "대상"} #${event.targetId}`;
  const resource = event.targetType === "post" ? "posts" : event.targetType === "place" ? "places" : null;
  return resource ? <Link href={`#/${resource}/${event.targetId}/show`} variant="caption">{label} 상세 →</Link> : <Typography variant="caption" color="text.secondary">{label}</Typography>;
}

function Pager({ page, size, total, onPage }: { page: number; size: number; total: number; onPage: (page: number) => void }) {
  if (total <= size) return null;
  return <Stack direction="row" sx={{ justifyContent: "end", alignItems: "center", gap: 1, my: 1 }}>
    <Button size="small" disabled={page === 0} onClick={() => onPage(page - 1)}>이전</Button>
    <Typography variant="caption">{page + 1} / {Math.ceil(total / size)}</Typography>
    <Button size="small" disabled={(page + 1) * size >= total} onClick={() => onPage(page + 1)}>다음</Button>
  </Stack>;
}
export function Loading() { return <Box sx={{ py: 4, textAlign: "center" }}><CircularProgress size={24} aria-label="분석 불러오는 중" /></Box>; }
