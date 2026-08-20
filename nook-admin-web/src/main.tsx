import AssignmentTurnedInIcon from "@mui/icons-material/AssignmentTurnedIn";
import ArrowForwardIcon from "@mui/icons-material/ArrowForward";
import DashboardIcon from "@mui/icons-material/Dashboard";
import LocalOfferIcon from "@mui/icons-material/LocalOffer";
import OpenInNewIcon from "@mui/icons-material/OpenInNew";
import PlaceIcon from "@mui/icons-material/Place";
import { Alert, Autocomplete, Box, Button, Card, CardActionArea, CardActions, CardContent, Chip, CircularProgress, Dialog, DialogActions, DialogContent, DialogTitle, FormControlLabel, MenuItem, Stack, Switch, TextField, Typography } from "@mui/material";
import { createTheme } from "@mui/material/styles";
import React, { useEffect, useState } from "react";
import { Admin, Datagrid, DateField, FunctionField, Layout, List, Menu, Resource, SelectInput, Show, SimpleShowLayout, TextField as RaTextField, TextInput, TopToolbar, type DataProvider, type LayoutProps, useNotify, useRecordContext, useRefresh } from "react-admin";
import { createRoot } from "react-dom/client";
import "./styles.css";

const apiBase = (import.meta.env.VITE_ADMIN_API_BASE_URL ?? "").replace(/\/$/, "");
type Envelope<T> = { success?: T; error?: { reason?: string } };
type Page<T> = { items: T[]; total: number };
type Place = { id: number; name: string; address: string; provider: string; externalPlaceId: string; thumbnailUrl?: string; representativeTags?: string[] };
type PostMedia = { mediaType: "IMAGE" | "VIDEO"; mediaUrl: string; sequence: number };
type PostDetail = { id: number; canonicalUrl: string; authorIdentifier?: string; title?: string; body?: string; sourceLocationTag?: string; hashtags: string[]; media: PostMedia[]; manuallyOverridden: boolean; contentParsingStatus: string; contentParsingFailureReason?: string; placeParsingStatus?: string; placeParsingFailureReason?: string; savedUserCount: number; mappingReviewed: boolean; places: Array<Place & { sequence: number }> };
type ManagedPlace = Place & { city?: string; latitude: string; longitude: string; category?: string; phoneNumber?: string; photoUrls: string[]; openingHours?: unknown; linkedPostCount: number; affectedUserCount: number; posts: Array<{ id: number; title?: string; authorIdentifier?: string; canonicalUrl: string; createdAt: string }> };
type PlaceTagDefinition = { id: string; tagCode: string; category: string; displayName: string; matchingKeywords: string[]; enabled: boolean; sortOrder: number; updatedAt: string };

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
    Object.entries(params.filter ?? {}).forEach(([key, value]) => value !== undefined && value !== null && value !== "" && search.set(key, String(value)));
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

function App() { return <Admin dashboard={Dashboard} dataProvider={dataProvider} disableTelemetry layout={AdminLayout} theme={theme} title="Nook Admin"><Resource name="posts" options={{ label: "게시글 관리" }} icon={AssignmentTurnedInIcon} list={PostList} show={PostShow} /><Resource name="places" options={{ label: "장소 관리" }} icon={PlaceIcon} list={PlaceList} show={PlaceShow} /><Resource name="place-tags" options={{ label: "장소 태그 관리" }} icon={LocalOfferIcon} list={PlaceTagList} /><Resource name="audit-logs" options={{ label: "감사 로그" }} icon={AssignmentTurnedInIcon} list={AuditLogList} /></Admin>; }
function AdminLayout(props: LayoutProps) { return <Layout {...props} menu={AdminMenu} />; }
function AdminMenu() { return <Menu><Box className="nook-brand"><Box className="nook-brand-mark">N</Box><Box className="nook-brand-copy"><span className="nook-brand-title">Nook Admin</span><span className="nook-brand-caption">Operations</span></Box></Box><Menu.DashboardItem leftIcon={<DashboardIcon />} /><Menu.ResourceItem name="posts" /><Menu.ResourceItem name="places" /><Menu.ResourceItem name="place-tags" /><Menu.ResourceItem name="audit-logs" /></Menu>; }

