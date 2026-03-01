# Sakhi — Frontend

React + Vite + Tailwind mobile-first UI for ASHA workers, deployed on [Vercel](https://sakhi-asha.vercel.app).

## Stack

| Tool | Version | Purpose |
|---|---|---|
| React | 18 | UI framework |
| Vite | 6 | Dev server + build |
| Tailwind CSS | 3 | Styling (no custom CSS files) |
| react-router-dom | 6 | Client-side routing |
| react-i18next | 16 | English / Hindi (Devanagari) i18n |
| react-markdown | 10 | Render markdown in chat responses |

## Project structure

```
src/
├── api/
│   └── api.js               # All fetch calls — single source of truth
├── components/
│   ├── BottomNav.jsx         # Tab bar navigation
│   ├── Disclaimer.jsx        # "Sakhi supports your judgment…" footer
│   ├── RiskBadge.jsx         # Color-coded green / yellow / red badge
│   └── TopBar.jsx            # Header with back button + screen title
├── context/
│   └── AppContext.jsx        # Global state: patients, session, language
├── data/
│   └── mockPatients.json     # 6 ANC + 6 newborn demo patients, all risk levels
├── hooks/
│   └── useDebounce.js        # Debounce hook for patient search input
├── locales/
│   ├── en.json               # English UI strings
│   └── hi.json               # Hindi UI strings (Devanagari)
└── pages/
    ├── Onboarding.jsx         # ASHA worker name selection (no auth)
    ├── Home.jsx               # Patient list, risk summary strip, search
    ├── PatientProfile.jsx     # ANC patient detail + checkup history
    ├── NewbornProfile.jsx     # Newborn detail + visit history
    ├── NewCheckupPicker.jsx   # Choose ANC or newborn visit
    ├── CheckupForm.jsx        # 2-step ANC vitals + symptoms form
    ├── NewbornCheckupForm.jsx # Newborn visit observations
    ├── Assessment.jsx         # AI output screen (shared ANC + newborn)
    ├── AskSakhi.jsx           # Free-form chat with optional patient context
    └── Schedule.jsx           # Follow-up appointment calendar
```

## Local development

```bash
npm install
npm run dev
# → http://localhost:5173
```

The Vite dev server automatically proxies `/api/*` to `http://localhost:8000`. No environment variable needed for local development — the proxy config lives in [vite.config.js](vite.config.js).

## Deployment (Vercel)

1. Set the root directory to `frontend` and the framework to **Vite**.
2. Add one environment variable:

```
VITE_API_URL=https://YOUR_HF_USERNAME-sakhi-api.hf.space/api
```

The app reads `VITE_API_URL` at build time. If the variable is absent (local dev), the Vite proxy handles routing automatically.

## Architecture notes

- **Single API module** — every network call goes through `src/api/api.js`. No `fetch` calls in pages or components.
- **Global state** — `AppContext.jsx` holds the patient list, current session, and active language. All pages read from and write to this context.
- **i18n** — language toggles between `en` and `hi` at runtime. All user-visible strings live in `src/locales/`. AI responses follow the same language setting via the `language` field sent in API requests.
- **Mock data** — `mockPatients.json` seeds the app with 12 demo patients on first load so the UI is immediately demonstrable without running any checkups.

## Design constraints

- Max content width **430 px**, centered on desktop (mobile-first).
- Tailwind only — no custom CSS files.
- Body text minimum **16 px**. Vital numbers displayed at `text-2xl`+.
- Every screen has exactly **one** obvious primary action.
- Warm tone: "Sakhi is thinking…" not "Loading…".
