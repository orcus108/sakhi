# ASHA-G Architecture (MVP)

## Mobile
- Local-first writes to SQLite.
- Local outbox (`sync_outbox`) stores pending operations.
- Periodic or manual sync pushes to gateway.
- Core modules:
  - OCR digitization pipeline
  - RAG assistant query flow
  - HBNC schedule generator with risk trigger
  - Incentive ledger tracking

## AI runtime strategy
- Runtime selector chooses TFLite on NNAPI-capable devices.
- ONNX fallback for CPU path.
- Quantization policy:
  - low power mode -> int4
  - normal mode -> int8

## Backend sync gateway
- Minimal append-only operation log.
- Optional claim status mirror for incentive transparency.
