const USER_KEY = "red_admin_user";

const authPage = document.getElementById("auth-page");
const appPage = document.getElementById("app-page");
const authError = document.getElementById("auth-error");
const taskBanner = document.getElementById("task-banner");
const currentPhone = document.getElementById("current-phone");
const healthDot = document.getElementById("health-dot");
const taskBody = document.getElementById("task-tbody");
const tokenBanner = document.getElementById("token-banner");
const tokenBody = document.getElementById("token-tbody");
const pageTitle = document.getElementById("page-title");
let currentPanel = "tasks";
let tokenQuery = { status: "", tokenType: "", reunionCode: "" };
let taskQuery = { gameCode: "", taskType: "", status: "", reunionCode: "" };
let taskCatalog = { games: [] };
let tokenTypes = [];

function showBanner(el, message, type) {
  el.textContent = message;
  el.classList.remove("hidden", "error", "ok");
  el.classList.add(type);
}

function hideBanner(el) {
  el.classList.add("hidden");
}

async function api(path, options = {}) {
  const response = await fetch(path, {
    headers: { "Content-Type": "application/json", ...(options.headers || {}) },
    ...options
  });
  const text = await response.text();
  let data = null;
  try {
    data = text ? JSON.parse(text) : null;
  } catch (ignore) {
    data = { error: text || "invalid response" };
  }
  if (!response.ok) {
    const message = data && data.error ? data.error : "请求失败 (" + response.status + ")";
    throw new Error(message);
  }
  return data;
}

function currentUser() {
  try {
    return JSON.parse(localStorage.getItem(USER_KEY) || "null");
  } catch (ignore) {
    return null;
  }
}

function setUser(user) {
  localStorage.setItem(USER_KEY, JSON.stringify(user));
}

function clearUser() {
  localStorage.removeItem(USER_KEY);
}

function renderAuth() {
  authPage.classList.remove("hidden");
  appPage.classList.add("hidden");
}

function renderApp(user) {
  authPage.classList.add("hidden");
  appPage.classList.remove("hidden");
  currentPhone.textContent = user && user.phone ? user.phone : "";
  loadCatalogs().then(() => refreshAll()).catch((ex) => {
    showBanner(taskBanner, ex.message, "error");
  });
}

function statusLabel(status) {
  return ({
    PENDING: "待领取",
    RUNNING: "执行中",
    DONE: "已完成",
    FAILED: "失败"
  })[status] || status || "-";
}

async function refreshHealth() {
  try {
    const data = await api("/api/v1/health");
    if (data.database === false) {
      healthDot.textContent = "服务正常，数据库未连接";
      healthDot.className = "health bad";
    } else {
      healthDot.textContent = "服务正常";
      healthDot.className = "health ok";
    }
    healthDot.title = data.time || "";
  } catch (ex) {
    healthDot.textContent = "服务异常";
    healthDot.className = "health bad";
  }
}

async function refreshStats() {
  const stats = await api("/api/v1/tasks/stats");
  document.getElementById("stat-pending").textContent = stats.pending ?? 0;
  document.getElementById("stat-running").textContent = stats.running ?? 0;
  document.getElementById("stat-done").textContent = stats.done ?? 0;
  document.getElementById("stat-failed").textContent = stats.failed ?? 0;
}

