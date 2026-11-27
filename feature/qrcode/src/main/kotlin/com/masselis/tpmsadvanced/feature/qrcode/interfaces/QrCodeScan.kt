@file:Suppress("MaxLineLength")

package com.masselis.tpmsadvanced.feature.qrcode.interfaces

import android.Manifest.permission.CAMERA
import android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
import android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickMultipleVisualMedia
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraSelector.DEFAULT_BACK_CAMERA
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.core.resolutionselector.ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.masselis.tpmsadvanced.core.common.R as CommonR
import com.masselis.tpmsadvanced.core.common.localized
import com.masselis.tpmsadvanced.core.ui.LocalHomeNavController
import com.masselis.tpmsadvanced.core.ui.MissingPermission
import com.masselis.tpmsadvanced.data.vehicle.model.SensorBrand.WICARLINK
import com.masselis.tpmsadvanced.feature.main.interfaces.composable.asSensorId
import com.masselis.tpmsadvanced.feature.qrcode.interfaces.QRCodeViewModel.Event
import com.masselis.tpmsadvanced.feature.qrcode.ioc.Bindings.Companion.QrCodeViewModel
import com.masselis.tpmsadvanced.feature.qrcode.model.QrCodeResult


@OptIn(ExperimentalPermissionsApi::class)
@Suppress("LongMethod", "CyclomaticComplexMethod")
@Composable
/**
 * Scans a QR code, then [onFound] with what it holds, see [QrCodeResultDialog]. The sensor ID can be
 * typed in instead, or the code read from pictures, the camera allowed or not.
 */
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
    val controller = if (permissionState.allPermissionsGranted) rememberCameraController() else null
    val viewModel = remember(controller) { QrCodeViewModel(controller) }
    val isUnknownCode by viewModel.isUnknownCode.collectAsState()
    val isReading by viewModel.isReading.collectAsState()
    val choices by viewModel.choices.collectAsState()
    val suggestedId by viewModel.suggestedId.collectAsState()
    var isEnteringId by rememberSaveable { mutableStateOf(false) }
    val pickPictures = rememberLauncherForActivityResult(PickMultipleVisualMedia()) { viewModel.read(it) }

    Box(modifier) {
        controller
            ?.also { controller ->
                AndroidView(
                    { context -> PreviewView(context).apply { this.controller = controller } },
                    Modifier.fillMaxSize()
                )
                QrCodeOverlay(Modifier.fillMaxSize())
            }
            ?: MissingPermission(
                text = localized(CommonR.string.persistent_tpms_need_you_to_approve_a),
                refusedText = localized(CommonR.string.this_app_requires_camera_permission_to_scan),
                permissionState = permissionState,
                autoRequest = true,
                modifier = Modifier.fillMaxSize(),
            )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 32.dp, vertical = 48.dp),
        ) {
            if (controller != null) Text(
                text = if (isUnknownCode) stringResource(CommonR.string.not_a_known_tpms_qr_code) else stringResource(CommonR.string.point_the_camera_at_the_sensors_qr),
                color = Color.White,
                textAlign = TextAlign.Center,
            )
            suggestedId?.also { id ->
                Button(onClick = { viewModel.enter(id) }, enabled = isReading.not()) {
                    Text(stringResource(CommonR.string.qr_use_printed_id, id), textAlign = TextAlign.Center)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilledTonalButton(onClick = { isEnteringId = true }, enabled = isReading.not()) {
                    Text(stringResource(CommonR.string.qr_enter_id))
                }
                FilledTonalButton(
                    onClick = { pickPictures.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly)) },
                    enabled = isReading.not(),
                ) {
                    Text(stringResource(CommonR.string.qr_from_gallery))
                }
            }
        }
        if (isReading) CircularProgressIndicator(Modifier.align(Alignment.Center))
    }

    if (isEnteringId) EnterIdDialog(
        onEnter = {
            isEnteringId = false
            viewModel.enter(it)
        },
        onDismiss = { isEnteringId = false },
    )
    if (choices.isNotEmpty()) ChoicesDialog(choices, onChoose = viewModel::choose)

    val navController = LocalHomeNavController.current
    LaunchedEffect(viewModel) {
        for (event in viewModel.eventChannel) {
            when (event) {
                is Event.Found -> onFound(event.result)

                Event.NoCodeInPictures -> snackbarHostState.showSnackbar(localized(CommonR.string.qr_no_code_in_pictures))

                Event.LeaveBecauseCameraUnavailable -> {
                    snackbarHostState.showSnackbar(localized(CommonR.string.needs_camera))
                    navController.popBackStack()
                }
            }
        }
    }
}

/** The back camera, bound to the screen's lifecycle */
@Composable
private fun rememberCameraController(cameraSelector: CameraSelector = DEFAULT_BACK_CAMERA) = LocalContext.current
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

/** The 6 hexadecimal characters of a sensor ID, the way a Wicarlink cap prints them */
@Composable
private fun EnterIdDialog(
    onEnter: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var id by rememberSaveable { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val isComplete = id.length == ID_LENGTH
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(CommonR.string.qr_sensor_id)) },
        text = {
            OutlinedTextField(
                value = id,
                // Only what an ID can hold, so a mistyped O or I shows rather than being refused later
                onValueChange = { typed ->
                    id = typed
                        .uppercase()
                        .filter { it in '0'..'9' || it in 'A'..'F' }
                        .take(ID_LENGTH)
                },
                supportingText = { Text(stringResource(CommonR.string.qr_sensor_id_hint)) },
                textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    autoCorrectEnabled = false,
                    keyboardType = KeyboardType.Ascii,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { if (isComplete) onEnter(id) }),
                singleLine = true,
                modifier = Modifier.focusRequester(focusRequester),
            )
        },
        confirmButton = {
            TextButton(onClick = { onEnter(id) }, enabled = isComplete) {
                Text(stringResource(CommonR.string.text_continue))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(CommonR.string.cancel)) } },
    )
    LaunchedEffect(focusRequester) { focusRequester.requestFocus() }
}

/** The pictures picked hold several codes: which one? */
@Composable
private fun ChoicesDialog(
    choices: List<QrCodeResult>,
    onChoose: (QrCodeResult?) -> Unit,
) = AlertDialog(
    onDismissRequest = { onChoose(null) },
    title = { Text(stringResource(CommonR.string.qr_several_codes)) },
    text = {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            choices.forEach { result ->
                TextButton(onClick = { onChoose(result) }, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = when (result) {
                            is QrCodeResult.Wicarlink -> stringResource(CommonR.string.qr_choice_wicarlink, result.id.asSensorId(WICARLINK))
                            is QrCodeResult.Sensors -> stringResource(CommonR.string.qr_choice_sysgration, result.sensors.size)
                            is QrCodeResult.Shared -> stringResource(CommonR.string.qr_choice_shared, result.shared.name)
                            else -> stringResource(CommonR.string.qr_choice_unusable)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    },
    confirmButton = {},
    dismissButton = { TextButton(onClick = { onChoose(null) }) { Text(stringResource(CommonR.string.cancel)) } },
)

@Composable
private fun QrCodeOverlay(modifier: Modifier = Modifier) {
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
    }
}

private const val WINDOW_FRACTION = .7f
private const val BRACKET_FRACTION = .15f
private const val DIM_ALPHA = .55f
private val CORNER_RADIUS = 4.dp
private val BRACKET_WIDTH = 4.dp
private const val ID_LENGTH = 6
