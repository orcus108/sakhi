const STORE_KEY = "asha_g_web_store_v1";
const seedStore = {
    digitizedRecords: [],
    kbChunks: [
        {
            id: "kb-1",
            content: "Danger signs in newborn: breathing rate above 60/min, chest indrawing, fever, hypothermia, poor feeding, convulsions, jaundice in first 24 hours."
        },
        {
            id: "kb-2",
            content: "Birth weight below 1.8 kg requires urgent referral to SNCU according to protocol."
        }
    ],
    visits: [],
    claims: [],
    outbox: []
};
function loadStore() {
    const raw = localStorage.getItem(STORE_KEY);
    if (!raw)
        return seedStore;
    try {
        return JSON.parse(raw);
    }
    catch {
        return seedStore;
    }
}
function saveStore(store) {
    localStorage.setItem(STORE_KEY, JSON.stringify(store));
}
function enqueueOutbox(store, tableName, recordId, payload) {
    const opId = `${tableName}-${recordId}-${Date.now()}`;
    store.outbox.push({
        opId,
        tableName,
        recordId,
        operation: "INSERT",
        payloadJson: JSON.stringify(payload),
        syncStatus: "PENDING",
        clientTs: Date.now()
    });
    return store;
}
export function createDigitizedRecord(payload) {
    const store = loadStore();
    const id = `dig-${Date.now()}`;
    store.digitizedRecords.unshift({ id, payload });
    enqueueOutbox(store, "digitized_record", id, payload);
    saveStore(store);
}
export function listDigitizedRecords() {
    return loadStore().digitizedRecords;
}
export function searchKnowledge(query) {
    const q = query.toLowerCase();
    return loadStore()
        .kbChunks.filter((c) => c.content.toLowerCase().includes(q))
        .slice(0, 5)
        .map((c) => c.content);
}
export function createVisit(visit) {
    const store = loadStore();
    store.visits.push(visit);
    enqueueOutbox(store, "hbnc_visit", visit.id, visit);
    saveStore(store);
}
export function listVisits() {
    return loadStore().visits.sort((a, b) => a.dueDate.localeCompare(b.dueDate));
}
export function createClaim(claim) {
    const store = loadStore();
    store.claims.unshift(claim);
    enqueueOutbox(store, "incentive_claim", claim.id, claim);
    saveStore(store);
}
export function listClaims(ashaId) {
    return loadStore().claims.filter((c) => c.ashaId === ashaId);
}
export function getPendingOutbox(limit = 50) {
    return loadStore().outbox.filter((o) => o.syncStatus === "PENDING").slice(0, limit);
}
export function markOutboxStatus(opId, status) {
    const store = loadStore();
    const item = store.outbox.find((o) => o.opId === opId);
    if (item)
        item.syncStatus = status;
    saveStore(store);
}
