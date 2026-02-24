# Sakhi — CLAUDE.md
> AI Clinical Companion for ASHA Workers | MedGemma Hackathon Project

## What is this project?
Sakhi is a mobile-first React web app that helps ASHA (Accredited Social Health Activist) 
workers in rural India with antenatal care checkups and clinical decision support.
It uses MedGemma 1.5 4B IT (via API) as its AI backbone.

Built for: Google MedGemma Impact Challenge (Kaggle), 3-day hackathon.
Deadline: February 24, 2026.

---

## Stack
- **Frontend:** React + Vite + Tailwind CSS, deployed on Vercel
- **Backend:** Python FastAPI, deployed on Render
- **AI Model:** Groq llama-3.1-8b-instant (active) | Gemini 2.0 Flash (stub) | MedGemma 1.5 4B IT (stub, swap in Day 2)
- **Data:** Mock JSON only. No database. localStorage for session state.
- **State:** React Context (no Redux)

---

## Project Structure
```
sakhi/
├── frontend/
│   ├── src/
│   │   ├── components/          # Reusable UI components
│   │   │   ├── BottomNav.jsx    # Tab navigation (Home, Ask, Schedule)
│   │   │   ├── Disclaimer.jsx   # "Sakhi supports your judgment..." footer
│   │   │   ├── RiskBadge.jsx    # Color-coded risk level badge
│   │   │   └── TopBar.jsx       # Header bar with back button + title
│   │   ├── pages/               # One file per screen
│   │   │   ├── Onboarding.jsx
│   │   │   ├── Home.jsx
│   │   │   ├── PatientProfile.jsx
│   │   │   ├── CheckupForm.jsx
│   │   │   ├── Assessment.jsx   # Shared for ANC + newborn
│   │   │   ├── AskSakhi.jsx
│   │   │   ├── NewCheckupPicker.jsx   # Choose ANC or newborn visit
│   │   │   ├── NewbornProfile.jsx
│   │   │   ├── NewbornCheckupForm.jsx
│   │   │   └── Schedule.jsx
│   │   ├── hooks/
│   │   │   └── useDebounce.js   # Debounce hook for search
│   │   ├── data/                # mockPatients.json (ANC + newborn)
│   │   ├── context/             # AppContext.jsx
│   │   └── api/                 # api.js — all fetch calls live here
│   ├── index.html
│   ├── vite.config.js
│   ├── tailwind.config.js
│   └── package.json
├── backend/
│   ├── main.py              # FastAPI entry point
│   ├── routes/
│   │   ├── checkup.py       # POST /api/checkup-assessment (ANC + newborn)
│   │   └── chat.py          # POST /api/chat
│   ├── prompts.py           # ALL system prompts in one place
│   ├── model.py             # Model abstraction layer (swap here only)
│   └── limiter.py           # SlowAPI rate limiter instance
├── CLAUDE.md                # This file
└── README.md
```

---

## The Model Abstraction Rule (CRITICAL)
`backend/model.py` is the ONLY file that talks to the AI model.
All routes import from model.py. Never call the model API directly from routes.

Switching models = changing ONE env var (read by model.py at startup):
```
MODEL_PROVIDER=groq      # current active
MODEL_PROVIDER=gemini    # stub ready
MODEL_PROVIDER=medgemma  # stub ready, needs MEDGEMMA_API_URL + MEDGEMMA_API_KEY
```

Do not break this pattern under any circumstances.

---

## Environment Variables
```
# backend/.env
GROQ_API_KEY=xxx            # Active — llama-3.1-8b-instant
GOOGLE_API_KEY=xxx          # Gemini stub (Day 2 option)
MEDGEMMA_API_URL=xxx        # HuggingFace or Vertex endpoint (Day 2)
MEDGEMMA_API_KEY=xxx        # Day 2
MODEL_PROVIDER=groq         # Active provider. Switch to "gemini" or "medgemma" when ready
```

---

## Screens (in order)
0. **Onboarding** — Pick ASHA worker name, no password
1. **Home** — Patient list (ANC + newborn), risk summary strip, search
2. **Patient Profile** — ANC patient detail + checkup history
2b. **Newborn Profile** — Newborn detail + visit history
3. **New Checkup Picker** — Choose ANC checkup or newborn visit
4. **Checkup Form** — 2-step ANC vitals + symptoms input
4b. **Newborn Checkup Form** — Newborn visit observations (weight, cord, feeding, etc.)
5. **Assessment** — AI output, color-coded risk, actions (shared for ANC + newborn)
6. **Ask Sakhi** — Free-form chat with optional patient context
7. **Schedule** — Appointment/follow-up schedule view

