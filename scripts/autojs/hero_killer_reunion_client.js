"auto";

/**
 * 英雄杀云手机自动化脚本（唯一客户端）
 * ------------------------------------------------------------
 * 目标：从服务端动态领取账号 token 与重逢码，按 13 步业务流程执行后回传结果。
 *
 * 业务流程：
 * 1. 打开游戏
 * 2. 进入登录页面（已在大厅则自动跳过登录相关步骤）
 * 3. 勾选协议
 * 4. 选择 QQ 登录
 * 5. 自动拉起登号器
 * 6. 填写账号 token，点击 OP 授权
 * 7. 进入游戏大厅
 * 8. 点击左下角好友
 * 9. 进入好友页后，点击左侧第二个按钮（广结好友）
 * 10. 弹窗中填入重逢码并点击确定
 * 11. 关闭重试弹窗
 * 12. 返回大厅，进入个人中心并切换账号
 * 13. 返回至登录页面
 *
 * 运行模式：只从服务端领取本脚本对应的任务（gameCode=hero_killer, taskType=reunion），执行中心跳，结束后上报。
 *
 * 说明：
 * - SERVER_BASE 改成当前服务地址。DEVICE_ID 建议留空：每台云手机会自动生成并写到本地，多机可共用同一份脚本
 * - 若要手动指定，填写互不相同的名字，例如 redfinger-01、redfinger-02
 * - 游戏自绘页优先走 COORD_ONLY_MODE 坐标点击
 */

/**
 * 全局配置区（先改这里）
 */
