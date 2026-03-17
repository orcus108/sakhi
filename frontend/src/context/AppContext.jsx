import { createContext, useContext, useState, useEffect, useRef } from 'react'
import mockPatients from '../data/mockPatients.json'
import i18n from '../i18n.js'
import { fetchCheckupAssessmentDirect } from '../api/api.js'
import { getQueue, removeFromQueue } from '../utils/offlineQueue.js'

/**
 * AppContext.jsx — Global application state
 *
 * Single source of truth for:
 *  - ashaName:         The logged-in ASHA worker's name (persisted to localStorage)
 *  - ashaId:           The ASHA worker's ID (e.g. "ASH1001"), persisted to localStorage
 *  - language:         Active UI language ('en' | 'hi'), synced with i18next
 *  - patients:         ANC patient list (filtered by ashaId, persisted per worker)
 *  - newborns:         Newborn patient list (same source, separate key)
 *  - selectedPatient:  The patient/newborn the user is currently viewing (in-memory only)
 *  - lastAssessment:   The most recent AI assessment result (in-memory only, cleared on logout)
 *  - checkupDraft:     Partially-filled checkup saved when the ASHA navigates to Ask Sakhi
 *                      mid-form, so they can return without losing their input
 *
 * There is no real database — all persistence is via localStorage.
 * Mock data is the initial seed; after first load the stored version takes over.
 * localStorage keys are namespaced by ashaId so each worker has independent data.
 *
 * Date-shifting: mockPatients.json was designed for a reference date of 2026-02-28.
 * On each new calendar day, dates are re-seeded from the JSON with all checkup/visit
 * dates shifted forward so the next-due distribution always looks the same
 * (a couple overdue, a few due today, most upcoming). Within a single day, any
 * checkups the ASHA adds are preserved via localStorage.
 */
const AppContext = createContext(null)

const LANGUAGE_KEY  = 'sakhi_language'
const ASHA_NAME_KEY = 'sakhi_asha_name'
const ASHA_ID_KEY   = 'sakhi_asha_id'

// Per-worker localStorage keys (namespaced by ASHA ID)
const patientsKey  = id => `sakhi_patients_${id}`
const newbornsKey  = id => `sakhi_newborns_${id}`
const dataDateKey  = id => `sakhi_data_date_${id}`

// The calendar date the mock JSON was authored for. All dates in the JSON are
// meaningful relative to this anchor — shifting them keeps the same clinical picture.
const REFERENCE_DATE = '2026-02-28'

/** Returns today's date as YYYY-MM-DD in local time (not UTC). */
function todayStr() {
  const d = new Date()
  const yyyy = d.getFullYear()
  const mm   = String(d.getMonth() + 1).padStart(2, '0')
  const dd   = String(d.getDate()).padStart(2, '0')
  return `${yyyy}-${mm}-${dd}`
}

/** Shifts a YYYY-MM-DD string forward by deltaDays. */
function shiftDate(dateStr, deltaDays) {
  const d = new Date(dateStr)
  d.setDate(d.getDate() + deltaDays)
  const yyyy = d.getFullYear()
  const mm   = String(d.getMonth() + 1).padStart(2, '0')
  const dd   = String(d.getDate()).padStart(2, '0')
  return `${yyyy}-${mm}-${dd}`
}

/** Shifts all date fields in a patient/newborn record by deltaDays. */
function shiftPatient(patient, deltaDays) {
  const p = { ...patient }
  if (p.lmp)            p.lmp            = shiftDate(p.lmp, deltaDays)
  if (p.date_of_birth)  p.date_of_birth  = shiftDate(p.date_of_birth, deltaDays)
  if (p.checkup_history) {
    p.checkup_history = p.checkup_history.map(c => ({ ...c, date: shiftDate(c.date, deltaDays) }))
  }
  if (p.visit_history) {
    p.visit_history = p.visit_history.map(v => ({ ...v, date: shiftDate(v.date, deltaDays) }))
  }
  return p
}

/**
 * Returns { patients, newborns } for the given workerId from localStorage if the
 * data was seeded today, otherwise re-seeds from mockPatients filtered by workerId.
 * Returns empty arrays if workerId is null.
 */
