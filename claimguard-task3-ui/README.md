# Claimguard Investigator UI

A small React + TypeScript screen for SIU investigators. It uses the existing Claimguard alert API.

## Features

- Alert queue with status, line-of-business and minimum-score filters.
- Server-side pagination.
- Claim ID, policy number, masked mobile, score and triggered rule codes.
- Expandable alert details showing each rule's reason.
- Disposition form for `CONFIRMED_FRAUD` or `FALSE_POSITIVE`.
- Mandatory note and investigator name.
- Mobile masking in the UI as an extra safeguard.
- Vitest and React Testing Library tests.

## Requirements

- Node.js 18 or later
- npm
- Claimguard Spring Boot backend running on `http://localhost:8080`

## Start the UI

First, start the backend. Then, from this folder:

```bash
npm install
npm run dev
```

Open the local URL printed by Vite (usually `http://localhost:5173`).

Vite forwards `/api` requests to `http://localhost:8080`, so local development does not need a separate CORS setup.

## Run tests

```bash
npm test
```

## Build

```bash
npm run build
```

## API expected by the UI

### Queue

```http
GET /api/alerts?status=OPEN&lineOfBusiness=MOTOR&minScore=40&page=0&size=10
```

The UI expects a response shaped like:

```json
{
  "items": [
    {
      "id": 1,
      "claimId": "CLM-101",
      "policyNumber": "POL-101",
      "claimantMobile": "******3210",
      "score": 85,
      "status": "OPEN",
      "lineOfBusiness": "MOTOR",
      "triggeredRules": [
        {
          "code": "EARLY_CLAIM",
          "reason": "Loss occurred 6 days after policy start (limit 30)."
        }
      ]
    }
  ],
  "totalElements": 1,
  "totalPages": 1,
  "number": 0,
  "size": 10
}
```

The UI also accepts Spring Page-style `content`, `total`, and `page` fields.

### Disposition

```http
PATCH /api/alerts/1
Content-Type: application/json
```

```json
{
  "status": "CONFIRMED_FRAUD",
  "note": "Evidence reviewed",
  "investigator": "Alex"
}
```

The backend must accept only `CONFIRMED_FRAUD` and `FALSE_POSITIVE` when the current status is `OPEN`. It must reject a blank note, return `409 Conflict` if the alert is already closed, and write exactly one row to `alert_audit` in the same database transaction as the status change.

Suggested audit fields: `alert_id`, `investigator`, `changed_at`, `from_status`, `to_status`, `note`. The audit table should be append-only: do not update or delete old audit rows.

## Notes

- The UI masks mobile values before displaying them, even if the API accidentally returns the full number.
- The backend remains responsible for masking API responses and never logging full mobile numbers.
- The `PATCH` endpoint and the append-only `alert_audit` migration are implemented in the Spring Boot
  project alongside this UI. See the [root README](../README.md) for the backend design, configuration,
  and test commands.
