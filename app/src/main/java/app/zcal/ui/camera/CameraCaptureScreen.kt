package app.zcal.ui.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DirectionsWalk
import androidx.compose.material.icons.outlined.FlashOff
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.window.Dialog
import app.zcal.ui.components.ZCalPrimaryButton
import app.zcal.ui.components.ZCalSecondaryButton
import app.zcal.ui.localization.LocalZCalStrings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import app.zcal.domain.model.EnergyConversion
import app.zcal.ui.home.HomeViewModel
import app.zcal.ui.review.CaptureWorkflowState
import app.zcal.ui.review.ReviewViewModel
import app.zcal.ui.theme.LocalZCalColors
import app.zcal.ui.theme.ZCalShapes
import app.zcal.ui.theme.ZCalTypeScale
import app.zcal.util.ImageResizer
import app.zcal.worker.PendingScanWorker
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.roundToInt

/**
 * Pre-Built Camera Main Page of ZCal:
 * - Launches immediately as the primary home experience after onboarding.
 * - Features a live top HUD bar showing today's remaining Calories (kcal) & Kilojoules (kJ),
 *   eaten Protein (g), Fat (g), CLA (mg), and live Steps from Health Connect / Step Sensor.
 * - Snapping a photo immediately triggers AI analysis and transitions to the animated
 *   Nutrition Breakdown & Review Screen.
 */
