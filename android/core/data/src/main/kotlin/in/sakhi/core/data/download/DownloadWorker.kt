package `in`.sakhi.core.data.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Downloads the Gemma 4 E2B .litertlm model file (~2.58 GB) with:
 * - Resumable download (Range: bytes=N-) — resumes from partial file on retry
 * - SHA-256 verification after completion (deletes and fails on mismatch)
 * - Foreground service notification with progress %
 * - Progress reported via WorkManager Data so DownloadScreen can observe it
 *
 * The worker is EXPEDITED so Android keeps it running. Only one download can be
 * in-flight at a time — the caller must enforce this via unique work.
 *
 * Output key: PROGRESS (Int 0-100), KEY_MODEL_PATH (String absolute path)
 *
 * On SHA-256 mismatch: deletes the corrupt file and returns Failure so WorkManager
 * can retry. The partial file is NOT preserved on mismatch.
 *
 * On network error mid-download: leaves the partial file in place (for Range resume),
 * returns Retry so WorkManager can schedule a re-attempt.
 */
@HiltWorker
class DownloadWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    companion object {
        const val KEY_DOWNLOAD_URL = "download_url"
        const val KEY_EXPECTED_SHA256 = "expected_sha256"
        const val KEY_MODEL_PATH = "model_path"
        const val PROGRESS = "progress"

        private const val MODEL_FILENAME = "gemma4-e2b.litertlm"
        private const val NOTIFICATION_CHANNEL_ID = "sakhi_download"
        private const val NOTIFICATION_ID = 42
        private const val BUFFER_SIZE = 64 * 1024   // 64 KB read buffer
    }

    override suspend fun doWork(): Result {
        val downloadUrl = inputData.getString(KEY_DOWNLOAD_URL)
            ?: return Result.failure(workDataOf("error" to "No download URL provided"))
        val expectedSha256 = inputData.getString(KEY_EXPECTED_SHA256) ?: ""

        val modelsDir = File(appContext.getExternalFilesDir(null), "models").also { it.mkdirs() }
        val targetFile = File(modelsDir, MODEL_FILENAME)

        setForeground(buildForegroundInfo(0))

        return try {
            downloadWithResume(url = downloadUrl, targetFile = targetFile)

            // Verify SHA-256 if provided
            if (expectedSha256.isNotEmpty()) {
                val actual = sha256Hex(targetFile)
                if (!actual.equals(expectedSha256, ignoreCase = true)) {
                    targetFile.delete()
                    return Result.failure(workDataOf("error" to "SHA-256 mismatch — corrupt download"))
                }
            }

            Result.success(workDataOf(KEY_MODEL_PATH to targetFile.absolutePath))
        } catch (e: Exception) {
            // Leave partial file in place so next attempt can resume
            Result.retry()
        }
    }

    /**
     * HTTP download with Range header resume support.
     * If [targetFile] already exists, sends `Range: bytes=<size>-` to resume.
     */
    private suspend fun downloadWithResume(url: String, targetFile: File) {
        val existingBytes = if (targetFile.exists()) targetFile.length() else 0L
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 30_000
            readTimeout    = 30_000
            if (existingBytes > 0) setRequestProperty("Range", "bytes=$existingBytes-")
        }

        connection.connect()

        val responseCode = connection.responseCode
        val resuming = responseCode == HttpURLConnection.HTTP_PARTIAL  // 206
        if (responseCode != HttpURLConnection.HTTP_OK && !resuming) {
            connection.disconnect()
            error("Unexpected HTTP response: $responseCode")
        }

        val totalBytes = if (resuming) {
            // Content-Range: bytes start-end/total → extract total
            connection.getHeaderField("Content-Range")
                ?.substringAfterLast('/')?.toLongOrNull()
                ?: (existingBytes + connection.contentLengthLong)
        } else {
            connection.contentLengthLong
        }

        val startOffset = if (resuming) existingBytes else 0L
        var downloaded = startOffset

        RandomAccessFile(targetFile, "rw").use { raf ->
            raf.seek(startOffset)
            val buffer = ByteArray(BUFFER_SIZE)
            connection.inputStream.use { input ->
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    raf.write(buffer, 0, bytesRead)
                    downloaded += bytesRead
                    val pct = if (totalBytes > 0) (downloaded * 100 / totalBytes).toInt() else 0
                    setProgress(workDataOf(PROGRESS to pct))
                    setForeground(buildForegroundInfo(pct))
                }
            }
        }
        connection.disconnect()
    }

    private fun sha256Hex(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(BUFFER_SIZE)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo = buildForegroundInfo(0)

    private fun buildForegroundInfo(progressPct: Int): ForegroundInfo {
        ensureNotificationChannel()
        val notification = NotificationCompat.Builder(appContext, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Downloading Sakhi AI")
            .setContentText("$progressPct% — please keep the app open")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, progressPct, progressPct == 0)
            .setOngoing(true)
            .setSilent(true)
            .build()
        return ForegroundInfo(NOTIFICATION_ID, notification)
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Model Download",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Sakhi AI model download progress" }
            appContext.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }
}
