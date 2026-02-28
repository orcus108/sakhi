"""
One-time indexing script. Run after adding/updating PDFs in guidelines/:
    cd backend && python ingest.py

Uses pdfplumber (handles mixed text+image PDFs better than pypdf).
Image-only pages that yield no text are silently skipped.
"""
import os
import re
import chromadb
import pdfplumber
from sentence_transformers import SentenceTransformer

GUIDELINES_DIR = "guidelines"
CHROMA_DIR = "chroma_db"
CHUNK_SIZE = 400      # characters — ~100 tokens, safe for all models
CHUNK_OVERLAP = 60

def extract_text(path: str) -> str:
    text_parts = []
    with pdfplumber.open(path) as pdf:
        for page in pdf.pages:
            t = page.extract_text()
            if t:
                text_parts.append(t)
    # Use double newline between pages so paragraph splitting can find boundaries
    return "\n\n".join(text_parts)

def clean(text: str) -> str:
    # Rejoin hyphenated line-breaks (e.g. "mater-\nnal" → "maternal")
    text = re.sub(r"-\n(\w)", r"\1", text)
    # Collapse excessive whitespace
    text = re.sub(r"\n{3,}", "\n\n", text)
    text = re.sub(r"[ \t]+", " ", text)
    return text.strip()

def chunk(text: str) -> list[str]:
    # Split into lines, drop noise (page numbers, very short lines), then
    # accumulate lines into chunks up to CHUNK_SIZE characters.
    lines = [l.strip() for l in text.splitlines() if len(l.strip()) > 30]
    chunks, current = [], ""
    for line in lines:
        if len(current) + len(line) + 1 < CHUNK_SIZE:
            current += (" " if current else "") + line
        else:
            if current:
                chunks.append(current)
            current = line
    if current:
        chunks.append(current)
    return chunks

def ingest():
    print("Loading embedding model…")
    model = SentenceTransformer("paraphrase-multilingual-MiniLM-L12-v2")

    client = chromadb.PersistentClient(path=CHROMA_DIR)
    # Recreate collection so re-runs don't accumulate duplicates
    try:
        client.delete_collection("guidelines")
    except Exception:
        pass
    collection = client.create_collection("guidelines")

    pdfs = [f for f in os.listdir(GUIDELINES_DIR) if f.lower().endswith(".pdf")]
    if not pdfs:
        print("No PDFs found in guidelines/")
        return

    total_chunks = 0
    for fname in sorted(pdfs):
        path = os.path.join(GUIDELINES_DIR, fname)
        print(f"  {fname}…", end=" ", flush=True)
        try:
            raw = extract_text(path)
            if not raw.strip():
                print("no text extracted (image-only PDF?) — skipped")
                continue
            text = clean(raw)
            chunks = chunk(text)
            embeddings = model.encode(chunks, show_progress_bar=False).tolist()
            ids = [f"{fname}::{i}" for i in range(len(chunks))]
            collection.upsert(documents=chunks, embeddings=embeddings, ids=ids)
            print(f"{len(chunks)} chunks")
            total_chunks += len(chunks)
        except Exception as e:
            print(f"ERROR — {e}")

    print(f"\nDone. {total_chunks} chunks indexed across {len(pdfs)} PDFs.")
    print(f"Index saved to {CHROMA_DIR}/")

if __name__ == "__main__":
    ingest()