function seedData(workerId) {
  if (!workerId) return { patients: [], newborns: [] }

  const today = todayStr()
  const storedDate = localStorage.getItem(dataDateKey(workerId))

  if (storedDate === today) {
    try {
      const patients = JSON.parse(localStorage.getItem(patientsKey(workerId)))
      const newborns = JSON.parse(localStorage.getItem(newbornsKey(workerId)))
      if (patients && newborns) return { patients, newborns }
    } catch {
      // fall through to re-seed
    }
  }

  // Re-seed: filter by worker, shift all mock dates by (today − referenceDate)
  const deltaDays = Math.round(
    (new Date(today) - new Date(REFERENCE_DATE)) / 86400000
  )
  // Unknown worker ID → fall back to ASH1001's patient set as a generic demo list
  const matched = mockPatients.filter(p => p.asha_worker_id === workerId)
  const workerRecords = matched.length > 0
    ? matched
    : mockPatients.filter(p => p.asha_worker_id === 'ASH1001')
  const all      = workerRecords.map(p => shiftPatient(p, deltaDays))
  const patients = all.filter(p => p.patient_type === 'anc')
  const newborns = all.filter(p => p.patient_type === 'newborn')

  localStorage.setItem(patientsKey(workerId),  JSON.stringify(patients))
  localStorage.setItem(newbornsKey(workerId),  JSON.stringify(newborns))
  localStorage.setItem(dataDateKey(workerId), today)

  return { patients, newborns }
}

