"auto";

/**
 * Hero Killer cloud-phone automation client (Auto.js)
 *
 * Workflow:
 * 1) Claim account + reunion code from Java server
 * 2) Run game flow:
 *    launch game -> QQ login -> agreement -> login helper -> game lobby
 *    -> friends -> reunion popup -> fill code -> confirm
 *    -> close retry popup -> back to lobby -> profile -> switch account
 * 3) Report success/failed to server
 *
 * IMPORTANT:
 * - Update selectors in CONFIG.SELECTORS to match your real UI.
 * - Some game pages are OpenGL and may require OCR/image matching fallback.
 */

/**
 * Runtime configuration.
 *
 * Tip:
 * - First adjust SERVER_BASE / DEVICE_ID / API_KEY.
 * - Then tune SELECTORS with your real UI text.
 */
const CONFIG = {
  SERVER_BASE: "http://127.0.0.1:8080/api/v1",
  API_KEY: "", // Optional. Set if server has API_KEY enabled.
  DEVICE_ID: "redfinger-01",
  GAME_APP_NAME: "英雄杀",
  POLL_INTERVAL_MS: 5000,
  HEARTBEAT_INTERVAL_MS: 60000,
  ACTION_TIMEOUT_MS: 15000,
  PAGE_WAIT_MS: 1200,
  SELECTORS: {
    LOGIN_PAGE: /(QQ登录|微信登录|游客登录|快速登录)/,
    QQ_LOGIN_BTN: /(QQ登录)/,
    AGREEMENT_CHECKBOX: /(同意|已阅读|用户协议|隐私政策)/,
    LOGIN_HELPER_ACCOUNT_HINT: /(账号|QQ号|请输入账号)/,
    LOGIN_HELPER_OP_BTN: /(OP|登录|确定|确认)/,
    LOBBY_MARK: /(好友|商城|排位|活动)/,
    FRIEND_BTN: /(好友)/,
    LEFT_SECOND_BTN_TEXT: /(重逢|召回|回归|老友)/,
    REUNION_CODE_INPUT_HINT: /(重逢码|邀请码|兑换码|请输入)/,
    REUNION_CONFIRM_BTN: /(确定|提交|兑换|确认)/,
    RETRY_POPUP_CLOSE: /(关闭|取消|知道了|X)/,
    PROFILE_BTN: /(头像|个人中心|我的)/,
    SWITCH_ACCOUNT_BTN: /(切换账号|退出登录|注销)/,
    BACK_TO_LOGIN_MARK: /(QQ登录|微信登录|游客登录|快速登录)/
  }
};

// Wait for Accessibility Service, otherwise text/desc queries cannot work.
auto.waitFor();
console.show();
log("Script started.");
let screenshotEnabled = false;
try {
  // Request screenshot once so failure snapshots can be saved for debugging.
  screenshotEnabled = requestScreenCapture(false);
} catch (e) {
  // Screenshot permission is optional; flow can still continue without it.
  screenshotEnabled = false;
}

/**
 * Build HTTP headers for all server requests.
 */
function headers() {
  const h = { "Content-Type": "application/json" };
  if (CONFIG.API_KEY && CONFIG.API_KEY.length > 0) {
    h["X-API-Key"] = CONFIG.API_KEY;
  }
  return h;
}

/**
 * Parse JSON safely to avoid script crash on malformed response.
 */
function safeJsonParse(raw) {
  try {
    return JSON.parse(raw);
  } catch (e) {
    return null;
  }
}

/**
 * POST JSON request helper.
 */
function httpPostJson(path, payload) {
  const url = CONFIG.SERVER_BASE + path;
  const res = http.postJson(url, payload, { headers: headers() });
  if (!res) {
    throw new Error("HTTP request failed: " + path);
  }
  const body = res.body ? res.body.string() : "";
  return {
    statusCode: res.statusCode,
    bodyRaw: body,
    json: safeJsonParse(body)
  };
}

/**
 * GET request helper.
 */
function httpGet(path) {
  const url = CONFIG.SERVER_BASE + path;
  const res = http.get(url, { headers: headers() });
  if (!res) {
    throw new Error("HTTP request failed: " + path);
  }
  const body = res.body ? res.body.string() : "";
  return {
    statusCode: res.statusCode,
    bodyRaw: body,
    json: safeJsonParse(body)
  };
}

// Unified short sleep; lets us tune pacing globally.
function sleepShort(ms) {
  sleep(ms || CONFIG.PAGE_WAIT_MS);
}

// Click the center point of a UI node.
function clickCenterOf(node) {
  if (!node) return false;
  const b = node.bounds();
  return click(b.centerX(), b.centerY());
}

