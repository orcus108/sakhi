package `in`.sakhi.core.data.db.entity

import `in`.sakhi.core.domain.model.AncCheckup
import `in`.sakhi.core.domain.model.AncPatient
import `in`.sakhi.core.domain.model.AssessmentResult
import `in`.sakhi.core.domain.model.AuditEntry
import `in`.sakhi.core.domain.model.ChatMessage
import `in`.sakhi.core.domain.model.ChatRole
import `in`.sakhi.core.domain.model.NewbornPatient
import `in`.sakhi.core.domain.model.NewbornVisit
import `in`.sakhi.core.domain.model.RiskLevel
import `in`.sakhi.core.domain.model.SyncQueueEntry
import `in`.sakhi.core.domain.model.VisitDay
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val json = Json { ignoreUnknownKeys = true }

// ── AncPatient ────────────────────────────────────────────────────────────────

fun AncPatientEntity.toDomain(checkupHistory: List<AncCheckup> = emptyList()): AncPatient =
    AncPatient(
        id = id, serverId = serverId, ashaWorkerId = ashaWorkerId, abdmId = abdmId,
        name = name, nameHi = nameHi, age = age, village = village, villageHi = villageHi,
        lmp = lmp, gestationalWeeks = gestationalWeeks, gravida = gravida, para = para,
        riskLevel = RiskLevel.from(riskLevel), phone = phone, checkupHistory = checkupHistory,
        lastModifiedAt = lastModifiedAt, lastSyncedAt = lastSyncedAt, dirty = dirty == 1
    )

fun AncPatient.toEntity(): AncPatientEntity =
    AncPatientEntity(
        id = id, serverId = serverId, ashaWorkerId = ashaWorkerId, abdmId = abdmId,
        name = name, nameHi = nameHi, age = age, village = village, villageHi = villageHi,
        lmp = lmp, gestationalWeeks = gestationalWeeks, gravida = gravida, para = para,
        riskLevel = riskLevel.key, phone = phone,
        lastModifiedAt = lastModifiedAt, lastSyncedAt = lastSyncedAt, dirty = if (dirty) 1 else 0
    )

// ── NewbornPatient ────────────────────────────────────────────────────────────

fun NewbornPatientEntity.toDomain(visitHistory: List<NewbornVisit> = emptyList()): NewbornPatient =
    NewbornPatient(
        id = id, serverId = serverId, ashaWorkerId = ashaWorkerId, abdmId = abdmId,
        name = name, nameHi = nameHi, gender = gender, dateOfBirth = dateOfBirth,
        village = village, villageHi = villageHi, motherId = motherId,
        motherName = motherName, motherNameHi = motherNameHi,
        birthWeightKg = birthWeightKg, currentWeightKg = currentWeightKg,
        riskLevel = RiskLevel.from(riskLevel), visitHistory = visitHistory,
        lastModifiedAt = lastModifiedAt, lastSyncedAt = lastSyncedAt, dirty = dirty == 1
    )

fun NewbornPatient.toEntity(): NewbornPatientEntity =
    NewbornPatientEntity(
        id = id, serverId = serverId, ashaWorkerId = ashaWorkerId, abdmId = abdmId,
        name = name, nameHi = nameHi, gender = gender, dateOfBirth = dateOfBirth,
        village = village, villageHi = villageHi, motherId = motherId,
        motherName = motherName, motherNameHi = motherNameHi,
        birthWeightKg = birthWeightKg, currentWeightKg = currentWeightKg,
        riskLevel = riskLevel.key,
        lastModifiedAt = lastModifiedAt, lastSyncedAt = lastSyncedAt, dirty = if (dirty) 1 else 0
    )

// ── AncCheckup ────────────────────────────────────────────────────────────────

fun AncCheckupEntity.toDomain(): AncCheckup =
    AncCheckup(
        id = id, patientId = patientId, serverId = serverId, date = date,
        weightKg = weightKg, fundalHeightCm = fundalHeightCm,
        bpSystolic = bpSystolic, bpDiastolic = bpDiastolic,
        fetalHeartRate = fetalHeartRate, hemoglobin = hemoglobin,
        symptoms = json.decodeFromString(symptoms),
        riskLevel = RiskLevel.from(riskLevel), notes = notes,
        lastModifiedAt = lastModifiedAt, lastSyncedAt = lastSyncedAt, dirty = dirty == 1
    )

fun AncCheckup.toEntity(): AncCheckupEntity =
    AncCheckupEntity(
        id = id, patientId = patientId, serverId = serverId, date = date,
        weightKg = weightKg, fundalHeightCm = fundalHeightCm,
        bpSystolic = bpSystolic, bpDiastolic = bpDiastolic,
        fetalHeartRate = fetalHeartRate, hemoglobin = hemoglobin,
        symptoms = json.encodeToString(symptoms),
        riskLevel = riskLevel.key, notes = notes,
        lastModifiedAt = lastModifiedAt, lastSyncedAt = lastSyncedAt, dirty = if (dirty) 1 else 0
    )

