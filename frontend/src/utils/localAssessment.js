/**
 * localAssessment.js — Rule-based clinical triage for offline use.
 *
 * Implements a subset of MOHFW ANC and HBNC guidelines sufficient to
 * produce a meaningful risk classification (green / yellow / red) when
 * the AI backend is unreachable. Results carry _offline: true so the UI
 * can indicate that AI review is pending.
 */

export function assessLocally(patient, checkup, patientType) {
  return patientType === 'newborn'
    ? assessNewbornLocally(patient, checkup)
    : assessANCLocally(patient, checkup)
}

// ─── ANC ──────────────────────────────────────────────────────────────────────

function assessANCLocally(patient, checkup) {
  const { bp_systolic: sys, bp_diastolic: dia, weight_kg, hemoglobin, symptoms = [] } = checkup
  const notices = []
  let risk = 'green'

  // Blood pressure (MOHFW thresholds)
  if (sys >= 160 || dia >= 110) {
    risk = 'red'
    notices.push(`Severely high blood pressure: ${sys}/${dia} mmHg — refer immediately`)
  } else if (sys >= 140 || dia >= 90) {
    risk = 'red'
    notices.push(`High blood pressure: ${sys}/${dia} mmHg — PHC referral needed`)
  } else if (sys >= 130 || dia >= 80) {
    if (risk === 'green') risk = 'yellow'
    notices.push(`Elevated blood pressure: ${sys}/${dia} mmHg — monitor closely`)
  } else {
    notices.push(`Blood pressure ${sys}/${dia} mmHg — within normal range`)
  }

  // Haemoglobin (WHO anaemia thresholds for pregnancy)
  if (hemoglobin != null) {
    if (hemoglobin < 7) {
      risk = 'red'
      notices.push(`Severe anaemia: Hb ${hemoglobin} g/dL — urgent referral needed`)
    } else if (hemoglobin < 11) {
      if (risk === 'green') risk = 'yellow'
      notices.push(`Anaemia: Hb ${hemoglobin} g/dL — ensure IFA tablets taken daily`)
    } else {
      notices.push(`Haemoglobin ${hemoglobin} g/dL — normal`)
    }
  }

  // Weight trend vs most recent previous visit
  const prev = patient.checkup_history?.at(-1)
  if (prev?.weight_kg && weight_kg < prev.weight_kg - 1) {
    if (risk === 'green') risk = 'yellow'
    notices.push(`Weight decreased from ${prev.weight_kg} kg to ${weight_kg} kg — check nutrition`)
  }

  // Symptoms — danger vs monitoring
  const DANGER_KEYWORDS = [
    'headache', 'vision', 'fits', 'seizure', 'bleed', 'absent fetal',
    'no fetal', 'abdominal pain', 'chest pain', 'breathless', 'convuls',
  ]
  const MONITOR_KEYWORDS = [
    'swelling', 'oedema', 'edema', 'fever', 'burning', 'discharge',
    'nausea', 'vomit',
  ]
  const symsLower = symptoms.map(s => s.toLowerCase())
  const hasDanger  = DANGER_KEYWORDS.some(kw => symsLower.some(s => s.includes(kw)))
  const hasMonitor = MONITOR_KEYWORDS.some(kw => symsLower.some(s => s.includes(kw)))

  if (hasDanger) {
    risk = 'red'
    notices.push('Danger symptoms reported — immediate PHC referral required')
  } else if (hasMonitor && risk === 'green') {
    risk = 'yellow'
    notices.push('Some symptoms noted — follow up within 7 days')
  }

  return {
    risk_level: risk,
    risk_reason:
      risk === 'red'    ? 'High-risk findings — immediate PHC referral required'
      : risk === 'yellow' ? 'Some findings need monitoring — follow up within 7 days'
      :                     'Vitals within normal range — continue routine ANC care',
    what_sakhi_noticed: notices.length
      ? notices
      : ['All vitals recorded — AI review will update this assessment when connectivity returns'],
    what_to_tell_patient:
      risk === 'red'
        ? 'Your readings show something that needs a doctor today. Please go to the PHC right away — do not wait.'
        : risk === 'yellow'
        ? 'Your readings need a closer look. Take your IFA tablets every day, eat well, and come for your next checkup as scheduled.'
        : 'You are doing well. Keep taking your IFA tablets daily and eating nutritious food. Come for your next checkup on schedule.',
    what_to_do_next:
      risk === 'red'
        ? 'Refer to PHC today. Do not delay. Call 108 if the PHC is far.'
        : risk === 'yellow'
        ? 'Schedule follow-up within 7 days. Provide IFA tablets and nutrition counselling.'
        : 'Schedule next ANC visit as per schedule. Provide IFA tablets and advice.',
    follow_up_date: offlineFollowUpDate(risk, 7, 28),
    _offline: true,
  }
}

