/**
 * RiskBadge.jsx — Color-coded risk level indicator pill.
 *
 * Renders a small rounded badge with a colored dot and label for one of
 * three risk levels: green (Normal), yellow (Monitor), red (High Risk).
 *
 * Props:
 *  - level: 'green' | 'yellow' | 'red'
 *  - size:  'sm' (default, for cards) | 'lg' (for the Assessment banner)
 *
 * The config object maps each level to its Tailwind colour classes and
 * the i18n key for the label, keeping all styling decisions in one place.
 */
import React from 'react'
import { useTranslation } from 'react-i18next'

const config = {
  green:  { bg: 'bg-green-100',  text: 'text-green-700',  border: 'border-green-200',  dot: 'bg-green-500',  key: 'common.risk.normal' },
  yellow: { bg: 'bg-yellow-100', text: 'text-yellow-700', border: 'border-yellow-200', dot: 'bg-yellow-400', key: 'common.risk.monitor' },
  red:    { bg: 'bg-red-100',    text: 'text-red-700',    border: 'border-red-200',    dot: 'bg-red-500',    key: 'common.risk.high' },
}

export default function RiskBadge({ level, size = 'sm' }) {
  const { t } = useTranslation()
  const c = config[level] || config.green
  const textSize = size === 'lg' ? 'text-sm font-semibold px-4 py-1.5' : 'text-xs font-semibold px-3 py-1'

  return (
    <span className={`inline-flex items-center gap-1.5 rounded-full border ${c.bg} ${c.text} ${c.border} ${textSize}`}>
      <span className={`w-2 h-2 rounded-full ${c.dot}`} />
      {t(c.key)}
    </span>
  )
}
