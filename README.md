# Sakhi MVP

Offline-first AI companion for ASHA workers.

## Apps
- `apps/mobile`: React Native (Expo) mobile app with offline SQLite, OCR pipeline hooks, RAG query flow, HBNC scheduler, incentive ledger.
- `apps/web`: React + Vite web app with equivalent core modules for browser testing and desktop workflows.
- `backend/sync-gateway`: Optional sync gateway for eventual cloud synchronization.
- `packages/shared-types`: Shared TypeScript domain models.

## Quick start

1. Install dependencies:
   - `npm install`
2. Run backend:
   - `npm run dev:backend`
3. Run mobile:
   - `npm run dev:mobile`
4. Run web:
   - `npm run dev:web`

## Sakhi Mobile Speech/OCR Add-ons

- For on-device OCR and STT in mobile builds, install optional native modules:
  - `cd apps/mobile`
  - `npx expo install expo-text-extractor expo-speech-recognition expo-speech`
- Expo Go may not include all optional modules. For full OCR/STT, use a dev build:
  - `npx expo run:ios` or `npx expo run:android`

## Notes
- Mobile SQLite schema is at `apps/mobile/src/db/schema.sql`.
- Web app uses browser local storage as an offline-first store for MVP simulation.
- Local AI runtime functions are wired for ONNX/TFLite integration points and currently ship with deterministic fallbacks so the app runs without model binaries.