function Dashboard() {
  const destinations = [
    { href: "#/posts", eyebrow: "CONTENT", title: "게시글 검수", description: "파싱 상태와 장소 매핑을 확인하고 필요한 교정을 진행합니다.", icon: <AssignmentTurnedInIcon /> },
    { href: "#/places", eyebrow: "PLACE", title: "장소 관리", description: "공용 장소 정보와 연결된 게시글, 사용자 영향 범위를 확인합니다.", icon: <PlaceIcon /> },
    { href: "#/place-tags", eyebrow: "TAG", title: "장소 태그 관리", description: "태그 이름과 매칭 키워드, 노출 여부와 순서를 관리합니다.", icon: <LocalOfferIcon /> },
    { href: "#/audit-logs", eyebrow: "HISTORY", title: "감사 로그", description: "운영자가 수행한 변경과 사유, 변경 전후 값을 조회합니다.", icon: <DashboardIcon /> },
  ];
  return <Stack spacing={3} sx={{ maxWidth: 1080 }}><Box><Typography variant="overline" color="primary.main" sx={{ fontFamily: '"Roboto Mono", monospace', fontWeight: 600, letterSpacing: ".08em" }}>NOOK OPERATIONS</Typography><Typography variant="h4">오늘의 운영 업무</Typography><Typography color="text.secondary" sx={{ mt: .5 }}>확인하거나 관리할 영역을 선택하세요.</Typography></Box><Stack direction={{ xs: "column", md: "row" }} spacing={2}>{destinations.map((destination) => <Card variant="outlined" sx={{ flex: 1 }} key={destination.href}><CardActionArea href={destination.href} sx={{ height: "100%", p: .5 }}><CardContent><Box className="summary-icon">{destination.icon}</Box><Typography variant="overline" color="text.secondary" sx={{ display: "block", mt: 2 }}>{destination.eyebrow}</Typography><Typography variant="h6">{destination.title}</Typography><Typography color="text.secondary" sx={{ mt: 1, minHeight: 42 }}>{destination.description}</Typography><Stack direction="row" spacing={.5} sx={{ alignItems: "center", mt: 2, color: "primary.main" }}><Typography variant="subtitle2">관리 화면 열기</Typography><ArrowForwardIcon fontSize="small" /></Stack></CardContent></CardActionArea></Card>)}</Stack><Alert severity="info" variant="outlined">공용 데이터 변경은 사용자 화면에 영향을 줄 수 있습니다. 상세 화면의 영향 범위를 확인하고 사유를 남겨주세요.</Alert></Stack>;
}

function StatusChip({ value }: { value?: string }) {
  const normalized = value ?? "미시작";
  const tone = normalized.includes("COMPLETED") || normalized === "완료" ? { color: "#16795A", background: "#EAF8F3" } : normalized.includes("FAILED") ? { color: "#C33D30", background: "#FFF0EE" } : { color: "#67707D", background: "#F4F5F7" };
  return <Chip size="small" label={normalized} sx={tone} />;
}

function ProviderBadge({ provider }: { provider: string }) {
  if (provider === "KAKAO") return <Chip size="small" icon={<Box component="svg" viewBox="0 0 24 24" sx={{ width: 18, height: 18 }}><path fill="#3C1E1E" d="M12 3C6.48 3 2 6.58 2 11c0 2.84 1.85 5.34 4.64 6.76L5.7 21l3.78-2.2c.81.13 1.65.2 2.52.2 5.52 0 10-3.58 10-8S17.52 3 12 3Z" /></Box>} label="카카오" sx={{ color: "#3C1E1E", background: "#FEE500", border: 0 }} />;
  if (provider === "NAVER") return <Chip size="small" icon={<Box component="svg" viewBox="0 0 24 24" sx={{ width: 17, height: 17 }}><path fill="#03C75A" d="M4 4h5.2l5.6 8V4H20v16h-5.2l-5.6-8v8H4z" /></Box>} label="네이버" sx={{ color: "#075D2D", background: "#E9FAF0", border: 0 }} />;
  return <Chip size="small" variant="outlined" label={provider} className="mono-text" />;
}

function Thumbnail({ src, alt }: { src?: string; alt: string }) {
  return src ? <Box component="img" src={src} alt={alt} loading="lazy" className="table-thumbnail" /> : <Box className="table-thumbnail empty-thumbnail">사진 없음</Box>;
}

function MediaGallery({ media }: { media: PostMedia[] }) {
  if (!media.length) return <Box className="empty-gallery">등록된 사진이나 영상이 없습니다.</Box>;
  return <Box className="media-grid">{media.map((item) => <Box className="media-item" key={`${item.sequence}-${item.mediaUrl}`}>{item.mediaType === "VIDEO" ? <Box component="video" src={item.mediaUrl} controls preload="metadata" /> : <Box component="img" src={item.mediaUrl} alt={`게시글 이미지 ${item.sequence + 1}`} loading="lazy" />}<span>{item.sequence + 1}</span></Box>)}</Box>;
}

