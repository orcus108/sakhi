FROM ollama/ollama:latest

RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

COPY start.sh /start.sh
RUN chmod +x /start.sh

EXPOSE 7860

# Override the ollama entrypoint so our script runs directly
ENTRYPOINT ["/start.sh"]
