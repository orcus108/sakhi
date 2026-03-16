/**
 * offlineQueue.js — Persistent queue for checkup submissions that couldn't
 * reach the API because the device was offline.
 *
 * Each item stores everything needed to replay the API call:
 *   { queueId, queued_at, patientId, patientType, checkupDate, patient, checkup, language }
 *
 * The queue lives in localStorage so it survives app restarts.
 * AppContext drains it automatically when connectivity returns.
 */

const QUEUE_KEY = 'sakhi_pending_sync'

export function getQueue() {
  try {
    return JSON.parse(localStorage.getItem(QUEUE_KEY) || '[]')
  } catch {
    return []
  }
}

export function enqueue(item) {
  const queue = getQueue()
  const entry = {
    ...item,
    queueId: `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
    queued_at: new Date().toISOString(),
  }
  localStorage.setItem(QUEUE_KEY, JSON.stringify([...queue, entry]))
  return entry
}

export function removeFromQueue(queueId) {
  const queue = getQueue().filter(item => item.queueId !== queueId)
  localStorage.setItem(QUEUE_KEY, JSON.stringify(queue))
}
