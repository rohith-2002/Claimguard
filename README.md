# Claimguard

Screens every incoming motor and health insurance claim against configurable fraud rules, stores an
alert with per-rule reasons when the score crosses a threshold, and exposes a paginated alert queue for
the SIU.

**Status:** Task 1 (REST screening, rules, alerts, queue API) - done. Task 2 (Kafka + Redis) and Task 3
(React UI, disposition, audit) - not started.

## Architecture

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

- `ScreeningService` scores the rules (weights, no I/O) and adds history and persistence.
- Rules extend `ConfiguredRule`, which reads `enabled / threshold / weight` from `screening.rules.<CODE>`.
- Time windows use the claim's `reportedAt` (event time), never the wall clock, so replays are deterministic.

### Packages

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

## Run

Requires Java 17+, Maven, Docker.

```bash
cp .env.example .env            # optional, defaults work locally
docker compose up -d            # PostgreSQL
mvn spring-boot:run             # Flyway creates the schema on startup
```

### curl

```bash
curl -s -X POST localhost:8080/api/claims/screen -H 'Content-Type: application/json' -d '{
  "claimId":"CLM-1","policyNumber":"POL-1","policyStartDate":"2026-01-01","sumInsured":500000,
  "lineOfBusiness":"MOTOR","vehicleRegNo":"KA01AB1234","providerId":"GAR-7","claimantMobile":"9876543210",
  "lossDate":"2026-01-07","reportedAt":"2026-02-01T10:00:00Z","claimedAmount":450000}'
# -> score 85, alertRaised true, 3 triggered rules (EARLY_CLAIM, HIGH_AMOUNT_RATIO, LATE_REPORTING)

curl -s 'localhost:8080/api/alerts?status=OPEN&lineOfBusiness=MOTOR&minScore=40&page=0&size=20'
# -> items sorted by score desc, newest first; claimantMobile is "******3210"
```

Invalid input returns `400 {"error":"Validation failed","fieldErrors":{...}}`.

## Configuration (application.yml)

| Key | Default | Fires when |
|---|---|---|
| `screening.alert-threshold` | 50 | total score `>=` this raises an alert |
| `screening.rules.EARLY_CLAIM` | threshold 30, weight 30 | loss `<=` 30 days after policy start |
| `screening.rules.HIGH_AMOUNT_RATIO` | threshold 0.8, weight 35 | claimed `>=` 0.8 x sum insured |
| `screening.rules.LATE_REPORTING` | threshold 15, weight 20 | reported `>` 15 days after loss |
| `screening.rules.REPEAT_POLICY_CLAIM` | threshold 2, weight 25 | `>=` 2 earlier claims on the policy in the 180 days before `reportedAt` |

Each rule also has `enabled`. The 180-day window is a constant. "Earlier" means a strictly earlier `reportedAt`.

**Documented example:** a claim whose loss is 6 days after policy start fires `EARLY_CLAIM` at the default
threshold 30. Set `screening.rules.EARLY_CLAIM.threshold: 3`, restart, and the same claim no longer fires
(`EarlyClaimRuleTest.changingConfiguredThresholdChangesOutcome`).

## Design decisions

- **Idempotency per claimId.** Rules ignore the claim's own id when reading history, `record()` is a no-op for a
  known claimId, and `alerts.claim_id` has a unique constraint (a concurrent duplicate insert is caught and
  ignored). Re-posting never creates a second alert or changes later claims' `REPEAT_POLICY_CLAIM` result.
- **PII.** Only the masked mobile (`******3210`) is persisted or returned. `ClaimRequest.toString()` omits the
  number and no code logs the request body. Validation errors never echo submitted values.
- **Rules in separate classes** behind `FraudRule`; boundary semantics are explicit and tested.
- **Money math** uses `BigDecimal`; the ratio rule compares by multiplication so the boundary is exact.
- **Queue index** `idx_alerts_queue (status, line_of_business, score DESC, reported_at DESC, id DESC)` matches the
  filter + sort of `GET /api/alerts`.
- Triggered rules live in a child table (`alert_rules`), which also works with H2 in the unit-test run.

## Tests

```bash
mvn test                  # offline, no Docker (H2 in PostgreSQL mode); integration-tagged tests are excluded
mvn test -Pintegration    # also runs @Tag("integration") tests (Docker/Testcontainers) - Task 2
```

- One test class per rule, each with the exact-threshold case.
- `ScreeningServiceTest`: re-screening keeps one alert; reposts do not inflate the repeat-policy count.
- `AlertApiTest`: screen -> persisted -> visible in `GET /api/alerts`; sorting, filters, pagination;
  400 field errors; the response body never contains the unmasked mobile.
- `DatasetScreeningTest`: screens `data/claims.jsonl` and prints the table below.

## Data generator and hit table

```bash
mvn -q test-compile exec:java -Dexec.classpathScope=test \
    -Dexec.mainClass=com.claimguard.screening.ClaimDataGenerator     # seed 42 -> data/claims.jsonl
mvn test -Dtest=DatasetScreeningTest                              # prints the table
```

692 claims ordered by `reportedAt`, 143 (20.7%) carry an injected pattern. A label is set on the claim that is
expected to trigger the rule (e.g. the third claim of a repeat-policy burst).

| Rule | Hits | Injected | Injected & detected |
|---|---|---|---|
| EARLY_CLAIM | 50 | 50 | 50 |
| HIGH_AMOUNT_RATIO | 50 | 50 | 50 |
| LATE_REPORTING | 45 | 45 | 45 |
| REPEAT_POLICY_CLAIM | 12 | 12 | 12 |
| REPEAT_VEHICLE | Task 2 | 15 | - |
| PROVIDER_SPIKE | Task 2 | 6 | - |

Alerts raised on the full file: 35. (Re-run `DatasetScreeningTest` and paste its output if you change the
generator or the defaults.)

## Done / partial / not done

- Done: Task 1 requirements.
- Not done: Task 2 (Kafka consumer, Redis history store, REPEAT_VEHICLE, PROVIDER_SPIKE, DLT), Task 3 (UI,
  PATCH disposition, audit table), stretch goals.

## What I would do next

Redis-backed `ClaimHistoryStore` behind the existing interface, Kafka listener calling `ScreeningService`,
then the investigator UI with an append-only audit table.
