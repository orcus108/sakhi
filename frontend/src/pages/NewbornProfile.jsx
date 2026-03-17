import React from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { useApp } from '../context/AppContext.jsx'
import TopBar from '../components/TopBar.jsx'
import RiskBadge from '../components/RiskBadge.jsx'
import { localName, localMotherName } from '../utils/nameUtils.js'

/**
 * NewbornProfile.jsx — Newborn patient detail screen (Screen 2b)
 *
 * Mirrors PatientProfile but for postnatal newborn records. Key differences:
 *  - Weight delta (current vs birth) is prominently shown with +/- colour coding
 *  - Next scheduled visit is computed from the standard NBCC visit schedule
 *    (Day 1, 3, 7, 14, 28, 6 Weeks) based on which visits have been completed
 *  - Danger signs in past observations are highlighted in red using keyword matching
 *
 * The hero card background reflects the newborn's current risk level, same
 * pattern as PatientProfile.
 */

// ── Visit schedule constants ──────────────────────────────────────────────────
const VISIT_ORDER = ['day_1', 'day_3', 'day_7', 'day_14', 'day_28', 'week_6']
const VISIT_DAYS  = { day_1: 1, day_3: 3, day_7: 7, day_14: 14, day_28: 28, week_6: 42 }
const VISIT_LABEL = { day_1: 'Day 1', day_3: 'Day 3', day_7: 'Day 7', day_14: 'Day 14', day_28: 'Day 28', week_6: '6 Weeks' }

function getDaysOld(dob) {
  return Math.floor((Date.now() - new Date(dob).getTime()) / 86400000)
}

function getNextVisit(baby) {
  const done = new Set(baby.visit_history.map(v => v.visit_day))
  const nextKey = VISIT_ORDER.find(k => !done.has(k))
  if (!nextKey) return null
  const expectedDate = new Date(new Date(baby.date_of_birth).getTime() + VISIT_DAYS[nextKey] * 86400000)
  return {
    label: VISIT_LABEL[nextKey],
    date: expectedDate.toISOString().split('T')[0],
  }
}

function weightDelta(baby) {
  const diff = (baby.current_weight_kg - baby.birth_weight_kg).toFixed(2)
  const pct = (((baby.current_weight_kg - baby.birth_weight_kg) / baby.birth_weight_kg) * 100).toFixed(1)
  return { diff, pct, gained: diff >= 0 }
}

function StatCard({ label, value, unit, valueClass = 'text-gray-900' }) {
  return (
    <div className="bg-white rounded-xl p-3 text-center shadow-sm">
      <p className={`text-2xl font-bold ${valueClass}`}>{value}</p>
      {unit && <p className="text-xs text-gray-400">{unit}</p>}
      <p className="text-xs text-gray-500 mt-0.5">{label}</p>
    </div>
  )
}

/**
 * WeightChart — SVG sparkline for newborn weight trajectory.
 * Draws a dashed horizontal reference line at birth weight so the
 * "dip below → recovery above" pattern is immediately visible.
 * Birth weight is included in the Y-scale so the reference line
 * is always visible even when all visit weights are above/below it.
 */
function WeightChart({ weights, birthWeight, color }) {
  if (!weights || weights.length < 2) return null
  const all = [...weights, birthWeight]
  const min = Math.min(...all)
  const max = Math.max(...all)
  const range = max - min || 1
  const W = 100, H = 52, px = 4, py = 7
  const toY = v => +(H - py - ((v - min) / range) * (H - py * 2)).toFixed(1)
  const pts = weights.map((v, i) => ({
    x: +(px + (i / (weights.length - 1)) * (W - px * 2)).toFixed(1),
    y: toY(v),
  }))
  const refY = toY(birthWeight)
  const area = `M${pts[0].x},${H} ${pts.map(p => `L${p.x},${p.y}`).join(' ')} L${pts.at(-1).x},${H} Z`
  return (
    <svg viewBox={`0 0 ${W} ${H}`} className="w-full" style={{ height: 52 }}>
      {/* Birth weight reference line */}
      <line x1={px} y1={refY} x2={W - px} y2={refY}
        stroke="#9ca3af" strokeWidth="1" strokeDasharray="3,2" />
      <path d={area} fill={color} fillOpacity="0.12" />
      <polyline
        points={pts.map(p => `${p.x},${p.y}`).join(' ')}
        fill="none" stroke={color} strokeWidth="2"
        strokeLinecap="round" strokeLinejoin="round"
      />
      {pts.map((p, i) => (
        <circle key={i} cx={p.x} cy={p.y} r={i === pts.length - 1 ? 3.5 : 2} fill={color} />
      ))}
    </svg>
  )
}

