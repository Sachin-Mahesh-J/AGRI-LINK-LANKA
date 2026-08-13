package com.example.agriscout.ui.components

import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.agriscout.camera.ImageFileProvider
import java.io.File
import androidx.core.net.toUri

@Composable
fun CapturedPhotoPreview(
    localUri: String?,
    remoteUrl: String?,
    contentDescription: String,
    modifier: Modifier = Modifier,
    heightDp: Int = 180,
    onClick: (() -> Unit)? = null
) {
    val imageModel = localUri?.toUri() ?: remoteUrl ?: return
    val imageModifier = modifier
        .fillMaxWidth()
        .height(heightDp.dp)
        .clip(MaterialTheme.shapes.large)
        .then(
            if (onClick != null) {
                Modifier.clickable(onClick = onClick)
            } else {
                Modifier
            }
        )
    coil.compose.AsyncImage(
        model = imageModel,
        contentDescription = contentDescription,
        modifier = imageModifier,
        contentScale = androidx.compose.ui.layout.ContentScale.Crop
    )
}

@Composable
fun CameraXPhotoCapture(
    createOutputFile: (Context) -> File,
    captureButtonText: String,
    onImageCaptured: (String) -> Unit,
    onError: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var initializing by remember { mutableStateOf(true) }

    DisposableEffect(lifecycleOwner, previewView) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val mainExecutor = ContextCompat.getMainExecutor(context)
        val listener = Runnable {
            runCatching {
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
                val captureUseCase = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    captureUseCase
                )
                imageCapture = captureUseCase
                initializing = false
            }.onFailure {
                initializing = false
                onError("Unable to start camera preview. ${it.localizedMessage ?: "Please retry."}")
            }
        }
        cameraProviderFuture.addListener(listener, mainExecutor)
        onDispose {
            runCatching { cameraProviderFuture.get().unbindAll() }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(AgriScoutSpacing.medium)) {
        Surface(shape = MaterialTheme.shapes.large, tonalElevation = 2.dp) {
            AndroidView(
                factory = { previewView },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .clip(MaterialTheme.shapes.large)
            )
        }
        if (initializing) {
            LoadingStatus("Preparing camera preview...")
        }
        Button(
            onClick = {
                val capture = imageCapture
                if (capture == null) {
                    onError("Camera is still initializing. Try again in a moment.")
                    return@Button
                }
                val outputFile = createOutputFile(context)
                val outputOptions = ImageCapture.OutputFileOptions.Builder(outputFile).build()
                capture.takePicture(
                    outputOptions,
                    ContextCompat.getMainExecutor(context),
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                            onImageCaptured(outputFile.toUri().toString())
                        }

                        override fun onError(exception: ImageCaptureException) {
                            onError("Image capture failed. ${exception.message ?: "Please retry."}")
                        }
                    }
                )
            },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            shape = MaterialTheme.shapes.medium,
            enabled = !initializing
        ) {
            Icon(Icons.Filled.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.size(8.dp))
            Text(captureButtonText)
        }
    }
}

fun ImageFileProvider.farmOutputFile(): (Context) -> File = { createFarmImageFile(it) }

fun ImageFileProvider.reportOutputFile(): (Context) -> File = { createReportImageFile(it) }

fun ImageFileProvider.visitOutputFile(): (Context) -> File = { createVisitImageFile(it) }