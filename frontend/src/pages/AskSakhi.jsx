import React, { useState, useRef, useEffect } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { useApp } from '../context/AppContext.jsx'
import TopBar from '../components/TopBar.jsx'
import Disclaimer from '../components/Disclaimer.jsx'
import { fetchChat } from '../api/api.js'
import { localName } from '../utils/nameUtils.js'
import ReactMarkdown from 'react-markdown'

/**
 * AskSakhi.jsx — Free-form AI chat screen (Screen 6)
 *
 * Can be opened standalone (from the bottom nav) or with patient context
 * (from PatientProfile, NewbornProfile, or mid-checkup via "Ask Sakhi" button).
 * Context is passed as URL search params: ?patient=<id>&type=anc|newborn&from=checkup
 *
 * Patient context is intentionally kept lean (buildEnrichedContext strips
 * checkup history) to avoid inflating token usage. If there's a recent
 * assessment or in-progress draft for this patient, that is included instead,
 * as it's the most clinically relevant information.
 *
 * Conversation history is capped at the last 10 messages sent to the API to
 * keep token count bounded across long sessions.
 *
 * Voice features use the Web Speech API (speech recognition + speech synthesis)
 * which is browser-native with no additional dependencies. The recogniser
 * language is set to hi-IN when Hindi is active, en-IN otherwise.
 *
 * Back navigation is context-aware:
 *  - from=checkup  → return to the checkup form (draft is preserved)
 *  - from=profile  → return to the assessment screen
 *  - standalone    → return to /home
 */
function Message({ msg, isSpeaking, onSpeak, t }) {
  const isUser = msg.role === 'user'
  return (
    <div className={`flex ${isUser ? 'justify-end' : 'justify-start'}`}>
      {!isUser && (
        <div className="w-8 h-8 rounded-full bg-blue-600 flex items-center justify-center mr-2 shrink-0 mt-1">
          <svg className="w-4 h-4 text-white" fill="currentColor" viewBox="0 0 24 24">
            <path d="M4.318 6.318a4.5 4.5 0 000 6.364L12 20.364l7.682-7.682a4.5 4.5 0 00-6.364-6.364L12 7.636l-1.318-1.318a4.5 4.5 0 00-6.364 0z" />
          </svg>
        </div>
      )}
      <div
        className={`max-w-[78%] px-4 py-3 rounded-2xl text-base ${
          isUser
            ? 'bg-blue-600 text-white rounded-tr-sm'
            : 'bg-white border border-gray-100 text-gray-800 rounded-tl-sm shadow-sm'
        }`}
      >
        {isUser ? (
          <p className="whitespace-pre-wrap leading-relaxed">{msg.content}</p>
        ) : (
          <ReactMarkdown
            components={{
              p: ({ children }) => <p className="leading-relaxed mb-2 last:mb-0">{children}</p>,
              strong: ({ children }) => <strong className="font-semibold text-gray-900">{children}</strong>,
              ul: ({ children }) => <ul className="list-disc list-outside ml-4 mb-2 space-y-1">{children}</ul>,
              ol: ({ children }) => <ol className="list-decimal list-outside ml-4 mb-2 space-y-1">{children}</ol>,
              li: ({ children }) => <li className="leading-relaxed">{children}</li>,
              h1: ({ children }) => <h1 className="text-base font-bold text-gray-900 mb-1">{children}</h1>,
              h2: ({ children }) => <h2 className="text-base font-bold text-gray-900 mb-1">{children}</h2>,
              h3: ({ children }) => <h3 className="text-sm font-semibold text-gray-900 mb-1">{children}</h3>,
            }}
          >
            {msg.content}
          </ReactMarkdown>
        )}
        {!isUser && (
          <div className="flex justify-end mt-2">
            <button
              onClick={onSpeak}
              className={`flex items-center gap-1 text-xs px-2 py-1 rounded-full transition-colors ${
                isSpeaking
                  ? 'bg-blue-100 text-blue-600'
                  : 'text-gray-400 hover:text-blue-500 hover:bg-gray-100'
              }`}
              aria-label={isSpeaking ? t('askSakhi.stop') : t('askSakhi.listen')}
            >
              {isSpeaking ? (
                <>
                  <svg className="w-3.5 h-3.5" fill="currentColor" viewBox="0 0 24 24">
                    <rect x="6" y="6" width="4" height="12" rx="1" />
                    <rect x="14" y="6" width="4" height="12" rx="1" />
                  </svg>
                  {t('askSakhi.stop')}
                </>
              ) : (
                <>
                  <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" strokeWidth={2} viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" d="M15.536 8.464a5 5 0 010 7.072M12 6v12m0 0l-3-3m3 3l3-3M9 9H5a1 1 0 00-1 1v4a1 1 0 001 1h4l5 5V4L9 9z" />
                  </svg>
                  {t('askSakhi.listen')}
                </>
              )}
            </button>
          </div>
        )}
      </div>
    </div>
  )
}

