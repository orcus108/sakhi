/**
 * Returns the localized display name for a patient/newborn record.
 * Falls back to the English name if no translation is available.
 *
 * Scalable pattern: each record carries its own translated fields
 * (name_hi, mother_name_hi, etc.). Adding a new language means
 * adding the corresponding field to the data — no component changes needed.
 *
 * @param {object} record - Patient or newborn record
 * @param {string} language - Current language code ('en', 'hi', ...)
 * @returns {string}
 */
export function localName(record, language) {
  const key = `name_${language}`
  return (record?.[key]) || record?.name || ''
}

/**
 * Returns the localized mother name for a newborn record.
 *
 * @param {object} record - Newborn record
 * @param {string} language - Current language code
 * @returns {string}
 */
export function localMotherName(record, language) {
  const key = `mother_name_${language}`
  return (record?.[key]) || record?.mother_name || ''
}
