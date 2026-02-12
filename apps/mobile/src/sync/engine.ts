import { getPowerMode } from "../services/battery/powerMode";
import { getPendingOutbox, markOutboxAck, markOutboxFailed } from "../db/repository";

type PushResponse = { opId: string; status: "ACK" | "FAILED" };

async function pushBatch(ops: Array<{ op_id: string; table_name: string; record_id: string; operation: string; payload_json: string }>): Promise<PushResponse[]> {
  try {
    const res = await fetch("http://localhost:8787/sync/push", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ operations: ops })
    });

    if (!res.ok) throw new Error(`push failed: ${res.status}`);
    const body = await res.json();
    return body.results as PushResponse[];
  } catch {
    return ops.map((op) => ({ opId: op.op_id, status: "FAILED" }));
  }
}

export async function runSyncOnce(): Promise<{ ack: number; failed: number }> {
  const power = await getPowerMode();
  if (power === "low_power") return { ack: 0, failed: 0 };

  const ops = await getPendingOutbox(100);
  if (ops.length === 0) return { ack: 0, failed: 0 };

  const results = await pushBatch(ops);

  let ack = 0;
  let failed = 0;
  for (const r of results) {
    if (r.status === "ACK") {
      ack += 1;
      await markOutboxAck(r.opId);
    } else {
      failed += 1;
      await markOutboxFailed(r.opId);
    }
  }

  return { ack, failed };
}
