package com.masselis.tpmsadvanced.feature.qrcode.interfaces

import androidx.camera.core.ImageAnalysis
import androidx.camera.mlkit.vision.MlKitAnalyzer
import androidx.camera.view.CameraController
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.ZoomSuggestionOptions
import com.google.mlkit.vision.barcode.common.Barcode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

@Suppress("OPT_IN_USAGE")
internal class CameraAnalyser {

    private val executor = Executors.newCachedThreadPool()

    fun findQrCode(controller: CameraController) =
        callbackFlow<List<Barcode>> {
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
            try {
                controller.setImageAnalysisAnalyzer(
                    executor,
                    MlKitAnalyzer(
                        listOf(scanner),
                        ImageAnalysis.COORDINATE_SYSTEM_VIEW_REFERENCED,
                        executor
                    ) {
                        it.getValue(scanner)?.also { barcodes -> launch { send(barcodes) } }
                    }
                )
                // Fired by `CameraSelector.select(LinkedHashSet<CameraInternal> cameras)`
            } catch (exc: IllegalArgumentException) {
                throw CameraUnavailable(exc)
            }
            awaitClose {
                controller.clearImageAnalysisAnalyzer()
                scanner.close()
            }
        }.flowOn(Dispatchers.Main.immediate)
            .flatMapConcat { it.asFlow() }
            // With zoom suggestions on, a code found but not decoded yet comes with an empty value,
            // until zoomed in enough
            .mapNotNull { barcode -> barcode.rawValue?.takeIf { it.isNotEmpty() } }

    private companion object {
        // Until the camera tells its own, a zoom most phones reach
        const val DEFAULT_MAX_ZOOM_RATIO = 5f
    }

    class CameraUnavailable(source: IllegalArgumentException) : IllegalArgumentException(source)
}
