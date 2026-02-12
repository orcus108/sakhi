import { all, run } from "./client";
import type { HBNCVisit, IncentiveClaim } from "@asha-g/shared-types";

export type DigitizedRecord = {
  id: string;
  payload: Record<string, string | number | null>;
  createdAt: number;
};

function enqueueSync(tableName: string, recordId: string, operation: "INSERT" | "UPDATE", payload: unknown): void {
  const opId = `${tableName}-${recordId}-${Date.now()}`;
  run(
    `INSERT INTO sync_outbox (op_id, table_name, record_id, operation, payload_json, client_ts, sync_status)
     VALUES (?, ?, ?, ?, ?, ?, 'PENDING')`,
    [opId, tableName, recordId, operation, JSON.stringify(payload), Date.now()]
  );
}

export async function upsertDigitizedRecord(payload: Record<string, string | number | null>): Promise<void> {
  const id = `dig-${Date.now()}`;
  const now = Date.now();
  run(
    `INSERT INTO digitized_record (id, payload_json, created_at, updated_at)
     VALUES (?, ?, ?, ?)`,
    [id, JSON.stringify(payload), now, now]
  );
  enqueueSync("digitized_record", id, "INSERT", payload);
}

export async function listDigitizedRecords(limit = 20): Promise<DigitizedRecord[]> {
  const rows = all<{ id: string; payload_json: string; created_at: number }>(
    `SELECT id, payload_json, created_at FROM digitized_record ORDER BY created_at DESC LIMIT ?`,
    [limit]
  );

  return rows.map((r) => ({
    id: r.id,
    payload: JSON.parse(r.payload_json) as Record<string, string | number | null>,
    createdAt: r.created_at
  }));
}

export async function searchKnowledge(query: string): Promise<Array<{ content: string }>> {
  const cleaned = query
    .replace(/[^a-zA-Z0-9\s]/g, " ")
    .trim()
    .split(/\s+/)
    .filter(Boolean)
    .join(" OR ");

  if (!cleaned) return [];

  const rows = all<{ content: string }>(
    `SELECT content FROM kb_chunk_fts WHERE kb_chunk_fts MATCH ? LIMIT 5`,
    [cleaned]
  );
  return rows;
}

export async function createVisit(input: HBNCVisit): Promise<void> {
  run(
    `INSERT OR REPLACE INTO hbnc_visit (id, newborn_case_id, visit_day, due_date, completed_at, findings_json, created_at, updated_at)
     VALUES (?, ?, ?, ?, ?, ?, ?, ?)`,
    [input.id, input.newbornCaseId, input.visitDay, input.dueDate, input.completedAt ?? null, input.findingsJson ?? null, input.createdAt, input.updatedAt]
  );
  enqueueSync("hbnc_visit", input.id, "INSERT", input);
}

export async function listVisits(): Promise<HBNCVisit[]> {
  return all<HBNCVisit>(
    `SELECT
      id,
      newborn_case_id as newbornCaseId,
      visit_day as visitDay,
      due_date as dueDate,
      completed_at as completedAt,
      findings_json as findingsJson,
      created_at as createdAt,
      updated_at as updatedAt
    FROM hbnc_visit ORDER BY due_date ASC`
  );
}

export async function createClaim(input: IncentiveClaim): Promise<void> {
  run(
    `INSERT OR REPLACE INTO incentive_claim (id, asha_id, service_type, amount_rupees, status, rejection_reason, related_entity_id, created_at, updated_at)
     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)`,
    [
      input.id,
      input.ashaId,
      input.serviceType,
      input.amountRupees,
      input.status,
      input.rejectionReason ?? null,
      input.relatedEntityId ?? null,
      input.createdAt,
      input.updatedAt
    ]
  );
  enqueueSync("incentive_claim", input.id, "INSERT", input);
}

export async function listClaims(ashaId: string): Promise<IncentiveClaim[]> {
  return all<IncentiveClaim>(
    `SELECT
      id,
      asha_id as ashaId,
      service_type as serviceType,
      amount_rupees as amountRupees,
      status,
      rejection_reason as rejectionReason,
      related_entity_id as relatedEntityId,
      created_at as createdAt,
      updated_at as updatedAt
    FROM incentive_claim
    WHERE asha_id = ?
    ORDER BY created_at DESC`,
    [ashaId]
  );
}

export async function getPendingOutbox(limit = 50): Promise<Array<{
  op_id: string;
  table_name: string;
  record_id: string;
  operation: string;
  payload_json: string;
}>> {
  return all(
    `SELECT op_id, table_name, record_id, operation, payload_json
     FROM sync_outbox
     WHERE sync_status = 'PENDING'
     ORDER BY client_ts ASC
     LIMIT ?`,
    [limit]
  );
}

export async function getPendingOutboxCount(): Promise<number> {
  const rows = all<{ count: number }>(`SELECT COUNT(*) as count FROM sync_outbox WHERE sync_status = 'PENDING'`);
  return rows[0]?.count ?? 0;
}

export async function markOutboxAck(opId: string): Promise<void> {
  run(`UPDATE sync_outbox SET sync_status = 'ACK' WHERE op_id = ?`, [opId]);
}

export async function markOutboxFailed(opId: string): Promise<void> {
  run(`UPDATE sync_outbox SET sync_status = 'FAILED' WHERE op_id = ?`, [opId]);
}
