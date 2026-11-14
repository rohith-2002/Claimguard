# Claimguard

Claimguard is a small Spring Boot service that helps an insurer catch suspicious claims early. Every
claim that arrives is run past a handful of configurable fraud rules; each rule that fires adds its
weight and a short, readable reason. If the total score reaches the alert threshold, the service stores
an alert — with all of its reasons — and adds it to a queue the Special Investigations Unit (SIU) can
work through.

The project was built in three stages, and all three are complete:

- **Task 1** — the REST screening endpoint, the first four rules, alerts, and the queue API.
- **Task 2** — a Kafka intake path, a Redis-backed claim history store, the `REPEAT_VEHICLE` and
  `PROVIDER_SPIKE` rules, bounded retries, and a dead-letter topic.
- **Task 3** — a React investigator UI, `PATCH` disposition for an alert, and an append-only audit
  trail.

## What the service exposes

| Surface | Purpose |
|---|---|
| `POST /api/claims/screen` | Screen one claim over HTTP. |
| `GET /api/alerts` | Browse the SIU queue with status, line-of-business, and minimum-score filters. |
| `PATCH /api/alerts/{id}` | Close an `OPEN` alert as `CONFIRMED_FRAUD` or `FALSE_POSITIVE`. |
| `GET /api/stream/stats` | In-memory counter snapshot for the Kafka pipeline. |
| Kafka `claims.submitted` | The streaming intake path; bad messages go to `claims.submitted.DLT`. |

## How a claim flows through the service

1. A claim arrives either as a `POST /api/claims/screen` request or as a message on the Kafka topic
   `claims.submitted`.
2. `ClaimConsumer` parses and validates Kafka messages; the HTTP path does the same through Spring MVC
   validation. In both cases, the claim is handed to the same `ScreeningService`.
3. `ScreeningService` runs every enabled `FraudRule` and sums the weights of the rules that fire.
4. Each claim is recorded in the `ClaimHistoryStore` (Redis in normal runs, in-memory in tests).
5. If the score is at or above the alert threshold, an alert is written to the `alerts` table, with one
   row per triggered rule in the child `alert_rules` table.
6. The SIU browses those alerts through `GET /api/alerts`, filtered by status, line of business, and
   minimum score, and sorted worst-first.
7. An investigator closes an alert through `PATCH /api/alerts/{id}`; the status change and its audit
   row are written in the same transaction.

The screening path is safe to call more than once: screening is idempotent per `claimId`, so a re-sent
claim — over HTTP, Kafka, or a replay — never creates a second alert or changes later claims' history
counts.

## How the pieces fit together

```
POST /api/claims/screen --> ScreeningController --\
                                                    \
Kafka claims.submitted --> ClaimConsumer -----------> ScreeningService --> FraudRule(s)
                         (parse + validate)                 |              (EARLY_CLAIM, HIGH_AMOUNT_RATIO,
                                                            |               LATE_REPORTING, REPEAT_POLICY_CLAIM,
                                                            |               REPEAT_VEHICLE, PROVIDER_SPIKE)
                                                            |--> ClaimHistoryStore (Redis; in-memory in tests)
                                                            '--> AlertRepository --> PostgreSQL
                                                                 (alerts, alert_rules)

GET   /api/alerts      --> AlertController --> AlertService --> AlertRepository
PATCH /api/alerts/{id} --> AlertController --> AlertService --> AlertRepository + AlertAuditRepository
GET   /api/stream/stats --> StatsController --> StreamStats
```

A few things worth knowing:

- `ScreeningService` is the single screening path. It scores the rules first (pure logic, no I/O), then
  records history and persists the alert.
- Each rule extends `ConfiguredRule`, which reads `enabled`, `threshold`, and `weight` from
  `screening.rules.<CODE>` in `application.yml` — so thresholds and weights can be tuned without
  touching any code.
- All time windows are measured against the claim's `reportedAt` (the event time), never the wall clock,
  so replaying the same input always produces the same result.

## Task 2: Kafka intake and Redis history

### Intake and failure handling

