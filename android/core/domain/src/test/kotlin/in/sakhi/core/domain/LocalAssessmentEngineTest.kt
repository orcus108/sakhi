package `in`.sakhi.core.domain

import `in`.sakhi.core.domain.model.AncCheckup
import `in`.sakhi.core.domain.model.AncPatient
import `in`.sakhi.core.domain.model.NewbornPatient
import `in`.sakhi.core.domain.model.NewbornVisit
import `in`.sakhi.core.domain.model.RiskLevel
import `in`.sakhi.core.domain.model.VisitDay
import `in`.sakhi.core.domain.usecase.LocalAssessmentEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Unit tests for LocalAssessmentEngine.kt
 *
 * All boundary conditions derived from localAssessment.js source.
 * Each test has a comment referencing the JS line(s) it verifies.
 */
class LocalAssessmentEngineTest {

    // ── Helpers ────────────────────────────────────────────────────────────────────

    private fun ancPatient(checkupHistory: List<AncCheckup> = emptyList()) = AncPatient(
        id = "p1",
        ashaWorkerId = "w1",
        name = "Test Patient",
        age = 25,
        village = "Test Village",
        checkupHistory = checkupHistory
    )

    private fun ancCheckup(
        bpSystolic: Int = 120,
        bpDiastolic: Int = 80,
        weightKg: Double = 60.0,
        hemoglobin: Double? = null,
        symptoms: List<String> = emptyList()
    ) = AncCheckup(
        id = "c1",
        patientId = "p1",
        date = "2026-04-11",
        weightKg = weightKg,
        fundalHeightCm = 25.0,
        bpSystolic = bpSystolic,
        bpDiastolic = bpDiastolic,
        hemoglobin = hemoglobin,
        symptoms = symptoms
    )

    private fun newbornPatient(birthWeightKg: Double = 3.0) = NewbornPatient(
        id = "nb1",
        ashaWorkerId = "w1",
        name = "Baby Test",
        dateOfBirth = "2026-04-01",
        village = "Test Village",
        birthWeightKg = birthWeightKg
    )

    private fun newbornVisit(
        weightKg: Double = 2.95,
        observations: List<String> = emptyList()
    ) = NewbornVisit(
        id = "v1",
        patientId = "nb1",
        date = "2026-04-11",
        visitDay = VisitDay.DAY_7,
        weightKg = weightKg,
        observations = observations
    )

    // ── ANC — Blood Pressure ───────────────────────────────────────────────────────

    // JS line 24: sys >= 160 → RED
    @Test fun `bp sys 160 is RED`() {
        val result = LocalAssessmentEngine.assess(ancPatient(), ancCheckup(bpSystolic = 160, bpDiastolic = 80))
        assertEquals(RiskLevel.RED, result.riskLevel)
    }

    // JS line 24: dia >= 110 → RED
    @Test fun `bp dia 110 is RED`() {
        val result = LocalAssessmentEngine.assess(ancPatient(), ancCheckup(bpSystolic = 100, bpDiastolic = 110))
        assertEquals(RiskLevel.RED, result.riskLevel)
    }

    // JS line 24: sys 159 and dia 109 — both below severe threshold
    @Test fun `bp sys 159 dia 109 is NOT severe RED (but still RED via second tier)`() {
        val result = LocalAssessmentEngine.assess(ancPatient(), ancCheckup(bpSystolic = 159, bpDiastolic = 109))
        // sys=159 >= 140 → still RED via second tier
        assertEquals(RiskLevel.RED, result.riskLevel)
    }

    // JS line 27: sys >= 140 → RED (second tier)
    @Test fun `bp sys 140 is RED`() {
        val result = LocalAssessmentEngine.assess(ancPatient(), ancCheckup(bpSystolic = 140, bpDiastolic = 75))
        assertEquals(RiskLevel.RED, result.riskLevel)
    }

    // JS line 27: dia >= 90 → RED (second tier)
    @Test fun `bp dia 90 is RED`() {
        val result = LocalAssessmentEngine.assess(ancPatient(), ancCheckup(bpSystolic = 100, bpDiastolic = 90))
        assertEquals(RiskLevel.RED, result.riskLevel)
    }