---

## API Contracts

### POST /api/checkup-assessment
Rate limit: 10/minute per IP
Request: `{ patient: PatientObject, checkup: CheckupObject, patient_type: "anc"|"newborn" }`
Response:
```json
{
  "risk_level": "green|yellow|red",
  "risk_reason": "string",
  "what_sakhi_noticed": ["string"],
  "what_to_tell_patient": "string",
  "what_to_do_next": "string",
  "follow_up_date": "YYYY-MM-DD|null"
}
```

### POST /api/chat
Rate limit: 20/minute per IP
Request: `{ messages: [{role, content}], patient_context: PatientObject|null }`
Response: `{ reply: "string" }`

---

## Design Rules (Non-Negotiable)
- Mobile-first. Max content width 430px, centered on desktop
- Tailwind only. No custom CSS files unless absolutely necessary
- Color palette: white bg, blue-600 primary, red-500 urgent only, yellow-400 warnings
- Body text minimum 16px. Vitals numbers displayed large (text-2xl+)
- Every screen: ONE obvious primary button
- Warm tone: "Sakhi is thinking..." not "Loading..."
- Disclaimer on Assessment + Chat screens: 
  "Sakhi supports your judgment — always refer when unsure."

---

## Mock Data Rules
- 6 ANC patients: 2 green (normal), 2 yellow (monitor), 2 red (high risk)
- 6 newborn patients: mix of risk levels, with mother_name, birth_weight_kg, date_of_birth
- All in "Rampur Village, Rajasthan"
- Realistic Indian names, ANC ages 19–32
- ANC patients: 1–3 past checkup_history records with realistic vitals
- Newborn patients: 1–3 past visit_history records (weight, observations)
- Red ANC patients must have clearly concerning vitals (BP > 140/90, symptoms)
- patient_type field distinguishes "anc" vs "newborn" in mockPatients.json

---

## What Is Explicitly OUT OF SCOPE for V1
Do not build these. Do not suggest building these.
- Authentication / passwords / JWT
- Any real database (SQLite, Postgres, etc.)
- ABDM / health record API integration  
- Voice input or output
- Hindi or other Indic languages
- Push notifications
- Immunization workflows (newborn postpartum visits ARE in scope; immunization tracking is not)
- Offline mode / service workers
- Fine-tuning pipeline (separate notebook, not in this app)

If asked about these, respond: "Out of scope for V1 — noted for V2 roadmap."

---

## System Prompts (in prompts.py)
```python
CHECKUP_SYSTEM_PROMPT = """
You are Sakhi, an AI clinical companion for ASHA workers in rural India.
ASHA workers are trained community health workers, not doctors. They perform 
antenatal checkups and refer high-risk cases to Primary Health Centres (PHC).

Your role:
- Help interpret clinical readings (BP, weight, fundal height)
- Flag warning signs clearly and urgently
- Give practical, actionable advice in simple English
- Support referral decisions

Your rules:
- Never diagnose. Support judgment, don't replace it.
- Always recommend PHC referral for BP > 140/90, severe symptoms, or uncertainty
- Be concise — ASHA workers are in the field
- Simple language, no complex medical terms
- Be warm but direct

Respond ONLY in this exact JSON format, no other text:
{
  "risk_level": "green" | "yellow" | "red",
  "risk_reason": "one sentence",
  "what_sakhi_noticed": ["point 1", "point 2", "point 3"],
  "what_to_tell_patient": "1-2 sentences of direct advice",
  "what_to_do_next": "concrete next action",
  "follow_up_date": "YYYY-MM-DD or null"
}
"""

NEWBORN_CHECKUP_SYSTEM_PROMPT = """
You are Sakhi, an AI clinical companion for ASHA workers in rural India.
You are assessing a newborn's health based on a postnatal visit.

Your role:
- Flag danger signs (low weight, cord issues, jaundice, feeding problems, breathing)
- Give age-appropriate guidance (weight gain expectations differ by day/week)
- Support referral decisions for high-risk newborns

Respond ONLY in this exact JSON format, no other text:
{
  "risk_level": "green" | "yellow" | "red",
  "risk_reason": "one sentence",
  "what_sakhi_noticed": ["point 1", "point 2", "point 3"],
  "what_to_tell_patient": "1-2 sentences of direct advice for the mother",
  "what_to_do_next": "concrete next action",
  "follow_up_date": "YYYY-MM-DD or null"
}
"""

CHAT_SYSTEM_PROMPT = """
You are Sakhi, an AI clinical companion for ASHA workers in rural India.
Answer questions about maternal health, pregnancy complications, and ANC protocols.
Be concise, warm, and practical. Use simple English.
If patient context is provided, factor it into your answer.
Always end with: refer to PHC if there is any doubt.
Never diagnose. You support the ASHA worker's judgment.
"""
```

