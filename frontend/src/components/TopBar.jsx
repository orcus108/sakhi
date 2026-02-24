import React from 'react'
import { useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'

/**
 * TopBar.jsx — Sticky screen header with optional back button and action slot.
 *
 * Props:
 *  - title:  String displayed as the page heading.
 *  - backTo: Controls the back button behaviour:
 *              undefined → no back button rendered
 *              null      → back button shown, navigates to browser history (-1)
 *              string    → back button shown, navigates to the given path
 *  - action: Optional JSX rendered in the right slot (e.g., patient context pill
 *            on Ask Sakhi, or a settings icon).
 */
export default function TopBar({ title, backTo, action }) {
  const navigate = useNavigate()
  const { t } = useTranslation()

  return (
    <header className="sticky top-0 z-10 bg-white border-b border-gray-100 px-4 py-3 flex items-center gap-3">
      {backTo !== undefined && (
        <button
          onClick={() => backTo ? navigate(backTo) : navigate(-1)}
          className="p-1 -ml-1 text-gray-500 hover:text-gray-900"
          aria-label={t('common.goBack')}
        >
          <svg className="w-6 h-6" fill="none" stroke="currentColor" strokeWidth={2} viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" d="M15 19l-7-7 7-7" />
          </svg>
        </button>
      )}
      <h1 className="flex-1 text-lg font-semibold text-gray-900 truncate">{title}</h1>
      {action && action}
    </header>
  )
}
