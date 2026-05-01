package `in`.sakhi.app.debug

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import `in`.sakhi.core.data.auth.AuthPreferences
import `in`.sakhi.core.domain.model.AncCheckup
import `in`.sakhi.core.domain.model.AncPatient
import `in`.sakhi.core.domain.model.NewbornPatient
import `in`.sakhi.core.domain.model.NewbornVisit
import `in`.sakhi.core.domain.model.RiskLevel
import `in`.sakhi.core.domain.model.VisitDay
import `in`.sakhi.core.domain.repository.PatientRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Seeds one ASHA worker (Kamla Devi) and 15 demo patients into Room on first debug launch.
 *
 * Patients are designed to produce an interesting home screen:
 *   - Several due today (ANC + newborn)
 *   - A couple overdue
 *   - Two RED urgent patients
 *   - Mix of Rampur and Khanpur villages
 *
 * All records are seeded with dirty=false so SyncWorker ignores them.
 * The seeder flag is stored in plain (unencrypted) SharedPreferences because
 * EncryptedSharedPreferences is tied to the real user session.
 */
@Singleton
class DebugDataSeederImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authPrefs: AuthPreferences,
    private val patientRepository: PatientRepository,
) : DebugDataSeeder {

    private val seedPrefs: SharedPreferences by lazy {
        context.getSharedPreferences("sakhi_debug_seed", Context.MODE_PRIVATE)
    }

    override suspend fun seedIfNeeded() {
        if (seedPrefs.getBoolean(KEY_SEEDED, false)) return

        authPrefs.saveSession(
            userId = WORKER_ID,
            accessToken = "debug-access-token-not-real",
            refreshToken = "debug-refresh-token-not-real",
        )
        authPrefs.saveWorkerInfo(
            name = "Kamla Devi",
            phone = "9876500001",
            language = "en",
        )
        authPrefs.saveAshaId("ASH1001")

        ANC_PATIENTS.forEach { patientRepository.upsertAncPatient(it) }
        ANC_CHECKUPS.forEach { patientRepository.upsertAncCheckup(it) }
        NEWBORN_PATIENTS.forEach { patientRepository.upsertNewbornPatient(it) }
        NEWBORN_VISITS.forEach { patientRepository.upsertNewbornVisit(it) }

        seedPrefs.edit().putBoolean(KEY_SEEDED, true).apply()
    }

    companion object {

        const val WORKER_ID = "asha-debug-kamla-001"
        private const val KEY_SEEDED = "debug_seed_v4"

        // ── Villages ──────────────────────────────────────────────────────────────

        private const val RAMPUR = "Rampur Village, Rajasthan"
        private const val RAMPUR_HI = "रामपुर गाँव, राजस्थान"
        private const val KHANPUR = "Khanpur Hamlet, Rajasthan"
        private const val KHANPUR_HI = "खानपुर बस्ती, राजस्थान"

        // ── ANC patients ──────────────────────────────────────────────────────────
        //
        // Next-due logic (HomeViewModel):
        //   GREEN  = last checkup + 28 days
        //   YELLOW = last checkup + 14 days
        //   RED    = last checkup +  7 days
        //
        // Reference date: 2026-04-11 (today)

        val ANC_PATIENTS = listOf(

            // 1. Meena Devi — 29w GREEN, last checkup 2026-03-14 → next due 2026-04-11 (DUE TODAY)
            AncPatient(
                id = "anc-01-meena-devi",
                ashaWorkerId = WORKER_ID,
                name = "Meena Devi", nameHi = "मीना देवी",
                age = 24, phone = "9876543210",
                village = RAMPUR, villageHi = RAMPUR_HI,
                lmp = "2025-09-20", gestationalWeeks = 29,
                gravida = 2, para = 1,
                riskLevel = RiskLevel.GREEN,
                dirty = false,
            ),

            // 2. Sunita Kumari — 26w GREEN, last checkup 2026-03-22 → next due 2026-04-19
            AncPatient(
                id = "anc-02-sunita-kumari",
                ashaWorkerId = WORKER_ID,
                name = "Sunita Kumari", nameHi = "सुनीता कुमारी",
                age = 22, phone = "9812345678",
                village = RAMPUR, villageHi = RAMPUR_HI,
                lmp = "2025-10-11", gestationalWeeks = 26,
                gravida = 1, para = 0,
                riskLevel = RiskLevel.GREEN,
                dirty = false,
            ),

            // 3. Priya Sharma — 18w GREEN, last checkup 2026-03-28 → next due 2026-04-25
            AncPatient(
                id = "anc-03-priya-sharma",
                ashaWorkerId = WORKER_ID,
                name = "Priya Sharma", nameHi = "प्रिया शर्मा",
                age = 26, phone = "9823456789",
                village = RAMPUR, villageHi = RAMPUR_HI,
                lmp = "2025-12-06", gestationalWeeks = 18,
                gravida = 1, para = 0,
                riskLevel = RiskLevel.GREEN,
                dirty = false,
            ),

            // 4. Rekha Sharma — 30w YELLOW (elevated BP + pedal oedema)
            //    Last checkup 2026-03-28 → next due 2026-04-11 (DUE TODAY)
            AncPatient(
                id = "anc-04-rekha-sharma",
                ashaWorkerId = WORKER_ID,
                name = "Rekha Sharma", nameHi = "रेखा शर्मा",
                age = 28, phone = "9867452310",
                village = RAMPUR, villageHi = RAMPUR_HI,
                lmp = "2025-09-13", gestationalWeeks = 30,
                gravida = 3, para = 2,
                riskLevel = RiskLevel.YELLOW,
                dirty = false,
            ),

            // 5. Geeta Patel — 26w YELLOW (anaemia Hb 9.8)
            //    Last checkup 2026-03-28 → next due 2026-04-11 (DUE TODAY)
            AncPatient(
                id = "anc-05-geeta-patel",
                ashaWorkerId = WORKER_ID,
                name = "Geeta Patel", nameHi = "गीता पटेल",
                age = 25, phone = "9845671234",
                village = KHANPUR, villageHi = KHANPUR_HI,
                lmp = "2025-10-11", gestationalWeeks = 26,
                gravida = 2, para = 1,
                riskLevel = RiskLevel.YELLOW,
                dirty = false,
            ),

            // 6. Kavita Yadav — 36w RED (BP 148/98, Hb 8.0, pre-eclampsia signs)
            //    Last checkup 2026-04-05 → next due 2026-04-12 (tomorrow, RED → urgent)
            AncPatient(
                id = "anc-06-kavita-yadav",
                ashaWorkerId = WORKER_ID,
                name = "Kavita Yadav", nameHi = "कविता यादव",
                age = 27, phone = "9856789012",
                village = KHANPUR, villageHi = KHANPUR_HI,
                lmp = "2025-08-03", gestationalWeeks = 36,
                gravida = 2, para = 1,
                riskLevel = RiskLevel.RED,
                dirty = false,
            ),

            // 7. Anita Devi — 32w YELLOW (mild anaemia + swelling)
            //    Last checkup 2026-03-28 → next due 2026-04-11 (DUE TODAY)
            AncPatient(
                id = "anc-07-anita-devi",
                ashaWorkerId = WORKER_ID,
                name = "Anita Devi", nameHi = "अनीता देवी",
                age = 29, phone = "9834512670",
                village = RAMPUR, villageHi = RAMPUR_HI,
                lmp = "2025-08-31", gestationalWeeks = 32,
                gravida = 2, para = 1,
                riskLevel = RiskLevel.YELLOW,
                dirty = false,
            ),

            // 8. Pooja Singh — 14w GREEN, last checkup 2026-03-20 → next due 2026-04-17
            AncPatient(
                id = "anc-08-pooja-singh",
                ashaWorkerId = WORKER_ID,
                name = "Pooja Singh", nameHi = "पूजा सिंह",
                age = 21, phone = "9890123456",
                village = RAMPUR, villageHi = RAMPUR_HI,
                lmp = "2026-01-03", gestationalWeeks = 14,
                gravida = 1, para = 0,
                riskLevel = RiskLevel.GREEN,
                dirty = false,
            ),

            // 9. Savitri Bai — 34w YELLOW (Hb 9.2, BP 128/84)
            //    Last checkup 2026-03-22 → next due 2026-04-05 (6 days OVERDUE)
            AncPatient(
                id = "anc-09-savitri-bai",
                ashaWorkerId = WORKER_ID,
                name = "Savitri Bai", nameHi = "सावित्री बाई",
                age = 31, phone = "9878901234",
                village = KHANPUR, villageHi = KHANPUR_HI,
                lmp = "2025-08-17", gestationalWeeks = 34,
                gravida = 3, para = 2,
                riskLevel = RiskLevel.YELLOW,
                dirty = false,
            ),

            // 10. Lalita Kumari — 10w GREEN (early ANC registration)
            //     Last checkup 2026-04-01 → next due 2026-04-29
            AncPatient(
                id = "anc-10-lalita-kumari",
                ashaWorkerId = WORKER_ID,
                name = "Lalita Kumari", nameHi = "ललिता कुमारी",
                age = 23, phone = "9801234567",
                village = RAMPUR, villageHi = RAMPUR_HI,
                lmp = "2026-02-01", gestationalWeeks = 10,
                gravida = 1, para = 0,
                riskLevel = RiskLevel.GREEN,
                dirty = false,
            ),
        )

        // ── ANC checkups ──────────────────────────────────────────────────────────

        val ANC_CHECKUPS = listOf(

            // Meena Devi (anc-01) — 2 checkups
            AncCheckup(
                id = "chk-m01-01", patientId = "anc-01-meena-devi",
                date = "2026-02-15", weightKg = 58.0, fundalHeightCm = 22.0,
                bpSystolic = 112, bpDiastolic = 72, fetalHeartRate = 142, hemoglobin = 11.2,
                symptoms = emptyList(), riskLevel = RiskLevel.GREEN,
                notes = "Normal progress. IFA tablets given.", dirty = false,
            ),
            AncCheckup(
                id = "chk-m01-02", patientId = "anc-01-meena-devi",
                date = "2026-03-14", weightKg = 60.0, fundalHeightCm = 25.0,
                bpSystolic = 114, bpDiastolic = 74, fetalHeartRate = 138, hemoglobin = 11.5,
                symptoms = emptyList(), riskLevel = RiskLevel.GREEN,
                notes = "Good weight gain. Advised rest and iron-rich foods.", dirty = false,
            ),

            // Sunita Kumari (anc-02) — 2 checkups
            AncCheckup(
                id = "chk-m02-01", patientId = "anc-02-sunita-kumari",
                date = "2026-02-22", weightKg = 52.0, fundalHeightCm = 17.0,
                bpSystolic = 108, bpDiastolic = 68, fetalHeartRate = 145, hemoglobin = 10.8,
                symptoms = listOf("mild nausea"), riskLevel = RiskLevel.GREEN,
                notes = "First pregnancy. Nausea normal. Advised small frequent meals.", dirty = false,
            ),
            AncCheckup(
                id = "chk-m02-02", patientId = "anc-02-sunita-kumari",
                date = "2026-03-22", weightKg = 53.5, fundalHeightCm = 21.0,
                bpSystolic = 110, bpDiastolic = 70, fetalHeartRate = 148, hemoglobin = 11.0,
                symptoms = emptyList(), riskLevel = RiskLevel.GREEN,
                notes = "Nausea resolved. Good progress.", dirty = false,
            ),

            // Priya Sharma (anc-03) — 1 checkup
            AncCheckup(
                id = "chk-m03-01", patientId = "anc-03-priya-sharma",
                date = "2026-03-28", weightKg = 54.0, fundalHeightCm = 14.0,
                bpSystolic = 110, bpDiastolic = 70, fetalHeartRate = 144, hemoglobin = 11.8,
                symptoms = emptyList(), riskLevel = RiskLevel.GREEN,
                notes = "First ANC visit. All normal. IFA and calcium started.", dirty = false,
            ),

            // Rekha Sharma (anc-04) — 3 checkups, YELLOW trend
            AncCheckup(
                id = "chk-m04-01", patientId = "anc-04-rekha-sharma",
                date = "2026-01-05", weightKg = 68.0, fundalHeightCm = 22.0,
                bpSystolic = 130, bpDiastolic = 84, fetalHeartRate = 136, hemoglobin = 9.8,
                symptoms = listOf("mild swelling in feet"), riskLevel = RiskLevel.YELLOW,
                notes = "BP slightly elevated. Haemoglobin borderline. Monitoring closely.", dirty = false,
            ),
            AncCheckup(
                id = "chk-m04-02", patientId = "anc-04-rekha-sharma",
                date = "2026-02-14", weightKg = 70.0, fundalHeightCm = 25.0,
                bpSystolic = 128, bpDiastolic = 82, fetalHeartRate = 134, hemoglobin = 10.0,
                symptoms = listOf("swelling in feet", "mild headache"), riskLevel = RiskLevel.YELLOW,
                notes = "BP still elevated. Referred to PHC for review. Advised rest.", dirty = false,
            ),
            AncCheckup(
                id = "chk-m04-03", patientId = "anc-04-rekha-sharma",
                date = "2026-03-28", weightKg = 72.0, fundalHeightCm = 28.0,
                bpSystolic = 132, bpDiastolic = 86, fetalHeartRate = 132, hemoglobin = 10.2,
                symptoms = listOf("swelling in feet"), riskLevel = RiskLevel.YELLOW,
                notes = "BP unchanged. Continue monitoring. PHC follow-up next week.", dirty = false,
            ),

            // Geeta Patel (anc-05) — 3 checkups, YELLOW (anaemia)
            AncCheckup(
                id = "chk-m05-01", patientId = "anc-05-geeta-patel",
                date = "2026-01-25", weightKg = 55.0, fundalHeightCm = 13.0,
                bpSystolic = 122, bpDiastolic = 78, fetalHeartRate = 140, hemoglobin = 9.2,
                symptoms = listOf("fatigue", "dizziness on standing"), riskLevel = RiskLevel.YELLOW,
                notes = "Haemoglobin low — anaemia likely. IFA tablets doubled. Dietary counselling done.", dirty = false,
            ),
            AncCheckup(
                id = "chk-m05-02", patientId = "anc-05-geeta-patel",
                date = "2026-02-20", weightKg = 56.0, fundalHeightCm = 17.0,
                bpSystolic = 118, bpDiastolic = 76, fetalHeartRate = 142, hemoglobin = 9.5,
                symptoms = listOf("fatigue"), riskLevel = RiskLevel.YELLOW,
                notes = "Hb slightly improved. Continuing IFA. Advised diet with greens and jaggery.", dirty = false,
            ),
            AncCheckup(
                id = "chk-m05-03", patientId = "anc-05-geeta-patel",
                date = "2026-03-28", weightKg = 57.0, fundalHeightCm = 21.0,
                bpSystolic = 120, bpDiastolic = 78, fetalHeartRate = 140, hemoglobin = 9.8,
                symptoms = emptyList(), riskLevel = RiskLevel.YELLOW,
                notes = "Hb improving slowly. Continue IFA. Next visit in 14 days.", dirty = false,
            ),

            // Kavita Yadav (anc-06) — 3 checkups, RED (pre-eclampsia signs)
            AncCheckup(
                id = "chk-m06-01", patientId = "anc-06-kavita-yadav",
                date = "2026-02-10", weightKg = 75.0, fundalHeightCm = 25.0,
                bpSystolic = 138, bpDiastolic = 90, fetalHeartRate = 128, hemoglobin = 8.5,
                symptoms = listOf("severe headache", "blurred vision"), riskLevel = RiskLevel.RED,
                notes = "BP dangerously elevated. Severe anaemia. Urgent PHC referral advised.", dirty = false,
            ),
            AncCheckup(
                id = "chk-m06-02", patientId = "anc-06-kavita-yadav",
                date = "2026-03-12", weightKg = 78.0, fundalHeightCm = 30.0,
                bpSystolic = 142, bpDiastolic = 94, fetalHeartRate = 126, hemoglobin = 8.1,
                symptoms = listOf("severe headache", "swelling in hands and face"), riskLevel = RiskLevel.RED,
                notes = "BP worsening. Admitted to PHC for 2 days. Back home, still high risk.", dirty = false,
            ),
            AncCheckup(
                id = "chk-m06-03", patientId = "anc-06-kavita-yadav",
                date = "2026-04-05", weightKg = 80.0, fundalHeightCm = 33.0,
                bpSystolic = 148, bpDiastolic = 98, fetalHeartRate = 124, hemoglobin = 8.0,
                symptoms = listOf("severe headache", "blurred vision", "reduced fetal movement"), riskLevel = RiskLevel.RED,
                notes = "Critical. Immediate hospital referral. Ambulance arranged.", dirty = false,
            ),

            // Anita Devi (anc-07) — 2 checkups, YELLOW
            AncCheckup(
                id = "chk-m07-01", patientId = "anc-07-anita-devi",
                date = "2026-02-10", weightKg = 65.0, fundalHeightCm = 21.0,
                bpSystolic = 118, bpDiastolic = 76, fetalHeartRate = 138, hemoglobin = 9.8,
                symptoms = listOf("swelling in feet"), riskLevel = RiskLevel.YELLOW,
                notes = "Mild anaemia and oedema. IFA given. Advised reduced salt intake.", dirty = false,
            ),
            AncCheckup(
                id = "chk-m07-02", patientId = "anc-07-anita-devi",
                date = "2026-03-28", weightKg = 68.0, fundalHeightCm = 27.0,
                bpSystolic = 122, bpDiastolic = 80, fetalHeartRate = 136, hemoglobin = 9.5,
                symptoms = listOf("swelling in feet", "fatigue"), riskLevel = RiskLevel.YELLOW,
                notes = "Hb marginally improved. Oedema persists. PHC referral given.", dirty = false,
            ),

            // Pooja Singh (anc-08) — 1 checkup, GREEN
            AncCheckup(
                id = "chk-m08-01", patientId = "anc-08-pooja-singh",
                date = "2026-03-20", weightKg = 48.0, fundalHeightCm = 10.0,
                bpSystolic = 106, bpDiastolic = 66, fetalHeartRate = 152, hemoglobin = 12.0,
                symptoms = listOf("nausea"), riskLevel = RiskLevel.GREEN,
                notes = "First trimester. Nausea normal. IFA started. Tetanus vaccination given.", dirty = false,
            ),

            // Savitri Bai (anc-09) — 2 checkups, YELLOW
            AncCheckup(
                id = "chk-m09-01", patientId = "anc-09-savitri-bai",
                date = "2026-02-16", weightKg = 70.0, fundalHeightCm = 23.0,
                bpSystolic = 124, bpDiastolic = 80, fetalHeartRate = 134, hemoglobin = 9.5,
                symptoms = emptyList(), riskLevel = RiskLevel.YELLOW,
                notes = "Third pregnancy. Hb low. IFA doubled. Advised nutritious diet.", dirty = false,
            ),
            AncCheckup(
                id = "chk-m09-02", patientId = "anc-09-savitri-bai",
                date = "2026-03-22", weightKg = 73.0, fundalHeightCm = 29.0,
                bpSystolic = 128, bpDiastolic = 84, fetalHeartRate = 130, hemoglobin = 9.2,
                symptoms = listOf("mild headache", "swelling in feet"), riskLevel = RiskLevel.YELLOW,
                notes = "BP rising. Hb not improving. Referral to PHC recommended.", dirty = false,
            ),

            // Lalita Kumari (anc-10) — 1 checkup, GREEN
            AncCheckup(
                id = "chk-m10-01", patientId = "anc-10-lalita-kumari",
                date = "2026-04-01", weightKg = 45.0, fundalHeightCm = 8.0,
                bpSystolic = 104, bpDiastolic = 64, fetalHeartRate = null, hemoglobin = 11.4,
                symptoms = listOf("nausea", "vomiting"), riskLevel = RiskLevel.GREEN,
                notes = "Early registration. All normal. IFA and calcium started. Counselling done.", dirty = false,
            ),
        )

        // ── Newborn patients ──────────────────────────────────────────────────────
        //
        // Next-due logic (HomeViewModel):
        //   DAY_1  → DAY_3  (+2 days)
        //   DAY_3  → DAY_7  (+4 days)
        //   DAY_7  → DAY_14 (+7 days)
        //   DAY_14 → DAY_28 (+14 days)
        //   DAY_28 → null   (terminal)
        //   WEEK_6 → null   (terminal)

        val NEWBORN_PATIENTS = listOf(

            // 11. Baby Arjun — son of Meena Devi, born 2026-04-04 (7d old), GREEN
            //     Last visit: Day-3 on 2026-04-07 → next due 2026-04-11 (DUE TODAY)
            NewbornPatient(
                id = "nb-11-arjun",
                ashaWorkerId = WORKER_ID,
                name = "Baby Arjun", nameHi = "बेबी अर्जुन",
                gender = "male",
                dateOfBirth = "2026-04-04",
                village = RAMPUR, villageHi = RAMPUR_HI,
                motherId = "anc-01-meena-devi",
                motherName = "Meena Devi", motherNameHi = "मीना देवी",
                birthWeightKg = 3.1, currentWeightKg = 3.05,
                riskLevel = RiskLevel.GREEN,
                dirty = false,
            ),

            // 12. Baby Rani — daughter of Radha Devi, born 2026-04-08 (3d old), GREEN
            //     Last visit: Day-1 on 2026-04-09 → next due 2026-04-11 (DUE TODAY)
            NewbornPatient(
                id = "nb-12-rani",
                ashaWorkerId = WORKER_ID,
                name = "Baby Rani", nameHi = "बेबी रानी",
                gender = "female",
                dateOfBirth = "2026-04-08",
                village = RAMPUR, villageHi = RAMPUR_HI,
                motherName = "Radha Devi", motherNameHi = "राधा देवी",
                birthWeightKg = 2.8, currentWeightKg = 2.75,
                riskLevel = RiskLevel.GREEN,
                dirty = false,
            ),

            // 13. Baby Vikram — son of Sita Devi, born 2026-03-28 (14d old), YELLOW (LBW 2.1kg)
            //     Last visit: Day-7 on 2026-04-04 → next due 2026-04-11 (DUE TODAY)
            NewbornPatient(
                id = "nb-13-vikram",
                ashaWorkerId = WORKER_ID,
                name = "Baby Vikram", nameHi = "बेबी विक्रम",
                gender = "male",
                dateOfBirth = "2026-03-28",
                village = KHANPUR, villageHi = KHANPUR_HI,
                motherName = "Sita Devi", motherNameHi = "सीता देवी",
                birthWeightKg = 2.1, currentWeightKg = 2.2,
                riskLevel = RiskLevel.YELLOW,
                dirty = false,
            ),

            // 14. Baby Lakshmi — daughter of Usha Devi, born 2026-03-14 (28d old), GREEN
            //     Last visit: Day-14 on 2026-03-28 → next due 2026-04-11 (DUE TODAY)
            NewbornPatient(
                id = "nb-14-lakshmi",
                ashaWorkerId = WORKER_ID,
                name = "Baby Lakshmi", nameHi = "बेबी लक्ष्मी",
                gender = "female",
                dateOfBirth = "2026-03-14",
                village = RAMPUR, villageHi = RAMPUR_HI,
                motherName = "Usha Devi", motherNameHi = "उषा देवी",
                birthWeightKg = 2.9, currentWeightKg = 3.15,
                riskLevel = RiskLevel.GREEN,
                dirty = false,
            ),

            // 15. Baby Preeti — daughter of Nirmala, born 2026-04-10 (1d old), RED (VLBW 1.8kg, preterm)
            //     Last visit: Day-1 on 2026-04-11 → next due 2026-04-13 (RED → urgent)
            NewbornPatient(
                id = "nb-15-preeti",
                ashaWorkerId = WORKER_ID,
                name = "Baby Preeti", nameHi = "बेबी प्रीति",
                gender = "female",
                dateOfBirth = "2026-04-10",
                village = KHANPUR, villageHi = KHANPUR_HI,
                motherName = "Nirmala Devi", motherNameHi = "निर्मला देवी",
                birthWeightKg = 1.8, currentWeightKg = 1.78,
                riskLevel = RiskLevel.RED,
                dirty = false,
            ),
        )

        // ── Newborn visits ────────────────────────────────────────────────────────

        val NEWBORN_VISITS = listOf(

            // Baby Arjun (nb-11) — Day-1 and Day-3 visits done
            NewbornVisit(
                id = "vis-nb11-01", patientId = "nb-11-arjun",
                date = "2026-04-05", visitDay = VisitDay.DAY_1,
                weightKg = 3.05,
                observations = listOf("active", "feeding well", "normal cord"),
                notes = "Good birth weight. Breastfeeding established.", riskLevel = RiskLevel.GREEN,
                dirty = false,
            ),
            NewbornVisit(
                id = "vis-nb11-02", patientId = "nb-11-arjun",
                date = "2026-04-07", visitDay = VisitDay.DAY_3,
                weightKg = 3.02,
                observations = listOf("good reflexes", "feeding well", "mild jaundice"),
                notes = "Physiological jaundice — normal at 3 days. Monitor. No treatment needed.", riskLevel = RiskLevel.GREEN,
                dirty = false,
            ),

            // Baby Rani (nb-12) — Day-1 visit done
            NewbornVisit(
                id = "vis-nb12-01", patientId = "nb-12-rani",
                date = "2026-04-09", visitDay = VisitDay.DAY_1,
                weightKg = 2.75,
                observations = listOf("alert", "feeding well", "normal cry"),
                notes = "Normal delivery. Birth weight acceptable. Mother counselled on exclusive breastfeeding.", riskLevel = RiskLevel.GREEN,
                dirty = false,
            ),

            // Baby Vikram (nb-13) — Day-1, Day-3, Day-7 visits done, YELLOW throughout
            NewbornVisit(
                id = "vis-nb13-01", patientId = "nb-13-vikram",
                date = "2026-03-29", visitDay = VisitDay.DAY_1,
                weightKg = 2.05,
                observations = listOf("low birth weight", "weak cry", "pale"),
                notes = "LBW baby. Referred to CHC for assessment. Kangaroo mother care advised.", riskLevel = RiskLevel.YELLOW,
                dirty = false,
            ),
            NewbornVisit(
                id = "vis-nb13-02", patientId = "nb-13-vikram",
                date = "2026-03-31", visitDay = VisitDay.DAY_3,
                weightKg = 2.08,
                observations = listOf("poor feeding", "mild jaundice", "low weight"),
                notes = "Weight marginally up. KMC continuing. Jaundice needs monitoring.", riskLevel = RiskLevel.YELLOW,
                dirty = false,
            ),
            NewbornVisit(
                id = "vis-nb13-03", patientId = "nb-13-vikram",
                date = "2026-04-04", visitDay = VisitDay.DAY_7,
                weightKg = 2.15,
                observations = listOf("improving feeding", "jaundice resolving"),
                notes = "Slow but steady weight gain. Jaundice improving. Continue KMC.", riskLevel = RiskLevel.YELLOW,
                dirty = false,
            ),

            // Baby Lakshmi (nb-14) — 4 visits, GREEN throughout
            NewbornVisit(
                id = "vis-nb14-01", patientId = "nb-14-lakshmi",
                date = "2026-03-15", visitDay = VisitDay.DAY_1,
                weightKg = 2.85,
                observations = listOf("alert", "feeding well", "normal cord"),
                notes = "Normal delivery. Good birth weight. BCG and OPV given.", riskLevel = RiskLevel.GREEN,
                dirty = false,
            ),
            NewbornVisit(
                id = "vis-nb14-02", patientId = "nb-14-lakshmi",
                date = "2026-03-17", visitDay = VisitDay.DAY_3,
                weightKg = 2.88,
                observations = listOf("good reflexes", "feeding well"),
                notes = "Physiological weight loss minimal. Excellent breastfeeding.", riskLevel = RiskLevel.GREEN,
                dirty = false,
            ),
            NewbornVisit(
                id = "vis-nb14-03", patientId = "nb-14-lakshmi",
                date = "2026-03-21", visitDay = VisitDay.DAY_7,
                weightKg = 2.92,
                observations = listOf("thriving", "good weight gain"),
                notes = "Weight regained to birth weight. All milestones on track.", riskLevel = RiskLevel.GREEN,
                dirty = false,
            ),
            NewbornVisit(
                id = "vis-nb14-04", patientId = "nb-14-lakshmi",
                date = "2026-03-28", visitDay = VisitDay.DAY_14,
                weightKg = 3.15,
                observations = listOf("active", "excellent feeding", "good eye contact"),
                notes = "Excellent weight gain. DPT and Hep-B given. Next visit at 28 days.", riskLevel = RiskLevel.GREEN,
                dirty = false,
            ),

            // Baby Preeti (nb-15) — Day-1 visit, RED (VLBW preterm)
            NewbornVisit(
                id = "vis-nb15-01", patientId = "nb-15-preeti",
                date = "2026-04-11", visitDay = VisitDay.DAY_1,
                weightKg = 1.78,
                observations = listOf("very low birth weight", "preterm 34 weeks", "poor feeding", "temperature instability"),
                notes = "VLBW preterm baby. Immediate hospital referral arranged. KMC initiated while awaiting transport.", riskLevel = RiskLevel.RED,
                dirty = false,
            ),
        )
    }
}