// Prefer text selector first.
function tapByTextRegex(regex, timeoutMs) {
  const n = textMatches(regex).findOne(timeoutMs || 1000);
  if (!n) return false;
  const ok = clickCenterOf(n);
  if (ok) sleepShort();
  return ok;
}

// Fallback to content-desc selector.
function tapByDescRegex(regex, timeoutMs) {
  const n = descMatches(regex).findOne(timeoutMs || 1000);
  if (!n) return false;
  const ok = clickCenterOf(n);
  if (ok) sleepShort();
  return ok;
}

// Generic tap helper used by almost all workflow steps.
function tapByRegex(regex, timeoutMs) {
  return tapByTextRegex(regex, timeoutMs) || tapByDescRegex(regex, timeoutMs);
}

// Wait until an anchor element of current page appears.
function waitByRegex(regex, timeoutMs) {
  const t = timeoutMs || CONFIG.ACTION_TIMEOUT_MS;
  if (textMatches(regex).findOne(t)) return true;
  return !!descMatches(regex).findOne(300);
}

// Best-effort close of common startup/update popups.
function closeCommonPopups(rounds) {
  const times = rounds || 6;
  for (let i = 0; i < times; i++) {
    let acted = false;
    acted = tapByRegex(/(同意|允许|确认|继续|跳过|知道了|关闭|X)/, 600) || acted;
    if (!acted) break;
  }
}

/**
 * Locate text input by:
 * 1) Direct EditText lookup
 * 2) Hint text + parent traversal lookup
 */
function findInputByHintRegex(regex, timeoutMs) {
  const deadline = new Date().getTime() + (timeoutMs || 6000);
  while (new Date().getTime() < deadline) {
    const editTextNode = className("android.widget.EditText").findOne(500);
    if (editTextNode) return editTextNode;

    const hintNode = textMatches(regex).findOne(300) || descMatches(regex).findOne(300);
    if (hintNode && hintNode.parent()) {
      const p = hintNode.parent();
      const candidate = p.findOne(className("android.widget.EditText"));
      if (candidate) return candidate;
    }
  }
  return null;
}

// Robust text input: node.setText first, global setText as fallback.
function setInputText(inputNode, value) {
  if (!inputNode) return false;
  inputNode.click();
  sleepShort(400);
  try {
    inputNode.setText(value);
    sleepShort(400);
    return true;
  } catch (e) {
    try {
      setText(value);
      sleepShort(400);
      return true;
    } catch (ex) {
      return false;
    }
  }
}

/**
 * Core business flow for one task.
 * Throws Error on any required-step failure, and caller reports failed state.
 */
function runLoginAndReunionFlow(task) {
  const account = task.account;
  const reunionCode = task.reunionCode;

  // Step 1: launch game and handle startup dialogs.
  log("Launching game...");
  launchApp(CONFIG.GAME_APP_NAME);
  sleep(8000);
  closeCommonPopups(8);

  if (!waitByRegex(CONFIG.SELECTORS.LOGIN_PAGE, 15000)) {
    throw new Error("Login page not found.");
  }

  // Step 2: enter QQ login and accept agreement if needed.
  log("Tap QQ login...");
  if (!tapByRegex(CONFIG.SELECTORS.QQ_LOGIN_BTN, 5000)) {
    throw new Error("QQ login button not found.");
  }

  tapByRegex(CONFIG.SELECTORS.AGREEMENT_CHECKBOX, 3000);

  // Step 3: in login helper, fill account and submit.
  log("Fill account in login helper...");
  sleep(3000);
  const accountInput = findInputByHintRegex(CONFIG.SELECTORS.LOGIN_HELPER_ACCOUNT_HINT, 10000);
  if (!accountInput) {
    throw new Error("Account input not found in login helper.");
  }
  if (!setInputText(accountInput, account)) {
    throw new Error("Unable to input account.");
  }

  if (!tapByRegex(CONFIG.SELECTORS.LOGIN_HELPER_OP_BTN, 8000)) {
    throw new Error("OP/login button not found.");
  }

  // Step 4: wait for lobby.
  log("Waiting for game lobby...");
  if (!waitByRegex(CONFIG.SELECTORS.LOBBY_MARK, 25000)) {
    throw new Error("Game lobby not detected after login.");
  }

  // Step 5: open friend page and enter reunion panel.
  log("Open friends page...");
  if (!tapByRegex(CONFIG.SELECTORS.FRIEND_BTN, 8000)) {
    throw new Error("Friend button not found.");
  }

  sleep(2500);
  if (!tapByRegex(CONFIG.SELECTORS.LEFT_SECOND_BTN_TEXT, 7000)) {
    throw new Error("Left second button for reunion not found.");
  }

  // Step 6: fill reunion code and confirm.
  log("Input reunion code...");
  const codeInput = findInputByHintRegex(CONFIG.SELECTORS.REUNION_CODE_INPUT_HINT, 10000);
  if (!codeInput) {
    throw new Error("Reunion code input not found.");
  }
  if (!setInputText(codeInput, reunionCode)) {
    throw new Error("Unable to input reunion code.");
  }

  if (!tapByRegex(CONFIG.SELECTORS.REUNION_CONFIRM_BTN, 6000)) {
    throw new Error("Reunion confirm button not found.");
  }

  // Step 7: close retry popup if any, then return lobby.
  tapByRegex(CONFIG.SELECTORS.RETRY_POPUP_CLOSE, 3000);
  sleepShort(800);

  log("Back to lobby...");
  back();
  sleepShort();
  back();
  sleep(2000);

  // Step 8: open profile and switch account for next loop.
  log("Open profile and switch account...");
  if (!tapByRegex(CONFIG.SELECTORS.PROFILE_BTN, 7000)) {
    throw new Error("Profile button not found.");
  }
  sleep(2000);
  if (!tapByRegex(CONFIG.SELECTORS.SWITCH_ACCOUNT_BTN, 7000)) {
    throw new Error("Switch account button not found.");
  }

  if (!waitByRegex(CONFIG.SELECTORS.BACK_TO_LOGIN_MARK, 15000)) {
    throw new Error("Did not return to login page.");
  }
}

