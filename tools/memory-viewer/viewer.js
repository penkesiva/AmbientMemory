import * as THREE from "https://esm.sh/three@0.164.1";
import { OrbitControls } from "https://esm.sh/three@0.164.1/examples/jsm/controls/OrbitControls.js";

const baseSqlUrl = "https://cdnjs.cloudflare.com/ajax/libs/sql.js/1.10.2";

const els = {
  activityFilter: document.getElementById("activityFilter"),
  whereFilter: document.getElementById("whereFilter"),
  timeSlider: document.getElementById("timeSlider"),
  sessionSlider: document.getElementById("sessionSlider"),
  sessionLabel: document.getElementById("sessionLabel"),
  sessionChips: document.getElementById("sessionChips"),
  detailText: document.getElementById("detailText"),
  detailImg: document.getElementById("detailImg"),
  sceneConf: document.getElementById("sceneConf"),
  activityConf: document.getElementById("activityConf"),
  reloadBtn: document.getElementById("reloadBtn"),
  status: document.getElementById("status"),
  modalThumbBtn: document.getElementById("modalThumbBtn"),
  modalThumbImg: document.getElementById("modalThumbImg"),
};

const THEME_KEY = "ambientMemoryViewerTheme";
const imageLightbox = document.getElementById("imageLightbox");
const lightboxImg = document.getElementById("lightboxImg");

function applyTheme(theme) {
  const t = theme === "light" ? "light" : "dark";
  document.documentElement.setAttribute("data-theme", t);
  try {
    localStorage.setItem(THEME_KEY, t);
  } catch {
    /* ignore quota / private mode */
  }
  const btn = document.getElementById("themeToggleBtn");
  if (btn) {
    btn.textContent = t === "light" ? "Dark mode" : "Light mode";
    btn.setAttribute(
      "aria-label",
      t === "light" ? "Switch to dark theme" : "Switch to light theme",
    );
  }
}

function initTheme() {
  try {
    const stored = localStorage.getItem(THEME_KEY);
    if (stored === "light" || stored === "dark") {
      applyTheme(stored);
      return;
    }
  } catch {
    /* ignore */
  }
  const prefersLight =
    typeof window.matchMedia === "function" &&
    window.matchMedia("(prefers-color-scheme: light)").matches;
  applyTheme(prefersLight ? "light" : "dark");
}

function openImageLightbox(src, alt) {
  if (!imageLightbox || !lightboxImg || !src) return;
  lightboxImg.src = src;
  lightboxImg.alt = alt || "Enlarged capture";
  imageLightbox.hidden = false;
  imageLightbox.setAttribute("aria-hidden", "false");
}

function closeImageLightbox() {
  if (!imageLightbox || !lightboxImg) return;
  imageLightbox.hidden = true;
  imageLightbox.setAttribute("aria-hidden", "true");
  lightboxImg.src = "";
  lightboxImg.alt = "";
}

initTheme();

function setStatus(msg, kind = "muted") {
  if (!els.status) return;
  els.status.textContent = msg;
  els.status.style.color = kind === "danger" ? "var(--danger)" : "var(--muted)";
}

function fmtTime(ms) {
  try {
    const d = new Date(ms);
    return d.toLocaleString();
  } catch {
    return String(ms);
  }
}