// ── NewbornVisit ──────────────────────────────────────────────────────────────

fun NewbornVisitEntity.toDomain(): NewbornVisit =
    NewbornVisit(
        id = id, patientId = patientId, serverId = serverId, date = date,
        visitDay = VisitDay.from(visitDay), weightKg = weightKg,
        observations = json.decodeFromString(observations),
        otherObservations = otherObservations, notes = notes,
        riskLevel = RiskLevel.from(riskLevel),
        lastModifiedAt = lastModifiedAt, lastSyncedAt = lastSyncedAt, dirty = dirty == 1
    )

fun NewbornVisit.toEntity(): NewbornVisitEntity =
    NewbornVisitEntity(
        id = id, patientId = patientId, serverId = serverId, date = date,
        visitDay = visitDay.key, weightKg = weightKg,
        observations = json.encodeToString(observations),
        otherObservations = otherObservations, notes = notes,
        riskLevel = riskLevel.key,
        lastModifiedAt = lastModifiedAt, lastSyncedAt = lastSyncedAt, dirty = if (dirty) 1 else 0
    )

// ── Assessment ────────────────────────────────────────────────────────────────

fun AssessmentEntity.toDomain(): AssessmentResult =
    AssessmentResult(
        id = id, checkupId = checkupId, patientId = patientId, patientType = patientType,
        serverId = serverId, riskLevel = RiskLevel.from(riskLevel),
        riskReason = riskReason,
        whatSakhiNoticed = json.decodeFromString(whatSakhiNoticed),
        whatToTellPatient = whatToTellPatient, whatToDoNext = whatToDoNext,
        followUpDate = followUpDate, isOffline = isOffline == 1,
        createdAt = createdAt, lastSyncedAt = lastSyncedAt, dirty = dirty == 1
    )

fun AssessmentResult.toEntity(): AssessmentEntity =
    AssessmentEntity(
        id = id, checkupId = checkupId, patientId = patientId, patientType = patientType,
        serverId = serverId, riskLevel = riskLevel.key,
        riskReason = riskReason,
        whatSakhiNoticed = json.encodeToString(whatSakhiNoticed),
        whatToTellPatient = whatToTellPatient, whatToDoNext = whatToDoNext,
        followUpDate = followUpDate, isOffline = if (isOffline) 1 else 0,
        createdAt = createdAt, lastSyncedAt = lastSyncedAt, dirty = if (dirty) 1 else 0
    )

// ── ChatMessage ───────────────────────────────────────────────────────────────

fun ChatMessageEntity.toDomain(): ChatMessage =
    ChatMessage(
        id = id, sessionId = sessionId, patientId = patientId, patientType = patientType,
        role = if (role == "user") ChatRole.USER else ChatRole.ASSISTANT,
        content = content, refer = refer == 1, createdAt = createdAt
    )

fun ChatMessage.toEntity(): ChatMessageEntity =
    ChatMessageEntity(
        id = id, sessionId = sessionId, patientId = patientId, patientType = patientType,
        role = role.name.lowercase(), content = content,
        refer = if (refer) 1 else 0, createdAt = createdAt
    )

// ── SyncQueueEntry ────────────────────────────────────────────────────────────

fun SyncQueueEntity.toDomain(): SyncQueueEntry =
    SyncQueueEntry(
        id = id, entityType = entityType, entityId = entityId,
        operation = operation, payload = payload,
        retryCount = retryCount, lastAttemptAt = lastAttemptAt, createdAt = createdAt
    )

fun SyncQueueEntry.toEntity(): SyncQueueEntity =
    SyncQueueEntity(
        id = id, entityType = entityType, entityId = entityId,
        operation = operation, payload = payload,
        retryCount = retryCount, lastAttemptAt = lastAttemptAt, createdAt = createdAt
    )

// ── AuditEntry ────────────────────────────────────────────────────────────────

fun AuditLogEntity.toDomain(): AuditEntry =
    AuditEntry(
        id = id, ashaWorkerId = ashaWorkerId, action = action,
        entityType = entityType, entityId = entityId,
        createdAt = createdAt, synced = synced == 1
    )

fun AuditEntry.toEntity(): AuditLogEntity =
    AuditLogEntity(
        id = id, ashaWorkerId = ashaWorkerId, action = action,
        entityType = entityType, entityId = entityId,
        createdAt = createdAt, synced = if (synced) 1 else 0
    )
