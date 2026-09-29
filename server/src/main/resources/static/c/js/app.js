const USER_KEY = "red_c_user";

const authPage = document.getElementById("auth-page");
const appPage = document.getElementById("app-page");
const authError = document.getElementById("auth-error");
const chatList = document.getElementById("chat-list");
const projectList = document.getElementById("project-list");
const headerBalance = document.getElementById("header-balance");
const userAvatar = document.getElementById("user-avatar");
const sheet = document.getElementById("sheet");
const sheetMask = document.getElementById("sheet-mask");
const sheetTitle = document.getElementById("sheet-title");
const sheetBody = document.getElementById("sheet-body");
const toast = document.getElementById("toast");

let currentUser = null;
let currentView = "chat";
let currentProjectId = null;
let projects = [];
let keywords = [];

function yuan(cents) {
  return "¥" + (Number(cents || 0) / 100).toFixed(2);
}

function escapeHtml(value) {
  return String(value ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;");
}

function showToast(message) {
  toast.textContent = message;
  toast.classList.remove("hidden");
  clearTimeout(showToast.timer);
  showToast.timer = setTimeout(() => toast.classList.add("hidden"), 2200);
}

function showAuthError(message) {
  authError.textContent = message;
  authError.classList.remove("hidden");
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
    throw new Error(data && data.error ? data.error : "请求失败");
  }
  return data;
}

function loadUser() {
  try {
    return JSON.parse(localStorage.getItem(USER_KEY) || "null");
  } catch (ignore) {
    return null;
  }
}

function saveUser(user) {
  currentUser = user;
  localStorage.setItem(USER_KEY, JSON.stringify(user));
  headerBalance.textContent = yuan(user.points);
  userAvatar.textContent = String(user.phone || "U").slice(-1);
}

function clearUser() {
  currentUser = null;
  localStorage.removeItem(USER_KEY);
}

function formatTime(value) {
  if (!value) return "";
  return String(value).replace("T", " ").slice(0, 19);
}

function statusText(status) {
  return ({ PROCESSING: "处理中", DONE: "已完成", FAILED: "失败" })[status] || status;
}

function renderAuth() {
  authPage.classList.remove("hidden");
  appPage.classList.add("hidden");
}

async function renderApp(user) {
  saveUser(user);
  authPage.classList.add("hidden");
  appPage.classList.remove("hidden");
  try {
    const me = await api("/api/v1/c/me?userId=" + user.id);
    if (me.user) saveUser(me.user);
  } catch (ignore) {
    // 旧进程尚未加载 C 端接口时，先用登录返回的用户信息。
  }
  await Promise.all([loadProjects(), loadKeywords(), loadMessages()]);
  switchView("chat");
}

function renderMessages(messages) {
  const balanceCard =
    '<article class="balance-card">余额：' + yuan(currentUser && currentUser.points) + "</article>";
  if (!messages.length) {
    chatList.innerHTML = balanceCard + '<div class="bubble system">暂无消息</div>';
    return;
  }
  chatList.innerHTML = balanceCard + messages.map((msg) => {
    if (msg.msgType === "order") {
      const extra = msg.extra || {};
      return (
        '<article class="order-card">' +
          "<strong>下单成功</strong>" +
          "<div>项目：" + escapeHtml(extra.projectName) + "</div>" +
          "<div>单号：" + escapeHtml(extra.orderNo) + "</div>" +
          "<div>邀请码：" + escapeHtml(extra.inviteCode) + "</div>" +
          "<div>扣款：-" + yuan(extra.amountCents) + "</div>" +
          "<div>余额：" + yuan(extra.balanceCents) + "</div>" +
          "<div>时间：" + escapeHtml(formatTime(extra.createdTime || msg.createdTime)) + "</div>" +
          '<div class="order-wait">处理中，请耐心等待…</div>' +
        "</article>"
      );
    }
    const role = msg.role === "user" ? "user" : "system";
    return (
      '<div class="bubble ' + role + '">' +
        escapeHtml(msg.content) +
        '<div class="msg-time">' + escapeHtml(formatTime(msg.createdTime)) + "</div>" +
      "</div>"
    );
  }).join("");
  chatList.scrollTop = chatList.scrollHeight;
}

