"""
Retrieval module. Called by routes before invoking the AI model.

preload() is called once at server startup (see main.py lifespan) so the
embedding model is warm before any request arrives.

retrieve() is async and runs the CPU-bound encode() in a thread pool so it
never blocks the event loop.

Usage:
    from rag import retrieve
    context = await retrieve("high blood pressure in pregnancy")
"""
import asyncio
import logging
import chromadb
from sentence_transformers import SentenceTransformer

logger = logging.getLogger(__name__)

_model = None
_collection = None


def preload():
    """Load the embedding model and ChromaDB index. Call once at startup."""
    global _model, _collection
    if _collection is not None:
        return
    try:
        client = chromadb.PersistentClient(path="chroma_db")
        _collection = client.get_collection("guidelines")
        _model = SentenceTransformer("paraphrase-multilingual-MiniLM-L12-v2")
        logger.info("RAG: loaded guideline index (%d chunks)", _collection.count())
    except Exception as e:
        logger.warning("RAG index unavailable — %s. Continuing without it.", e)


async def retrieve(query: str, top_k: int = 3) -> str:
    """
    Returns relevant guideline text to prepend to the system prompt, or '' if
    the index is unavailable or the query yields nothing useful.
    Runs encode() in a thread pool to avoid blocking the event loop.
    """
    if _collection is None or not query.strip():
        return ""
    try:
        embedding = await asyncio.to_thread(_model.encode, [query])
        results = _collection.query(query_embeddings=embedding.tolist(), n_results=top_k)
        chunks = results["documents"][0]
        return "\n\n---\n".join(chunks)
    except Exception as e:
        logger.warning("RAG retrieval failed: %s", e)
        return ""
