package com.jarvis.assistant.voice

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.sqrt

/**
 * Captures raw 16-bit PCM mono audio at 16,000 Hz (little-endian) as required by Gemini Live API.
 * Uses VOICE_COMMUNICATION audio source + hardware AcousticEchoCanceler to prevent speaker feedback
 * from being re-transcribed as user input, while supporting natural barge-in.
 */
class AudioInputManager(
    private val scope: CoroutineScope,
    private val isAssistantSpeakingProvider: () -> Boolean,
    private val onUserBargeInDetected: () -> Unit,
    private val onPcmChunkCaptured: (ByteArray) -> Unit,
    private val onCaptureError: (String) -> Unit
) {
    private var audioRecord: AudioRecord? = null
    private var echoCanceler: AcousticEchoCanceler? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    private var gainControl: AutomaticGainControl? = null
    private var captureJob: Job? = null

    private val _isCapturing = MutableStateFlow(false)
    val isCapturing: StateFlow<Boolean> = _isCapturing.asStateFlow()

    private val _inputAmplitude = MutableStateFlow(0f)
    val inputAmplitude: StateFlow<Float> = _inputAmplitude.asStateFlow()

    @SuppressLint("MissingPermission")
    fun startCapture(): Boolean {
        if (_isCapturing.value) return true

        val minBufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE_HZ,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBufferSize == AudioRecord.ERROR || minBufferSize == AudioRecord.ERROR_BAD_VALUE) {
            onCaptureError("Device microphone does not support 16kHz 16-bit mono PCM capture.")
            return false
        }

        val bufferSize = maxOf(minBufferSize * 2, CHUNK_SIZE_BYTES * 2)

        return try {
            val record = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                SAMPLE_RATE_HZ,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )

            if (record.state != AudioRecord.STATE_INITIALIZED) {
                record.release()
                onCaptureError("Failed to initialize microphone AudioRecord.")
                return false
            }

            attachHardwareAudioEffects(record.audioSessionId)
            record.startRecording()
            audioRecord = record
            _isCapturing.value = true

            captureJob = scope.launch(Dispatchers.IO) {
                val readBuffer = ByteArray(CHUNK_SIZE_BYTES)
                var consecutiveBargeInFrames = 0

                while (isActive && _isCapturing.value) {
                    val bytesRead = record.read(readBuffer, 0, readBuffer.size)
                    if (bytesRead > 0) {
                        val rmsNormalized = calculateNormalizedRms(readBuffer, bytesRead)
                        _inputAmplitude.value = rmsNormalized

                        val assistantSpeaking = isAssistantSpeakingProvider()
                        val hasHardwareAec = echoCanceler?.enabled == true

                        if (assistantSpeaking) {
                            // Detect intentional user barge-in over assistant speech
                            val bargeInThreshold = if (hasHardwareAec) 0.24f else 0.42f
                            if (rmsNormalized >= bargeInThreshold) {
                                consecutiveBargeInFrames++
                                if (consecutiveBargeInFrames >= 3) {
                                    consecutiveBargeInFrames = 0
                                    onUserBargeInDetected()
                                }
                            } else {
                                consecutiveBargeInFrames = 0
                            }

                            // If device lacks hardware AEC, suppress low-energy speaker bleed frames
                            if (!hasHardwareAec && rmsNormalized < 0.35f) {
                                continue
                            }
                        } else {
                            consecutiveBargeInFrames = 0
                        }

                        val chunk = if (bytesRead == readBuffer.size) {
                            readBuffer.copyOf()
                        } else {
                            readBuffer.copyOfRange(0, bytesRead)
                        }
                        onPcmChunkCaptured(chunk)
                    } else if (bytesRead < 0) {
                        onCaptureError("Microphone read error (code $bytesRead).")
                        break
                    }
                }
            }
            true
        } catch (e: Exception) {
            stopCapture()
            onCaptureError("Could not start microphone capture: ${e.localizedMessage ?: "Unknown error"}")
            false
        }
    }

    fun stopCapture() {
        _isCapturing.value = false
        _inputAmplitude.value = 0f
        captureJob?.cancel()
        captureJob = null

        try {
            echoCanceler?.release()
        } catch (_: Exception) {}
        echoCanceler = null

        try {
            noiseSuppressor?.release()
        } catch (_: Exception) {}
        noiseSuppressor = null

        try {
            gainControl?.release()
        } catch (_: Exception) {}
        gainControl = null

        try {
            audioRecord?.let { rec ->
                if (rec.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    rec.stop()
                }
                rec.release()
            }
        } catch (_: Exception) {}
        audioRecord = null
    }

    private fun attachHardwareAudioEffects(audioSessionId: Int) {
        try {
            if (AcousticEchoCanceler.isAvailable()) {
                echoCanceler = AcousticEchoCanceler.create(audioSessionId)?.apply {
                    enabled = true
                }
            }
            if (NoiseSuppressor.isAvailable()) {
                noiseSuppressor = NoiseSuppressor.create(audioSessionId)?.apply {
                    enabled = true
                }
            }
            if (AutomaticGainControl.isAvailable()) {
                gainControl = AutomaticGainControl.create(audioSessionId)?.apply {
                    enabled = true
                }
            }
        } catch (_: Exception) {
            // Audio effects are optional hardware enhancements
        }
    }

    private fun calculateNormalizedRms(buffer: ByteArray, length: Int): Float {
        val sampleCount = length / 2
        if (sampleCount <= 0) return 0f
        var sumSquares = 0.0
        var i = 0
        while (i + 1 < length) {
            val low = buffer[i].toInt() and 0xFF
            val high = buffer[i + 1].toInt()
            val sample = (high shl 8) or low
            val normalized = sample / 32768.0
            sumSquares += normalized * normalized
            i += 2
        }
        val rms = sqrt(sumSquares / sampleCount).toFloat()
        return (rms * 3.5f).coerceIn(0f, 1f)
    }

    companion object {
        const val SAMPLE_RATE_HZ = 16000
        // 100ms chunk at 16,000 Hz, 16-bit (2 bytes/sample) = 3,200 bytes
        const val CHUNK_SIZE_BYTES = 3200
    }
}