const CONFIG = {
  // Java 服务端基础地址，需与当前服务端口一致
  SERVER_BASE: "http://101.201.102.6:8889/api/v1",

  // 若服务端开启 API_KEY，这里填写同样的 key；未开启则留空
  API_KEY: "",

  // 云手机唯一标识。留空则用本机 AndroidId（多机可共用脚本）。要人工命名时每台填不同值。
  DEVICE_ID: "",

  // 领取时只拿本脚本能执行的任务，避免和后续其它游戏/玩法抢队列
  GAME_CODE: "hero_killer",
  TASK_TYPE: "reunion",

  // 游戏 App 名称（按云手机实际名称修改）
  GAME_APP_NAME: "英雄杀",

  // 桌面图标文案兜底（launchApp 失败时点击）
  GAME_ICON_TEXT_FALLBACK: /(英雄杀|英雄sha)/,

  // 可选：登号器包名。知道就填，稳定性更高；留空则靠页面元素判断
  LOGIN_HELPER_PACKAGE: "",

  // 轮询无任务时等待间隔
  POLL_INTERVAL_MS: 5000,

  // 心跳间隔（续租任务，防止服务端把任务回收）
  HEARTBEAT_INTERVAL_MS: 60000,

  // 单步默认等待超时
  ACTION_TIMEOUT_MS: 15000,

  // 启动后识别「登录页 / 大厅」的窗口，放宽避免慢启动误判
  ENTRY_DETECT_TIMEOUT_MS: 45000,

  // 坐标优先模式：true 时弱化文字识别，避免自绘层识别失败导致中断
  COORD_ONLY_MODE: true,

  // 坐标模式下，登录后固定等待进入大厅的时长
  WAIT_AFTER_LOGIN_MS: 12000,

  // 点击后短等待，给 UI 渲染时间
  PAGE_WAIT_MS: 1200,

  // 点击兜底坐标（比例值 0~1，基于当前屏幕宽高）
  COORD_FALLBACK: {
    QQ_LOGIN: { x: 0.69, y: 0.84 },
    AGREEMENT_CHECKBOX: { x: 0.03, y: 0.92 },
    LOGIN_HELPER_ACCOUNT_INPUT: { x: 0.50, y: 0.49 },
    LOGIN_HELPER_OP_BTN: { x: 0.50, y: 0.84 },
    FRIEND_BTN: { x: 0.04, y: 0.79 },
    LEFT_SECOND_BTN: { x: 0.05, y: 0.36 },
    REUNION_INPUT: { x: 0.50, y: 0.56 },
    REUNION_CONFIRM: { x: 0.50, y: 0.76 },
    PROFILE_BTN: { x: 0.05, y: 0.07 },
    SWITCH_ACCOUNT_BTN: { x: 0.18, y: 0.68 }
  },

  // 绝对像素坐标，优先于比例坐标
  ABS_COORD: {
    AGREEMENT_CHECKBOX: { x: 81, y: 679 },
    QQ_LOGIN: { x: 846, y: 597 },
    FRIEND_BTN: { x: 56, y: 590 },
    LEFT_SECOND_BTN: { x: 58, y: 258 },
    REUNION_INPUT: { x: 639, y: 386 },
    REUNION_CONFIRM: { x: 635, y: 536 },
    PAGE_BACK: { x: 64, y: 35 },
    PROFILE_BTN: { x: 58, y: 35 },
    SWITCH_ACCOUNT_BTN: { x: 218, y: 486 },
    ACCEPT_INVITE: { x: 1044, y: 147 },
    POPUP_CLOSE: { x: 1081, y: 147 },
    LOGIN_HELPER_ACCOUNT_INPUT: { x: 385, y: 503 },
    LOGIN_HELPER_OP_BTN: { x: 380, y: 1034 }
  },

  /**
   * 页面文案锚点。建议用 Auto.js 布局分析核对后再改。
   */
  SELECTORS: {
    LOGIN_PAGE: /(QQ登录|微信登录|游客登录|快速登录|二维码登录|用户协议|隐私政策|我已经详细阅读并同意)/,
    QQ_LOGIN_BTN: /(QQ登录)/,
    AGREEMENT_CHECKBOX: /(同意|已阅读|用户协议|隐私政策|我已经详细阅读并同意)/,
    LOGIN_HELPER_ACCOUNT_HINT: /(账号|QQ号|请输入账号|token|TOKEN)/,
    LOGIN_HELPER_OP_BTN: /(输入OP数据点我授权|点我授权|OP数据|授权|OP|登录|确定|确认)/,
    LOBBY_MARK: /(好友|商城|排位|活动|新手签到|新手任务|召唤)/,
    FRIEND_BTN: /(好友)/,
    LEFT_SECOND_BTN_TEXT: /(广结好友|重逢|召回|回归|老友|换一批)/,
    REUNION_CODE_INPUT_HINT: /(重逢码|邀请码|兑换码|请输入)/,
    REUNION_CONFIRM_BTN: /(确认绑定|确定|提交|兑换|确认)/,
    RETRY_POPUP_CLOSE: /(关闭|取消|知道了|X)/,
    PROFILE_BTN: /(头像|个人中心|我的|个人信息)/,
    SWITCH_ACCOUNT_BTN: /(切换账号|退出登录|注销|切换帐号)/,
    BACK_TO_LOGIN_MARK: /(QQ登录|微信登录|游客登录|快速登录|二维码登录)/
  }
};

// 当前任务入口状态：login | lobby | unknown
const FLOW_STATE = {
  entryState: "unknown"
};

auto.waitFor();
console.show();
log("英雄杀自动化脚本启动。");

let screenshotEnabled = false;
try {
  screenshotEnabled = requestScreenCapture(false);
} catch (e) {
  screenshotEnabled = false;
}

/** 构造请求头。 */
function buildHeaders() {
  const h = { "Content-Type": "application/json" };
  if (CONFIG.API_KEY && CONFIG.API_KEY.length > 0) {
    h["X-API-Key"] = CONFIG.API_KEY;
  }
  return h;
}

/** 安全解析 JSON，防止后端异常内容直接崩脚本。 */
function safeJsonParse(raw) {
  try {
    return JSON.parse(raw);
  } catch (e) {
    return null;
  }
}

/** 统一 POST JSON。 */
function httpPostJson(path, payload) {
  const url = CONFIG.SERVER_BASE + path;
  const res = http.postJson(url, payload, { headers: buildHeaders() });
  if (!res) throw new Error("HTTP 请求失败: " + path);
  const body = res.body ? res.body.string() : "";
  return { statusCode: res.statusCode, bodyRaw: body, json: safeJsonParse(body) };
}

