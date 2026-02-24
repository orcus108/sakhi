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
 */
const AppContext = createContext(null)

const PATIENTS_KEY  = 'sakhi_patients'
const NEWBORNS_KEY  = 'sakhi_newborns'
const LANGUAGE_KEY  = 'sakhi_language'
const ASHA_NAME_KEY = 'sakhi_asha_name'

/** Safely reads and JSON-parses a localStorage value, returning fallback on any error. */
function loadOrDefault(key, fallback) {
  try {
    const stored = localStorage.getItem(key)
    if (stored) return JSON.parse(stored)
  } catch {
    // ignore parse errors
  }
  return fallback
}

export function AppProvider({ children }) {
  const [language, setLanguageState] = useState(() =>
    localStorage.getItem(LANGUAGE_KEY) || 'en'
  )
  const [ashaName, setAshaNameState] = useState(() =>
    localStorage.getItem(ASHA_NAME_KEY) || null
  )
  const [patients, setPatients] = useState(() =>
    loadOrDefault(PATIENTS_KEY, mockPatients.filter(p => p.patient_type === 'anc'))
  )
  const [newborns, setNewborns] = useState(() =>
    loadOrDefault(NEWBORNS_KEY, mockPatients.filter(p => p.patient_type === 'newborn'))
  )
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
