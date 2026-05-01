package `in`.sakhi.app.download

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import `in`.sakhi.core.data.download.DownloadWorker
import `in`.sakhi.core.ui.theme.SakhiColors
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map

/**
 * Full-screen download UI shown when the Gemma 4 E2B model is not yet present.
 *
 * Tapping "Download Sakhi AI" enqueues a unique foreground DownloadWorker.
 * Progress is observed from WorkManager output data (PROGRESS key, 0-100).
 *
 * "Skip for now" lets the user proceed without the model — the app will use
 * MockInferenceEngine in debug or show limited functionality in release.
 *
 * The model URL and SHA-256 are baked into the build via BuildConfig fields
 * MODEL_DOWNLOAD_URL and MODEL_SHA256. Both are empty strings in debug
 * builds (MockInferenceEngine is used instead, so download never runs).
 */
private const val UNIQUE_WORK_NAME = "sakhi_model_download"

// ~2.58 GB displayed as a human-readable size hint
private const val MODEL_SIZE_LABEL = "~2.58 GB"

@Composable
fun DownloadScreen(
    onSkip: () -> Unit,
    onDownloadComplete: (modelPath: String) -> Unit
) {
    val context = LocalContext.current
    val workManager = remember { WorkManager.getInstance(context) }

    var progress by remember { mutableIntStateOf(0) }
    var isDownloading by remember { mutableStateOf(false) }
    var isFailed by remember { mutableStateOf(false) }

    // Observe WorkManager state for any in-progress or completed download
    val workInfos by workManager.getWorkInfosForUniqueWorkFlow(UNIQUE_WORK_NAME)
        .collectAsStateWithLifecycle(initialValue = emptyList())

    LaunchedEffect(workInfos) {
        val info = workInfos.firstOrNull() ?: return@LaunchedEffect
        when (info.state) {
            WorkInfo.State.RUNNING,
            WorkInfo.State.ENQUEUED -> {
                isDownloading = true
                isFailed = false
                progress = info.progress.getInt(DownloadWorker.PROGRESS, 0)
            }
            WorkInfo.State.SUCCEEDED -> {
                isDownloading = false
                val path = info.outputData.getString(DownloadWorker.KEY_MODEL_PATH)
                if (path != null) onDownloadComplete(path)
            }
            WorkInfo.State.FAILED -> {
                isDownloading = false
                isFailed = true
            }
            else -> Unit
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp)
    ) {
        // Sakhi wordmark
        Text(
            text = "Sakhi",
            fontSize = 40.sp,
            fontWeight = FontWeight.Bold,
            color = SakhiColors.Primary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "AI-powered maternal care assistant",
            fontSize = 15.sp,
            color = SakhiColors.TextSecondary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(48.dp))

        if (isDownloading) {
            Text(
                text = "Downloading Sakhi AI…",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = SakhiColors.TextPrimary
            )
            Spacer(modifier = Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { progress / 100f },
                modifier = Modifier.fillMaxWidth(),
                color = SakhiColors.Primary,
                trackColor = SakhiColors.Divider
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "$progress% of $MODEL_SIZE_LABEL",
                fontSize = 13.sp,
                color = SakhiColors.TextSecondary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Please keep the app open",
                fontSize = 12.sp,
                color = SakhiColors.TextSecondary
            )
        } else {
            if (isFailed) {
                Text(
                    text = "Download failed — check your connection",
                    fontSize = 14.sp,
                    color = SakhiColors.RedText,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            Text(
                text = "Download the Sakhi AI model to enable full clinical assessment support.",
                fontSize = 14.sp,
                color = SakhiColors.TextSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Size: $MODEL_SIZE_LABEL  ·  Wi-Fi recommended",
                fontSize = 12.sp,
                color = SakhiColors.TextSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = { enqueueDownload(workManager, context) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SakhiColors.Primary)
            ) {
                Text(
                    text = if (isFailed) "Retry Download" else "Download Sakhi AI ($MODEL_SIZE_LABEL)",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            TextButton(onClick = onSkip) {
                Text(
                    text = "Skip for now — use basic rules",
                    color = SakhiColors.TextSecondary,
                    fontSize = 14.sp
                )
            }
        }
    }
}

private fun enqueueDownload(workManager: WorkManager, context: Context) {
    // In debug builds, MODEL_DOWNLOAD_URL is empty — skip.
    val modelUrl = try {
        val cls = Class.forName("${context.packageName}.BuildConfig")
        cls.getField("MODEL_DOWNLOAD_URL").get(null) as? String ?: ""
    } catch (_: Exception) { "" }

    val sha256 = try {
        val cls = Class.forName("${context.packageName}.BuildConfig")
        cls.getField("MODEL_SHA256").get(null) as? String ?: ""
    } catch (_: Exception) { "" }

    if (modelUrl.isEmpty()) return   // debug build or URL not configured

    val request = OneTimeWorkRequestBuilder<DownloadWorker>()
        .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
        .setConstraints(
            Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
        )
        .setInputData(
            workDataOf(
                DownloadWorker.KEY_DOWNLOAD_URL to modelUrl,
                DownloadWorker.KEY_EXPECTED_SHA256 to sha256
            )
        )
        .build()

    workManager.enqueueUniqueWork(
        UNIQUE_WORK_NAME,
        ExistingWorkPolicy.KEEP,   // don't restart if already running
        request
    )
}
