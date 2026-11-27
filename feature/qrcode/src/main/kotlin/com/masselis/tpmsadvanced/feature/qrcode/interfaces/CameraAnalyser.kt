package com.masselis.tpmsadvanced.feature.qrcode.interfaces

import android.net.Uri
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.view.CameraController
import co.touchlab.kermit.Logger
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.ZoomSuggestionOptions
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.masselis.tpmsadvanced.core.common.appContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import kotlin.time.Duration.Companion.milliseconds

/** Finds the QR codes, and the text around them, the camera or a picture shows */
internal class CameraAnalyser {

    /** What a camera frame or a picture shows: its QR codes' values, and its text, line by line and word by word */
    data class Seen(val codes: List<String>, val texts: List<String>)

    private val executor = Executors.newCachedThreadPool()

    /**
     * What each camera frame shows. Every frame is searched for QR codes, but reading its text
     * takes several times longer: only a frame every [TEXT_INTERVAL] is, so the codes are still
     * searched for as often. Without its text, a frame's [Seen.texts] is empty.
     */
    @androidx.annotation.OptIn(ExperimentalGetImage::class)
    fun analyse(controller: CameraController): Flow<Seen> = callbackFlow {
        // Zooms in on a code too small to be decoded, as the stock camera does: a phone without
        // a macro lens can't get close enough to a small one while keeping it sharp
        val scanner = ZoomSuggestionOptions
            .Builder { ratio ->
                launch(Dispatchers.Main) { controller.setZoomRatio(ratio) }
                true
            }
            .setMaxSupportedZoomRatio(
                controller.cameraInfo?.zoomState?.value?.maxZoomRatio ?: DEFAULT_MAX_ZOOM_RATIO
            )
            .build()
            .let { zoom ->
                BarcodeScannerOptions
                    .Builder()
                    .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                    .setZoomSuggestionOptions(zoom)
                    .build()
            }
            .let { BarcodeScanning.getClient(it) }
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        // In the frames' own clock, nanoseconds
        var textReadAt: Long? = null
        try {
            // Frames come one at a time on the executor, which waits for each to be read: the
            // camera drops the ones arriving meanwhile
            controller.setImageAnalysisAnalyzer(executor) { proxy ->
                proxy.use {
                    val media = proxy.image ?: return@use
                    val image = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
                    val readsText = textReadAt
                        ?.let { proxy.imageInfo.timestamp - it >= TEXT_INTERVAL.inWholeNanoseconds }
                        ?: true
                    if (readsText) textReadAt = proxy.imageInfo.timestamp
                    Seen(
                        codes = scanner.process(image).awaitOrNull()?.values().orEmpty(),
                        texts = recognizer
                            .takeIf { readsText }
                            ?.process(image)
                            ?.awaitOrNull()
                            ?.words()
                            .orEmpty(),
                    ).also { trySend(it) }
                }
            }
            // Fired by `CameraSelector.select(LinkedHashSet<CameraInternal> cameras)`
        } catch (exc: IllegalArgumentException) {
            throw CameraUnavailable(exc)
        }
        awaitClose {
            controller.clearImageAnalysisAnalyzer()
            scanner.close()
            recognizer.close()
        }
    }.flowOn(Dispatchers.Main.immediate)

    /** What the picture [uri] shows, nothing when it can't be read */
    suspend fun read(uri: Uri): Seen = withContext(Dispatchers.IO) {
        try {
            // Turned upright from its EXIF orientation, as the gallery shows it
            InputImage.fromFilePath(appContext, uri)
        } catch (exc: IOException) {
            // A picture removed or not shared anymore: the others are still read
            logger.w(exc) { "Picture unreadable: $uri" }
            null
        }
            ?.let { image ->
                BarcodeScanning
                    .getClient(BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build())
                    .use { scanner ->
                        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS).use { recognizer ->
                            Seen(
                                codes = scanner.process(image).awaitOrNull()?.values().orEmpty(),
                                texts = recognizer.process(image).awaitOrNull()?.words().orEmpty(),
                            )
                        }
                    }
            }
            ?: Seen(emptyList(), emptyList())
    }

    // With zoom suggestions on, a code found but not decoded yet comes with an empty value, until
    // zoomed in enough
    private fun List<Barcode>.values() = mapNotNull { barcode -> barcode.rawValue?.takeIf { it.isNotEmpty() } }

    // Both, since a printed ID can be read as a word of a longer line or split into several words
    private fun Text.words() = textBlocks
        .flatMap { it.lines }
        .flatMap { line -> listOf(line.text) + line.elements.map { it.text } }

    /** Blocks until done, on a thread that isn't the main one. Null when it failed: the next frame is tried. */
    private fun <T> Task<T>.awaitOrNull(): T? = try {
        Tasks.await(this)
    } catch (exc: ExecutionException) {
        logger.w(exc) { "Analysis failed" }
        null
    } catch (_: CancellationException) {
        // The scan ended with a frame still being read: its detectors closed, the frame is dropped
        null
    }

    private companion object {
        val logger = Logger.withTag("CameraAnalyser")

        // Until the camera tells its own, a zoom most phones reach
        const val DEFAULT_MAX_ZOOM_RATIO = 5f

        val TEXT_INTERVAL = 500.milliseconds
    }

    class CameraUnavailable(source: IllegalArgumentException) : IllegalArgumentException(source)
}
