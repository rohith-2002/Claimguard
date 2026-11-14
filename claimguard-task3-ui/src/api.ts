import type { AlertFilters, AlertPage, ClaimAlert, DispositionRequest } from "./types";

function maskMobile(value: string): string {
  if (!value) return "—";
  if (value.includes("*")) return value;
  const digits = value.replace(/\D/g, "");
  return digits.length <= 4 ? "******" : `******${digits.slice(-4)}`;
}

// Apply masking in the UI as an extra safeguard, even if the API sends digits.
export function safeAlert(alert: ClaimAlert): ClaimAlert {
  return { ...alert, claimantMobile: maskMobile(alert.claimantMobile) };
}

export async function getAlerts(filters: AlertFilters): Promise<AlertPage> {
  const params = new URLSearchParams({
    page: String(filters.page),
    size: String(filters.size)
  });
  if (filters.status) params.set("status", filters.status);
  if (filters.lineOfBusiness) params.set("lineOfBusiness", filters.lineOfBusiness);
  if (filters.minScore) params.set("minScore", filters.minScore);

  const response = await fetch(`http://localhost:8080/api/alerts?${params.toString()}`);
  if (!response.ok) throw new Error(`Could not load alerts (${response.status})`);

  const data = await response.json();
  const items: ClaimAlert[] = (data.items ?? data.content ?? []).map(safeAlert);
  return {
    items,
    totalElements: data.totalElements ?? data.total ?? items.length,
    totalPages: data.totalPages ?? Math.max(1, Math.ceil((data.totalElements ?? items.length) / (data.size ?? filters.size))),
    number: data.number ?? data.page ?? filters.page,
    size: data.size ?? filters.size
  };
}

export async function updateAlert(id: number, body: DispositionRequest): Promise<void> {
  const response = await fetch(`http://localhost:8080/api/alerts/${id}`, {
    method: "PATCH",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body)
  });
  if (!response.ok) {
    if (response.status === 409) throw new Error("This alert has already been closed.");
    if (response.status === 400) throw new Error("Please provide a note and investigator name.");
    throw new Error(`Could not update alert (${response.status})`);
  }
}