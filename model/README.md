# Sakhi — Model Fine-Tuning

This directory contains the complete fine-tuning and evaluation pipeline for Sakhi's maternal health adapter, built on top of **MedGemma 1.5 4B IT**.

## Contents

```
model/
├── finetuning-medgemma.ipynb   # QLoRA fine-tuning pipeline (run on Kaggle)
├── testing-ft-model.ipynb      # Triage evaluation harness (run on Kaggle)
└── data/
    └── maternal_triage_cases.json  # 75 labelled maternal triage cases for evaluation
```

---

## HuggingFace Artifacts

| Repo | Type | Purpose |
|---|---|---|
| [`docvm/sakhi-medgemma-1.5-4b-maternal`](https://huggingface.co/docvm/sakhi-medgemma-1.5-4b-maternal) | LoRA adapter | Fine-tuned on maternal/neonatal data — output of this pipeline |
| [`docvm/medgemma-1.5-4b-it-GGUF`](https://huggingface.co/docvm/medgemma-1.5-4b-it-GGUF) | GGUF (Q4_K_M, ~2.5 GB) | Quantized base model for self-hosted serving via `medgemma-space/` |

Base model: `google/medgemma-1.5-4b-it`

---

## Training Setup

| Parameter | Value |
|---|---|
| Method | QLoRA (4-bit NF4 quantisation) |
| LoRA rank | 16 |
| LoRA alpha | 16 |
| LoRA dropout | 0.05 |
| Target modules | all-linear |
| Optimizer | paged_adamw_8bit |
| Learning rate | 2e-4 |
| LR schedule | cosine |
| Epochs | 1 |
| Batch size | 2 (effective 8 with grad accumulation steps=4) |
| Max sequence length | 512 |
| Hardware | Kaggle 2×T4 |
| Training time | ~4.2 hours |
| Trainable params | 38.5M / 4.34B (0.89%) |
| Final training loss | 2.13 |

---

## Training Data

Two HuggingFace datasets, filtered to maternal/neonatal content using keyword matching:

| Dataset | Source | Filtered size |
|---|---|---|
| ChatDoctor-HealthCareMagic-100k | `lavita/ChatDoctor-HealthCareMagic-100k` | 5,000 examples |
| WikiDoc Patient Information | `medalpaca/medical_meadow_wikidoc_patient_information` | 1,500 examples |

**Filter keywords:** pregnancy, antenatal, postpartum, newborn, neonate, breastfeed, jaundice, preeclampsia, gestational diabetes, anaemia, low birth weight, cord, lactation, miscarriage, ectopic, folic acid, iron

Total training examples after 95/5 split: ~5,300 train / ~280 eval.

---

## Evaluation Dataset (`data/maternal_triage_cases.json`)

75 labelled maternal triage cases, authored against **MOHFW/WHO maternal referral guidelines**.

**Distribution:**
| Risk level | Count | Case IDs |
|---|---|---|
| HIGH RISK | 27 | 1, 4, 5, 6, 8, 9, 13, 15, 17–20, 25, 28, 30, 32, 34, 36, 41, 42, 47, 48, 53–55, 58, 60, 65, 68, 70, 72, 75 |
| MODERATE RISK | 23 | 2, 7, 11, 12, 16, 21, 23, 27, 29, 33, 35, 38–40, 43–45, 50, 52, 61, 66, 69, 73 |
| LOW RISK | 25 | 3, 10, 14, 22, 24, 26, 31, 37, 46, 49, 51, 56–57, 59, 62–64, 67, 71, 74 |

**Conditions covered:**
- Eclampsia and severe/mild preeclampsia (antenatal and postpartum)
- Primary and secondary PPH
- Postpartum sepsis and endometritis
- Gestational diabetes (mild to uncontrolled)
- Severe and moderate anaemia
- Ectopic pregnancy, placenta previa, placental abruption
- Cord prolapse, PROM, chorioamnionitis
- Neonatal sepsis, omphalitis, hypothermia, pathological jaundice
- DVT, cardiac decompensation, suspected TB
- Borderline cases (BP 142–150, borderline sugars, mild oedema) to test clinical reasoning at decision thresholds

Each case has: `age`, `gestational_stage`, `vitals` (BP, temperature, blood sugar), `symptoms`, `history`, `guideline_triage`, and a `justification` citing the clinical threshold applied.

---

## Evaluation Metrics (`testing-ft-model.ipynb`)

The harness feeds each case through the fine-tuned model with a structured triage prompt and parses `Triage: HIGH/MODERATE/LOW` from the output.

Key metrics reported:
- **Overall agreement** with guideline triage
- **False Negative Rate (HIGH)** — missed high-risk cases; the most safety-critical metric
- **False Positive Rate (HIGH)** — over-referral rate
- **Sensitivity** for HIGH RISK detection
- **Specificity**
- Full 3×3 confusion matrix (HIGH / MODERATE / LOW)

The FNR for HIGH RISK is the primary safety metric: in maternal triage, missing a high-risk case is far more dangerous than over-referring.

---

## System Prompt (Triage Mode)

```
You are a maternal triage AI.

Classify the case into exactly one of:

Triage: HIGH
Triage: MODERATE
Triage: LOW

Escalate to HIGH if any of the following are present:
- BP ≥160 systolic or ≥110 diastolic
- Seizures, convulsions
- Heavy bleeding
- Signs of sepsis (fever + rigors + abdominal tenderness postpartum)
- Visual disturbance + hypertension
Otherwise classify appropriately.

Output strictly in this format:
Triage: <HIGH/MODERATE/LOW>
Reason: <one short sentence>
```

---

## How This Adapter Is Used in the App

The adapter is **not** used directly in Sakhi's current backend. The production app uses the model cascade in `backend/model.py` (`MedGemma → Gemma 3n → Gemini → Groq`) with a more detailed JSON output schema for the full assessment response.

The fine-tuned adapter demonstrates that MedGemma can be reliably steered toward India-specific clinical reasoning. When self-hosted Ollama deployment becomes feasible, this adapter can be merged into the base model and served via `medgemma-space/`.