/** 统一 GET。 */
function httpGet(path) {
  const url = CONFIG.SERVER_BASE + path;
  const res = http.get(url, { headers: buildHeaders() });
  if (!res) throw new Error("HTTP 请求失败: " + path);
  const body = res.body ? res.body.string() : "";
  return { statusCode: res.statusCode, bodyRaw: body, json: safeJsonParse(body) };
}

function sleepShort(ms) {
  sleep(ms || CONFIG.PAGE_WAIT_MS);
}

/**
 * 本机设备名。CONFIG.DEVICE_ID 有值则用配置；否则用 AndroidId。
 * 不用 files / storages，Auto.js 6 访问这两类对象容易报「无效的对象属性」。
 */
var cachedDeviceId = "";
function getDeviceId() {
  if (cachedDeviceId) {
    return cachedDeviceId;
  }
  var configured = String(CONFIG.DEVICE_ID || "").trim();
  if (configured && configured.toLowerCase() !== "auto") {
    cachedDeviceId = configured;
    return cachedDeviceId;
  }
  var androidId = "";
  try {
    androidId = String(device.getAndroidId() || "").trim();
  } catch (e) {
    androidId = "";
  }
  if (androidId && androidId !== "null" && androidId.toLowerCase() !== "unknown") {
    cachedDeviceId = "phone-" + androidId;
    return cachedDeviceId;
  }
  cachedDeviceId = "phone-" + Date.now();
  return cachedDeviceId;
}

/** 点击节点中心点。 */
function clickCenter(node) {
  if (!node) return false;
  const b = node.bounds();
  return click(b.centerX(), b.centerY());
}

/** 按屏幕比例点击（自绘页兜底）。 */
function tapByRatioPoint(point, tag) {
  if (!point) return false;
  const x = Math.floor(device.width * point.x);
  const y = Math.floor(device.height * point.y);
  const ok = click(x, y);
  if (ok) {
    log("坐标兜底点击[" + (tag || "unknown") + "] -> (" + x + "," + y + ")");
    sleepShort();
  }
  return ok;
}

/** 按绝对像素坐标点击。 */
function tapByAbsolutePoint(point, tag) {
  if (!point) return false;
  const ok = click(point.x, point.y);
  if (ok) {
    log("绝对坐标点击[" + (tag || "unknown") + "] -> (" + point.x + "," + point.y + ")");
    sleepShort();
  }
  return ok;
}

function tapByTextRegex(regex, timeoutMs) {
  const node = textMatches(regex).findOne(timeoutMs || 1000);
  if (!node) return false;
  const ok = clickCenter(node);
  if (ok) sleepShort();
  return ok;
}

function tapByDescRegex(regex, timeoutMs) {
  const node = descMatches(regex).findOne(timeoutMs || 1000);
  if (!node) return false;
  const ok = clickCenter(node);
  if (ok) sleepShort();
  return ok;
}

/** 综合点击：text 优先，desc 兜底。 */
function tapByRegex(regex, timeoutMs) {
  return tapByTextRegex(regex, timeoutMs) || tapByDescRegex(regex, timeoutMs);
}

/** 先文案，再绝对坐标，最后比例坐标。 */
function tapWithFallbackEx(regex, absPoint, ratioPoint, tag, timeoutMs) {
  const ok = tapByRegex(regex, timeoutMs);
  if (ok) return true;
  if (tapByAbsolutePoint(absPoint, tag + "-abs")) return true;
  return tapByRatioPoint(ratioPoint, tag + "-ratio");
}

/** 等待页面锚点出现。 */
function waitByRegex(regex, timeoutMs) {
  const timeout = timeoutMs || CONFIG.ACTION_TIMEOUT_MS;
  if (textMatches(regex).findOne(timeout)) return true;
  return !!descMatches(regex).findOne(300);
}

function existsByRegex(regex) {
  return textMatches(regex).exists() || descMatches(regex).exists();
}

