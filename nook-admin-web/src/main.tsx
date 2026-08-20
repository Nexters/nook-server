import AssignmentTurnedInIcon from "@mui/icons-material/AssignmentTurnedIn";
import ArrowForwardIcon from "@mui/icons-material/ArrowForward";
import DashboardIcon from "@mui/icons-material/Dashboard";
import OpenInNewIcon from "@mui/icons-material/OpenInNew";
import PlaceIcon from "@mui/icons-material/Place";
import { Alert, Autocomplete, Box, Button, Card, CardActionArea, CardActions, CardContent, Chip, CircularProgress, Dialog, DialogActions, DialogContent, DialogTitle, Stack, TextField, Typography } from "@mui/material";
import { createTheme } from "@mui/material/styles";
import React, { useEffect, useState } from "react";
import { Admin, Datagrid, DateField, FunctionField, Layout, List, Menu, Resource, Show, SimpleShowLayout, TextField as RaTextField, TextInput, TopToolbar, type DataProvider, type LayoutProps, useNotify, useRecordContext, useRefresh } from "react-admin";
import { createRoot } from "react-dom/client";
import "./styles.css";

const apiBase = (import.meta.env.VITE_ADMIN_API_BASE_URL ?? "").replace(/\/$/, "");
type Envelope<T> = { success?: T; error?: { reason?: string } };
type Page<T> = { items: T[]; total: number };
type Place = { id: number; name: string; address: string; provider: string; externalPlaceId: string };
type PostDetail = { id: number; canonicalUrl: string; authorIdentifier?: string; title?: string; body?: string; contentParsingStatus: string; contentParsingFailureReason?: string; placeParsingStatus?: string; placeParsingFailureReason?: string; savedUserCount: number; mappingReviewed: boolean; places: Array<Place & { sequence: number }> };
type ManagedPlace = Place & { linkedPostCount: number; affectedUserCount: number; posts: Array<{ id: number; title?: string; authorIdentifier?: string; canonicalUrl: string; createdAt: string }> };

