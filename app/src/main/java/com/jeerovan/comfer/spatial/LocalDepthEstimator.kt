package com.jeerovan.comfer.spatial

import android.content.Context
import android.graphics.Bitmap
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import org.tensorflow.lite.Interpreter
import java.io.File
import java.io.IOException
import java.net.URL
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

internal const val DEPTH_MODEL_REVISION = "178427e448dbf4da93b1e7b1b2abc103ad329bd6"
internal const val DEPTH_MODEL_SHA256 = "f74509422e4a9270a354b249a9193abdd4903354be63701262238a7f4b869611"
internal const val DEPTH_MODEL_URL = "https://huggingface.co/litert-community/depth-anything-v2-small/resolve/$DEPTH_MODEL_REVISION/tflite/depth_anything_v2_small_wi8_afp32.tflite"

internal fun sha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().buffered().use { stream ->
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val count = stream.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

private val modelDownloadMutex = kotlinx.coroutines.sync.Mutex()

internal suspend fun ensureDepthModel(context: Context, progress: (Float?) -> Unit): File {
    modelDownloadMutex.lock()
    try { return downloadDepthModel(context, progress) }
    finally { modelDownloadMutex.unlock() }
}

/** Invoked only after local spatial opt-in. Downloads model weights, never image data. */
private suspend fun downloadDepthModel(context: Context, progress: (Float?) -> Unit): File {
    val directory = File(context.noBackupFilesDir, "spatial-models").apply { mkdirs() }
    val model = File(directory, "$DEPTH_MODEL_SHA256.tflite")
    if (model.isFile && sha256(model) == DEPTH_MODEL_SHA256) return model
    val temporary = File(directory, "depth.download")
    val connection = URL(DEPTH_MODEL_URL).openConnection().apply {
        connectTimeout = 20_000
        readTimeout = 30_000
    }
    try {
        connection.getInputStream().use { input ->
            temporary.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024)
                var count = 0L
                val length = connection.contentLengthLong
                val started = android.os.SystemClock.elapsedRealtime()
                while (true) {
                    currentCoroutineContext().ensureActive()
                    if (android.os.SystemClock.elapsedRealtime() - started > 300_000) throw IOException("Model download timed out")
                    val read = input.read(buffer)
                    if (read < 0) break
                    count += read
                    if (count > 32L * 1024 * 1024) throw IOException("Model exceeds size limit")
                    output.write(buffer, 0, read)
                    progress(if (length > 0) (count.toFloat() / length).coerceIn(0f, 1f) else null)
                }
            }
        }
        check(sha256(temporary) == DEPTH_MODEL_SHA256) { "Depth model checksum mismatch" }
        check(temporary.renameTo(model)) { "Could not store depth model" }
        return model
    } finally {
        temporary.delete()
        (connection as? java.net.HttpURLConnection)?.disconnect()
    }
}

/** Serialized by the repository, off the UI thread. Releases native model memory after one image. */
internal fun estimateDepth(model: File, bitmap: Bitmap): SpatialDepth {
    val width = 686; val height = 518
    val resized = Bitmap.createScaledBitmap(bitmap, width, height, true)
    val pixels = IntArray(width * height)
    resized.getPixels(pixels, 0, width, 0, 0, width, height)
    if (resized !== bitmap) resized.recycle()
    val input = ByteBuffer.allocateDirect(3 * width * height * 4).order(ByteOrder.nativeOrder())
    val mean = floatArrayOf(0.485f, 0.456f, 0.406f)
    val deviation = floatArrayOf(0.229f, 0.224f, 0.225f)
    for (channel in 0..2) for (pixel in pixels) {
        input.putFloat((((pixel shr (16 - channel * 8)) and 255) / 255f - mean[channel]) / deviation[channel])
    }
    input.rewind()
    val output = ByteBuffer.allocateDirect(width * height * 4).order(ByteOrder.nativeOrder())
    Interpreter(model, Interpreter.Options().setNumThreads(2)).use { interpreter ->
        require(interpreter.getInputTensor(0).shape().contentEquals(intArrayOf(1, 3, height, width)))
        require(interpreter.getOutputTensor(0).numElements() == width * height)
        interpreter.run(input, output)
    }
    output.rewind()
    val values = FloatArray(width * height)
    output.asFloatBuffer().get(values)
    return SpatialDepth.normalize(width, height, values)
}

// Wait until native processing releases the input, even if the caller is cancelled meanwhile.
// A cancellable Task.await followed by bitmap.recycle() can free a still-in-use ML input.
private suspend fun <T> Task<T>.awaitCompletion(): T = suspendCoroutine { continuation ->
    addOnCompleteListener { task ->
        if (task.isSuccessful) continuation.resume(task.result)
        else continuation.resumeWithException(task.exception ?: IOException("Subject processing cancelled"))
    }
}

internal suspend fun segmentSubject(context: Context, bitmap: Bitmap): FloatArray? {
    val segmenter = SubjectSegmentation.getClient(SubjectSegmenterOptions.Builder()
        .enableForegroundConfidenceMask().build())
    try {
        val modules = ModuleInstall.getClient(context)
        if (!modules.areModulesAvailable(segmenter).awaitCompletion().areModulesAvailable()) {
            modules.installModules(ModuleInstallRequest.newBuilder().addApi(segmenter).build()).awaitCompletion()
            // Bounded first-use wait. Missing Play services/network falls back to the depth mesh.
            var available = false
            repeat(60) {
                if (!available) {
                    delay(1_000)
                    available = modules.areModulesAvailable(segmenter).awaitCompletion().areModulesAvailable()
                }
            }
            if (!available) return null
        }
        currentCoroutineContext().ensureActive()
        val result = segmenter.process(InputImage.fromBitmap(bitmap, 0)).awaitCompletion()
        currentCoroutineContext().ensureActive()
        val buffer = result.foregroundConfidenceMask ?: return null
        if (buffer.capacity() != bitmap.width * bitmap.height) return null
        val values = FloatArray(buffer.capacity()) { buffer.get(it).coerceIn(0f, 1f) }
        if (values.any { !it.isFinite() }) return null
        val fraction = values.count { it > 0.5f }.toFloat() / values.size
        return values.takeIf { fraction in 0.01f..0.85f }
    } finally {
        segmenter.close()
    }
}
