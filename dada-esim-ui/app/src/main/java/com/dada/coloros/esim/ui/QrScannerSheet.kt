package com.dada.coloros.esim.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.dada.coloros.esim.ui.components.CouiPillButton
import com.dada.coloros.esim.ui.theme.CouiTheme
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.kyant.capsule.ContinuousRoundedRectangle
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@Composable
fun QrScannerPanel(
    onScanned: (String) -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val colors = CouiTheme.colors
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Scan eSIM QR Code",
            style = CouiTheme.typography.topBarTitle,
            color = colors.onSurface,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Align the carrier LPA QR code inside the frame",
            style = CouiTheme.typography.summary,
            color = colors.onSurfaceVariantSummary,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(16.dp))

        val frameShape = ContinuousRoundedRectangle(20.dp)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(frameShape)
                .background(Color.Black, frameShape)
                .border(1.5.dp, colors.primary.copy(alpha = 0.7f), frameShape),
            contentAlignment = Alignment.Center,
        ) {
            if (hasCameraPermission) {
                val executor = remember { Executors.newSingleThreadExecutor() }
                val delivered = remember { AtomicBoolean(false) }

                DisposableEffect(Unit) {
                    onDispose { executor.shutdown() }
                }

                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        val previewView = PreviewView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT,
                            )
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                        }
                        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                        cameraProviderFuture.addListener({
                            runCatching {
                                val provider = cameraProviderFuture.get()
                                val preview = Preview.Builder().build().apply {
                                    surfaceProvider = previewView.surfaceProvider
                                }
                                val reader = MultiFormatReader().apply {
                                    setHints(mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE)))
                                }
                                val analysis = ImageAnalysis.Builder()
                                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                    .build()
                                    .apply {
                                        setAnalyzer(executor) { imageProxy ->
                                            try {
                                                val decoded = decodeQrFromImage(reader, imageProxy)
                                                if (decoded != null &&
                                                    decoded.trim().startsWith("LPA:", ignoreCase = true) &&
                                                    delivered.compareAndSet(false, true)
                                                ) {
                                                    ContextCompat.getMainExecutor(ctx).execute {
                                                        onScanned(decoded.trim())
                                                    }
                                                }
                                            } finally {
                                                imageProxy.close()
                                            }
                                        }
                                    }
                                provider.unbindAll()
                                provider.bindToLifecycle(
                                    lifecycleOwner,
                                    CameraSelector.DEFAULT_BACK_CAMERA,
                                    preview,
                                    analysis,
                                )
                            }
                        }, ContextCompat.getMainExecutor(ctx))
                        previewView
                    },
                )
            } else {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "Camera permission is required to scan QR codes.",
                        style = CouiTheme.typography.body,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    CouiPillButton(
                        text = "Grant Camera",
                        onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        CouiPillButton(
            text = "Cancel",
            onClick = onCancel,
            primary = false,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(12.dp))
    }
}

private fun decodeQrFromImage(reader: MultiFormatReader, image: ImageProxy): String? {
    if (image.format != ImageFormat.YUV_420_888 &&
        image.format != ImageFormat.YUV_422_888 &&
        image.format != ImageFormat.YUV_444_888
    ) {
        return null
    }
    val plane = image.planes.firstOrNull() ?: return null
    val buffer = plane.buffer
    val data = ByteArray(buffer.remaining())
    buffer.get(data)
    val width = image.width
    val height = image.height
    val rowStride = plane.rowStride
    val yData = if (rowStride == width) {
        data
    } else {
        ByteArray(width * height).also { out ->
            for (row in 0 until height) {
                val srcOffset = row * rowStride
                val copyLen = minOf(width, data.size - srcOffset)
                if (copyLen > 0) {
                    System.arraycopy(data, srcOffset, out, row * width, copyLen)
                }
            }
        }
    }
    val source = PlanarYUVLuminanceSource(yData, width, height, 0, 0, width, height, false)
    val bitmap = BinaryBitmap(HybridBinarizer(source))
    return runCatching {
        reader.decodeWithState(bitmap).text
    }.getOrNull().also {
        reader.reset()
    }
}
