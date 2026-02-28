import { createContext, useContext, useState } from 'react'
import mockPatients from '../data/mockPatients.json'
import i18n from '../i18n.js'

/**
 * AppContext.jsx — Global application state
 *
 * Single source of truth for:
 *  - ashaName:         The logged-in ASHA worker's name (persisted to localStorage)
 *  - language:         Active UI language ('en' | 'hi'), synced with i18next
 *  - patients:         ANC patient list (seeded from mockPatients.json, persisted)
 *  - newborns:         Newborn patient list (same source, separate key)
 *  - selectedPatient:  The patient/newborn the user is currently viewing (in-memory only)
 *  - lastAssessment:   The most recent AI assessment result (in-memory only, cleared on logout)
 *  - checkupDraft:     Partially-filled checkup saved when the ASHA navigates to Ask Sakhi
 *                      mid-form, so they can return without losing their input
 *
 * There is no real database — all persistence is via localStorage.
 * Mock data is the initial seed; after first load the stored version takes over.
 *
 * Date-shifting: mockPatients.json was designed for a reference date of 2026-02-28.
 * On each new calendar day, dates are re-seeded from the JSON with all checkup/visit
 * dates shifted forward so the next-due distribution always looks the same
 * (a couple overdue, a few due today, most upcoming). Within a single day, any
 * checkups the ASHA adds are preserved via localStorage.
 */
const AppContext = createContext(null)

const PATIENTS_KEY  = 'sakhi_patients'
const NEWBORNS_KEY  = 'sakhi_newborns'
const DATA_DATE_KEY = 'sakhi_data_date'
const LANGUAGE_KEY  = 'sakhi_language'
const ASHA_NAME_KEY = 'sakhi_asha_name'

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
 * Returns { patients, newborns } from localStorage if the data was seeded today,
 * otherwise re-seeds from mockPatients with dates shifted to today's date.
 * This ensures the next-due distribution always looks fresh regardless of when the
 * app is opened — critical for hackathon demos.
 */
function seedData() {
  const today = todayStr()
  const storedDate = localStorage.getItem(DATA_DATE_KEY)

  if (storedDate === today) {
    try {
      const patients = JSON.parse(localStorage.getItem(PATIENTS_KEY))
      const newborns = JSON.parse(localStorage.getItem(NEWBORNS_KEY))
      if (patients && newborns) return { patients, newborns }
    } catch {
      // fall through to re-seed
    }
  }

  // Re-seed: shift all mock dates by (today − referenceDate)
  const deltaDays = Math.round(
    (new Date(today) - new Date(REFERENCE_DATE)) / 86400000
  )
  const all      = mockPatients.map(p => shiftPatient(p, deltaDays))
  const patients = all.filter(p => p.patient_type === 'anc')
  const newborns = all.filter(p => p.patient_type === 'newborn')

  localStorage.setItem(PATIENTS_KEY,  JSON.stringify(patients))
  localStorage.setItem(NEWBORNS_KEY,  JSON.stringify(newborns))
  localStorage.setItem(DATA_DATE_KEY, today)

  return { patients, newborns }
}

export function AppProvider({ children }) {
  const [language, setLanguageState] = useState(() =>
    localStorage.getItem(LANGUAGE_KEY) || 'en'
  )
  const [ashaName, setAshaNameState] = useState(() =>
    localStorage.getItem(ASHA_NAME_KEY) || null
  )
  const [{ patients: initPatients, newborns: initNewborns }] = useState(() => seedData())
  const [patients, setPatients] = useState(initPatients)
  const [newborns, setNewborns] = useState(initNewborns)
  const [selectedPatient, setSelectedPatient] = useState(null)
  const [lastAssessment, setLastAssessment] = useState(null)
  const [checkupDraft, setCheckupDraft] = useState(null)

  /** Persists the ASHA worker name to localStorage so the session survives page refresh. */
  function setAshaName(name) {
    setAshaNameState(name)
    localStorage.setItem(ASHA_NAME_KEY, name)
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
  function saveCheckup(patientId, patientType, checkup, assessment) {
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
      localStorage.setItem(PATIENTS_KEY, JSON.stringify(updated))
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
      localStorage.setItem(NEWBORNS_KEY, JSON.stringify(updated))
      setNewborns(updated)
      const updatedNewborn = updated.find(n => n.id === patientId)
      if (updatedNewborn && selectedPatient?.id === patientId) {
        setSelectedPatient(updatedNewborn)
      }
    }
  }

  function logout() {
    localStorage.removeItem(ASHA_NAME_KEY)
    setAshaNameState(null)
  }

  return (
    <AppContext.Provider
      value={{
        ashaName,
        setAshaName,
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
