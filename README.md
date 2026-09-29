# Cloud phone task server (Java)

This repository now includes a Java server for cloud-phone automation workflows.

Current focus:
- Provide login account + reunion code to a cloud phone
- Receive task execution status from the cloud phone

Planned later:
- Admin APIs/UI for account input and reunion-code input

## Directory layout

```text
server/src/com/aimovies/cloud/CloudPhoneTaskSpringBootApplication.java  # Spring Boot entry
server/src/com/aimovies/cloud/TaskApiController.java      # /api/v1 tasks + health
server/src/com/aimovies/cloud/AdminApiController.java     # /api/v1/admin endpoints
server/src/com/aimovies/cloud/TaskService.java            # JDBC task service logic
server/src/com/aimovies/cloud/CloudPhoneTaskServer.java   # legacy standalone HttpServer (kept for reference)
data/tasks.csv                                             # task seed file (first run only)
data/taskdb.mv.db                                          # H2 database file (created at runtime, gitignored)
pom.xml                                                    # Maven + Spring Boot build config
```

## Persistence

Task state is stored in an embedded [H2](https://www.h2database.com/) database via
JDBC. Claims, heartbeats, and reports are written to the database, so task state
survives server restarts and no state is kept only in memory.

`data/tasks.csv` is used **only to seed the database on first run** (when the
`tasks` table is empty). After that, the database is the source of truth; edit
tasks through the admin API instead of the CSV.

### Schema

Tables are created automatically on startup (`CREATE TABLE IF NOT EXISTS`).

`tasks` — work queue consumed by cloud phones:

| Column | Type | Notes |
| --- | --- | --- |
| `id` | BIGINT PK | seeded in CSV row order |
| `account` | VARCHAR | login account |
| `reunion_code` | VARCHAR | reunion code |
| `status` | VARCHAR | `PENDING` / `RUNNING` / `DONE` / `FAILED` |
| `assigned_device` | VARCHAR | device holding the lease |
| `run_id` | VARCHAR | claim token |
| `lease_until` | BIGINT | lease expiry (epoch seconds) |
| `attempts` | INT | claim count |
| `last_error` | CLOB | last failure message |

`data_accounts` (数据号表) — one data account (login token) and its association state:

| Column | Type | Notes |
| --- | --- | --- |
| `id` | BIGINT PK | auto-increment (主键) |
| `token` | VARCHAR | account/token (账号), unique |
| `status` | VARCHAR | `UNLINKED`未关联 / `LINKED`已关联 / `BOUND`已绑定 / `ERROR`错误 (状态) |
| `reunion_code` | VARCHAR | reunion code (重逢码) |

`reunion_codes` (重逢码表) — one reunion code and its bind count:

| Column | Type | Notes |
| --- | --- | --- |
| `id` | BIGINT PK | auto-increment (主键) |
| `reunion_code` | VARCHAR | reunion code (重逢码), unique |
| `bind_count` | INT | number of bound accounts (绑定数量), default 0 |

## Prerequisites

- JDK 17+ (JDK 21 is also fine)
- Maven 3.9+

## Run (Spring Boot)

```bash
cd /workspace
mvn spring-boot:run
```

Package executable jar:

```bash
mvn clean package
java -jar target/cloud-phone-task-server-1.0.0.jar
```

Optional environment variables:

- `PORT` (default `8080`)
- `TASK_FILE` (default `data/tasks.csv`, used only for first-run seeding)
- `LEASE_SECONDS` (default `300`)
- `API_KEY` (optional, enables `X-API-Key` auth when set)
- `DB_URL` (default `jdbc:h2:file:./data/taskdb;AUTO_SERVER=TRUE`)
- `DB_USER` (default `sa`)
- `DB_PASSWORD` (default empty)

## Task file format (seed)

`data/tasks.csv`

```csv
account,reunionCode
qq_account_001,HF123456
qq_account_002,HF999888
```

Each row is one task. On first run these rows seed the database in row order.
Changing the CSV afterwards has no effect unless the `tasks` table is empty
(for example, after deleting the `data/taskdb.*` files).

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

## Admin APIs

Now that tasks are stored in the database, basic admin endpoints are available.

### List all tasks

`GET /api/v1/admin/tasks`

```json
{
  "tasks": [
    {
      "id": 1,
      "account": "qq_account_001",
      "reunionCode": "HF123456",
      "status": "DONE",
      "assignedDevice": "redfinger-01",
      "attempts": 1,
      "lastError": ""
    }
  ]
}
```

### Add a task

`POST /api/v1/admin/tasks`

Request (JSON):

```json
{
  "account": "qq_account_003",
  "reunionCode": "HF555444"
}
```

Response (`201 Created`):

```json
{
  "task": {
    "id": 3,
    "account": "qq_account_003",
    "reunionCode": "HF555444",
    "status": "PENDING"
  }
}
```

Other `/api/v1/admin/*` paths still return HTTP `501` as placeholders.

## Auto.js cloud-phone client script

Script file:

```text
scripts/autojs/hero_killer_reunion_client.js
```

What it does:
- claims dynamic task data (`account` token, `reunionCode`) from `/api/v1/tasks/claim`
- runs login + reunion-code flow in game
- sends heartbeat while running
- reports `done` or `failed` to `/api/v1/tasks/{id}/report`

Before running:
1. update `CONFIG.SERVER_BASE` and `CONFIG.DEVICE_ID`
2. set `CONFIG.API_KEY` if server auth is enabled
3. adjust selector regex values in `CONFIG.SELECTORS` to your real game/login-helper UI
