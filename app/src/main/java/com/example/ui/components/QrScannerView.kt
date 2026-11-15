package com.example.ui.components

import android.util.Log
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.ui.theme.AmberPrimary
import com.example.ui.theme.Slate100
import com.example.util.QrCodeDecoder
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

/**
 * In-process camera preview that keeps scanning QR codes and reports each detection.
 *
 * The camera runs inside the alarm activity (no external scanner app), so it also works
 * while Device Owner lock task mode is active.
 */
@Composable
fun QrScannerView(
  modifier: Modifier = Modifier,
  onQrDetected: (String) -> Unit,
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val currentOnQrDetected by rememberUpdatedState(onQrDetected)
  val lastDeliveredAt = remember { AtomicLong(0L) }
  var lensFacing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
  var errorMessage by remember { mutableStateOf<String?>(null) }
  var boundCamera by remember { mutableStateOf<Camera?>(null) }
  var torchEnabled by remember { mutableStateOf(false) }
  val hasFlashUnit = boundCamera?.cameraInfo?.hasFlashUnit() == true

  val previewView = remember {
    PreviewView(context).apply {
      implementationMode = PreviewView.ImplementationMode.COMPATIBLE
      scaleType = PreviewView.ScaleType.FILL_CENTER
    }
  }

  DisposableEffect(lensFacing, lifecycleOwner) {
    val mainExecutor = ContextCompat.getMainExecutor(context)
    val analyzerExecutor = Executors.newSingleThreadExecutor()
    val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
    var boundProvider: ProcessCameraProvider? = null
    var analysis: ImageAnalysis? = null
    var disposed = false

    val listener = Runnable {
      if (disposed) return@Runnable
      try {
        val provider = cameraProviderFuture.get()
        boundProvider = provider

        val preview = Preview.Builder().build().also {
          it.setSurfaceProvider(previewView.surfaceProvider)
        }

        val imageAnalysis = ImageAnalysis.Builder()
          .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
          .build()
        imageAnalysis.setAnalyzer(analyzerExecutor) { imageProxy ->
          try {
            val payload = QrCodeDecoder.decode(imageProxy)
            if (payload != null) {
              val now = System.currentTimeMillis()
              // Debounce so a frame storm cannot spam the caller; the caller may still
              // reject a wrong QR and keep scanning.
              if (now - lastDeliveredAt.get() > 1200L) {
                lastDeliveredAt.set(now)
                mainExecutor.execute { currentOnQrDetected(payload) }
              }
            }
          } catch (e: Exception) {
            Log.w("QrScannerView", "Frame decode failed", e)
          } finally {
            imageProxy.close()
          }
        }
        analysis = imageAnalysis

        val selector = CameraSelector.Builder()
          .requireLensFacing(lensFacing)
          .build()

        provider.unbindAll()
        boundCamera = provider.bindToLifecycle(lifecycleOwner, selector, preview, imageAnalysis)
        torchEnabled = false
        errorMessage = null
      } catch (e: Exception) {
        Log.w("QrScannerView", "Could not bind camera", e)
        errorMessage = "カメラを起動できませんでした。別のカメラに切り替えてみてください。"
      }
    }

    cameraProviderFuture.addListener(listener, mainExecutor)

    onDispose {
      disposed = true
      try {
        boundCamera?.cameraControl?.enableTorch(false)
      } catch (_: Exception) {
      }
      try {
        boundProvider?.unbindAll()
      } catch (_: Exception) {
      }
      try {
        analysis?.clearAnalyzer()
      } catch (_: Exception) {
      }
      analyzerExecutor.shutdown()
    }
  }

  // Keep the torch in sync with the toggle. Front cameras usually have no flash unit.
  LaunchedEffect(torchEnabled, boundCamera) {
    try {
      boundCamera?.cameraControl?.enableTorch(torchEnabled && hasFlashUnit)
    } catch (_: Exception) {
    }
  }

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(Color.Black),
  ) {
    AndroidView(
      factory = { previewView },
      modifier = Modifier.fillMaxSize(),
    )

    // Scan guide: keeps the QR inside the frame so the user does not have to guess.
    Box(
      modifier = Modifier
        .align(Alignment.Center)
        .fillMaxWidth(0.72f)
        .aspectRatio(1f)
        .border(2.dp, AmberPrimary, RoundedCornerShape(18.dp)),
    )

    Column(
      modifier = Modifier
        .align(Alignment.BottomCenter)
        .fillMaxWidth()
        .padding(16.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      errorMessage?.let { message ->
        Text(
          text = message,
          color = Slate100,
          fontSize = 12.sp,
          textAlign = TextAlign.Center,
        )
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        if (hasFlashUnit) {
          OutlinedButton(
            onClick = { torchEnabled = !torchEnabled },
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.weight(1f),
          ) {
            Text(
              text = if (torchEnabled) "ライトを消す" else "ライトを点ける",
              color = Slate100,
              fontSize = 13.sp,
            )
          }
        }

        OutlinedButton(
          onClick = {
            lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
              CameraSelector.LENS_FACING_FRONT
            } else {
              CameraSelector.LENS_FACING_BACK
            }
          },
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier.weight(1f),
        ) {
          Text(
            text = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
              "インカメラに切り替える"
            } else {
              "アウトカメラに切り替える"
            },
            color = Slate100,
            fontSize = 13.sp,
          )
        }
      }
    }
  }
}
