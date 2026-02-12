import { useMemo, useState } from "react";
import { Card } from "./components/Card";
import { digitizeTextToRecord } from "./features/ocr/pipeline";
import { askAshabotFromText } from "./features/rag/ashabot";
import { computeRisk, scheduleHbnc } from "./features/hbnc/scheduler";
import { createIncentiveDraft, getLedgerSummary } from "./features/incentives/ledger";
import { runSyncOnce } from "./sync/engine";
import { listVisits } from "./db/store";

export default function App() {
  const [registerInput, setRegisterInput] = useState("name: Sita\nvillage: Rampur\nnewborn_weight: 1.7");
  const [digitized, setDigitized] = useState("");
  const [query, setQuery] = useState("What are danger signs for a newborn?");
  const [botAnswer, setBotAnswer] = useState("");
  const [birthDate, setBirthDate] = useState("2026-02-10");
  const [weight, setWeight] = useState("1.7");
  const [syncResult, setSyncResult] = useState("");
  const [ledgerTick, setLedgerTick] = useState(0);

  const visits = useMemo(() => listVisits(), [digitized, syncResult]);
  const risk = useMemo(() => computeRisk(Number(weight)), [weight]);
  const ledger = useMemo(() => getLedgerSummary("asha-001"), [ledgerTick]);

  return (
    <main className="container">
      <header className="hero">
        <h1>Sakhi Web</h1>
        <p>Offline-first companion for digitization, guidance, HBNC scheduling, and incentive tracking.</p>
      </header>

      <Card title="1. Multimodal Digitization" subtitle="Paste OCR text from register/diary to structured output.">
        <textarea value={registerInput} onChange={(e) => setRegisterInput(e.target.value)} rows={6} />
        <button
          onClick={async () => {
            const record = await digitizeTextToRecord(registerInput);
            setDigitized(JSON.stringify(record, null, 2));
          }}
        >
          Digitize
        </button>
        {digitized ? <pre>{digitized}</pre> : null}
      </Card>

      <Card title="2. ASHABot (Offline RAG)" subtitle="Ask newborn danger-sign queries.">
        <input value={query} onChange={(e) => setQuery(e.target.value)} />
        <button onClick={async () => setBotAnswer(await askAshabotFromText(query))}>Ask</button>
        {botAnswer ? <pre>{botAnswer}</pre> : null}
      </Card>

      <Card title="3. HBNC Scheduler + Risk" subtitle="Auto-generate Day 3/7/14/21/28/42 visits.">
        <div className="row">
          <label>
            Birth Date
            <input type="date" value={birthDate} onChange={(e) => setBirthDate(e.target.value)} />
          </label>
          <label>
            Birth Weight (kg)
            <input value={weight} onChange={(e) => setWeight(e.target.value)} />
          </label>
        </div>
        <div className="badge">Risk: {risk.riskLevel} {risk.sncuReferral ? "(SNCU Referral)" : ""}</div>
        <button onClick={() => scheduleHbnc("web-case-001", birthDate)}>Generate HBNC Visits</button>
        <ul>
          {visits.map((v) => (
            <li key={v.id}>Day {v.visitDay}: {v.dueDate}</li>
          ))}
        </ul>
      </Card>

      <Card title="4. Incentive Ledger" subtitle="Single-window draft claim tracking.">
        <button
          onClick={() => {
            createIncentiveDraft({ ashaId: "asha-001", serviceType: "ANC" });
            setLedgerTick((x) => x + 1);
          }}
        >
          Add ANC Draft (₹300)
        </button>
        <div className="row statRow">
          <div>Total: ₹{ledger.total}</div>
          <div>Pending: ₹{ledger.pending}</div>
          <div>Paid: ₹{ledger.paid}</div>
        </div>
      </Card>

      <Card title="5. Offline Sync" subtitle="Push queued operations to sync-gateway.">
        <button
          onClick={async () => {
            const out = await runSyncOnce();
            setSyncResult(`ACK: ${out.ack}, FAILED: ${out.failed}`);
          }}
        >
          Sync Now
        </button>
        {syncResult ? <p>{syncResult}</p> : null}
      </Card>
    </main>
  );
}