`ClaimConsumer` listens on `claims.submitted`, deserialises the JSON into the same `ClaimRequest` the
HTTP API uses, validates it, and calls `ScreeningService.screen`. The listener and its topic beans are
enabled by `claimguard.kafka.enabled=true` and disabled in the unit-test profile.

Kafka wiring lives in `KafkaConfig`:

- `claims.submitted` and `claims.submitted.DLT` are created with one partition and one replica.
- Transient failures are retried with a fixed 500 ms backoff before the message is dead-lettered.
- Deterministic bad input — unparseable JSON, an empty value, or a validation failure — is not retried;
  it goes straight to the DLT so one poison message cannot stall the partition.
- The consumer keeps going after a dead-letter, which is the important operational property.
- `StreamStats` exposes `consumed`, `duplicatesSkipped`, `deadLettered`, and `alertsCreated` through
  `GET /api/stream/stats`. The counters are in-memory and reset when the service restarts.

Because the topic has one partition and the replay tool publishes in file order, claims are consumed in
`reportedAt` order. That ordering is what makes the Redis windows exact; more partitions would require
a different windowing strategy.

### Redis windows

`RedisClaimHistoryStore` keeps one sorted set per history dimension, scored by `reportedAt` epoch millis:

| Key | Window | Member |
|---|---|---|
| `claims:policy:<policyNumber>` | 180 days | `claimId` |
| `claims:vehicle:<vehicleRegNo>` | 90 days | `policyNumber\|claimId` |
| `claims:provider:<providerId>` | 7 days | `claimId` |

Recording a claim uses `ZADD`, trims entries older than the window with `ZREMRANGEBYSCORE`, and sets a
TTL of the window plus 30 days. Duplicate `claimId`s are naturally idempotent because the sorted set
member already exists. The policy and vehicle windows are half-open (`[from, to)`); the provider window
is closed at both ends because the rule counts the current claim as part of the spike.

## Task 3: investigator UI and disposition

### Disposition API

`PATCH /api/alerts/{id}` accepts:

```json
{
  "status": "CONFIRMED_FRAUD",
  "note": "Evidence reviewed",
  "investigator": "inv-anna"
}
```

Rules enforced by the backend:

- Only `CONFIRMED_FRAUD` and `FALSE_POSITIVE` are valid targets, and only when the alert is `OPEN`.
- `note` and `investigator` are mandatory and must not be blank.
- A second decision on an already-closed alert returns `409 Conflict`.
- An unknown alert returns `404`.
- Exactly one row is appended to `alert_audit` in the same transaction as the status change.

`alert_audit` stores `alert_id`, `investigator`, `changed_at`, `from_status`, `to_status`, and `note`.
It is append-only: rows are inserted, never updated or deleted. The audit trail is currently read
directly from the database; there is no HTTP read endpoint for it yet.

### React UI

The investigator workspace lives in [`claimguard-task3-ui/`](claimguard-task3-ui) and uses the alert
API directly. It provides:

- an alert queue with status, line-of-business, and minimum-score filters;
- server-side pagination and expandable rule reasons;
- a disposition form with a mandatory note and investigator name;
- mobile masking in the UI as an extra safeguard, even if the API ever returns digits.

Vite proxies `/api` to `http://localhost:8080`, so local development needs no CORS setup. See the
[UI README](claimguard-task3-ui/README.md) for its API contract and details.

## Package layout

```
com.claimguard
├── ClaimguardApplication.java
├── ScreeningProperties.java
├── api/        ScreeningController, AlertController, StatsController, ApiExceptionHandler,
│               ClaimRequest, ScreeningResponse, AlertView, PageResponse, DispositionRequest
├── rules/      FraudRule, ConfiguredRule, EarlyClaimRule, HighAmountRatioRule,
│               LateReportingRule, RepeatPolicyClaimRule, RepeatVehicleRule,
│               ProviderSpikeRule, TriggeredRule
├── screening/  ScreeningService, ClaimHistoryStore, InMemoryClaimHistoryStore,
│               RedisClaimHistoryStore, StreamStats
├── stream/     ClaimConsumer, KafkaConfig, InvalidClaimMessageException
└── alerts/     AlertEntity, AlertRepository, AlertService, AlertAuditEntity,
                AlertAuditRepository, AlertStatus, LineOfBusiness, Masking
```

