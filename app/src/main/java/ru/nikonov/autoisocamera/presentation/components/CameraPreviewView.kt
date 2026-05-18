package ru.nikonov.autoisocamera.presentation.components

import androidx.camera.core.Preview
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Wraps [PreviewView] as a Compose node.
 *
 * [onSurfaceProviderReady] is called once with the [Preview.SurfaceProvider];
 * the caller is responsible for binding it to the camera use-case.
 */
@Composable
fun CameraPreviewView(
    modifier: Modifier = Modifier,
    onSurfaceProviderReady: (Preview.SurfaceProvider) -> Unit,
) {
    // remember the lambda to avoid re-creating AndroidView on recomposition.
    val callback = remember(onSurfaceProviderReady) { onSurfaceProviderReady }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
                implementationMode = PreviewView.ImplementationMode.PERFORMANCE
            }
        },
        update = { previewView ->
            callback(previewView.surfaceProvider)
        },
    )
}
