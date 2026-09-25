package com.jeerovan.comfer.spatial

import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

internal enum class DepthModelState { MISSING, DOWNLOADING, READY, FAILED }

/** Independent of Settings' lifetime so leaving Settings never interrupts an opted-in download. */
internal object DepthModelDownload {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private val mutableState = MutableStateFlow(DepthModelState.MISSING)
    val state = mutableState.asStateFlow()

    suspend fun check(context: Context) = withContext(Dispatchers.IO) {
        val model = File(context.noBackupFilesDir, "spatial-models/$DEPTH_MODEL_SHA256.tflite")
        val available = model.isFile && runCatching { sha256(model) == DEPTH_MODEL_SHA256 }.getOrDefault(false)
        if (mutableState.value != DepthModelState.DOWNLOADING)
            mutableState.value = if (available) DepthModelState.READY else DepthModelState.MISSING
    }

    @Synchronized fun start(context: Context) {
        if (job?.isActive == true) return
        job = scope.launch {
            mutableState.value = DepthModelState.DOWNLOADING
            try {
                ensureDepthModel(context) {}
                mutableState.value = DepthModelState.READY
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutableState.value = DepthModelState.FAILED }
        }
    }
}