// ─── Newborn ───────────────────────────────────────────────────────────────────

function assessNewbornLocally(patient, checkup) {
  const { weight_kg, observations = [] } = checkup
  const birthWeight = patient.birth_weight_kg
  const notices = []
  let risk = 'green'

  // Weight assessment (WHO / HBNC thresholds)
  if (weight_kg != null) {
    if (weight_kg < 1.5) {
      risk = 'red'
      notices.push(`Very low weight: ${weight_kg} kg — urgent referral needed`)
    } else if (weight_kg < 2.5) {
      if (risk === 'green') risk = 'yellow'
      notices.push(`Low weight: ${weight_kg} kg — monitor closely`)
    } else if (birthWeight != null) {
      const lossPct = ((birthWeight - weight_kg) / birthWeight) * 100
      if (lossPct > 10) {
        risk = 'red'
        notices.push(`Weight loss >10% from birth weight (${birthWeight} kg → ${weight_kg} kg) — urgent`)
      } else if (lossPct > 7) {
        if (risk === 'green') risk = 'yellow'
        notices.push(`Weight loss >7% from birth weight — review feeding frequency`)
      } else {
        notices.push(`Weight ${weight_kg} kg — within expected range`)
      }
    }
  }

  // HBNC danger signs vs monitoring observations
  const DANGER_OBS = [
    'not feeding', 'unable to feed', 'fast breathing', 'slow breathing',
    'fits', 'convuls', 'unconscious', 'lethargic', 'bulging fontanelle',
    'yellow palms', 'yellow soles', 'cold to touch', 'not cry',
  ]
  const MONITOR_OBS = [
    'not feeding well', 'jaundice', 'cord discharge', 'cord bleed',
    'fever', 'poor cry', 'not breastfeed',
  ]
  const obsLower = observations.map(o => o.toLowerCase())
  const hasDangerObs  = DANGER_OBS.some(kw => obsLower.some(o => o.includes(kw)))
  const hasMonitorObs = MONITOR_OBS.some(kw => obsLower.some(o => o.includes(kw)))

  if (hasDangerObs) {
    risk = 'red'
    notices.push('Danger signs present — immediate PHC referral required')
  } else if (hasMonitorObs && risk === 'green') {
    risk = 'yellow'
    notices.push('Some observations need monitoring — follow up within 2 days')
  }

  return {
    risk_level: risk,
    risk_reason:
      risk === 'red'    ? 'Danger signs detected — immediate PHC referral needed'
      : risk === 'yellow' ? 'Some findings need monitoring — follow up within 2 days'
      :                     'Newborn appears well — continue routine HBNC care',
    what_sakhi_noticed: notices.length
      ? notices
      : ['Visit observations recorded — AI review will update this assessment when connectivity returns'],
    what_to_tell_patient:
      risk === 'red'
        ? 'Your baby needs to be seen by a doctor right away. Please go to the PHC immediately.'
        : risk === 'yellow'
        ? 'Keep breastfeeding every 2 hours. Keep the baby warm. Watch for danger signs like fast breathing, poor feeding, or fits.'
        : 'Your baby is doing well. Continue breastfeeding every 2 hours. Keep the baby warm and the cord clean and dry.',
    what_to_do_next:
      risk === 'red'
        ? 'Refer to PHC immediately. Call 108 if needed. Do not delay.'
        : risk === 'yellow'
        ? 'Follow up within 2 days. Advise exclusive breastfeeding and keeping baby warm.'
        : 'Schedule next HBNC visit as per schedule. Continue support for exclusive breastfeeding.',
    follow_up_date: offlineFollowUpDate(risk, 2, 3),
    _offline: true,
  }
}

// ─── Helpers ───────────────────────────────────────────────────────────────────

function offlineFollowUpDate(risk, yellowDays, greenDays) {
  if (risk === 'red') return null
  const d = new Date()
  d.setDate(d.getDate() + (risk === 'yellow' ? yellowDays : greenDays))
  return d.toISOString().split('T')[0]
}