function isLoginPageNow() {
  return existsByRegex(CONFIG.SELECTORS.LOGIN_PAGE);
}

function isLobbyNow() {
  return existsByRegex(CONFIG.SELECTORS.LOBBY_MARK);
}

/**
 * 启动后页面探测：
 * - login: 明确看到登录页
 * - lobby: 明确看到大厅（可能已自动登录）
 * - unknown: 两者都识别不到（自绘层常见）
 */
function detectEntryState(timeoutMs) {
  const deadline = new Date().getTime() + timeoutMs;
  while (new Date().getTime() < deadline) {
    if (isLobbyNow()) return "lobby";
    if (isLoginPageNow()) return "login";
    sleep(700);
  }
  return "unknown";
}

function alreadyInLobby() {
  return FLOW_STATE.entryState === "lobby" || isLobbyNow();
}

/** 关闭启动时常见弹窗。 */
function closeCommonPopups(rounds) {
  const n = rounds || 6;
  for (let i = 0; i < n; i++) {
    const acted = tapByRegex(/(同意|允许|确认|继续|跳过|知道了|关闭|X)/, 600);
    if (!acted) break;
  }
}

/**
 * 查找输入框：
 * 1) 直接找 EditText
 * 2) 通过 hint 文案向父级回溯再找 EditText
 */
function findInputByHintRegex(regex, timeoutMs) {
  const deadline = new Date().getTime() + (timeoutMs || 6000);
  while (new Date().getTime() < deadline) {
    const direct = className("android.widget.EditText").findOne(500);
    if (direct) return direct;

    const hintNode = textMatches(regex).findOne(300) || descMatches(regex).findOne(300);
    if (hintNode && hintNode.parent()) {
      const parentNode = hintNode.parent();
      const candidate = parentNode.findOne(className("android.widget.EditText"));
      if (candidate) return candidate;
    }
  }
  return null;
}

/** 安全填值：优先节点 setText，失败再全局 setText。 */
function setInputText(inputNode, value) {
  if (!inputNode) return false;
  inputNode.click();
  sleepShort(300);
  try {
    inputNode.setText(value);
    sleepShort(300);
    return true;
  } catch (e) {
    try {
      setText(value);
      sleepShort(300);
      return true;
    } catch (e2) {
      return false;
    }
  }
}

function setInputTextByPoint(point, value, tag) {
  if (!tapByRatioPoint(point, tag)) return false;
  sleepShort(300);
  try {
    setText(value);
    sleepShort(500);
    return true;
  } catch (e) {
    return false;
  }
}

function setInputTextByPointAbs(point, value, tag) {
  if (!tapByAbsolutePoint(point, tag)) return false;
  sleepShort(300);
  try {
    setText(value);
    sleepShort(500);
    return true;
  } catch (e) {
    return false;
  }
}

function setInputTextBySmartPoint(absPoint, ratioPoint, value, tag) {
  if (setInputTextByPointAbs(absPoint, value, tag + "-abs")) return true;
  return setInputTextByPoint(ratioPoint, value, tag + "-ratio");
}

/**
 * 等待登号器：
 * - 配置了包名则先等包名切换
 * - 否则等账号输入框或 OP 按钮
 */
function waitLoginHelperReady() {
  if (CONFIG.LOGIN_HELPER_PACKAGE && CONFIG.LOGIN_HELPER_PACKAGE.length > 0) {
    const ok = waitForPackage(CONFIG.LOGIN_HELPER_PACKAGE, 8000);
    if (!ok) {
      log("未检测到登号器包名，继续尝试页面元素识别。");
    } else {
      return true;
    }
  }
  if (findInputByHintRegex(CONFIG.SELECTORS.LOGIN_HELPER_ACCOUNT_HINT, 8000)) {
    return true;
  }
  if (waitByRegex(CONFIG.SELECTORS.LOGIN_HELPER_OP_BTN, 3000)) {
    return true;
  }
  if (!waitByRegex(CONFIG.SELECTORS.LOBBY_MARK, 1000)) {
    log("未识别到登号器输入框，按兜底路径继续。");
    return true;
  }
  return false;
}

