package ru.nikonov.autoisocamera.presentation

import androidx.camera.core.Preview
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.nikonov.autoisocamera.domain.model.FrameData
import ru.nikonov.autoisocamera.domain.repository.CameraRepository
import ru.nikonov.autoisocamera.domain.usecase.ComputeAdaptiveIsoUseCase

class CameraViewModel(
    private val cameraRepository: CameraRepository,
    private val computeAdaptiveIso: ComputeAdaptiveIsoUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CameraUiState())
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()

    private var analysisJob: Job? = null

    // ── Public API ───────────────────────────────────────────────────────────

    fun startCamera(lifecycleOwner: LifecycleOwner, surfaceProvider: Preview.SurfaceProvider) {
        viewModelScope.launch {
            try {
                cameraRepository.startCamera(lifecycleOwner, surfaceProvider)
                observeFrames()
                observeCapabilities()
                observeFps()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Camera failed to start: ${e.message}") }
            }
        }
    }

    fun toggleAdaptive() {
        _uiState.update { it.copy(isAdaptiveEnabled = !it.isAdaptiveEnabled) }
        if (!_uiState.value.isAdaptiveEnabled) {
            computeAdaptiveIso.reset()
        }
    }

    override fun onCleared() {
        super.onCleared()
        analysisJob?.cancel()
        cameraRepository.stopCamera()
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private fun observeFrames() {
        analysisJob?.cancel()
        analysisJob = viewModelScope.launch {
            cameraRepository.frameData.collectLatest { frame ->
                frame ?: return@collectLatest
                val caps = cameraRepository.capabilities.value ?: return@collectLatest
                if (!_uiState.value.isAdaptiveEnabled) {
                    updateLuminanceOnly(frame)
                    return@collectLatest
                }

                val params = computeAdaptiveIso.execute(frame, caps)

                _uiState.update { state ->
                    state.copy(
                        currentLuminance = frame.luminance,
                        targetLuminance = computeAdaptiveIso.targetLuminance,
                        targetIso = params.iso,
                        exposureTimeMs = params.exposureTimeNs / 1_000_000f,
                    )
                }

                // Called directly — no nested launch.
                // collectLatest cancels this suspension point when the next frame
                // arrives, so only the most-recent ISO is ever in flight.
                cameraRepository.applyIsoParameters(params)
            }
        }
    }

    private fun observeCapabilities() {
        viewModelScope.launch {
            cameraRepository.capabilities.collect { caps ->
                _uiState.update { it.copy(supportsManualSensor = caps?.supportsManualSensor == true) }
            }
        }
    }

    private fun observeFps() {
        viewModelScope.launch {
            combine(
                cameraRepository.fps,
                cameraRepository.activeParameters,
            ) { fps, params -> fps to params }.collect { (fps, params) ->
                _uiState.update { it.copy(fps = fps, currentIso = params?.iso ?: 0) }
            }
        }
    }

    private fun updateLuminanceOnly(frame: FrameData) {
        _uiState.update { it.copy(currentLuminance = frame.luminance) }
    }
}