function renderProjects() {
  if (!projects.length) {
    projectList.innerHTML = '<div class="empty">暂无项目</div>';
    return;
  }
  projectList.innerHTML = projects.map((item) => {
    const open = item.status === "OPEN";
    return (
      '<article class="project-card" data-id="' + item.id + '">' +
        "<header><div>" + escapeHtml(item.name) + "</div>" +
          '<span class="tag">' + escapeHtml(item.category || "未分类") + "</span></header>" +
        '<div class="price">' + yuan(item.priceCents) + "<small>零售价</small></div>" +
        '<div class="tags">' +
          '<span class="tag ' + (open ? "open" : "") + '">' + (open ? "接单中" : "未开放") + "</span>" +
        "</div>" +
        '<div class="project-actions"><button type="button" class="btn" data-tutorial="' + item.id + '">教程</button></div>' +
      "</article>"
    );
  }).join("");
}

async function loadProjects() {
  const data = await api("/api/v1/c/projects");
  projects = data.projects || [];
  renderProjects();
}

async function loadKeywords() {
  const data = await api("/api/v1/c/keywords");
  keywords = data.keywords || [];
}

async function loadMessages() {
  const data = await api("/api/v1/c/messages?userId=" + currentUser.id);
  renderMessages(data.messages || []);
}

function switchView(view) {
  currentView = view;
  document.querySelectorAll(".main-tab").forEach((tab) => {
    tab.classList.toggle("active", tab.dataset.view === view);
  });
  document.getElementById("view-chat").classList.toggle("hidden", view !== "chat");
  if (window.matchMedia("(min-width: 1024px)").matches) {
    document.getElementById("view-projects").classList.remove("hidden");
  } else {
    document.getElementById("view-projects").classList.toggle("hidden", view !== "projects");
  }
}

function openSheet(title, html) {
  sheetTitle.textContent = title;
  sheetBody.innerHTML = html;
  sheet.classList.remove("hidden");
  sheetMask.classList.remove("hidden");
}

function closeSheet() {
  sheet.classList.add("hidden");
  sheetMask.classList.add("hidden");
}

async function openKeywords() {
  const html = keywords.length
    ? keywords.map((item) => (
        '<button type="button" class="keyword" data-word="' + escapeHtml(item.word) + '" data-project="' + item.projectId + '">' +
          escapeHtml(item.word) + " · " + escapeHtml(item.projectName) +
        "</button>"
      )).join("")
    : '<div class="empty">暂无关键词</div>';
  openSheet("关键词", html);
}

async function openOrders() {
  const data = await api("/api/v1/c/orders?userId=" + currentUser.id);
  const rows = data.orders || [];
  const html = rows.length
    ? rows.map((row) => (
        '<div class="order-row">' +
          "<div><strong>" + escapeHtml(row.projectName) + "</strong> · " + escapeHtml(row.orderNo) + "</div>" +
          "<div>邀请码：" + escapeHtml(row.inviteCode || "-") + "</div>" +
          "<div>" + yuan(row.amountCents) + " · " + statusText(row.status) + "</div>" +
          "<div>" + escapeHtml(formatTime(row.createdTime)) + "</div>" +
        "</div>"
      )).join("")
    : '<div class="empty">暂无订单</div>';
  openSheet("订单", html);
}

function openRecharge() {
  openSheet(
    "充值",
    "<p class=\"empty\">暂未开通在线支付，请使用卡密到账。</p>" +
    '<label>卡密<input id="recharge-key" placeholder="输入卡密"></label>' +
    '<button type="button" class="btn primary" id="recharge-btn">兑换到余额</button>'
  );
}

function openCard() {
  openSheet(
    "卡密兑换",
    '<label>卡密<input id="card-key" placeholder="输入卡密"></label>' +
    '<button type="button" class="btn primary" id="card-btn">立即兑换</button>'
  );
}

async function redeem(cardKey) {
  const data = await api("/api/v1/c/cards/redeem", {
    method: "POST",
    body: JSON.stringify({ userId: currentUser.id, cardKey: cardKey })
  });
  saveUser(data.user);
  closeSheet();
  showToast("兑换成功，余额 " + yuan(data.user.points));
  await loadMessages();
}

