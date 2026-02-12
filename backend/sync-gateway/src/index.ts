import cors from "cors";
import express from "express";
import { initDb, insertOperation, getClaimStatus, upsertClaimStatus } from "./db.js";

const app = express();
app.use(cors());
app.use(express.json({ limit: "2mb" }));

app.get("/health", (_req, res) => {
  res.json({ ok: true, service: "sync-gateway" });
});

app.post("/sync/push", async (req, res) => {
  const operations = Array.isArray(req.body?.operations) ? req.body.operations : [];
  const results: Array<{ opId: string; status: "ACK" | "FAILED" }> = [];

  for (const op of operations) {
    try {
      await insertOperation({
        opId: op.op_id,
        tableName: op.table_name,
        recordId: op.record_id,
        operation: op.operation,
        payloadJson: op.payload_json
      });

      if (op.table_name === "incentive_claim") {
        await upsertClaimStatus({
          claimId: op.record_id,
          status: "SUBMITTED"
        });
      }

      results.push({ opId: op.op_id, status: "ACK" });
    } catch {
      results.push({ opId: op.op_id, status: "FAILED" });
    }
  }

  res.json({ results });
});

app.get("/claims/:claimId", async (req, res) => {
  const data = await getClaimStatus(req.params.claimId);
  if (!data) {
    res.status(404).json({ message: "claim not found" });
    return;
  }
  res.json(data);
});

const port = Number(process.env.PORT ?? 8787);

initDb().then(() => {
  app.listen(port, () => {
    console.log(`sync-gateway listening on ${port}`);
  });
});