---

## Current Status
- [x] Backend scaffolded
- [x] Mock data created (ANC + newborn patients)
- [x] /api/checkup-assessment working (Groq, ANC + newborn via patient_type)
- [x] /api/chat working (Groq)
- [x] Frontend: Onboarding screen
- [x] Frontend: Home screen
- [x] Frontend: Patient Profile screen (ANC)
- [x] Frontend: Newborn Profile screen
- [x] Frontend: New Checkup Picker screen
- [x] Frontend: Checkup Form screen (ANC)
- [x] Frontend: Newborn Checkup Form screen
- [x] Frontend: Assessment screen (shared ANC + newborn)
- [x] Frontend: Ask Sakhi screen
- [x] Frontend: Schedule screen
- [ ] Deployed to Vercel + Render
- [ ] MedGemma swapped in (credentials needed)
- [ ] Fine-tuning notebook done

Update this checklist as you go.

## Design System

### Philosophy
Data-forward but human. Every number that matters should LOOK like it matters.
White space is not wasted space. Cards should feel like physical objects (subtle shadow, rounded).
The app should feel closer to a modern health app (think: a polished consumer app) 
than a government form.

### Spacing & Layout
- Page padding: px-4 (16px) consistently
- Card gap: gap-3 between cards
- Section gap: gap-6 between sections
- Inner card padding: p-4 standard, p-5 for hero cards
- Border radius: rounded-2xl for cards, rounded-xl for inputs, rounded-full for badges

### Elevation (shadows) — use these, not flat borders
- Cards: shadow-sm (default), shadow-md (hover/active)
- Hero card: shadow-lg
- Bottom CTA button: shadow-lg with color tint
- Never use plain borders alone for cards. Always shadow-sm + optional border-gray-100

### Color Tokens (Tailwind classes only)
Primary blue:      bg-blue-600, text-blue-600, border-blue-600
Light blue bg:     bg-blue-50  (for success states, normal risk backgrounds)
Red urgent:        bg-red-500, text-red-600, bg-red-50 (for high risk backgrounds)
Yellow monitor:    bg-yellow-400, text-yellow-600, bg-yellow-50
Neutral dark:      text-gray-900 (headings), text-gray-600 (body), text-gray-400 (hints)
Card background:   bg-white
Page background:   bg-gray-50
Dividers:          border-gray-100

### Risk badge colors (updated for blue primary)
High Risk:  bg-red-100 text-red-700 border border-red-200
Monitor:    bg-yellow-100 text-yellow-700 border border-yellow-200
Normal:     bg-blue-100 text-blue-700 border border-blue-200

### Typography Scale
Page title:        text-2xl font-bold text-gray-900
Section heading:   text-base font-semibold text-gray-900 uppercase tracking-wide
Card title:        text-lg font-bold text-gray-900
Body:              text-base text-gray-600 (never smaller than text-sm)
Hint/label:        text-xs text-gray-400 uppercase tracking-wide
VITAL NUMBER:      text-3xl font-bold (BP), text-2xl font-bold (others)
                   These are the hero numbers — make them BIG

### Vital Numbers — Special Treatment
Vitals are the most important data. They must be displayed as LARGE numbers with 
small label below, not as "Label: value" text.

Pattern for each vital card:
┌─────────────────┐
│  156/100        │  ← text-3xl font-bold, colored if abnormal
│  mmHg           │  ← text-xs text-gray-400
│  Blood Pressure │  ← text-xs text-gray-500
└─────────────────┘

BP coloring: 
- Normal (<120/80): text-gray-900
- Elevated (120-139/80-89): text-yellow-600
- High (≥140/90): text-red-600 + bg-red-50 card background

### Risk Badges
Pattern: <dot> Label  (e.g., ● High Risk)
Size: text-xs font-semibold px-3 py-1 rounded-full

