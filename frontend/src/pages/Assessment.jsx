import React from 'react'
import { useParams, useNavigate, useLocation } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { useApp } from '../context/AppContext.jsx'
import TopBar from '../components/TopBar.jsx'
import RiskBadge from '../components/RiskBadge.jsx'
import Disclaimer from '../components/Disclaimer.jsx'
import { localName } from '../utils/nameUtils.js'

/**
 * Assessment.jsx — AI assessment results screen (Screen 5), shared by ANC and newborn.
 *
 * Reads lastAssessment from AppContext (set by CheckupForm / NewbornCheckupForm
 * after a successful API call). Guards against direct navigation by checking
 * that lastAssessment.patientId matches the URL param :id.
 *
 * Distinguishes ANC vs newborn by inspecting location.pathname — both
 * /patient/:id/assessment and /newborn/:id/assessment render this component.
 *
 * For high-risk (red) patients, surfaces a WhatsApp deep-link that pre-fills
 * a referral message so the ASHA can forward patient info to the PHC with
 * one tap.
 */

/**
 * Returns the Tailwind text colour class for a BP reading displayed on
 * the "Today's readings" card, using standard hypertension thresholds.
 */
function getBpColor(systolic, diastolic) {
  if (systolic >= 140 || diastolic >= 90) return 'text-red-600'
  if (systolic >= 120 || diastolic >= 80) return 'text-yellow-600'
  return 'text-gray-900'
}

const riskBanner = {
  green: 'bg-green-50 border-green-500',
  yellow: 'bg-yellow-50 border-yellow-400',
  red: 'bg-red-50 border-red-500',
}

const nextAction = {
  red:    { card: 'bg-red-50 border-red-500',     text: 'text-red-700 font-semibold' },
  yellow: { card: 'bg-yellow-50 border-yellow-400', text: 'text-yellow-700' },
  green:  { card: 'bg-green-50 border-green-500',  text: 'text-green-700' },
}

/**
 * Constructs a WhatsApp share URL with a pre-filled referral message.
 * Uses the wa.me universal link format which works on both mobile and desktop.
 * Only shown for red-level assessments to prompt urgent PHC referral.
 */
function buildWhatsAppUrl({ assessment, checkup, patient, ashaName, t, language }) {
  const lines = [
    t('assessment.whatsapp.header'),
    `${t('assessment.whatsapp.patient')}: ${localName(patient, language) || t('common.unknown')}`,
    t('assessment.whatsapp.riskLevel'),
  ]
  if (checkup.bp_systolic != null) {
    lines.push(`BP: ${checkup.bp_systolic}/${checkup.bp_diastolic} mmHg`)
  }
  if (checkup.weight_kg != null) {
    lines.push(`Weight: ${checkup.weight_kg} kg`)
  }
  lines.push('', `${t('assessment.whatsapp.noticed')} ${assessment.risk_reason}`)
  lines.push(`${t('assessment.whatsapp.action')} ${assessment.what_to_do_next}`)
  lines.push('', t('assessment.whatsapp.footer', { name: ashaName }))
  return `https://wa.me/?text=${encodeURIComponent(lines.join('\n'))}`
}

