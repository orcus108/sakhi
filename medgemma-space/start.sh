#!/bin/bash
set -e

export OLLAMA_HOST=0.0.0.0:7860

ollama serve &
SERVER_PID=$!

echo "Waiting for Ollama to start..."
until curl -sf http://localhost:7860/api/tags > /dev/null 2>&1; do
  sleep 1
done
echo "Ollama started."

echo "Pulling fine-tuned MedGemma GGUF from HuggingFace..."
OLLAMA_HOST=localhost:7860 ollama pull hf.co/docvm/sakhi-medgemma-1.5-4b-maternal-GGUF:Q4_K_M
echo "Model ready."

wait $SERVER_PID