## Running it locally

You'll need Java 17+, Maven, Docker, and Node.js 18+ for the UI. Docker runs PostgreSQL, Kafka, and
Redis:

```bash
docker compose up -d        # PostgreSQL :5432, Kafka :9092, Redis :6379
mvn spring-boot:run         # Flyway creates the schema on the first start
```

The defaults line up: Docker Compose creates the `claimguard` database/user/password, and the app reads
the same defaults. To override database credentials, set the `DB_*` values for both Docker Compose (via
`.env`, copied from `.env.example`) and the app (environment variables).

The service listens on http://localhost:8080. The default profile uses Redis for history and enables
the Kafka listener. The test profile uses H2 and the in-memory store, and disables Kafka.

### Poking at it with curl

```bash
curl -s -X POST localhost:8080/api/claims/screen -H 'Content-Type: application/json' -d '{
  "claimId":"CLM-1","policyNumber":"POL-1","policyStartDate":"2026-01-01","sumInsured":500000,
  "lineOfBusiness":"MOTOR","vehicleRegNo":"KA01AB1234","providerId":"GAR-7","claimantMobile":"9876543210",
  "lossDate":"2026-01-07","reportedAt":"2026-02-01T10:00:00Z","claimedAmount":450000}'
# -> score 85, alertRaised true, 3 triggered rules (EARLY_CLAIM, HIGH_AMOUNT_RATIO, LATE_REPORTING)

curl -s 'localhost:8080/api/alerts?status=OPEN&lineOfBusiness=MOTOR&minScore=40&page=0&size=20'
# -> items sorted by score desc, newest first; claimantMobile is "******3210"

curl -s -X PATCH localhost:8080/api/alerts/1 -H 'Content-Type: application/json' -d '{
  "status":"CONFIRMED_FRAUD","note":"Evidence reviewed","investigator":"inv-anna"}'
# -> status CONFIRMED_FRAUD; one append-only row in alert_audit
# -> a second PATCH on the same alert returns 409 Conflict

curl -s localhost:8080/api/stream/stats
# -> {"consumed":...,"duplicatesSkipped":...,"deadLettered":...,"alertsCreated":...}
```

Anything invalid comes back as a `400` with `{"error":"Validation failed","fieldErrors":{...}}`.

> **If startup fails with `invalid value for parameter "TimeZone"`:** the JVM is handing PostgreSQL a
> legacy timezone id (for example `Asia/Calcutta`). Give the app a UTC clock instead, e.g.
> `mvn spring-boot:run -Dspring-boot.run.jvmArguments="-Duser.timezone=UTC"`.

### Replaying the dataset through Kafka

The replay tool publishes `data/claims.jsonl` to `claims.submitted` in file order, keyed by policy
number. The optional `--with-bad` flag first sends two malformed records so the dead-letter path can be
observed:

```bash
mvn -q test-compile exec:java -Dexec.classpathScope=test \
    -Dexec.mainClass=com.claimguard.screening.ClaimReplay \
    -Dexec.args="data/claims.jsonl --with-bad"

curl -s localhost:8080/api/stream/stats
```

The replay producer honours `KAFKA_BOOTSTRAP` (default `localhost:9092`). After a clean replay the stats
should show the claims consumed, duplicate re-sends counted, and any bad records dead-lettered.

### Starting the UI

With the backend running:

```bash
cd claimguard-task3-ui
npm install
npm run dev
```

