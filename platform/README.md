# Order platform (Spring Boot)

Points-based game-assist ordering platform, modeled on the "网页下单 / 项目中心"
screenshots. It sits on top of the cloud-phone execution layer: users spend
**points** (no real currency) to place orders, orders are split into worker
**tasks**, and the existing Auto.js cloud-phone clients claim/execute/report
those tasks. Order success/failure and refunds are settled automatically.

## Stack

- Spring Boot 4 (Web MVC, Spring Data JPA, Validation)
- Embedded H2 file database (`./data/platformdb`), survives restarts
- Java 21, Maven (via the bundled `./mvnw` wrapper)

## Run

```bash
cd platform
./mvnw -B -DskipTests package
java -jar target/platform-0.0.1-SNAPSHOT.jar
```

Server listens on `:8080`. On first run it seeds:

- `admin` / `admin123` (role ADMIN, 0 points)
- `user` / `user123` (role USER, 100 points)
- the 6 demo projects from the screenshots
- 20 data accounts (`tok_0001`..`tok_0020`)

Admin points page: <http://localhost:8080/admin.html>
H2 console (dev): <http://localhost:8080/h2-console>

## Concepts

- **Points**: internal wallet. Top-ups are done by an admin editing points; every
  change is recorded in `point_transactions`.
- **Project**: a purchasable item (项目中心). Unified retail `price` (points) and
  `heads` = number of data accounts (N头) required per order.
- **Order**: one purchase. On placement it deducts points and reserves `heads`
  data accounts, creating one `task` per account (all filling the same invite code).
- **Task**: one worker unit = one data account (token) + the invite code.
- **Data account** (数据号): login token with state UNLINKED→LINKED→BOUND/ERROR.
- **Reunion code** (重逢码): tracks `bind_count` across successful binds.

## Order lifecycle

```
place order -> deduct points -> reserve N data accounts -> create N tasks (PROCESSING)
worker claim -> execute -> report done/failed
  all tasks done  -> order SUCCESS, accounts BOUND, reunion_codes.bind_count++
  any task failed -> order FAILED, full point REFUND, siblings cancelled + released
```

## API

Auth uses a bearer token from login: `Authorization: Bearer <token>`.

### User

| Method | Path | Notes |
| --- | --- | --- |
| POST | `/api/v1/auth/register` | `{username,password}` → token |
| POST | `/api/v1/auth/login` | `{username,password}` → token |
| GET | `/api/v1/auth/me` | current user + points |
| GET | `/api/v1/projects` | project catalog |
| POST | `/api/v1/orders` | `{projectId, code}` — `code` may be a raw code or a pasted 口令 like `…[CODE]…` |
| GET | `/api/v1/orders` | my orders |
| GET | `/api/v1/orders/{orderNo}` | one order |

### Worker (cloud phone) — compatible with the Auto.js clients

| Method | Path | Notes |
| --- | --- | --- |
| GET | `/api/v1/health` | health |
| POST | `/api/v1/tasks/claim` | `{deviceId}` → `{task:{id,account,reunionCode,runId}}` or `{task:null}` |
| POST | `/api/v1/tasks/{id}/heartbeat` | `{deviceId,runId}` |
| POST | `/api/v1/tasks/{id}/report` | `{deviceId,runId,status,error}` (`status`=done/failed) |
| GET | `/api/v1/tasks/stats` | queue counts |

### Admin (role ADMIN)

| Method | Path | Notes |
| --- | --- | --- |
| GET | `/api/v1/admin/users` | list users |
| POST | `/api/v1/admin/users/{id}/points` | edit points: `{"set":N}` or `{"delta":N}` |
| GET | `/api/v1/admin/orders` | all orders |
| GET/POST/PUT/DELETE | `/api/v1/admin/projects` | project CRUD |
| GET/POST | `/api/v1/admin/data-accounts` | pool summary / add tokens |

## Notes / next steps

- Bearer tokens are in-memory (reset on restart); a JWT/persistent scheme can replace `TokenStore`.
- Passwords use SHA-256 (phase 1); switch to a real KDF (bcrypt/argon2) later.
- Concurrency is guarded by `synchronized` service methods on a single instance; a
  multi-instance deployment would need DB-level locking.
- Next phases: buyer H5 (chat 下单 + 项目中心), card-key/recharge if ever needed, richer admin.