export function AppProvider({ children }) {
  const [language, setLanguageState] = useState(() =>
    localStorage.getItem(LANGUAGE_KEY) || 'en'
  )
  const [ashaName, setAshaNameState] = useState(() =>
    localStorage.getItem(ASHA_NAME_KEY) || null
  )
  const [ashaId, setAshaIdState] = useState(() =>
    localStorage.getItem(ASHA_ID_KEY) || null
  )
  const [{ patients: initPatients, newborns: initNewborns }] = useState(() =>
    seedData(localStorage.getItem(ASHA_ID_KEY))
  )
  const [patients, setPatients] = useState(initPatients)
  const [newborns, setNewborns] = useState(initNewborns)
  const [selectedPatient, setSelectedPatient] = useState(null)
  const [lastAssessment, setLastAssessment] = useState(null)
  const [checkupDraft, setCheckupDraft] = useState(null)
  const [pendingCount, setPendingCount] = useState(() => getQueue().length)

  // Ref so the online event handler always sees the latest sync function
  const syncRef = useRef(null)

  /**
   * Log in as an ASHA worker. Sets both name and ID, loads their patient list.
   * Called from Onboarding on first run or after logout.
   */
  function login(name, id) {
    localStorage.setItem(ASHA_NAME_KEY, name)
    localStorage.setItem(ASHA_ID_KEY, id)
    setAshaNameState(name)
    setAshaIdState(id)
    const { patients: p, newborns: n } = seedData(id)
    setPatients(p)
    setNewborns(n)
  }

  /** Switches the active language, persists it, and tells i18next to re-render translated strings. */
  function setLanguage(lang) {
    setLanguageState(lang)
    localStorage.setItem(LANGUAGE_KEY, lang)
    i18n.changeLanguage(lang)
  }

  // Appends a new checkup record and updates risk_level on the patient/newborn.
  // Persists both changes to localStorage so they survive refresh.
  // Also refreshes selectedPatient so the profile screen shows the new data immediately.
  // When the assessment was produced offline (_offline: true), increments pendingCount.
  function saveCheckup(patientId, patientType, checkup, assessment) {
    if (assessment._offline) setPendingCount(n => n + 1)
    const newRecord = { ...checkup, risk_level: assessment.risk_level, notes: assessment.risk_reason }

    if (patientType === 'anc') {
      const updated = patients.map(p => {
        if (p.id !== patientId) return p
        return {
          ...p,
          risk_level: assessment.risk_level,
          checkup_history: [...(p.checkup_history ?? []), newRecord],
        }
      })
      localStorage.setItem(patientsKey(ashaId), JSON.stringify(updated))
      setPatients(updated)
      const updatedPatient = updated.find(p => p.id === patientId)
      if (updatedPatient && selectedPatient?.id === patientId) {
        setSelectedPatient(updatedPatient)
      }
    } else {
      const updated = newborns.map(n => {
        if (n.id !== patientId) return n
        return {
          ...n,
          risk_level: assessment.risk_level,
          current_weight_kg: checkup.weight_kg ?? n.current_weight_kg,
          visit_history: [...(n.visit_history ?? []), newRecord],
        }
      })
      localStorage.setItem(newbornsKey(ashaId), JSON.stringify(updated))
      setNewborns(updated)
      const updatedNewborn = updated.find(n => n.id === patientId)
      if (updatedNewborn && selectedPatient?.id === patientId) {
        setSelectedPatient(updatedNewborn)
      }
    }
  }

  /**
   * Replays all queued offline submissions against the real API.
   * Called automatically when the browser comes back online.
   * Updates stored checkup records and lastAssessment in-place so the ASHA
   * sees the AI result without navigating away.
   */
  async function syncPendingQueue() {
    const queue = getQueue()
    if (queue.length === 0) return

    for (const item of queue) {
      try {
        const result = await fetchCheckupAssessmentDirect(
          item.patient, item.checkup, item.patientType, item.language
        )

        // Update the stored checkup_history / visit_history record
        if (item.patientType === 'anc') {
          setPatients(prev => {
            const updated = prev.map(p => {
              if (p.id !== item.patientId) return p
              const history = (p.checkup_history ?? []).map(c =>
                c.date === item.checkupDate
                  ? { ...c, risk_level: result.risk_level, notes: result.risk_reason }
                  : c
              )
              return { ...p, risk_level: result.risk_level, checkup_history: history }
            })
            localStorage.setItem(patientsKey(ashaId), JSON.stringify(updated))
            return updated
          })
        } else {
          setNewborns(prev => {
            const updated = prev.map(n => {
              if (n.id !== item.patientId) return n
              const history = (n.visit_history ?? []).map(v =>
                v.date === item.checkupDate
                  ? { ...v, risk_level: result.risk_level, notes: result.risk_reason }
                  : v
              )
              return { ...n, risk_level: result.risk_level, visit_history: history }
            })
            localStorage.setItem(newbornsKey(ashaId), JSON.stringify(updated))
            return updated
          })
        }

        // If the assessment screen is still showing this offline result, upgrade it
        setLastAssessment(prev => {
          if (
            prev?.patientId === item.patientId &&
            prev?.assessment?._offline &&
            prev?.checkup?.date === item.checkupDate
          ) {
            return { ...prev, assessment: result }
          }
          return prev
        })

        removeFromQueue(item.queueId)
        setPendingCount(n => Math.max(0, n - 1))
      } catch {
        // Network still unavailable — leave in queue and try again next time
      }
    }
  }

  // Keep ref current so the event listener always calls the latest version
  syncRef.current = syncPendingQueue

  // Auto-sync when connectivity is restored
  useEffect(() => {
    const handleOnline = () => syncRef.current?.()
    window.addEventListener('online', handleOnline)
    return () => window.removeEventListener('online', handleOnline)
  }, [])

  function logout() {
    localStorage.removeItem(ASHA_NAME_KEY)
    localStorage.removeItem(ASHA_ID_KEY)
    setAshaNameState(null)
    setAshaIdState(null)
    setPatients([])
    setNewborns([])
    setLastAssessment(null)
  }

  return (
    <AppContext.Provider
      value={{
        ashaName,
        ashaId,
        login,
        logout,
        patients,
        newborns,
        selectedPatient,
        setSelectedPatient,
        lastAssessment,
        setLastAssessment,
        checkupDraft,
        setCheckupDraft,
        saveCheckup,
        pendingCount,
        syncPendingQueue,
        language,
        setLanguage,
      }}
    >
      {children}
    </AppContext.Provider>
  )
}

export function useApp() {
  const ctx = useContext(AppContext)
  if (!ctx) throw new Error('useApp must be used within AppProvider')
  return ctx
}