/** 失败截图，便于回看为什么没点到。 */
function saveFailureScreenshot(taskId) {
  if (!screenshotEnabled) return;
  try {
    const image = captureScreen();
    if (!image) return;
    const path = "/sdcard/Download/hs_task_failed_" + taskId + ".png";
    images.save(image, path, "png", 100);
    log("失败截图已保存: " + path);
  } catch (e) {
    log("截图失败: " + e);
  }
}

/** 心跳线程：防止任务在服务端因租约超时被回收。 */
function startHeartbeatLoop(taskId, runId, stopRef) {
  return threads.start(function () {
    while (!stopRef.stop) {
      sleep(CONFIG.HEARTBEAT_INTERVAL_MS);
      if (stopRef.stop) break;
      try {
        const res = httpPostJson("/tasks/" + taskId + "/heartbeat", {
          deviceId: getDeviceId(),
          runId: runId
        });
        log("heartbeat: " + res.bodyRaw);
      } catch (e) {
        log("heartbeat 异常: " + e);
      }
    }
  });
}

/** 领取任务：只领英雄杀-结义。返回 {id, account, reunionCode, runId}，队列空则返回 null。 */
function claimTask() {
  const res = httpPostJson("/tasks/claim", {
    deviceId: getDeviceId(),
    gameCode: CONFIG.GAME_CODE,
    taskType: CONFIG.TASK_TYPE
  });
  if (res.statusCode !== 200 || !res.json) {
    throw new Error("领取任务失败: " + res.bodyRaw);
  }
  return res.json.task;
}

/** 上报任务结果。 */
function reportTask(taskId, runId, status, errorMsg) {
  const payload = {
    deviceId: getDeviceId(),
    runId: runId,
    status: status
  };
  if (errorMsg) payload.error = errorMsg;
  const res = httpPostJson("/tasks/" + taskId + "/report", payload);
  log("report: " + res.bodyRaw);
}

/**
 * =========================
 * 13 步业务流程
 * =========================
 */

function step01LaunchGame() {
  log("Step1 打开游戏");
  let launched = launchApp(CONFIG.GAME_APP_NAME);
  if (!launched) {
    log("launchApp 失败，尝试点击桌面图标兜底。");
    launched = tapByRegex(CONFIG.GAME_ICON_TEXT_FALLBACK, 4000);
  }
  if (!launched) {
    throw new Error("无法启动游戏（应用名与图标文案都未命中）。");
  }
  sleep(8000);
  closeCommonPopups(8);
}

function step02EnsureLoginPage() {
  if (CONFIG.COORD_ONLY_MODE) {
    log("Step2 坐标模式：跳过登录页识别，固定等待页面稳定。");
    FLOW_STATE.entryState = "unknown";
    sleep(5000);
    return;
  }

  log("Step2 识别入口状态（登录页/大厅）");
  const state = detectEntryState(CONFIG.ENTRY_DETECT_TIMEOUT_MS);
  FLOW_STATE.entryState = state;

  if (state === "lobby") {
    log("已检测到大厅，判定为自动登录，后续跳过登录与登号器步骤。");
    return;
  }
  if (state === "login") {
    log("已检测到登录页。");
    return;
  }
  log("未识别到登录页/大厅锚点，继续执行（将依赖坐标兜底）。");
}

function step03AgreeProtocol() {
  if (CONFIG.COORD_ONLY_MODE) {
    log("Step3 坐标模式：直接勾选协议。");
    tapWithFallbackEx(
      CONFIG.SELECTORS.AGREEMENT_CHECKBOX,
      CONFIG.ABS_COORD.AGREEMENT_CHECKBOX,
      CONFIG.COORD_FALLBACK.AGREEMENT_CHECKBOX,
      "协议勾选",
      1500
    );
    return;
  }

  if (alreadyInLobby()) {
    log("Step3 跳过：当前已在大厅，无需勾选协议。");
    return;
  }
  log("Step3 勾选协议");
  tapWithFallbackEx(
    CONFIG.SELECTORS.AGREEMENT_CHECKBOX,
    CONFIG.ABS_COORD.AGREEMENT_CHECKBOX,
    CONFIG.COORD_FALLBACK.AGREEMENT_CHECKBOX,
    "协议勾选",
    3000
  );
}

