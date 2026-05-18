package ru.nikonov.autoisocamera.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.nikonov.autoisocamera.presentation.CameraUiState

private val overlayBackground = Color(0xCC000000)  // 80 % black
private val labelColor = Color(0xFFAAAAAA)
private val valueColor = Color.White
private val accentGreen = Color(0xFF4CAF50)
private val accentOrange = Color(0xFFFF9800)

@Composable
fun StatsOverlay(state: CameraUiState, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(overlayBackground, RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        OverlayTitle(state)
        StatRow(label = "Luminance", value = "%.1f / %.0f".format(state.currentLuminance, state.targetLuminance))
        StatRow(label = "ISO  active", value = if (state.currentIso > 0) state.currentIso.toString() else "—")
        StatRow(label = "ISO  target", value = if (state.targetIso > 0) state.targetIso.toString() else "—", accent = true)
        StatRow(label = "Exposure", value = "%.2f ms".format(state.exposureTimeMs))
        StatRow(label = "FPS", value = "%.1f".format(state.fps))

        if (!state.supportsManualSensor) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Manual sensor not supported",
                color = accentOrange,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

@Composable
private fun OverlayTitle(state: CameraUiState) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "AUTO ISO",
            color = valueColor,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
        )
        Spacer(Modifier.width(8.dp))
        val (label, color) = if (state.isAdaptiveEnabled) "ON" to accentGreen else "OFF" to accentOrange
        Text(
            text = label,
            color = color,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
private fun StatRow(label: String, value: String, accent: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            color = labelColor,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
        )
        Text(
            text = value,
            color = if (accent) accentGreen else valueColor,
            fontSize = 11.sp,
            fontWeight = if (accent) FontWeight.SemiBold else FontWeight.Normal,
            fontFamily = FontFamily.Monospace,
        )
    }
}