### Patient Cards (Home Screen)
Left accent bar (4px wide, full height, colored by risk) instead of just border.
Show: Name (text-base font-semibold) + Age + Weeks (text-sm text-gray-500)
      Obstetric history (G4P3) + Last seen date
      One-line status message in italic text-sm text-gray-500
Right: Risk badge + chevron
Background: white, shadow-sm, rounded-2xl

### Home Screen Header
Full-bleed blue header (bg-blue-600) with:
- "Welcome back," in text-sm text-blue-100
- Worker name in text-3xl font-bold text-white
- Summary strip INSIDE the header as 3 white cards with colored numbers
  (not separate section below — part of the header visually)

### Checkup Form
Input fields: Large touch targets (min h-14), rounded-xl, border-gray-200
              Focus state: border-blue-500 ring-2 ring-blue-100
BP inputs: Side by side with "/" separator in between, LARGE text-xl when filled
Hint text below each input in text-xs text-gray-400
Step progress: Two segments at top, colored blue when complete, gray when pending
              Label below each segment (1. Vitals / 2. Symptoms)

Symptom tiles: 2-column grid, each tile rounded-xl border
               Default: bg-white border-gray-200 text-gray-700
               Selected: bg-blue-50 border-blue-500 text-blue-700
                         with checkmark icon top-right corner
               Min height h-14, centered text

### Assessment Screen — Most Important Screen
This needs to feel like a diagnosis card, not a list.

Risk banner: Full-width, colored bg (red-50/yellow-50/green-50), 
             large risk badge centered, reason text below. 
             Add a colored left border (4px) matching risk color.
             Rounded-2xl, p-5.

"What Sakhi noticed" section:
  Numbered list but with GREEN circle numbers (not default browser list)
  Each item: text-base text-gray-700, generous line height

"Tell the patient" section:
  Light blue-gray background (bg-blue-50), rounded-2xl, p-4
  Person icon + "Tell the patient" heading in text-sm font-semibold
  Quote-style: italic text, as if it's a script for what to say

"Next action" section:
  If RED: bg-red-50 border-l-4 border-red-500, text-red-700 font-semibold
  If YELLOW: bg-yellow-50 border-l-4 border-yellow-400
  If GREEN: bg-green-50 border-l-4 border-green-500

Disclaimer: text-xs text-gray-400 centered at bottom, always visible

### Ask Sakhi Screen
Header: white bg, back button + "Ask Sakhi" title + patient context pill (blue)
        Patient pill: bg-blue-100 text-blue-700 text-xs px-2 py-1 rounded-full

Empty state (no messages yet):
  Blue avatar circle (bg-blue-600, white heart icon inside)
  "Ask me anything" heading
  Subtitle with patient name in bold
  Common questions as full-width tappable cards (bg-white shadow-sm rounded-xl)
  Section label "COMMON QUESTIONS" in uppercase tracking-wide text-xs text-gray-400

Chat bubbles:
  User: bg-blue-600 text-white rounded-2xl rounded-br-sm, right aligned
  Sakhi: bg-white shadow-sm rounded-2xl rounded-bl-sm, left aligned
  Sakhi avatar: small blue circle with "S", sits to left of bubble

Input bar: Sticky bottom, bg-white border-t border-gray-100 px-4 py-3
           Input: rounded-full border border-gray-200 flex-1
           Send button: bg-blue-600 rounded-full w-10 h-10 (arrow icon, white)

### Bottom Navigation (if used)
bg-white border-t border-gray-100 shadow-[0_-4px_6px_rgba(0,0,0,0.05)]
Three tabs: Home, Ask Sakhi, Schedule (+ New Checkup floating action)
Active: text-blue-600 icon filled
Inactive: text-gray-400 icon outline

### Animations (simple, not distracting)
- Screen transitions: none needed for V1
- Loading state: "Sakhi is thinking..." with 3 pulsing blue dots (animate-pulse)
  Not a spinner — feels more human
- Symptom tile select: brief scale-95 on tap (active:scale-95 transition-transform)
- Risk banner on Assessment: fade-in (animate-in or simple opacity transition)

### What to avoid
- No gray backgrounds for cards (use white + shadow instead)  
- No colored text on colored backgrounds (bad contrast)
- No borders as the only visual separator for important cards
- No ALL CAPS for anything except section labels
- No small touch targets (everything tappable must be min h-12)
- Don't center-align body text, only hero numbers and empty states