async function api<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${apiBase}/api/admin/v1${path}`, { credentials: "include", ...init, headers: { "Content-Type": "application/json", ...init?.headers } });
  const envelope = (await response.json().catch(() => ({}))) as Envelope<T>;
  if (!response.ok || envelope.success === undefined) throw new Error(envelope.error?.reason ?? `요청에 실패했습니다. (${response.status})`);
  return envelope.success;
}

const dataProvider: DataProvider = {
  getList: async (resource, params) => {
    const limit = params.pagination?.perPage ?? 20;
    const offset = ((params.pagination?.page ?? 1) - 1) * limit;
    const search = new URLSearchParams({ offset: String(offset), limit: String(limit) });
    Object.entries(params.filter ?? {}).forEach(([key, value]) => value && search.set(key, String(value)));
    const path = resource === "places" ? `/places/manage?${search}` : `/${resource}?${search}`;
    const result = await api<Page<Record<string, unknown>>>(path);
    return { data: result.items as never[], total: result.total };
  },
  getOne: async (resource, params) => ({ data: await api(`/${resource}/${params.id}`) }),
  getMany: async () => ({ data: [] }), getManyReference: async () => ({ data: [], total: 0 }),
  create: async () => Promise.reject(new Error("지원하지 않는 동작입니다.")), update: async () => Promise.reject(new Error("지원하지 않는 동작입니다.")), updateMany: async () => Promise.reject(new Error("지원하지 않는 동작입니다.")), delete: async () => Promise.reject(new Error("지원하지 않는 동작입니다.")), deleteMany: async () => Promise.reject(new Error("지원하지 않는 동작입니다.")),
};

const theme = createTheme({
  palette: {
    primary: { main: "#558EFF", dark: "#356FE5", light: "#EDF3FF", contrastText: "#FFFFFF" },
    secondary: { main: "#738295" },
    error: { main: "#FA5947" },
    warning: { main: "#FFA30E" },
    success: { main: "#2BAE7F" },
    info: { main: "#4E6AF3" },
    text: { primary: "#1F1F1F", secondary: "#67707D", disabled: "#99A0AC" },
    divider: "#E4E6E9",
    background: { default: "#F4F5F7", paper: "#FFFFFF" },
  },
  shape: { borderRadius: 12 },
  typography: {
    fontFamily: '"SUIT Variable", SUIT, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif',
    h4: { fontSize: 24, fontWeight: 800, lineHeight: 1.5, letterSpacing: "-0.02em" },
    h5: { fontSize: 20, fontWeight: 800, lineHeight: 1.5, letterSpacing: "-0.02em" },
    h6: { fontSize: 18, fontWeight: 700, lineHeight: 1.5, letterSpacing: "-0.02em" },
    subtitle1: { fontSize: 16, fontWeight: 700, lineHeight: 1.5, letterSpacing: "-0.02em" },
    subtitle2: { fontSize: 14, fontWeight: 700, lineHeight: 1.5, letterSpacing: "-0.02em" },
    body1: { fontSize: 14, fontWeight: 500, lineHeight: 1.5, letterSpacing: "-0.02em" },
    body2: { fontSize: 12, fontWeight: 500, lineHeight: 1.5, letterSpacing: "-0.02em" },
    button: { fontSize: 14, fontWeight: 700, letterSpacing: "-0.02em", textTransform: "none" },
  },
  components: {
    MuiCssBaseline: { styleOverrides: { body: { color: "#1F1F1F" } } },
    MuiAppBar: { styleOverrides: { root: { color: "#1F1F1F", background: "rgba(255,255,255,.88)", borderBottom: "1px solid #E4E6E9", boxShadow: "none", backdropFilter: "blur(18px)" } } },
    MuiButton: { defaultProps: { disableElevation: true }, styleOverrides: { root: { minHeight: 40, borderRadius: 10, paddingInline: 16, "&.MuiButton-containedPrimary": { boxShadow: "0 8px 18px rgba(85,142,255,.22)" } } } },
    MuiCard: { styleOverrides: { root: { borderColor: "#E4E6E9", borderRadius: 16, boxShadow: "0 8px 24px rgba(53,60,70,.06)" } } },
    MuiCardContent: { styleOverrides: { root: { padding: 20, "&:last-child": { paddingBottom: 20 } } } },
    MuiChip: { styleOverrides: { root: { borderRadius: 8, fontWeight: 700 }, sizeSmall: { height: 26, fontSize: 11 } } },
    MuiDialog: { styleOverrides: { paper: { borderRadius: 20, boxShadow: "0 24px 72px rgba(31,31,31,.18)" } } },
    MuiDialogTitle: { styleOverrides: { root: { padding: "24px 24px 12px", fontSize: 20, fontWeight: 800, letterSpacing: "-0.02em" } } },
    MuiOutlinedInput: { styleOverrides: { root: { borderRadius: 10, background: "#FFFFFF", "&:hover .MuiOutlinedInput-notchedOutline": { borderColor: "#99A0AC" }, "&.Mui-focused .MuiOutlinedInput-notchedOutline": { borderWidth: 1 } }, notchedOutline: { borderColor: "#CACED4" } } },
    MuiAlert: { styleOverrides: { root: { borderRadius: 12, alignItems: "center", "&.MuiAlert-standardInfo": { background: "#F0F3FF" }, "&.MuiAlert-standardWarning": { background: "#FFF7E8" }, "&.MuiAlert-standardSuccess": { background: "#EAF8F3" } } } },
    MuiTableCell: { styleOverrides: { root: { paddingBlock: 14 } } },
  },
});

function App() { return <Admin dashboard={Dashboard} dataProvider={dataProvider} disableTelemetry layout={AdminLayout} theme={theme} title="Nook Admin"><Resource name="posts" options={{ label: "게시글 관리" }} icon={AssignmentTurnedInIcon} list={PostList} show={PostShow} /><Resource name="places" options={{ label: "장소 관리" }} icon={PlaceIcon} list={PlaceList} show={PlaceShow} /><Resource name="audit-logs" options={{ label: "감사 로그" }} icon={AssignmentTurnedInIcon} list={AuditLogList} /></Admin>; }
function AdminLayout(props: LayoutProps) { return <Layout {...props} menu={AdminMenu} />; }
function AdminMenu() { return <Menu><Box className="nook-brand"><Box className="nook-brand-mark">N</Box><Box className="nook-brand-copy"><span className="nook-brand-title">Nook Admin</span><span className="nook-brand-caption">Operations</span></Box></Box><Menu.DashboardItem leftIcon={<DashboardIcon />} /><Menu.ResourceItem name="posts" /><Menu.ResourceItem name="places" /><Menu.ResourceItem name="audit-logs" /></Menu>; }

function Dashboard() {
  const destinations = [
    { href: "#/posts", eyebrow: "CONTENT", title: "게시글 검수", description: "파싱 상태와 장소 매핑을 확인하고 필요한 교정을 진행합니다.", icon: <AssignmentTurnedInIcon /> },
    { href: "#/places", eyebrow: "PLACE", title: "장소 관리", description: "공용 장소 정보와 연결된 게시글, 사용자 영향 범위를 확인합니다.", icon: <PlaceIcon /> },
    { href: "#/audit-logs", eyebrow: "HISTORY", title: "감사 로그", description: "운영자가 수행한 변경과 사유, 변경 전후 값을 조회합니다.", icon: <DashboardIcon /> },
  ];
  return <Stack spacing={3} sx={{ maxWidth: 1080 }}><Box><Typography variant="overline" color="primary.main" sx={{ fontFamily: '"Roboto Mono", monospace', fontWeight: 600, letterSpacing: ".08em" }}>NOOK OPERATIONS</Typography><Typography variant="h4">오늘의 운영 업무</Typography><Typography color="text.secondary" sx={{ mt: .5 }}>확인하거나 관리할 영역을 선택하세요.</Typography></Box><Stack direction={{ xs: "column", md: "row" }} spacing={2}>{destinations.map((destination) => <Card variant="outlined" sx={{ flex: 1 }} key={destination.href}><CardActionArea href={destination.href} sx={{ height: "100%", p: .5 }}><CardContent><Box className="summary-icon">{destination.icon}</Box><Typography variant="overline" color="text.secondary" sx={{ display: "block", mt: 2 }}>{destination.eyebrow}</Typography><Typography variant="h6">{destination.title}</Typography><Typography color="text.secondary" sx={{ mt: 1, minHeight: 42 }}>{destination.description}</Typography><Stack direction="row" spacing={.5} sx={{ alignItems: "center", mt: 2, color: "primary.main" }}><Typography variant="subtitle2">관리 화면 열기</Typography><ArrowForwardIcon fontSize="small" /></Stack></CardContent></CardActionArea></Card>)}</Stack><Alert severity="info" variant="outlined">공용 데이터 변경은 사용자 화면에 영향을 줄 수 있습니다. 상세 화면의 영향 범위를 확인하고 사유를 남겨주세요.</Alert></Stack>;
}

function StatusChip({ value }: { value?: string }) {
  const normalized = value ?? "미시작";
  const tone = normalized.includes("COMPLETED") || normalized === "완료" ? { color: "#16795A", background: "#EAF8F3" } : normalized.includes("FAILED") ? { color: "#C33D30", background: "#FFF0EE" } : { color: "#67707D", background: "#F4F5F7" };
  return <Chip size="small" label={normalized} sx={tone} />;
}

function PostList() { return <List filters={[<TextInput key="query" source="query" label="제목·작성자·URL 검색" alwaysOn />]}><Datagrid rowClick="show" bulkActionButtons={false}><RaTextField source="id" label="ID" sx={{ fontFamily: '"Roboto Mono", monospace', color: "text.secondary" }} /><RaTextField source="title" label="제목" sx={{ fontWeight: 700 }} /><RaTextField source="authorIdentifier" label="작성자" sx={{ fontFamily: '"Roboto Mono", monospace' }} /><FunctionField label="게시글 파싱" render={(record: { contentParsingStatus?: string }) => <StatusChip value={record.contentParsingStatus} />} /><FunctionField label="장소 파싱" render={(record: { placeParsingStatus?: string }) => <StatusChip value={record.placeParsingStatus} />} /><RaTextField source="placeCount" label="장소 수" /><RaTextField source="savedUserCount" label="저장 사용자" /><FunctionField label="검수" render={(record: { mappingReviewed: boolean }) => <StatusChip value={record.mappingReviewed ? "완료" : "미검수"} />} /><DateField source="createdAt" label="생성일" showTime /></Datagrid></List>; }
function PostShow() { return <Show actions={<PostActions />}><SimpleShowLayout><PostPanel /></SimpleShowLayout></Show>; }
function PostActions() { const record = useRecordContext<PostDetail>(); return <TopToolbar>{record && <Button href={record.canonicalUrl} target="_blank" endIcon={<OpenInNewIcon />}>원문 열기</Button>}</TopToolbar>; }

function PostPanel() {
  const record = useRecordContext<PostDetail>(); const [open, setOpen] = useState(false); if (!record?.places) return null;
  const failure = record.contentParsingFailureReason ?? record.placeParsingFailureReason;
  return <Stack spacing={3}><Stack direction="row" spacing={1} useFlexGap sx={{ flexWrap: "wrap" }}><StatusChip value={`게시글 ${record.contentParsingStatus}`} /><StatusChip value={`장소 ${record.placeParsingStatus ?? "미시작"}`} /><StatusChip value={record.mappingReviewed ? "완료" : "미검수"} /></Stack>{failure && <Alert severity="error">{failure}</Alert>}<Card variant="outlined"><CardContent><Typography variant="h6">{record.title ?? "제목 없음"}</Typography><Typography color="text.secondary" className="mono-text">{record.authorIdentifier ?? "작성자 미상"} · 저장 사용자 {record.savedUserCount}명</Typography><Typography sx={{ whiteSpace: "pre-wrap", mt: 2, color: "#353C46" }}>{record.body}</Typography></CardContent></Card><Box><Stack direction={{ xs: "column", sm: "row" }} spacing={1} sx={{ justifyContent: "space-between", alignItems: { sm: "center" } }}><Box><Typography variant="h6">공용 장소 매핑</Typography><Typography color="text.secondary" variant="body2">장소를 선택하면 장소 관리 상세로 이동합니다.</Typography></Box><Button variant="contained" onClick={() => setOpen(true)}>매핑 교정</Button></Stack><Stack spacing={1.5} sx={{ mt: 2 }}>{record.places.map((place) => <Card variant="outlined" key={place.id}><CardActionArea href={`#/places/${place.id}/show`}><CardContent><Stack direction="row" spacing={1.5} sx={{ alignItems: "center" }}><Box className="summary-icon">{place.sequence + 1}</Box><Box sx={{ flex: 1 }}><Typography variant="subtitle1">{place.name}</Typography><Typography color="text.secondary">{place.address}</Typography><Typography variant="caption" className="mono-text" color="text.disabled">{place.provider} · {place.externalPlaceId}</Typography></Box><ArrowForwardIcon color="disabled" /></Stack></CardContent></CardActionArea></Card>)}</Stack></Box><MappingDialog open={open} post={record} onClose={() => setOpen(false)} /></Stack>;
}

