import { useState, useEffect } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { useApp } from '../context/AppContext.jsx'
import TopBar from '../components/TopBar.jsx'
import { fetchCheckupAssessment } from '../api/api.js'

/**
 * CheckupForm.jsx — ANC vitals + symptoms data-entry form (Screen 4)
 *
 * Two-step form:
 *  Step 0 (Vitals)   – BP, weight, fundal height, optional FHR + haemoglobin
 *  Step 1 (Symptoms) – Multi-select tile grid + free-text "other"
 *
 * On final submit, calls the /api/checkup-assessment endpoint, saves the
 * result to AppContext (and localStorage), then navigates to Assessment.
 *
 * Draft persistence: if the ASHA taps "Ask Sakhi" mid-form, the current
 * form state is saved to checkupDraft in AppContext. When they return,
 * the useEffect on mount restores that state so no data is lost.
 */

// API values MUST stay in English — only display labels translate
const SYMPTOMS_OPTIONS = [
  'headache',
  'blurred vision',
  'swelling in feet',
  'swelling in hands or face',
  'upper abdominal pain',
  'reduced fetal movement',
  'difficulty breathing',
  'nausea or vomiting',
  'dizziness',
  'fatigue',
  'fever',
  'vaginal bleeding',
]

/** Generic labelled number input used for weight, fundal height, FHR, haemoglobin. */
function FieldInput({ label, hint, value, onChange, type = 'number', min, step }) {
  return (
    <div>
      <label className="block text-base font-semibold text-gray-800 mb-1">{label}</label>
      {hint && <p className="text-sm text-gray-400 mb-2">{hint}</p>}
      <input
        type={type}
        value={value}
        onChange={e => onChange(e.target.value)}
        min={min}
        step={step}
        className="w-full border-2 border-gray-200 rounded-xl px-4 py-3 text-xl font-semibold focus:outline-none focus:border-blue-500 transition-colors"
        inputMode="decimal"
      />
    </div>
  )
}