    // JS line 30: sys >= 130 → YELLOW (only if currently GREEN)
    @Test fun `bp sys 130 is YELLOW`() {
        val result = LocalAssessmentEngine.assess(ancPatient(), ancCheckup(bpSystolic = 130, bpDiastolic = 75))
        assertEquals(RiskLevel.YELLOW, result.riskLevel)
    }

    // JS line 30: dia >= 80 → YELLOW (only if currently GREEN)
    @Test fun `bp dia 80 is YELLOW`() {
        val result = LocalAssessmentEngine.assess(ancPatient(), ancCheckup(bpSystolic = 100, bpDiastolic = 80))
        assertEquals(RiskLevel.YELLOW, result.riskLevel)
    }

    // Below all thresholds → GREEN
    @Test fun `bp sys 129 dia 79 is GREEN`() {
        val result = LocalAssessmentEngine.assess(ancPatient(), ancCheckup(bpSystolic = 129, bpDiastolic = 79))
        assertEquals(RiskLevel.GREEN, result.riskLevel)
    }

    // ── ANC — Haemoglobin ─────────────────────────────────────────────────────────

    // JS line 39: hb < 7 → RED
    @Test fun `hb 6_9 is RED`() {
        val result = LocalAssessmentEngine.assess(ancPatient(), ancCheckup(hemoglobin = 6.9, bpSystolic = 120, bpDiastolic = 79))
        assertEquals(RiskLevel.RED, result.riskLevel)
    }

    // JS line 39: boundary — hb = 7.0 is NOT < 7, so falls through to < 11 → YELLOW
    @Test fun `hb 7_0 is YELLOW not RED`() {
        val result = LocalAssessmentEngine.assess(ancPatient(), ancCheckup(hemoglobin = 7.0, bpSystolic = 120, bpDiastolic = 79))
        assertEquals(RiskLevel.YELLOW, result.riskLevel)
    }

    // JS line 42: hb < 11 → YELLOW
    @Test fun `hb 10_9 is YELLOW`() {
        val result = LocalAssessmentEngine.assess(ancPatient(), ancCheckup(hemoglobin = 10.9, bpSystolic = 120, bpDiastolic = 79))
        assertEquals(RiskLevel.YELLOW, result.riskLevel)
    }

    // JS line 42: boundary — hb = 11.0 is NOT < 11 → GREEN (when BP is also fine)
    @Test fun `hb 11_0 is GREEN`() {
        val result = LocalAssessmentEngine.assess(ancPatient(), ancCheckup(hemoglobin = 11.0, bpSystolic = 120, bpDiastolic = 79))
        assertEquals(RiskLevel.GREEN, result.riskLevel)
    }

    // Nullable hb — no notice emitted
    @Test fun `null hb does not affect risk`() {
        val result = LocalAssessmentEngine.assess(ancPatient(), ancCheckup(hemoglobin = null, bpSystolic = 120, bpDiastolic = 79))
        assertEquals(RiskLevel.GREEN, result.riskLevel)
        assertTrue(result.whatSakhiNoticed.none { it.contains("Hb") || it.contains("anaemia") })
    }

    // ── ANC — Weight trend ────────────────────────────────────────────────────────

    // JS line 52: weight_kg < prev.weight_kg - 1 → YELLOW
    @Test fun `weight drop more than 1 kg is YELLOW`() {
        val prev = ancCheckup(id = "c0", weightKg = 60.0)
        val patient = ancPatient(checkupHistory = listOf(prev))
        // 60.0 - 58.9 = 1.1 > 1.0 → triggers
        val result = LocalAssessmentEngine.assess(patient, ancCheckup(weightKg = 58.9))
        assertEquals(RiskLevel.YELLOW, result.riskLevel)
    }

    // JS line 52: boundary — exactly 1 kg less does NOT trigger (strictly less-than)
    @Test fun `weight drop exactly 1 kg is GREEN`() {
        val prev = ancCheckup(id = "c0", weightKg = 60.0)
        val patient = ancPatient(checkupHistory = listOf(prev))
        // 60.0 - 59.0 = 1.0, condition is < prev - 1 → not triggered
        val result = LocalAssessmentEngine.assess(patient, ancCheckup(weightKg = 59.0))
        assertEquals(RiskLevel.GREEN, result.riskLevel)
    }