function MappingDialog({ open, post, onClose }: { open: boolean; post: PostDetail; onClose: () => void }) {
  const [selected, setSelected] = useState<Place[]>(post.places); const [options, setOptions] = useState<Place[]>(post.places); const [query, setQuery] = useState(""); const [reason, setReason] = useState(""); const [saving, setSaving] = useState(false); const notify = useNotify(); const refresh = useRefresh();
  useEffect(() => { if (query.trim().length < 2) return; const timer = window.setTimeout(() => api<Place[]>(`/places?query=${encodeURIComponent(query)}`).then((items) => setOptions([...selected, ...items].filter((item, index, all) => all.findIndex((candidate) => candidate.id === item.id) === index))), 250); return () => window.clearTimeout(timer); }, [query, selected]);
  const save = async () => { setSaving(true); try { await api(`/posts/${post.id}/places`, { method: "PUT", body: JSON.stringify({ placeIds: selected.map((place) => place.id), reason }) }); notify("매핑을 교정하고 감사 로그를 남겼습니다.", { type: "success" }); refresh(); onClose(); } catch (error) { notify(error instanceof Error ? error.message : "교정에 실패했습니다.", { type: "error" }); } finally { setSaving(false); } };
  return <Dialog open={open} onClose={onClose} fullWidth maxWidth="md"><DialogTitle>공용 장소 매핑 교정</DialogTitle><DialogContent><Stack spacing={2} sx={{ mt: 1 }}><Alert severity="info">기존 사용자 {post.savedUserCount}명의 저장 장소는 유지되고 이후 저장 건에만 적용됩니다. 장소를 모두 제거하는 교정도 가능합니다.</Alert><Autocomplete multiple options={options} value={selected} onChange={(_, value) => setSelected(value)} onInputChange={(_, value) => setQuery(value)} getOptionLabel={(place) => `${place.name} · ${place.address}`} isOptionEqualToValue={(a, b) => a.id === b.id} renderInput={(params) => <TextField {...params} label="장소 검색" helperText="두 글자 이상 입력하세요." />} /><TextField label="수정 사유" required multiline minRows={3} value={reason} onChange={(event) => setReason(event.target.value)} /></Stack></DialogContent><DialogActions><Button onClick={onClose}>취소</Button><Button variant="contained" disabled={saving || !reason.trim()} onClick={save}>{saving ? <CircularProgress size={20} /> : "교정 저장"}</Button></DialogActions></Dialog>;
}