/** Android stores absolute file paths; viewer pulls files into data/captures/<filename>. */
function captureFilenameFromRow(e) {
  const uri = e?.image_uri ?? e?.imageUri ?? e?.IMAGE_URI;
  if (uri == null || uri === "") return "";
  return String(uri)
    .trim()
    .replace(/^file:\/\//i, "")
    .split(/[/\\]/)
    .filter(Boolean)
    .pop() || "";
}

function colorPalette() {
  return {
    working: 0x67e8f9,
    eating: 0xf59e0b,
    meeting: 0xa78bfa,
    walking: 0x22c55e,
    commuting: 0x60a5fa,
    shopping: 0xfb923c,
    resting: 0x94a3b8,
    relaxing: 0x38bdf8,
    sitting: 0x34d399,
    exercising: 0xf472b6,
    household: 0x7dd3fc,
    socializing: 0xfda4af,
    unknown: 0x93c5fd,
  };
}

async function loadSqlJs() {
  // sql-wasm.js is loaded via script tag; initSqlJs is global.
  // eslint-disable-next-line no-undef
  const init = globalThis.initSqlJs;
  if (typeof init !== "function") throw new Error("initSqlJs is not available yet.");
  const SQL = await init({ locateFile: (file) => `${baseSqlUrl}/${file}` });
  return SQL;
}

async function loadDbArrayBuffer() {
  const res = await fetch("./data/ambient_memory.db", { cache: "no-store" });
  if (!res.ok) throw new Error(`Failed to fetch DB (${res.status})`);
  return await res.arrayBuffer();
}

function queryEvents(db, limit = 2000) {
  const q = `
    SELECT
      ie.id,
      ie.start_time_millis,
      ie.activity,
      ie.where_label,
      ie.what_summary,
      ie.how_summary,
      ie.why_summary,
      ie.confidence,
      ie.scene_confidence,
      ie.inference_source,
      ie.timeline_session_id,
      re.image_uri
    FROM inferred_events ie
    JOIN raw_capture_events re ON re.id = ie.raw_capture_id
    ORDER BY ie.start_time_millis ASC
    LIMIT ${limit};
  `;
  const res = db.exec(q);
  if (!res || res.length === 0) return [];
  const { columns, values } = res[0];
  return values.map((row) => {
    const obj = {};
    columns.forEach((c, i) => {
      obj[c] = row[i];
      const lower = String(c).toLowerCase();
      if (lower !== c) obj[lower] = row[i];
    });
    return obj;
  });
}

function querySessions(db, limit = 200) {
  const q = `
    SELECT
      id,
      title,
      summary,
      status,
      start_time_millis,
      event_count
    FROM timeline_sessions
    ORDER BY start_time_millis DESC
    LIMIT ${limit};
  `;
  const res = db.exec(q);
  if (!res || res.length === 0) return [];
  const { columns, values } = res[0];
  return values.map((row) => {
    const obj = {};
    columns.forEach((c, i) => (obj[c] = row[i]));
    return obj;
  });
}

function buildEventColors(events, activityColors) {
  const whereLabels = Array.from(new Set(events.map((e) => e.where_label || "unknown")));
  const whereToIndex = new Map(whereLabels.map((w, i) => [w, i]));
  const maxRadius = 10;
  const baseRadius = 2.5;

  const minTime = Math.min(...events.map((e) => e.start_time_millis));
  const maxTime = Math.max(...events.map((e) => e.start_time_millis));
  const range = Math.max(1, maxTime - minTime);

  const positions = new Float32Array(events.length * 3);
  const colors = new Float32Array(events.length * 3);
  const baseColors = new Float32Array(events.length * 3);

  const angleStep = Math.PI * 0.12;
  for (let i = 0; i < events.length; i++) {
    const e = events[i];
    const tNorm = (e.start_time_millis - minTime) / range; // 0..1
    const angle = i * angleStep;
    const whereIdx = whereToIndex.get(e.where_label || "unknown") || 0;
    const radius = baseRadius + (whereIdx % 8) * (maxRadius - baseRadius) / 7;
    const x = radius * Math.cos(angle);
    const y = radius * Math.sin(angle);
    const z = (tNorm - 0.5) * 20;

    positions[i * 3 + 0] = x;
    positions[i * 3 + 1] = y;
    positions[i * 3 + 2] = z;

    const activity = (e.activity || "unknown").toLowerCase();
    const base = activityColors[activity] ?? activityColors.unknown;
    const sceneConf = Number(e.scene_confidence ?? 0.5);
    const a = Math.max(0.25, Math.min(1.0, sceneConf));

    const col = new THREE.Color(base);
    col.multiplyScalar(0.35 + a * 0.65);

    colors[i * 3 + 0] = col.r;
    colors[i * 3 + 1] = col.g;
    colors[i * 3 + 2] = col.b;
    baseColors[i * 3 + 0] = col.r;
    baseColors[i * 3 + 1] = col.g;
    baseColors[i * 3 + 2] = col.b;
  }

  return { positions, colors, baseColors, minTime, maxTime };
}

function makePointsScene({ positions, colors }) {
  const group = new THREE.Group();

  const geom = new THREE.BufferGeometry();
  geom.setAttribute("position", new THREE.BufferAttribute(positions, 3));
  geom.setAttribute("color", new THREE.BufferAttribute(colors, 3));

  const material = new THREE.PointsMaterial({
    size: 0.24,
    vertexColors: true,
    transparent: true,
    opacity: 0.95,
    blending: THREE.AdditiveBlending,
    depthWrite: false,
  });

  const points = new THREE.Points(geom, material);
  group.add(points);

  // Soft glow layer
  const glowGeom = geom.clone();
  const glowMat = new THREE.PointsMaterial({
    size: 0.42,
    vertexColors: true,
    transparent: true,
    opacity: 0.25,
    blending: THREE.AdditiveBlending,
    depthWrite: false,
  });
  const glow = new THREE.Points(glowGeom, glowMat);
  // Only the main Points layer should receive raycasts (glow duplicates vertices).
  glow.raycast = () => {};
  group.add(glow);

  return { group, points, geom, glowGeom };
}

const MODAL_WHAT_MAX_LEN = 720;
const MODAL_PICK_HINT_DEFAULT =
  "Click a dot to load that event here and in the Event detail panel. Each dot is one captured moment; the line connects them in time order (filtered set).";

function setModalPickHint(message) {
  const structured = document.getElementById("modalPickStructured");
  const hint = document.getElementById("modalPickHint");
  if (structured) structured.hidden = true;
  if (hint) {
    hint.hidden = false;
    hint.textContent = message;
  }
}

function fillModalPickFromEvent(e) {
  const structured = document.getElementById("modalPickStructured");
  const hint = document.getElementById("modalPickHint");
  const whatWrap = document.getElementById("modalPickWhatWrap");
  const whatText = document.getElementById("modalPickWhatText");
  if (!structured) return;
  if (hint) hint.hidden = true;
  structured.hidden = false;

  const id = e.id;
  const activity = String(e.activity ?? "unknown").replace(/^./, (c) => c.toUpperCase());
  const where = e.where_label ?? "unknown";
  const scenePct = Math.round((Number(e.scene_confidence ?? 0.5) * 100) || 0);
  const actPct = Math.round((Number(e.confidence ?? 0.5) * 100) || 0);
  const rawWhat = String(e.what_summary ?? "").trim();

  const idEl = document.getElementById("modalPickEventId");
  const timeEl = document.getElementById("modalPickDateTime");
  if (idEl) idEl.textContent = `#${id}`;
  if (timeEl) {
    timeEl.textContent = fmtTime(e.start_time_millis);
    try {
      timeEl.dateTime = new Date(Number(e.start_time_millis)).toISOString();
    } catch {
      timeEl.removeAttribute("datetime");
    }
  }
  const actEl = document.getElementById("modalPickActivity");
  const whereEl = document.getElementById("modalPickWhere");
  if (actEl) actEl.textContent = activity;
  if (whereEl) whereEl.textContent = where;
  const sceneM = document.getElementById("modalPickSceneMetric");
  const actM = document.getElementById("modalPickActMetric");
  if (sceneM) sceneM.textContent = `Scene ${scenePct}%`;
  if (actM) actM.textContent = `Activity ${actPct}%`;

  if (whatWrap && whatText) {
    if (rawWhat) {
      const truncated =
        rawWhat.length > MODAL_WHAT_MAX_LEN
          ? `${rawWhat.slice(0, MODAL_WHAT_MAX_LEN)}…`
          : rawWhat;
      whatText.textContent = truncated;
      whatWrap.hidden = false;
    } else {
      whatText.textContent = "";
      whatWrap.hidden = true;
    }
  }
}

function updateHighlight(pointsGeom, baseColors, highlightIndex, glowGeom = null) {
  const c = pointsGeom.attributes.color.array;
  for (let i = 0; i < baseColors.length / 3; i++) {
    if (i === highlightIndex) {
      c[i * 3 + 0] = 1;
      c[i * 3 + 1] = 1;
      c[i * 3 + 2] = 1;
    } else {
      c[i * 3 + 0] = baseColors[i * 3 + 0];
      c[i * 3 + 1] = baseColors[i * 3 + 1];
      c[i * 3 + 2] = baseColors[i * 3 + 2];
    }
  }
  pointsGeom.attributes.color.needsUpdate = true;

  if (glowGeom) {
    const gc = glowGeom.attributes.color.array;
    for (let i = 0; i < baseColors.length / 3; i++) {
      if (i === highlightIndex) {
        gc[i * 3 + 0] = 1;
        gc[i * 3 + 1] = 1;
        gc[i * 3 + 2] = 1;
      } else {
        gc[i * 3 + 0] = baseColors[i * 3 + 0];
        gc[i * 3 + 1] = baseColors[i * 3 + 1];
        gc[i * 3 + 2] = baseColors[i * 3 + 2];
      }
    }
    glowGeom.attributes.color.needsUpdate = true;
  }
}

function setEventDetails(events, e) {
  if (!e) return;
  const id = e.id;
  const time = fmtTime(e.start_time_millis);
  const activity = e.activity ?? "unknown";
  const where = e.where_label ?? "unknown";
  const what = (e.what_summary ?? "").slice(0, 180);
  const how = e.how_summary ?? "";
  const why = e.why_summary ?? "";
  const sessionId = e.timeline_session_id ?? null;
  const sessionTitle =
    sessionId != null ? state?.sessionById?.get(Number(sessionId))?.title : null;
  const sessionLine = sessionTitle ? `Session: ${sessionTitle}\n` : "";

  els.detailText.textContent =
    `#${id}\n` +
    `${time}\n` +
    sessionLine +
    `Activity: ${String(activity).replace(/^./, (c) => c.toUpperCase())}\n` +
    `Where: ${where}\n\n` +
    `What: ${what}\n\n` +
    `How: ${how}\n` +
    `Why: ${why || "—"}`;

  const file = captureFilenameFromRow(e);
  if (file) {
    els.detailImg.src = `./data/captures/${encodeURIComponent(file)}`;
  } else {
    els.detailImg.src = "";
    els.detailImg.alt = "No image path in database";
  }
  els.detailImg.onerror = () => {
    els.detailImg.src = "";
    els.detailImg.alt = "Image missing under data/captures/";
    if (els.modalThumbBtn && els.modalThumbImg) {
      els.modalThumbBtn.hidden = true;
      els.modalThumbImg.src = "";
    }
  };

  if (els.modalThumbBtn && els.modalThumbImg) {
    if (file) {
      const capSrc = `./data/captures/${encodeURIComponent(file)}`;
      els.modalThumbImg.src = capSrc;
      els.modalThumbImg.alt = `Preview #${id}`;
      els.modalThumbBtn.hidden = false;
      els.modalThumbBtn.disabled = false;
      els.modalThumbImg.onerror = () => {
        els.modalThumbBtn.hidden = true;
        els.modalThumbImg.src = "";
      };
    } else {
      els.modalThumbImg.src = "";
      els.modalThumbBtn.hidden = true;
      els.modalThumbBtn.disabled = true;
    }
  }

  const scenePct = Math.round((Number(e.scene_confidence ?? 0.5) * 100) || 0);
  const actPct = Math.round((Number(e.confidence ?? 0.5) * 100) || 0);
  els.sceneConf.textContent = `Scene ${scenePct}%`;
  els.activityConf.textContent = `Activity ${actPct}%`;

  fillModalPickFromEvent(e);
}

let state = null;

const viewerModal = document.getElementById("viewerModal");
const modalCanvasHost = document.getElementById("modalCanvasHost");
const mainCanvasHost = document.getElementById("canvas");
const open3dModalBtn = document.getElementById("open3dModalBtn");

function fitRendererToHost() {
  if (!state?.renderer?.domElement) return;
  const parent = state.renderer.domElement.parentElement;
  if (!parent) return;
  const w = Math.max(1, Math.floor(parent.clientWidth));
  const h = Math.max(1, Math.floor(parent.clientHeight));
  state.camera.aspect = w / h;
  state.camera.updateProjectionMatrix();
  state.renderer.setSize(w, h);
}

function close3dModal() {
  closeImageLightbox();
  if (!viewerModal || !mainCanvasHost || !state?.renderer) return;
  if (viewerModal.hidden) return;
  mainCanvasHost.appendChild(state.renderer.domElement);
  viewerModal.hidden = true;
  viewerModal.setAttribute("aria-hidden", "true");
  fitRendererToHost();
}

function open3dModal() {
  if (!viewerModal || !modalCanvasHost || !state?.renderer) return;
  modalCanvasHost.appendChild(state.renderer.domElement);
  viewerModal.hidden = false;
  viewerModal.setAttribute("aria-hidden", "false");
  if (state.highlightIndex >= 0 && state.events[state.highlightIndex]) {
    setEventDetails(state.events, state.events[state.highlightIndex]);
  } else {
    setModalPickHint(MODAL_PICK_HINT_DEFAULT);
  }
  requestAnimationFrame(() => {
    fitRendererToHost();
    requestAnimationFrame(fitRendererToHost);
  });
}

let viewerChromeBound = false;
function bindViewerChromeOnce() {
  if (viewerChromeBound) return;
  viewerChromeBound = true;
  window.addEventListener("resize", () => fitRendererToHost());
  document.getElementById("themeToggleBtn")?.addEventListener("click", () => {
    const cur = document.documentElement.getAttribute("data-theme") || "dark";
    applyTheme(cur === "light" ? "dark" : "light");
  });
  open3dModalBtn?.addEventListener("click", () => open3dModal());
  document.getElementById("close3dModal")?.addEventListener("click", () => close3dModal());
  viewerModal?.querySelector("[data-close-modal]")?.addEventListener("click", () => close3dModal());
  imageLightbox?.querySelector("[data-close-lightbox]")?.addEventListener("click", () =>
    closeImageLightbox(),
  );
  document.getElementById("lightboxClose")?.addEventListener("click", () => closeImageLightbox());
  els.modalThumbBtn?.addEventListener("click", () => {
    const src = els.modalThumbImg?.src;
    if (!src) return;
    openImageLightbox(src, els.modalThumbImg.alt || "Enlarged capture");
  });
  els.detailImg?.addEventListener("click", () => {
    const src = els.detailImg?.src;
    if (!src) return;
    openImageLightbox(src, els.detailImg.alt || "Enlarged capture");
  });
  document.addEventListener("keydown", (e) => {
    if (e.key !== "Escape") return;
    if (imageLightbox && !imageLightbox.hidden) {
      e.preventDefault();
      closeImageLightbox();
      return;
    }
    if (viewerModal && viewerModal.hidden === false) {
      e.preventDefault();
      close3dModal();
    }
  });
}

bindViewerChromeOnce();

async function loadAndRender() {
  if (!els.reloadBtn) return;
  close3dModal();
  if (open3dModalBtn) open3dModalBtn.disabled = true;
  setStatus("Loading local DB…");
  const SQL = await loadSqlJs();
  const buf = await loadDbArrayBuffer();
  const db = new SQL.Database(new Uint8Array(buf));

  setStatus("Querying events…");
  const events = queryEvents(db, 2500);
  if (!events.length) {
    setStatus("No inferred events found. Pull the DB first.", "danger");
    if (open3dModalBtn) open3dModalBtn.disabled = true;
    return;
  }

  setStatus("Querying timeline sessions…");
  const sessions = querySessions(db, 200);
  const sessionById = new Map(sessions.map((s) => [s.id, s]));

  // Filters
  const activities = Array.from(new Set(events.map((e) => e.activity || "unknown"))).sort();
  const whereLabels = Array.from(new Set(events.map((e) => e.where_label || "unknown"))).sort();
  els.activityFilter.innerHTML = `<option value="__all__">All</option>` + activities.map((a) => `<option value="${a}">${a}</option>`).join("");
  els.whereFilter.innerHTML = `<option value="__all__">All</option>` + whereLabels.map((w) => `<option value="${w}">${w}</option>`).join("");

  // 3D scene setup
  const activityColors = colorPalette();
  const built = buildEventColors(events, activityColors);
  const { group, points, geom, glowGeom } = makePointsScene({
    positions: built.positions,
    colors: built.colors,
  });

  // Scene init (reuse if possible)
  const canvasContainer = document.getElementById("canvas");
  canvasContainer.innerHTML = "";
  const width = window.innerWidth;
  const height = window.innerHeight;

  const renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true });
  renderer.setSize(width, height);
  renderer.setPixelRatio(Math.min(2, window.devicePixelRatio || 1));
  canvasContainer.appendChild(renderer.domElement);

  const scene = new THREE.Scene();
  scene.add(group);

  const grid = new THREE.GridHelper(56, 28, 0x455a6e, 0x141820);
  grid.position.set(0, -9, 0);
  scene.add(grid);

  const axes = new THREE.AxesHelper(2.8);
  scene.add(axes);

  const camera = new THREE.PerspectiveCamera(60, width / height, 0.01, 2000);
  camera.position.set(0, 2.5, 22);

  const controls = new OrbitControls(camera, renderer.domElement);
  controls.enableDamping = true;
  controls.dampingFactor = 0.08;
  controls.minDistance = 6;
  controls.maxDistance = 60;
  controls.screenSpacePanning = true;
  // Explicit bindings so the header hint matches behavior (Three r164+)
  controls.mouseButtons = {
    LEFT: THREE.MOUSE.ROTATE,
    MIDDLE: THREE.MOUSE.DOLLY,
    RIGHT: THREE.MOUSE.PAN,
  };
  controls.touches = {
    ONE: THREE.TOUCH.ROTATE,
    TWO: THREE.TOUCH.DOLLY_PAN,
  };

  const light = new THREE.DirectionalLight(0x86efac, 0.4);
  light.position.set(2, 4, 3);
  scene.add(light);

  const raycaster = new THREE.Raycaster();
  const mouse = new THREE.Vector2();

  state = {
    renderer,
    scene,
    camera,
    controls,
    events,
    points,
    geom,
    glowGeom,
    built,
    baseColors: built.baseColors,
    filterBaseColors: built.baseColors.slice(),
    highlightIndex: -1,
    filteredMask: new Array(events.length).fill(true),
    sessions,
    sessionById,
    // Default "All sessions": many rows have timeline_session_id NULL until regrouped; picking a
    // session first would hide them all and leave detail stuck on "Loading…".
    selectedSessionId: null,
    pathLine: null,
  };

  function rebuildTimelinePath() {
    if (state.pathLine) {
      group.remove(state.pathLine);
      state.pathLine.geometry.dispose();
      state.pathLine.material.dispose();
      state.pathLine = null;
    }
    const indices = [];
    for (let i = 0; i < events.length; i++) {
      if (state.filteredMask[i]) indices.push(i);
    }
    indices.sort((a, b) => events[a].start_time_millis - events[b].start_time_millis);
    if (indices.length < 2) return;

    const pos = new Float32Array(indices.length * 3);
    for (let j = 0; j < indices.length; j++) {
      const i = indices[j];
      pos[j * 3] = built.positions[i * 3];
      pos[j * 3 + 1] = built.positions[i * 3 + 1];
      pos[j * 3 + 2] = built.positions[i * 3 + 2];
    }
    const lg = new THREE.BufferGeometry();
    lg.setAttribute("position", new THREE.BufferAttribute(pos, 3));
    const lm = new THREE.LineBasicMaterial({
      color: 0x5eead4,
      transparent: true,
      opacity: 0.42,
      depthWrite: false,
    });
    const line = new THREE.Line(lg, lm);
    line.raycast = () => {};
    group.add(line);
    state.pathLine = line;
  }

  function currentSelectionMask() {
    const act = els.activityFilter.value;
    const where = els.whereFilter.value;
    const selectedSessionId = state.selectedSessionId;
    return events.map((e) => {
      if (act !== "__all__" && e.activity !== act) return false;
      if (where !== "__all__" && e.where_label !== where) return false;
      if (selectedSessionId != null) {
        const eid = e.timeline_session_id ?? null;
        if (eid == null) return false;
        if (Number(eid) !== Number(selectedSessionId)) return false;
      }
      return true;
    });
  }

  function applyFilterColors({ forceHighlight = true } = {}) {
    const mask = currentSelectionMask();
    state.filteredMask = mask;

    // Dim non-matching points (keeps geometry stable and avoids expensive rebuilds).
    const darkScale = 0.05;
    const c = state.filterBaseColors;
    for (let i = 0; i < events.length; i++) {
      const isOn = mask[i];
      for (let k = 0; k < 3; k++) {
        c[i * 3 + k] = state.baseColors[i * 3 + k] * (isOn ? 1 : darkScale);
      }
    }

    const geomColors = state.geom.attributes.color.array;
    geomColors.set(c);
    state.geom.attributes.color.needsUpdate = true;

    if (state.glowGeom) {
      const glowColors = state.glowGeom.attributes.color.array;
      glowColors.set(c);
      state.glowGeom.attributes.color.needsUpdate = true;
    }

    if (forceHighlight) {
      // If current highlight no longer matches the filter, jump to the newest match.
      if (state.highlightIndex !== -1 && !mask[state.highlightIndex]) {
        state.highlightIndex = -1;
        // Choose an initial match near newest.
        for (let i = events.length - 1; i >= 0; i--) {
          if (mask[i]) {
            state.highlightIndex = i;
            setEventDetails(events, events[i]);
            updateHighlight(state.geom, c, i, state.glowGeom);
            break;
          }
        }
      } else if (state.highlightIndex === -1) {
        // Nothing highlighted yet.
        for (let i = events.length - 1; i >= 0; i--) {
          if (mask[i]) {
            state.highlightIndex = i;
            setEventDetails(events, events[i]);
            updateHighlight(state.geom, c, i, state.glowGeom);
            break;
          }
        }
      }
    }
    rebuildTimelinePath();
  }

  function highlightByEventId(id) {
    const idx = events.findIndex((e) => e.id === id);
    if (idx === -1) return;
    state.highlightIndex = idx;
    updateHighlight(state.geom, state.filterBaseColors, idx, state.glowGeom);
    setEventDetails(events, events[idx]);
  }

  function highlightBySlider(opts = {}) {
    const allowSessionFallback = opts.allowSessionFallback !== false;
    const ratio = Number(els.timeSlider.value) / Number(els.timeSlider.max);
    const mask = state.filteredMask;
    /** Indices of visible events, chronological */
    const indices = [];
    for (let i = 0; i < events.length; i++) {
      if (mask[i]) indices.push(i);
    }
    indices.sort((a, b) => events[a].start_time_millis - events[b].start_time_millis);
    if (indices.length === 0) {
      if (allowSessionFallback && state.selectedSessionId != null) {
        state.selectedSessionId = null;
        if (els.sessionSlider) els.sessionSlider.value = "0";
        updateSessionUI();
        applyFilterColors({ forceHighlight: false });
        highlightBySlider({ allowSessionFallback: false });
        return;
      }
      els.detailText.textContent =
        "No events match the current filters or session.\n\n" +
        'Try session chip “All”, or set Activity / Where to “All”.';
      els.detailImg.src = "";
      els.sceneConf.textContent = "Scene —";
      els.activityConf.textContent = "Activity —";
      setModalPickHint(
        "No events match the current filters or session. Try “All” for session, Activity, and Where.",
      );
      return;
    }
    if (indices.length === 1) {
      highlightByEventId(events[indices[0]].id);
      return;
    }

    const localMin = events[indices[0]].start_time_millis;
    const localMax = events[indices[indices.length - 1]].start_time_millis;
    const timeSpan = localMax - localMin;

    let bestIdx = indices[0];
    // Same (or nearly same) timestamp: time scrub can't move selection — use order index instead.
    if (timeSpan < 2) {
      const pos = Math.round(ratio * (indices.length - 1));
      bestIdx = indices[pos];
    } else {
      const targetTime = localMin + ratio * timeSpan;
      let bestDist = Infinity;
      for (const i of indices) {
        const d = Math.abs(events[i].start_time_millis - targetTime);
        if (d < bestDist) {
          bestDist = d;
          bestIdx = i;
        }
      }
    }
    highlightByEventId(events[bestIdx].id);
  }

  // Point picking: only on a true click/tap (not after orbit drag)
  const PICK_SLOP_PX = 12;
  let pickPointer = null;

  function pickNearestEventAtClient(clientX, clientY) {
    const rect = renderer.domElement.getBoundingClientRect();
    if (rect.width < 4 || rect.height < 4) return -1;
    mouse.x = ((clientX - rect.left) / rect.width) * 2 - 1;
    mouse.y = -(((clientY - rect.top) / rect.height) * 2 - 1);
    raycaster.setFromCamera(mouse, camera);
    raycaster.params.Points.threshold = 0.45;

    const hits = raycaster.intersectObject(points, false);
    for (let h = 0; h < hits.length; h++) {
      const idx = hits[h].index;
      if (idx != null && idx >= 0 && state.filteredMask[idx]) return idx;
    }
    return -1;
  }

  renderer.domElement.addEventListener("pointerdown", (ev) => {
    if (ev.button !== 0) return;
    pickPointer = { id: ev.pointerId, x: ev.clientX, y: ev.clientY };
  });

  renderer.domElement.addEventListener("pointerup", (ev) => {
    if (ev.button !== 0 || pickPointer == null || ev.pointerId !== pickPointer.id) return;
    const dx = ev.clientX - pickPointer.x;
    const dy = ev.clientY - pickPointer.y;
    pickPointer = null;
    if (dx * dx + dy * dy > PICK_SLOP_PX * PICK_SLOP_PX) return;

    const best = pickNearestEventAtClient(ev.clientX, ev.clientY);
    if (best !== -1) {
      state.highlightIndex = best;
      updateHighlight(state.geom, state.filterBaseColors, best, state.glowGeom);
      setEventDetails(events, events[best]);
    }
  });

  renderer.domElement.addEventListener("pointercancel", () => {
    pickPointer = null;
  });

  // Filter changes: we currently re-query only highlight colors; cheap. If you need full point filtering,
  // we can rebuild geometry per filter.
  els.activityFilter.addEventListener("change", () => {
    applyFilterColors({ forceHighlight: false });
    highlightBySlider();
  });
  els.whereFilter.addEventListener("change", () => {
    applyFilterColors({ forceHighlight: false });
    highlightBySlider();
  });

  // Session selection (scrubber)
  const chips = [];
  function updateSessionUI() {
    const sliderVal = Number(els.sessionSlider.value);
    // 0 == all sessions, otherwise 1..N select sessions[sliderVal-1]
    const selectedSessionIndex = sliderVal === 0 ? -1 : sliderVal - 1;
    state.selectedSessionId = selectedSessionIndex === -1 ? null : state.sessions[selectedSessionIndex]?.id ?? null;

    const label =
      state.selectedSessionId == null
        ? "All sessions"
        : (state.sessionById.get(Number(state.selectedSessionId))?.title ?? "Session");
    if (els.sessionLabel) els.sessionLabel.textContent = label;

    for (const btn of chips) {
      const idx = Number(btn.dataset.sessionIndex);
      btn.classList.toggle("chipBtnActive", idx === selectedSessionIndex);
    }
  }

  function buildSessionChips() {
    if (!els.sessionChips) return;
    els.sessionChips.innerHTML = "";
    chips.length = 0;

    const allBtn = document.createElement("div");
    allBtn.className = "chipBtn";
    allBtn.textContent = "All";
    allBtn.dataset.sessionIndex = "-1";
    allBtn.addEventListener("click", () => {
      if (els.sessionSlider) els.sessionSlider.value = "0";
      updateSessionUI();
      applyFilterColors({ forceHighlight: false });
      els.timeSlider.value = 1000;
      highlightBySlider();
    });
    els.sessionChips.appendChild(allBtn);
    chips.push(allBtn);

    for (let i = 0; i < state.sessions.length; i++) {
      const s = state.sessions[i];
      const btn = document.createElement("div");
      btn.className = "chipBtn";
      btn.textContent = `${s.title || "Session"} (${s.event_count ?? 0})`;
      btn.dataset.sessionIndex = String(i);
      btn.addEventListener("click", () => {
        if (els.sessionSlider) els.sessionSlider.value = String(i + 1);
        updateSessionUI();
        applyFilterColors({ forceHighlight: false });
        els.timeSlider.value = 1000;
        highlightBySlider();
      });
      els.sessionChips.appendChild(btn);
      chips.push(btn);
    }
  }

  // Init session scrubber
  if (els.sessionSlider) {
    const max = state.sessions.length;
    els.sessionSlider.min = "0";
    els.sessionSlider.max = String(max);
    els.sessionSlider.step = "1";
    // Start on "All sessions" so every inferred row is visible (see selectedSessionId default).
    els.sessionSlider.value = "0";
  }
  buildSessionChips();
  updateSessionUI();

  if (els.sessionSlider) {
    els.sessionSlider.addEventListener("input", () => {
      updateSessionUI();
      applyFilterColors({ forceHighlight: false });
      els.timeSlider.value = 1000;
      highlightBySlider();
    });
  }

  const onTimeSlider = () => highlightBySlider();
  els.timeSlider.addEventListener("input", onTimeSlider);
  els.timeSlider.addEventListener("change", onTimeSlider);

  // Initial highlight: newest
  els.timeSlider.value = 1000;
  applyFilterColors({ forceHighlight: false });
  highlightBySlider();

  setStatus(`Loaded ${events.length} events. Click points to inspect.`);
  if (open3dModalBtn) open3dModalBtn.disabled = false;

  const animate = () => {
    requestAnimationFrame(animate);
    controls.update();
    const t = Date.now() * 0.001;

    // subtle pulse: rotate a hair (very slight)
    group.rotation.y = Math.sin(t * 0.35) * 0.02;

    renderer.render(scene, camera);
  };
  animate();
}

els.reloadBtn?.addEventListener("click", async () => {
  try {
    await loadAndRender();
  } catch (e) {
    console.error(e);
    setStatus(`Reload failed: ${e.message || e}`, "danger");
    if (open3dModalBtn) open3dModalBtn.disabled = true;
  }
});

// First load
try {
  await loadAndRender();
} catch (e) {
  console.error(e);
  setStatus(`Init failed: ${e.message || e}`, "danger");
  if (open3dModalBtn) open3dModalBtn.disabled = true;
}