async function refreshTasks() {
  const params = new URLSearchParams();
  if (taskQuery.gameCode) params.set("gameCode", taskQuery.gameCode);
  if (taskQuery.taskType) params.set("taskType", taskQuery.taskType);
  if (taskQuery.status) params.set("status", taskQuery.status);
  if (taskQuery.reunionCode) params.set("reunionCode", taskQuery.reunionCode);
  const qs = params.toString();
  const data = await api("/api/v1/admin/tasks" + (qs ? "?" + qs : ""));
  const tasks = data.tasks || [];
  const countEl = document.getElementById("task-count");
  if (countEl) countEl.textContent = "共 " + tasks.length + " 条";
  const checkAll = document.getElementById("task-check-all");
  if (checkAll) checkAll.checked = false;
  if (!tasks.length) {
    taskBody.innerHTML = '<tr><td colspan="10" class="empty">没有符合条件的任务</td></tr>';
    return;
  }
  taskBody.innerHTML = tasks.map((task) => {
    const status = task.status || "";
    return (
      "<tr>" +
        '<td><input type="checkbox" class="task-check" value="' + task.id + '"></td>' +
        "<td>" + task.id + "</td>" +
        "<td>" + escapeHtml(task.gameName || task.gameCode || "-") + "</td>" +
        "<td>" + escapeHtml(task.taskName || task.taskType || "-") + "</td>" +
        '<td class="token" title="' + escapeHtml(task.account) + '">' + escapeHtml(task.account || "-") + "</td>" +
        "<td>" + escapeHtml(task.reunionCode || "-") + "</td>" +
        '<td><span class="status ' + status + '">' + statusLabel(status) + "</span></td>" +
        "<td>" + escapeHtml(task.assignedDevice || "-") + "</td>" +
        "<td>" + (task.attempts ?? 0) + "</td>" +
        "<td>" + escapeHtml(task.lastError || "-") + "</td>" +
      "</tr>"
    );
  }).join("");
}

function selectedTaskIds() {
  return Array.from(document.querySelectorAll(".task-check:checked")).map((el) => Number(el.value));
}

function fillTaskQueryTypes(keepType) {
  const games = taskCatalog.games || [];
  const gameCode = document.getElementById("task-query-game").value;
  const game = games.find((item) => item.code === gameCode);
  const tasks = game && game.tasks ? game.tasks : games.flatMap((item) => item.tasks || []);
  const unique = [];
  const seen = new Set();
  tasks.forEach((item) => {
    if (item && item.code && !seen.has(item.code)) {
      seen.add(item.code);
      unique.push(item);
    }
  });
  const selected = keepType && unique.some((item) => item.code === keepType) ? keepType : "";
  fillSelect(document.getElementById("task-query-type"), unique, selected, "全部");
}

function selectedGame() {
  const code = document.getElementById("task-game").value;
  return (taskCatalog.games || []).find((game) => game.code === code) || null;
}

function selectedTaskType() {
  const game = selectedGame();
  if (!game) return null;
  const code = document.getElementById("task-type").value;
  return (game.tasks || []).find((item) => item.code === code) || null;
}

function fillSelect(el, items, selected, emptyLabel) {
  const empty = emptyLabel
    ? '<option value="">' + escapeHtml(emptyLabel) + "</option>"
    : "";
  el.innerHTML = empty + items.map((item) => {
    const picked = item.code === selected ? " selected" : "";
    return '<option value="' + escapeHtml(item.code) + '"' + picked + ">" + escapeHtml(item.name) + "</option>";
  }).join("");
}

function renderTaskTypeFields() {
  const type = selectedTaskType();
  const hint = document.getElementById("task-type-hint");
  const fieldsEl = document.getElementById("task-fields");
  if (!type) {
    hint.textContent = "请选择游戏和任务类型。";
    fieldsEl.innerHTML = "";
    return;
  }
  hint.textContent = type.description || "";
  fieldsEl.innerHTML = (type.fields || []).map((field) => {
    const required = field.required ? " required" : "";
    const inputType = field.inputType === "textarea" ? "textarea" : "text";
    const placeholder = escapeHtml(field.placeholder || "");
    const label = escapeHtml(field.label || field.key);
    if (inputType === "textarea") {
      return (
        '<label class="field-wide">' + label +
          '<textarea name="' + escapeHtml(field.key) + '" placeholder="' + placeholder + '"' + required + "></textarea>" +
        "</label>"
      );
    }
    return (
      "<label>" + label +
        '<input name="' + escapeHtml(field.key) + '" type="text" placeholder="' + placeholder + '"' + required + ">" +
      "</label>"
    );
  }).join("");
}

