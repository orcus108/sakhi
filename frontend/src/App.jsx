import React from 'react'
import { Routes, Route, Navigate, useLocation } from 'react-router-dom'
import { useApp } from './context/AppContext.jsx'

import Onboarding from './pages/Onboarding.jsx'
import Home from './pages/Home.jsx'
import PatientProfile from './pages/PatientProfile.jsx'
import CheckupForm from './pages/CheckupForm.jsx'
import Assessment from './pages/Assessment.jsx'
import AskSakhi from './pages/AskSakhi.jsx'
import NewbornProfile from './pages/NewbornProfile.jsx'
import NewbornCheckupForm from './pages/NewbornCheckupForm.jsx'
import Schedule from './pages/Schedule.jsx'
import NewCheckupPicker from './pages/NewCheckupPicker.jsx'
import BottomNav, { BOTTOM_NAV_PATHS } from './components/BottomNav.jsx'
import OfflineBanner from './components/OfflineBanner.jsx'

/**
 * Route guard: redirects unauthenticated users (no ashaName) to the
 * onboarding screen. ashaName is the only "session" identifier — there are
 * no passwords or tokens in V1.
 */
function RequireAuth({ children }) {
  const { ashaName } = useApp()
  if (!ashaName) return <Navigate to="/" replace />
  return children
}

/**
 * App.jsx — Root component and route tree
 *
 * Defines all client-side routes. ANC and newborn flows share the same
 * Assessment component (rendered at both /patient/:id/assessment and
 * /newborn/:id/assessment) — the component distinguishes which type it's
 * rendering by inspecting location.pathname.
 *
 * BottomNav is only rendered on the four tab-root paths (BOTTOM_NAV_PATHS).
 * All other screens (profiles, forms, assessment) hide the nav bar so it
 * doesn't obscure the sticky CTA buttons at the bottom of those pages.
 *
 * The outer div centres the max-430px app shell on wider screens.
 */
export default function App() {
  const { pathname } = useLocation()
  const showNav = BOTTOM_NAV_PATHS.includes(pathname)

  return (
    <div className="min-h-screen bg-gray-50 flex justify-center">
      <div className="w-full max-w-[430px] min-h-screen bg-white shadow-sm flex flex-col">
        <OfflineBanner />
        <Routes>
          {/* Onboarding */}
          <Route path="/" element={<Onboarding />} />

          {/* Tab roots */}
          <Route path="/home"        element={<RequireAuth><Home /></RequireAuth>} />
          <Route path="/ask"         element={<RequireAuth><AskSakhi /></RequireAuth>} />
          <Route path="/schedule"    element={<RequireAuth><Schedule /></RequireAuth>} />
          <Route path="/new-checkup" element={<RequireAuth><NewCheckupPicker /></RequireAuth>} />

          {/* ANC */}
          <Route path="/patient/:id"            element={<RequireAuth><PatientProfile /></RequireAuth>} />
          <Route path="/patient/:id/checkup"    element={<RequireAuth><CheckupForm /></RequireAuth>} />
          <Route path="/patient/:id/assessment" element={<RequireAuth><Assessment /></RequireAuth>} />

          {/* Newborn */}
          <Route path="/newborn/:id"            element={<RequireAuth><NewbornProfile /></RequireAuth>} />
          <Route path="/newborn/:id/checkup"    element={<RequireAuth><NewbornCheckupForm /></RequireAuth>} />
          <Route path="/newborn/:id/assessment" element={<RequireAuth><Assessment /></RequireAuth>} />

          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
        {showNav && <BottomNav />}
      </div>
    </div>
  )
}