function step04TapQqLogin() {
  if (CONFIG.COORD_ONLY_MODE) {
    log("Step4 坐标模式：直接点击QQ登录。");
    if (!tapWithFallbackEx(
      CONFIG.SELECTORS.QQ_LOGIN_BTN,
      CONFIG.ABS_COORD.QQ_LOGIN,
      CONFIG.COORD_FALLBACK.QQ_LOGIN,
      "QQ登录",
      1500
    )) {
      throw new Error("坐标模式下点击QQ登录失败。");
    }
    return;
  }

  if (alreadyInLobby()) {
    log("Step4 跳过：当前已在大厅，无需点 QQ 登录。");
    return;
  }
  log("Step4 点击 QQ 登录");
  if (!tapWithFallbackEx(
    CONFIG.SELECTORS.QQ_LOGIN_BTN,
    CONFIG.ABS_COORD.QQ_LOGIN,
    CONFIG.COORD_FALLBACK.QQ_LOGIN,
    "QQ登录",
    6000
  )) {
    throw new Error("未找到 QQ 登录按钮。");
  }
}

function step05WaitLoginHelper() {
  if (CONFIG.COORD_ONLY_MODE) {
    log("Step5 坐标模式：跳过登号器识别，固定等待。");
    sleep(2500);
    return;
  }

  if (alreadyInLobby()) {
    log("Step5 跳过：当前已在大厅，无需等待登号器。");
    return;
  }
  log("Step5 等待登号器拉起");
  if (!waitLoginHelperReady()) {
    throw new Error("登号器未拉起或页面元素不可识别。");
  }
}

function step06InputAccountAndSubmit(account) {
  if (CONFIG.COORD_ONLY_MODE) {
    log("Step6 坐标模式：直接填 token 并点击授权。");
    if (!setInputTextBySmartPoint(
      CONFIG.ABS_COORD.LOGIN_HELPER_ACCOUNT_INPUT,
      CONFIG.COORD_FALLBACK.LOGIN_HELPER_ACCOUNT_INPUT,
      account,
      "账号输入框兜底"
    )) {
      throw new Error("坐标模式下账号/token输入失败。");
    }
    if (!tapWithFallbackEx(
      CONFIG.SELECTORS.LOGIN_HELPER_OP_BTN,
      CONFIG.ABS_COORD.LOGIN_HELPER_OP_BTN,
      CONFIG.COORD_FALLBACK.LOGIN_HELPER_OP_BTN,
      "输入OP数据点我授权",
      1500
    )) {
      throw new Error("坐标模式下未点到OP授权按钮。");
    }
    sleep(6000);
    return;
  }

  if (alreadyInLobby()) {
    log("Step6 跳过：当前已在大厅，无需填写账号。");
    return;
  }

  log("Step6 填写账号/token 并点击授权");
  const accountInput = findInputByHintRegex(CONFIG.SELECTORS.LOGIN_HELPER_ACCOUNT_HINT, 10000);
  if (accountInput) {
    if (!setInputText(accountInput, account)) throw new Error("账号填写失败。");
  } else if (!setInputTextBySmartPoint(
    CONFIG.ABS_COORD.LOGIN_HELPER_ACCOUNT_INPUT,
    CONFIG.COORD_FALLBACK.LOGIN_HELPER_ACCOUNT_INPUT,
    account,
    "账号输入框兜底"
  )) {
    throw new Error("未找到账号输入框，且坐标兜底输入失败。");
  }
  if (!tapWithFallbackEx(
    CONFIG.SELECTORS.LOGIN_HELPER_OP_BTN,
    CONFIG.ABS_COORD.LOGIN_HELPER_OP_BTN,
    CONFIG.COORD_FALLBACK.LOGIN_HELPER_OP_BTN,
    "输入OP数据点我授权",
    8000
  )) {
    throw new Error("未找到「输入OP数据点我授权」按钮。");
  }
  sleep(6000);
}