    // No history → no weight notice
    @Test fun `no checkup history skips weight trend`() {
        val result = LocalAssessmentEngine.assess(ancPatient(), ancCheckup(weightKg = 55.0))
        assertEquals(RiskLevel.GREEN, result.riskLevel)
        assertTrue(result.whatSakhiNoticed.none { it.contains("Weight decreased") })
    }

    // ── ANC — Symptoms (substring, case-insensitive) ─────────────────────────────

    @Test fun `symptom headache is RED`() {
        val result = LocalAssessmentEngine.assess(ancPatient(), ancCheckup(symptoms = listOf("bad headache since morning")))
        assertEquals(RiskLevel.RED, result.riskLevel)
    }

    @Test fun `symptom HEADACHE uppercase is RED (case-insensitive)`() {
        val result = LocalAssessmentEngine.assess(ancPatient(), ancCheckup(symptoms = listOf("HEADACHE")))
        assertEquals(RiskLevel.RED, result.riskLevel)
    }

    @Test fun `symptom absent fetal movement is RED`() {
        val result = LocalAssessmentEngine.assess(ancPatient(), ancCheckup(symptoms = listOf("absent fetal movement")))
        assertEquals(RiskLevel.RED, result.riskLevel)
    }

    @Test fun `symptom no fetal is RED`() {
        val result = LocalAssessmentEngine.assess(ancPatient(), ancCheckup(symptoms = listOf("no fetal movement felt")))
        assertEquals(RiskLevel.RED, result.riskLevel)
    }

    @Test fun `symptom swelling in feet is YELLOW`() {
        val result = LocalAssessmentEngine.assess(ancPatient(), ancCheckup(symptoms = listOf("swelling in feet")))
        assertEquals(RiskLevel.YELLOW, result.riskLevel)
    }

    @Test fun `symptom oedema is YELLOW`() {
        val result = LocalAssessmentEngine.assess(ancPatient(), ancCheckup(symptoms = listOf("mild oedema")))
        assertEquals(RiskLevel.YELLOW, result.riskLevel)
    }

    @Test fun `symptom edema variant spelling is YELLOW`() {
        val result = LocalAssessmentEngine.assess(ancPatient(), ancCheckup(symptoms = listOf("edema in legs")))
        assertEquals(RiskLevel.YELLOW, result.riskLevel)
    }

    @Test fun `unrecognised symptom tiredness is GREEN`() {
        val result = LocalAssessmentEngine.assess(ancPatient(), ancCheckup(symptoms = listOf("tiredness")))
        assertEquals(RiskLevel.GREEN, result.riskLevel)
    }

    // RED trumps YELLOW — danger + monitor symptom together → RED
    @Test fun `danger symptom overrides monitor symptom`() {
        val result = LocalAssessmentEngine.assess(
            ancPatient(),
            ancCheckup(symptoms = listOf("headache", "swelling in feet"))
        )
        assertEquals(RiskLevel.RED, result.riskLevel)
    }

    // ── ANC — Follow-up dates ─────────────────────────────────────────────────────

    @Test fun `RED follow-up date is null`() {
        val result = LocalAssessmentEngine.assess(ancPatient(), ancCheckup(bpSystolic = 160, bpDiastolic = 80))
        assertNull(result.followUpDate)
    }

    @Test fun `YELLOW follow-up date is today plus 7 days`() {
        val result = LocalAssessmentEngine.assess(ancPatient(), ancCheckup(bpSystolic = 130, bpDiastolic = 75))
        val expected = LocalDate.now().plusDays(7).toString()
        assertEquals(expected, result.followUpDate)
    }

    @Test fun `GREEN follow-up date is today plus 28 days`() {
        val result = LocalAssessmentEngine.assess(ancPatient(), ancCheckup(bpSystolic = 120, bpDiastolic = 79))
        val expected = LocalDate.now().plusDays(28).toString()
        assertEquals(expected, result.followUpDate)
    }

    // ── ANC — Result shape ────────────────────────────────────────────────────────

    @Test fun `assessment result always has all required fields`() {
        val result = LocalAssessmentEngine.assess(ancPatient(), ancCheckup())
        assertNotNull(result.id)
        assertNotNull(result.riskReason)
        assertTrue(result.whatSakhiNoticed.isNotEmpty())
        assertNotNull(result.whatToTellPatient)
        assertNotNull(result.whatToDoNext)
        assertEquals(true, result.isOffline)
        assertEquals("anc", result.patientType)
    }

