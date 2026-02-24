/**
 * i18n.js — Internationalisation setup (i18next + react-i18next)
 *
 * Supported languages:
 *  - 'en' — English (default)
 *  - 'hi' — Hindi
 *
 * Translation strings live in src/locales/{lang}.json.
 * The active language is persisted to localStorage under 'sakhi_language'
 * so the user's choice survives a page refresh.
 *
 * escapeValue: false — React already escapes values, so double-escaping
 * would corrupt strings containing HTML entities.
 *
 * This module is imported for its side-effect in main.jsx before the
 * component tree mounts, ensuring all t() calls resolve synchronously.
 */
import i18n from 'i18next'
import { initReactI18next } from 'react-i18next'
import en from './locales/en.json'
import hi from './locales/hi.json'

const savedLang = localStorage.getItem('sakhi_language') || 'en'

i18n
  .use(initReactI18next)
  .init({
    resources: {
      en: { translation: en },
      hi: { translation: hi },
    },
    lng: savedLang,
    fallbackLng: 'en',
    interpolation: {
      escapeValue: false,
    },
  })

export default i18n
