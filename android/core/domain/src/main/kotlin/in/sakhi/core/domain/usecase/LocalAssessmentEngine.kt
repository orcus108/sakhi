package `in`.sakhi.core.domain.usecase

import `in`.sakhi.core.domain.model.AncCheckup
import `in`.sakhi.core.domain.model.AncPatient
import `in`.sakhi.core.domain.model.AssessmentResult
import `in`.sakhi.core.domain.model.Checkup
import `in`.sakhi.core.domain.model.NewbornPatient
import `in`.sakhi.core.domain.model.NewbornVisit
import `in`.sakhi.core.domain.model.Patient
import `in`.sakhi.core.domain.model.RiskLevel
import java.time.LocalDate
import java.util.UUID

/**
 * Rule-based offline clinical triage.
 *
 * This is a direct port of frontend/src/utils/localAssessment.js.
 * Implements MOHFW ANC and WHO/HBNC guidelines sufficient to classify
 * risk as green/yellow/red when the AI model is unavailable.
 *
 * Results carry isOffline = true so the UI shows "AI review pending".
 *
 * DIVERGENCE FROM JS SOURCE (intentional, user-approved):
 *   The JS code checks DANGER_OBS before MONITOR_OBS using substring matching.
 *   Because "not feeding" is a DANGER keyword and "not feeding well" is a MONITOR
 *   keyword, "not feeding well".contains("not feeding") = true → wrongly triggers RED.
 *   Fix: observations matching a MONITOR keyword are tagged first and excluded from
 *   the DANGER keyword check. This preserves the substring-based matching approach
 *   while correctly mapping "not feeding well" → YELLOW.
 */
object LocalAssessmentEngine {

    fun assess(patient: Patient, checkup: Checkup): AssessmentResult {
        return when {
            patient is AncPatient && checkup is AncCheckup -> assessAnc(patient, checkup)
            patient is NewbornPatient && checkup is NewbornVisit -> assessNewborn(patient, checkup)
            else -> error("Patient and checkup types do not match: ${patient::class.simpleName} / ${checkup::class.simpleName}")
        }
    }

    // ── ANC ───────────────────────────────────────────────────────────────────────

