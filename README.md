# Claimguard

Claimguard is a small Spring Boot service that helps an insurer catch suspicious claims early. Every
claim that arrives is run past a handful of configurable fraud rules; each rule that fires adds its
weight and a short, readable reason. If the total score reaches the alert threshold, the service stores
an alert — with all of its reasons — and adds it to a queue the Special Investigations Unit (SIU) can
work through.

The project is being built in stages. **Task 1** is done: the REST screening endpoint, the rules, alerts,
and the queue API. Task 2 (a Kafka intake path and a Redis-backed history store) and Task 3 (a small
React UI with claim disposition and an audit trail) haven't been started yet.

## How a claim flows through the service

1. A client `POST`s a claim to `/api/claims/screen`.
2. `ScreeningService` hands the claim to every enabled `FraudRule`.
3. Each rule that fires returns a weight and a plain-English reason; the weights are summed into a score.
4. If the score is at or above the alert threshold, an alert is written to the `alerts` table, with one
   row per triggered rule in the child `alert_rules` table.
5. The SIU browses those alerts through `GET /api/alerts`, filtered by status, line of business, and
   minimum score, and sorted worst-first.

The screening endpoint is safe to call more than once: screening is idempotent per `claimId`, so a
re-sent claim never creates a second alert.

## How the pieces fit together

```
POST /api/claims/screen
        |
  ScreeningController --> ScreeningService --> FraudRule(s)
                               |                 (EARLY_CLAIM, HIGH_AMOUNT_RATIO,
                               |                  LATE_REPORTING, REPEAT_POLICY_CLAIM)
                               |--> ClaimHistoryStore (in-memory; Redis in Task 2)
                               '--> AlertRepository --> PostgreSQL (alerts, alert_rules)
GET /api/alerts --> AlertController --> AlertService --> AlertRepository
```

A few things worth knowing:

- `ScreeningService` is the single screening path. It scores the rules first (pure logic, no I/O), then
  records history and persists the alert.
- Each rule extends `ConfiguredRule`, which reads `enabled`, `threshold`, and `weight` from
  `screening.rules.<CODE>` in `application.yml` — so thresholds and weights can be tuned without
  touching any code.
- All time windows are measured against the claim's `reportedAt` (the event time), never the wall clock,
  so replaying the same input always produces the same result.

## Package layout

```
com.claimguard
├── ClaimguardApplication.java
├── ScreeningProperties.java
├── api/        ScreeningController, AlertController, ApiExceptionHandler,
│               ClaimRequest, ScreeningResponse, AlertView, PageResponse
├── rules/      FraudRule, ConfiguredRule, EarlyClaimRule, HighAmountRatioRule,
│               LateReportingRule, RepeatPolicyClaimRule, TriggeredRule
├── screening/  ScreeningService, ClaimHistoryStore, InMemoryClaimHistoryStore
└── alerts/     AlertEntity, AlertRepository, AlertService,
                AlertStatus, LineOfBusiness, Masking
```

## Running it locally

You'll need Java 17+, Maven, and Docker. Docker is used for one thing only: running PostgreSQL.

```bash
cp .env.example .env        # optional — the defaults work fine locally
docker compose up -d        # start PostgreSQL on :5432
mvn spring-boot:run         # Flyway creates the schema on the first start
```

The service then listens on http://localhost:8080.

### Poking at it with curl

```bash
curl -s -X POST localhost:8080/api/claims/screen -H 'Content-Type: application/json' -d '{
  "claimId":"CLM-1","policyNumber":"POL-1","policyStartDate":"2026-01-01","sumInsured":500000,
  "lineOfBusiness":"MOTOR","vehicleRegNo":"KA01AB1234","providerId":"GAR-7","claimantMobile":"9876543210",
  "lossDate":"2026-01-07","reportedAt":"2026-02-01T10:00:00Z","claimedAmount":450000}'
# -> score 85, alertRaised true, 3 triggered rules (EARLY_CLAIM, HIGH_AMOUNT_RATIO, LATE_REPORTING)

curl -s 'localhost:8080/api/alerts?status=OPEN&lineOfBusiness=MOTOR&minScore=40&page=0&size=20'
# -> items sorted by score desc, newest first; claimantMobile is "******3210"
```

Anything invalid comes back as a `400` with `{"error":"Validation failed","fieldErrors":{...}}`.

> **If startup fails with `invalid value for parameter "TimeZone"`:** the JVM is handing PostgreSQL a
> legacy timezone id (for example `Asia/Calcutta`). Give the app a UTC clock instead, e.g.
> `mvn spring-boot:run -Dspring-boot.run.jvmArguments="-Duser.timezone=UTC"`.

## Configuration (application.yml)

