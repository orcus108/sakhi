import { useState, useMemo } from 'react'
import { useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { useApp } from '../context/AppContext.jsx'
import RiskBadge from '../components/RiskBadge.jsx'
import { localName, localMotherName } from '../utils/nameUtils.js'

/**
 * Schedule.jsx — Upcoming appointment calendar view (Screen 7)
 *
 * Computes the next due date for every patient (ANC and newborn) and
 * organises them into four time buckets: Overdue, Today, This Week, Coming Up.
 *
 * ANC next-due: last checkup date + risk-based interval (ANC_INTERVAL)
 * Newborn next-due: date_of_birth + days to the next unvisited milestone
 *
 * The range toggle (Week / Month / All) filters the "Coming Up" section only;
 * overdue and today events are always visible regardless of selected range.
 *
 * The "Switch worker" button in the footer clears the session (logout) so a
 * different ASHA worker can log in on a shared device.
 */

// ── Visit schedule constants (mirrors NewbornProfile) ─────────────────────────
const VISIT_ORDER = ['day_1', 'day_3', 'day_7', 'day_14', 'day_28', 'week_6']
const VISIT_DAYS  = { day_1: 1, day_3: 3, day_7: 7, day_14: 14, day_28: 28, week_6: 42 }
const VISIT_LABEL = { day_1: 'Day 1', day_3: 'Day 3', day_7: 'Day 7', day_14: 'Day 14', day_28: 'Day 28', week_6: '6 Weeks' }

// ── ANC: interval from last checkup based on risk ─────────────────────────────
const ANC_INTERVAL = { red: 7, yellow: 14, green: 28 }

function addDays(dateStr, days) {
  return new Date(new Date(dateStr).getTime() + days * 86400000)
    .toISOString().split('T')[0]
}

function daysDiff(dateStr) {
  const today = new Date(); today.setHours(0,0,0,0)
  const d = new Date(dateStr); d.setHours(0,0,0,0)
  return Math.round((d - today) / 86400000)
}

function formatDate(dateStr) {
  return new Date(dateStr).toLocaleDateString('en-IN', { day: 'numeric', month: 'short' })
}

// ── Compute all upcoming events ───────────────────────────────────────────────
function buildEvents(patients, newborns, t, language) {
  const events = []

  for (const p of patients) {
    const last = p.checkup_history.at(-1)
    if (!last) continue
    const nextDate = addDays(last.date, ANC_INTERVAL[p.risk_level] ?? 28)
    events.push({
      id:          p.id,
      name:        localName(p, language),
      type:        'anc',
      event:       t('schedule.ancCheckup'),
      date:        nextDate,
      risk_level:  p.risk_level,
      path:        `/patient/${p.id}`,
      checkupPath: `/patient/${p.id}/checkup`,
      subtitle:    `${p.gestational_weeks} ${t('patient.weeksPregnant')}`,
    })
  }

  for (const b of newborns) {
    const done    = new Set(b.visit_history.map(v => v.visit_day))
    const nextKey = VISIT_ORDER.find(k => !done.has(k))
    if (!nextKey) continue
    const nextDate = addDays(b.date_of_birth, VISIT_DAYS[nextKey])
    events.push({
      id:          b.id,
      name:        localName(b, language),
      type:        'newborn',
      event:       t('schedule.newbornVisit', { label: VISIT_LABEL[nextKey] }),
      date:        nextDate,
      risk_level:  b.risk_level,
      path:        `/newborn/${b.id}`,
      checkupPath: `/newborn/${b.id}/checkup`,
      subtitle:    `${t('newborn.mother')} ${localMotherName(b, language)}`,
    })
  }

  return events.sort((a, b) => a.date.localeCompare(b.date))
}

// ── Section grouping ──────────────────────────────────────────────────────────
function groupEvents(events) {
  const overdue   = []
  const today     = []
  const thisWeek  = []
  const upcoming  = []

  for (const e of events) {
    const diff = daysDiff(e.date)
    if (diff < 0)       overdue.push(e)
    else if (diff === 0) today.push(e)
    else if (diff <= 7)  thisWeek.push(e)
    else                 upcoming.push(e)
  }

  return { overdue, today, thisWeek, upcoming }
}

// ── Single event card ─────────────────────────────────────────────────────────
const riskAccent = { red: 'bg-red-500', yellow: 'bg-yellow-400', green: 'bg-green-500' }

function EventCard({ event, onClick, directCheckup = false, t }) {
  const diff  = daysDiff(event.date)
  const label = diff < 0
    ? t('schedule.relative.daysOverdue', { count: Math.abs(diff) })
    : diff === 0
      ? t('schedule.relative.today')
      : t('schedule.relative.inDays', { count: diff })

  const labelColor = diff < 0
    ? 'text-red-600'
    : diff === 0
      ? 'text-blue-600'
      : 'text-gray-500'

  return (
    <button
      onClick={onClick}
      className="w-full text-left bg-white rounded-2xl shadow-sm hover:shadow-md transition-all active:scale-[0.99] overflow-hidden flex"
    >
      <div className={`w-1.5 shrink-0 ${riskAccent[event.risk_level]}`} />
      <div className="flex-1 px-4 py-3.5">
        <div className="flex items-start justify-between gap-3">
          <div className="min-w-0">
            <p className="text-base font-semibold text-gray-900 truncate">{event.name}</p>
            <p className="text-sm text-gray-500 mt-0.5">{event.event}</p>
            <p className="text-xs text-gray-400 mt-0.5">{event.subtitle}</p>
          </div>
          <div className="shrink-0 text-right">
            <RiskBadge level={event.risk_level} size="sm" />
            <p className={`text-xs font-semibold mt-1 ${labelColor}`}>
              {formatDate(event.date)}
            </p>
            <p className={`text-xs ${labelColor}`}>{label}</p>
            {directCheckup && (
              <p className="text-xs font-semibold text-blue-600 mt-1">{t('schedule.startCheckup')}</p>
            )}
          </div>
        </div>
      </div>
    </button>
  )
}

function Section({ title, events, onEventClick, directCheckup = false, titleClass = 'text-gray-500', t }) {
  if (!events.length) return null
  return (
    <div>
      <p className={`text-xs font-semibold uppercase tracking-wide mb-2 ${titleClass}`}>
        {title}
      </p>
      <div className="space-y-2.5">
        {events.map(e => (
          <EventCard key={`${e.id}-${e.date}`} event={e} onClick={() => onEventClick(e)} directCheckup={directCheckup} t={t} />
        ))}
      </div>
    </div>
  )
}

// ── Page ──────────────────────────────────────────────────────────────────────
export default function Schedule() {
  const { ashaName, patients, newborns, logout, language } = useApp()
  const { t } = useTranslation()
  const navigate = useNavigate()
  const [range, setRange] = useState('month')

  const RANGE_OPTIONS = [
    { key: 'week',  label: t('schedule.ranges.week'),  days: 7        },
    { key: 'month', label: t('schedule.ranges.month'), days: 30       },
    { key: 'all',   label: t('schedule.ranges.all'),   days: Infinity },
  ]

  const events = useMemo(() => buildEvents(patients, newborns, t, language), [patients, newborns, t, language])
  const { overdue, today, thisWeek, upcoming } = useMemo(() => groupEvents(events), [events])

  const visibleUpcoming = useMemo(() => {
    const cutoff = RANGE_OPTIONS.find(r => r.key === range)?.days ?? 30
    if (cutoff === Infinity) return upcoming
    return upcoming.filter(e => daysDiff(e.date) <= cutoff)
  }, [upcoming, range, RANGE_OPTIONS])

  const hiddenCount = upcoming.length - visibleUpcoming.length
  const hasAny = overdue.length + today.length + thisWeek.length + visibleUpcoming.length > 0

  const todayStr = new Date().toLocaleDateString('en-IN', {
    weekday: 'long', day: 'numeric', month: 'long'
  })

  return (
    <div className="flex flex-col min-h-screen bg-gray-50 pb-16">
      {/* Header */}
      <header className="bg-blue-600 px-4 pt-10 pb-4 flex items-center justify-between">
        <div>
          <h1 className="text-white text-3xl font-bold">{t('schedule.title')}</h1>
          <p className="text-blue-100 text-sm mt-0.5">{todayStr}</p>
        </div>
        <svg className="w-7 h-7 text-white opacity-90" fill="currentColor" viewBox="0 0 24 24">
          <path d="M4.318 6.318a4.5 4.5 0 000 6.364L12 20.364l7.682-7.682a4.5 4.5 0 00-6.364-6.364L12 7.636l-1.318-1.318a4.5 4.5 0 00-6.364 0z" />
        </svg>
      </header>

      {/* Summary pills + range toggle */}
      <div className="bg-white border-b border-gray-100 px-4 py-3 space-y-2.5">
        {(overdue.length > 0 || today.length > 0 || thisWeek.length > 0) && (
          <div className="flex items-center gap-2">
            {overdue.length > 0 && (
              <span className="bg-red-100 text-red-700 text-xs font-semibold px-3 py-1 rounded-full border border-red-200">
                {t('schedule.pills.overdue', { count: overdue.length })}
              </span>
            )}
            {today.length > 0 && (
              <span className="bg-blue-100 text-blue-700 text-xs font-semibold px-3 py-1 rounded-full border border-blue-200">
                {t('schedule.pills.today', { count: today.length })}
              </span>
            )}
            {thisWeek.length > 0 && (
              <span className="bg-gray-100 text-gray-600 text-xs font-semibold px-3 py-1 rounded-full border border-gray-200">
                {t('schedule.pills.thisWeek', { count: thisWeek.length })}
              </span>
            )}
          </div>
        )}

        <div className="flex items-center gap-1.5">
          <span className="text-xs text-gray-400 shrink-0">{t('schedule.showUpcoming')}</span>
          {RANGE_OPTIONS.map(opt => (
            <button
              key={opt.key}
              onClick={() => setRange(opt.key)}
              className={`text-xs font-semibold px-3 py-1.5 rounded-full border transition-colors ${
                range === opt.key
                  ? 'bg-blue-600 text-white border-blue-600'
                  : 'bg-white text-gray-600 border-gray-200 hover:border-blue-300'
              }`}
            >
              {opt.label}
            </button>
          ))}
        </div>
      </div>

      {/* Event list */}
      <div className="flex-1 px-4 pt-4 pb-8 space-y-6">
        {!hasAny && (
          <div className="flex flex-col items-center justify-center py-20 text-center">
            <div className="w-16 h-16 rounded-full bg-green-100 flex items-center justify-center mb-4">
              <svg className="w-8 h-8 text-green-600" fill="none" stroke="currentColor" strokeWidth={1.5} viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" d="M9 12.75L11.25 15 15 9.75M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
              </svg>
            </div>
            <p className="text-lg font-semibold text-gray-700">{t('schedule.allClear')}</p>
            <p className="text-sm text-gray-400 mt-1">{t('schedule.allClearSub')}</p>
          </div>
        )}

        <Section title={t('schedule.sections.overdue')}  events={overdue}         onEventClick={e => navigate(e.path)} titleClass="text-red-600"  t={t} />
        <Section title={t('schedule.sections.today')}    events={today}           onEventClick={e => navigate(e.path)} titleClass="text-blue-600" t={t} />
        <Section title={t('schedule.sections.thisWeek')} events={thisWeek}        onEventClick={e => navigate(e.path)} t={t} />
        <Section title={t('schedule.sections.comingUp')} events={visibleUpcoming} onEventClick={e => navigate(e.path)} t={t} />

        {hiddenCount > 0 && (
          <p className="text-center text-xs text-gray-400 pb-2">
            {t('schedule.hiddenCount', { count: hiddenCount })}{' '}
            <button onClick={() => setRange('all')} className="text-blue-500 font-medium underline">
              {t('schedule.showAll')}
            </button>
          </p>
        )}
      </div>

      {/* Footer */}
      <div className="px-4 pb-6 text-center">
        <button onClick={logout} className="text-sm text-gray-400 hover:text-gray-600">
          {t('schedule.switchWorker', { name: ashaName })}
        </button>
      </div>
    </div>
  )
}