function renderTaskTypeOptions(keepType) {
  const game = selectedGame();
  const typeSelect = document.getElementById("task-type");
  const tasks = game && game.tasks ? game.tasks : [];
  const selected = keepType && tasks.some((item) => item.code === keepType)
    ? keepType
    : (tasks[0] ? tasks[0].code : "");
  fillSelect(typeSelect, tasks, selected);
  renderTaskTypeFields();
}

async function loadCatalogs() {
  const [taskData, tokenData] = await Promise.all([
    api("/api/v1/admin/tasks/types"),
    api("/api/v1/admin/tokens/types")
  ]);
  taskCatalog = taskData || { games: [] };
  tokenTypes = (tokenData && tokenData.types) || [];
  const games = taskCatalog.games || [];
  const gameSelect = document.getElementById("task-game");
  const previousGame = gameSelect.value;
  const selected = games.some((game) => game.code === previousGame)
    ? previousGame
    : (games[0] ? games[0].code : "");
  fillSelect(gameSelect, games, selected);
  renderTaskTypeOptions(document.getElementById("task-type").value);

  const importType = document.getElementById("token-import-type");
  const prevImport = importType.value;
  fillSelect(
    importType,
    tokenTypes,
    tokenTypes.some((item) => item.code === prevImport) ? prevImport : (tokenTypes[0] ? tokenTypes[0].code : "")
  );
  const queryType = document.getElementById("token-query-type");
  fillSelect(queryType, tokenTypes, queryType.value, "全部");

  const queryGame = document.getElementById("task-query-game");
  fillSelect(queryGame, games, queryGame.value, "全部");
  fillTaskQueryTypes(document.getElementById("task-query-type").value);
}

function tokenStatusLabel(status) {
  return ({
    UNLINKED: "未关联",
    LINKED: "已关联",
    BOUND: "已绑定",
    ERROR: "错误"
  })[status] || status || "-";
}

async function refreshTokens() {
  const params = new URLSearchParams();
  if (tokenQuery.status) params.set("status", tokenQuery.status);
  if (tokenQuery.tokenType) params.set("tokenType", tokenQuery.tokenType);
  if (tokenQuery.reunionCode) params.set("reunionCode", tokenQuery.reunionCode);
  const qs = params.toString();
  const data = await api("/api/v1/admin/tokens" + (qs ? "?" + qs : ""));
  const tokens = data.tokens || [];
  document.getElementById("token-count").textContent = "共 " + tokens.length + " 条";
  const checkAll = document.getElementById("token-check-all");
  if (checkAll) checkAll.checked = false;
  if (!tokens.length) {
    tokenBody.innerHTML = '<tr><td colspan="8" class="empty">没有符合条件的 Token</td></tr>';
    return;
  }
  tokenBody.innerHTML = tokens.map((row) => {
    const status = row.status || "";
    return (
      "<tr>" +
        '<td><input type="checkbox" class="token-check" value="' + row.id + '"></td>' +
        "<td>" + row.id + "</td>" +
        "<td>" + escapeHtml(row.tokenTypeName || row.tokenType || "-") + "</td>" +
        '<td class="token" title="' + escapeHtml(row.token) + '">' + escapeHtml(row.token) + "</td>" +
        '<td><span class="status ' + status + '">' + tokenStatusLabel(status) + "</span></td>" +
        "<td>" + escapeHtml(row.reunionCode || "-") + "</td>" +
        "<td>" + escapeHtml(formatTime(row.updatedTime)) + "</td>" +
        '<td><button type="button" class="btn btn-copy" data-token="' + encodeURIComponent(row.token) + '">复制</button></td>' +
      "</tr>"
    );
  }).join("");
}

function formatTime(value) {
  if (!value) return "-";
  return String(value).replace("T", " ").replace("Z", "");
}

