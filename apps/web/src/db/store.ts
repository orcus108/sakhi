import type { HBNCVisit, IncentiveClaim } from "@asha-g/shared-types";

type KbChunk = { id: string; content: string };

type OutboxRecord = {
  opId: string;
  tableName: string;
  recordId: string;
  operation: "INSERT" | "UPDATE" | "DELETE";
  payloadJson: string;
  syncStatus: "PENDING" | "ACK" | "FAILED";
  clientTs: number;
};

type AppStore = {
  digitizedRecords: Array<{ id: string; payload: Record<string, string | number | null> }>;
  kbChunks: KbChunk[];
  visits: HBNCVisit[];
  claims: IncentiveClaim[];
  outbox: OutboxRecord[];
};

const STORE_KEY = "asha_g_web_store_v1";

const seedStore: AppStore = {
  digitizedRecords: [],
  kbChunks: [
    {
      id: "kb-1",
      content:
        "Danger signs in newborn: breathing rate above 60/min, chest indrawing, fever, hypothermia, poor feeding, convulsions, jaundice in first 24 hours."
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

function loadStore(): AppStore {
  const raw = localStorage.getItem(STORE_KEY);
  if (!raw) return seedStore;

  try {
    return JSON.parse(raw) as AppStore;
  } catch {
    return seedStore;
  }
}

function saveStore(store: AppStore): void {
  localStorage.setItem(STORE_KEY, JSON.stringify(store));
}

function enqueueOutbox(store: AppStore, tableName: string, recordId: string, payload: unknown): AppStore {
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

export function createDigitizedRecord(payload: Record<string, string | number | null>): void {
  const store = loadStore();
  const id = `dig-${Date.now()}`;
  store.digitizedRecords.unshift({ id, payload });
  enqueueOutbox(store, "digitized_record", id, payload);
  saveStore(store);
}

export function listDigitizedRecords(): Array<{ id: string; payload: Record<string, string | number | null> }> {
  return loadStore().digitizedRecords;
}

export function searchKnowledge(query: string): string[] {
  const q = query.toLowerCase();
  return loadStore()
    .kbChunks.filter((c) => c.content.toLowerCase().includes(q))
    .slice(0, 5)
    .map((c) => c.content);
}

export function createVisit(visit: HBNCVisit): void {
  const store = loadStore();
  store.visits.push(visit);
  enqueueOutbox(store, "hbnc_visit", visit.id, visit);
  saveStore(store);
}

export function listVisits(): HBNCVisit[] {
  return loadStore().visits.sort((a, b) => a.dueDate.localeCompare(b.dueDate));
}

export function createClaim(claim: IncentiveClaim): void {
  const store = loadStore();
  store.claims.unshift(claim);
  enqueueOutbox(store, "incentive_claim", claim.id, claim);
  saveStore(store);
}

export function listClaims(ashaId: string): IncentiveClaim[] {
  return loadStore().claims.filter((c) => c.ashaId === ashaId);
}

export function getPendingOutbox(limit = 50): OutboxRecord[] {
  return loadStore().outbox.filter((o) => o.syncStatus === "PENDING").slice(0, limit);
}

export function markOutboxStatus(opId: string, status: "ACK" | "FAILED"): void {
  const store = loadStore();
  const item = store.outbox.find((o) => o.opId === opId);
  if (item) item.syncStatus = status;
  saveStore(store);
}