/**
 * Background heartbeat thread.
 * Keeps lease alive while the task is running.
 */
function startHeartbeatLoop(taskId, runId, stopFlagRef) {
  return threads.start(function () {
    while (!stopFlagRef.stop) {
      sleep(CONFIG.HEARTBEAT_INTERVAL_MS);
      if (stopFlagRef.stop) break;
      try {
        const res = httpPostJson("/tasks/" + taskId + "/heartbeat", {
          deviceId: CONFIG.DEVICE_ID,
          runId: runId
        });
        log("heartbeat: " + res.bodyRaw);
      } catch (e) {
        log("heartbeat error: " + e);
      }
    }
  });
}

// Ask server for next account/reunion-code task.
function claimTask() {
  const res = httpPostJson("/tasks/claim", { deviceId: CONFIG.DEVICE_ID });
  if (res.statusCode !== 200 || !res.json) {
    throw new Error("Claim failed: " + res.bodyRaw);
  }
  return res.json.task;
}

// Report final status for claimed task.
function reportTask(taskId, runId, status, errorMsg) {
  const payload = {
    deviceId: CONFIG.DEVICE_ID,
    runId: runId,
    status: status
  };
  if (errorMsg) payload.error = errorMsg;
  const res = httpPostJson("/tasks/" + taskId + "/report", payload);
  log("report: " + res.bodyRaw);
}

// Save screenshot when task fails (for later selector tuning).
function saveFailureScreenshot(taskId) {
  if (!screenshotEnabled) return;
  try {
    const img = captureScreen();
    if (!img) return;
    const path = "/sdcard/Download/hs_task_failed_" + taskId + ".png";
    images.save(img, path, "png", 100);
    log("screenshot saved: " + path);
  } catch (e) {
    log("screenshot error: " + e);
  }
}

/**
 * Main worker loop:
 * - health check
 * - claim task
 * - execute flow
 * - report result
 * - repeat forever
 */
function mainLoop() {
  log("Check server health...");
  const health = httpGet("/health");
  log("health: " + health.bodyRaw);

  while (true) {
    let task = null;
    try {
      task = claimTask();
    } catch (e) {
      log("claim error: " + e);
      sleep(CONFIG.POLL_INTERVAL_MS);
      continue;
    }

    if (!task) {
      log("No task. waiting...");
      sleep(CONFIG.POLL_INTERVAL_MS);
      continue;
    }

    log("Task claimed id=" + task.id + ", account=" + task.account);
    const stopFlag = { stop: false };
    const hbThread = startHeartbeatLoop(task.id, task.runId, stopFlag);
    let status = "done";
    let error = "";

    try {
      runLoginAndReunionFlow(task);
      log("Task done.");
      toast("Task done: " + task.id);
    } catch (e) {
      // Any thrown error means this task is considered failed.
      status = "failed";
      error = String(e);
      log("Task failed: " + error);
      toast("Task failed: " + task.id);
      saveFailureScreenshot(task.id);
    } finally {
      stopFlag.stop = true;
      try {
        hbThread.interrupt();
      } catch (ignore) {}
    }

    try {
      reportTask(task.id, task.runId, status, error);
    } catch (e) {
      // Reporting failure should not crash the worker loop.
      log("report error: " + e);
    }

    sleep(1500);
  }
}

mainLoop();