export default function AskSakhi() {
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const { t } = useTranslation()
  const { patients, newborns, lastAssessment, checkupDraft, language } = useApp()

  const QUICK_QUESTIONS = [
    t('askSakhi.q1'),
    t('askSakhi.q2'),
    t('askSakhi.q3'),
    t('askSakhi.q4'),
  ]

  const patientId = searchParams.get('patient')
  const patientType = searchParams.get('type') || 'anc'
  const from = searchParams.get('from')

  const patientContext = patientId
    ? (patientType === 'newborn'
        ? newborns.find(n => n.id === patientId)
        : patients.find(p => p.id === patientId)) || null
    : null

  // Build a lean context — only the fields the backend actually uses.
  // Deliberately excludes checkup_history / visit_history to keep token count low.
  function buildEnrichedContext() {
    if (!patientContext) return null

    const base = {
      name: patientContext.name,
      age: patientContext.age,
      risk_level: patientContext.risk_level,
      patient_type: patientContext.patient_type,
      gestational_weeks: patientContext.gestational_weeks,
      gravida: patientContext.gravida,
      para: patientContext.para,
      mother_name: patientContext.mother_name,
      date_of_birth: patientContext.date_of_birth,
      birth_weight_kg: patientContext.birth_weight_kg,
    }

    if (lastAssessment?.patientId === patientId) {
      return {
        ...base,
        current_checkup: lastAssessment.checkup,
        assessment_summary: {
          risk_level: lastAssessment.assessment.risk_level,
          risk_reason: lastAssessment.assessment.risk_reason,
          what_to_do_next: lastAssessment.assessment.what_to_do_next,
        },
      }
    }
    if (checkupDraft?.patientId === patientId) {
      const v = checkupDraft.vitals
      return {
        ...base,
        current_checkup: {
          ...v,
          symptoms: v.symptoms
            ? [...v.symptoms, ...(v.other_symptom ? [v.other_symptom] : [])]
            : undefined,
        },
      }
    }
    return base
  }

  const enrichedContext = buildEnrichedContext()

  const [messages, setMessages] = useState([])
  const [input, setInput] = useState('')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [recording, setRecording] = useState(false)
  const [transcribing, setTranscribing] = useState(false)
  const [speakingIndex, setSpeakingIndex] = useState(null)
  const bottomRef = useRef(null)
  const textareaRef = useRef(null)
  const recognitionRef = useRef(null)

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages, loading])

  async function send(text) {
    const trimmed = text.trim()
    if (!trimmed || loading) return

    const newMessages = [...messages, { role: 'user', content: trimmed }]
    setMessages(newMessages)
    setInput('')
    setLoading(true)
    setError('')

    try {
      // Send only the last 10 messages to keep token count bounded.
      const { reply } = await fetchChat(newMessages.slice(-10), enrichedContext, language)
      setMessages(prev => [...prev, { role: 'assistant', content: reply }])
    } catch (e) {
      setError(`${t('askSakhi.couldNotReach')} ${e.message}`)
    } finally {
      setLoading(false)
    }
  }

  function handleKeyDown(e) {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault()
      send(input)
    }
  }

  function startRecording() {
    setError('')
    const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition
    if (!SpeechRecognition) {
      setError(t('askSakhi.micError'))
      return
    }

    const recognition = new SpeechRecognition()
    recognition.lang = language === 'hi' ? 'hi-IN' : 'en-IN'
    recognition.interimResults = false
    recognition.maxAlternatives = 1
    recognition.continuous = false

    recognition.onresult = e => {
      const text = e.results[0][0].transcript
      if (text) {
        setInput(prev => (prev ? prev + ' ' + text : text))
        setTimeout(() => {
          if (textareaRef.current) {
            textareaRef.current.style.height = 'auto'
            textareaRef.current.style.height =
              Math.min(textareaRef.current.scrollHeight, 120) + 'px'
            textareaRef.current.focus()
          }
        }, 0)
      }
    }

    recognition.onerror = () => {
      setError(t('askSakhi.transcribeError'))
    }

    recognition.onend = () => {
      setRecording(false)
      setTranscribing(false)
    }

    recognitionRef.current = recognition
    recognition.start()
    setRecording(true)
  }

  function stopRecording() {
    recognitionRef.current?.stop()
    setRecording(false)
    setTranscribing(true)
  }

  function toggleRecording() {
    if (recording) {
      stopRecording()
    } else {
      startRecording()
    }
  }

  /**
   * Reads a Sakhi response aloud using the Web Speech API.
   * Calling again while already speaking the same message cancels it (toggle behaviour).
   * Voices are selected to prefer Indian English or Hindi depending on active language.
   */
  function speak(text, index) {
    if (!window.speechSynthesis) return

    if (speakingIndex === index) {
      window.speechSynthesis.cancel()
      setSpeakingIndex(null)
      return
    }

    window.speechSynthesis.cancel()

    const utterance = new SpeechSynthesisUtterance(text)
    utterance.rate = 0.95
    utterance.pitch = 1.05

    // Use hi-IN voice when language is Hindi; fall back to Indian English otherwise
    const voices = window.speechSynthesis.getVoices()
    const voice = language === 'hi'
      ? (voices.find(v => v.lang === 'hi-IN') ||
         voices.find(v => v.lang.startsWith('hi')) ||
         voices.find(v => v.lang === 'en-IN') ||
         null)
      : (voices.find(v => v.lang === 'en-IN') ||
         voices.find(v => v.lang.startsWith('en-') && v.name.toLowerCase().includes('female')) ||
         voices.find(v => v.lang.startsWith('en')) ||
         null)
    if (voice) utterance.voice = voice

    utterance.onstart = () => setSpeakingIndex(index)
    utterance.onend = () => setSpeakingIndex(null)
    utterance.onerror = () => setSpeakingIndex(null)

    window.speechSynthesis.speak(utterance)
  }

  const backTo = from === 'checkup'
    ? (patientType === 'newborn' ? `/newborn/${patientId}/checkup` : `/patient/${patientId}/checkup`)
    : patientId
      ? (patientType === 'newborn' ? `/newborn/${patientId}/assessment` : `/patient/${patientId}/assessment`)
      : '/home'

  return (
    <div className="flex flex-col bg-gray-50" style={{ height: 'calc(100vh - 64px)' }}>
      <TopBar
        title={t('askSakhi.title')}
        backTo={backTo}
        action={
          patientContext && (
            <span className="text-xs bg-blue-100 text-blue-700 px-2.5 py-1 rounded-full font-medium truncate max-w-[120px]">
              {localName(patientContext, language)}
            </span>
          )
        }
      />

      {/* Checkup-in-progress banner */}
      {from === 'checkup' && (
        <button
          onClick={() => navigate(backTo)}
          className="sticky top-0 z-10 w-full flex items-center justify-center gap-2 bg-amber-50 border-b border-amber-200 px-4 py-2.5 text-sm font-medium text-amber-800 active:bg-amber-100 transition-colors"
        >
          <svg className="w-4 h-4 shrink-0" fill="none" stroke="currentColor" strokeWidth={2} viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2" />
          </svg>
          {t('askSakhi.checkupInProgress')}
          <svg className="w-4 h-4 shrink-0" fill="none" stroke="currentColor" strokeWidth={2} viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" d="M9 5l7 7-7 7" />
          </svg>
        </button>
      )}

      {/* Messages area */}
      <div className="flex-1 min-h-0 overflow-y-auto px-4 pt-4 pb-4 space-y-3">
        {messages.length === 0 && (
          <div className="pt-6">
            <div className="text-center mb-6">
              <div className="w-16 h-16 rounded-full bg-blue-600 flex items-center justify-center mx-auto mb-3">
                <svg className="w-8 h-8 text-white" fill="currentColor" viewBox="0 0 24 24">
                  <path d="M4.318 6.318a4.5 4.5 0 000 6.364L12 20.364l7.682-7.682a4.5 4.5 0 00-6.364-6.364L12 7.636l-1.318-1.318a4.5 4.5 0 00-6.364 0z" />
                </svg>
              </div>
              <h2 className="text-xl font-bold text-gray-800">{t('askSakhi.emptyHeading')}</h2>
              <p className="text-gray-500 text-sm mt-1">
                {t('askSakhi.emptySubtitle')}
                {patientContext && <> {t('askSakhi.advisingOn')} <strong>{localName(patientContext, language)}</strong>.</>}
                {enrichedContext?.current_checkup && (
                  <> {t('askSakhi.readingsContext')}</>
                )}
              </p>
            </div>

            {/* Quick questions — only shown without patient context */}
            {!patientContext && (
              <div className="space-y-2">
                <p className="text-xs text-gray-400 font-medium uppercase tracking-wide">{t('askSakhi.commonQuestions')}</p>
                {QUICK_QUESTIONS.map(q => (
                  <button
                    key={q}
                    onClick={() => send(q)}
                    className="w-full text-left bg-white border border-gray-100 rounded-xl px-4 py-3 text-base text-gray-700 hover:border-blue-300 hover:bg-blue-50 transition-colors"
                  >
                    {q}
                  </button>
                ))}
              </div>
            )}
          </div>
        )}

        {messages.map((msg, i) => (
          <Message
            key={i}
            msg={msg}
            isSpeaking={speakingIndex === i}
            onSpeak={() => speak(msg.content, i)}
            t={t}
          />
        ))}

        {loading && (
          <div className="flex justify-start">
            <div className="w-8 h-8 rounded-full bg-blue-600 flex items-center justify-center mr-2 shrink-0">
              <svg className="w-4 h-4 text-white" fill="currentColor" viewBox="0 0 24 24">
                <path d="M4.318 6.318a4.5 4.5 0 000 6.364L12 20.364l7.682-7.682a4.5 4.5 0 00-6.364-6.364L12 7.636l-1.318-1.318a4.5 4.5 0 00-6.364 0z" />
              </svg>
            </div>
            <div className="bg-white border border-gray-100 rounded-2xl rounded-tl-sm px-4 py-3 shadow-sm">
              <div className="flex gap-1.5 items-center">
                <span className="w-2 h-2 bg-blue-400 rounded-full animate-bounce" style={{ animationDelay: '0ms' }} />
                <span className="w-2 h-2 bg-blue-400 rounded-full animate-bounce" style={{ animationDelay: '150ms' }} />
                <span className="w-2 h-2 bg-blue-400 rounded-full animate-bounce" style={{ animationDelay: '300ms' }} />
                <span className="text-sm text-gray-400 ml-1">{t('askSakhi.thinking')}</span>
              </div>
            </div>
          </div>
        )}

        {error && (
          <div className="bg-red-50 border border-red-200 rounded-xl p-3">
            <p className="text-red-600 text-sm">{error}</p>
          </div>
        )}

        <div ref={bottomRef} />
      </div>

      <Disclaimer />

      {/* Input area */}
      <div className="bg-white border-t border-gray-100 px-4 py-3">
        {recording && (
          <div className="flex items-center gap-2 mb-2 px-1">
            <span className="w-2 h-2 rounded-full bg-red-500 animate-pulse" />
            <span className="text-sm text-red-500 font-medium">{t('askSakhi.listeningHint')}</span>
          </div>
        )}
        {transcribing && (
          <div className="flex items-center gap-2 mb-2 px-1">
            <span className="w-2 h-2 rounded-full bg-blue-400 animate-pulse" />
            <span className="text-sm text-blue-500 font-medium">{t('askSakhi.transcribing')}</span>
          </div>
        )}
        <div className="flex gap-2 items-end">
          {/* Mic button */}
          <button
            onClick={toggleRecording}
            disabled={transcribing || loading}
            className={`w-12 h-12 rounded-xl flex items-center justify-center shrink-0 transition-all ${
              recording
                ? 'bg-red-500 text-white shadow-lg scale-110'
                : 'bg-gray-100 text-gray-500 hover:bg-gray-200 disabled:opacity-40'
            }`}
            aria-label={recording ? t('askSakhi.stop') : t('askSakhi.listen')}
          >
            {recording ? (
              <svg className="w-5 h-5" fill="currentColor" viewBox="0 0 24 24">
                <rect x="6" y="6" width="12" height="12" rx="2" />
              </svg>
            ) : (
              <svg className="w-5 h-5" fill="none" stroke="currentColor" strokeWidth={2} viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" d="M12 1a3 3 0 00-3 3v8a3 3 0 006 0V4a3 3 0 00-3-3z" />
                <path strokeLinecap="round" strokeLinejoin="round" d="M19 10v2a7 7 0 01-14 0v-2M12 19v4M8 23h8" />
              </svg>
            )}
          </button>

          <textarea
            ref={textareaRef}
            value={input}
            onChange={e => setInput(e.target.value)}
            onKeyDown={handleKeyDown}
            placeholder={recording ? t('askSakhi.listening') : t('askSakhi.placeholder')}
            disabled={recording}
            rows={1}
            className="flex-1 border-2 border-gray-200 rounded-xl px-4 py-3 text-base focus:outline-none focus:border-blue-500 resize-none leading-relaxed disabled:bg-gray-50 disabled:text-gray-400"
            style={{ minHeight: '52px', maxHeight: '120px' }}
            onInput={e => {
              e.target.style.height = 'auto'
              e.target.style.height = Math.min(e.target.scrollHeight, 120) + 'px'
            }}
          />
          <button
            onClick={() => send(input)}
            disabled={!input.trim() || loading || recording}
            className="w-12 h-12 bg-blue-600 hover:bg-blue-700 disabled:bg-blue-200 text-white rounded-xl flex items-center justify-center transition-colors shrink-0"
            aria-label="Send"
          >
            <svg className="w-5 h-5" fill="none" stroke="currentColor" strokeWidth={2} viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" d="M6 12L3.269 3.126A59.768 59.768 0 0121.485 12 59.77 59.77 0 013.27 20.876L5.999 12zm0 0h7.5" />
            </svg>
          </button>
        </div>
      </div>
    </div>
  )
}
