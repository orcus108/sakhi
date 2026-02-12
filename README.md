# Sakhi (ASHA-G) MVP

Sakhi is an offline-first assistant for ASHA workflows: register digitization, protocol guidance, newborn visit scheduling, and incentive claim tracking.

This repository is a TypeScript monorepo with:
- A React Native mobile app (primary field workflow)
- A React web app (desktop/browser simulation and testing)
- A minimal sync gateway backend
- Shared domain types

## What the app currently does

The MVP ships 5 end-to-end flows in both mobile and web:

1. Multimodal digitization
- Convert register text into structured JSON
- Mobile supports camera capture + OCR pipeline
- Data is persisted locally and added to sync outbox

2. ASHABot guidance (offline RAG-style)
- Ask newborn/HBNC questions
- Returns protocol-first response plus local reference passages

3. HBNC scheduling + risk signaling
- Generates Day 3/7/14/21/28/42 visits from birth date
- Risk rule based on birth weight:
  - `< 1.8 kg` => `HIGH` and SNCU referral required
  - `< 2.5 kg` => `MODERATE`
  - otherwise `LOW`

4. Incentive ledger
- Create draft claims by service type (e.g. ANC)
- Track totals and pending/approved/paid summary

5. Offline sync
- Local writes are queued in an outbox
- Manual sync pushes batched operations to gateway
- Gateway responds with `ACK`/`FAILED` per operation

## Repository structure

- `apps/mobile` - Expo + React Native app with SQLite local-first storage
- `apps/web` - React + Vite app using localStorage-backed store for MVP parity
- `backend/sync-gateway` - Express + SQLite sync endpoint and claim-status mirror
- `packages/shared-types` - Shared TypeScript interfaces used across apps
- `docs/architecture.md` - Short architecture summary

## Tech stack

- Frontend: React Native (Expo), React, TypeScript
- Storage:
  - Mobile: `expo-sqlite`
  - Web: browser `localStorage`
  - Backend: `sqlite3`
- Backend: Express + CORS
- Monorepo: npm workspaces

## Getting started

### Prerequisites

- Node.js 18+ (recommended)
- npm 9+ (recommended)
- For mobile native builds:
  - Xcode (iOS) and/or Android Studio (Android)

### Install

```bash
npm install
```

### Run services

Start each service in its own terminal:

```bash
# sync gateway (http://localhost:8787)
npm run dev:backend

# web app (Vite dev server)
npm run dev:web

# mobile app (Expo)
npm run dev:mobile
```

## Mobile OCR/STT modules (optional but recommended)

The mobile app degrades gracefully without native OCR/STT modules, but full camera OCR and speech-to-text require installing these in `apps/mobile`:

```bash
cd apps/mobile
npx expo install expo-text-extractor expo-speech-recognition expo-speech
```

For best results, run a dev build instead of Expo Go:

```bash
npx expo run:ios
# or
npx expo run:android
```

## Build checks

```bash
npm run build
```

This runs workspace build/typecheck scripts:
- mobile TypeScript check
- web TypeScript + Vite build
- backend TypeScript build
- shared-types TypeScript build

## Data and sync model

- Mobile schema: `apps/mobile/src/db/schema.sql`
- Local outbox table: `sync_outbox`
- Push endpoint: `POST /sync/push`
- Health check: `GET /health`
- Claim status lookup: `GET /claims/:claimId`

Current sync behavior is push-only from client outbox to backend operation log.

## Important MVP notes

- AI runtime hooks are present for ONNX/TFLite routing and quantization policy, but model execution currently uses deterministic local stubs so the app runs without model binaries.
- Mobile sync URL is hardcoded to `http://localhost:8787`; on physical devices this usually requires replacing `localhost` with your machine LAN IP.
- Web app uses localStorage for offline simulation, not SQLite.

## Architecture reference

For a concise architecture overview, see:
- `docs/architecture.md`