function PlaceList() { return <List filters={[<TextInput key="query" source="query" label="장소명·주소·외부 ID 검색" alwaysOn />]}><Datagrid rowClick="show" bulkActionButtons={false}><RaTextField source="id" label="ID" sx={{ fontFamily: '"Roboto Mono", monospace', color: "text.secondary" }} /><RaTextField source="name" label="장소명" sx={{ fontWeight: 700 }} /><RaTextField source="address" label="주소" /><FunctionField label="Provider" render={(record: Place) => <Chip size="small" variant="outlined" label={record.provider} sx={{ fontFamily: '"Roboto Mono", monospace' }} />} /><RaTextField source="externalPlaceId" label="외부 ID" sx={{ fontFamily: '"Roboto Mono", monospace' }} /><RaTextField source="linkedPostCount" label="연결 게시글" /><RaTextField source="affectedUserCount" label="영향 사용자" /></Datagrid></List>; }
function PlaceShow() { return <Show><SimpleShowLayout><PlacePanel /></SimpleShowLayout></Show>; }
function PlacePanel() {
  const record = useRecordContext<ManagedPlace>(); const [open, setOpen] = useState(false); if (!record?.posts) return null;
  return <Stack spacing={3}><Alert severity="warning" variant="outlined">이 장소는 게시글 {record.linkedPostCount}개와 저장 사용자 {record.affectedUserCount}명에게 노출됩니다. 장소명·주소 수정은 기존 사용자 화면에도 즉시 반영됩니다.</Alert><Card variant="outlined"><CardContent><Stack direction={{ xs: "column", sm: "row" }} spacing={2} sx={{ justifyContent: "space-between", alignItems: { sm: "center" } }}><Box><Stack direction="row" spacing={1} sx={{ alignItems: "center" }}><Typography variant="h5">{record.name}</Typography><Chip size="small" variant="outlined" label={record.provider} /></Stack><Typography color="text.secondary" sx={{ mt: .75 }}>{record.address}</Typography><Typography variant="caption" className="mono-text" color="text.disabled">{record.externalPlaceId}</Typography></Box><Button variant="contained" onClick={() => setOpen(true)}>장소 정보 수정</Button></Stack></CardContent></Card><Box><Typography variant="h6">연결 게시글</Typography><Typography variant="body2" color="text.secondary" sx={{ mb: 1.5 }}>게시글을 선택하면 게시글 관리 상세로 이동합니다.</Typography><Stack spacing={1.5}>{record.posts.map((post) => <Card variant="outlined" key={post.id}><CardActionArea href={`#/posts/${post.id}/show`}><CardContent><Stack direction="row" spacing={1} sx={{ alignItems: "center" }}><Box sx={{ flex: 1 }}><Typography variant="subtitle1">{post.title ?? "제목 없음"}</Typography><Typography color="text.secondary" className="mono-text">{post.authorIdentifier ?? "작성자 미상"}</Typography></Box><ArrowForwardIcon color="disabled" /></Stack></CardContent></CardActionArea><CardActions sx={{ px: 2, pb: 2, pt: 0 }}><Button size="small" href={post.canonicalUrl} target="_blank" endIcon={<OpenInNewIcon />}>원문 열기</Button></CardActions></Card>)}</Stack></Box><PlaceEditDialog open={open} place={record} onClose={() => setOpen(false)} /></Stack>;
}
function PlaceEditDialog({ open, place, onClose }: { open: boolean; place: ManagedPlace; onClose: () => void }) {
  const [name, setName] = useState(place.name); const [address, setAddress] = useState(place.address); const [reason, setReason] = useState(""); const [saving, setSaving] = useState(false); const notify = useNotify(); const refresh = useRefresh();
  const save = async () => { setSaving(true); try { await api(`/places/${place.id}`, { method: "PUT", body: JSON.stringify({ name, address, reason }) }); notify("장소 정보를 수정하고 감사 로그를 남겼습니다.", { type: "success" }); refresh(); onClose(); } catch (error) { notify(error instanceof Error ? error.message : "수정에 실패했습니다.", { type: "error" }); } finally { setSaving(false); } };
  return <Dialog open={open} onClose={onClose} fullWidth maxWidth="sm"><DialogTitle>공용 장소 정보 수정</DialogTitle><DialogContent><Stack spacing={2} sx={{ mt: 1 }}><Alert severity="warning">게시글 {place.linkedPostCount}개와 저장 사용자 {place.affectedUserCount}명의 화면에 변경된 정보가 표시됩니다.</Alert><TextField label="장소명" required value={name} onChange={(event) => setName(event.target.value)} /><TextField label="주소" required value={address} onChange={(event) => setAddress(event.target.value)} /><TextField label="수정 사유" required multiline minRows={3} value={reason} onChange={(event) => setReason(event.target.value)} /></Stack></DialogContent><DialogActions><Button onClick={onClose}>취소</Button><Button variant="contained" disabled={saving || !name.trim() || !address.trim() || !reason.trim()} onClick={save}>{saving ? <CircularProgress size={20} /> : "수정 저장"}</Button></DialogActions></Dialog>;
}

