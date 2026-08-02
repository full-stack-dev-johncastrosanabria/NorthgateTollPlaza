# Northgate Toll Plaza — System Architecture

**Author:** Senior Software Architect design document
**Date:** 2026-08-02
**Reference:** Backend structure and patterns mirror [full-stack-dev-johncastrosanabria/spring-demo](https://github.com/full-stack-dev-johncastrosanabria/spring-demo) — layered architecture (`controllers` / `dto` / `entity` / `exception` / `repository` / `service`), DTO-based request/response contracts, Jakarta Bean Validation, `GlobalExceptionHandler` + `ResourceNotFoundException`, Lombok, Spring Data JPA — upgraded from the repo's Java 17/MySQL to **Java 21 / PostgreSQL**, and scaled from one module to a Maven multi-module with two microservices.

---

## 1. System Architecture Overview

```
                        ┌──────────────────────────────┐
                        │   Angular 21 SPA  (port 4200) │
                        │   login · lane console ·      │
                        │   manager dashboard           │
                        └──────────┬───────────────────┘
                                   │  HTTP/JSON + JWT
                     dev proxy (proxy.conf.json)
                    ┌──────────────┴───────────────┐
        /api/toll/** │                              │ /api/audit/**
                    ▼                              ▼
   ┌────────────────────────────┐   ┌────────────────────────────┐
   │  toll-service   (port 8080)│   │  audit-service  (port 8081)│
   │  Spring Boot · Java 21     │──▶│  Spring Boot · Java 21     │
   │  auth, lanes, shifts,      │   │  audit trail,              │
   │  passes, exceptions,       │   │  raw plate scans (ANPR)    │
   │  dashboard aggregates      │   │                            │
   └──────────────┬─────────────┘   └──────────────┬─────────────┘
                  │ JPA/Hibernate                   │ Spring Data MongoDB
                  ▼                                 ▼
   ┌────────────────────────────┐   ┌────────────────────────────┐
   │  PostgreSQL (local)        │   │  MongoDB (local)           │
   │  db: northgate_toll        │   │  db: northgate_audit       │
   └────────────────────────────┘   └────────────────────────────┘
```

**Interaction model**

1. The Angular SPA is the only client. In development it uses Angular's dev-server proxy (`proxy.conf.json`) to route `/api/toll/**` → `localhost:8080` and `/api/audit/**` → `localhost:8081`. No API gateway — with two services and one client, a gateway is ceremony without payoff; the proxy file *is* the routing table. If a third service ever appears, introduce Spring Cloud Gateway and change only the proxy file.
2. **toll-service** owns all transactional state (system of record) in PostgreSQL. It issues JWTs at login (`staff-id` + PIN); both services validate the same shared-secret HS256 token, so audit-service needs no auth round-trip.
3. **toll-service → audit-service** communication is one-way, fire-and-forget REST (`@Async` + `RestClient`). Every business event (sign-in, pass recorded, exception opened, override) is POSTed to audit-service *after* the local transaction commits. If audit-service is down, the toll lane keeps working — audit writes are logged-and-dropped, never block a gate opening. This is the deliberate consistency trade-off: tolls are ACID, audit is best-effort.
4. **audit-service** additionally receives raw ANPR plate scans (simulated by the frontend or a seed script) into a TTL-expiring MongoDB collection; the lane console's "Auto-read or type" plate field polls it for the latest scan on its lane.
5. The manager dashboard reads aggregates (revenue today, vehicles/hour, per-lane stats) from toll-service, which computes them with SQL over `pass` — no separate analytics store at this scale.

---

## 2. Microservice Breakdown

### toll-service (PostgreSQL · port 8080)

The system of record. Everything money-related and stateful:

| Concern | Detail |
|---|---|
| Auth | `POST /api/toll/auth/login` — staff code + PIN (BCrypt) → JWT with `role` claim (`OPERATOR` \| `MANAGER`) |
| Tariffs | Vehicle classes and fares (Motorcycle 1.50 … Truck 14.00) |
| Passes | Record a pass (class, plate, payment method cash/card/tag, amount), list recent passes per shift |
| Exceptions | Open/clear/override lane exceptions (`UNREAD_TAG`, `VIOLATION`, `OVERPAYMENT`) |
| Shifts & lanes | Operator↔lane assignment, shift window, shift totals; lane status (`OPEN`/`CLOSED`/`FAULT`), queue length |
| Dashboard | `GET /api/toll/dashboard` (MANAGER only) — plaza KPIs, per-lane cards, traffic-by-hour |

Why one service and not four: lanes, shifts, passes, and exceptions share transactions and foreign keys (a pass belongs to a shift on a lane). Splitting them would turn local joins into network calls. The genuine seam is *transactional toll processing vs. append-only observation* — which is exactly where the service boundary sits.

### audit-service (MongoDB · port 8081)

Append-only, schema-flexible, losable-without-losing-money:

| Concern | Detail |
|---|---|
| Audit trail | `POST /api/audit/events` — immutable log of every business action with a free-form `payload` document (this is why Mongo: each event type carries a different shape) |
| Plate scans | `POST /api/audit/scans` + `GET /api/audit/scans/latest?laneId=` — raw ANPR reads with confidence score, TTL-expired after 24 h (Mongo TTL index; transient sensor data doesn't belong in the relational model) |
| Audit query | `GET /api/audit/events?laneId=&staffCode=&type=` — manager-facing audit review |

---

## 3. Folder Structure

### Backend — Maven multi-module

Each service internally replicates the spring-demo layout exactly.

```
northgate-backend/
├── pom.xml                                  # parent: packaging=pom, Java 21, Spring Boot BOM
├── mvnw / mvnw.cmd / .mvn/
├── toll-service/
│   ├── pom.xml                              # web, data-jpa, security, validation, postgresql, flyway, lombok, jjwt
│   └── src/
│       ├── main/java/com/john/northgate/toll/
│       │   ├── TollServiceApplication.java
│       │   ├── config/                      # SecurityConfig, JwtFilter, AsyncConfig, AuditClientConfig
│       │   ├── controllers/
│       │   │   ├── AuthController.java
│       │   │   ├── PassController.java
│       │   │   ├── LaneExceptionController.java
│       │   │   ├── LaneController.java
│       │   │   ├── ShiftController.java
│       │   │   └── DashboardController.java
│       │   ├── dto/                         # LoginRequestDto, LoginResponseDto, PassRequestDto,
│       │   │                                #   PassResponseDto, ExceptionResponseDto, OverrideRequestDto,
│       │   │                                #   ShiftSummaryDto, DashboardResponseDto, LaneStatsDto
│       │   ├── entity/                      # Staff, Lane, Shift, VehicleClass, Pass, LaneException (+ enums)
│       │   ├── exception/                   # GlobalExceptionHandler, ResourceNotFoundException,
│       │   │                                #   InvalidCredentialsException
│       │   ├── repository/                  # StaffRepository, LaneRepository, ShiftRepository,
│       │   │                                #   VehicleClassRepository, PassRepository, LaneExceptionRepository
│       │   ├── service/                     # AuthService, PassService, LaneExceptionService,
│       │   │                                #   ShiftService, DashboardService
│       │   └── client/                      # AuditClient (@Async fire-and-forget RestClient)
│       └── main/resources/
│           ├── application.yaml
│           └── db/migration/                # V1__schema.sql, V2__seed.sql (Flyway)
└── audit-service/
    ├── pom.xml                              # web, data-mongodb, validation, lombok, jjwt
    └── src/
        ├── main/java/com/john/northgate/audit/
        │   ├── AuditServiceApplication.java
        │   ├── config/                      # SecurityConfig (JWT validate only), MongoIndexConfig (TTL)
        │   ├── controllers/                 # AuditEventController, PlateScanController
        │   ├── dto/                         # AuditEventRequestDto, AuditEventResponseDto,
        │   │                                #   PlateScanRequestDto, PlateScanResponseDto
        │   ├── document/                    # AuditEvent, PlateScan   (Mongo @Document ≈ repo's entity/)
        │   ├── exception/                   # GlobalExceptionHandler, ResourceNotFoundException
        │   ├── repository/                  # AuditEventRepository, PlateScanRepository
        │   └── service/                     # AuditEventService, PlateScanService
        └── main/resources/application.yaml
```

### Frontend — Angular 21 (standalone components + signals)

```
northgate-frontend/
├── angular.json
├── proxy.conf.json                          # /api/toll → :8080, /api/audit → :8081
└── src/
    ├── main.ts
    ├── styles.css
    └── app/
        ├── app.config.ts                    # provideRouter, provideHttpClient(withInterceptors)
        ├── app.routes.ts                    # /login, /lane (OPERATOR), /dashboard (MANAGER)
        ├── core/
        │   ├── auth/                        # auth.service.ts (signal<Session|null>), auth.guard.ts,
        │   │                                #   role.guard.ts, jwt.interceptor.ts
        │   └── api/                         # toll-api.service.ts, audit-api.service.ts
        ├── shared/
        │   ├── models/                      # pass.model.ts, lane.model.ts, exception.model.ts,
        │   │                                #   dashboard.model.ts, vehicle-class.model.ts
        │   └── ui/                          # badge, money-pipe, stat-card components
        └── features/
            ├── login/                       # login.component.{ts,html,css}
            ├── lane-console/                # lane-console.component + children:
            │   ├── new-pass-form/           #   class picker, plate (auto-read poll), payment tabs
            │   ├── exception-list/          #   open/cleared badges, override action
            │   ├── recent-passes/
            │   └── shift-summary/
            └── dashboard/                   # dashboard.component + children:
                ├── kpi-row/                 #   revenue, vehicles, lanes open, queued
                ├── lane-grid/               #   lane cards w/ status + open-exception banner
                └── traffic-chart/           #   vehicles-by-hour bars
```

---

## 4. Database Schema Design

### PostgreSQL — `northgate_toll` (toll-service)

Money as `NUMERIC(8,2)` — never floats. Enums as `VARCHAR` + CHECK constraints (portable, JPA-friendly).

```sql
CREATE TABLE staff (
    id            BIGSERIAL PRIMARY KEY,
    staff_code    VARCHAR(10)  NOT NULL UNIQUE,        -- 'OP-14', 'MG-02'
    full_name     VARCHAR(100) NOT NULL,               -- 'R. Alvarez'
    role          VARCHAR(20)  NOT NULL CHECK (role IN ('OPERATOR','MANAGER')),
    pin_hash      VARCHAR(100) NOT NULL,               -- BCrypt
    active        BOOLEAN      NOT NULL DEFAULT TRUE
);

CREATE TABLE lane (
    id            BIGSERIAL PRIMARY KEY,
    lane_number   INT          NOT NULL UNIQUE,        -- 1..6
    mode          VARCHAR(20)  NOT NULL CHECK (mode IN ('MANNED','AUTOMATED')),
    status        VARCHAR(20)  NOT NULL CHECK (status IN ('OPEN','CLOSED','FAULT')),
    queue_length  INT          NOT NULL DEFAULT 0      -- pushed by sensors; simulated here
);

CREATE TABLE vehicle_class (
    id            BIGSERIAL PRIMARY KEY,
    code          VARCHAR(20)  NOT NULL UNIQUE,        -- MOTORCYCLE, CAR, VAN_SUV, BUS, TRUCK
    label         VARCHAR(40)  NOT NULL,
    fare          NUMERIC(8,2) NOT NULL CHECK (fare >= 0),
    sort_order    INT          NOT NULL
);

CREATE TABLE shift (
    id            BIGSERIAL PRIMARY KEY,
    staff_id      BIGINT       NOT NULL REFERENCES staff(id),
    lane_id       BIGINT       NOT NULL REFERENCES lane(id),
    starts_at     TIMESTAMPTZ  NOT NULL,               -- 06:00
    ends_at       TIMESTAMPTZ  NOT NULL,               -- 14:00
    status        VARCHAR(20)  NOT NULL CHECK (status IN ('ACTIVE','CLOSED'))
);
CREATE UNIQUE INDEX uq_shift_active_lane ON shift(lane_id) WHERE status = 'ACTIVE';

CREATE TABLE pass (
    id               BIGSERIAL PRIMARY KEY,
    lane_id          BIGINT       NOT NULL REFERENCES lane(id),
    shift_id         BIGINT       REFERENCES shift(id),          -- NULL on automated lanes
    vehicle_class_id BIGINT       NOT NULL REFERENCES vehicle_class(id),
    plate            VARCHAR(15)  NOT NULL,                      -- 'KTR 8891'
    payment_method   VARCHAR(10)  NOT NULL CHECK (payment_method IN ('CASH','CARD','TAG')),
    amount           NUMERIC(8,2) NOT NULL,                      -- fare snapshot at time of pass
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_pass_lane_created ON pass(lane_id, created_at DESC);   -- recent passes
CREATE INDEX idx_pass_created      ON pass(created_at);                 -- dashboard rollups

CREATE TABLE lane_exception (
    id            BIGSERIAL PRIMARY KEY,
    lane_id       BIGINT       NOT NULL REFERENCES lane(id),
    shift_id      BIGINT       REFERENCES shift(id),
    plate         VARCHAR(15),
    type          VARCHAR(20)  NOT NULL CHECK (type IN ('UNREAD_TAG','VIOLATION','OVERPAYMENT')),
    description   VARCHAR(200) NOT NULL,               -- 'Tag not detected — vehicle waiting'
    status        VARCHAR(20)  NOT NULL CHECK (status IN ('OPEN','CLEARED','OVERRIDDEN')),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    resolved_at   TIMESTAMPTZ,
    resolved_by   BIGINT       REFERENCES staff(id)
);
CREATE INDEX idx_exception_open ON lane_exception(lane_id) WHERE status = 'OPEN';
```

Notes:
- `pass.amount` snapshots the fare so historical revenue survives tariff changes.
- Every dashboard number derives from these tables: revenue today = `SUM(amount)`, veh/hr = `COUNT(*) GROUP BY date_trunc('hour', created_at)`, per-lane stats = same grouped by `lane_id`. `queue_length` sits on `lane` because it's ephemeral sensor state, not history.
- The partial unique index enforces one active shift per lane at the database level.

### MongoDB — `northgate_audit` (audit-service)

```js
// audit_events — immutable business audit trail; payload shape varies per event type
{
  _id: ObjectId,
  eventType: "PASS_RECORDED",       // SIGN_IN | PASS_RECORDED | EXCEPTION_OPENED
                                     //   | EXCEPTION_OVERRIDDEN | EXCEPTION_CLEARED
                                     //   | SHIFT_STARTED | SHIFT_CLOSED | LANE_STATUS_CHANGED
  staffCode: "OP-14",
  laneNumber: 3,
  entityType: "PASS", entityId: "812",
  payload: { plate: "KTR 8891", vehicleClass: "CAR", paymentMethod: "TAG", amount: "3.00" },
  occurredAt: ISODate("2026-08-02T09:42:00Z")
}
// Indexes: {occurredAt:-1}, {laneNumber:1, occurredAt:-1}, {staffCode:1, occurredAt:-1}, {eventType:1}

// plate_scans — transient ANPR reads; TTL-expired
{
  _id: ObjectId,
  laneNumber: 3,
  plate: "HRV 7745",
  confidence: 0.97,
  tagId: null,                      // null when tag unread
  scannedAt: ISODate("2026-08-02T09:40:12Z")
}
// Indexes: {laneNumber:1, scannedAt:-1};  TTL: {scannedAt:1} expireAfterSeconds: 86400
```

---

## 5. First Steps

**Step 1 — Repos and Maven parent.** Inside `NorthgateTollPlaza/`, `git init`, create `northgate-backend/pom.xml` with `packaging=pom`, `<java.version>21</java.version>`, the Spring Boot parent (4.x, matching the reference repo's line), and `<modules>toll-service, audit-service</modules>`.

**Step 2 — Scaffold both services.** Generate each with Spring Initializr and drop them in as modules (strip their `<parent>` to point at your new parent):

```bash
curl -s https://start.spring.io/starter.tgz -d type=maven-project -d language=java \
  -d javaVersion=21 -d groupId=com.john.northgate -d artifactId=toll-service \
  -d packageName=com.john.northgate.toll \
  -d dependencies=web,data-jpa,postgresql,flyway,security,validation,lombok,devtools \
  | tar -xz -C northgate-backend/toll-service --strip-components=0

curl -s https://start.spring.io/starter.tgz -d type=maven-project -d language=java \
  -d javaVersion=21 -d groupId=com.john.northgate -d artifactId=audit-service \
  -d packageName=com.john.northgate.audit \
  -d dependencies=web,data-mongodb,security,validation,lombok,devtools \
  | tar -xz -C northgate-backend/audit-service --strip-components=0
```

**Step 3 — Databases and migrations.** `createdb northgate_toll` (Mongo creates `northgate_audit` lazily). Write `V1__schema.sql` (section 4 DDL) and `V2__seed.sql` (staff OP-14/MG-02 with BCrypt-hashed PIN `1234`, lanes 1–6, the five vehicle classes with prototype fares, one active shift on lane 3). Configure both `application.yaml`s (datasource / `spring.data.mongodb.uri`, ports 8080/8081, shared JWT secret). Verify `mvn spring-boot:run` boots both and Flyway applies cleanly.

**Running the tests.** `./mvnw test` from `northgate-backend`. Surefire supplies
`NORTHGATE_JWT_SECRET` itself, so nothing needs exporting. The dashboard's SQL is
covered by `DashboardAggregationTest`, which runs against a real PostgreSQL —
create it once with `createdb northgate_toll_test`. It applies only the V1 schema
(`spring.flyway.target=1`, so the V2/V3 demo seed stays out of the fixtures) and
rolls back every test, leaving both databases untouched.

**Step 4 — Frontend shell + first vertical slice.** `npx @angular/cli@21 new northgate-frontend`, add `proxy.conf.json`, routes `/login → /lane | /dashboard` with role guards. Then build one end-to-end slice before anything else: `POST /auth/login` → JWT stored → `POST /passes` from the lane console → row in PostgreSQL → `PASS_RECORDED` document visible in `northgate_audit.audit_events`. That slice exercises every architectural joint (auth, both services, both databases, the async client); everything after is repetition.
