package com.masselis.tpmsadvanced.feature.qrcode.interfaces

import android.Manifest.permission.CAMERA
import android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
import android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
import androidx.activity.compose.LocalActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraSelector.DEFAULT_BACK_CAMERA
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.core.resolutionselector.ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.masselis.tpmsadvanced.core.ui.LocalHomeNavController
import com.masselis.tpmsadvanced.core.ui.MissingPermission
import com.masselis.tpmsadvanced.feature.qrcode.interfaces.QRCodeViewModel.Event
import com.masselis.tpmsadvanced.feature.qrcode.model.QrCodeResult
import com.masselis.tpmsadvanced.feature.qrcode.ioc.Bindings.Companion.QrCodeViewModel


@OptIn(ExperimentalPermissionsApi::class)
@Composable
/** Scans a QR code, then [onFound] with what it holds, see [QrCodeResultDialog] */
public fun QrCodeScan(
    snackbarHostState: SnackbarHostState,
    onFound: (QrCodeResult) -> Unit,
    modifier: Modifier = Modifier,
) {
    // The scanner's layout isn't made for landscape, so the screen stays in portrait while it's shown
    LocalActivity.current?.also { activity ->
        DisposableEffect(activity) {
            activity.requestedOrientation = SCREEN_ORIENTATION_PORTRAIT
            onDispose {
                // Rotating to portrait recreates the activity: the new one locks it again, so only unlock on leave
                if (activity.isChangingConfigurations.not())
                    activity.requestedOrientation = SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
    }
    val permissionState = rememberMultiplePermissionsState(listOf(CAMERA))
    when {
        permissionState.allPermissionsGranted.not() -> MissingPermission(
            text = "Persistent TPMS need you to approve a permission to scan the QR Code",
            refusedText = "This app requires camera permission to scan QR, please enable it in settings",
            permissionState = permissionState,
            autoRequest = true,
            modifier = modifier,
        )

        else -> Preview(
            snackbarHostState = snackbarHostState,
            onFound = onFound,
            modifier = modifier,
        )
    }
}

@Composable
private fun Preview(
    snackbarHostState: SnackbarHostState,
    onFound: (QrCodeResult) -> Unit,
    modifier: Modifier = Modifier,
    cameraSelector: CameraSelector = DEFAULT_BACK_CAMERA,
) {
    val controller = LocalContext.current
        .let { context ->
            remember {
                LifecycleCameraController(context).apply {
                    this.cameraSelector = cameraSelector
                    // The default 640x480 leaves a module of a small code, like the one etched on
                    // a Wicarlink cap, a pixel or two wide: decoded only once in a while
                    imageAnalysisResolutionSelector = ResolutionSelector
                        .Builder()
                        .setResolutionStrategy(
                            ResolutionStrategy(android.util.Size(1920, 1080), FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)
                        )
                        .build()
                }
            }
        }
        .also { controller ->
            LocalLifecycleOwner.current.also { lifecycleOwner ->
                DisposableEffect(controller) {
                    controller.bindToLifecycle(lifecycleOwner)
                    onDispose { controller.unbind() }
                }
            }
        }

    val viewModel = remember(controller) { QrCodeViewModel(controller) }
    val isUnknownCode by viewModel.isUnknownCode.collectAsState()
    Box(modifier) {
        AndroidView(
            { context -> PreviewView(context).apply { this.controller = controller } },
            Modifier.fillMaxSize()
        )
        QrCodeOverlay(isUnknownCode, Modifier.fillMaxSize())
    }

    val navController = LocalHomeNavController.current
    LaunchedEffect(viewModel) {
        for (event in viewModel.eventChannel) {
            when (event) {
                is Event.Found -> onFound(event.result)

                Event.LeaveBecauseCameraUnavailable -> {
                    snackbarHostState.showSnackbar("Your device should to have a camera to continue")
                    navController.popBackStack()
                }
            }
        }
    }
}

@Composable
private fun QrCodeOverlay(
    isUnknownCode: Boolean,
    modifier: Modifier = Modifier
) {
    Box(modifier) {
        // The usual scanner look: the preview dimmed around a clear square, its corners marked
        Canvas(
            Modifier
                .fillMaxSize()
                // Offscreen, so the clear square cuts through the dim rather than painting over it
                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
        ) {
            val side = size.minDimension * WINDOW_FRACTION
            val topLeft = Offset((size.width - side) / 2, (size.height - side) / 2)
            val radius = CornerRadius(CORNER_RADIUS.toPx())
            drawRect(Color.Black.copy(alpha = DIM_ALPHA))
            drawRoundRect(Color.Transparent, topLeft, Size(side, side), radius, blendMode = BlendMode.Clear)
            val stroke = BRACKET_WIDTH.toPx()
            val arm = side * BRACKET_FRACTION
            // Each corner: its position, and which way its arms go
            listOf(
                topLeft to Offset(1f, 1f),
                topLeft + Offset(side, 0f) to Offset(-1f, 1f),
                topLeft + Offset(0f, side) to Offset(1f, -1f),
                topLeft + Offset(side, side) to Offset(-1f, -1f),
            ).forEach { (corner, direction) ->
                drawLine(Color.White, corner, corner + Offset(arm * direction.x, 0f), stroke, StrokeCap.Round)
                drawLine(Color.White, corner, corner + Offset(0f, arm * direction.y), stroke, StrokeCap.Round)
            }
        }
        Text(
            text = if (isUnknownCode) "Not a known TPMS QR code" else "Point the camera at the sensors' QR code",
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 32.dp, vertical = 48.dp),
        )
    }
}

private const val WINDOW_FRACTION = .7f
private const val BRACKET_FRACTION = .15f
private const val DIM_ALPHA = .55f
private val CORNER_RADIUS = 4.dp
private val BRACKET_WIDTH = 4.dp
