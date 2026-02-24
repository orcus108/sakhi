/**
 * Disclaimer.jsx — Safety footer rendered on Assessment and Ask Sakhi screens.
 *
 * Reminds ASHA workers that Sakhi supports but does not replace their
 * clinical judgment. Required on any screen showing AI-generated output.
 * The text is internationalised via the 'common.disclaimer' translation key.
 */
import React from 'react'
import { useTranslation } from 'react-i18next'

export default function Disclaimer() {
  const { t } = useTranslation()
  return (
    <p className="text-xs text-gray-400 text-center px-4 py-2">
      {t('common.disclaimer')}
    </p>
  )
}
