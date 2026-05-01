package `in`.sakhi.core.network

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.ktor.http.HttpHeaders
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Supabase push/pull API for offline-first sync.
 *
 * Conflict resolution: last-write-wins on [last_modified_at] (Unix ms).
 * Server assigns canonical [server_id] on first upsert.
 * All tables have RLS: ASHA reads/writes only rows where asha_worker_id = auth.uid().
 *
 * Push: upserts dirty=1 records via PostgREST; returns server's last_modified_at and server_id.
 * Pull: fetches rows where last_modified_at > lastSyncTimestamp for the current worker.
 *
 * Supabase table names mirror Room entity names. [local_id] is the Room-side UUID used
 * as the conflict target on upsert (so the server doesn't create duplicate rows if the
 * same record is pushed twice before receiving a server_id).
 */
class SupabaseSyncApi(
    private val supabase: SupabaseClient,
    private val supabaseUrl: String,
    private val supabaseKey: String
) {

    // ── DTOs ────────────────────────────────────────────────────────────────────

    @Serializable
    data class AncPatientDto(
        @SerialName("local_id") val localId: String,
        @SerialName("server_id") val serverId: String? = null,
        @SerialName("asha_worker_id") val ashaWorkerId: String,
        @SerialName("name") val name: String,
        @SerialName("name_hi") val nameHi: String? = null,
        @SerialName("age") val age: Int,
        @SerialName("village") val village: String,
        @SerialName("village_hi") val villageHi: String? = null,
        @SerialName("lmp") val lmp: String? = null,
        @SerialName("gestational_weeks") val gestationalWeeks: Int? = null,
        @SerialName("gravida") val gravida: Int? = null,
        @SerialName("para") val para: Int? = null,
        @SerialName("risk_level") val riskLevel: String,
        @SerialName("phone") val phone: String? = null,
        @SerialName("abdm_id") val abdmId: String? = null,
        @SerialName("last_modified_at") val lastModifiedAt: Long,
    )

    @Serializable
    data class NewbornPatientDto(
        @SerialName("local_id") val localId: String,
        @SerialName("server_id") val serverId: String? = null,
        @SerialName("asha_worker_id") val ashaWorkerId: String,
        @SerialName("name") val name: String,
        @SerialName("name_hi") val nameHi: String? = null,
        @SerialName("gender") val gender: String,
        @SerialName("date_of_birth") val dateOfBirth: String,
        @SerialName("mother_id") val motherId: String? = null,
        @SerialName("mother_name") val motherName: String,
        @SerialName("mother_name_hi") val motherNameHi: String? = null,
        @SerialName("village") val village: String,
        @SerialName("village_hi") val villageHi: String? = null,
        @SerialName("birth_weight_kg") val birthWeightKg: Double,
        @SerialName("current_weight_kg") val currentWeightKg: Double? = null,
        @SerialName("risk_level") val riskLevel: String,
        @SerialName("last_modified_at") val lastModifiedAt: Long,
    )

    @Serializable
    data class AncCheckupDto(
        @SerialName("local_id") val localId: String,
        @SerialName("server_id") val serverId: String? = null,
        @SerialName("patient_local_id") val patientLocalId: String,
        @SerialName("date") val date: String,
        @SerialName("weight_kg") val weightKg: Double,
        @SerialName("fundal_height_cm") val fundalHeightCm: Double,
        @SerialName("bp_systolic") val bpSystolic: Int,
        @SerialName("bp_diastolic") val bpDiastolic: Int,
        @SerialName("fetal_heart_rate") val fetalHeartRate: Int? = null,
        @SerialName("hemoglobin") val hemoglobin: Double? = null,
        @SerialName("symptoms") val symptoms: List<String>,
        @SerialName("risk_level") val riskLevel: String,
        @SerialName("notes") val notes: String? = null,
        @SerialName("last_modified_at") val lastModifiedAt: Long,
    )

    @Serializable
    data class NewbornVisitDto(
        @SerialName("local_id") val localId: String,
        @SerialName("server_id") val serverId: String? = null,
        @SerialName("patient_local_id") val patientLocalId: String,
        @SerialName("date") val date: String,
        @SerialName("visit_day") val visitDay: String,
        @SerialName("weight_kg") val weightKg: Double,
        @SerialName("observations") val observations: List<String>,
        @SerialName("other_observations") val otherObservations: String? = null,
        @SerialName("risk_level") val riskLevel: String,
        @SerialName("notes") val notes: String? = null,
        @SerialName("last_modified_at") val lastModifiedAt: Long,
    )

    @Serializable
    data class AssessmentDto(
        @SerialName("local_id") val localId: String,
        @SerialName("server_id") val serverId: String? = null,
        @SerialName("checkup_local_id") val checkupLocalId: String,
        @SerialName("patient_local_id") val patientLocalId: String,
        @SerialName("patient_type") val patientType: String,
        @SerialName("risk_level") val riskLevel: String,
        @SerialName("risk_reason") val riskReason: String,
        @SerialName("what_sakhi_noticed") val whatSakhiNoticed: List<String>,
        @SerialName("what_to_tell_patient") val whatToTellPatient: String,
        @SerialName("what_to_do_next") val whatToDoNext: String,
        @SerialName("follow_up_date") val followUpDate: String? = null,
        @SerialName("is_offline") val isOffline: Boolean,
        @SerialName("created_at") val createdAt: Long,
        @SerialName("last_modified_at") val lastModifiedAt: Long,
    )

    /** Returned after a successful upsert — used to update local dirty/server_id state. */
    @Serializable
    data class UpsertResult(
        @SerialName("local_id") val localId: String,
        @SerialName("server_id") val serverId: String,
        @SerialName("last_modified_at") val lastModifiedAt: Long,
    )

    /** All changes pulled from server since lastSyncAt. */
    data class PullResult(
        val ancPatients: List<AncPatientDto> = emptyList(),
        val newbornPatients: List<NewbornPatientDto> = emptyList(),
        val ancCheckups: List<AncCheckupDto> = emptyList(),
        val newbornVisits: List<NewbornVisitDto> = emptyList(),
        val assessments: List<AssessmentDto> = emptyList(),
    )

    // ── Push ────────────────────────────────────────────────────────────────────

    suspend fun pushAncPatients(patients: List<AncPatientDto>): List<UpsertResult> {
        if (patients.isEmpty()) return emptyList()
        return supabase.from("anc_patients")
            .upsert(patients) { onConflict = "local_id" }
            .decodeList()
    }

    suspend fun pushNewbornPatients(patients: List<NewbornPatientDto>): List<UpsertResult> {
        if (patients.isEmpty()) return emptyList()
        return supabase.from("newborn_patients")
            .upsert(patients) { onConflict = "local_id" }
            .decodeList()
    }

    suspend fun pushAncCheckups(checkups: List<AncCheckupDto>): List<UpsertResult> {
        if (checkups.isEmpty()) return emptyList()
        return supabase.from("anc_checkups")
            .upsert(checkups) { onConflict = "local_id" }
            .decodeList()
    }

    suspend fun pushNewbornVisits(visits: List<NewbornVisitDto>): List<UpsertResult> {
        if (visits.isEmpty()) return emptyList()
        return supabase.from("newborn_visits")
            .upsert(visits) { onConflict = "local_id" }
            .decodeList()
    }

    suspend fun pushAssessments(assessments: List<AssessmentDto>): List<UpsertResult> {
        if (assessments.isEmpty()) return emptyList()
        return supabase.from("assessments")
            .upsert(assessments) { onConflict = "local_id" }
            .decodeList()
    }

    // ── Pull ────────────────────────────────────────────────────────────────────

    /**
     * Fetch all records for [userId] modified after [lastSyncAt] (Unix ms).
     * Uses RLS so the JWT in the session already scopes to the ASHA's own rows.
     */
    suspend fun pullChanges(userId: String, lastSyncAt: Long): PullResult {
        val ancPatients = supabase.from("anc_patients")
            .select {
                filter {
                    eq("asha_worker_id", userId)
                    FilterOperator.GT.let { op ->
                        filter("last_modified_at", op, lastSyncAt.toString())
                    }
                }
            }
            .decodeList<AncPatientDto>()

        val newborns = supabase.from("newborn_patients")
            .select {
                filter {
                    eq("asha_worker_id", userId)
                    FilterOperator.GT.let { op ->
                        filter("last_modified_at", op, lastSyncAt.toString())
                    }
                }
            }
            .decodeList<NewbornPatientDto>()

        val checkups = supabase.from("anc_checkups")
            .select {
                filter {
                    FilterOperator.GT.let { op ->
                        filter("last_modified_at", op, lastSyncAt.toString())
                    }
                }
            }
            .decodeList<AncCheckupDto>()

        val visits = supabase.from("newborn_visits")
            .select {
                filter {
                    FilterOperator.GT.let { op ->
                        filter("last_modified_at", op, lastSyncAt.toString())
                    }
                }
            }
            .decodeList<NewbornVisitDto>()

        val assessments = supabase.from("assessments")
            .select {
                filter {
                    FilterOperator.GT.let { op ->
                        filter("last_modified_at", op, lastSyncAt.toString())
                    }
                }
            }
            .decodeList<AssessmentDto>()

        return PullResult(ancPatients, newborns, checkups, visits, assessments)
    }

    // ── Account deletion ────────────────────────────────────────────────────────

    /**
     * Call the Supabase Edge Function `delete-account`.
     * The function verifies the JWT (auth.uid()), then calls admin.deleteUser()
     * which cascade-deletes all Supabase rows via FK ON DELETE CASCADE.
     *
     * Edge Function code (deploy separately):
     * ```typescript
     * import { createClient } from '@supabase/supabase-js'
     * Deno.serve(async (req) => {
     *   const supabase = createClient(Deno.env.get('SUPABASE_URL')!, Deno.env.get('SERVICE_ROLE_KEY')!)
     *   const jwt = req.headers.get('Authorization')?.replace('Bearer ', '') ?? ''
     *   const { data: { user } } = await supabase.auth.getUser(jwt)
     *   if (!user) return new Response('Unauthorized', { status: 401 })
     *   await supabase.auth.admin.deleteUser(user.id)
     *   return new Response('ok')
     * })
     * ```
     */
    suspend fun requestServerAccountDeletion() {
        val accessToken = supabase.auth.currentSessionOrNull()?.accessToken ?: return
        supabase.httpClient.post("$supabaseUrl/functions/v1/delete-account") {
            headers.append(HttpHeaders.Authorization, "Bearer $accessToken")
            headers.append("apikey", supabaseKey)
        }
    }
}
