# Cloud phone task server (Java)

This repository now includes a Java server for cloud-phone automation workflows.

Current focus:
- Provide login account + reunion code to a cloud phone
- Receive task execution status from the cloud phone

Planned later:
- Admin APIs/UI for account input and reunion-code input

## Directory layout

```text
server/src/com/aimovies/cloud/CloudPhoneTaskServer.java   # main server code
data/tasks.csv                                             # task seed file
```

## Prerequisites

- JDK 17+ (JDK 21 is also fine)
- No Maven/Gradle needed

## Run

```bash
cd /workspace
mkdir -p out
javac -d out server/src/com/aimovies/cloud/CloudPhoneTaskServer.java
java -cp out com.aimovies.cloud.CloudPhoneTaskServer
```

Optional environment variables:

- `PORT` (default `8080`)
- `TASK_FILE` (default `data/tasks.csv`)
- `LEASE_SECONDS` (default `300`)
- `API_KEY` (optional, enables `X-API-Key` auth when set)

## Task file format

`data/tasks.csv`

```csv
account,reunionCode
qq_account_001,HF123456
qq_account_002,HF999888
```

Each row is one task. Tasks are claimed in row order.

## API

Base path: `/api/v1`

### 1) Health

`GET /api/v1/health`

Response:

```json
{
  "status": "ok",
  "time": "2026-09-23T02:45:00Z"
}
```

### 2) Claim next task (cloud phone)

`POST /api/v1/tasks/claim`

Request (JSON):

```json
{
  "deviceId": "redfinger-01"
}
```

Response with task:

```json
{
  "task": {
    "id": 1,
    "account": "qq_account_001",
    "reunionCode": "HF123456",
    "runId": "5d86b2d2-6fd3-49a9-8a07-8f0f90f50a2d"
  }
}
```

Response when queue is empty:

```json
{
  "task": null
}
```

### 3) Heartbeat (extend lease)

`POST /api/v1/tasks/{id}/heartbeat`

Request:

```json
{
  "deviceId": "redfinger-01",
  "runId": "5d86b2d2-6fd3-49a9-8a07-8f0f90f50a2d"
}
```

Response:

```json
{
  "ok": true
}
```

### 4) Report result

`POST /api/v1/tasks/{id}/report`

Request (success):

```json
{
  "deviceId": "redfinger-01",
  "runId": "5d86b2d2-6fd3-49a9-8a07-8f0f90f50a2d",
  "status": "done"
}
```

Request (failure):

```json
{
  "deviceId": "redfinger-01",
  "runId": "5d86b2d2-6fd3-49a9-8a07-8f0f90f50a2d",
  "status": "failed",
  "error": "cannot find reunion-code confirm button"
}
```

Response:

```json
{
  "ok": true
}
```

### 5) Stats

`GET /api/v1/tasks/stats`

Response:

```json
{
  "pending": 1,
  "running": 0,
  "done": 1,
  "failed": 0
}
```

## Notes for postponed admin features

Endpoints under `/api/v1/admin/*` currently return HTTP `501` as placeholders.

For now, update `data/tasks.csv` directly to input account + reunion code.

## Auto.js cloud-phone client script

Script file:

```text
scripts/autojs/hero_killer_reunion_client.js
scripts/autojs/hero_killer_reunion_client_v2_cn.js
scripts/autojs/hero_killer_single_run_verify_cn.js
```

What it does:
- claims dynamic task data (`account`, `reunionCode`) from `/api/v1/tasks/claim`
- runs login + reunion-code flow in game
- sends heartbeat while running
- reports `done` or `failed` to `/api/v1/tasks/{id}/report`

Before running:
1. update `CONFIG.SERVER_BASE` and `CONFIG.DEVICE_ID`
2. set `CONFIG.API_KEY` if server auth is enabled
3. adjust selector regex values in `CONFIG.SELECTORS` to your real game/login-helper UI

V2 note:
- `hero_killer_reunion_client_v2_cn.js` uses fully Chinese inline comments
- the game flow is explicitly mapped to the 13 business steps

### Test script without backend (offline mode)

If backend is not ready yet, use offline mode in:
`scripts/autojs/hero_killer_reunion_client_v2_cn.js`

1) set:
- `LOCAL_TEST_MODE: true`
- `LOCAL_TEST_EXIT_WHEN_DONE: true` (optional, exit after all local tasks)

2) edit local tasks:

```js
LOCAL_TEST_TASKS: [
  { account: "qq_test_001", reunionCode: "74061c8f23" },
  { account: "qq_test_002", reunionCode: "abc1234567" }
]
```

3) run script in Auto.js with accessibility/screenshot permission.

In offline mode:
- no request to `/health`/`/claim`/`/heartbeat`/`/report`
- tasks are pulled from `LOCAL_TEST_TASKS`
- report output is printed to console log only

### Single account / single run verify script

Use this file for one-shot validation without backend:

`scripts/autojs/hero_killer_single_run_verify_cn.js`

Edit before run:
- `LOGIN_TOKEN` (account token for login-helper)
- `REUNION_CODE`

Behavior:
- execute the full 13-step flow once
- auto exit after success/failure
- save failure screenshot to `/sdcard/Download/hs_single_run_failed.png`
