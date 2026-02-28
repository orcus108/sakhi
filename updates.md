# Sakhi — Change Log

---

## 2026-02-28 — New Checkup screen redesign

### `frontend/src/pages/NewCheckupPicker.jsx`

- **Category cards** — added two side-by-side tap targets in the resting state: "Pregnant" (bg-blue-50, person icon) and "Newborns" (bg-purple-50, face icon). Tapping a card filters the patient list to that type and activates the results view. An "All patients" link in the result header clears the filter and returns to resting.
- **Layout reorder** — resting state now flows: motivational line → search bar → "Browse by category" label → category cards. Previously the search bar was buried below the cards.
- **Motivational line** — "You're the reason they're safe." added above the search bar in resting state; collapses to `max-h-0` when search activates so it never obscures results.
- **Compact header** — reduced vertical padding (`pt-10 → pt-8`) and title size (`text-3xl → text-2xl`); heart icon scaled down to `w-6 h-6`.
- **Removed View Schedule buttons** — both the resting-state button and the recents "view full schedule" link have been removed. Schedule is accessible from the bottom navigation bar.
- **Search bar** — strengthened border (`border-gray-300`), taller touch target (`py-3`), white background; cleaner active sticky-bar transition.
- **Spacing** — strict 8pt system throughout (pt-6, pb-3, gap-3, px-4).
- **Category-scoped search** — when a category card is active, typing in the search bar scopes results to that category only.

---

## 2026-02-28

### RAG on WHO / MOHFW Clinical Guidelines

- **New directory: `backend/guidelines/`**
  Holds 10 PDF source documents: ASHA Training Modules 1–7, WHO ANC 2016 recommendations, MOHFW HBNC Operational Guidelines (Revised 2014), and MOHFW ANC / Skilled Attendance at Birth guidelines.

- **New file: `backend/ingest.py`**
  One-time indexing script. Extracts text from PDFs using `pdfplumber` (handles mixed text+image documents), splits into line-accumulated chunks (~400 chars), embeds with `paraphrase-multilingual-MiniLM-L12-v2`, and stores in a persistent ChromaDB collection at `backend/chroma_db/`. Re-run after adding new PDFs.

- **New file: `backend/rag.py`**
  Retrieval module. Exposes `preload()` (called at server startup) and `async retrieve(query)` (called per request). `encode()` runs in a thread pool via `asyncio.to_thread` so it never blocks the event loop.

- **Updated: `backend/main.py`**
  Added FastAPI lifespan context manager that calls `rag.preload()` at startup, warming the embedding model before the first request arrives.

- **Updated: `backend/routes/chat.py`**
  `POST /api/chat` now retrieves the top-3 relevant guideline chunks using the user's last message as the query and prepends them to the system prompt before calling the model.

- **Updated: `backend/routes/checkup.py`**
  `POST /api/checkup-assessment` now retrieves guideline chunks using a focused query built from the patient's reported symptoms/observations (not the full patient blob), and prepends them to the system prompt.

- **Updated: `backend/requirements.txt`**
  Added `pdfplumber==0.11.4`, `chromadb==0.6.3`, `sentence-transformers==3.4.1`.

> ✅ *Reflected in README — 2026-02-28*

---

### Fine-tuned + Quantized MedGemma Integration

- **New file: `model/merge-and-quantize.ipynb`**
  Kaggle notebook that merges the `docvm/sakhi-medgemma-1.5-4b-maternal` LoRA adapter into the base `google/medgemma-1.5-4b-it` model (in bfloat16), converts to GGUF via llama.cpp, quantizes to Q4_K_M, and pushes the result to `docvm/sakhi-medgemma-1.5-4b-maternal-GGUF` on HuggingFace Hub.

- **Updated: `medgemma-space/start.sh`**
  Ollama now pulls the fine-tuned GGUF (`docvm/sakhi-medgemma-1.5-4b-maternal-GGUF:Q4_K_M`) instead of the base model GGUF on Space startup.

- **Updated: `backend/model.py`**
  - MedGemma promoted to **first** in the provider cascade (was last). When `MEDGEMMA_API_URL` is set, all requests go through the fine-tuned model before any fallback is attempted.
  - Default `MEDGEMMA_MODEL_NAME` updated to `hf.co/docvm/sakhi-medgemma-1.5-4b-maternal-GGUF:Q4_K_M`.
  - Docstring updated to reflect new cascade order and model reference.

> ✅ *Reflected in README — 2026-02-28*
