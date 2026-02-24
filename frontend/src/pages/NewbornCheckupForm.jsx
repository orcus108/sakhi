import { useState, useEffect } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { useApp } from '../context/AppContext.jsx'
import TopBar from '../components/TopBar.jsx'
import { fetchCheckupAssessment } from '../api/api.js'

/**
 * NewbornCheckupForm.jsx — Newborn postnatal visit form (Screen 4b)
 *
 * Two-step form mirroring CheckupForm but for newborns:
 *  Step 0 (Measurements) – visit day selector + current weight
 *  Step 1 (Observations) – grouped observation tiles (feeding, breathing, skin)
 *
 * The visit day selector auto-selects the next recommended milestone visit
 * based on which visits have already been recorded (getRecommendedVisit).
 * A blue dot indicator marks the recommended option to guide the ASHA worker.
 *
 * Observation items carry an isDanger flag. When a danger-sign observation
 * is selected, an inline red warning box appears to prompt urgent attention.
 *
 * API contract: observation labels are always sent in English regardless of
 * UI language (same pattern as ANC symptoms). The key/label separation keeps
 * translation at the display layer and the API payload language-stable.
 *
 * Draft persistence: same pattern as CheckupForm — checkupDraft in AppContext
 * preserves form state if the ASHA navigates to Ask Sakhi mid-visit.
 */

// ── Visit schedule ────────────────────────────────────────────────────────────
const VISIT_OPTIONS = [
  { key: 'day_1',  label: 'Day 1' },
  { key: 'day_3',  label: 'Day 3' },
  { key: 'day_7',  label: 'Day 7' },
  { key: 'day_14', label: 'Day 14' },
  { key: 'day_28', label: 'Day 28' },
  { key: 'week_6', label: '6 Weeks' },
]

// Observation items — labels MUST stay English (sent to API)
// isDanger drives colour, key drives translation lookup
const OBSERVATION_GROUPS = [
  {
    titleKey: 'feeding',
    title: 'Feeding & Activity',
    items: [
      { key: 'feeding_well',   label: 'Feeding well (breastfeeding established)', isDanger: false },
      { key: 'baby_alert',     label: 'Baby alert and active',                    isDanger: false },
      { key: 'baby_lethargic', label: 'Baby lethargic or not responding',         isDanger: true  },
    ],
  },
  {
    titleKey: 'breathing',
    title: 'Breathing',
    items: [
      { key: 'breathing_normal',  label: 'Breathing normal',          isDanger: false },
      { key: 'breathing_labored', label: 'Breathing labored or fast', isDanger: true  },
    ],
  },
  {
    titleKey: 'skin',
    title: 'Skin, Cord & Eyes',
    items: [
      { key: 'cord_clean',    label: 'Umbilical cord — clean and dry',           isDanger: false },
      { key: 'cord_infected', label: 'Umbilical cord — red / swollen / smelly',  isDanger: true  },
      { key: 'jaundice',      label: 'Jaundice visible (yellow skin / eyes)',     isDanger: true  },
      { key: 'skin_rash',     label: 'Skin rash or pustules',                    isDanger: true  },
      { key: 'eye_discharge', label: 'Eyes — discharge present',                 isDanger: true  },
    ],
  },
]

// Flat list used for submission (labels stay English)
const OBSERVATIONS = OBSERVATION_GROUPS.flatMap(g => g.items)

function getRecommendedVisit(baby) {
  const done = new Set(baby.visit_history.map(v => v.visit_day))
  return VISIT_OPTIONS.find(v => !done.has(v.key))?.key || 'day_1'
}