export default function Assessment() {
  const { id } = useParams()
  const navigate = useNavigate()
  const location = useLocation()
  const { t } = useTranslation()
  const { lastAssessment, setCheckupDraft, selectedPatient, ashaName, language } = useApp()

  const isNewborn = location.pathname.startsWith('/newborn/')
  const profilePath = isNewborn ? `/newborn/${id}` : `/patient/${id}`
  const checkupPath = isNewborn ? `/newborn/${id}/checkup` : `/patient/${id}/checkup`

  if (!lastAssessment || lastAssessment.patientId !== id) {
    return (
      <div className="flex flex-col min-h-screen bg-gray-50">
        <TopBar title={t('assessment.title')} backTo={null} />
        <div className="flex-1 flex flex-col items-center justify-center gap-4 px-6 text-center">
          <p className="text-gray-400 text-lg">{t('assessment.noAssessment')}</p>
          <p className="text-gray-400 text-sm">{t('assessment.completeCheckup')}</p>
          <button
            onClick={() => navigate(checkupPath)}
            className="bg-blue-600 text-white px-6 py-3 rounded-xl font-semibold"
          >
            {t('assessment.startCheckup')}
          </button>
        </div>
      </div>
    )
  }

  const { assessment, checkup } = lastAssessment
  const level = assessment.risk_level
  const actionStyle = nextAction[level]
  const hasAncReadings = checkup.bp_systolic != null

  return (
    <div className="flex flex-col min-h-screen bg-gray-50">
      <TopBar title={t('assessment.title')} backTo={null} />

      <div className="flex-1 overflow-y-auto pb-72 px-4 pt-4 space-y-4">

        {/* Checkup recorded confirmation */}
        <div className="flex items-center gap-2 bg-green-50 border border-green-200 rounded-xl px-4 py-2.5">
          <svg className="w-4 h-4 text-green-500 shrink-0" fill="none" stroke="currentColor" strokeWidth={2.5} viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" />
          </svg>
          <p className="text-sm text-green-700 font-medium">{t('assessment.checkupRecorded')}</p>
        </div>

        {/* Risk banner */}
        <div className={`rounded-2xl border-l-4 p-5 ${riskBanner[level]}`}>
          <div className="flex justify-center mb-2">
            <RiskBadge level={level} size="lg" />
          </div>
          <p className="text-center text-gray-700 text-sm leading-relaxed">
            {assessment.risk_reason}
          </p>
        </div>

        {/* What Sakhi noticed */}
        <div className="bg-white rounded-2xl p-4 shadow-sm">
          <h3 className="text-xs font-semibold text-gray-400 uppercase tracking-wide mb-3">
            {t('assessment.whatNoticed')}
          </h3>
          <ul className="space-y-3">
            {assessment.what_sakhi_noticed.map((point, i) => (
              <li key={i} className="flex gap-3">
                <span className="w-5 h-5 rounded-full bg-blue-100 text-blue-700 text-xs flex items-center justify-center font-bold shrink-0 mt-0.5">
                  {i + 1}
                </span>
                <p className="text-base text-gray-700 leading-snug">{point}</p>
              </li>
            ))}
          </ul>
        </div>

        {/* Tell the patient / mother */}
        <div className="bg-blue-50 rounded-2xl p-4 shadow-sm">
          <div className="flex items-center gap-2 mb-2">
            <svg className="w-4 h-4 text-blue-500 shrink-0" fill="none" stroke="currentColor" strokeWidth={2} viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z" />
            </svg>
            <h3 className="text-xs font-semibold text-gray-500 uppercase tracking-wide">
              {isNewborn ? t('assessment.tellMother') : t('assessment.tellPatient')}
            </h3>
          </div>
          <p className="text-base text-gray-700 italic leading-relaxed">
            "{assessment.what_to_tell_patient}"
          </p>
        </div>

        {/* Next action */}
        <div className={`rounded-2xl border-l-4 p-4 ${actionStyle.card}`}>
          <h3 className="text-xs font-semibold text-gray-400 uppercase tracking-wide mb-2">{t('assessment.nextAction')}</h3>
          <p className={`text-base ${actionStyle.text}`}>{assessment.what_to_do_next}</p>
          {assessment.follow_up_date && (
            <p className="mt-2 text-sm text-gray-500">
              {t('assessment.followUp')} <strong>{assessment.follow_up_date}</strong>
            </p>
          )}
        </div>

        {/* Today's readings */}
        <div className="bg-white rounded-2xl p-4 shadow-sm">
          <h3 className="text-xs font-semibold text-gray-400 uppercase tracking-wide mb-3">{t('assessment.todaysReadings')}</h3>
          {hasAncReadings ? (
            <div className="grid grid-cols-3 gap-2 text-center">
              <div>
                <p className={`text-2xl font-bold ${getBpColor(checkup.bp_systolic, checkup.bp_diastolic)}`}>
                  {checkup.bp_systolic}/{checkup.bp_diastolic}
                </p>
                <p className="text-xs text-gray-400">mmHg</p>
                <p className="text-xs text-gray-500">{t('assessment.bloodPressure')}</p>
              </div>
              <div>
                <p className="text-2xl font-bold text-gray-900">{checkup.weight_kg}</p>
                <p className="text-xs text-gray-400">kg</p>
                <p className="text-xs text-gray-500">{t('assessment.weight')}</p>
              </div>
              <div>
                <p className="text-2xl font-bold text-gray-900">{checkup.fundal_height_cm}</p>
                <p className="text-xs text-gray-400">cm</p>
                <p className="text-xs text-gray-500">{t('assessment.fundalHt')}</p>
              </div>
            </div>
          ) : (
            <div className="grid grid-cols-2 gap-2 text-center">
              <div>
                <p className="text-2xl font-bold text-gray-900">{checkup.weight_kg}</p>
                <p className="text-xs text-gray-400">kg</p>
                <p className="text-xs text-gray-500">{t('assessment.weight')}</p>
              </div>
              <div>
                <p className="text-2xl font-bold text-gray-900 capitalize">
                  {checkup.visit_day?.replace('_', ' ')}
                </p>
                <p className="text-xs text-gray-500">{t('assessment.visit')}</p>
              </div>
            </div>
          )}
        </div>

        <Disclaimer />
      </div>

      {/* Bottom actions */}
      <div className="fixed bottom-0 left-0 right-0 flex justify-center pointer-events-none">
        <div className="w-full max-w-[430px] px-4 pb-6 pt-4 bg-gradient-to-t from-white via-white to-transparent pointer-events-auto space-y-2">
          {level === 'red' && (
            <a
              href={buildWhatsAppUrl({ assessment, checkup, patient: selectedPatient, ashaName, t, language })}
              target="_blank"
              rel="noopener noreferrer"
              className="w-full flex items-center justify-center gap-2 bg-green-500 hover:bg-green-600 text-white font-semibold text-base rounded-xl py-3.5 transition-colors shadow-md"
            >
              <svg viewBox="0 0 24 24" fill="currentColor" className="w-5 h-5 shrink-0">
                <path d="M17.472 14.382c-.297-.149-1.758-.867-2.03-.967-.273-.099-.471-.148-.67.15-.197.297-.767.966-.94 1.164-.173.199-.347.223-.644.075-.297-.15-1.255-.463-2.39-1.475-.883-.788-1.48-1.761-1.653-2.059-.173-.297-.018-.458.13-.606.134-.133.298-.347.446-.52.149-.174.198-.298.298-.497.099-.198.05-.371-.025-.52-.075-.149-.669-1.612-.916-2.207-.242-.579-.487-.5-.669-.51-.173-.008-.371-.01-.57-.01-.198 0-.52.074-.792.372-.272.297-1.04 1.016-1.04 2.479 0 1.462 1.065 2.875 1.213 3.074.149.198 2.096 3.2 5.077 4.487.709.306 1.262.489 1.694.625.712.227 1.36.195 1.871.118.571-.085 1.758-.719 2.006-1.413.248-.694.248-1.289.173-1.413-.074-.124-.272-.198-.57-.347m-5.421 7.403h-.004a9.87 9.87 0 01-5.031-1.378l-.361-.214-3.741.982.998-3.648-.235-.374a9.86 9.86 0 01-1.51-5.26c.001-5.45 4.436-9.884 9.888-9.884 2.64 0 5.122 1.03 6.988 2.898a9.825 9.825 0 012.893 6.994c-.003 5.45-4.437 9.884-9.885 9.884m8.413-18.297A11.815 11.815 0 0012.05 0C5.495 0 .16 5.335.157 11.892c0 2.096.547 4.142 1.588 5.945L.057 24l6.305-1.654a11.882 11.882 0 005.683 1.448h.005c6.554 0 11.89-5.335 11.893-11.893a11.821 11.821 0 00-3.48-8.413z" />
              </svg>
              {t('assessment.referWhatsapp')}
            </a>
          )}
          <button
            onClick={() => navigate(`/ask?patient=${id}${isNewborn ? '&type=newborn' : ''}`)}
            className="w-full bg-white border-2 border-blue-600 text-blue-600 font-semibold text-base rounded-xl py-3 transition-colors hover:bg-blue-50"
          >
            {t('assessment.askSakhi')}
          </button>
          <button
            onClick={() => { setCheckupDraft(null); navigate('/home') }}
            className="w-full bg-blue-600 hover:bg-blue-700 text-white font-semibold text-lg rounded-xl py-4 transition-colors shadow-lg"
          >
            {t('assessment.backToPatients')}
          </button>
        </div>
      </div>
    </div>
  )
}