async function readTokenFile(file) {
  const buffer = await file.arrayBuffer();
  const bytes = new Uint8Array(buffer);
  const decode = (label) => new TextDecoder(label).decode(buffer);
  if (bytes.length >= 2 && bytes[0] === 0xff && bytes[1] === 0xfe) {
    return decode("utf-16le");
  }
  if (bytes.length >= 2 && bytes[0] === 0xfe && bytes[1] === 0xff) {
    return decode("utf-16be");
  }
  if (bytes.length >= 3 && bytes[0] === 0xef && bytes[1] === 0xbb && bytes[2] === 0xbf) {
    return decode("utf-8");
  }
  let nuls = 0;
  const probe = Math.min(bytes.length, 400);
  for (let i = 1; i < probe; i += 2) {
    if (bytes[i] === 0) nuls++;
  }
  if (probe > 20 && nuls > probe / 6) {
    return decode("utf-16le");
  }
  return decode("utf-8");
}

function selectedTokenIds() {
  return Array.from(document.querySelectorAll(".token-check:checked")).map((el) => Number(el.value));
}

async function copyToken(token) {
  try {
    await navigator.clipboard.writeText(token);
    showBanner(tokenBanner, "已复制 Token", "ok");
  } catch (ignore) {
    const box = document.createElement("textarea");
    box.value = token;
    document.body.appendChild(box);
    box.select();
    document.execCommand("copy");
    box.remove();
    showBanner(tokenBanner, "已复制 Token", "ok");
  }
}

function switchPanel(panel) {
  currentPanel = panel;
  document.querySelectorAll(".nav-item").forEach((item) => {
    item.classList.toggle("active", item.dataset.panel === panel);
  });
  document.getElementById("panel-tasks").classList.toggle("hidden", panel !== "tasks");
  document.getElementById("panel-tokens").classList.toggle("hidden", panel !== "tokens");
  pageTitle.textContent = panel === "tokens" ? "Token 管理" : "任务管理";
  refreshAll();
}

