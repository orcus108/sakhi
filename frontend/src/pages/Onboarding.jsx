import React, { useState } from 'react'
import { useNavigate, Navigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { useApp } from '../context/AppContext.jsx'

/**
 * Onboarding.jsx — First-run screen (Screen 0)
 *
 * Collects the ASHA worker's name and preferred language before entering
 * the app. There is no password — any name is accepted.
 *
 * If an ashaName is already stored in localStorage (i.e. the user has
 * been here before), the component redirects straight to /home.
 *
 * Language selection here sets the i18next language AND persists it so
 * it is still active on next visit.
 */
export default function Onboarding() {
  const { ashaName, setAshaName, language, setLanguage } = useApp()
  const { t } = useTranslation()
  const navigate = useNavigate()
  const [name, setName] = useState('')
  const [error, setError] = useState('')

  // Already logged in
  if (ashaName) return <Navigate to="/home" replace />

  function handleStart() {
    const trimmed = name.trim()
    if (!trimmed) {
      setError(t('onboarding.nameError'))
      return
    }
    setAshaName(trimmed)
    navigate('/home', { replace: true })
  }

  function handleKeyDown(e) {
    if (e.key === 'Enter') handleStart()
  }

  return (
    <div className="flex flex-col min-h-screen px-6 pt-16 pb-10">
      {/* Logo / branding */}
      <div className="flex flex-col items-center mb-12">
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

      {/* Name input */}
      <div className="flex-1">
        <label className="block text-lg font-semibold text-gray-800 mb-2">
          {t('onboarding.nameLabel')}
        </label>
        <input
          type="text"
          value={name}
          onChange={e => { setName(e.target.value); setError('') }}
          onKeyDown={handleKeyDown}
          placeholder={t('onboarding.namePlaceholder')}
          className="w-full border-2 border-gray-200 rounded-xl px-4 py-3 text-lg focus:outline-none focus:border-blue-500 transition-colors"
          autoFocus
        />
        {error && <p className="mt-2 text-red-500 text-sm">{error}</p>}
      </div>

      {/* CTA */}
      <button
        onClick={handleStart}
        className="w-full bg-blue-600 hover:bg-blue-700 active:bg-blue-800 text-white font-semibold text-lg rounded-xl py-4 transition-colors shadow-sm"
      >
        {t('onboarding.startButton')}
      </button>

    </div>
  )
}