function PhotoGallery({ urls, name }: { urls: string[]; name: string }) {
  if (!urls.length) return <Box className="empty-gallery">등록된 장소 사진이 없습니다.</Box>;
  return <Box className="media-grid">{urls.map((url, index) => <Box className="media-item" key={url}><Box component="img" src={url} alt={`${name} 사진 ${index + 1}`} loading="lazy" /><span>{index + 1}</span></Box>)}</Box>;
}

function PostList() { return <List filters={[<TextInput key="query" source="query" label="제목·작성자·URL 검색" alwaysOn />]}><Datagrid rowClick="show" bulkActionButtons={false}><RaTextField source="id" label="ID" sx={{ fontFamily: '"Roboto Mono", monospace', color: "text.secondary" }} /><RaTextField source="title" label="제목" sx={{ fontWeight: 700 }} /><RaTextField source="authorIdentifier" label="작성자" sx={{ fontFamily: '"Roboto Mono", monospace' }} /><FunctionField label="게시글 파싱" render={(record: { contentParsingStatus?: string }) => <StatusChip value={record.contentParsingStatus} />} /><FunctionField label="장소 파싱" render={(record: { placeParsingStatus?: string }) => <StatusChip value={record.placeParsingStatus} />} /><RaTextField source="placeCount" label="장소 수" /><RaTextField source="savedUserCount" label="저장 사용자" /><FunctionField label="검수" render={(record: { mappingReviewed: boolean }) => <StatusChip value={record.mappingReviewed ? "완료" : "미검수"} />} /><DateField source="createdAt" label="생성일" showTime /></Datagrid></List>; }
function PostShow() { return <Show actions={<PostActions />}><SimpleShowLayout><PostPanel /></SimpleShowLayout></Show>; }
function PostActions() { const record = useRecordContext<PostDetail>(); return <TopToolbar>{record && <Button href={record.canonicalUrl} target="_blank" endIcon={<OpenInNewIcon />}>원문 열기</Button>}</TopToolbar>; }

function PostPanel() {
  const record = useRecordContext<PostDetail>(); const [open, setOpen] = useState(false); const [editing, setEditing] = useState(false); if (!record?.places) return null;
  const failure = record.contentParsingFailureReason ?? record.placeParsingFailureReason;
  return <Stack spacing={3}><Stack direction="row" spacing={1} useFlexGap sx={{ flexWrap: "wrap" }}><StatusChip value={`게시글 ${record.contentParsingStatus}`} /><StatusChip value={`장소 ${record.placeParsingStatus ?? "미시작"}`} /><StatusChip value={record.mappingReviewed ? "완료" : "미검수"} />{record.manuallyOverridden && <Chip size="small" color="primary" label="운영자 수정 보호" />}</Stack>{failure && <Alert severity="error">{failure}</Alert>}<Card variant="outlined"><CardContent><Stack direction="row" sx={{ justifyContent: "space-between", alignItems: "flex-start" }}><Box><Typography variant="h6">{record.title ?? "제목 없음"}</Typography><Typography color="text.secondary" className="mono-text">{record.authorIdentifier ?? "작성자 미상"} · 저장 사용자 {record.savedUserCount}명</Typography></Box><Button variant="outlined" onClick={() => setEditing(true)}>게시글 정보 수정</Button></Stack><Typography sx={{ whiteSpace: "pre-wrap", mt: 2, color: "#353C46" }}>{record.body}</Typography>{record.sourceLocationTag && <Typography color="text.secondary" sx={{ mt: 1 }}>위치 태그: {record.sourceLocationTag}</Typography>}<Stack direction="row" spacing={.5} useFlexGap sx={{ flexWrap: "wrap", mt: 2 }}>{record.hashtags?.map((tag) => <Chip key={tag} size="small" label={`#${tag}`} />)}</Stack></CardContent></Card><Box><Typography variant="h6" sx={{ mb: 1.5 }}>게시글 미디어</Typography><MediaGallery media={record.media ?? []} /></Box><Box><Stack direction={{ xs: "column", sm: "row" }} spacing={1} sx={{ justifyContent: "space-between", alignItems: { sm: "center" } }}><Box><Typography variant="h6">공용 장소 매핑</Typography><Typography color="text.secondary" variant="body2">장소를 선택하면 장소 관리 상세로 이동합니다.</Typography></Box><Button variant="contained" onClick={() => setOpen(true)}>매핑 교정</Button></Stack><Stack spacing={1.5} sx={{ mt: 2 }}>{record.places.map((place) => <Card variant="outlined" key={place.id}><CardActionArea href={`#/places/${place.id}/show`}><CardContent><Stack direction="row" spacing={1.5} sx={{ alignItems: "center" }}><Thumbnail src={place.thumbnailUrl} alt={place.name} /><Box sx={{ flex: 1 }}><Typography variant="subtitle1">{place.name}</Typography><Typography color="text.secondary">{place.address}</Typography><Stack direction="row" spacing={1} sx={{ mt: .5, alignItems: "center" }}><ProviderBadge provider={place.provider} /><Typography variant="caption" className="mono-text" color="text.disabled">{place.externalPlaceId}</Typography></Stack></Box><ArrowForwardIcon color="disabled" /></Stack></CardContent></CardActionArea></Card>)}</Stack></Box><MappingDialog open={open} post={record} onClose={() => setOpen(false)} /><PostEditDialog open={editing} post={record} onClose={() => setEditing(false)} /></Stack>;
}

