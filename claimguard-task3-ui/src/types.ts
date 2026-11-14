export type AlertStatus = "OPEN" | "CONFIRMED_FRAUD" | "FALSE_POSITIVE";
export type LineOfBusiness = "MOTOR" | "HEALTH";

export interface TriggeredRule {
  code: string;
  reason: string;
}

export interface ClaimAlert {
  id: number;
  claimId: string;
  policyNumber: string;
  claimantMobile: string;
  score: number;
  status: AlertStatus;
  lineOfBusiness: LineOfBusiness;
  triggeredRules: TriggeredRule[];
}

export interface AlertPage {
  items: ClaimAlert[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

export interface AlertFilters {
  status: string;
  lineOfBusiness: string;
  minScore: string;
  page: number;
  size: number;
}

export interface DispositionRequest {
  status: "CONFIRMED_FRAUD" | "FALSE_POSITIVE";
  note: string;
  investigator: string;
}