import { Accordion, AccordionDetails, AccordionSummary, Alert, Box, Card, CardActionArea, CardContent, Chip, Stack, Typography } from "@mui/material";
import ExpandMoreIcon from "@mui/icons-material/ExpandMore";
import { Link as RouterLink } from "react-router-dom";

export type UnresolvedPlaceResult = {
  clue: { name: string; region?: string; addressHint?: string; queries?: string[]; evidence?: { imageIndex: number; evidenceText: string }[] };
  reason: string;
  type?: "NOT_EXTRACTED" | "RESOLUTION_FAILED";
};
export type PlaceResultPost = {
  id: number;
  placeParsingStatus?: string;
  placeParsingOutcome?: string;
  expectedPlaceCount?: number;
  extractedPlaceCount?: number;
  resolvedPlaceCount?: number;
  unresolvedPlaceClues?: UnresolvedPlaceResult[];
  mappingReviewed?: boolean;
  places: { id: number; name: string; address: string }[];
};

const clean = (text: string) => text.replace(/https?:\/\/[^\s<>"']+/gi, "[원본 URL 숨김]").replace(/(Bearer\s+)[a-z0-9._~+/-]+/gi, "$1[숨김]").replace(/((?:token|password|secret|api[_-]?key|authorization)["']?\s*[:=]\s*)(?:"[^"]*"|'[^']*'|[^,;\s]+)/gi, "$1[숨김]");
function cause(item: UnresolvedPlaceResult) {
  if (item.type === "NOT_EXTRACTED") return "원문에 있는 장소를 분석 결과에서 찾지 못했습니다.";
  if (/not grounded/i.test(item.reason)) return "검색한 장소를 원문 근거로 확인할 수 없어 연결하지 못했습니다.";
  if (/no place candidate found/i.test(item.reason)) return "장소 검색에서 일치하는 후보를 찾지 못했습니다.";
  if (/no place candidate selected/i.test(item.reason)) return "검색 후보 중 원문의 장소로 확정할 항목을 고르지 못했습니다.";
  return "이 장소 단서를 저장된 장소로 연결하지 못했습니다.";
}

export function PostPlaceResults({ post, actions }: { post: PlaceResultPost; actions?: React.ReactNode }) {
  const unresolved = [...new Map((post.unresolvedPlaceClues ?? []).map(item => [JSON.stringify([item.clue.name, item.clue.region, item.clue.addressHint]), item])).values()];
  const resolved = post.resolvedPlaceCount;
  const unaccounted = post.expectedPlaceCount != null && resolved != null ? Math.max(0, post.expectedPlaceCount - resolved - unresolved.length) : 0;
  const partial = post.placeParsingOutcome === "PARTIAL";
  const inProgress = ["PENDING", "PROCESSING"].includes(post.placeParsingStatus ?? "");
  return <Stack spacing={2}>
    <Stack direction={{ xs: "column", sm: "row" }} spacing={1} sx={{ justifyContent: "space-between" }}><Typography variant="h6">장소별 처리 결과</Typography>{actions}</Stack>
    <Stack direction="row" useFlexGap sx={{ flexWrap: "wrap" }} spacing={1}>
      <Chip label={`예상 ${post.expectedPlaceCount ?? "미기록"}${post.expectedPlaceCount != null ? "곳" : ""}`} />
      <Chip label={`단서 추출 ${post.extractedPlaceCount ?? "미기록"}${post.extractedPlaceCount != null ? "곳" : ""}`} />
      <Chip label={`파싱 시 저장 ${resolved ?? "미기록"}${resolved != null ? "곳" : ""}`} />
    </Stack>
    <Typography variant="body2" color="text.secondary">장소 개수에 대한 결과입니다. 본문·미디어·태그 작업의 성공 건수와는 별도로 집계합니다. 예상 수는 분석에서 추정한 값입니다.</Typography>
    {inProgress && <Alert severity="info">장소 처리가 진행 중입니다. 아래 진단은 이전 실행 결과일 수 있습니다.</Alert>}
    {post.placeParsingStatus === "FAILED" && <Alert severity="error">장소 처리가 실패했습니다. 아래 저장 목록은 현재 남아 있는 연결이고, 진단은 마지막으로 기록된 결과입니다.</Alert>}
    {unaccounted > 0 && <Alert severity="warning"><Typography variant="subtitle2">예상 수 기준 결과 미확인 {unaccounted}곳</Typography><Typography variant="body2">예상 {post.expectedPlaceCount}곳 중 저장 {resolved}곳, 이름이 기록된 미해결 단서 {unresolved.length}곳입니다. 나머지 항목은 이름과 실패 사유가 기록돼 있지 않아 어느 장소인지 확정할 수 없습니다. 원문과 저장 목록을 대조해야 합니다.</Typography></Alert>}
    {partial && unresolved.length === 0 && unaccounted === 0 && <Alert severity="warning">일부 완료로 기록됐지만, 미해결 항목의 상세 사유는 저장돼 있지 않습니다.</Alert>}
    {unresolved.length > 0 && <Box><Typography variant="subtitle1" sx={{ mb: 1 }}>미해결 단서 · {unresolved.length}곳</Typography><Stack spacing={1}>
      {unresolved.map((item, index) => <Card variant="outlined" key={`${item.clue.name}-${index}`}><CardContent><Stack spacing={1}>
        <Stack direction="row" spacing={1} useFlexGap sx={{ flexWrap: "wrap" }}><Typography variant="subtitle2">{item.clue.name}</Typography><Chip size="small" color="warning" label={item.type === "NOT_EXTRACTED" ? "단서 추출 누락" : "장소 검색·선택 미해결"} /></Stack>
        {(item.clue.region || item.clue.addressHint) && <Typography variant="body2">{item.clue.addressHint ?? item.clue.region}</Typography>}
        <Typography variant="body2">{cause(item)}</Typography>
        <Accordion disableGutters variant="outlined"><AccordionSummary expandIcon={<ExpandMoreIcon />}>사유·검색어·원문 근거</AccordionSummary><AccordionDetails><Stack spacing={1}>
          <Typography variant="body2" sx={{ overflowWrap: "anywhere" }}>기록된 사유: {clean(item.reason || "미기록")}</Typography>
          {!!item.clue.queries?.length && <Typography variant="body2" sx={{ overflowWrap: "anywhere" }}>검색어: {clean(item.clue.queries.join(" · "))}</Typography>}
          {item.clue.evidence?.map((e, i) => <Typography key={i} variant="body2" sx={{ whiteSpace: "pre-wrap", overflowWrap: "anywhere" }}>원문 이미지 {e.imageIndex}번: {clean(e.evidenceText)}</Typography>)}
        </Stack></AccordionDetails></Accordion>
      </Stack></CardContent></Card>)}
    </Stack></Box>}
    <Box><Typography variant="subtitle1" sx={{ mb: 1 }}>현재 저장된 장소 · {post.places.length}곳</Typography>
      {(post.mappingReviewed || (resolved != null && resolved !== post.places.length)) && <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>현재 연결은 수동 교정 등으로 파싱 당시 결과와 다를 수 있습니다.</Typography>}
      {!post.places.length ? <Alert severity="info">현재 연결된 장소가 없습니다.{post.placeParsingStatus === "COMPLETED" && !partial && resolved === 0 && !post.mappingReviewed ? " 장소 0곳으로 처리가 완료됐습니다." : ""}</Alert> : <Stack spacing={1}>{post.places.map(place => <Card variant="outlined" key={place.id}><CardActionArea component={RouterLink} to={`/places/${place.id}/show`} aria-label={`${place.name} 장소 상세`}><CardContent><Stack direction="row" spacing={1} sx={{ justifyContent: "space-between" }}><Box><Typography variant="subtitle2">{place.name}</Typography><Typography variant="body2" color="text.secondary">{place.address}</Typography></Box><Chip size="small" color="success" label="저장됨" /></Stack></CardContent></CardActionArea></Card>)}</Stack>}
    </Box>
  </Stack>;
}
