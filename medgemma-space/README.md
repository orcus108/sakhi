# Sakhi — MedGemma Space

A deployable Hugging Face Space that serves the fine-tuned MedGemma model via an OpenAI-compatible HTTP API.

The backend's `model.py` calls this Space as its first-priority provider. Point `MEDGEMMA_API_URL` at this Space's URL to activate MedGemma with no other code changes.

## What it does

1. Starts an [Ollama](https://ollama.com) server bound to `0.0.0.0:7860`.
2. Pulls `hf.co/docvm/sakhi-medgemma-1.5-4b-maternal-GGUF:Q4_K_M` from Hugging Face Hub on startup (~2.5 GB Q4_K_M quantization).
3. Exposes an OpenAI-compatible `/v1/chat/completions` endpoint on port 7860.

## Model

**`docvm/sakhi-medgemma-1.5-4b-maternal-GGUF`** — MedGemma 1.5 4B IT fine-tuned on maternal and neonatal triage cases aligned with Indian MOHFW guidelines, then merged and quantized to Q4_K_M GGUF for CPU-viable inference.

See [`model/README.md`](../model/README.md) for fine-tuning details, training config, and evaluation metrics.

## Files

| File | Purpose |
|---|---|
| `Dockerfile` | Extends `ollama/ollama:latest`, copies `start.sh`, exposes port 7860 |
| `start.sh` | Starts Ollama, waits for readiness, pulls the GGUF model, then blocks |

## Deployment (Hugging Face Spaces)

1. Create a new HF Space with the **Docker** SDK.
2. Push the contents of this `medgemma-space/` directory to the Space repo.
3. The Space will pull and serve the model automatically on startup. First boot takes a few minutes while the model downloads.

Once running, the Space URL will be something like:
```
https://YOUR_HF_USERNAME-sakhi-medgemma.hf.space
```

Set this as `MEDGEMMA_API_URL` in the backend's HF Space secrets to activate MedGemma as the primary provider.

## Local testing

```bash
# Requires Docker
docker build -t sakhi-medgemma .
docker run -p 7860:7860 sakhi-medgemma

# Confirm the model is loaded
curl http://localhost:7860/api/tags

# Test inference
curl http://localhost:7860/v1/chat/completions \
  -H "Content-Type: application/json" \
  -d '{
    "model": "hf.co/docvm/sakhi-medgemma-1.5-4b-maternal-GGUF:Q4_K_M",
    "messages": [{"role": "user", "content": "BP is 145/95 at 32 weeks. What is the risk level?"}]
  }'
```

## Notes

- The model is pulled fresh on every cold start. HF Spaces with persistent storage can cache the model layer to speed up restarts.
- No API key is required by default. If you add authentication to the Ollama server, set `MEDGEMMA_API_KEY` in the backend as well.
- The `OLLAMA_HOST=0.0.0.0:7860` export in `start.sh` is required for HF Spaces to route external traffic correctly.
