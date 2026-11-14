import { useCallback, useEffect, useState } from "react";
import type { FormEvent } from "react";
import { getAlerts, updateAlert } from "./api";
import type { AlertFilters, AlertPage, ClaimAlert, DispositionRequest } from "./types";

const initialFilters: AlertFilters = {
  status: "OPEN",
  lineOfBusiness: "",
  minScore: "",
  page: 0,
  size: 10
};

export default function App() {
  const [filters, setFilters] = useState(initialFilters);
  const [result, setResult] = useState<AlertPage | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [expandedId, setExpandedId] = useState<number | null>(null);
  const [savingId, setSavingId] = useState<number | null>(null);
  const [message, setMessage] = useState("");

  const loadAlerts = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      setResult(await getAlerts(filters));
    } catch (err) {
      setError(err instanceof Error ? err.message : "Something went wrong.");
    } finally {
      setLoading(false);
    }
  }, [filters]);

  useEffect(() => {
    void loadAlerts();
  }, [loadAlerts]);

  function changeFilter(name: keyof AlertFilters, value: string) {
    setFilters((current) => ({ ...current, [name]: name === "size" ? Number(value) : value, page: 0 }));
  }

  async function submitDisposition(alert: ClaimAlert, body: DispositionRequest) {
    setSavingId(alert.id);
    setMessage("");
    setError("");
    try {
      await updateAlert(alert.id, body);
      setMessage(`Claim ${alert.claimId} was updated.`);
      setExpandedId(null);
      await loadAlerts();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not update alert.");
    } finally {
      setSavingId(null);
    }
  }

  return (
    <main className="page">
      <header className="topbar">
        <div>
          <p className="eyebrow">CLAIMGUARD · SIU WORKSPACE</p>
          <h1>Investigator queue</h1>
          <p className="subtitle">Review flagged claims and record a decision.</p>
        </div>
        <span className="secure-label">Mobile numbers are masked</span>
      </header>

      <section className="panel filters" aria-label="Alert filters">
        <label>
          Status
          <select value={filters.status} onChange={(e) => changeFilter("status", e.target.value)}>
            <option value="">All statuses</option>
            <option value="OPEN">Open</option>
            <option value="CONFIRMED_FRAUD">Confirmed fraud</option>
            <option value="FALSE_POSITIVE">False positive</option>
          </select>
        </label>
        <label>
          Line of business
          <select value={filters.lineOfBusiness} onChange={(e) => changeFilter("lineOfBusiness", e.target.value)}>
            <option value="">All lines</option>
            <option value="MOTOR">Motor</option>
            <option value="HEALTH">Health</option>
          </select>
        </label>
        <label>
          Minimum score
          <input
            type="number"
            min="0"
            max="100"
            value={filters.minScore}
            onChange={(e) => changeFilter("minScore", e.target.value)}
            placeholder="Any score"
          />
        </label>
        <label>
          Rows per page
          <select value={filters.size} onChange={(e) => changeFilter("size", e.target.value)}>
            <option value="10">10</option>
            <option value="20">20</option>
            <option value="50">50</option>
          </select>
        </label>
      </section>

      {message && <p className="notice success" role="status">{message}</p>}
      {error && <p className="notice error" role="alert">{error}</p>}

      <section className="panel">
        <div className="table-heading">
          <div>
            <h2>Alerts</h2>
            <p>{result ? `${result.totalElements} alerts found` : "Loading alerts…"}</p>
          </div>
          <button className="secondary-button" onClick={() => void loadAlerts()}>Refresh</button>
        </div>

        {loading ? (
          <p className="empty">Loading alerts…</p>
        ) : result && result.items.length > 0 ? (
          <>
            <div className="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>Claim ID</th>
                    <th>Policy</th>
                    <th>Mobile</th>
                    <th>Score</th>
                    <th>Rules</th>
                    <th>Status</th>
                    <th>Details</th>
                  </tr>
                </thead>
                <tbody>
                  {result.items.map((alert) => (
                    <tr key={alert.id}>
                      <td className="strong">{alert.claimId}</td>
                      <td>{alert.policyNumber}</td>
                      <td>{alert.claimantMobile}</td>
                      <td><span className="score">{alert.score}</span></td>
                      <td>{alert.triggeredRules.map((rule) => rule.code).join(", ") || "—"}</td>
                      <td><span className={`status status-${alert.status.toLowerCase()}`}>{alert.status.replace(/_/g, " ")}</span></td>
                      <td>
                        <button
                          className="text-button"
                          onClick={() => setExpandedId(expandedId === alert.id ? null : alert.id)}
                          aria-expanded={expandedId === alert.id}
                        >
                          {expandedId === alert.id ? "Hide" : "View"}
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            {result.items.filter((alert) => alert.id === expandedId).map((alert) => (
              <div className="detail-panel" key={alert.id}>
                <div className="detail-heading">
                  <div>
                    <h3>Claim details · {alert.claimId}</h3>
                    <p>Triggered rules and their reasons</p>
                  </div>
                  <button className="text-button" onClick={() => setExpandedId(null)}>Close</button>
                </div>
                {alert.triggeredRules.length ? (
                  <ul className="rule-list">
                    {alert.triggeredRules.map((rule) => (
                      <li key={rule.code}>
                        <strong>{rule.code}</strong>
                        <span>{rule.reason}</span>
                      </li>
                    ))}
                  </ul>
                ) : <p>No triggered rule details were returned.</p>}
                {alert.status === "OPEN" && (
                  <DispositionForm
                    busy={savingId === alert.id}
                    onSubmit={(body) => void submitDisposition(alert, body)}
                  />
                )}
              </div>
            ))}

            <div className="pagination">
              <span>Page {filters.page + 1} of {Math.max(1, result.totalPages)}</span>
              <div>
                <button
                  className="secondary-button"
                  disabled={filters.page <= 0}
                  onClick={() => setFilters((current) => ({ ...current, page: current.page - 1 }))}
                >Previous</button>
                <button
                  className="secondary-button"
                  disabled={filters.page + 1 >= result.totalPages}
                  onClick={() => setFilters((current) => ({ ...current, page: current.page + 1 }))}
                >Next</button>
              </div>
            </div>
          </>
        ) : (
          <p className="empty">No alerts match these filters.</p>
        )}
      </section>
      <footer>Claimguard · Investigator workspace</footer>
    </main>
  );
}

function DispositionForm({
  busy,
  onSubmit
}: {
  busy: boolean;
  onSubmit: (body: DispositionRequest) => void;
}) {
  const [status, setStatus] = useState<DispositionRequest["status"]>("CONFIRMED_FRAUD");
  const [note, setNote] = useState("");
  const [investigator, setInvestigator] = useState("");

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!note.trim() || !investigator.trim()) return;
    onSubmit({ status, note: note.trim(), investigator: investigator.trim() });
  }

  return (
    <form className="disposition-form" onSubmit={submit}>
      <h3>Record decision</h3>
      <div className="form-grid">
        <label>
          Decision
          <select value={status} onChange={(e) => setStatus(e.target.value as DispositionRequest["status"])}>
            <option value="CONFIRMED_FRAUD">Confirmed fraud</option>
            <option value="FALSE_POSITIVE">False positive</option>
          </select>
        </label>
        <label>
          Investigator
          <input value={investigator} onChange={(e) => setInvestigator(e.target.value)} required placeholder="Your name" />
        </label>
      </div>
      <label>
        Note <span className="required">Required</span>
        <textarea value={note} onChange={(e) => setNote(e.target.value)} required rows={3} placeholder="Add a short reason for this decision" />
      </label>
      <button className="primary-button" type="submit" disabled={busy || !note.trim() || !investigator.trim()}>
        {busy ? "Saving…" : "Save decision"}
      </button>
    </form>
  );
}