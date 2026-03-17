/**
 * Returns the localized display name for a patient/newborn record.
 * Falls back to the English name if no translation is available.
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

/**
 * Returns the localized village for a patient/newborn record.
 * Falls back to the English village name if no translation is stored.
 *
 * @param {object} record - Patient or newborn record
 * @param {string} language - Current language code
 * @returns {string}
 */
export function localVillage(record, language) {
  const key = `village_${language}`
  return (record?.[key]) || record?.village || ''
}