    private fun assessAnc(patient: AncPatient, checkup: AncCheckup): AssessmentResult {
        val notices = mutableListOf<String>()
        var risk = RiskLevel.GREEN

        val sys = checkup.bpSystolic
        val dia = checkup.bpDiastolic

        // Blood pressure — three tiers (MOHFW thresholds)
        when {
            sys >= 160 || dia >= 110 -> {
                risk = RiskLevel.RED
                notices.add("Severely high blood pressure: $sys/$dia mmHg — refer immediately")
            }
            sys >= 140 || dia >= 90 -> {
                risk = RiskLevel.RED
                notices.add("High blood pressure: $sys/$dia mmHg — PHC referral needed")
            }
            sys >= 130 || dia >= 80 -> {
                if (risk == RiskLevel.GREEN) risk = RiskLevel.YELLOW
                notices.add("Elevated blood pressure: $sys/$dia mmHg — monitor closely")
            }
            else -> {
                notices.add("Blood pressure $sys/$dia mmHg — within normal range")
            }
        }

        // Haemoglobin (WHO anaemia thresholds for pregnancy)
        checkup.hemoglobin?.let { hb ->
            when {
                hb < 7.0 -> {
                    risk = RiskLevel.RED
                    notices.add("Severe anaemia: Hb $hb g/dL — urgent referral needed")
                }
                hb < 11.0 -> {
                    if (risk == RiskLevel.GREEN) risk = RiskLevel.YELLOW
                    notices.add("Anaemia: Hb $hb g/dL — ensure IFA tablets taken daily")
                }
                else -> {
                    notices.add("Haemoglobin $hb g/dL — normal")
                }
            }
        }

        // Weight trend vs most recent previous visit
        val prev = patient.checkupHistory.lastOrNull()
        if (prev != null && checkup.weightKg < prev.weightKg - 1.0) {
            if (risk == RiskLevel.GREEN) risk = RiskLevel.YELLOW
            notices.add("Weight decreased from ${prev.weightKg} kg to ${checkup.weightKg} kg — check nutrition")
        }

        // Symptoms — danger keywords override monitor keywords
        val symsLower = checkup.symptoms.map { it.lowercase() }
        val hasDanger  = ANC_DANGER_KEYWORDS.any { kw -> symsLower.any { s -> s.contains(kw) } }
        val hasMonitor = ANC_MONITOR_KEYWORDS.any { kw -> symsLower.any { s -> s.contains(kw) } }

        when {
            hasDanger -> {
                risk = RiskLevel.RED
                notices.add("Danger symptoms reported — immediate PHC referral required")
            }
            hasMonitor && risk == RiskLevel.GREEN -> {
                risk = RiskLevel.YELLOW
                notices.add("Some symptoms noted — follow up within 7 days")
            }
        }

        val finalNotices = notices.ifEmpty {
            listOf("All vitals recorded — AI review will update this assessment when connectivity returns")
        }

        return AssessmentResult(
            id = UUID.randomUUID().toString(),
            checkupId = checkup.id,
            patientId = patient.id,
            patientType = "anc",
            riskLevel = risk,
            riskReason = when (risk) {
                RiskLevel.RED    -> "High-risk findings — immediate PHC referral required"
                RiskLevel.YELLOW -> "Some findings need monitoring — follow up within 7 days"
                RiskLevel.GREEN  -> "Vitals within normal range — continue routine ANC care"
            },
            whatSakhiNoticed = finalNotices,
            whatToTellPatient = when (risk) {
                RiskLevel.RED    -> "Your readings show something that needs a doctor today. Please go to the PHC right away — do not wait."
                RiskLevel.YELLOW -> "Your readings need a closer look. Take your IFA tablets every day, eat well, and come for your next checkup as scheduled."
                RiskLevel.GREEN  -> "You are doing well. Keep taking your IFA tablets daily and eating nutritious food. Come for your next checkup on schedule."
            },
            whatToDoNext = when (risk) {
                RiskLevel.RED    -> "Refer to PHC today. Do not delay. Call 108 if the PHC is far."
                RiskLevel.YELLOW -> "Schedule follow-up within 7 days. Provide IFA tablets and nutrition counselling."
                RiskLevel.GREEN  -> "Schedule next ANC visit as per schedule. Provide IFA tablets and advice."
            },
            followUpDate = followUpDate(risk, yellowDays = 7, greenDays = 28),
            isOffline = true
        )
    }

    // ── Newborn ───────────────────────────────────────────────────────────────────