function AuditLogList() { return <List filters={[<TextInput key="targetType" source="targetType" label="대상 유형" />, <TextInput key="targetId" source="targetId" label="대상 ID" />]}><Datagrid bulkActionButtons={false} expand={<AuditDetails />}><DateField source="createdAt" label="시각" showTime /><RaTextField source="actorEmail" label="운영자" sx={{ fontFamily: '"Roboto Mono", monospace' }} /><FunctionField label="동작" render={(record: { action?: string }) => <Chip size="small" label={record.action ?? "-"} sx={{ color: "#356FE5", background: "#EDF3FF" }} />} /><RaTextField source="targetType" label="대상" /><RaTextField source="targetId" label="대상 ID" sx={{ fontFamily: '"Roboto Mono", monospace' }} /><RaTextField source="reason" label="사유" /><RaTextField source="requestId" label="Request ID" sx={{ fontFamily: '"Roboto Mono", monospace', color: "text.secondary" }} /></Datagrid></List>; }
function AuditDetails() { const record = useRecordContext<{ beforeValue?: string; afterValue?: string }>(); if (!record) return null; return <Stack direction={{ xs: "column", md: "row" }} spacing={2} sx={{ p: 2 }}><Box sx={{ flex: 1 }}><Typography variant="subtitle2">변경 전</Typography><Box component="pre" sx={{ whiteSpace: "pre-wrap", overflowWrap: "anywhere" }}>{formatJson(record.beforeValue)}</Box></Box><Box sx={{ flex: 1 }}><Typography variant="subtitle2">변경 후</Typography><Box component="pre" sx={{ whiteSpace: "pre-wrap", overflowWrap: "anywhere" }}>{formatJson(record.afterValue)}</Box></Box></Stack>; }
function formatJson(value?: string) { if (!value) return "-"; try { return JSON.stringify(JSON.parse(value), null, 2); } catch { return value; } }

createRoot(document.getElementById("root")!).render(<React.StrictMode><App /></React.StrictMode>);
