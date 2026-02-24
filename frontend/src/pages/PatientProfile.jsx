import React from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { useApp } from '../context/AppContext.jsx'
import TopBar from '../components/TopBar.jsx'
import RiskBadge from '../components/RiskBadge.jsx'
import { localName } from '../utils/nameUtils.js'

/**
 * PatientProfile.jsx — ANC patient detail screen (Screen 2)
 *
 * Shows the patient's current risk status, latest vitals, and full
 * checkup history. The hero card background colour reflects risk level
 * to make the patient's status immediately obvious at a glance.
 *
 * The sticky "Start Checkup" CTA navigates to CheckupForm and also sets
 * selectedPatient in AppContext so the form can access patient data.
 */

/**
 * Returns Tailwind colour classes for the BP reading card based on
 * standard hypertension thresholds used in Indian ANC protocols:
 *  ≥140/90  → high (red)   — must refer to PHC
 *  ≥120/80  → elevated (yellow) — monitor
 *  < 120/80 → normal (gray)
 */
function getBpStyle(systolic, diastolic) {
  if (systolic >= 140 || diastolic >= 90) return { text: 'text-red-600', bg: 'bg-red-50' }
  if (systolic >= 120 || diastolic >= 80) return { text: 'text-yellow-600', bg: 'bg-white' }
  return { text: 'text-gray-900', bg: 'bg-white' }
}

/**
 * Renders a single vital statistic as a centered card:
 * large number on top, unit below, label at the bottom.
 */
function VitalItem({ label, value, unit, valueClass = 'text-gray-900', cardBg = 'bg-white' }) {
  return (
    <div className={`${cardBg} rounded-xl p-3 text-center shadow-sm`}>
      <p className={`text-2xl font-bold ${valueClass}`}>{value}</p>
      {unit && <p className="text-xs text-gray-400">{unit}</p>}
      <p className="text-xs text-gray-500 mt-0.5">{label}</p>
    </div>
  )
}

export default function PatientProfile() {
  const { id } = useParams()
  const navigate = useNavigate()
  const { t } = useTranslation()
  const { patients, setSelectedPatient, language } = useApp()

  const patient = patients.find(p => p.id === id)

  if (!patient) {
    return (
      <div className="flex flex-col min-h-screen bg-gray-50">
        <TopBar title={t('patient.notFound')} backTo={null} />
        <div className="flex-1 flex items-center justify-center text-gray-400">
          {t('patient.notFound')}
        </div>
      </div>
    )
  }

  const lastCheckup = patient.checkup_history.at(-1)
  const bpStyle = lastCheckup
    ? getBpStyle(lastCheckup.bp_systolic, lastCheckup.bp_diastolic)
    : { text: 'text-gray-900', bg: 'bg-white' }

  function startCheckup() {
    setSelectedPatient(patient)
    navigate(`/patient/${patient.id}/checkup`)
  }

  const heroBg = patient.risk_level === 'red' ? 'bg-red-50'
    : patient.risk_level === 'yellow' ? 'bg-yellow-50'
    : 'bg-green-50'

  return (
    <div className="flex flex-col min-h-screen bg-gray-50">
      <TopBar title={localName(patient, language)} backTo={null} />

      <div className="flex-1 overflow-y-auto pb-28">

        {/* Hero patient card */}
        <div className="px-4 pt-4">
          <div className={`rounded-2xl p-5 shadow-lg ${heroBg}`}>
            <div className="flex items-start justify-between">
              <div>
                <h2 className="text-xl font-bold text-gray-900">{localName(patient, language)}</h2>
                <p className="text-gray-600 mt-0.5">
                  {patient.age} yrs · {patient.village}
                </p>
                <p className="text-gray-500 text-sm mt-1">
                  G{patient.gravida}P{patient.para} · LMP: {patient.lmp}
                </p>
              </div>
              <RiskBadge level={patient.risk_level} size="sm" />
            </div>
            <div className="mt-3 pt-3 border-t border-white/60">
              <p className="text-3xl font-bold text-gray-900">
                {patient.gestational_weeks} <span className="text-lg font-normal text-gray-500">{t('patient.weeksPregnant')}</span>
              </p>
              <p className="text-xs text-gray-400 uppercase tracking-wide mt-0.5">{t('patient.gestationalAge')}</p>
            </div>
          </div>
        </div>

        {/* Latest vitals */}
        {lastCheckup && (
          <div className="px-4 mt-6">
            <h3 className="text-xs font-semibold text-gray-400 uppercase tracking-wide mb-3">{t('patient.latestVitals')}</h3>
            <div className="grid grid-cols-2 gap-2">
              <VitalItem
                label={t('assessment.bloodPressure')}
                value={`${lastCheckup.bp_systolic}/${lastCheckup.bp_diastolic}`}
                unit="mmHg"
                valueClass={bpStyle.text}
                cardBg={bpStyle.bg}
              />
              <VitalItem label={t('assessment.weight')} value={lastCheckup.weight_kg} unit="kg" />
              <VitalItem label={t('patient.fundalHeight')} value={lastCheckup.fundal_height_cm} unit="cm" />
              {lastCheckup.fetal_heart_rate && (
                <VitalItem label={t('patient.fetalHR')} value={lastCheckup.fetal_heart_rate} unit="bpm" />
              )}
              {lastCheckup.hemoglobin && (
                <VitalItem label={t('patient.haemoglobin')} value={lastCheckup.hemoglobin} unit="g/dL" />
              )}
            </div>
            {lastCheckup.symptoms?.length > 0 && (
              <div className="mt-3 bg-yellow-50 rounded-xl p-3 shadow-sm">
                <p className="text-sm font-medium text-yellow-800 mb-1">{t('patient.symptomsReported')}</p>
                <div className="flex flex-wrap gap-1.5">
                  {lastCheckup.symptoms.map(s => (
                    <span key={s} className="bg-yellow-100 text-yellow-700 text-xs px-2.5 py-1 rounded-full capitalize">
                      {s}
                    </span>
                  ))}
                </div>
              </div>
            )}
            <p className="text-xs text-gray-400 mt-2 text-right">{t('patient.recorded')} {lastCheckup.date}</p>
          </div>
        )}

        {/* Checkup history */}
        <div className="px-4 mt-6">
          <h3 className="text-xs font-semibold text-gray-400 uppercase tracking-wide mb-3">{t('patient.checkupHistory')}</h3>
          <div className="space-y-3">
            {[...patient.checkup_history].reverse().map((c, i) => (
              <div key={i} className="rounded-2xl p-4 bg-white shadow-sm">
                <div className="flex items-center justify-between mb-2">
                  <p className="text-sm font-semibold text-gray-700">{c.date}</p>
                  <RiskBadge level={c.risk_level} size="sm" />
                </div>
                <p className="text-sm text-gray-600">
                  BP: <strong>{c.bp_systolic}/{c.bp_diastolic}</strong> ·{' '}
                  Wt: <strong>{c.weight_kg}kg</strong> ·{' '}
                  FH: <strong>{c.fundal_height_cm}cm</strong>
                </p>
                {c.notes && (
                  <p className="mt-1.5 text-sm text-gray-500 italic">{c.notes}</p>
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
            {t('patient.startCheckup')}
          </button>
        </div>
      </div>
    </div>
  )
}
