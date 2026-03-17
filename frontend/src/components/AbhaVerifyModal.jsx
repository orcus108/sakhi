import React, { useState, useRef, useEffect } from 'react'
import { fetchAbhaRequestOtp, fetchAbhaVerifyOtp } from '../api/api.js'

/**
 * AbhaVerifyModal — 3-state identity verification flow:
 *
 *  "idle"        — ABHA number input + "Send OTP" button
 *  "otp_sent"    — OTP input + "Verify" button
 *  "confirmed"   — success screen with name/DOB + mismatch warning if needed
 *
 * Props:
 *   isOpen       {boolean}           — controls visibility
 *   onClose      {() => void}        — called when the modal is dismissed
 *   onVerified   {(profile) => void} — called with ABDM profile on success
 *   initialAbha  {string}            — pre-fills the ABHA field if stored
 *   patientName  {string}            — stored patient name for mismatch detection
 */
export default function AbhaVerifyModal({
  isOpen,
  onClose,
  onVerified,
  initialAbha = '',
  patientName = '',
}) {
  const [step, setStep]       = useState('idle')   // 'idle' | 'otp_sent' | 'confirmed'
  const [abhaNumber, setAbhaNumber] = useState(initialAbha)
  const [otp, setOtp]         = useState('')
  const [txnId, setTxnId]     = useState('')
  const [profile, setProfile] = useState(null)
  const [isDemo, setIsDemo]   = useState(false)
  const [loading, setLoading] = useState(false)
  const [error, setError]     = useState('')

  const abhaInputRef = useRef(null)
  const otpInputRef  = useRef(null)

  // Lock body scroll while modal is open
  useEffect(() => {
    if (isOpen) {
      document.body.style.overflow = 'hidden'
    } else {
      document.body.style.overflow = ''
    }
    return () => { document.body.style.overflow = '' }
  }, [isOpen])

  // Reset state each time the modal opens
  useEffect(() => {
    if (isOpen) {
      setStep('idle')
      setAbhaNumber(initialAbha)
      setOtp('')
      setTxnId('')
      setProfile(null)
      setIsDemo(false)
      setError('')
      setTimeout(() => abhaInputRef.current?.focus(), 100)
    }
  }, [isOpen, initialAbha])

  // Focus OTP input when step advances
  useEffect(() => {
    if (step === 'otp_sent') {
      setTimeout(() => otpInputRef.current?.focus(), 100)
    }
  }, [step])

  if (!isOpen) return null

  // Returns true if the ABDM name plausibly matches the stored patient name.
  // Uses a loose inclusion check to handle capitalisation / middle-name differences.
  function nameMatches(stored, fromAbdm) {
    if (!stored || !fromAbdm) return true
    const a = stored.toLowerCase().trim()
    const b = fromAbdm.toLowerCase().replace(/\s*\(demo\)\s*/i, '').trim()
    return a === b || a.includes(b) || b.includes(a)
  }

  // ── Handlers ────────────────────────────────────────────────────────────────

  async function handleRequestOtp(e) {
    e.preventDefault()
    const cleaned = abhaNumber.trim()
    if (!cleaned) {
      setError('Please enter an ABHA number.')
      return
    }
    setError('')
    setLoading(true)
    try {
      const res = await fetchAbhaRequestOtp(cleaned)
      setTxnId(res.txn_id)
      setIsDemo(!!res._demo)
      setStep('otp_sent')
    } catch (err) {
      setError(err.message || 'Could not send OTP. Please try again.')
    } finally {
      setLoading(false)
    }
  }

  async function handleVerifyOtp(e) {
    e.preventDefault()
    const cleaned = otp.trim()
    if (cleaned.length !== 6) {
      setError('Please enter the full 6-digit OTP.')
      return
    }
    setError('')
    setLoading(true)
    try {
      const result = await fetchAbhaVerifyOtp(txnId, cleaned)
      setProfile(result)
      setStep('confirmed')
      onVerified?.(result)
    } catch (err) {
      setError(err.message || 'OTP verification failed. Please try again.')
    } finally {
      setLoading(false)
    }
  }

  function handleResend() {
    setStep('idle')
    setOtp('')
    setError('')
  }

  const mismatch = profile && !isDemo && !nameMatches(patientName, profile.name)

  // ── Render ──────────────────────────────────────────────────────────────────

  return (
    <div
      className="fixed inset-0 z-50 flex items-end justify-center"
      onClick={e => { if (e.target === e.currentTarget) onClose() }}
    >
      <div className="absolute inset-0 bg-black/40" />

      {/* Sheet */}
      <div className="relative w-full max-w-[430px] bg-white rounded-t-3xl px-5 pt-5 pb-8 shadow-2xl">

        {/* Handle bar */}
        <div className="w-10 h-1 bg-gray-200 rounded-full mx-auto mb-5" />

        {/* Header */}
        <div className="flex items-center justify-between mb-5">
          <div className="flex items-center gap-2">
            <div className="w-8 h-8 rounded-full bg-blue-100 flex items-center justify-center">
              <svg className="w-4 h-4 text-blue-600" fill="none" stroke="currentColor" strokeWidth={2} viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round"
                  d="M9 12.75L11.25 15 15 9.75m-3-7.036A11.959 11.959 0 013.598 6 11.99 11.99 0 003 9.749c0 5.592 3.824 10.29 9 11.623 5.176-1.332 9-6.03 9-11.622 0-1.31-.21-2.571-.598-3.751h-.152c-3.196 0-6.1-1.248-8.25-3.285z" />
              </svg>
            </div>
            <div>
              <h2 className="text-base font-bold text-gray-900">Verify ABHA</h2>
              {isDemo && step !== 'idle' && (
                <p className="text-xs text-yellow-600 font-medium">Demo mode — any 6-digit OTP works</p>
              )}
            </div>
          </div>
          <button
            onClick={onClose}
            className="w-8 h-8 flex items-center justify-center rounded-full text-gray-400 hover:text-gray-600 active:bg-gray-100"
            aria-label="Close"
          >
            <svg className="w-5 h-5" fill="none" stroke="currentColor" strokeWidth={2} viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        </div>

        {/* ── Step 1: Enter ABHA number ── */}
        {step === 'idle' && (
          <form onSubmit={handleRequestOtp}>
            <p className="text-sm text-gray-500 mb-4">
              Ask the patient for their ABHA ID. An OTP will be sent to their Aadhaar-linked mobile number.
            </p>
            <label className="block text-xs font-semibold text-gray-500 uppercase tracking-wide mb-1.5">
              ABHA Number
            </label>
            <input
              ref={abhaInputRef}
              type="text"
              value={abhaNumber}
              onChange={e => setAbhaNumber(e.target.value)}
              placeholder="91-0000-0000-0001"
              className="w-full h-14 px-4 rounded-xl border border-gray-200 text-lg font-mono focus:outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100 transition"
            />
            <p className="text-xs text-gray-400 mt-1.5">Format: 91-XXXX-XXXX-XXXX</p>

            {error && (
              <p className="mt-3 text-sm text-red-600 bg-red-50 rounded-xl px-4 py-2">{error}</p>
            )}

            <button
              type="submit"
              disabled={loading}
              className="mt-5 w-full bg-blue-600 hover:bg-blue-700 active:bg-blue-800 disabled:opacity-50 text-white font-semibold text-base rounded-xl py-4 transition-colors shadow-lg"
            >
              {loading ? (
                <span className="flex items-center justify-center gap-2">
                  <span className="w-4 h-4 border-2 border-white/40 border-t-white rounded-full animate-spin" />
                  Sending OTP…
                </span>
              ) : 'Send OTP'}
            </button>
          </form>
        )}

        {/* ── Step 2: Enter OTP ── */}
        {step === 'otp_sent' && (
          <form onSubmit={handleVerifyOtp}>
            <div className="bg-blue-50 rounded-xl px-4 py-3 mb-4 flex items-center gap-2">
              <svg className="w-4 h-4 text-blue-500 shrink-0" fill="none" stroke="currentColor" strokeWidth={2} viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" d="M10.5 1.5H8.25A2.25 2.25 0 006 3.75v16.5a2.25 2.25 0 002.25 2.25h7.5A2.25 2.25 0 0018 20.25V3.75a2.25 2.25 0 00-2.25-2.25H13.5m-3 0V3h3V1.5m-3 0h3m-3 8.25h3m-3 3.75h3" />
              </svg>
              <p className="text-sm text-blue-700">
                OTP sent to the phone linked with{' '}
                <span className="font-mono font-semibold">{abhaNumber}</span>
              </p>
            </div>

            <label className="block text-xs font-semibold text-gray-500 uppercase tracking-wide mb-1.5">
              Enter OTP
            </label>
            <input
              ref={otpInputRef}
              type="tel"
              inputMode="numeric"
              maxLength={6}
              value={otp}
              onChange={e => setOtp(e.target.value.replace(/\D/g, ''))}
              placeholder="••••••"
              className="w-full h-14 px-4 rounded-xl border border-gray-200 text-2xl font-mono text-center tracking-widest focus:outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100 transition"
            />

            {error && (
              <p className="mt-3 text-sm text-red-600 bg-red-50 rounded-xl px-4 py-2">{error}</p>
            )}

            <button
              type="submit"
              disabled={loading}
              className="mt-5 w-full bg-blue-600 hover:bg-blue-700 active:bg-blue-800 disabled:opacity-50 text-white font-semibold text-base rounded-xl py-4 transition-colors shadow-lg"
            >
              {loading ? (
                <span className="flex items-center justify-center gap-2">
                  <span className="w-4 h-4 border-2 border-white/40 border-t-white rounded-full animate-spin" />
                  Verifying…
                </span>
              ) : 'Verify'}
            </button>

            <button
              type="button"
              onClick={handleResend}
              className="mt-3 w-full text-sm text-blue-600 py-2 active:opacity-70"
            >
              Resend OTP
            </button>
          </form>
        )}

        {/* ── Step 3: Confirmed ── */}
        {step === 'confirmed' && profile && (
          <div>
            {/* Mismatch warning — shown before the success banner so it's seen first */}
            {mismatch && (
              <div className="bg-yellow-50 border border-yellow-200 rounded-2xl px-4 py-3 mb-4 flex gap-3">
                <svg className="w-5 h-5 text-yellow-600 shrink-0 mt-0.5" fill="none" stroke="currentColor" strokeWidth={2} viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" d="M12 9v3.75m-9.303 3.376c-.866 1.5.217 3.374 1.948 3.374h14.71c1.73 0 2.813-1.874 1.948-3.374L13.949 3.378c-.866-1.5-3.032-1.5-3.898 0L2.697 16.126zM12 15.75h.007v.008H12v-.008z" />
                </svg>
                <div>
                  <p className="text-sm font-semibold text-yellow-800">Name mismatch</p>
                  <p className="text-xs text-yellow-700 mt-0.5">
                    Record shows <span className="font-semibold">{patientName}</span> but ABDM returned{' '}
                    <span className="font-semibold">{profile.name}</span>. Confirm you have the right patient before proceeding.
                  </p>
                </div>
              </div>
            )}

            {/* Success banner */}
            <div className="flex flex-col items-center py-3">
              <div className="w-16 h-16 rounded-full bg-green-100 flex items-center justify-center mb-3">
                {profile.photo ? (
                  <img
                    src={`data:image/jpeg;base64,${profile.photo}`}
                    alt="ABHA photo"
                    className="w-16 h-16 rounded-full object-cover"
                  />
                ) : (
                  <svg className="w-8 h-8 text-green-600" fill="none" stroke="currentColor" strokeWidth={2} viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" d="M9 12.75L11.25 15 15 9.75M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
                  </svg>
                )}
              </div>
              <p className="text-xs font-semibold text-green-700 uppercase tracking-wide">
                Identity Confirmed
              </p>
            </div>

            {/* Profile details */}
            <div className="bg-green-50 rounded-2xl px-4 py-4 space-y-2 mb-5">
              {profile.name && (
                <div className="flex justify-between items-center">
                  <span className="text-xs text-gray-400 uppercase tracking-wide">Name</span>
                  <span className={`text-sm font-semibold ${mismatch ? 'text-yellow-700' : 'text-gray-900'}`}>
                    {profile.name}
                  </span>
                </div>
              )}
              {profile.dob && (
                <div className="flex justify-between items-center">
                  <span className="text-xs text-gray-400 uppercase tracking-wide">Date of Birth</span>
                  <span className="text-sm font-semibold text-gray-900">{profile.dob}</span>
                </div>
              )}
              {profile.gender && (
                <div className="flex justify-between items-center">
                  <span className="text-xs text-gray-400 uppercase tracking-wide">Gender</span>
                  <span className="text-sm font-semibold text-gray-900 capitalize">{profile.gender}</span>
                </div>
              )}
              {profile.abha_number && (
                <div className="flex justify-between items-center">
                  <span className="text-xs text-gray-400 uppercase tracking-wide">ABHA Number</span>
                  <span className="text-xs font-mono text-gray-600">{profile.abha_number}</span>
                </div>
              )}
            </div>

            <button
              onClick={onClose}
              className="w-full bg-blue-600 hover:bg-blue-700 active:bg-blue-800 text-white font-semibold text-base rounded-xl py-4 transition-colors shadow-lg"
            >
              Done
            </button>
          </div>
        )}
      </div>
    </div>
  )
}
