/**
 * api.js — All backend fetch calls live here.
 *
 * BASE_URL reads from VITE_API_URL (set in .env.local for local dev, and as
 * a Vercel env var in production). Falls back to '/api' so the Vite proxy
 * can forward requests to the local FastAPI server without CORS issues.
 *
 * Error handling: all functions throw an Error with the backend's
 * `detail` field (FastAPI standard) or a fallback HTTP status string.
 * Callers are responsible for catching and displaying these errors.
 *
 * Offline behaviour: fetchCheckupAssessment catches network errors and falls
 * back to a local rule-based assessment (localAssessment.js). The submission
 * is queued in localStorage and replayed automatically when connectivity
 * returns. The result carries _offline: true so the UI can flag it.
 */
import { assessLocally } from '../utils/localAssessment.js'
import { enqueue } from '../utils/offlineQueue.js'

const BASE_URL =
  import.meta.env.VITE_API_URL ||
  (import.meta.env.PROD ? 'https://docvm-sakhi-api.hf.space/api' : '/api')

/** Raw POST to the assessment endpoint — throws on any failure, no fallback. */
async function postCheckupAssessment(patient, checkup, patientType, language) {
  const res = await fetch(`${BASE_URL}/checkup-assessment`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ patient, checkup, patient_type: patientType, language }),
  })
  if (!res.ok) {
    const err = await res.json().catch(() => ({}))
    throw new Error(err.detail || `HTTP ${res.status}`)
  }
  return res.json()
}

/**
 * Submits vitals + symptoms to the AI assessment endpoint.
 * Falls back to local rule-based triage when the network is unavailable,
 * queuing the submission for background sync.
 *
 * @param {object} patient     - Full patient/newborn record from AppContext
 * @param {object} checkup     - Vitals collected in CheckupForm / NewbornCheckupForm
 * @param {'anc'|'newborn'} patientType - Determines which system prompt the backend uses
 * @param {string} language    - Current UI language ('en' | 'hi')
 * @returns {Promise<AssessmentResult>}  result._offline === true when local fallback was used
 */
export async function fetchCheckupAssessment(patient, checkup, patientType = 'anc', language = 'en') {
  try {
    return await postCheckupAssessment(patient, checkup, patientType, language)
  } catch (err) {
    // TypeError = network failure (fetch couldn't reach the server)
    if (err instanceof TypeError || !navigator.onLine) {
      enqueue({ patientId: patient.id, patientType, checkupDate: checkup.date, patient, checkup, language })
      return assessLocally(patient, checkup, patientType)
    }
    throw err
  }
}

/**
 * Direct API call without offline fallback — used by AppContext sync logic.
 * Throws on network failure so the caller can decide whether to retry.
 */
export async function fetchCheckupAssessmentDirect(patient, checkup, patientType = 'anc', language = 'en') {
  return postCheckupAssessment(patient, checkup, patientType, language)
}

/**
 * Uploads a recorded audio blob for server-side speech-to-text transcription.
 * Used as a fallback when the Web Speech API is unavailable.
 *
 * @param {Blob} audioBlob  - Raw audio data from MediaRecorder
 * @param {string} mimeType - MIME type reported by MediaRecorder (webm or mp4)
 * @returns {Promise<{ transcript: string }>}
 */
export async function fetchTranscribe(audioBlob, mimeType = 'audio/webm') {
  const ext = mimeType.includes('mp4') ? 'mp4' : 'webm'
  const formData = new FormData()
  formData.append('file', audioBlob, `audio.${ext}`)
  const res = await fetch(`${BASE_URL}/transcribe`, {
    method: 'POST',
    body: formData,
  })
  if (!res.ok) {
    const err = await res.json().catch(() => ({}))
    throw new Error(err.detail || `HTTP ${res.status}`)
  }
  return res.json()
}

/**
 * Sends the conversation history and optional patient context to the chat endpoint.
 *
 * @param {Array<{role: string, content: string}>} messages - Full conversation so far (caller slices to last 10)
 * @param {object|null} patientContext - Lean patient snapshot built by AskSakhi.buildEnrichedContext()
 * @param {string} language           - Current UI language
 * @returns {Promise<{ reply: string }>}
 */
export async function fetchChat(messages, patientContext = null, language = 'en') {
  const res = await fetch(`${BASE_URL}/chat`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ messages, patient_context: patientContext, language }),
  })
  if (!res.ok) {
    const err = await res.json().catch(() => ({}))
    throw new Error(err.detail || `HTTP ${res.status}`)
  }
  return res.json()
}
