package com.jarvis.assistant.voice

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.sqrt

/**
 * Plays incoming Gemini Live API PCM audio chunks (24,000 Hz, 16-bit mono, little-endian).
 * Supports immediate interruption/barge-in and exposes playback state and voice amplitude.
 */
class AudioOutputManager(
    private val scope: CoroutineScope,
    private val onPlaybackStarted: () -> Unit,
    private val onPlaybackQueueDrained: () -> Unit,
    private val onPlaybackError: (String) -> Unit
) {
    private var audioTrack: AudioTrack? = null
    private var audioQueue = Channel<ByteArray>(capacity = Channel.UNLIMITED)
    private var playbackJob: Job? = null

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _outputAmplitude = MutableStateFlow(0f)
    val outputAmplitude: StateFlow<Float> = _outputAmplitude.asStateFlow()

    fun initializeTrack() {
        if (audioTrack != null) return
        try {
            val minBuffer = AudioTrack.getMinBufferSize(
                OUTPUT_SAMPLE_RATE_HZ,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val bufferSize = maxOf(minBuffer * 2, 9600)

            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(OUTPUT_SAMPLE_RATE_HZ)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            track.play()
            audioTrack = track
            startPlaybackWorker()
        } catch (e: Exception) {
            onPlaybackError("Failed to initialize audio speaker output: ${e.localizedMessage}")
        }
    }

    fun enqueuePcmChunk(pcmBytes: ByteArray) {
        if (pcmBytes.isEmpty()) return
        if (audioTrack == null) {
            initializeTrack()
        }
        audioQueue.trySend(pcmBytes)
    }

    /**
     * Immediately stops speaker output and discards buffered audio chunks (used for Stop Speaking & Barge-In).
     */
    fun stopPlaybackAndClearQueue() {
        while (audioQueue.tryReceive().isSuccess) {
            // Drain pending chunks
        }
        try {
            audioTrack?.let { track ->
                if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    track.pause()
                    track.flush()
                    track.play()
                }
            }
        } catch (_: Exception) {}

        if (_isPlaying.value) {
            _isPlaying.value = false
            _outputAmplitude.value = 0f
            onPlaybackQueueDrained()
        }
    }

    fun release() {
        playbackJob?.cancel()
        playbackJob = null
        while (audioQueue.tryReceive().isSuccess) {}
        _isPlaying.value = false
        _outputAmplitude.value = 0f
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (_: Exception) {}
        audioTrack = null
    }

    private fun startPlaybackWorker() {
        playbackJob?.cancel()
        playbackJob = scope.launch(Dispatchers.IO) {
            for (chunk in audioQueue) {
                if (!isActive) break
                val track = audioTrack ?: continue
                if (!_isPlaying.value) {
                    _isPlaying.value = true
                    onPlaybackStarted()
                }
                _outputAmplitude.value = calculateRms(chunk)
                try {
                    track.write(chunk, 0, chunk.size)
                } catch (e: Exception) {
                    onPlaybackError("Audio playback write failed: ${e.localizedMessage}")
                }
                if (audioQueue.isEmpty) {
                    _isPlaying.value = false
                    _outputAmplitude.value = 0f
                    onPlaybackQueueDrained()
                }
            }
        }
    }

    private fun calculateRms(buffer: ByteArray): Float {
        val sampleCount = buffer.size / 2
        if (sampleCount <= 0) return 0f
        var sumSquares = 0.0
        var i = 0
        while (i + 1 < buffer.size) {
            val low = buffer[i].toInt() and 0xFF
            val high = buffer[i + 1].toInt()
            val sample = (high shl 8) or low
            val norm = sample / 32768.0
            sumSquares += norm * norm
            i += 2
        }
        return (sqrt(sumSquares / sampleCount).toFloat() * 3.2f).coerceIn(0f, 1f)
    }

    companion object {
        const val OUTPUT_SAMPLE_RATE_HZ = 24000
    }
}