document.querySelectorAll(".auth-tab").forEach((tab) => {
  tab.addEventListener("click", () => {
    document.querySelectorAll(".auth-tab").forEach((item) => item.classList.remove("active"));
    tab.classList.add("active");
    const login = tab.dataset.tab === "login";
    document.getElementById("login-form").classList.toggle("hidden", !login);
    document.getElementById("register-form").classList.toggle("hidden", login);
    authError.classList.add("hidden");
  });
});

async function submitAuth(path, form) {
  authError.classList.add("hidden");
  const data = new FormData(form);
  const result = await api(path, {
    method: "POST",
    body: JSON.stringify({
      phone: String(data.get("phone") || "").trim(),
      password: String(data.get("password") || "")
    })
  });
  await renderApp(result.user);
}

document.getElementById("login-form").addEventListener("submit", async (event) => {
  event.preventDefault();
  try {
    await submitAuth("/api/v1/users/login", event.target);
  } catch (ex) {
    showAuthError(ex.message);
  }
});

document.getElementById("register-form").addEventListener("submit", async (event) => {
  event.preventDefault();
  try {
    await submitAuth("/api/v1/users/register", event.target);
  } catch (ex) {
    showAuthError(ex.message);
  }
});

document.getElementById("logout-btn").addEventListener("click", () => {
  clearUser();
  renderAuth();
});

document.querySelectorAll(".main-tab").forEach((tab) => {
  tab.addEventListener("click", () => switchView(tab.dataset.view));
});

document.getElementById("chat-form").addEventListener("submit", async (event) => {
  event.preventDefault();
  const input = document.getElementById("chat-input");
  const text = input.value.trim();
  if (!text) return;
  input.value = "";
  try {
    const data = await api("/api/v1/c/chat", {
      method: "POST",
      body: JSON.stringify({
        userId: currentUser.id,
        text,
        projectId: currentProjectId
      })
    });
    if (data.user) saveUser(data.user);
    await loadMessages();
  } catch (ex) {
    showToast(ex.message);
    await loadMessages();
  }
});

document.getElementById("project-list").addEventListener("click", (event) => {
  const tutorialBtn = event.target.closest("[data-tutorial]");
  if (tutorialBtn) {
    event.stopPropagation();
    const item = projects.find((row) => String(row.id) === tutorialBtn.dataset.tutorial);
    openSheet("教程", '<div class="order-row">' + escapeHtml(item && item.tutorial ? item.tutorial : "暂无教程") + "</div>");
    return;
  }
  const card = event.target.closest(".project-card");
  if (!card) return;
  currentProjectId = Number(card.dataset.id);
  const item = projects.find((row) => row.id === currentProjectId);
  switchView("chat");
  document.getElementById("chat-input").placeholder = "向「" + (item ? item.name : "当前项目") + "」发送重逢码…";
  showToast("已选择 " + (item ? item.name : "项目"));
});

document.querySelectorAll(".dock button").forEach((btn) => {
  btn.addEventListener("click", async () => {
    const type = btn.dataset.sheet;
    try {
      if (type === "keywords") await openKeywords();
      if (type === "orders") await openOrders();
      if (type === "recharge") openRecharge();
      if (type === "card") openCard();
    } catch (ex) {
      showToast(ex.message);
    }
  });
});

sheetBody.addEventListener("click", async (event) => {
  const keyword = event.target.closest(".keyword");
  if (keyword) {
    currentProjectId = Number(keyword.dataset.project);
    document.getElementById("chat-input").value = keyword.dataset.word + " ";
    document.getElementById("chat-input").focus();
    closeSheet();
    switchView("chat");
    return;
  }
  const redeemBtn = event.target.closest("#card-btn, #recharge-btn");
  if (redeemBtn) {
    const input = document.getElementById(redeemBtn.id === "card-btn" ? "card-key" : "recharge-key");
    try {
      await redeem(String(input && input.value || "").trim());
    } catch (ex) {
      showToast(ex.message);
    }
  }
});

document.getElementById("sheet-close").addEventListener("click", closeSheet);
sheetMask.addEventListener("click", closeSheet);
window.addEventListener("resize", () => switchView(currentView));

const bootUser = loadUser();
if (bootUser && bootUser.id) {
  renderApp(bootUser).catch(() => {
    clearUser();
    renderAuth();
  });
} else {
  renderAuth();
}
