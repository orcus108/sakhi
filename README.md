# Sakhi — AI Clinical Companion for ASHA Workers

> MedGemma Impact Challenge (Kaggle) | Deadline: Feb 24, 2026

## Quick Start

### Backend
```bash
cd backend
python -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
cp .env.example .env   # add your GOOGLE_API_KEY
uvicorn main:app --reload
```
API runs at http://localhost:8000

### Frontend
```bash
cd frontend
npm install
npm run dev
```
App runs at http://localhost:5173

## Switching to MedGemma (Day 2)
Edit `backend/.env`:
```
MODEL_PROVIDER=medgemma
MEDGEMMA_API_URL=https://...
MEDGEMMA_API_KEY=...
```
That's the only change needed.

## Project Structure
See [CLAUDE.md](CLAUDE.md) for full architecture notes.
