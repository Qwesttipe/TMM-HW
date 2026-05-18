package ru.nikonov.autoisocamera.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.nikonov.autoisocamera.presentation.components.CameraPreviewView
import ru.nikonov.autoisocamera.presentation.components.StatsOverlay

@Composable
fun CameraScreen(viewModel: CameraViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    Box(modifier = Modifier.fillMaxSize()) {

        // ── Camera preview (full screen) ────────────────────────────────────
        CameraPreviewView(
            modifier = Modifier.fillMaxSize(),
            onSurfaceProviderReady = { surfaceProvider ->
                viewModel.startCamera(lifecycleOwner, surfaceProvider)
            },
        )

        // ── Stats HUD (top-left) ────────────────────────────────────────────
        StatsOverlay(
            state = uiState,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp),
        )

        // ── Toggle button (bottom-center) ───────────────────────────────────
        Button(
            onClick = viewModel::toggleAdaptive,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 40.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (uiState.isAdaptiveEnabled) Color(0xFF4CAF50) else Color(0xFF757575),
            ),
        ) {
            Text(text = if (uiState.isAdaptiveEnabled) "Adaptive ISO: ON" else "Adaptive ISO: OFF")
        }

        // ── Error banner ────────────────────────────────────────────────────
        uiState.error?.let { message ->
            Snackbar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
            ) {
                Text(message)
            }
        }
    }
}
