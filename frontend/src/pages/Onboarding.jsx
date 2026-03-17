import React, { useState } from 'react'
import { useNavigate, Navigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { useApp } from '../context/AppContext.jsx'

/**
 * Onboarding.jsx — First-run screen (Screen 0)
 *
 * Collects the ASHA worker's ID and name before entering the app.
 * Three demo workers are pre-seeded with known IDs — entering a known ID
 * auto-fills the name and loads that worker's patient list.
 * Unknown IDs are also accepted and get a fresh (generic) patient set.
 *
 * Language selection here sets the i18next language AND persists it so
 * it is still active on next visit.
 */

// Pre-seeded demo workers. Entering a known ID auto-fills the name.
const DEMO_WORKERS = {
  'ASH1001': 'Sunita Devi',
  'ASH2047': 'Rekha Kumari',
  'ASH3112': 'Meera Singh',
}

export default function Onboarding() {
  const { ashaName, login, language, setLanguage } = useApp()
  const { t } = useTranslation()
  const navigate = useNavigate()
  const [id, setId]     = useState('')
  const [name, setName] = useState('')
  const [errors, setErrors] = useState({})

  // Already logged in
  if (ashaName) return <Navigate to="/home" replace />

  function handleIdChange(val) {
    const upper = val.toUpperCase().replace(/[^A-Z0-9]/g, '')
    setId(upper)
    setErrors(e => ({ ...e, id: '' }))
    // Auto-fill name if it's a known demo worker
    if (DEMO_WORKERS[upper]) {
      setName(DEMO_WORKERS[upper])
      setErrors(e => ({ ...e, name: '' }))
    }
  }

  function handleStart() {
    const trimmedId   = id.trim()
    const trimmedName = name.trim()
    const newErrors   = {}
    if (!trimmedId)   newErrors.id   = t('onboarding.idError')
    if (!trimmedName) newErrors.name = t('onboarding.nameError')
    if (Object.keys(newErrors).length) {
      setErrors(newErrors)
      return
    }
    login(trimmedName, trimmedId)
    navigate('/home', { replace: true })
  }

  function handleKeyDown(e) {
    if (e.key === 'Enter') handleStart()
  }

  const isDemoWorker = !!DEMO_WORKERS[id]

  return (
    <div className="flex flex-col min-h-screen px-6 pt-16 pb-10">
      {/* Logo / branding */}
      <div className="flex flex-col items-center mb-10">
        <div className="w-20 h-20 rounded-full bg-blue-600 flex items-center justify-center mb-4 shadow-lg">
          <svg className="w-10 h-10 text-white" fill="none" stroke="currentColor" strokeWidth={1.5} viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round"
              d="M4.318 6.318a4.5 4.5 0 000 6.364L12 20.364l7.682-7.682a4.5 4.5 0 00-6.364-6.364L12 7.636l-1.318-1.318a4.5 4.5 0 00-6.364 0z" />
          </svg>
        </div>
        <h1 className="text-4xl font-bold text-blue-600 tracking-tight">{t('onboarding.appName')}</h1>
        <p className="mt-2 text-gray-500 text-base text-center">{t('onboarding.tagline')}</p>
      </div>

      {/* Language toggle */}
      <div className="mb-6">
        <p className="text-xs font-semibold text-gray-400 uppercase tracking-wide mb-2">
          {t('onboarding.langLabel')}
        </p>
        <div className="flex gap-2">
          {[{ code: 'en', label: 'English' }, { code: 'hi', label: 'हिंदी' }].map(opt => (
            <button
              key={opt.code}
              onClick={() => setLanguage(opt.code)}
              className={`flex-1 py-2.5 rounded-xl border-2 font-semibold text-base transition-all ${
                language === opt.code
                  ? 'bg-blue-600 border-blue-600 text-white'
                  : 'bg-white border-gray-200 text-gray-600 hover:border-blue-300'
              }`}
            >
              {opt.label}
            </button>
          ))}
        </div>
      </div>

      {/* ASHA ID input */}
      <div className="mb-4">
        <label className="block text-base font-semibold text-gray-800 mb-1.5">
          {t('onboarding.idLabel')}
        </label>
        <input
          type="text"
          value={id}
          onChange={e => handleIdChange(e.target.value)}
          onKeyDown={handleKeyDown}
          placeholder={t('onboarding.idPlaceholder')}
          className="w-full border-2 border-gray-200 rounded-xl px-4 py-3 text-lg font-mono tracking-wider focus:outline-none focus:border-blue-500 transition-colors uppercase"
          autoFocus
          autoCapitalize="characters"
        />
        {errors.id && <p className="mt-1.5 text-red-500 text-sm">{errors.id}</p>}
        {isDemoWorker && (
          <p className="mt-1.5 text-blue-600 text-sm flex items-center gap-1">
            <svg className="w-4 h-4" fill="none" stroke="currentColor" strokeWidth={2} viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" d="M5 13l4 4L19 7" />
            </svg>
            {t('onboarding.demoWorkerFound')}
          </p>
        )}
      </div>

      {/* Name input */}
      <div className="flex-1">
        <label className="block text-base font-semibold text-gray-800 mb-1.5">
          {t('onboarding.nameLabel')}
        </label>
        <input
          type="text"
          value={name}
          onChange={e => { setName(e.target.value); setErrors(err => ({ ...err, name: '' })) }}
          onKeyDown={handleKeyDown}
          placeholder={t('onboarding.namePlaceholder')}
          className="w-full border-2 border-gray-200 rounded-xl px-4 py-3 text-lg focus:outline-none focus:border-blue-500 transition-colors"
        />
        {errors.name && <p className="mt-1.5 text-red-500 text-sm">{errors.name}</p>}
      </div>

      {/* CTA */}
      <button
        onClick={handleStart}
        className="w-full bg-blue-600 hover:bg-blue-700 active:bg-blue-800 text-white font-semibold text-lg rounded-xl py-4 transition-colors shadow-sm mt-6"
      >
        {t('onboarding.startButton')}
      </button>

      <p className="text-center text-xs text-gray-400 mt-4">{t('onboarding.footer')}</p>
    </div>
  )
}