function step07WaitLobby() {
  if (CONFIG.COORD_ONLY_MODE) {
    log("Step7 坐标模式：固定等待进入大厅。");
    sleep(CONFIG.WAIT_AFTER_LOGIN_MS);
    return;
  }

  if (alreadyInLobby()) {
    log("Step7 已在大厅，无需等待。");
    return;
  }
  log("Step7 等待进入大厅");
  if (!waitByRegex(CONFIG.SELECTORS.LOBBY_MARK, 25000)) {
    throw new Error("登录后未进入大厅。");
  }
}

function step08OpenFriendPage() {
  log("Step8 打开好友页");
  if (CONFIG.COORD_ONLY_MODE) {
    tapByAbsolutePoint(CONFIG.ABS_COORD.FRIEND_BTN, "好友按钮-abs-1");
    sleep(2200);
    return;
  }

  if (!tapWithFallbackEx(
    CONFIG.SELECTORS.FRIEND_BTN,
    CONFIG.ABS_COORD.FRIEND_BTN,
    CONFIG.COORD_FALLBACK.FRIEND_BTN,
    "好友按钮",
    8000
  )) {
    throw new Error("未找到好友按钮。");
  }
  sleep(2200);
}

function step09TapLeftSecondButton() {
  log("Step9 点击左侧第二个按钮");
  if (CONFIG.COORD_ONLY_MODE) {
    sleep(3000);
    tapByAbsolutePoint(CONFIG.ABS_COORD.LEFT_SECOND_BTN, "左侧第二按钮");
    sleep(1200);
    if (!waitByRegex(CONFIG.SELECTORS.REUNION_CONFIRM_BTN, 1500)) {
      log("Step9 未检测到重逢弹窗，尝试点击【接受邀请】");
      tapByAbsolutePoint(CONFIG.ABS_COORD.ACCEPT_INVITE, "接受邀请");
      sleep(1500);
    }
    return;
  }

  if (!tapWithFallbackEx(
    CONFIG.SELECTORS.LEFT_SECOND_BTN_TEXT,
    CONFIG.ABS_COORD.LEFT_SECOND_BTN,
    CONFIG.COORD_FALLBACK.LEFT_SECOND_BTN,
    "左侧第二按钮",
    7000
  )) {
    throw new Error("未找到左侧第二个目标按钮。");
  }
  if (!waitByRegex(CONFIG.SELECTORS.REUNION_CONFIRM_BTN, 1500)) {
    log("Step9 未检测到重逢弹窗，尝试点击【接受邀请】");
    tapByAbsolutePoint(CONFIG.ABS_COORD.ACCEPT_INVITE, "接受邀请");
    sleep(1500);
  }
}

function step10InputReunionCodeAndConfirm(reunionCode) {
  log("Step10 填重逢码并确认");
  const codeInput = findInputByHintRegex(CONFIG.SELECTORS.REUNION_CODE_INPUT_HINT, 10000);
  let filled = false;
  if (codeInput) {
    filled = setInputText(codeInput, reunionCode);
  } else {
    filled = setInputTextBySmartPoint(
      CONFIG.ABS_COORD.REUNION_INPUT,
      CONFIG.COORD_FALLBACK.REUNION_INPUT,
      reunionCode,
      "重逢码输入框"
    );
  }
  if (!filled) throw new Error("重逢码填写失败。");
  if (!tapWithFallbackEx(
    CONFIG.SELECTORS.REUNION_CONFIRM_BTN,
    CONFIG.ABS_COORD.REUNION_CONFIRM,
    CONFIG.COORD_FALLBACK.REUNION_CONFIRM,
    "确认绑定",
    6000
  )) {
    throw new Error("未找到重逢码确认按钮。");
  }
}

