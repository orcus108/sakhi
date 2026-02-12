import { getPendingOutbox, markOutboxStatus } from "../db/store";
export async function runSyncOnce() {
    const ops = getPendingOutbox(100);
    if (ops.length === 0)
        return { ack: 0, failed: 0 };
    try {
        const res = await fetch("http://localhost:8787/sync/push", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ operations: ops.map((o) => ({
                    op_id: o.opId,
                    table_name: o.tableName,
                    record_id: o.recordId,
                    operation: o.operation,
                    payload_json: o.payloadJson
                })) })
        });
        if (!res.ok)
            throw new Error("sync failed");
        const body = (await res.json());
        let ack = 0;
        let failed = 0;
        for (const result of body.results) {
            markOutboxStatus(result.opId, result.status);
            if (result.status === "ACK")
                ack += 1;
            else
                failed += 1;
        }
        return { ack, failed };
    }
    catch {
        for (const op of ops)
            markOutboxStatus(op.opId, "FAILED");
        return { ack: 0, failed: ops.length };
    }
}
