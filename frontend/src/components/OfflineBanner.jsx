import React from 'react'
import { useApp } from '../context/AppContext.jsx'
import { useOnlineStatus } from '../hooks/useOnlineStatus.js'

/**
 * OfflineBanner — sticky strip shown when the device has no connectivity,
 * or when online and pending queue items are being synced.
 *
 * Hidden entirely when online with nothing to sync.
 */
export default function OfflineBanner() {
  const isOnline = useOnlineStatus()
  const { pendingCount } = useApp()

  if (isOnline && pendingCount === 0) return null

  const pending = `${pendingCount} assessment${pendingCount !== 1 ? 's' : ''}`

  return (
    <div
      role="status"
      className={`flex items-center gap-2 px-4 py-2 text-sm font-medium ${
        isOnline
          ? 'bg-blue-50 text-blue-700'
          : 'bg-amber-50 text-amber-700 border-b border-amber-100'
      }`}
    >
      {isOnline ? (
        /* Syncing spinner */
        <svg className="w-4 h-4 shrink-0 animate-spin" fill="none" viewBox="0 0 24 24">
          <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
          <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v4a4 4 0 00-4 4H4z" />
        </svg>
      ) : (
        /* Offline dot */
        <span className="w-2 h-2 rounded-full bg-amber-500 shrink-0" />
      )}

      <span>
        {isOnline
          ? `Syncing ${pending} with Sakhi AI…`
          : pendingCount > 0
            ? `Offline — ${pending} will sync when connected`
            : 'Offline — assessments use local rules until connected'}
      </span>
    </div>
  )
}