export default function NewbornProfile() {
  const { id } = useParams()
  const navigate = useNavigate()
  const { t } = useTranslation()
  const { newborns, setSelectedPatient, language } = useApp()

  const baby = newborns.find(n => n.id === id)

  if (!baby) {
    return (
      <div className="flex flex-col min-h-screen bg-gray-50">
        <TopBar title={t('newborn.notFound')} backTo={null} />
        <div className="flex-1 flex items-center justify-center text-gray-400">
          {t('newborn.notFound')}
        </div>
      </div>
    )
  }

  const daysOld   = getDaysOld(baby.date_of_birth)
  const nextVisit = getNextVisit(baby)
  const wt        = weightDelta(baby)
  const lastVisit = baby.visit_history.at(-1)

  // Weight trend chart data — sorted chronologically
  const sortedVisits = [...baby.visit_history].sort((a, b) => new Date(a.date) - new Date(b.date))
  const visitWeights = sortedVisits.map(v => v.weight_kg)
  const latestWeight = baby.current_weight_kg
  const weightTrendColor = latestWeight >= baby.birth_weight_kg ? '#16a34a'
    : latestWeight >= 2.5 ? '#ca8a04'
    : '#ef4444'
  const weightDeltaFromBirth = Math.round((latestWeight - baby.birth_weight_kg) * 100) / 100
  const showWeightTrend = baby.visit_history.length >= 2

  const heroBg = baby.risk_level === 'red'
    ? 'bg-red-50'
    : baby.risk_level === 'yellow'
      ? 'bg-yellow-50'
      : 'bg-green-50'

  function startCheckup() {
    setSelectedPatient(baby)
    navigate(`/newborn/${baby.id}/checkup`)
  }

  return (
    <div className="flex flex-col min-h-screen bg-gray-50">
      <TopBar title={localName(baby, language)} backTo={null} />

      <div className="flex-1 overflow-y-auto pb-28">

        {/* Hero card */}
        <div className="px-4 pt-4">
          <div className={`rounded-2xl p-5 shadow-lg ${heroBg}`}>
            <div className="flex items-start justify-between">
              <div>
                <h2 className="text-xl font-bold text-gray-900">{localName(baby, language)}</h2>
                <p className="text-gray-600 mt-0.5 capitalize">
                  {baby.gender} · {baby.village}
                </p>
                <p className="text-gray-500 text-sm mt-1">
                  {t('newborn.mother')} <span className="font-medium">{localMotherName(baby, language)}</span>
                </p>
                <p className="text-gray-500 text-sm">
                  {t('newborn.dob')} {baby.date_of_birth}
                </p>
                {baby.abdm_id ? (
                  <button
                    onClick={() => navigator.clipboard?.writeText(baby.abdm_id)}
                    className="flex items-center gap-1.5 mt-2 active:opacity-70 transition-opacity"
                    title="Tap to copy ABHA ID"
                  >
                    <svg className="w-3 h-3 text-blue-400 shrink-0" fill="none" stroke="currentColor" strokeWidth={2} viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" d="M9 12.75L11.25 15 15 9.75m-3-7.036A11.959 11.959 0 013.598 6 11.99 11.99 0 003 9.749c0 5.592 3.824 10.29 9 11.623 5.176-1.332 9-6.03 9-11.622 0-1.31-.21-2.571-.598-3.751h-.152c-3.196 0-6.1-1.248-8.25-3.285z" />
                    </svg>
                    <span className="text-xs font-medium text-blue-500 uppercase tracking-wide">ABHA</span>
                    <span className="text-xs font-mono text-gray-500 bg-white/70 px-2 py-0.5 rounded-full">{baby.abdm_id}</span>
                  </button>
                ) : (
                  <p className="text-xs text-gray-400 mt-2 italic">No ABHA ID — not yet registered</p>
                )}
              </div>
              <RiskBadge level={baby.risk_level} size="sm" />
            </div>

            {/* Prominent age display */}
            <div className="mt-3 pt-3 border-t border-white/60">
              <p className="text-4xl font-bold text-gray-900">
                {daysOld} <span className="text-lg font-normal text-gray-500">
                  {t('newborn.daysOld', { count: daysOld }).replace(/^\d+ /, '')}
                </span>
              </p>
              <p className="text-xs text-gray-400 uppercase tracking-wide mt-0.5">
                {t('newborn.born')} {baby.date_of_birth}
              </p>
            </div>
          </div>
        </div>

        {/* Weight stats */}
        <div className="px-4 mt-5">
          <h3 className="text-xs font-semibold text-gray-400 uppercase tracking-wide mb-3">{t('newborn.weight')}</h3>
          <div className="grid grid-cols-3 gap-2">
            <StatCard label={t('newborn.birthWeight')} value={baby.birth_weight_kg} unit="kg" />
            <StatCard
              label={t('newborn.currentWeight')}
              value={baby.current_weight_kg}
              unit="kg"
              valueClass={
                baby.current_weight_kg < 2.5
                  ? 'text-red-600'
                  : baby.current_weight_kg < baby.birth_weight_kg
                    ? 'text-yellow-600'
                    : 'text-green-700'
              }
            />
            <StatCard
              label={wt.gained ? t('newborn.gained') : t('newborn.lost')}
              value={`${wt.gained ? '+' : ''}${wt.diff} kg`}
              unit={`${wt.gained ? '+' : ''}${wt.pct}%`}
              valueClass={wt.gained ? 'text-green-700' : 'text-yellow-600'}
            />
          </div>
        </div>

        {/* Weight trend chart */}
        {showWeightTrend && (
          <div className="px-4 mt-5">
            <h3 className="text-xs font-semibold text-gray-400 uppercase tracking-wide mb-3">
              {t('newborn.weightTrend')}
            </h3>
            <div className="bg-white rounded-2xl p-4 shadow-sm">
              <div className="flex items-baseline justify-between mb-3">
                <div>
                  <span className="text-xl font-bold" style={{ color: weightTrendColor }}>{latestWeight}</span>
                  <span className="text-xs text-gray-400 ml-1">kg now</span>
                </div>
                <span className="text-xs text-gray-400">
                  {weightDeltaFromBirth >= 0 ? '+' : ''}{weightDeltaFromBirth} kg since birth · {visitWeights.length} visits
                </span>
              </div>
              <WeightChart weights={visitWeights} birthWeight={baby.birth_weight_kg} color={weightTrendColor} />
              <div className="flex items-center gap-1.5 mt-2">
                <svg width="16" height="8" className="shrink-0">
                  <line x1="0" y1="4" x2="16" y2="4" stroke="#9ca3af" strokeWidth="1.5" strokeDasharray="3,2" />
                </svg>
                <p className="text-xs text-gray-400">birth weight {baby.birth_weight_kg} kg</p>
              </div>
            </div>
          </div>
        )}

        {/* Next scheduled visit */}
        {nextVisit ? (
          <div className="px-4 mt-4">
            <div className="bg-green-50 border border-green-200 rounded-2xl p-4 flex items-center justify-between">
              <div>
                <p className="text-xs font-semibold text-green-700 uppercase tracking-wide">{t('newborn.nextVisitDue')}</p>
                <p className="text-lg font-bold text-gray-900 mt-0.5">{nextVisit.label}</p>
                <p className="text-sm text-gray-500">{nextVisit.date}</p>
              </div>
              <svg className="w-8 h-8 text-green-400" fill="none" stroke="currentColor" strokeWidth={1.5} viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" d="M6.75 3v2.25M17.25 3v2.25M3 18.75V7.5a2.25 2.25 0 012.25-2.25h13.5A2.25 2.25 0 0121 7.5v11.25m-18 0A2.25 2.25 0 005.25 21h13.5A2.25 2.25 0 0021 18.75m-18 0v-7.5A2.25 2.25 0 015.25 9h13.5A2.25 2.25 0 0121 11.25v7.5" />
              </svg>
            </div>
          </div>
        ) : (
          <div className="px-4 mt-4">
            <div className="bg-gray-50 border border-gray-100 rounded-2xl p-4 text-center">
              <p className="text-sm text-gray-500">{t('newborn.allVisitsDone')}</p>
            </div>
          </div>
        )}

        {/* Latest observations */}
        {lastVisit && (
          <div className="px-4 mt-5">
            <h3 className="text-xs font-semibold text-gray-400 uppercase tracking-wide mb-3">
              {t('newborn.latestVisit')} — {VISIT_LABEL[lastVisit.visit_day] || lastVisit.visit_day} ({lastVisit.date})
            </h3>
            <div className="bg-white rounded-2xl p-4 shadow-sm">
              {lastVisit.observations.length > 0 && (
                <div className="flex flex-wrap gap-1.5 mb-3">
                  {lastVisit.observations.map(obs => {
                    const isDanger = obs.toLowerCase().includes('jaundice') ||
                      obs.toLowerCase().includes('lethargic') ||
                      obs.toLowerCase().includes('labored') ||
                      obs.toLowerCase().includes('infected') ||
                      obs.toLowerCase().includes('rash') ||
                      obs.toLowerCase().includes('discharge')
                    return (
                      <span
                        key={obs}
                        className={`text-xs px-2.5 py-1 rounded-full font-medium ${
                          isDanger
                            ? 'bg-red-50 text-red-600 border border-red-200'
                            : 'bg-green-50 text-green-700 border border-green-200'
                        }`}
                      >
                        {obs}
                      </span>
                    )
                  })}
                </div>
              )}
              {lastVisit.other_observations && (
                <p className="text-sm text-gray-500 italic mb-2">"{lastVisit.other_observations}"</p>
              )}
              {lastVisit.notes && (
                <p className="text-sm text-gray-600 border-t border-gray-50 pt-2 mt-1">{lastVisit.notes}</p>
              )}
            </div>
          </div>
        )}

        {/* Visit history */}
        <div className="px-4 mt-5">
          <h3 className="text-xs font-semibold text-gray-400 uppercase tracking-wide mb-3">{t('newborn.visitHistory')}</h3>
          <div className="space-y-3">
            {[...baby.visit_history].reverse().map((v, i) => (
              <div key={i} className="rounded-2xl p-4 bg-white shadow-sm">
                <div className="flex items-center justify-between mb-2">
                  <div>
                    <p className="text-sm font-semibold text-gray-700">
                      {VISIT_LABEL[v.visit_day] || v.visit_day}
                    </p>
                    <p className="text-xs text-gray-400">{v.date}</p>
                  </div>
                  <RiskBadge level={v.risk_level} size="sm" />
                </div>
                <p className="text-sm text-gray-600">
                  {t('newborn.weight')}: <strong>{v.weight_kg} kg</strong>
                </p>
                {v.notes && (
                  <p className="mt-1.5 text-sm text-gray-500 italic">{v.notes}</p>
                )}
              </div>
            ))}
          </div>
        </div>
      </div>

      {/* Sticky CTA */}
      <div className="fixed bottom-0 left-0 right-0 flex justify-center pointer-events-none">
        <div className="w-full max-w-[430px] px-4 pb-6 pt-4 bg-gradient-to-t from-white via-white to-transparent pointer-events-auto">
          <button
            onClick={startCheckup}
            className="w-full bg-blue-600 hover:bg-blue-700 active:bg-blue-800 text-white font-semibold text-lg rounded-xl py-4 transition-colors shadow-lg"
          >
            {t('newborn.startCheckup')}
          </button>
        </div>
      </div>
    </div>
  )
}