    // ── Newborn — Weight ──────────────────────────────────────────────────────────

    // JS line 114: weight < 1.5 → RED
    @Test fun `newborn weight 1_49 is RED`() {
        val result = LocalAssessmentEngine.assess(newbornPatient(), newbornVisit(weightKg = 1.49))
        assertEquals(RiskLevel.RED, result.riskLevel)
    }

    // JS line 117: boundary — weight = 1.5 is NOT < 1.5, falls to < 2.5 → YELLOW
    @Test fun `newborn weight 1_5 is YELLOW not RED`() {
        val result = LocalAssessmentEngine.assess(newbornPatient(), newbornVisit(weightKg = 1.5))
        assertEquals(RiskLevel.YELLOW, result.riskLevel)
    }

    // JS line 117: weight < 2.5 → YELLOW
    @Test fun `newborn weight 2_49 is YELLOW`() {
        val result = LocalAssessmentEngine.assess(newbornPatient(), newbornVisit(weightKg = 2.49))
        assertEquals(RiskLevel.YELLOW, result.riskLevel)
    }

    // JS line 121-130: weight >= 2.5, calculate % loss
    // birthWeight = 3.0, currentWeight = 2.69 → loss = (3.0-2.69)/3.0*100 = 10.33% > 10 → RED
    @Test fun `newborn weight loss above 10 percent is RED`() {
        val result = LocalAssessmentEngine.assess(newbornPatient(3.0), newbornVisit(weightKg = 2.69))
        assertEquals(RiskLevel.RED, result.riskLevel)
    }

    // JS line 122: boundary — loss exactly 10% → NOT > 10, checks > 7 → YELLOW
    // birthWeight = 3.0, currentWeight = 2.7 → loss = 10.0% exactly → NOT triggered as RED
    @Test fun `newborn weight loss exactly 10 percent is YELLOW not RED`() {
        val result = LocalAssessmentEngine.assess(newbornPatient(3.0), newbornVisit(weightKg = 2.7))
        assertEquals(RiskLevel.YELLOW, result.riskLevel)
    }

    // JS line 125: loss > 7% → YELLOW
    // birthWeight = 3.0, currentWeight = 2.79 → loss = 7.0% → NOT > 7 → GREEN
    @Test fun `newborn weight loss exactly 7 percent is GREEN`() {
        val result = LocalAssessmentEngine.assess(newbornPatient(3.0), newbornVisit(weightKg = 2.79))
        assertEquals(RiskLevel.GREEN, result.riskLevel)
    }

    // JS line 125: loss just above 7%
    // birthWeight = 3.0, currentWeight = 2.78 → loss = 7.33% > 7 → YELLOW
    @Test fun `newborn weight loss above 7 percent is YELLOW`() {
        val result = LocalAssessmentEngine.assess(newbornPatient(3.0), newbornVisit(weightKg = 2.78))
        assertEquals(RiskLevel.YELLOW, result.riskLevel)
    }

    @Test fun `newborn healthy weight is GREEN`() {
        val result = LocalAssessmentEngine.assess(newbornPatient(3.0), newbornVisit(weightKg = 2.95))
        assertEquals(RiskLevel.GREEN, result.riskLevel)
    }

    // ── Newborn — Observations ────────────────────────────────────────────────────

    @Test fun `observation not feeding is RED`() {
        val result = LocalAssessmentEngine.assess(newbornPatient(), newbornVisit(observations = listOf("not feeding")))
        assertEquals(RiskLevel.RED, result.riskLevel)
    }

    @Test fun `observation lethargic is RED`() {
        val result = LocalAssessmentEngine.assess(newbornPatient(), newbornVisit(observations = listOf("baby is lethargic")))
        assertEquals(RiskLevel.RED, result.riskLevel)
    }

    @Test fun `observation jaundice is YELLOW`() {
        val result = LocalAssessmentEngine.assess(newbornPatient(), newbornVisit(observations = listOf("jaundice visible")))
        assertEquals(RiskLevel.YELLOW, result.riskLevel)
    }

    @Test fun `observation cord discharge is YELLOW`() {
        val result = LocalAssessmentEngine.assess(newbornPatient(), newbornVisit(observations = listOf("cord discharge present")))
        assertEquals(RiskLevel.YELLOW, result.riskLevel)
    }

