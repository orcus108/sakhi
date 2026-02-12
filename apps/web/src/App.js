import { jsx as _jsx, jsxs as _jsxs } from "react/jsx-runtime";
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
    return (_jsxs("main", { className: "container", children: [_jsxs("header", { className: "hero", children: [_jsx("h1", { children: "Sakhi Web" }), _jsx("p", { children: "Offline-first companion for digitization, guidance, HBNC scheduling, and incentive tracking." })] }), _jsxs(Card, { title: "1. Multimodal Digitization", subtitle: "Paste OCR text from register/diary to structured output.", children: [_jsx("textarea", { value: registerInput, onChange: (e) => setRegisterInput(e.target.value), rows: 6 }), _jsx("button", { onClick: async () => {
                            const record = await digitizeTextToRecord(registerInput);
                            setDigitized(JSON.stringify(record, null, 2));
                        }, children: "Digitize" }), digitized ? _jsx("pre", { children: digitized }) : null] }), _jsxs(Card, { title: "2. ASHABot (Offline RAG)", subtitle: "Ask newborn danger-sign queries.", children: [_jsx("input", { value: query, onChange: (e) => setQuery(e.target.value) }), _jsx("button", { onClick: async () => setBotAnswer(await askAshabotFromText(query)), children: "Ask" }), botAnswer ? _jsx("pre", { children: botAnswer }) : null] }), _jsxs(Card, { title: "3. HBNC Scheduler + Risk", subtitle: "Auto-generate Day 3/7/14/21/28/42 visits.", children: [_jsxs("div", { className: "row", children: [_jsxs("label", { children: ["Birth Date", _jsx("input", { type: "date", value: birthDate, onChange: (e) => setBirthDate(e.target.value) })] }), _jsxs("label", { children: ["Birth Weight (kg)", _jsx("input", { value: weight, onChange: (e) => setWeight(e.target.value) })] })] }), _jsxs("div", { className: "badge", children: ["Risk: ", risk.riskLevel, " ", risk.sncuReferral ? "(SNCU Referral)" : ""] }), _jsx("button", { onClick: () => scheduleHbnc("web-case-001", birthDate), children: "Generate HBNC Visits" }), _jsx("ul", { children: visits.map((v) => (_jsxs("li", { children: ["Day ", v.visitDay, ": ", v.dueDate] }, v.id))) })] }), _jsxs(Card, { title: "4. Incentive Ledger", subtitle: "Single-window draft claim tracking.", children: [_jsx("button", { onClick: () => {
                            createIncentiveDraft({ ashaId: "asha-001", serviceType: "ANC" });
                            setLedgerTick((x) => x + 1);
                        }, children: "Add ANC Draft (\u20B9300)" }), _jsxs("div", { className: "row statRow", children: [_jsxs("div", { children: ["Total: \u20B9", ledger.total] }), _jsxs("div", { children: ["Pending: \u20B9", ledger.pending] }), _jsxs("div", { children: ["Paid: \u20B9", ledger.paid] })] })] }), _jsxs(Card, { title: "5. Offline Sync", subtitle: "Push queued operations to sync-gateway.", children: [_jsx("button", { onClick: async () => {
                            const out = await runSyncOnce();
                            setSyncResult(`ACK: ${out.ack}, FAILED: ${out.failed}`);
                        }, children: "Sync Now" }), syncResult ? _jsx("p", { children: syncResult }) : null] })] }));
}