Open the local URL printed by Vite (usually http://localhost:5173).

## Configuration (application.yml)

Screening rules:

| Key | Default | Fires when |
|---|---|---|
| `screening.alert-threshold` | 50 | total score `>=` this raises an alert |
| `screening.rules.EARLY_CLAIM` | threshold 30, weight 30 | loss `<=` 30 days after policy start |
| `screening.rules.HIGH_AMOUNT_RATIO` | threshold 0.8, weight 35 | claimed `>=` 0.8 x sum insured |
| `screening.rules.LATE_REPORTING` | threshold 15, weight 20 | reported `>` 15 days after loss |
| `screening.rules.REPEAT_POLICY_CLAIM` | threshold 2, weight 25 | `>=` 2 earlier claims on the policy in the 180 days before `reportedAt` |
| `screening.rules.REPEAT_VEHICLE` | threshold 1, weight 40 | `>=` 1 earlier claim on the same vehicle under a different policy in the 90 days before `reportedAt` |
| `screening.rules.PROVIDER_SPIKE` | threshold 10, weight 30 | more than 10 claims for the provider in the 7 days up to and including `reportedAt` (the current claim counts) |

Each rule also has an `enabled` flag. The policy and vehicle windows are half-open; the provider window
is closed at both ends, and its threshold is strict ("more than"). "Earlier" means a strictly earlier
`reportedAt`.

Runtime and infrastructure:

| Key | Default | Purpose |
|---|---|---|
| `claimguard.kafka.enabled` | `true` | enables the Kafka listener, topics, and error handler |
| `claimguard.history.store` | `redis` | `redis` or `memory` |
| `spring.kafka.bootstrap-servers` | `localhost:9092` | Kafka broker |
| `spring.data.redis.host` / `port` | `localhost` / `6379` | Redis connection |
| `DB_URL` / `DB_USER` / `DB_PASSWORD` | local PostgreSQL | database connection |

**A worked example.** A claim whose loss is 6 days after the policy start fires `EARLY_CLAIM` at the
default threshold of 30. Change `screening.rules.EARLY_CLAIM.threshold` to `3`, restart, and the same
claim no longer fires. That behaviour is pinned by
`EarlyClaimRuleTest.changingConfiguredThresholdChangesOutcome`.

## Tests

The short version: **`mvn test` is completely self-contained — no database, no broker, no Docker.** The
Spring tests run against an in-memory H2 database in PostgreSQL-compatibility mode, Flyway replays both
migrations against H2, the history store is in-memory, and the Kafka listener is off. Once your
dependencies are cached, it even runs offline.

```bash
mvn test                    # 61 unit/API/dataset tests — no Docker, no database, no broker
mvn test -Pintegration      # also runs the Testcontainers Kafka + Redis end-to-end test
cd claimguard-task3-ui && npm test   # Vitest + React Testing Library
```

If a test genuinely needs Docker/Testcontainers or a live service, annotate it with `@Tag("integration")`.
The default build excludes that tag (see `test.excluded.groups` in `pom.xml`), and the `integration`
Maven profile clears the exclusion so those tests only run when you explicitly ask for them. Every test
also defaults to the in-memory `test` profile, so no test can accidentally reach for a real database.
`StreamIntegrationTest` starts real Kafka and Redis containers with Testcontainers, so the integration
profile needs a running Docker daemon.

What's covered today:

- one test class per rule, each pinning the exact threshold boundary — including the inclusive 90-day
  vehicle window, the `90 days + 1 second` miss, the provider's strict "more than 10" boundary, and the
  7-day inclusive start;
- `InMemoryClaimHistoryStoreTest`: duplicate `claimId`s are recorded once;
- `ScreeningServiceTest`: re-screening a claim keeps a single alert, and re-posts don't inflate the
  repeat-policy count for later claims;
- `AlertApiTest`: a screened claim is persisted and shows up in `GET /api/alerts`, including the sorting,
  filters, and pagination; validation errors are `400`s; the response body never contains the unmasked
  mobile;
- `AlertDispositionApiTest`: both closing statuses, mandatory note/investigator, `409` for an already
  closed alert, `404` for an unknown one, rejection of `OPEN`, and exactly one audit row per decision;
- `StreamIntegrationTest`: a duplicate message creates one alert, a malformed message lands in the DLT,
  the consumer keeps processing, and the Redis history key gets a TTL;
- `DatasetScreeningTest`: screens `data/claims.jsonl` and prints the hit table below.

## Data generator and hit table

```bash
mvn -q test-compile exec:java -Dexec.classpathScope=test \
    -Dexec.mainClass=com.claimguard.screening.ClaimDataGenerator   # seed 42 -> data/claims.jsonl
mvn test -Dtest=DatasetScreeningTest                               # prints the table
```

The generator writes 692 claims ordered by `reportedAt`; 143 of them (20.7%) carry an injected pattern.
The label is only set on the claim that is expected to trigger the rule — for instance, the third claim
of a repeat-policy burst, the second claim of a repeat-vehicle pair, or the 11th and 12th claims of a
provider spike.

| Rule | Hits | Injected | Injected & detected |
|---|---|---|---|
| EARLY_CLAIM | 50 | 50 | 50 |
| HIGH_AMOUNT_RATIO | 50 | 50 | 50 |
| LATE_REPORTING | 45 | 45 | 45 |
| REPEAT_POLICY_CLAIM | 12 | 12 | 12 |
| REPEAT_VEHICLE | 15 | 15 | 15 |
| PROVIDER_SPIKE | 6 | 6 | 6 |

Alerts raised on the full file: 35. (If you change the generator or the defaults, re-run
`DatasetScreeningTest` and update this table.)

## Design decisions

- **Both intake paths share one screening path.** HTTP and Kafka both call `ScreeningService`, so the
  rules, idempotency, history, and alert persistence cannot drift apart.
- **At-least-once intake, idempotent screening.** Kafka may deliver a message more than once;
  `history.record()` is a no-op for a known `claimId`, `alerts.claim_id` is unique, and a concurrent
  duplicate insert is caught and ignored. The stream stats count the skipped duplicate.
- **Poison messages are isolated.** Unparseable or invalid messages are not retried; they go straight to
  the DLT so the partition keeps moving. Transient failures get a short bounded retry first.
- **Redis windows are event-time and order-sensitive.** Each dimension is a sorted set scored by
  `reportedAt`, trimmed on write and expired after the window plus slack. Input must arrive in
  `reportedAt` order for the windows to stay exact, which is why the topic has one partition and the
  replay tool publishes in file order.
- **The audit trail is append-only.** `AlertService.dispose` changes the status and inserts the audit row
  in one transaction; a closed alert cannot be changed again. Old audit rows are never updated or
  deleted.
- **`PATCH` validates the decision, not just the body.** Only `CONFIRMED_FRAUD` and `FALSE_POSITIVE` are
  closable targets, the note and investigator are mandatory, and re-closing returns `409`.
- **Idempotency per `claimId`.** Rules ignore the claim's own id when reading history, `record()` is a
  no-op for a known `claimId`, and `alerts.claim_id` has a unique constraint. Re-posting never creates a
  second alert or changes later claims' `REPEAT_POLICY_CLAIM` result.
- **PII.** Only the masked mobile (`******3210`) is persisted or returned. `ClaimRequest.toString()`
  omits the number, no code logs the request body or a Kafka payload, and validation errors never echo
  submitted values. The UI also masks defensively.
- **Rules live in separate classes** behind `FraudRule`, so their boundary semantics are explicit and
  individually tested.
- **Money math uses `BigDecimal`;** the ratio rule compares by multiplication, so the boundary is exact.
- **The queue index** `idx_alerts_queue (status, line_of_business, score DESC, reported_at DESC, id DESC)`
  matches the filter and sort of `GET /api/alerts`.
- **Triggered rules live in a child table** (`alert_rules`) via an `@ElementCollection` of an embeddable
  `TriggeredRule` record, which also works against H2 in the unit-test run.

## Where the project stands

- **Done:** the Task 1 requirements.
- **Done:** Task 2 — Kafka consumer, retries and DLT, Redis history store, `REPEAT_VEHICLE`,
  `PROVIDER_SPIKE`, replay tooling, and pipeline stats.
- **Done:** Task 3 — the React investigator UI, `PATCH` disposition, and the append-only `alert_audit`
  table.
- **Not done:** optional stretch goals and production hardening (authentication, an audit read API,
  durable metrics).

## What I'd do next

Add authentication and authorisation around the SIU APIs, expose the audit trail through a read endpoint
and show it as a timeline in the UI, and replace the in-memory stream counters with durable metrics. For
the stream itself, the next steps would be a schema registry or explicit message contract, an outbox or
transactional producer for reliable publication, and a partitioning strategy that preserves the
event-time ordering the Redis windows depend on.