export default function NewbornCheckupForm() {
  const { id } = useParams()
  const navigate = useNavigate()
  const { t } = useTranslation()
  const { newborns, setLastAssessment, checkupDraft, setCheckupDraft, saveCheckup, language } = useApp()

  const baby = newborns.find(n => n.id === id)

  const STEP_LABELS = [t('newbornCheckup.steps.measurements'), t('newbornCheckup.steps.observations')]

  const [step, setStep] = useState(0)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')

  // Step 1 state
  const [visitDay, setVisitDay] = useState(() =>
    baby ? getRecommendedVisit(baby) : 'day_1'
  )
  const [weight, setWeight] = useState('')

  // Step 2 state
  const [selected, setSelected] = useState(new Set())
  const [otherObs, setOtherObs] = useState('')

  // Restore form state if returning from Ask Sakhi mid-checkup
  useEffect(() => {
    if (checkupDraft?.patientId === id && checkupDraft?.patientType === 'newborn') {
      const v = checkupDraft.vitals
      if (v.visit_day) setVisitDay(v.visit_day)
      if (v.weight_kg) setWeight(String(v.weight_kg))
      if (v.observations) {
        const keys = OBSERVATIONS.filter(o => v.observations.includes(o.label)).map(o => o.key)
        setSelected(new Set(keys))
      }
      if (v.other_observations) setOtherObs(v.other_observations)
      if (checkupDraft.step != null) setStep(checkupDraft.step)
    }
  }, []) // eslint-disable-line react-hooks/exhaustive-deps

  if (!baby) {
    return (
      <div className="flex flex-col min-h-screen bg-gray-50">
        <TopBar title={t('newbornCheckup.title')} backTo={null} />
        <div className="flex-1 flex items-center justify-center text-gray-400">
          {t('newbornCheckup.notFound')}
        </div>
      </div>
    )
  }

  function toggleObs(key) {
    setSelected(prev => {
      const next = new Set(prev)
      next.has(key) ? next.delete(key) : next.add(key)
      return next
    })
  }

  function handleAskSakhi() {
    const observationLabels = OBSERVATIONS.filter(o => selected.has(o.key)).map(o => o.label)
    setCheckupDraft({
      patientId: id,
      patientType: 'newborn',
      step,
      vitals: {
        visit_day: visitDay,
        weight_kg: weight ? Number(weight) : null,
        observations: observationLabels,
        other_observations: otherObs.trim(),
      },
    })
    navigate(`/ask?patient=${id}&type=newborn&from=checkup`)
  }

  function validateStep0() {
    if (!weight || isNaN(Number(weight)) || Number(weight) <= 0)
      return t('newbornCheckup.validationWeight')
    return ''
  }

  async function handleNext() {
    if (step === 0) {
      const err = validateStep0()
      if (err) { setError(err); return }
      setError('')
      setStep(1)
      return
    }

    // Step 1 → submit
    setLoading(true)
    setError('')

    // Convert selected keys to English labels (API contract)
    const observationLabels = OBSERVATIONS
      .filter(o => selected.has(o.key))
      .map(o => o.label)

    const checkup = {
      date: new Date().toISOString().split('T')[0],
      visit_day: visitDay,
      weight_kg: Number(weight),
      observations: observationLabels,
      other_observations: otherObs.trim(),
    }

    try {
      const result = await fetchCheckupAssessment(baby, checkup, 'newborn', language)
      saveCheckup(id, 'newborn', checkup, result)
      setLastAssessment({ assessment: result, checkup, patientId: id })
      navigate(`/newborn/${id}/assessment`, { replace: true })
    } catch (e) {
      setError(`Could not get assessment: ${e.message}`)
    } finally {
      setLoading(false)
    }
  }

  const activeDangerSigns = OBSERVATIONS.filter(o => o.isDanger && selected.has(o.key))
  const delta = weight ? (Number(weight) - baby.birth_weight_kg).toFixed(2) : null

  return (
    <div className="flex flex-col min-h-screen bg-gray-50">
      <TopBar
        title={`${t('newbornCheckup.title')} — ${baby.name}`}
        backTo={step === 0 ? null : undefined}
      />

      {/* Step indicator */}
      <div className="px-4 pt-3 pb-1 bg-white">
        <div className="flex gap-2">
          {STEP_LABELS.map((label, i) => (
            <div key={i} className="flex-1">
              <div className={`h-1.5 rounded-full transition-colors ${i <= step ? 'bg-blue-500' : 'bg-gray-200'}`} />
              <p className={`text-xs mt-1 font-medium ${i === step ? 'text-blue-600' : 'text-gray-400'}`}>
                {i + 1}. {label}
              </p>
            </div>
          ))}
        </div>
      </div>

      <div className="flex-1 overflow-y-auto px-4 pt-4 pb-32">

        {/* ── Step 1: Measurements ── */}
        {step === 0 && (
          <div className="space-y-6">
            <p className="text-sm text-gray-500">
              {baby.name} · {baby.mother_name}'s baby · {t('newbornCheckup.birthWeight')} {baby.birth_weight_kg} kg
            </p>

            {/* Visit day selector */}
            <div>
              <label className="block text-base font-semibold text-gray-800 mb-1">
                {t('newbornCheckup.visitSelector.label')} <span className="text-red-500">*</span>
              </label>
              <p className="text-sm text-gray-400 mb-3">
                {t('newbornCheckup.visitSelector.hint')}
              </p>
              <div className="grid grid-cols-3 gap-2">
                {VISIT_OPTIONS.map(opt => {
                  const isRecommended = opt.key === getRecommendedVisit(baby)
                  const isSelected = visitDay === opt.key
                  return (
                    <button
                      key={opt.key}
                      onClick={() => setVisitDay(opt.key)}
                      className={`py-3 rounded-xl border-2 text-sm font-semibold transition-all relative ${
                        isSelected
                          ? 'bg-blue-600 border-blue-600 text-white'
                          : 'bg-white border-gray-200 text-gray-700'
                      }`}
                    >
                      {opt.label}
                      {isRecommended && !isSelected && (
                        <span className="absolute -top-1.5 -right-1.5 w-3 h-3 bg-blue-500 rounded-full border-2 border-white" />
                      )}
                    </button>
                  )
                })}
              </div>
            </div>

            {/* Weight */}
            <div>
              <label className="block text-base font-semibold text-gray-800 mb-1">
                {t('newbornCheckup.currentWeight.label')}
              </label>
              <p className="text-sm text-gray-400 mb-2">
                {t('newbornCheckup.currentWeight.hint')}
              </p>
              <input
                type="number"
                value={weight}
                onChange={e => { setWeight(e.target.value); setError('') }}
                placeholder={t('newbornCheckup.currentWeight.placeholder')}
                min="0.5"
                step="0.05"
                inputMode="decimal"
                className="w-full border-2 border-gray-200 rounded-xl px-4 py-3 text-3xl font-bold focus:outline-none focus:border-blue-500 transition-colors text-center"
              />
              {weight && baby.birth_weight_kg && (
                <p className={`mt-2 text-sm font-medium text-center ${
                  Number(weight) >= baby.birth_weight_kg
                    ? 'text-green-600'
                    : Number(weight) < baby.birth_weight_kg * 0.9
                      ? 'text-red-500'
                      : 'text-yellow-600'
                }`}>
                  {Number(weight) >= baby.birth_weight_kg
                    ? t('newbornCheckup.currentWeight.aboveBirth', { delta: Math.abs(delta) })
                    : t('newbornCheckup.currentWeight.belowBirth', { delta: Math.abs(delta) })
                  }
                </p>
              )}
            </div>
          </div>
        )}

        {/* ── Step 2: Observations ── */}
        {step === 1 && (
          <div className="space-y-4">
            <div>
              <p className="text-base font-semibold text-gray-800 mb-1">
                {t('newbornCheckup.observationsTitle')}
              </p>
              <p className="text-sm text-gray-500 mb-4">
                {t('newbornCheckup.observationsHint')} {t('assessment.visit')}: <strong>{VISIT_OPTIONS.find(v => v.key === visitDay)?.label}</strong> · {t('newborn.weight')}: <strong>{weight} kg</strong>
              </p>

              <div className="space-y-5">
                {OBSERVATION_GROUPS.map(group => (
                  <div key={group.titleKey}>
                    <p className="text-xs font-semibold text-gray-400 uppercase tracking-wide mb-2">
                      {t(`newbornCheckup.groups.${group.titleKey}`, group.title)}
                    </p>
                    <div className="space-y-2">
                      {group.items.map(obs => {
                        const isSelected = selected.has(obs.key)
                        return (
                          <button
                            key={obs.key}
                            onClick={() => toggleObs(obs.key)}
                            className={`w-full text-left px-4 py-3 rounded-xl border-2 text-sm font-medium transition-all flex items-center gap-3 ${
                              isSelected
                                ? obs.isDanger
                                  ? 'bg-red-50 border-red-400 text-red-700'
                                  : 'bg-green-50 border-green-500 text-green-700'
                                : 'bg-white border-gray-200 text-gray-600'
                            }`}
                          >
                            <span className={`w-5 h-5 rounded-full border-2 flex items-center justify-center shrink-0 ${
                              isSelected
                                ? obs.isDanger
                                  ? 'bg-red-500 border-red-500'
                                  : 'bg-green-500 border-green-500'
                                : 'border-gray-300'
                            }`}>
                              {isSelected && (
                                <svg className="w-3 h-3 text-white" fill="none" stroke="currentColor" strokeWidth={3} viewBox="0 0 24 24">
                                  <path strokeLinecap="round" strokeLinejoin="round" d="M5 13l4 4L19 7" />
                                </svg>
                              )}
                            </span>
                            {t(`newbornCheckup.obs.${obs.key}`, obs.label)}
                          </button>
                        )
                      })}
                    </div>
                  </div>
                ))}
              </div>
            </div>

            {/* Inline danger sign warning */}
            {activeDangerSigns.length > 0 && (
              <div className="bg-red-50 border border-red-200 rounded-xl p-3">
                <p className="text-sm font-semibold text-red-700 mb-1">
                  {t('newbornCheckup.dangerWarning')}
                </p>
                <ul className="text-sm text-red-600 space-y-0.5">
                  {activeDangerSigns.map(s => (
                    <li key={s.key}>· {t(`newbornCheckup.obs.${s.key}`, s.label)}</li>
                  ))}
                </ul>
                <p className="text-xs text-red-500 mt-2">
                  {t('newbornCheckup.dangerNote')}
                </p>
              </div>
            )}

            {/* Free text */}
            <div>
              <label className="block text-sm font-semibold text-gray-700 mb-1">
                {t('newbornCheckup.otherObs')}
              </label>
              <textarea
                value={otherObs}
                onChange={e => setOtherObs(e.target.value)}
                placeholder={t('newbornCheckup.otherPlaceholder')}
                rows={3}
                className="w-full border-2 border-gray-200 rounded-xl px-4 py-3 text-base focus:outline-none focus:border-blue-500 resize-none"
              />
            </div>
          </div>
        )}

        {error && (
          <div className="mt-4 bg-red-50 border border-red-200 rounded-xl p-3">
            <p className="text-red-600 text-sm">{error}</p>
          </div>
        )}
      </div>

      {/* Sticky CTA */}
      <div className="fixed bottom-0 left-0 right-0 flex justify-center pointer-events-none">
        <div className="w-full max-w-[430px] px-4 pb-6 pt-4 bg-gradient-to-t from-white via-white to-transparent pointer-events-auto space-y-2">
          <button
            onClick={handleAskSakhi}
            className="w-full bg-white border-2 border-blue-600 text-blue-600 font-semibold text-base rounded-xl py-3 transition-colors hover:bg-blue-50"
          >
            {t('newbornCheckup.buttons.askSakhi')}
          </button>
          <button
            onClick={handleNext}
            disabled={loading}
            className="w-full bg-blue-600 hover:bg-blue-700 active:bg-blue-800 disabled:bg-blue-300 text-white font-semibold text-lg rounded-xl py-4 transition-colors shadow-md"
          >
            {loading
              ? t('common.loading')
              : step === 0
                ? t('newbornCheckup.buttons.nextObs')
                : t('newbornCheckup.buttons.getAssessment')}
          </button>
        </div>
      </div>
    </div>
  )
}
