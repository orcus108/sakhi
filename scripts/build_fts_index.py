#!/usr/bin/env python3
"""
build_fts_index.py — Build the pre-bundled FTS5 guidelines SQLite index.

Usage:
    pip install pymupdf  # or: pip install PyMuPDF
    python scripts/build_fts_index.py

Output:
    android/app/src/main/assets/guidelines_fts.db

The three MOHFW/WHO PDF documents in backend/ are chunked into ~500-word
overlapping segments and inserted into an FTS5 virtual table. The resulting
database ships in the APK assets/ directory and is opened read-only by
RagRepository at runtime (no on-device ingestion required).

Chunking strategy:
- Split each page's text into sentences using simple period/newline heuristics
- Group sentences into ~500-word windows with 50-word overlap
- Each chunk records its source PDF filename for provenance

FTS5 schema:
    CREATE VIRTUAL TABLE guidelines_fts USING fts5(
        chunk_text,
        source,
        content_rowid=rowid
    );
"""

import sqlite3
import os
import re
import sys
from pathlib import Path

try:
    import fitz  # PyMuPDF
except ImportError:
    print("ERROR: PyMuPDF not installed. Run: pip install PyMuPDF")
    sys.exit(1)

REPO_ROOT = Path(__file__).parent.parent
PDF_DIR = REPO_ROOT / "backend"
OUTPUT_DB = REPO_ROOT / "android" / "app" / "src" / "main" / "assets" / "guidelines_fts.db"

CHUNK_WORDS = 500
OVERLAP_WORDS = 50

PDF_FILES = [
    "ASHA_Module_6_Skills_That_Save_Lives.pdf.pdf",
    "Guidelines_for_Ante_Natal_Care_and_Skilled_Attendance_at_Birth.pdf.pdf",
    "Revised_Home_Based_New_Born_Care_Operational_Guidelines_2014.pdf",
]


def extract_text(pdf_path: Path) -> str:
    """Extract all text from a PDF, joining pages with newlines."""
    doc = fitz.open(str(pdf_path))
    pages = []
    for page in doc:
        pages.append(page.get_text("text"))
    doc.close()
    return "\n".join(pages)


def clean_text(text: str) -> str:
    """Normalise whitespace and remove non-printable characters."""
    text = re.sub(r"\s+", " ", text)
    text = re.sub(r"[^\x20-\x7E\u0900-\u097F\n]", "", text)  # keep ASCII + Devanagari
    return text.strip()


def chunk_text(text: str, chunk_words: int = CHUNK_WORDS, overlap: int = OVERLAP_WORDS):
    """
    Split text into overlapping word windows.
    Yields (start_word_index, chunk_text) tuples.
    """
    words = text.split()
    start = 0
    while start < len(words):
        end = min(start + chunk_words, len(words))
        yield " ".join(words[start:end])
        if end == len(words):
            break
        start += chunk_words - overlap


def build_index():
    if OUTPUT_DB.exists():
        OUTPUT_DB.unlink()
        print(f"Removed existing {OUTPUT_DB.name}")

    OUTPUT_DB.parent.mkdir(parents=True, exist_ok=True)

    conn = sqlite3.connect(str(OUTPUT_DB))
    cur = conn.cursor()

    # Create FTS5 virtual table
    cur.execute("""
        CREATE VIRTUAL TABLE guidelines_fts USING fts5(
            chunk_text,
            source,
            tokenize = 'porter ascii'
        )
    """)

    total_chunks = 0
    for filename in PDF_FILES:
        pdf_path = PDF_DIR / filename
        if not pdf_path.exists():
            print(f"WARNING: {pdf_path} not found — skipping")
            continue

        print(f"Processing {filename}…", end=" ", flush=True)
        raw = extract_text(pdf_path)
        text = clean_text(raw)
        source = Path(filename).stem.replace(".pdf", "")  # strip double .pdf extension

        chunks = list(chunk_text(text))
        for chunk in chunks:
            if len(chunk.split()) < 20:
                continue  # skip very short trailing chunks
            cur.execute(
                "INSERT INTO guidelines_fts(chunk_text, source) VALUES (?, ?)",
                (chunk, source)
            )
        total_chunks += len(chunks)
        print(f"{len(chunks)} chunks")

    conn.commit()

    # Optimize the FTS index
    cur.execute("INSERT INTO guidelines_fts(guidelines_fts) VALUES('optimize')")
    conn.commit()

    # Compact
    conn.execute("VACUUM")
    conn.close()

    size_mb = OUTPUT_DB.stat().st_size / (1024 * 1024)
    print(f"\nBuilt {OUTPUT_DB} ({size_mb:.1f} MB, {total_chunks} chunks total)")
    print("Copy to android/app/src/main/assets/ (already placed there).")


if __name__ == "__main__":
    build_index()