    private fun assessNewborn(patient: NewbornPatient, visit: NewbornVisit): AssessmentResult {
        val notices = mutableListOf<String>()
        var risk = RiskLevel.GREEN

        val weight = visit.weightKg
        val birthWeight = patient.birthWeightKg

        // Weight assessment (WHO/HBNC thresholds)
        when {
            weight < 1.5 -> {
                risk = RiskLevel.RED
                notices.add("Very low weight: $weight kg — urgent referral needed")
            }
            weight < 2.5 -> {
                if (risk == RiskLevel.GREEN) risk = RiskLevel.YELLOW
                notices.add("Low weight: $weight kg — monitor closely")
            }
            else -> {
                val lossPct = ((birthWeight - weight) / birthWeight) * 100.0
                when {
                    lossPct > 10.0 -> {
                        risk = RiskLevel.RED
                        notices.add("Weight loss >10% from birth weight ($birthWeight kg → $weight kg) — urgent")
                    }
                    lossPct > 7.0 -> {
                        if (risk == RiskLevel.GREEN) risk = RiskLevel.YELLOW
                        notices.add("Weight loss >7% from birth weight — review feeding frequency")
                    }
                    else -> {
                        notices.add("Weight $weight kg — within expected range")
                    }
                }
            }
        }

        // HBNC observations — fix applied: MONITOR keywords tagged first,
        // DANGER keywords only checked against non-monitor observations.
        val obsLower = visit.observations.map { it.lowercase() }

        // Step 1: tag observations that match a MONITOR keyword
        val monitorMatchedObs = obsLower.filter { obs ->
            NEWBORN_MONITOR_OBS.any { kw -> obs.contains(kw) }
        }.toSet()

        // Step 2: DANGER check only against observations not already tagged as MONITOR
        // This ensures "not feeding well" (monitor) is not caught by "not feeding" (danger).
        val hasDangerObs = NEWBORN_DANGER_OBS.any { kw ->
            obsLower.any { obs -> obs !in monitorMatchedObs && obs.contains(kw) }
        }

        val hasMonitorObs = monitorMatchedObs.isNotEmpty()

        when {
            hasDangerObs -> {
                risk = RiskLevel.RED
                notices.add("Danger signs present — immediate PHC referral required")
            }
            hasMonitorObs && risk == RiskLevel.GREEN -> {
                risk = RiskLevel.YELLOW
                notices.add("Some observations need monitoring — follow up within 2 days")
            }
        }

        val finalNotices = notices.ifEmpty {
            listOf("Visit observations recorded — AI review will update this assessment when connectivity returns")
        }

        return AssessmentResult(
            id = UUID.randomUUID().toString(),
            checkupId = visit.id,
            patientId = patient.id,
            patientType = "newborn",
            riskLevel = risk,
            riskReason = when (risk) {
                RiskLevel.RED    -> "Danger signs detected — immediate PHC referral needed"
                RiskLevel.YELLOW -> "Some findings need monitoring — follow up within 2 days"
                RiskLevel.GREEN  -> "Newborn appears well — continue routine HBNC care"
            },
            whatSakhiNoticed = finalNotices,
            whatToTellPatient = when (risk) {
                RiskLevel.RED    -> "Your baby needs to be seen by a doctor right away. Please go to the PHC immediately."
                RiskLevel.YELLOW -> "Keep breastfeeding every 2 hours. Keep the baby warm. Watch for danger signs like fast breathing, poor feeding, or fits."
                RiskLevel.GREEN  -> "Your baby is doing well. Continue breastfeeding every 2 hours. Keep the baby warm and the cord clean and dry."
            },
            whatToDoNext = when (risk) {
                RiskLevel.RED    -> "Refer to PHC immediately. Call 108 if needed. Do not delay."
                RiskLevel.YELLOW -> "Follow up within 2 days. Advise exclusive breastfeeding and keeping baby warm."
                RiskLevel.GREEN  -> "Schedule next HBNC visit as per schedule. Continue support for exclusive breastfeeding."
            },
            followUpDate = followUpDate(risk, yellowDays = 2, greenDays = 3),
            isOffline = true
        )
    }

    // ── Keyword lists — must match localAssessment.js exactly ─────────────────────

    private val ANC_DANGER_KEYWORDS = listOf(
        "headache", "vision", "fits", "seizure", "bleed", "absent fetal",
        "no fetal", "abdominal pain", "chest pain", "breathless", "convuls"
    )

    private val ANC_MONITOR_KEYWORDS = listOf(
        "swelling", "oedema", "edema", "fever", "burning", "discharge",
        "nausea", "vomit"
    )

    private val NEWBORN_DANGER_OBS = listOf(
        "not feeding", "unable to feed", "fast breathing", "slow breathing",
        "fits", "convuls", "unconscious", "lethargic", "bulging fontanelle",
        "yellow palms", "yellow soles", "cold to touch", "not cry"
    )

    private val NEWBORN_MONITOR_OBS = listOf(
        "not feeding well", "jaundice", "cord discharge", "cord bleed",
        "fever", "poor cry", "not breastfeed"
    )

    // ── Helper ─────────────────────────────────────────────────────────────────────

    private fun followUpDate(risk: RiskLevel, yellowDays: Long, greenDays: Long): String? {
        return when (risk) {
            RiskLevel.RED    -> null
            RiskLevel.YELLOW -> LocalDate.now().plusDays(yellowDays).toString()
            RiskLevel.GREEN  -> LocalDate.now().plusDays(greenDays).toString()
        }
    }
}