function PostEditDialog({ open, post, onClose }: { open: boolean; post: PostDetail; onClose: () => void }) {
  const [title, setTitle] = useState(post.title ?? ""); const [author, setAuthor] = useState(post.authorIdentifier ?? ""); const [body, setBody] = useState(post.body ?? ""); const [location, setLocation] = useState(post.sourceLocationTag ?? ""); const [hashtags, setHashtags] = useState(post.hashtags?.join(", ") ?? ""); const [media, setMedia] = useState(post.media?.map((item) => `${item.mediaType}|${item.mediaUrl}`).join("\n") ?? ""); const [reason, setReason] = useState(""); const [saving, setSaving] = useState(false); const notify = useNotify(); const refresh = useRefresh();
  const save = async () => { setSaving(true); try { const parsedMedia = media.split("\n").map((line) => line.trim()).filter(Boolean).map((line) => { const [mediaType, ...url] = line.split("|"); return { mediaType, mediaUrl: url.join("|") }; }); await api(`/posts/${post.id}`, { method: "PUT", body: JSON.stringify({ title, authorIdentifier: author, body, sourceLocationTag: location, hashtags: hashtags.split(",").map((v) => v.trim().replace(/^#/, "")).filter(Boolean), media: parsedMedia, reason }) }); notify("게시글 공용 정보를 수정했습니다.", { type: "success" }); refresh(); onClose(); } catch (error) { notify(error instanceof Error ? error.message : "수정에 실패했습니다.", { type: "error" }); } finally { setSaving(false); } };
  return <Dialog open={open} onClose={onClose} fullWidth maxWidth="md"><DialogTitle>게시글 공용 정보 수정</DialogTitle><DialogContent><Stack spacing={2} sx={{ mt: 1 }}><Alert severity="warning">저장하면 자동 재파싱으로 덮어쓰지 않는 운영자 수정값으로 보호되며 감사 로그가 남습니다.</Alert><TextField label="제목" value={title} onChange={(e) => setTitle(e.target.value)} /><TextField label="작성자" value={author} onChange={(e) => setAuthor(e.target.value)} /><TextField label="본문" multiline minRows={6} value={body} onChange={(e) => setBody(e.target.value)} /><TextField label="위치 태그" value={location} onChange={(e) => setLocation(e.target.value)} /><TextField label="해시태그" value={hashtags} onChange={(e) => setHashtags(e.target.value)} helperText="쉼표로 구분하세요." /><TextField label="미디어" multiline minRows={4} value={media} onChange={(e) => setMedia(e.target.value)} helperText="한 줄에 하나씩 IMAGE|URL 또는 VIDEO|URL 형식으로 입력하세요." /><TextField label="수정 사유" required multiline minRows={2} value={reason} onChange={(e) => setReason(e.target.value)} /></Stack></DialogContent><DialogActions><Button onClick={onClose}>취소</Button><Button variant="contained" disabled={saving || !reason.trim()} onClick={save}>{saving ? <CircularProgress size={20} /> : "수정 저장"}</Button></DialogActions></Dialog>;
}

function MappingDialog({ open, post, onClose }: { open: boolean; post: PostDetail; onClose: () => void }) {
  const [selected, setSelected] = useState<Place[]>(post.places); const [options, setOptions] = useState<Place[]>(post.places); const [query, setQuery] = useState(""); const [reason, setReason] = useState(""); const [saving, setSaving] = useState(false); const notify = useNotify(); const refresh = useRefresh();
  useEffect(() => { if (query.trim().length < 2) return; const timer = window.setTimeout(() => api<Place[]>(`/places?query=${encodeURIComponent(query)}`).then((items) => setOptions([...selected, ...items].filter((item, index, all) => all.findIndex((candidate) => candidate.id === item.id) === index))), 250); return () => window.clearTimeout(timer); }, [query, selected]);
  const save = async () => { setSaving(true); try { await api(`/posts/${post.id}/places`, { method: "PUT", body: JSON.stringify({ placeIds: selected.map((place) => place.id), reason }) }); notify("매핑을 교정하고 감사 로그를 남겼습니다.", { type: "success" }); refresh(); onClose(); } catch (error) { notify(error instanceof Error ? error.message : "교정에 실패했습니다.", { type: "error" }); } finally { setSaving(false); } };
  return <Dialog open={open} onClose={onClose} fullWidth maxWidth="md"><DialogTitle>공용 장소 매핑 교정</DialogTitle><DialogContent><Stack spacing={2} sx={{ mt: 1 }}><Alert severity="info">기존 사용자 {post.savedUserCount}명의 저장 장소는 유지되고 이후 저장 건에만 적용됩니다. 장소를 모두 제거하는 교정도 가능합니다.</Alert><Autocomplete multiple options={options} value={selected} onChange={(_, value) => setSelected(value)} onInputChange={(_, value) => setQuery(value)} getOptionLabel={(place) => `${place.name} · ${place.address}`} isOptionEqualToValue={(a, b) => a.id === b.id} renderInput={(params) => <TextField {...params} label="장소 검색" helperText="두 글자 이상 입력하세요." />} /><TextField label="수정 사유" required multiline minRows={3} value={reason} onChange={(event) => setReason(event.target.value)} /></Stack></DialogContent><DialogActions><Button onClick={onClose}>취소</Button><Button variant="contained" disabled={saving || !reason.trim()} onClick={save}>{saving ? <CircularProgress size={20} /> : "교정 저장"}</Button></DialogActions></Dialog>;
}

function PlaceList() { return <List filters={[<TextInput key="query" source="query" label="장소명·주소·외부 ID 검색" alwaysOn />]}><Datagrid rowClick="show" bulkActionButtons={false}><FunctionField label="사진" sortable={false} render={(record: Place) => <Thumbnail src={record.thumbnailUrl} alt={record.name} />} /><RaTextField source="id" label="ID" sx={{ fontFamily: '"Roboto Mono", monospace', color: "text.secondary" }} /><RaTextField source="name" label="장소명" sx={{ fontWeight: 700, minWidth: 150 }} /><RaTextField source="address" label="주소" sx={{ minWidth: 260, maxWidth: 420, overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }} /><FunctionField label="출처" render={(record: Place) => <ProviderBadge provider={record.provider} />} /><FunctionField label="태그" sortable={false} render={(record: Place) => <Stack direction="row" spacing={.5}>{record.representativeTags?.slice(0, 2).map((tag) => <Chip key={tag} size="small" label={tag} variant="outlined" />)}</Stack>} /><RaTextField source="linkedPostCount" label="연결 게시글" /><RaTextField source="affectedUserCount" label="영향 사용자" /></Datagrid></List>; }
function PlaceShow() { return <Show><SimpleShowLayout><PlacePanel /></SimpleShowLayout></Show>; }
function PlacePanel() {
  const record = useRecordContext<ManagedPlace>(); const [open, setOpen] = useState(false); if (!record?.posts) return null;
  const photos = [record.thumbnailUrl, ...(record.photoUrls ?? [])].filter((url, index, all): url is string => !!url && all.indexOf(url) === index);
  return <Stack spacing={3}><Alert severity="warning" variant="outlined">이 장소는 게시글 {record.linkedPostCount}개와 저장 사용자 {record.affectedUserCount}명에게 노출됩니다. 공용 정보 수정은 사용자 화면에도 즉시 반영됩니다.</Alert><Card variant="outlined"><CardContent><Stack direction={{ xs: "column", sm: "row" }} spacing={2} sx={{ justifyContent: "space-between", alignItems: { sm: "center" } }}><Stack direction="row" spacing={2}><Thumbnail src={record.thumbnailUrl} alt={record.name} /><Box><Stack direction="row" spacing={1} sx={{ alignItems: "center" }}><Typography variant="h5">{record.name}</Typography><ProviderBadge provider={record.provider} /></Stack><Typography color="text.secondary" sx={{ mt: .75 }}>{record.address}</Typography><Typography color="text.secondary">{[record.category, record.phoneNumber].filter(Boolean).join(" · ")}</Typography><Typography variant="caption" className="mono-text" color="text.disabled">{record.externalPlaceId} · {record.latitude}, {record.longitude}</Typography><Stack direction="row" spacing={.5} sx={{ mt: 1 }}>{record.representativeTags?.map((tag) => <Chip key={tag} size="small" label={tag} />)}</Stack></Box></Stack><Button variant="contained" onClick={() => setOpen(true)}>장소 정보 수정</Button></Stack></CardContent></Card><Box><Typography variant="h6" sx={{ mb: 1.5 }}>장소 사진</Typography><PhotoGallery urls={photos} name={record.name} /></Box><Box><Typography variant="h6">연결 게시글</Typography><Typography variant="body2" color="text.secondary" sx={{ mb: 1.5 }}>게시글을 선택하면 게시글 관리 상세로 이동합니다.</Typography><Stack spacing={1.5}>{record.posts.map((post) => <Card variant="outlined" key={post.id}><CardActionArea href={`#/posts/${post.id}/show`}><CardContent><Stack direction="row" spacing={1} sx={{ alignItems: "center" }}><Box sx={{ flex: 1 }}><Typography variant="subtitle1">{post.title ?? "제목 없음"}</Typography><Typography color="text.secondary" className="mono-text">{post.authorIdentifier ?? "작성자 미상"}</Typography></Box><ArrowForwardIcon color="disabled" /></Stack></CardContent></CardActionArea><CardActions sx={{ px: 2, pb: 2, pt: 0 }}><Button size="small" href={post.canonicalUrl} target="_blank" endIcon={<OpenInNewIcon />}>원문 열기</Button></CardActions></Card>)}</Stack></Box><PlaceEditDialog open={open} place={record} onClose={() => setOpen(false)} /></Stack>;
}
function PlaceEditDialog({ open, place, onClose }: { open: boolean; place: ManagedPlace; onClose: () => void }) {
  const [name, setName] = useState(place.name); const [address, setAddress] = useState(place.address); const [city, setCity] = useState(place.city ?? ""); const [category, setCategory] = useState(place.category ?? ""); const [phone, setPhone] = useState(place.phoneNumber ?? ""); const [thumbnail, setThumbnail] = useState(place.thumbnailUrl ?? ""); const [photos, setPhotos] = useState(place.photoUrls?.join("\n") ?? ""); const [tags, setTags] = useState(place.representativeTags ?? []); const [tagOptions, setTagOptions] = useState<PlaceTagDefinition[]>([]); const [hours, setHours] = useState(place.openingHours ? JSON.stringify(place.openingHours, null, 2) : ""); const [reason, setReason] = useState(""); const [saving, setSaving] = useState(false); const notify = useNotify(); const refresh = useRefresh();
  useEffect(() => { if (!open) return; api<Page<PlaceTagDefinition>>("/place-tags?enabled=true&offset=0&limit=100").then((page) => { setTagOptions(page.items); const activeCodes = new Set(page.items.map((tag) => tag.tagCode)); setTags((current) => current.filter((tag) => activeCodes.has(tag))); }).catch((error) => notify(error instanceof Error ? error.message : "태그 목록을 불러오지 못했습니다.", { type: "error" })); }, [open, notify]);
  const save = async () => { setSaving(true); try { await api(`/places/${place.id}`, { method: "PUT", body: JSON.stringify({ name, address, city, category, phoneNumber: phone, thumbnailUrl: thumbnail, photoUrls: photos.split("\n").map((v) => v.trim()).filter(Boolean), representativeTags: tags, openingHours: hours.trim() ? JSON.parse(hours) : null, reason }) }); notify("장소 공용 정보를 수정하고 감사 로그를 남겼습니다.", { type: "success" }); refresh(); onClose(); } catch (error) { notify(error instanceof Error ? error.message : "수정에 실패했습니다.", { type: "error" }); } finally { setSaving(false); } };
  const displayByCode = new Map(tagOptions.map((tag) => [tag.tagCode, tag.displayName]));
  return <Dialog open={open} onClose={onClose} fullWidth maxWidth="md"><DialogTitle>공용 장소 정보 수정</DialogTitle><DialogContent><Stack spacing={2} sx={{ mt: 1 }}><Alert severity="warning">게시글 {place.linkedPostCount}개와 저장 사용자 {place.affectedUserCount}명의 화면에 변경된 정보가 표시됩니다.</Alert><Stack direction={{ xs: "column", sm: "row" }} spacing={2}><TextField fullWidth label="장소명" required value={name} onChange={(e) => setName(e.target.value)} /><TextField fullWidth label="도시" value={city} onChange={(e) => setCity(e.target.value)} /></Stack><TextField label="주소" required value={address} onChange={(e) => setAddress(e.target.value)} /><Stack direction={{ xs: "column", sm: "row" }} spacing={2}><TextField fullWidth label="카테고리" value={category} onChange={(e) => setCategory(e.target.value)} /><TextField fullWidth label="전화번호" value={phone} onChange={(e) => setPhone(e.target.value)} /></Stack><TextField label="대표 이미지 URL" value={thumbnail} onChange={(e) => setThumbnail(e.target.value)} /><TextField label="추가 사진 URL" multiline minRows={3} value={photos} onChange={(e) => setPhotos(e.target.value)} helperText="한 줄에 하나씩, 최대 6개" /><Autocomplete multiple options={tagOptions.map((tag) => tag.tagCode)} value={tags} getOptionLabel={(tag) => displayByCode.get(tag) ?? tag} onChange={(_, value) => setTags(value.slice(0, 4))} renderInput={(params) => <TextField {...params} label="대표 태그" helperText="활성 태그 중 최대 4개" />} /><TextField label="영업시간 JSON" multiline minRows={4} value={hours} onChange={(e) => setHours(e.target.value)} /><TextField label="수정 사유" required multiline minRows={3} value={reason} onChange={(e) => setReason(e.target.value)} /></Stack></DialogContent><DialogActions><Button onClick={onClose}>취소</Button><Button variant="contained" disabled={saving || !name.trim() || !address.trim() || !reason.trim()} onClick={save}>{saving ? <CircularProgress size={20} /> : "수정 저장"}</Button></DialogActions></Dialog>;
}

const placeTagCategoryChoices = [
  { id: "ATMOSPHERE", name: "분위기" },
  { id: "SPACE", name: "공간" },
  { id: "PURPOSE", name: "목적" },
  { id: "EXPERIENCE", name: "경험" },
  { id: "FOOD_AND_BEVERAGE", name: "F&B" },
];

function categoryLabel(category: string) { return placeTagCategoryChoices.find((choice) => choice.id === category)?.name ?? category; }

function PlaceTagList() {
  return <List perPage={100} filters={[<SelectInput key="category" source="category" label="분류" choices={placeTagCategoryChoices} />, <SelectInput key="enabled" source="enabled" label="상태" choices={[{ id: true, name: "사용" }, { id: false, name: "중지" }]} />]}><Datagrid bulkActionButtons={false} rowClick={false}><RaTextField source="sortOrder" label="순서" /><RaTextField source="tagCode" label="태그 코드" sx={{ fontFamily: '"Roboto Mono", monospace' }} /><FunctionField label="분류" render={(record: PlaceTagDefinition) => categoryLabel(record.category)} /><RaTextField source="displayName" label="노출 태그" sx={{ fontWeight: 700 }} /><FunctionField label="매칭 키워드" sortable={false} render={(record: PlaceTagDefinition) => <Stack direction="row" spacing={.5} useFlexGap sx={{ flexWrap: "wrap", maxWidth: 520 }}>{record.matchingKeywords.slice(0, 5).map((keyword) => <Chip key={keyword} size="small" label={keyword} variant="outlined" />)}{record.matchingKeywords.length > 5 && <Chip size="small" label={`+${record.matchingKeywords.length - 5}`} />}</Stack>} /><FunctionField label="상태" render={(record: PlaceTagDefinition) => <StatusChip value={record.enabled ? "사용" : "중지"} />} /><DateField source="updatedAt" label="수정일" showTime /><FunctionField label="관리" sortable={false} render={(record: PlaceTagDefinition) => <PlaceTagEditButton tag={record} />} /></Datagrid></List>;
}

function PlaceTagEditButton({ tag }: { tag: PlaceTagDefinition }) {
  const [open, setOpen] = useState(false);
  return <><Button size="small" variant="outlined" onClick={(event) => { event.stopPropagation(); setOpen(true); }}>수정</Button><PlaceTagEditDialog open={open} tag={tag} onClose={() => setOpen(false)} /></>;
}

function PlaceTagEditDialog({ open, tag, onClose }: { open: boolean; tag: PlaceTagDefinition; onClose: () => void }) {
  const [category, setCategory] = useState(tag.category); const [displayName, setDisplayName] = useState(tag.displayName); const [keywords, setKeywords] = useState(tag.matchingKeywords.join("\n")); const [enabled, setEnabled] = useState(tag.enabled); const [sortOrder, setSortOrder] = useState(tag.sortOrder); const [reason, setReason] = useState(""); const [saving, setSaving] = useState(false); const notify = useNotify(); const refresh = useRefresh();
  useEffect(() => { if (!open) return; setCategory(tag.category); setDisplayName(tag.displayName); setKeywords(tag.matchingKeywords.join("\n")); setEnabled(tag.enabled); setSortOrder(tag.sortOrder); setReason(""); }, [open, tag]);
  const save = async () => { setSaving(true); try { await api(`/place-tags/${tag.tagCode}`, { method: "PUT", body: JSON.stringify({ category, displayName, matchingKeywords: keywords.split(/\n|,/).map((value) => value.trim()).filter(Boolean), enabled, sortOrder, reason }) }); notify("장소 태그 카탈로그를 수정했습니다.", { type: "success" }); refresh(); onClose(); } catch (error) { notify(error instanceof Error ? error.message : "태그 수정에 실패했습니다.", { type: "error" }); } finally { setSaving(false); } };
  return <Dialog open={open} onClose={onClose} fullWidth maxWidth="sm"><DialogTitle>장소 태그 수정</DialogTitle><DialogContent><Stack spacing={2} sx={{ mt: 1 }}><Alert severity="info">코드는 기존 데이터와 연결되어 있어 변경할 수 없습니다. 이름과 사용 여부는 사용자 응답에 즉시 반영됩니다.</Alert><TextField label="태그 코드" value={tag.tagCode} disabled /><TextField select label="분류" value={category} onChange={(event) => setCategory(event.target.value)}>{placeTagCategoryChoices.map((choice) => <MenuItem key={choice.id} value={choice.id}>{choice.name}</MenuItem>)}</TextField><TextField label="노출 태그명" required value={displayName} onChange={(event) => setDisplayName(event.target.value)} /><TextField label="본문 매칭 키워드" required multiline minRows={6} value={keywords} onChange={(event) => setKeywords(event.target.value)} helperText="줄바꿈 또는 쉼표로 구분하며 최대 20개입니다." /><TextField label="정렬 순서" type="number" required value={sortOrder} onChange={(event) => setSortOrder(Number(event.target.value))} /><FormControlLabel control={<Switch checked={enabled} onChange={(event) => setEnabled(event.target.checked)} />} label={enabled ? "사용 중" : "사용 중지"} /><TextField label="수정 사유" required multiline minRows={2} value={reason} onChange={(event) => setReason(event.target.value)} /></Stack></DialogContent><DialogActions><Button onClick={onClose}>취소</Button><Button variant="contained" disabled={saving || !displayName.trim() || !keywords.trim() || sortOrder < 1 || !reason.trim()} onClick={save}>{saving ? <CircularProgress size={20} /> : "수정 저장"}</Button></DialogActions></Dialog>;
}

function AuditLogList() { return <List filters={[<TextInput key="targetType" source="targetType" label="대상 유형" />, <TextInput key="targetId" source="targetId" label="대상 ID" />]}><Datagrid bulkActionButtons={false} expand={<AuditDetails />}><DateField source="createdAt" label="시각" showTime /><RaTextField source="actorEmail" label="운영자" sx={{ fontFamily: '"Roboto Mono", monospace' }} /><FunctionField label="동작" render={(record: { action?: string }) => <Chip size="small" label={record.action ?? "-"} sx={{ color: "#356FE5", background: "#EDF3FF" }} />} /><RaTextField source="targetType" label="대상" /><RaTextField source="targetId" label="대상 ID" sx={{ fontFamily: '"Roboto Mono", monospace' }} /><RaTextField source="reason" label="사유" /><RaTextField source="requestId" label="Request ID" sx={{ fontFamily: '"Roboto Mono", monospace', color: "text.secondary" }} /></Datagrid></List>; }
function AuditDetails() { const record = useRecordContext<{ beforeValue?: string; afterValue?: string }>(); if (!record) return null; return <Stack direction={{ xs: "column", md: "row" }} spacing={2} sx={{ p: 2 }}><Box sx={{ flex: 1 }}><Typography variant="subtitle2">변경 전</Typography><Box component="pre" sx={{ whiteSpace: "pre-wrap", overflowWrap: "anywhere" }}>{formatJson(record.beforeValue)}</Box></Box><Box sx={{ flex: 1 }}><Typography variant="subtitle2">변경 후</Typography><Box component="pre" sx={{ whiteSpace: "pre-wrap", overflowWrap: "anywhere" }}>{formatJson(record.afterValue)}</Box></Box></Stack>; }
function formatJson(value?: string) { if (!value) return "-"; try { return JSON.stringify(JSON.parse(value), null, 2); } catch { return value; } }

createRoot(document.getElementById("root")!).render(<React.StrictMode><App /></React.StrictMode>);
