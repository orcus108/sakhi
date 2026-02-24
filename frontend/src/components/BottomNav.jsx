import React from 'react'
import { useNavigate, useLocation } from 'react-router-dom'
import { useTranslation } from 'react-i18next'

/**
 * BottomNav.jsx — Persistent tab bar shown on the four main tab screens.
 *
 * BOTTOM_NAV_PATHS is exported so App.jsx can conditionally render this
 * component — it is hidden on profile, form, and assessment screens to
 * avoid competing with those pages' sticky CTA buttons.
 *
 * Each icon component accepts an `active` prop and toggles between a
 * filled (active) and outline (inactive) style using SVG fill/stroke.
 */
export const BOTTOM_NAV_PATHS = ['/home', '/new-checkup', '/ask', '/schedule']

function PatientsIcon({ active }) {
  return (
    <svg className="w-6 h-6" fill={active ? 'currentColor' : 'none'} stroke="currentColor" strokeWidth={1.5} viewBox="0 0 24 24">
      <path strokeLinecap="round" strokeLinejoin="round" d="M15 19.128a9.38 9.38 0 002.625.372 9.337 9.337 0 004.121-.952 4.125 4.125 0 00-7.533-2.493M15 19.128v-.003c0-1.113-.285-2.16-.786-3.07M15 19.128v.106A12.318 12.318 0 018.624 21c-2.331 0-4.512-.645-6.374-1.766l-.001-.109a6.375 6.375 0 0111.964-3.07M12 6.375a3.375 3.375 0 11-6.75 0 3.375 3.375 0 016.75 0zm8.25 2.25a2.625 2.625 0 11-5.25 0 2.625 2.625 0 015.25 0z" />
    </svg>
  )
}

function CheckupIcon({ active }) {
  return (
    <svg className="w-6 h-6" fill={active ? 'currentColor' : 'none'} stroke="currentColor" strokeWidth={1.5} viewBox="0 0 24 24">
      <path strokeLinecap="round" strokeLinejoin="round" d="M12 9v6m3-3H9m12 0a9 9 0 11-18 0 9 9 0 0118 0z" />
    </svg>
  )
}

function AskIcon({ active }) {
  return (
    <svg className="w-6 h-6" fill={active ? 'currentColor' : 'none'} stroke="currentColor" strokeWidth={1.5} viewBox="0 0 24 24">
      <path strokeLinecap="round" strokeLinejoin="round" d="M9.813 15.904L9 18.75l-.813-2.846a4.5 4.5 0 00-3.09-3.09L2.25 12l2.846-.813a4.5 4.5 0 003.09-3.09L9 5.25l.813 2.846a4.5 4.5 0 003.09 3.09L15.75 12l-2.846.813a4.5 4.5 0 00-3.09 3.09z" />
    </svg>
  )
}

function CalendarIcon({ active }) {
  return (
    <svg className="w-6 h-6" fill={active ? 'currentColor' : 'none'} stroke="currentColor" strokeWidth={1.5} viewBox="0 0 24 24">
      <path strokeLinecap="round" strokeLinejoin="round" d="M6.75 3v2.25M17.25 3v2.25M3 18.75V7.5a2.25 2.25 0 012.25-2.25h13.5A2.25 2.25 0 0121 7.5v11.25m-18 0A2.25 2.25 0 005.25 21h13.5A2.25 2.25 0 0021 18.75m-18 0v-7.5A2.25 2.25 0 015.25 9h13.5A2.25 2.25 0 0121 11.25v7.5" />
    </svg>
  )
}

export default function BottomNav() {
  const navigate = useNavigate()
  const { pathname } = useLocation()
  const { t } = useTranslation()

  const TABS = [
    { key: 'patients', label: t('nav.patients'),  path: '/home',        Icon: PatientsIcon },
    { key: 'checkup',  label: t('nav.newCheckup'), path: '/new-checkup', Icon: CheckupIcon },
    { key: 'ask',      label: t('nav.askSakhi'),   path: '/ask',         Icon: AskIcon },
    { key: 'schedule', label: t('nav.schedule'),   path: '/schedule',    Icon: CalendarIcon },
  ]

  return (
    <nav className="fixed bottom-0 left-1/2 -translate-x-1/2 w-full max-w-[430px] z-20 bg-white border-t border-gray-100 shadow-[0_-4px_6px_rgba(0,0,0,0.05)]">
      <div className="flex">
        {TABS.map(({ key, label, path, Icon }) => {
          const active = pathname === path
          return (
            <button
              key={key}
              onClick={() => navigate(path)}
              className={`flex-1 flex flex-col items-center gap-0.5 py-2.5 transition-colors ${
                active ? 'text-blue-600' : 'text-gray-400 hover:text-gray-600'
              }`}
            >
              <Icon active={active} />
              <span className="text-[10px] font-medium leading-none">{label}</span>
            </button>
          )
        })}
      </div>
    </nav>
  )
}