function step11CloseRetryPopup() {
  log("Step11 关闭重试弹窗（若出现）");
  if (!tapByRegex(CONFIG.SELECTORS.RETRY_POPUP_CLOSE, 2500)) {
    tapByAbsolutePoint(CONFIG.ABS_COORD.POPUP_CLOSE, "弹窗关闭按钮");
  }
  sleepShort(800);
}

function step12SwitchAccountFromProfile() {
  log("Step12 个人中心切换账号");
  const backClicked = tapByAbsolutePoint(CONFIG.ABS_COORD.PAGE_BACK, "页面返回按钮");
  if (!backClicked) {
    back();
  }
  sleep(1800);

  if (!tapWithFallbackEx(
    CONFIG.SELECTORS.PROFILE_BTN,
    CONFIG.ABS_COORD.PROFILE_BTN,
    CONFIG.COORD_FALLBACK.PROFILE_BTN,
    "个人中心",
    7000
  )) {
    throw new Error("未找到个人中心入口。");
  }
  sleep(2000);
  if (!tapWithFallbackEx(
    CONFIG.SELECTORS.SWITCH_ACCOUNT_BTN,
    CONFIG.ABS_COORD.SWITCH_ACCOUNT_BTN,
    CONFIG.COORD_FALLBACK.SWITCH_ACCOUNT_BTN,
    "切换账号",
    7000
  )) {
    throw new Error("未找到切换账号按钮。");
  }
}

function step13EnsureBackToLoginPage() {
  if (CONFIG.COORD_ONLY_MODE) {
    log("Step13 坐标模式：跳过登录页文本校验，等待页面切换完成。");
    sleep(3000);
    return;
  }

  log("Step13 校验回到登录页");
  if (!waitByRegex(CONFIG.SELECTORS.BACK_TO_LOGIN_MARK, 15000)) {
    throw new Error("切换账号后未回到登录页。");
  }
}

/** 单任务执行入口。任一步抛错都会被外层捕获并上报 failed。 */
function runFlowForTask(task) {
  FLOW_STATE.entryState = "unknown";
  const account = task.account;
  const reunionCode = task.reunionCode;

  step01LaunchGame();
  step02EnsureLoginPage();
  step03AgreeProtocol();
  step04TapQqLogin();
  step05WaitLoginHelper();
  step06InputAccountAndSubmit(account);
  step07WaitLobby();
  step08OpenFriendPage();
  step09TapLeftSecondButton();
  step10InputReunionCodeAndConfirm(reunionCode);
  step11CloseRetryPopup();
  step12SwitchAccountFromProfile();
  step13EnsureBackToLoginPage();
}

/**
 * 主循环：健康检查 -> 领取任务 -> 执行 -> 上报 -> 再领下一条。
 */
function mainLoop() {
  log("本机设备名: " + getDeviceId());
  const health = httpGet("/health");
  log("服务健康检查: " + health.bodyRaw);

  while (true) {
    let task = null;
    try {
      task = claimTask();
    } catch (e) {
      log("领取任务异常: " + e);
      sleep(CONFIG.POLL_INTERVAL_MS);
      continue;
    }

    if (!task) {
      log("暂无任务，等待下次轮询...");
      sleep(CONFIG.POLL_INTERVAL_MS);
      continue;
    }

    log("已领取任务 id=" + task.id + " account=" + task.account);

    const stopRef = { stop: false };
    const heartbeatThread = startHeartbeatLoop(task.id, task.runId, stopRef);
    let finalStatus = "done";
    let finalError = "";

    try {
      runFlowForTask(task);
      toast("任务完成: " + task.id);
      log("任务完成: " + task.id);
    } catch (e) {
      finalStatus = "failed";
      finalError = String(e);
      toast("任务失败: " + task.id);
      log("任务失败: " + finalError);
      saveFailureScreenshot(task.id);
    } finally {
      stopRef.stop = true;
      try {
        heartbeatThread.interrupt();
      } catch (ignore) {}
    }

    try {
      reportTask(task.id, task.runId, finalStatus, finalError);
    } catch (e) {
      log("结果上报异常: " + e);
    }

    sleep(1200);
  }
}

mainLoop();
