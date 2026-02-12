import sqlite3 from "sqlite3";

const db = new sqlite3.Database("sync_gateway.db");

export function initDb(): Promise<void> {
  return new Promise((resolve, reject) => {
    db.serialize(() => {
      db.run(
        `CREATE TABLE IF NOT EXISTS operation_log (
          op_id TEXT PRIMARY KEY,
          table_name TEXT NOT NULL,
          record_id TEXT NOT NULL,
          operation TEXT NOT NULL,
          payload_json TEXT NOT NULL,
          received_at INTEGER NOT NULL
        )`
      );

      db.run(
        `CREATE TABLE IF NOT EXISTS claim_status (
          claim_id TEXT PRIMARY KEY,
          status TEXT NOT NULL,
          rejection_reason TEXT,
          updated_at INTEGER NOT NULL
        )`,
        (err) => {
          if (err) reject(err);
          else resolve();
        }
      );
    });
  });
}

export function insertOperation(input: {
  opId: string;
  tableName: string;
  recordId: string;
  operation: string;
  payloadJson: string;
}): Promise<void> {
  return new Promise((resolve, reject) => {
    db.run(
      `INSERT OR REPLACE INTO operation_log (op_id, table_name, record_id, operation, payload_json, received_at)
       VALUES (?, ?, ?, ?, ?, ?)`,
      [input.opId, input.tableName, input.recordId, input.operation, input.payloadJson, Date.now()],
      (err) => {
        if (err) reject(err);
        else resolve();
      }
    );
  });
}

export function getClaimStatus(claimId: string): Promise<{ claimId: string; status: string; rejectionReason: string | null } | null> {
  return new Promise((resolve, reject) => {
    db.get(
      `SELECT claim_id as claimId, status, rejection_reason as rejectionReason
       FROM claim_status WHERE claim_id = ?`,
      [claimId],
      (err, row: { claimId: string; status: string; rejectionReason: string | null } | undefined) => {
        if (err) reject(err);
        else resolve(row ?? null);
      }
    );
  });
}

export function upsertClaimStatus(input: {
  claimId: string;
  status: string;
  rejectionReason?: string;
}): Promise<void> {
  return new Promise((resolve, reject) => {
    db.run(
      `INSERT OR REPLACE INTO claim_status (claim_id, status, rejection_reason, updated_at)
       VALUES (?, ?, ?, ?)`,
      [input.claimId, input.status, input.rejectionReason ?? null, Date.now()],
      (err) => {
        if (err) reject(err);
        else resolve();
      }
    );
  });
}