| Key | Default | Fires when |
|---|---|---|
| `screening.alert-threshold` | 50 | total score `>=` this raises an alert |
| `screening.rules.EARLY_CLAIM` | threshold 30, weight 30 | loss `<=` 30 days after policy start |
| `screening.rules.HIGH_AMOUNT_RATIO` | threshold 0.8, weight 35 | claimed `>=` 0.8 x sum insured |
| `screening.rules.LATE_REPORTING` | threshold 15, weight 20 | reported `>` 15 days after loss |
| `screening.rules.REPEAT_POLICY_CLAIM` | threshold 2, weight 25 | `>=` 2 earlier claims on the policy in the 180 days before `reportedAt` |

Each rule also has an `enabled` flag. The 180-day window is a constant, and "earlier" means a strictly
earlier `reportedAt`.

**A worked example.** A claim whose loss is 6 days after the policy start fires `EARLY_CLAIM` at the
default threshold of 30. Change `screening.rules.EARLY_CLAIM.threshold` to `3`, restart, and the same
claim no longer fires. That behaviour is pinned by
`EarlyClaimRuleTest.changingConfiguredThresholdChangesOutcome`.

## Tests

The short version: **`mvn test` is completely self-contained — no database, no broker, no Docker.** The
Spring tests run against an in-memory H2 database in PostgreSQL-compatibility mode, and Flyway replays
the same migration against H2, so the real schema is exercised without a container. Once your
dependencies are cached, it even runs offline.

```bash
mvn test                    # unit tests only — no Docker, no database
mvn test -Pintegration      # also runs tests tagged @Tag("integration")
```

If a test genuinely needs Docker/Testcontainers or a live service, annotate it with `@Tag("integration")`.
The default build excludes that tag (see `test.excluded.groups` in `pom.xml`), and the `integration`
Maven profile clears the exclusion so those tests only run when you explicitly ask for them. There are
none yet — they arrive with Task 2 (Kafka and Redis). Every test also defaults to the in-memory `test`
profile, so no test can accidentally reach for a real database.

What's covered today:

- one test class per rule, each pinning the exact threshold boundary;
- `ScreeningServiceTest`: re-screening a claim keeps a single alert, and re-posts don't inflate the
  repeat-policy count for later claims;
- `AlertApiTest`: a screened claim is persisted and shows up in `GET /api/alerts`, including the sorting,
  filters, and pagination; validation errors are `400`s; the response body never contains the unmasked
  mobile;
- `DatasetScreeningTest`: screens `data/claims.jsonl` and prints the hit table below.

## Data generator and hit table

```bash
mvn -q test-compile exec:java -Dexec.classpathScope=test \
    -Dexec.mainClass=com.claimguard.screening.ClaimDataGenerator   # seed 42 -> data/claims.jsonl
mvn test -Dtest=DatasetScreeningTest                               # prints the table
```

The generator writes 692 claims ordered by `reportedAt`; 143 of them (20.7%) carry an injected pattern.
The label is only set on the claim that is expected to trigger the rule — for instance, the third claim
of a repeat-policy burst.

| Rule | Hits | Injected | Injected & detected |
|---|---|---|---|
| EARLY_CLAIM | 50 | 50 | 50 |
| HIGH_AMOUNT_RATIO | 50 | 50 | 50 |
| LATE_REPORTING | 45 | 45 | 45 |
| REPEAT_POLICY_CLAIM | 12 | 12 | 12 |
| REPEAT_VEHICLE | Task 2 | 15 | - |
| PROVIDER_SPIKE | Task 2 | 6 | - |

Alerts raised on the full file: 35. (If you change the generator or the defaults, re-run
`DatasetScreeningTest` and update this table.)

## Design decisions

- **Idempotency per `claimId`.** Rules ignore the claim's own id when reading history, `record()` is a
  no-op for a known `claimId`, and `alerts.claim_id` has a unique constraint (a concurrent duplicate
  insert is caught and ignored). Re-posting never creates a second alert or changes later claims'
  `REPEAT_POLICY_CLAIM` result.
- **PII.** Only the masked mobile (`******3210`) is persisted or returned. `ClaimRequest.toString()`
  omits the number and no code logs the request body. Validation errors never echo submitted values.
- **Rules live in separate classes** behind `FraudRule`, so their boundary semantics are explicit and
  individually tested.
- **Money math uses `BigDecimal`;** the ratio rule compares by multiplication, so the boundary is exact.
- **The queue index** `idx_alerts_queue (status, line_of_business, score DESC, reported_at DESC, id DESC)`
  matches the filter and sort of `GET /api/alerts`.
- **Triggered rules live in a child table** (`alert_rules`) via an `@ElementCollection` of an embeddable
  `TriggeredRule` record, which also works against H2 in the unit-test run.

## Where the project stands

- **Done:** the Task 1 requirements.
- **Not done:** Task 2 (Kafka consumer, Redis history store, `REPEAT_VEHICLE`, `PROVIDER_SPIKE`,
  dead-letter handling) and Task 3 (UI, `PATCH` disposition, audit table), plus the stretch goals.

## What I'd do next

Swap in a Redis-backed `ClaimHistoryStore` behind the existing interface, add a Kafka listener that calls
`ScreeningService`, and then build the investigator UI on top of an append-only audit table.