function escapeHtml(value) {
  return String(value ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;");
}

async function refreshAll() {
  await refreshHealth();
  try {
    if (currentPanel === "tokens") {
      hideBanner(tokenBanner);
      await refreshTokens();
    } else {
      hideBanner(taskBanner);
      await Promise.all([refreshStats(), refreshTasks()]);
    }
  } catch (ex) {
    const banner = currentPanel === "tokens" ? tokenBanner : taskBanner;
    showBanner(banner, ex.message, "error");
  }
}

document.querySelectorAll(".tab").forEach((tab) => {
  tab.addEventListener("click", () => {
    document.querySelectorAll(".tab").forEach((item) => item.classList.remove("active"));
    tab.classList.add("active");
    const isLogin = tab.dataset.tab === "login";
    document.getElementById("login-form").classList.toggle("hidden", !isLogin);
    document.getElementById("register-form").classList.toggle("hidden", isLogin);
    hideBanner(authError);
  });
});

document.getElementById("login-form").addEventListener("submit", async (event) => {
  event.preventDefault();
  hideBanner(authError);
  const form = new FormData(event.target);
  try {
    const data = await api("/api/v1/users/login", {
      method: "POST",
      body: JSON.stringify({
        phone: String(form.get("phone") || "").trim(),
        password: String(form.get("password") || "")
      })
    });
    setUser(data.user);
    renderApp(data.user);
  } catch (ex) {
    showBanner(authError, ex.message, "error");
  }
});

document.getElementById("register-form").addEventListener("submit", async (event) => {
  event.preventDefault();
  hideBanner(authError);
  const form = new FormData(event.target);
  try {
    const data = await api("/api/v1/users/register", {
      method: "POST",
      body: JSON.stringify({
        phone: String(form.get("phone") || "").trim(),
        password: String(form.get("password") || "")
      })
    });
    setUser(data.user);
    renderApp(data.user);
  } catch (ex) {
    showBanner(authError, ex.message, "error");
  }
});

document.getElementById("task-game").addEventListener("change", () => {
  renderTaskTypeOptions("");
});
document.getElementById("task-type").addEventListener("change", renderTaskTypeFields);

document.getElementById("task-form").addEventListener("submit", async (event) => {
  event.preventDefault();
  hideBanner(taskBanner);
  const form = new FormData(event.target);
  const submitBtn = event.target.querySelector("button[type=submit]");
  submitBtn.disabled = true;
  const payload = {};
  document.querySelectorAll("#task-fields [name]").forEach((el) => {
    payload[el.name] = String(el.value || "").trim();
  });
  try {
    const result = await api("/api/v1/admin/tasks", {
      method: "POST",
      body: JSON.stringify({
        gameCode: String(form.get("gameCode") || "").trim(),
        taskType: String(form.get("taskType") || "").trim(),
        payload
      })
    });
    const type = selectedTaskType();
    const game = selectedGame();
    renderTaskTypeFields();
    const pooled = !!(type && type.tokenType);
    showBanner(
      taskBanner,
      "已创建 " + (result.count ?? 0) + " 条任务（"
        + (game ? game.name : "") + " / " + (type ? type.name : "") + "）"
        + (pooled ? "，Token 已从库中自动分配。" : "。"),
      "ok"
    );
    await Promise.all([refreshStats(), refreshTasks()]);
  } catch (ex) {
    showBanner(taskBanner, ex.message, "error");
  } finally {
    submitBtn.disabled = false;
  }
});

document.getElementById("refresh-btn").addEventListener("click", refreshAll);
document.getElementById("logout-btn").addEventListener("click", () => {
  clearUser();
  renderAuth();
});

document.querySelectorAll(".nav-item").forEach((item) => {
  item.addEventListener("click", () => switchPanel(item.dataset.panel));
});

document.getElementById("task-query-game").addEventListener("change", () => {
  fillTaskQueryTypes("");
});

document.getElementById("task-query-form").addEventListener("submit", async (event) => {
  event.preventDefault();
  const form = new FormData(event.target);
  taskQuery = {
    gameCode: String(form.get("gameCode") || "").trim(),
    taskType: String(form.get("taskType") || "").trim(),
    status: String(form.get("status") || "").trim(),
    reunionCode: String(form.get("reunionCode") || "").trim()
  };
  hideBanner(taskBanner);
  try {
    await refreshTasks();
  } catch (ex) {
    showBanner(taskBanner, ex.message, "error");
  }
});

document.getElementById("task-delete-btn").addEventListener("click", async () => {
  const ids = selectedTaskIds();
  if (!ids.length) {
    showBanner(taskBanner, "请先勾选要删除的任务", "error");
    return;
  }
  if (!window.confirm("确认删除选中的 " + ids.length + " 条任务？未完成任务占用的 Token 会放回未关联。")) {
    return;
  }
  hideBanner(taskBanner);
  try {
    const result = await api("/api/v1/admin/tasks/delete", {
      method: "POST",
      body: JSON.stringify({ ids: ids })
    });
    showBanner(taskBanner, "已删除 " + result.deleted + " 条", "ok");
    await Promise.all([refreshStats(), refreshTasks()]);
  } catch (ex) {
    showBanner(taskBanner, ex.message, "error");
  }
});

const retryMask = document.getElementById("retry-mask");
const retryDialog = document.getElementById("retry-dialog");
let pendingRetryIds = [];

function openRetryDialog(ids) {
  pendingRetryIds = ids;
  document.getElementById("retry-count").textContent = String(ids.length);
  retryMask.classList.remove("hidden");
  retryDialog.classList.remove("hidden");
}

function closeRetryDialog() {
  pendingRetryIds = [];
  retryMask.classList.add("hidden");
  retryDialog.classList.add("hidden");
}

async function submitRetry(switchToken) {
  const ids = pendingRetryIds.slice();
  if (!ids.length) {
    closeRetryDialog();
    return;
  }
  closeRetryDialog();
  hideBanner(taskBanner);
  try {
    const result = await api("/api/v1/admin/tasks/retry", {
      method: "POST",
      body: JSON.stringify({ ids: ids, switchToken: switchToken })
    });
    const mode = result.switchToken ? "并已切换 Token" : "且沿用原 Token";
    showBanner(taskBanner, "已重试 " + result.retried + " 条，" + mode, "ok");
    await Promise.all([refreshStats(), refreshTasks()]);
  } catch (ex) {
    showBanner(taskBanner, ex.message, "error");
  }
}

document.getElementById("task-retry-btn").addEventListener("click", () => {
  const ids = selectedTaskIds();
  if (!ids.length) {
    showBanner(taskBanner, "请先勾选要重试的任务", "error");
    return;
  }
  hideBanner(taskBanner);
  openRetryDialog(ids);
});

document.getElementById("retry-switch-btn").addEventListener("click", () => submitRetry(true));
document.getElementById("retry-keep-btn").addEventListener("click", () => submitRetry(false));
document.getElementById("retry-cancel-btn").addEventListener("click", closeRetryDialog);
retryMask.addEventListener("click", closeRetryDialog);

document.getElementById("task-check-all").addEventListener("change", (event) => {
  document.querySelectorAll(".task-check").forEach((box) => {
    box.checked = event.target.checked;
  });
});

document.getElementById("token-query-form").addEventListener("submit", async (event) => {
  event.preventDefault();
  const form = new FormData(event.target);
  tokenQuery = {
    status: String(form.get("status") || "").trim(),
    tokenType: String(form.get("tokenType") || "").trim(),
    reunionCode: String(form.get("reunionCode") || "").trim()
  };
  hideBanner(tokenBanner);
  try {
    await refreshTokens();
  } catch (ex) {
    showBanner(tokenBanner, ex.message, "error");
  }
});

document.getElementById("token-import-btn").addEventListener("click", async () => {
  const fileInput = document.getElementById("token-file");
  const file = fileInput.files && fileInput.files[0];
  if (!file) {
    showBanner(tokenBanner, "请先选择 txt 文件", "error");
    return;
  }
  const tokenType = String(document.getElementById("token-import-type").value || "").trim();
  if (!tokenType) {
    showBanner(tokenBanner, "请选择 Token 类型", "error");
    return;
  }
  hideBanner(tokenBanner);
  let content = "";
  try {
    content = await readTokenFile(file);
  } catch (ex) {
    showBanner(tokenBanner, "读取文件失败: " + ex.message, "error");
    return;
  }
  try {
    const result = await api("/api/v1/admin/tokens/import", {
      method: "POST",
      body: JSON.stringify({ tokenType: tokenType, content: content })
    });
    fileInput.value = "";
    showBanner(
      tokenBanner,
      "导入完成：共 " + result.total + " 条，新增 " + result.inserted + "，跳过重复 " + result.skipped,
      "ok"
    );
    await refreshTokens();
  } catch (ex) {
    showBanner(tokenBanner, ex.message, "error");
  }
});

document.getElementById("token-delete-btn").addEventListener("click", async () => {
  const ids = selectedTokenIds();
  if (!ids.length) {
    showBanner(tokenBanner, "请先勾选要删除的 Token", "error");
    return;
  }
  if (!window.confirm("确认删除选中的 " + ids.length + " 条 Token？")) {
    return;
  }
  hideBanner(tokenBanner);
  try {
    const result = await api("/api/v1/admin/tokens/delete", {
      method: "POST",
      body: JSON.stringify({ ids: ids })
    });
    showBanner(tokenBanner, "已删除 " + result.deleted + " 条", "ok");
    await refreshTokens();
  } catch (ex) {
    showBanner(tokenBanner, ex.message, "error");
  }
});

document.getElementById("token-check-all").addEventListener("change", (event) => {
  document.querySelectorAll(".token-check").forEach((box) => {
    box.checked = event.target.checked;
  });
});

document.getElementById("token-tbody").addEventListener("click", async (event) => {
  const btn = event.target.closest(".btn-copy");
  if (!btn) return;
  const token = decodeURIComponent(btn.getAttribute("data-token") || "");
  if (token) await copyToken(token);
});

const user = currentUser();
if (user) {
  renderApp(user);
} else {
  renderAuth();
}
