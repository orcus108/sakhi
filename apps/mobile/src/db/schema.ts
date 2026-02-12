const schemaSql = `
CREATE TABLE IF NOT EXISTS beneficiary (
  id TEXT PRIMARY KEY,
  abha_id TEXT,
  name TEXT NOT NULL,
  phone TEXT,
  village_code TEXT,
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL,
  deleted INTEGER DEFAULT 0
);

CREATE TABLE IF NOT EXISTS newborn_case (
  id TEXT PRIMARY KEY,
  beneficiary_id TEXT NOT NULL,
  birth_date TEXT NOT NULL,
  birth_weight_kg REAL,
  risk_level TEXT NOT NULL,
  sncu_referral_required INTEGER DEFAULT 0,
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL,
  FOREIGN KEY (beneficiary_id) REFERENCES beneficiary(id)
);

CREATE TABLE IF NOT EXISTS hbnc_visit (
  id TEXT PRIMARY KEY,
  newborn_case_id TEXT NOT NULL,
  visit_day INTEGER NOT NULL,
  due_date TEXT NOT NULL,
  completed_at TEXT,
  findings_json TEXT,
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL,
  FOREIGN KEY (newborn_case_id) REFERENCES newborn_case(id)
);

CREATE TABLE IF NOT EXISTS kb_chunk (
  id TEXT PRIMARY KEY,
  source_doc TEXT,
  section TEXT,
  language TEXT,
  content TEXT NOT NULL,
  embedding BLOB,
  created_at INTEGER NOT NULL
);

CREATE VIRTUAL TABLE IF NOT EXISTS kb_chunk_fts USING fts5(content, section, source_doc);

CREATE TABLE IF NOT EXISTS incentive_claim (
  id TEXT PRIMARY KEY,
  asha_id TEXT NOT NULL,
  service_type TEXT NOT NULL,
  amount_rupees INTEGER NOT NULL,
  status TEXT NOT NULL,
  rejection_reason TEXT,
  related_entity_id TEXT,
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS digitized_record (
  id TEXT PRIMARY KEY,
  payload_json TEXT NOT NULL,
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS sync_outbox (
  op_id TEXT PRIMARY KEY,
  table_name TEXT NOT NULL,
  record_id TEXT NOT NULL,
  operation TEXT NOT NULL,
  payload_json TEXT NOT NULL,
  base_version INTEGER,
  client_ts INTEGER NOT NULL,
  sync_status TEXT DEFAULT 'PENDING'
);

CREATE TABLE IF NOT EXISTS sync_checkpoint (
  id INTEGER PRIMARY KEY CHECK (id = 1),
  last_pulled_at INTEGER,
  last_pushed_at INTEGER,
  server_cursor TEXT
);

CREATE TABLE IF NOT EXISTS conflict_log (
  id TEXT PRIMARY KEY,
  table_name TEXT NOT NULL,
  record_id TEXT NOT NULL,
  local_payload_json TEXT NOT NULL,
  server_payload_json TEXT NOT NULL,
  resolution TEXT,
  created_at INTEGER NOT NULL
);
`;

export default schemaSql;