export default function CheckupForm() {
  const { id } = useParams()
  const navigate = useNavigate()
  const { t } = useTranslation()
  const { patients, setLastAssessment, checkupDraft, setCheckupDraft, saveCheckup, language } = useApp()

  const patient = patients.find(p => p.id === id)

  const STEP_LABELS = [t('checkupForm.steps.vitals'), t('checkupForm.steps.symptoms')]

  const [step, setStep] = useState(0)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')

  // Step 1: vitals
  const [bpSystolic, setBpSystolic] = useState('')
  const [bpDiastolic, setBpDiastolic] = useState('')
  const [weight, setWeight] = useState('')
  const [fundalHeight, setFundalHeight] = useState('')
  const [fhr, setFhr] = useState('')
  const [hemoglobin, setHemoglobin] = useState('')

  // Step 2: symptoms
  const [selectedSymptoms, setSelectedSymptoms] = useState([])
  const [otherSymptom, setOtherSymptom] = useState('')

  // Restore form state if returning from Ask Sakhi mid-checkup
  useEffect(() => {
    if (checkupDraft?.patientId === id && checkupDraft?.patientType === 'anc') {
      const v = checkupDraft.vitals
      if (v.bp_systolic) setBpSystolic(String(v.bp_systolic))
      if (v.bp_diastolic) setBpDiastolic(String(v.bp_diastolic))
      if (v.weight_kg) setWeight(String(v.weight_kg))
      if (v.fundal_height_cm) setFundalHeight(String(v.fundal_height_cm))
      if (v.fetal_heart_rate) setFhr(String(v.fetal_heart_rate))
      if (v.hemoglobin) setHemoglobin(String(v.hemoglobin))
      if (v.symptoms) setSelectedSymptoms(v.symptoms.filter(s => SYMPTOMS_OPTIONS.includes(s)))
      if (v.other_symptom) setOtherSymptom(v.other_symptom)
      if (checkupDraft.step != null) setStep(checkupDraft.step)
    }
  }, []) // eslint-disable-line react-hooks/exhaustive-deps

  if (!patient) {
    return (
      <div className="flex flex-col min-h-screen">
        <TopBar title={t('checkupForm.title')} backTo={null} />
        <div className="flex-1 flex items-center justify-center text-gray-400">{t('checkupForm.notFound')}</div>
      </div>
    )
  }

  function toggleSymptom(s) {
    setSelectedSymptoms(prev =>
      prev.includes(s) ? prev.filter(x => x !== s) : [...prev, s]
    )
  }

  function validateStep0() {
    if (!bpSystolic || !bpDiastolic) return t('checkupForm.validationBP')
    if (!weight) return t('checkupForm.validationWeight')
    if (!fundalHeight) return t('checkupForm.validationFundal')
    return ''
  }

  function handleAskSakhi() {
    setCheckupDraft({
      patientId: id,
      patientType: 'anc',
      step,
      vitals: {
        bp_systolic: bpSystolic ? Number(bpSystolic) : null,
        bp_diastolic: bpDiastolic ? Number(bpDiastolic) : null,
        weight_kg: weight ? Number(weight) : null,
        fundal_height_cm: fundalHeight ? Number(fundalHeight) : null,
        fetal_heart_rate: fhr ? Number(fhr) : null,
        hemoglobin: hemoglobin ? Number(hemoglobin) : null,
        symptoms: selectedSymptoms,
        other_symptom: otherSymptom.trim(),
      },
    })
    navigate(`/ask?patient=${id}&from=checkup`)
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

    const allSymptoms = [...selectedSymptoms]
    if (otherSymptom.trim()) allSymptoms.push(otherSymptom.trim())

    const checkup = {
      date: new Date().toISOString().split('T')[0],
      bp_systolic: Number(bpSystolic),
      bp_diastolic: Number(bpDiastolic),
      weight_kg: Number(weight),
      fundal_height_cm: Number(fundalHeight),
      fetal_heart_rate: fhr ? Number(fhr) : null,
      hemoglobin: hemoglobin ? Number(hemoglobin) : null,
      symptoms: allSymptoms,
    }

    try {
      const result = await fetchCheckupAssessment(patient, checkup, 'anc', language)
      saveCheckup(id, 'anc', checkup, result)
      setLastAssessment({ assessment: result, checkup, patientId: id })
      navigate(`/patient/${id}/assessment`, { replace: true })
    } catch (e) {
      setError(`Could not get assessment: ${e.message}`)
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="flex flex-col min-h-screen">
      <TopBar
        title={`${t('checkupForm.title')} — ${patient.name}`}
        backTo={step === 0 ? null : undefined}
      />

      {/* Step indicator */}
      <div className="px-4 pt-3 pb-1">
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
        {step === 0 && (
          <div className="space-y-5">
            <p className="text-sm text-gray-500">
              {patient.name} • {patient.gestational_weeks} {t('patient.weeksPregnant')} • G{patient.gravida}P{patient.para}
            </p>

            {/* BP */}
            <div>
              <label className="block text-base font-semibold text-gray-800 mb-2">
                {t('checkupForm.bp.label')} <span className="text-red-500">*</span>
              </label>
              <div className="flex gap-3 items-center">
                <div className="flex-1">
                  <p className="text-xs text-gray-400 mb-1">{t('checkupForm.bp.systolic')}</p>
                  <input
                    type="number"
                    value={bpSystolic}
                    onChange={e => setBpSystolic(e.target.value)}
                    placeholder={t('checkupForm.bp.placeholderSystolic')}
                    min="50"
                    className="w-full border-2 border-gray-200 rounded-xl px-4 py-3 text-xl font-semibold focus:outline-none focus:border-blue-500"
                    inputMode="numeric"
                  />
                </div>
                <span className="text-2xl text-gray-300 font-light mt-4">/</span>
                <div className="flex-1">
                  <p className="text-xs text-gray-400 mb-1">{t('checkupForm.bp.diastolic')}</p>
                  <input
                    type="number"
                    value={bpDiastolic}
                    onChange={e => setBpDiastolic(e.target.value)}
                    placeholder={t('checkupForm.bp.placeholderDiastolic')}
                    min="30"
                    className="w-full border-2 border-gray-200 rounded-xl px-4 py-3 text-xl font-semibold focus:outline-none focus:border-blue-500"
                    inputMode="numeric"
                  />
                </div>
              </div>
              {(bpSystolic >= 140 || bpDiastolic >= 90) && bpSystolic && bpDiastolic && (
                <p className="mt-2 text-red-500 text-sm font-medium">
                  {t('checkupForm.bp.elevated')}
                </p>
              )}
            </div>

            <FieldInput
              label={t('checkupForm.weight.label')}
              hint={t('checkupForm.weight.hint')}
              value={weight}
              onChange={setWeight}
              min="30"
              step="0.1"
            />
            <FieldInput
              label={t('checkupForm.fundalHeight.label')}
              hint={t('checkupForm.fundalHeight.hint')}
              value={fundalHeight}
              onChange={setFundalHeight}
              min="10"
            />
            <FieldInput
              label={t('checkupForm.fetalHR.label')}
              hint={t('checkupForm.fetalHR.hint')}
              value={fhr}
              onChange={setFhr}
              min="60"
            />
            <FieldInput
              label={t('checkupForm.haemoglobin.label')}
              hint={t('checkupForm.haemoglobin.hint')}
              value={hemoglobin}
              onChange={setHemoglobin}
              min="4"
              step="0.1"
            />
          </div>
        )}

        {step === 1 && (
          <div>
            <p className="text-base font-semibold text-gray-800 mb-1">
              {t('checkupForm.symptomsQuestion')}
            </p>
            <p className="text-sm text-gray-500 mb-4">{t('checkupForm.symptomsHint')}</p>
            <div className="grid grid-cols-2 gap-2">
              {SYMPTOMS_OPTIONS.map(s => {
                const selected = selectedSymptoms.includes(s)
                const displayLabel = t(`checkupForm.symptoms.${s.replace(/ /g, '_')}`, s)
                return (
                  <button
                    key={s}
                    onClick={() => toggleSymptom(s)}
                    className={`text-left px-3 py-2.5 rounded-xl border-2 text-sm font-medium transition-all ${
                      selected
                        ? 'bg-blue-50 border-blue-500 text-blue-700'
                        : 'bg-white border-gray-200 text-gray-600'
                    }`}
                  >
                    {selected && <span className="mr-1">✓</span>}
                    {displayLabel}
                  </button>
                )
              })}
            </div>
            <div className="mt-4">
              <label className="block text-sm font-medium text-gray-600 mb-1">
                {t('checkupForm.otherSymptom')}
              </label>
              <input
                type="text"
                value={otherSymptom}
                onChange={e => setOtherSymptom(e.target.value)}
                placeholder={t('checkupForm.otherPlaceholder')}
                className="w-full border-2 border-gray-200 rounded-xl px-4 py-3 text-base focus:outline-none focus:border-blue-500"
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

      {/* Sticky bottom button */}
      <div className="fixed bottom-0 left-0 right-0 flex justify-center pointer-events-none">
        <div className="w-full max-w-[430px] px-4 pb-6 pt-4 bg-gradient-to-t from-white via-white to-transparent pointer-events-auto space-y-2">
          <button
            onClick={handleAskSakhi}
            className="w-full bg-white border-2 border-blue-600 text-blue-600 font-semibold text-base rounded-xl py-3 transition-colors hover:bg-blue-50"
          >
            {t('checkupForm.buttons.askSakhi')}
          </button>
          <button
            onClick={handleNext}
            disabled={loading}
            className="w-full bg-blue-600 hover:bg-blue-700 active:bg-blue-800 disabled:bg-blue-300 text-white font-semibold text-lg rounded-xl py-4 transition-colors shadow-md"
          >
            {loading
              ? t('common.loading')
              : step === 0
                ? t('checkupForm.buttons.nextSymptoms')
                : t('checkupForm.buttons.getAssessment')}
          </button>
        </div>
      </div>
    </div>
  )
}