    // Critical regression test: "not feeding well" must map to YELLOW, NOT RED.
    // The danger keyword "not feeding" is a substring of "not feeding well".
    // Fix: MONITOR keywords are tagged first, those observations are excluded from DANGER check.
    @Test fun `not feeding well is YELLOW not RED - regression for substring bug fix`() {
        val result = LocalAssessmentEngine.assess(
            newbornPatient(),
            newbornVisit(observations = listOf("not feeding well"))
        )
        assertEquals(
            "Expected YELLOW for 'not feeding well' (monitor keyword) but got ${result.riskLevel}. " +
                "Regression: 'not feeding' danger keyword must not match 'not feeding well'.",
            RiskLevel.YELLOW,
            result.riskLevel
        )
    }

    // Verify "not feeding" (without "well") still triggers RED
    @Test fun `not feeding without well is RED`() {
        val result = LocalAssessmentEngine.assess(
            newbornPatient(),
            newbornVisit(observations = listOf("not feeding"))
        )
        assertEquals(RiskLevel.RED, result.riskLevel)
    }

    // "not breastfeed" → MONITOR → YELLOW
    @Test fun `not breastfeed is YELLOW`() {
        val result = LocalAssessmentEngine.assess(
            newbornPatient(),
            newbornVisit(observations = listOf("not breastfeed"))
        )
        assertEquals(RiskLevel.YELLOW, result.riskLevel)
    }

    @Test fun `poor cry is YELLOW`() {
        val result = LocalAssessmentEngine.assess(
            newbornPatient(),
            newbornVisit(observations = listOf("poor cry"))
        )
        assertEquals(RiskLevel.YELLOW, result.riskLevel)
    }

    // Empty observations → GREEN
    @Test fun `empty observations is GREEN`() {
        val result = LocalAssessmentEngine.assess(newbornPatient(), newbornVisit(observations = emptyList()))
        assertEquals(RiskLevel.GREEN, result.riskLevel)
    }

    // ── Newborn — Follow-up dates ─────────────────────────────────────────────────

    @Test fun `newborn RED follow-up date is null`() {
        val result = LocalAssessmentEngine.assess(newbornPatient(), newbornVisit(weightKg = 1.0))
        assertNull(result.followUpDate)
    }

    @Test fun `newborn YELLOW follow-up date is today plus 2 days`() {
        val result = LocalAssessmentEngine.assess(newbornPatient(), newbornVisit(observations = listOf("jaundice")))
        val expected = LocalDate.now().plusDays(2).toString()
        assertEquals(expected, result.followUpDate)
    }

    @Test fun `newborn GREEN follow-up date is today plus 3 days`() {
        val result = LocalAssessmentEngine.assess(newbornPatient(3.0), newbornVisit(weightKg = 2.95))
        val expected = LocalDate.now().plusDays(3).toString()
        assertEquals(expected, result.followUpDate)
    }

    // ── Newborn — Result shape ────────────────────────────────────────────────────

    @Test fun `newborn assessment result has all required fields`() {
        val result = LocalAssessmentEngine.assess(newbornPatient(), newbornVisit())
        assertNotNull(result.id)
        assertNotNull(result.riskReason)
        assertTrue(result.whatSakhiNoticed.isNotEmpty())
        assertNotNull(result.whatToTellPatient)
        assertNotNull(result.whatToDoNext)
        assertEquals(true, result.isOffline)
        assertEquals("newborn", result.patientType)
    }

    // ── Risk priority: RED overrides YELLOW regardless of order ──────────────────

    @Test fun `RED from BP overrides YELLOW from hb`() {
        // Hb < 11 would be YELLOW, but BP >= 140 pushes to RED first
        val result = LocalAssessmentEngine.assess(
            ancPatient(),
            ancCheckup(bpSystolic = 140, bpDiastolic = 75, hemoglobin = 10.5)
        )
        assertEquals(RiskLevel.RED, result.riskLevel)
    }

    @Test fun `YELLOW from bp does not elevate to RED from monitor symptoms`() {
        // BP 130 = YELLOW; monitor symptoms should not change it to RED
        val result = LocalAssessmentEngine.assess(
            ancPatient(),
            ancCheckup(bpSystolic = 130, bpDiastolic = 75, symptoms = listOf("swelling"))
        )
        assertEquals(RiskLevel.YELLOW, result.riskLevel)
    }
}
