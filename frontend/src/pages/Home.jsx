import { useState, useMemo, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { useApp } from '../context/AppContext.jsx'
import { useDebounce } from '../hooks/useDebounce.js'
import RiskBadge from '../components/RiskBadge.jsx'
import { localName, localMotherName } from '../utils/nameUtils.js'

// ── Date helpers ──────────────────────────────────────────────────────────────
function startOfToday() {
  const d = new Date()
  d.setHours(0, 0, 0, 0)
  return d
}

function addDays(dateStr, n) {
  const d = new Date(dateStr)
  d.setDate(d.getDate() + n)
  return d
}

// Positive = future, negative = overdue
function daysDiff(date) {
  return Math.round((date - startOfToday()) / 86400000)
}

function getDaysOld(dob) {
  return Math.floor((Date.now() - new Date(dob).getTime()) / 86400000)
}

function relativeLabel(date, t) {
  const d = daysDiff(date)
  if (d < 0)   return t('home.relative.daysOverdue', { count: -d })
  if (d === 0)  return t('home.relative.dueToday')
  if (d === 1)  return t('home.relative.dueTomorrow')
  if (d < 8)   return t('home.relative.inDays', { count: d })
  return date.toLocaleDateString('en-IN', { day: 'numeric', month: 'short' })
}

const ANC_INTERVAL = { red: 7, yellow: 14, green: 28 }
const NEWBORN_NEXT = { day_1: 2, day_3: 4, day_7: 7, day_14: 14, day_28: null }

function ancNextDue(p) {
  const last = p.checkup_history?.at(-1)
  if (!last) return null
  return addDays(last.date, ANC_INTERVAL[p.risk_level] ?? 28)
}

function newbornNextDue(n) {
  const last = n.visit_history?.at(-1)
  if (!last) return null
  const days = NEWBORN_NEXT[last.visit_day]
  if (days == null) return null
  return addDays(last.date, days)
}

const riskAccent = { red: 'bg-red-500', yellow: 'bg-yellow-400', green: 'bg-green-600' }

const PAGE_SIZE = 10

const riskFilterActive = {
  all:    'bg-blue-600 text-white border-blue-600',
  red:    'bg-red-500 text-white border-red-500',
  yellow: 'bg-yellow-400 text-gray-900 border-yellow-400',
  green:  'bg-green-600 text-white border-green-600',
}

// ── PatientRow ────────────────────────────────────────────────────────────────
function PatientRow({ record, nextDue, onClick, t, language }) {
  const diff = nextDue ? daysDiff(nextDue) : null
  const isOverdue = diff !== null && diff < 0
  const isToday = diff === 0

  const daysOld = record.patient_type === 'newborn' ? getDaysOld(record.date_of_birth) : 0
  const subtitle =
    record.patient_type === 'anc'
      ? `${record.gestational_weeks} ${t('patient.weeksPregnant')}`
      : `${t('newborn.daysOld', { count: daysOld })} · ${localMotherName(record, language)}`

  return (
    <button
      onClick={onClick}
      className="w-full text-left bg-white rounded-2xl shadow-sm hover:shadow-md transition-all active:scale-[0.99] overflow-hidden flex"
    >
      <div className={`w-1.5 shrink-0 ${riskAccent[record.risk_level]}`} />
      <div className="flex-1 px-4 py-3.5 flex items-center justify-between gap-3">
        <div className="min-w-0">
          <p className="text-base font-semibold text-gray-900 truncate">{localName(record, language)}</p>
          <p className="text-sm text-gray-500 mt-0.5 truncate">{subtitle}</p>
        </div>
        <div className="flex flex-col items-end gap-1.5 shrink-0 max-w-[140px]">
          {nextDue && (
            <span
              className={`text-xs font-semibold text-right ${
                isOverdue ? 'text-red-500' : isToday ? 'text-orange-500' : 'text-gray-400'
              }`}
            >
              {relativeLabel(nextDue, t)}
            </span>
          )}
          <div className="flex items-center gap-2">
            <RiskBadge level={record.risk_level} />
            <svg className="w-4 h-4 text-gray-300" fill="none" stroke="currentColor" strokeWidth={2} viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" d="M9 5l7 7-7 7" />
            </svg>
          </div>
        </div>
      </div>
    </button>
  )
}

function SectionHeader({ title, count, urgent }) {
  return (
    <div className="flex items-center gap-2 pt-4 pb-1">
      <p className={`text-xs font-semibold uppercase tracking-wide ${urgent ? 'text-red-500' : 'text-gray-500'}`}>
        {title}
      </p>
      <span className={`text-xs font-semibold px-2 py-0.5 rounded-full ${urgent ? 'bg-red-100 text-red-600' : 'bg-gray-100 text-gray-500'}`}>
        {count}
      </span>
    </div>
  )
}

function ShowMoreButton({ shown, total, onShowMore, t }) {
  const remaining = total - shown
  if (remaining <= 0) return null
  return (
    <button
      onClick={onShowMore}
      className="w-full mt-2 py-2.5 text-sm font-medium text-blue-600 bg-blue-50 rounded-xl hover:bg-blue-100 transition-colors"
    >
      {t('home.showMore', { count: Math.min(remaining, PAGE_SIZE) })}
    </button>
  )
}

// ── Home ──────────────────────────────────────────────────────────────────────
/**
 * Home.jsx — Patient list dashboard (Screen 1)
 *
 * Displays ANC patients and newborns in priority sections:
 *  1. Overdue     – all patients (any risk) whose next checkup date has passed
 *  2. Due Today   – all patients (any risk) due for a checkup today
 *  3. High Risk   – red-level patients not already shown in Overdue or Due Today
 *
 * When a risk filter pill is active, the sectioned view collapses into a
 * flat list of all patients at that risk level, sorted by next-due date.
 *
 * Search is debounced (300 ms) and matches across all localised name fields
 * (name, name_hi, mother_name, mother_name_hi) so ASHA workers can search
 * by typing in either language.
 *
 * Pagination (PAGE_SIZE = 10) prevents DOM overload if the patient list grows.
 * Pagination state resets whenever search, village, or risk filter changes.
 *
 * Next-due date logic:
 *  - ANC: last checkup date + interval based on risk level (ANC_INTERVAL)
 *  - Newborn: last visit date + interval based on the visit milestone (NEWBORN_NEXT)
 */
export default function Home() {
  const { ashaName, patients, newborns, setSelectedPatient, language, setLanguage } = useApp()
  const { t } = useTranslation()
  const navigate = useNavigate()
  const [search, setSearch] = useState('')
  const [village, setVillage] = useState('all')
  const [riskFilter, setRiskFilter] = useState('all')
  const [urgentLimit, setUrgentLimit] = useState(PAGE_SIZE)
  const [overdueLimit, setOverdueLimit] = useState(PAGE_SIZE)
  const [dueTodayLimit, setDueTodayLimit] = useState(PAGE_SIZE)
  const debouncedSearch = useDebounce(search, 300)

  const RISK_FILTERS = [
    { id: 'all',    label: t('home.filters.all'),     dot: null },
    { id: 'red',    label: t('home.filters.high'),    dot: 'bg-red-500' },
    { id: 'yellow', label: t('home.filters.monitor'), dot: 'bg-yellow-400' },
    { id: 'green',  label: t('home.filters.normal'),  dot: 'bg-green-500' },
  ]

  const riskSectionLabel = {
    red:    t('home.sections.highRisk'),
    yellow: t('home.filters.monitor'),
    green:  t('home.filters.normal'),
  }

  // Reset pagination whenever the active filters change
  useEffect(() => {
    setUrgentLimit(PAGE_SIZE)
    setOverdueLimit(PAGE_SIZE)
    setDueTodayLimit(PAGE_SIZE)
  }, [debouncedSearch, village, riskFilter])

  // Unique sorted village list — only recomputed when patient data changes
  const villages = useMemo(() => {
    const all = [
      ...patients.map(p => p.village),
      ...newborns.map(n => n.village),
    ]
    return [...new Set(all)].sort()
  }, [patients, newborns])

  const { urgent, overdue, dueToday, byRisk } = useMemo(() => {
    const q = debouncedSearch.toLowerCase()

    const allRecords = [
      ...patients.map(p => ({ ...p, nextDue: ancNextDue(p) })),
      ...newborns.map(n => ({ ...n, nextDue: newbornNextDue(n) })),
    ]

    const filtered = allRecords.filter(r => {
      if (village !== 'all' && r.village !== village) return false
      if (!q) return true
      // Search across all name fields so the user can type in any language
      if (Object.keys(r).some(k => (k === 'name' || k.startsWith('name_')) && r[k]?.toLowerCase().includes(q))) return true
      if (Object.keys(r).some(k => (k === 'mother_name' || k.startsWith('mother_name_')) && r[k]?.toLowerCase().includes(q))) return true
      return false
    })

    // Risk-filter mode: show ALL patients of that risk level
    if (riskFilter !== 'all') {
      const byRisk = filtered
        .filter(r => r.risk_level === riskFilter)
        .sort((a, b) => (a.nextDue ?? new Date(9e15)) - (b.nextDue ?? new Date(9e15)))
      return { urgent: [], overdue: [], dueToday: [], byRisk }
    }

    // Default "all" mode
    const overdue = filtered
      .filter(r => r.nextDue && daysDiff(r.nextDue) < 0)
      .sort((a, b) => a.nextDue - b.nextDue)

    const dueToday = filtered
      .filter(r => r.nextDue && daysDiff(r.nextDue) === 0)
      .sort((a, b) => a.nextDue - b.nextDue)

    // High risk: red patients not already captured in overdue or dueToday
    const shownIds = new Set([...overdue, ...dueToday].map(r => r.id))
    const urgent = filtered
      .filter(r => r.risk_level === 'red' && !shownIds.has(r.id))
      .sort((a, b) => (a.nextDue ?? new Date(9e15)) - (b.nextDue ?? new Date(9e15)))

    return { urgent, overdue, dueToday, byRisk: [] }
  }, [patients, newborns, debouncedSearch, village, riskFilter])

  function handleClick(record) {
    setSelectedPatient(record)
    navigate(record.patient_type === 'anc' ? `/patient/${record.id}` : `/newborn/${record.id}`)
  }

  const noResults =
    riskFilter === 'all'
      ? urgent.length === 0 && overdue.length === 0 && dueToday.length === 0
      : byRisk.length === 0

  return (
    <div className="flex flex-col min-h-screen bg-gray-50 pb-16">
      {/* Header */}
      <header className="bg-blue-600 px-4 pt-10 pb-4 flex items-center justify-between">
        <div>
          <p className="text-blue-100 text-sm">{t('home.welcomeBack')}</p>
          <h1 className="text-white text-3xl font-bold">{ashaName}</h1>
        </div>
        <div className="flex rounded-full overflow-hidden border border-white/30">
          {[{ code: 'en', label: 'EN' }, { code: 'hi', label: 'हिं' }].map(opt => (
            <button
              key={opt.code}
              onClick={() => setLanguage(opt.code)}
              className={`px-3 py-1.5 text-xs font-semibold transition-colors ${
                language === opt.code ? 'bg-white text-blue-600' : 'text-white/80 hover:text-white'
              }`}
            >
              {opt.label}
            </button>
          ))}
        </div>
      </header>

      {/* Search */}
      <div className="px-4 pt-3 pb-2 bg-white border-b border-gray-100">
        <div className="relative">
          <svg className="absolute left-3 top-1/2 -translate-y-1/2 w-5 h-5 text-gray-400" fill="none" stroke="currentColor" strokeWidth={2} viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" d="M21 21l-4.35-4.35M17 11A6 6 0 1 1 5 11a6 6 0 0 1 12 0z" />
          </svg>
          <input
            type="search"
            value={search}
            onChange={e => setSearch(e.target.value)}
            placeholder={t('home.searchPlaceholder')}
            className="w-full pl-10 pr-4 py-2.5 border border-gray-200 rounded-xl text-base focus:outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100 bg-gray-50"
          />
        </div>
      </div>

      {/* Risk filter pills */}
      <div className="flex gap-2 overflow-x-auto px-4 py-2 bg-white border-b border-gray-100">
        {RISK_FILTERS.map(f => (
          <button
            key={f.id}
            onClick={() => setRiskFilter(f.id)}
            className={`shrink-0 flex items-center gap-1.5 text-xs font-semibold px-3 py-1.5 rounded-full border transition-colors ${
              riskFilter === f.id
                ? riskFilterActive[f.id]
                : 'bg-white text-gray-600 border-gray-200 hover:border-gray-300'
            }`}
          >
            {f.dot && <span className={`w-2 h-2 rounded-full ${riskFilter === f.id ? 'bg-white opacity-80' : f.dot}`} />}
            {f.label}
          </button>
        ))}
      </div>

      {/* Village filter chips — only rendered when there are multiple villages */}
      {villages.length > 1 && (
        <div className="flex gap-2 overflow-x-auto px-4 py-2 bg-white border-b border-gray-100">
          <button
            onClick={() => setVillage('all')}
            className={`shrink-0 text-xs font-semibold px-3 py-1.5 rounded-full border transition-colors ${
              village === 'all'
                ? 'bg-blue-600 text-white border-blue-600'
                : 'bg-white text-gray-600 border-gray-200 hover:border-blue-300'
            }`}
          >
            {t('home.allVillages')}
          </button>
          {villages.map(v => (
            <button
              key={v}
              onClick={() => setVillage(v)}
              className={`shrink-0 text-xs font-semibold px-3 py-1.5 rounded-full border transition-colors ${
                village === v
                  ? 'bg-blue-600 text-white border-blue-600'
                  : 'bg-white text-gray-600 border-gray-200 hover:border-blue-300'
              }`}
            >
              {v.split(',')[0].trim()}
            </button>
          ))}
        </div>
      )}

      {/* Lists */}
      <div className="flex-1 px-4 pb-8">

        {/* Risk-filter mode: flat list of matching patients */}
        {riskFilter !== 'all' && byRisk.length > 0 && (
          <div>
            <SectionHeader title={riskSectionLabel[riskFilter]} count={byRisk.length} />
            <div className="space-y-2.5 mt-1">
              {byRisk.map(r => (
                <PatientRow key={r.id} record={r} nextDue={r.nextDue} onClick={() => handleClick(r)} t={t} language={language} />
              ))}
            </div>
          </div>
        )}

        {/* Default "all" mode sections */}
        {riskFilter === 'all' && (
          <>
            {/* Overdue */}
            {overdue.length > 0 && (
              <div>
                <SectionHeader title={t('home.sections.overdue')} count={overdue.length} urgent />
                <div className="space-y-2.5 mt-1">
                  {overdue.slice(0, overdueLimit).map(r => (
                    <PatientRow key={r.id} record={r} nextDue={r.nextDue} onClick={() => handleClick(r)} t={t} language={language} />
                  ))}
                </div>
                <ShowMoreButton
                  shown={overdueLimit}
                  total={overdue.length}
                  onShowMore={() => setOverdueLimit(prev => prev + PAGE_SIZE)}
                  t={t}
                />
              </div>
            )}

            {/* Due today */}
            {dueToday.length > 0 && (
              <div>
                <SectionHeader title={t('home.sections.dueToday')} count={dueToday.length} />
                <div className="space-y-2.5 mt-1">
                  {dueToday.slice(0, dueTodayLimit).map(r => (
                    <PatientRow key={r.id} record={r} nextDue={r.nextDue} onClick={() => handleClick(r)} t={t} language={language} />
                  ))}
                </div>
                <ShowMoreButton
                  shown={dueTodayLimit}
                  total={dueToday.length}
                  onShowMore={() => setDueTodayLimit(prev => prev + PAGE_SIZE)}
                  t={t}
                />
              </div>
            )}

            {/* High risk — red patients not already in overdue or due today */}
            {urgent.length > 0 && (
              <div>
                <SectionHeader title={t('home.sections.highRisk')} count={urgent.length} urgent />
                <div className="space-y-2.5 mt-1">
                  {urgent.slice(0, urgentLimit).map(r => (
                    <PatientRow key={r.id} record={r} nextDue={r.nextDue} onClick={() => handleClick(r)} t={t} language={language} />
                  ))}
                </div>
                <ShowMoreButton
                  shown={urgentLimit}
                  total={urgent.length}
                  onShowMore={() => setUrgentLimit(prev => prev + PAGE_SIZE)}
                  t={t}
                />
              </div>
            )}
          </>
        )}

        {/* Empty state */}
        {noResults && (
          <div className="text-center py-16 text-gray-400">
            {debouncedSearch || village !== 'all' || riskFilter !== 'all' ? (
              <p className="text-base">{t('home.empty.noResults')}</p>
            ) : (
              <>
                <p className="text-base font-medium text-gray-600">{t('home.empty.allCaughtUp')}</p>
                <p className="text-sm mt-1">{t('home.empty.allCaughtUpSub')}</p>
              </>
            )}
          </div>
        )}
      </div>
    </div>
  )
}
