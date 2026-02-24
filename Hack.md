# Kaggle MedGemma Hackathon — Project Context

## Overview

This project is a submission for the **Kaggle AI in Healthcare Hackathon** using Google's **HAI-DEF (Health AI Developer Foundations)** model collection, specifically **MedGemma**.

- **Competition page:** https://www.kaggle.com/competitions/med-gemma-impact-challenge
- **Submission format:** Single Kaggle Writeup (≤3 pages) + Video (≤3 min) + public code repo

---

## HAI-DEF Models

- **MedGemma** is the primary model — open-weight, privacy-focused, designed for healthcare/life sciences
- Models are designed to run locally (no constant internet / centralized infra required)
- Developers get full control over model weights and infrastructure
- Use of **at least one HAI-DEF model is mandatory**

---

## Judging Criteria (weights)

| Criterion | Weight | What judges look for |
|---|---|---|
| **Effective use of HAI-DEF models** | 20% | Are HAI-DEF models used to their fullest potential? Would other solutions be less effective? |
| **Problem domain** | 15% | Clear problem definition, unmet need, magnitude, user journey storytelling |
| **Impact potential** | 15% | Articulated real/anticipated impact, quantified estimates |
| **Product feasibility** | 20% | Fine-tuning docs, performance analysis, app stack, deployment challenges & solutions |
| **Execution & communication** | 30% | Video quality, write-up clarity, code quality (organization, comments, reusability) |

---

## Submission Requirements

### Mandatory
- [ ] Kaggle Writeup (≤3 pages, use template below)
- [ ] Video demo (≤3 minutes)
- [ ] Public code repository

### Bonus
- [ ] Live interactive demo app (public URL)
- [ ] Open-weight HuggingFace model tracing to a HAI-DEF model

### Track Selection
- All submissions compete in **Main Track**
- Eligible for **one** special award (pick only one):
  - Agentic Workflow Prize
  - The Novel Task Prize
  - The Edge of AI Prize

---

## Writeup Template

```markdown
### Project name
[Concise name]

### Your team
[Names, specialties, roles]

### Problem statement
[Answers "Problem domain" & "Impact potential" — who is the user, what is the unmet need, 
what is the magnitude, what is the impact if it works?]

### Overall solution
[Answers "Effective use of HAI-DEF models" — why MedGemma specifically, why not another model?]

### Technical details
[Answers "Product feasibility" — fine-tuning approach, performance analysis, app stack, 
deployment challenges and mitigations]
```

---

## Key Constraints & Design Principles

- **Privacy-first:** Solution should not depend on external APIs for sensitive data — MedGemma runs locally
- **Offline/edge capable:** Designed for clinical environments without reliable internet
- **Reproducible:** All code must be reproducible from the public repo
- **Real use case:** Judges favor practical, user-facing applications over pure benchmarking

---

## Notes for Development

- Keep code well-organized, commented, and reusable — code quality is part of the 30% execution score
- The video carries most of the narrative weight — write-up should be concise, not exhaustive
- Quantify impact estimates where possible (e.g., time saved per clinician per day, # patients affected)
- Document fine-tuning steps and model performance metrics explicitly