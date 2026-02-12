export type RiskLevel = "LOW" | "MODERATE" | "HIGH";

export type Beneficiary = {
  id: string;
  abhaId?: string;
  name: string;
  phone?: string;
  villageCode?: string;
  createdAt: number;
  updatedAt: number;
  deleted?: 0 | 1;
};

export type NewbornCase = {
  id: string;
  beneficiaryId: string;
  birthDate: string;
  birthWeightKg?: number;
  riskLevel: RiskLevel;
  sncuReferralRequired: 0 | 1;
  createdAt: number;
  updatedAt: number;
};

export type HBNCVisit = {
  id: string;
  newbornCaseId: string;
  visitDay: 3 | 7 | 14 | 21 | 28 | 42;
  dueDate: string;
  completedAt?: string;
  findingsJson?: string;
  createdAt: number;
  updatedAt: number;
};

export type IncentiveClaimStatus =
  | "DRAFT"
  | "SUBMITTED"
  | "APPROVED"
  | "REJECTED"
  | "PAID";

export type IncentiveClaim = {
  id: string;
  ashaId: string;
  serviceType: string;
  amountRupees: number;
  status: IncentiveClaimStatus;
  rejectionReason?: string;
  relatedEntityId?: string;
  createdAt: number;
  updatedAt: number;
};

export type SyncOperation = "INSERT" | "UPDATE" | "DELETE";

export type SyncOutboxRecord = {
  opId: string;
  tableName: string;
  recordId: string;
  operation: SyncOperation;
  payloadJson: string;
  baseVersion?: number;
  clientTs: number;
  syncStatus: "PENDING" | "SENT" | "ACK" | "FAILED" | "CONFLICT";
};
