import { useState, useRef, useMemo } from 'react'
import { useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { useApp } from '../context/AppContext.jsx'
import { useDebounce } from '../hooks/useDebounce.js'
import RiskBadge from '../components/RiskBadge.jsx'
import { localName, localMotherName } from '../utils/nameUtils.js'

/**
 * NewCheckupPicker.jsx — Patient search screen for starting a new checkup (Screen 3)
 *
 * Rather than navigating to a patient's profile first, ASHA workers can jump
 * directly from this screen to the appropriate checkup form (ANC or newborn).
 *
 * UX pattern: the screen has two visual states:
 *  - Resting  – prominent spacer + hint text centres the search bar vertically
 *  - Active   – spacer collapses and the search bar "flies up" to the top,
 *               making room for results below
 *
 * Recently checked patients are stored in localStorage (max 5) and displayed
 * when the search bar is focused but empty, so the most likely patients are
 * immediately accessible without typing.
 *
 * Search matches across all localised name fields (same strategy as Home.jsx).
 */
const RECENTS_KEY = 'sakhi_recent_patients'
const MAX_RECENTS = 5

const riskAccent = {
  red: 'bg-red-500',
  yellow: 'bg-yellow-400',
  green: 'bg-green-500',
}

function getDaysOld(dob) {
  return Math.floor((Date.now() - new Date(dob).getTime()) / 86400000)
}

function loadRecents() {
  try {
    return JSON.parse(localStorage.getItem(RECENTS_KEY) || '[]')
  } catch {
    return []
  }
}

function saveRecent(id, patientType) {
  const prev = loadRecents().filter(r => r.id !== id)
  const next = [{ id, patientType }, ...prev].slice(0, MAX_RECENTS)
  localStorage.setItem(RECENTS_KEY, JSON.stringify(next))
}

function PatientCard({ patient, onClick, language }) {
  return (
    <button
      onClick={onClick}
      className="w-full text-left bg-white rounded-2xl shadow-sm hover:shadow-md transition-all active:scale-[0.99] overflow-hidden flex"
    >
      <div className={`w-1.5 shrink-0 ${riskAccent[patient.risk_level]}`} />
      <div className="flex-1 px-4 py-3.5 flex items-center justify-between gap-3">
        <div className="min-w-0">
          <p className="text-base font-semibold text-gray-900 truncate">{localName(patient, language)}</p>
          <p className="text-sm text-gray-500 mt-0.5">{patient.subtitle}</p>
        </div>
        <div className="flex items-center gap-2 shrink-0">
          <RiskBadge level={patient.risk_level} size="sm" />
          <svg className="w-4 h-4 text-gray-300" fill="none" stroke="currentColor" strokeWidth={2} viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" d="M9 5l7 7-7 7" />
          </svg>
        </div>
      </div>
    </button>
  )
}

export default function NewCheckupPicker() {
  const { patients, newborns, setSelectedPatient, language } = useApp()
  const { t } = useTranslation()
  const navigate = useNavigate()
  const [search, setSearch] = useState('')
  const [focused, setFocused] = useState(false)
  const [recentIds, setRecentIds] = useState(loadRecents)
  const inputRef = useRef(null)
  const debouncedSearch = useDebounce(search, 300)

  // Active = user has tapped the search bar OR is mid-query
  const isActive = focused || search.length > 0

  const allPatients = useMemo(() => {
    const allAnc = patients.map(p => ({
      ...p,
      patientType: 'anc',
      subtitle: `${p.gestational_weeks} ${t('patient.weeksPregnant')}`,
      checkupPath: `/patient/${p.id}/checkup`,
    }))
    const allNewborns = newborns.map(n => {
      const daysOld = getDaysOld(n.date_of_birth)
      return {
        ...n,
        patientType: 'newborn',
        subtitle: `${t('newCheckupPicker.daysOld', { count: daysOld })} · ${localMotherName(n, language)}`,
        checkupPath: `/newborn/${n.id}/checkup`,
      }
    })
    return [...allAnc, ...allNewborns]
  }, [patients, newborns, t, language])

  function startCheckup(patient) {
    saveRecent(patient.id, patient.patientType)
    setRecentIds(loadRecents())
    setSelectedPatient(patient)
    navigate(patient.checkupPath)
  }

  const searchResults = useMemo(() => {
    const q = debouncedSearch.toLowerCase()
    if (!q) return []
    return allPatients.filter(p => {
      // Search across all name fields so the user can type in any language
      const nameMatch = Object.keys(p).some(
        k => (k === 'name' || k.startsWith('name_')) && p[k]?.toLowerCase().includes(q)
      )
      const motherMatch = Object.keys(p).some(
        k => (k === 'mother_name' || k.startsWith('mother_name_')) && p[k]?.toLowerCase().includes(q)
      )
      return nameMatch || motherMatch
    })
  }, [allPatients, debouncedSearch])

  const recentPatients = useMemo(() =>
    recentIds.map(r => allPatients.find(p => p.id === r.id)).filter(Boolean),
  [recentIds, allPatients])

  const showSearch = debouncedSearch.length > 0
  const showRecents = !showSearch && recentPatients.length > 0

  function handleBlur() {
    // Small delay so click events on results fire before we collapse
    setTimeout(() => {
      if (!inputRef.current?.value) setFocused(false)
    }, 100)
  }

  return (
    <div className="flex flex-col min-h-screen bg-gray-50 pb-16">

      {/* Persistent blue header — always visible, same style as Home/Schedule */}
      <header className="bg-blue-600 px-4 pt-10 pb-4 flex items-center justify-between">
        <div>
          <h1 className="text-white text-3xl font-bold">{t('newCheckupPicker.title')}</h1>
          <p className="text-blue-100 text-sm mt-0.5">{t('newCheckupPicker.subtitle')}</p>
        </div>
        <svg className="w-7 h-7 text-white opacity-90" fill="currentColor" viewBox="0 0 24 24">
          <path d="M4.318 6.318a4.5 4.5 0 000 6.364L12 20.364l7.682-7.682a4.5 4.5 0 00-6.364-6.364L12 7.636l-1.318-1.318a4.5 4.5 0 00-6.364 0z" />
        </svg>
      </header>

      {/* Spacer — padding-top collapses on active, making the search bar "fly up" */}
      <div className={`transition-all duration-300 ease-out ${isActive ? 'pt-0' : 'pt-[22vh]'}`} />

      {/* Resting-state subtitle — collapses as spacer shrinks */}
      <div className={`overflow-hidden transition-all duration-300 ease-out ${isActive ? 'max-h-0' : 'max-h-16'}`}>
        <div className="px-4 text-center pb-5">
          <p className="text-sm text-gray-500">{t('newCheckupPicker.resting')}</p>
        </div>
      </div>

      {/* Search bar — always in the DOM, container styling changes on active */}
      <div className={`px-4 transition-all duration-200 ${isActive ? 'bg-white border-b border-gray-100 py-3 shadow-sm' : 'py-0'}`}>
        <div className="relative">
          <svg className="absolute left-3 top-1/2 -translate-y-1/2 w-5 h-5 text-gray-400" fill="none" stroke="currentColor" strokeWidth={2} viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" d="M21 21l-4.35-4.35M17 11A6 6 0 1 1 5 11a6 6 0 0 1 12 0z" />
          </svg>
          <input
            ref={inputRef}
            type="search"
            value={search}
            onChange={e => setSearch(e.target.value)}
            onFocus={() => setFocused(true)}
            onBlur={handleBlur}
            placeholder={t('newCheckupPicker.searchPlaceholder')}
            className="w-full pl-10 pr-4 py-2.5 border border-gray-200 rounded-xl text-base focus:outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100 bg-gray-50"
          />
        </div>
      </div>

      {/* View schedule — only visible in resting state, fades out when active */}
      <div className={`overflow-hidden transition-all duration-200 ${isActive ? 'max-h-0' : 'max-h-20'}`}>
        <div className="flex justify-center pt-4 px-4">
          <button
            onClick={() => navigate('/schedule')}
            className="text-sm text-blue-600 font-medium py-2.5 px-6 rounded-xl border border-blue-100 bg-blue-50 hover:bg-blue-100 transition-colors"
          >
            {t('newCheckupPicker.viewSchedule')}
          </button>
        </div>
      </div>

      {/* Results — rendered once active, fades in */}
      <div className={`flex-1 px-4 pt-4 pb-6 transition-opacity duration-200 ${isActive ? 'opacity-100' : 'opacity-0 pointer-events-none'}`}>

        {showSearch && (
          searchResults.length > 0 ? (
            <div className="space-y-2.5">
              {searchResults.map(p => (
                <PatientCard key={p.id} patient={p} onClick={() => startCheckup(p)} language={language} />
              ))}
            </div>
          ) : (
            <div className="text-center py-16 text-gray-400">
              <p className="text-lg">{t('newCheckupPicker.noPatients')}</p>
              <p className="text-sm mt-1">{t('newCheckupPicker.tryDifferent')}</p>
            </div>
          )
        )}

        {showRecents && (
          <>
            <p className="text-xs font-semibold text-gray-500 uppercase tracking-wide pb-2">
              {t('newCheckupPicker.checkedRecently')}
            </p>
            <div className="space-y-2.5">
              {recentPatients.map(p => (
                <PatientCard key={p.id} patient={p} onClick={() => startCheckup(p)} language={language} />
              ))}
            </div>
            <button
              onClick={() => navigate('/schedule')}
              className="mt-6 w-full text-sm text-blue-600 font-medium py-3 rounded-xl border border-blue-100 bg-blue-50 hover:bg-blue-100 transition-colors"
            >
              {t('newCheckupPicker.viewFullSchedule')}
            </button>
          </>
        )}

      </div>
    </div>
  )
}