@Composable
fun CameraCaptureScreen(
    reviewViewModel: ReviewViewModel,
    homeViewModel: HomeViewModel,
    onAnalysisReadyForReview: () -> Unit,
    onOpenHistoryCache: () -> Unit,
    onOpenSettingsForKey: () -> Unit
) {
    val colors = LocalZCalColors.current
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    val workflowState by reviewViewModel.workflowState.collectAsState()
    val privacyDisclosed by reviewViewModel.privacyDisclosed.collectAsState()
    val summary by homeViewModel.daySummary.collectAsState()
    val profile by homeViewModel.userProfile.collectAsState()
    val healthSteps by homeViewModel.healthSteps.collectAsState()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    val activityRecognitionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { /* the sensor flow simply starts delivering once granted */ }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        } else if (
            android.os.Build.VERSION.SDK_INT >= 29 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            // Asked only after the camera prompt is settled so two dialogs never stack.
            activityRecognitionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
        }
        homeViewModel.refreshHealthConnectSteps()
    }

    var flashEnabled by remember { mutableStateOf(false) }
    var barcodeMode by remember { mutableStateOf(false) }
    var manualBarcodeText by remember { mutableStateOf("") }
    var userHintNote by remember { mutableStateOf("") }
    var showNoteDialog by remember { mutableStateOf(false) }

    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            handleCapturedUri(
                context = context,
                uri = uri,
                barcodeMode = barcodeMode,
                userNote = userHintNote,
                reviewViewModel = reviewViewModel,
                scope = scope
            )
        }
    }

    BackHandler(enabled = workflowState !is CaptureWorkflowState.Idle) {
        reviewViewModel.resetToIdle()
    }

    LaunchedEffect(workflowState) {
        if (workflowState is CaptureWorkflowState.ReadyForReview) {
            onAnalysisReadyForReview()
        }
    }

    val strings = LocalZCalStrings.current

    if (!privacyDisclosed) {
        Dialog(onDismissRequest = {}) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                shape = ZCalShapes.Dialog,
                color = colors.surfaceElevated,
                shadowElevation = 10.dp,
                border = BorderStroke(1.dp, colors.outline)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(ZCalShapes.Chip)
                                .background(colors.accent.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Shield,
                                contentDescription = null,
                                tint = colors.accent,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Text(
                            text = strings.privacyNoticeTitle,
                            style = ZCalTypeScale.Title,
                            color = colors.textPrimary
                        )
                    }

                    Text(
                        text = strings.privacyNoticeBody,
                        style = ZCalTypeScale.BodySmall,
                        color = colors.textSecondary
                    )

                    ZCalPrimaryButton(
                        text = strings.gotIt,
                        onClick = { reviewViewModel.acknowledgePrivacyDisclosure() },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        when (val state = workflowState) {
            is CaptureWorkflowState.Idle -> {
                if (hasCameraPermission) {
                    AndroidView(
                        factory = { ctx ->
                            val previewView = PreviewView(ctx)
                            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                            cameraProviderFuture.addListener({
                                val cameraProvider = cameraProviderFuture.get()
                                val preview = Preview.Builder().build().also {
                                    it.surfaceProvider = previewView.surfaceProvider
                                }
                                runCatching {
                                    cameraProvider.unbindAll()
                                    cameraProvider.bindToLifecycle(
                                        lifecycleOwner,
                                        CameraSelector.DEFAULT_BACK_CAMERA,
                                        preview,
                                        imageCapture
                                    )
                                }
                            }, ContextCompat.getMainExecutor(ctx))
                            previewView
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Camera access enables instant AI meal scanning on the main screen.",
                            style = ZCalTypeScale.Body,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                            Text("Enable Camera")
                        }
                    }
                }

                // Top Live HUD Overlay (Remaining kcal & kJ, Protein, Fat, CLA, Steps, Flash & Cache Button)
                val eatenKcal = summary?.kcal ?: 0
                val leftKcal = (profile.calorieGoal - eatenKcal).coerceAtLeast(0)
                val leftKj = EnergyConversion.kcalToKj(leftKcal)

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(top = 10.dp, start = 16.dp, end = 16.dp)
                        .align(Alignment.TopCenter),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Sleek floating glassmorphic HUD pill
                    Surface(
                        shape = ZCalShapes.Pill,
                        color = colors.canvas.copy(alpha = 0.88f),
                        border = BorderStroke(1.dp, colors.outline.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(colors.accent)
                                    )
                                    Text(
                                        text = "%,d kcal left".format(leftKcal),
                                        style = ZCalTypeScale.Title.copy(fontWeight = FontWeight.Bold, fontSize = 14.sp),
                                        color = colors.textPrimary
                                    )
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.padding(top = 2.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(colors.protein))
                                        Text("P ${(summary?.proteinG ?: 0.0).roundToInt()}g", style = ZCalTypeScale.Label.copy(fontSize = 11.sp), color = colors.textSecondary)
                                    }
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(colors.carbs))
                                        Text("C ${(summary?.carbsG ?: 0.0).roundToInt()}g", style = ZCalTypeScale.Label.copy(fontSize = 11.sp), color = colors.textSecondary)
                                    }
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(colors.fat))
                                        Text("F ${(summary?.fatG ?: 0.0).roundToInt()}g", style = ZCalTypeScale.Label.copy(fontSize = 11.sp), color = colors.textSecondary)
                                    }
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(colors.surfaceVariant)
                                        .clickable { onOpenHistoryCache() },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.History,
                                        contentDescription = "Monthly Food Image Cache",
                                        tint = colors.textPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(if (flashEnabled) colors.accent else colors.surfaceVariant)
                                        .clickable {
                                            flashEnabled = !flashEnabled
                                            imageCapture.flashMode = if (flashEnabled) {
                                                ImageCapture.FLASH_MODE_ON
                                            } else {
                                                ImageCapture.FLASH_MODE_OFF
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (flashEnabled) Icons.Outlined.FlashOn else Icons.Outlined.FlashOff,
                                        contentDescription = "Toggle Flash",
                                        tint = if (flashEnabled) colors.onAccent else colors.textPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }

                    if (healthSteps.steps > 0) {
                        Surface(
                            shape = ZCalShapes.Pill,
                            color = colors.canvas.copy(alpha = 0.82f),
                            border = BorderStroke(1.dp, colors.outline.copy(alpha = 0.4f)),
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Outlined.DirectionsWalk,
                                    contentDescription = null,
                                    tint = colors.accent,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "%,d steps (%d kcal burned)".format(
                                        healthSteps.steps,
                                        healthSteps.activeBurnKcal
                                    ),
                                    style = ZCalTypeScale.Label.copy(fontSize = 11.sp),
                                    color = colors.textPrimary
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        FilterChip(
                            selected = !barcodeMode,
                            onClick = { barcodeMode = false },
                            label = { Text("AI Photo Scan") },
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        FilterChip(
                            selected = barcodeMode,
                            onClick = { barcodeMode = true },
                            label = { Text("Barcode") }
                        )
                    }
                }

                // Bottom Camera Controls above Floating Nav Bar
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(start = 20.dp, end = 20.dp, bottom = 96.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (barcodeMode) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 10.dp)
                        ) {
                            OutlinedTextField(
                                value = manualBarcodeText,
                                onValueChange = { manualBarcodeText = it },
                                label = { Text("Type EAN/UPC barcode") },
                                modifier = Modifier.weight(1f),
                                shape = ZCalShapes.Input
                            )
                            Button(
                                onClick = {
                                    if (manualBarcodeText.isNotBlank()) {
                                        reviewViewModel.analyzeBarcodeString(manualBarcodeText)
                                    }
                                },
                                shape = ZCalShapes.Pill
                            ) {
                                Text("Lookup")
                            }
                        }
                    } else {
                        FilterChip(
                            selected = userHintNote.isNotBlank(),
                            onClick = { showNoteDialog = true },
                            label = {
                                Text(
                                    if (userHintNote.isBlank()) "+ Add meal note (e.g. fried in olive oil)"
                                    else "Note: $userHintNote"
                                )
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                galleryLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            modifier = Modifier.size(56.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.PhotoLibrary,
                                contentDescription = "Pick from Gallery",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        // 80 dp Pre-Built Main Shutter Button
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                val tempFile = File(context.cacheDir, "raw_capture_${System.currentTimeMillis()}.jpg")
                                val outputOptions = ImageCapture.OutputFileOptions.Builder(tempFile).build()
                                imageCapture.takePicture(
                                    outputOptions,
                                    ContextCompat.getMainExecutor(context),
                                    object : ImageCapture.OnImageSavedCallback {
                                        override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                            handleCapturedUri(
                                                context = context,
                                                uri = Uri.fromFile(tempFile),
                                                barcodeMode = barcodeMode,
                                                userNote = userHintNote,
                                                reviewViewModel = reviewViewModel,
                                                scope = scope
                                            )
                                        }

                                        override fun onError(exception: ImageCaptureException) {
                                            reviewViewModel.startManualFallback(userHintNote)
                                        }
                                    }
                                )
                            },
                            modifier = Modifier
                                .size(80.dp)
                                .border(4.dp, Color.White, CircleShape)
                                .padding(6.dp)
                                .clip(CircleShape)
                                .background(colors.primary)
                        ) {
                            if (barcodeMode) {
                                Icon(Icons.Outlined.QrCodeScanner, contentDescription = "Scan Barcode", tint = colors.onPrimary)
                            }
                        }

                        TextButton(onClick = { reviewViewModel.startManualFallback(userHintNote) }) {
                            Text("Manual", color = Color.White)
                        }
                    }
                }
            }

            is CaptureWorkflowState.Analyzing -> {
                AnalyzingShimmerOverlay(
                    photoFile = state.photoFile,
                    onCancel = { reviewViewModel.resetToIdle() }
                )
            }

            is CaptureWorkflowState.Error -> {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                        .align(Alignment.Center),
                    shape = ZCalShapes.Card,
                    color = colors.surface,
                    border = BorderStroke(1.dp, colors.outline)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(state.title, style = ZCalTypeScale.Title, color = colors.textPrimary)
                        Text(state.message, style = ZCalTypeScale.Body, color = colors.textSecondary)

                        if (state.isOfflineQueued) {
                            LaunchedEffect(Unit) {
                                PendingScanWorker.enqueueWhenOnline(context)
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = { reviewViewModel.retryLastScan() },
                                shape = ZCalShapes.Pill,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Retry")
                            }
                            Button(
                                onClick = { reviewViewModel.startManualFallback(userHintNote) },
                                shape = ZCalShapes.Pill,
                                colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Edit Manually", color = colors.onAccent)
                            }
                        }

                        if (state.isKeyError) {
                            TextButton(onClick = onOpenSettingsForKey) {
                                Text("Open Key Settings", color = colors.accent)
                            }
                        }
                    }
                }
            }

            is CaptureWorkflowState.ReadyForReview -> {
                LaunchedEffect(Unit) {
                    reviewViewModel.resetToIdle()
                }
            }
        }
    }

    if (showNoteDialog) {
        Dialog(onDismissRequest = { showNoteDialog = false }) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                shape = ZCalShapes.Dialog,
                color = colors.surfaceElevated,
                shadowElevation = 10.dp,
                border = BorderStroke(1.dp, colors.outline)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(ZCalShapes.Chip)
                                .background(colors.accent.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.EditNote,
                                contentDescription = null,
                                tint = colors.accent,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Text(
                            text = strings.addMealNote,
                            style = ZCalTypeScale.Title,
                            color = colors.textPrimary
                        )
                    }

                    OutlinedTextField(
                        value = userHintNote,
                        onValueChange = { userHintNote = it },
                        placeholder = { Text(strings.noteHint, style = ZCalTypeScale.BodySmall) },
                        shape = ZCalShapes.Input,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ZCalSecondaryButton(
                            text = strings.cancel,
                            onClick = { showNoteDialog = false },
                            modifier = Modifier.weight(1f)
                        )
                        ZCalPrimaryButton(
                            text = strings.save,
                            onClick = { showNoteDialog = false },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

private fun handleCapturedUri(
    context: Context,
    uri: Uri,
    barcodeMode: Boolean,
    userNote: String,
    reviewViewModel: ReviewViewModel,
    scope: CoroutineScope
) {
    scope.launch(Dispatchers.IO) {
        val resizedFile = ImageResizer.processAndSaveMealImage(context, uri)
        if (barcodeMode) {
            runCatching {
                val image = InputImage.fromFilePath(context, uri)
                val scanner = BarcodeScanning.getClient()
                scanner.process(image)
                    .addOnSuccessListener { barcodes ->
                        val rawValue = barcodes.firstOrNull()?.rawValue
                        if (!rawValue.isNullOrBlank()) {
                            reviewViewModel.analyzeBarcodeString(rawValue, fallbackPhoto = resizedFile)
                        } else if (resizedFile != null) {
                            reviewViewModel.analyzePhotoFile(resizedFile, userNote)
                        }
                    }
                    .addOnFailureListener {
                        if (resizedFile != null) {
                            reviewViewModel.analyzePhotoFile(resizedFile, userNote)
                        }
                    }
            }.onFailure {
                if (resizedFile != null) {
                    reviewViewModel.analyzePhotoFile(resizedFile, userNote)
                } else {
                    reviewViewModel.startManualFallback(userNote)
                }
            }
        } else if (resizedFile != null) {
            reviewViewModel.analyzePhotoFile(resizedFile, userNote)
        } else {
            reviewViewModel.startManualFallback(userNote)
        }
    }
}

@Composable
private fun AnalyzingShimmerOverlay(
    photoFile: File?,
    onCancel: () -> Unit
) {
    val colors = LocalZCalColors.current
    val statusMessages = listOf(
        "Identifying foods...",
        "Estimating portions...",
        "Calculating Protein, Fat, CLA, kcal & kJ..."
    )
    var msgIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1500L)
            msgIndex = (msgIndex + 1) % statusMessages.size
        }
    }

    val transition = rememberInfiniteTransition(label = "shimmerLoop")
    val shimmerOffset by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1200f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerTranslate"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        if (photoFile != null && photoFile.exists()) {
            AsyncImage(
                model = photoFile,
                contentDescription = "Analyzing meal photo",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.55f),
                            colors.accent.copy(alpha = 0.35f),
                            Color.Black.copy(alpha = 0.55f)
                        ),
                        start = Offset(shimmerOffset - 400f, shimmerOffset - 400f),
                        end = Offset(shimmerOffset, shimmerOffset)
                    )
                )
        )

        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = statusMessages[msgIndex],
                style = ZCalTypeScale.Headline,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(20.dp))
            OutlinedButton(
                onClick = onCancel,
                shape = ZCalShapes.Pill
            ) {
                Text("Cancel", color = Color.White)
            }
        }
    }
}
