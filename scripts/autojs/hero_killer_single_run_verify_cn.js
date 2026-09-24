"auto";

/**
 * 英雄杀单账号单次验证脚本（无后端）
 * ------------------------------------------------------------
 * 用途：仅用于“先验证流程能不能跑通”。
 * 特性：
 * - 不连接后端
 * - 只执行 1 个账号 + 1 个重逢码
 * - 跑完后自动退出
 */

const CONFIG = {
  // ===== 手动填写这两个值 =====
  // 登录凭证：这里填“账号 token”
  LOGIN_TOKEN: "864843B844DEC3A7D57C3C80514CEE08|D4EC0A89ECF5936CFA2B33C137454D88|9C8931F3714917638D6D81FDFE371F66|2510623798172dc4187d92c26f7c42fa|1789636379769",
  REUNION_CODE: "74061c8f23",

  // 游戏 App 名称
  GAME_APP_NAME: "英雄杀",
  // 桌面图标兜底（launchApp 失败时）
  GAME_ICON_TEXT_FALLBACK: /(英雄杀|英雄sha)/,
  // 可选：登号器包名（知道就填，稳定性更高）
  LOGIN_HELPER_PACKAGE: "",

  ACTION_TIMEOUT_MS: 15000,
  // 坐标优先模式：true 时弱化页面文字识别，避免自绘层识别失败导致中断
  COORD_ONLY_MODE: true,
  // 启动后“登录页/大厅”识别窗口，放宽到 45 秒避免慢启动误判
  ENTRY_DETECT_TIMEOUT_MS: 45000,
  // 坐标模式下，登录后固定等待进入大厅时长
  WAIT_AFTER_LOGIN_MS: 12000,
  PAGE_WAIT_MS: 1200,

  // 坐标兜底（比例值 0~1）
  COORD_FALLBACK: {
    QQ_LOGIN: { x: 0.69, y: 0.84 },
    AGREEMENT_CHECKBOX: { x: 0.03, y: 0.92 },
    // 上号器 token 输入区域（你提供截图为竖屏页）
    LOGIN_HELPER_TOKEN_INPUT: { x: 0.50, y: 0.49 },
    // 上号器「输入OP数据点我授权」按钮
    LOGIN_HELPER_OP_AUTH_BTN: { x: 0.50, y: 0.84 },
    FRIEND_BTN: { x: 0.04, y: 0.79 },
    LEFT_SECOND_BTN: { x: 0.05, y: 0.36 },
    REUNION_INPUT: { x: 0.50, y: 0.56 },
    REUNION_CONFIRM: { x: 0.50, y: 0.76 },
    PROFILE_BTN: { x: 0.05, y: 0.07 },
    SWITCH_ACCOUNT_BTN: { x: 0.18, y: 0.68 }
  },

  // 你提供的绝对坐标（像素），脚本会优先使用
  ABS_COORD: {
    // 横屏页面
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

    // 竖屏上号器页面
    LOGIN_HELPER_TOKEN_INPUT: { x: 385, y: 503 },
    LOGIN_HELPER_OP_AUTH_BTN: { x: 380, y: 1034 }
  },

  // 页面文案锚点（结合你的截图调过）
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

// 流程状态记录（用于“已在大厅则跳过登录步骤”）
const FLOW_STATE = {
  entryState: "unknown" // login | lobby | unknown
};

auto.waitFor();
console.show();
log("单次验证脚本启动。");

let screenshotEnabled = false;
try {
  screenshotEnabled = requestScreenCapture(false);
} catch (e) {
  screenshotEnabled = false;
}

function sleepShort(ms) {
  sleep(ms || CONFIG.PAGE_WAIT_MS);
}

function clickCenter(node) {
  if (!node) return false;
  const b = node.bounds();
  return click(b.centerX(), b.centerY());
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

function tapByRegex(regex, timeoutMs) {
  return tapByTextRegex(regex, timeoutMs) || tapByDescRegex(regex, timeoutMs);
}

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

function tapByAbsolutePoint(point, tag) {
  if (!point) return false;
  const ok = click(point.x, point.y);
  if (ok) {
    log("绝对坐标点击[" + (tag || "unknown") + "] -> (" + point.x + "," + point.y + ")");
    sleepShort();
  }
  return ok;
}

function tapWithFallback(regex, point, tag, timeoutMs) {
  const ok = tapByRegex(regex, timeoutMs);
  if (ok) return true;
  return tapByRatioPoint(point, tag);
}

function tapWithFallbackEx(regex, absPoint, ratioPoint, tag, timeoutMs) {
  const ok = tapByRegex(regex, timeoutMs);
  if (ok) return true;
  if (tapByAbsolutePoint(absPoint, tag + "-abs")) return true;
  return tapByRatioPoint(ratioPoint, tag + "-ratio");
}

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
 * - login: 明确看到登录页锚点
 * - lobby: 明确看到大厅锚点（可能自动登录）
 * - unknown: 两者都识别不到（游戏自绘层常见）
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

function closeCommonPopups(rounds) {
  const n = rounds || 6;
  for (let i = 0; i < n; i++) {
    const acted = tapByRegex(/(同意|允许|确认|继续|跳过|知道了|关闭|X)/, 600);
    if (!acted) break;
  }
}

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

function saveFailureScreenshot() {
  if (!screenshotEnabled) return;
  try {
    const image = captureScreen();
    if (!image) return;
    const path = "/sdcard/Download/hs_single_run_failed.png";
    images.save(image, path, "png", 100);
    log("失败截图已保存: " + path);
  } catch (e) {
    log("截图失败: " + e);
  }
}

// Step 1
function step01LaunchGame() {
  log("Step1 打开游戏");
  let launched = launchApp(CONFIG.GAME_APP_NAME);
  if (!launched) {
    log("launchApp 失败，尝试点击桌面图标兜底。");
    launched = tapByRegex(CONFIG.GAME_ICON_TEXT_FALLBACK, 4000);
  }
  if (!launched) throw new Error("无法启动游戏。");
  sleep(8000);
  closeCommonPopups(8);
}

// Step 2
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
  // unknown 不再直接失败，继续执行坐标兜底链路
  log("未识别到登录页/大厅锚点，继续执行（将依赖坐标兜底）。");
}

// Step 3
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

  if (FLOW_STATE.entryState === "lobby" || isLobbyNow()) {
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

// Step 4
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

  if (FLOW_STATE.entryState === "lobby" || isLobbyNow()) {
    log("Step4 跳过：当前已在大厅，无需点QQ登录。");
    return;
  }
  log("Step4 点击QQ登录");
  if (!tapWithFallbackEx(
    CONFIG.SELECTORS.QQ_LOGIN_BTN,
    CONFIG.ABS_COORD.QQ_LOGIN,
    CONFIG.COORD_FALLBACK.QQ_LOGIN,
    "QQ登录",
    6000
  )) {
    throw new Error("未找到QQ登录按钮。");
  }
}

// Step 5
function step05WaitLoginHelper() {
  if (CONFIG.COORD_ONLY_MODE) {
    log("Step5 坐标模式：跳过登号器识别，固定等待。");
    sleep(2500);
    return;
  }

  if (FLOW_STATE.entryState === "lobby" || isLobbyNow()) {
    log("Step5 跳过：当前已在大厅，无需等待登号器。");
    return;
  }
  log("Step5 等待登号器");
  if (!waitLoginHelperReady()) {
    throw new Error("登号器未拉起或不可识别。");
  }
}

// Step 6
function step06InputAccountAndSubmit() {
  if (CONFIG.COORD_ONLY_MODE) {
    log("Step6 坐标模式：直接填token并点击授权。");
    if (!setInputTextBySmartPoint(
      CONFIG.ABS_COORD.LOGIN_HELPER_TOKEN_INPUT,
      CONFIG.COORD_FALLBACK.LOGIN_HELPER_TOKEN_INPUT,
      CONFIG.LOGIN_TOKEN,
      "token输入"
    )) {
      throw new Error("坐标模式下 token 输入失败。");
    }
    if (!tapWithFallbackEx(
      CONFIG.SELECTORS.LOGIN_HELPER_OP_BTN,
      CONFIG.ABS_COORD.LOGIN_HELPER_OP_AUTH_BTN,
      CONFIG.COORD_FALLBACK.LOGIN_HELPER_OP_AUTH_BTN,
      "输入OP数据点我授权",
      1500
    )) {
      throw new Error("坐标模式下未点到OP授权按钮。");
    }
    sleep(6000);
    return;
  }

  if (FLOW_STATE.entryState === "lobby" || isLobbyNow()) {
    log("Step6 跳过：当前已在大厅，无需填写账号。");
    return;
  }
  log("Step6 填写token并点击授权");
  const accountInput = findInputByHintRegex(CONFIG.SELECTORS.LOGIN_HELPER_ACCOUNT_HINT, 10000);
  if (accountInput) {
    if (!setInputText(accountInput, CONFIG.LOGIN_TOKEN)) throw new Error("token填写失败。");
  } else {
    if (!setInputTextBySmartPoint(
      CONFIG.ABS_COORD.LOGIN_HELPER_TOKEN_INPUT,
      CONFIG.COORD_FALLBACK.LOGIN_HELPER_TOKEN_INPUT,
      CONFIG.LOGIN_TOKEN,
      "token输入"
    )) {
      throw new Error("未找到token输入框，且坐标兜底输入失败。");
    }
  }
  if (!tapWithFallbackEx(
    CONFIG.SELECTORS.LOGIN_HELPER_OP_BTN,
    CONFIG.ABS_COORD.LOGIN_HELPER_OP_AUTH_BTN,
    CONFIG.COORD_FALLBACK.LOGIN_HELPER_OP_AUTH_BTN,
    "输入OP数据点我授权",
    8000
  )) {
    throw new Error("未找到「输入OP数据点我授权」按钮。");
  }
  sleep(6000);
}

// Step 7
function step07WaitLobby() {
  if (CONFIG.COORD_ONLY_MODE) {
    log("Step7 坐标模式：固定等待进入大厅。");
    sleep(CONFIG.WAIT_AFTER_LOGIN_MS);
    return;
  }

  log("Step7 等待进入大厅");
  if (waitByRegex(CONFIG.SELECTORS.LOBBY_MARK, 25000)) {
    return;
  }
  // 大厅也可能是自绘层无法抓 text/desc，这里放宽，交给后续步骤验证。
  log("未识别到大厅锚点，继续尝试后续步骤（坐标兜底）。");
}

// Step 8
function step08OpenFriendPage() {
  log("Step8 打开好友页");
  if (CONFIG.COORD_ONLY_MODE) {
    // 按最新实测，左下好友点击一次即可
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

// Step 9
function step09TapLeftSecondButton() {
  log("Step9 点击左侧第二按钮");
  if (CONFIG.COORD_ONLY_MODE) {
    // 按你的要求：延迟3秒后点击一次准确坐标
    sleep(3000);
    tapByAbsolutePoint(CONFIG.ABS_COORD.LEFT_SECOND_BTN, "左侧第二按钮");
    sleep(1200);
    // 若点击广结好友后没出弹窗，则补点「接受邀请」
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
  // 若点击广结好友后没出弹窗，则补点「接受邀请」
  if (!waitByRegex(CONFIG.SELECTORS.REUNION_CONFIRM_BTN, 1500)) {
    log("Step9 未检测到重逢弹窗，尝试点击【接受邀请】");
    tapByAbsolutePoint(CONFIG.ABS_COORD.ACCEPT_INVITE, "接受邀请");
    sleep(1500);
  }
}

// Step 10
function step10InputReunionCodeAndConfirm() {
  log("Step10 填重逢码并确认");
  const codeInput = findInputByHintRegex(CONFIG.SELECTORS.REUNION_CODE_INPUT_HINT, 10000);
  let filled = false;
  if (codeInput) {
    filled = setInputText(codeInput, CONFIG.REUNION_CODE);
  } else {
    filled = setInputTextBySmartPoint(
      CONFIG.ABS_COORD.REUNION_INPUT,
      CONFIG.COORD_FALLBACK.REUNION_INPUT,
      CONFIG.REUNION_CODE,
      "重逢码输入"
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

// Step 11
function step11CloseRetryPopup() {
  log("Step11 关闭重试弹窗");
  if (!tapByRegex(CONFIG.SELECTORS.RETRY_POPUP_CLOSE, 2500)) {
    tapByAbsolutePoint(CONFIG.ABS_COORD.POPUP_CLOSE, "弹窗关闭按钮");
  }
  sleepShort(800);
}

// Step 12
function step12SwitchAccountFromProfile() {
  log("Step12 个人中心切换账号");
  // 先点页面返回按钮（好友页 -> 大厅），失败时再用系统返回键兜底
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

// Step 13
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

function runOnce() {
  step01LaunchGame();
  step02EnsureLoginPage();
  step03AgreeProtocol();
  step04TapQqLogin();
  step05WaitLoginHelper();
  step06InputAccountAndSubmit();
  step07WaitLobby();
  step08OpenFriendPage();
  step09TapLeftSecondButton();
  step10InputReunionCodeAndConfirm();
  step11CloseRetryPopup();
  step12SwitchAccountFromProfile();
  step13EnsureBackToLoginPage();
}

function main() {
  try {
    runOnce();
    toast("单次验证完成");
    log("单次验证完成，脚本退出。");
  } catch (e) {
    toast("单次验证失败");
    log("单次验证失败: " + e);
    saveFailureScreenshot();
  }
  exit();
}

main();
