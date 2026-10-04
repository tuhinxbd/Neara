package app.neara.android.call

import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.ToneGenerator
import kotlinx.coroutines.*
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.sqrt

class AudioStreamer(
    private val context: Context,
    private val localPort: Int,
    private val remoteIp: String,
    private val remotePort: Int,
    private val onLocalAudioLevel: (Float) -> Unit,
    private val onRemoteAudioLevel: (Float) -> Unit
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val isRunning = AtomicBoolean(false)
    val isMicMuted = AtomicBoolean(false)

    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var sendSocket: DatagramSocket? = null
    private var receiveSocket: DatagramSocket? = null
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    companion object {
        const val SAMPLE_RATE = 16000
        const val CHANNEL_CONFIG_IN = AudioFormat.CHANNEL_IN_MONO
        const val CHANNEL_CONFIG_OUT = AudioFormat.CHANNEL_OUT_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    fun start(speakerOn: Boolean) {
        if (isRunning.getAndSet(true)) return

        try {
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            setSpeakerphone(speakerOn)

            val minBufSizeIn = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG_IN, AUDIO_FORMAT)
            val minBufSizeOut = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG_OUT, AUDIO_FORMAT)
            val bufferSize = maxOf(minBufSizeIn, minBufSizeOut, 1280)

            // 1. Audio Record
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                SAMPLE_RATE,
                CHANNEL_CONFIG_IN,
                AUDIO_FORMAT,
                bufferSize * 2
            )

            // 2. Audio Track
            audioTrack = AudioTrack(
                AudioManager.STREAM_VOICE_CALL,
                SAMPLE_RATE,
                CHANNEL_CONFIG_OUT,
                AUDIO_FORMAT,
                bufferSize * 2,
                AudioTrack.MODE_STREAM
            )

            sendSocket = DatagramSocket()
            receiveSocket = DatagramSocket(localPort)

            audioTrack?.play()
            try {
                audioRecord?.startRecording()
            } catch (e: Exception) {}

            // Recording loop
            scope.launch {
                val buffer = ByteArray(bufferSize)
                val targetAddress = InetAddress.getByName(remoteIp)

                while (isRunning.get() && isActive) {
                    val read = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                    if (read > 0) {
                        // Compute RMS
                        val level = computeRms(buffer, read)
                        onLocalAudioLevel(level)

                        if (!isMicMuted.get()) {
                            try {
                                val packet = DatagramPacket(buffer, read, targetAddress, remotePort)
                                sendSocket?.send(packet)
                            } catch (e: Exception) {}
                        }
                    }
                }
            }

            // Playback loop
            scope.launch {
                val recvBuffer = ByteArray(bufferSize * 2)
                val recvPacket = DatagramPacket(recvBuffer, recvBuffer.size)

                while (isRunning.get() && isActive) {
                    try {
                        receiveSocket?.receive(recvPacket)
                        val length = recvPacket.length
                        if (length > 0) {
                            val level = computeRms(recvBuffer, length)
                            onRemoteAudioLevel(level)

                            audioTrack?.write(recvBuffer, 0, length)
                        }
                    } catch (e: Exception) {
                        if (!isRunning.get()) break
                    }
                }
            }
        } catch (e: Exception) {
            stop()
        }
    }

    fun setSpeakerphone(on: Boolean) {
        try {
            audioManager.isSpeakerphoneOn = on
        } catch (e: Exception) {}
    }

    fun stop() {
        if (!isRunning.getAndSet(false)) return

        scope.cancel()
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {}
        audioRecord = null

        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (e: Exception) {}
        audioTrack = null

        try {
            sendSocket?.close()
        } catch (e: Exception) {}
        sendSocket = null

        try {
            receiveSocket?.close()
        } catch (e: Exception) {}
        receiveSocket = null

        try {
            audioManager.mode = AudioManager.MODE_NORMAL
            audioManager.isSpeakerphoneOn = false
        } catch (e: Exception) {}

        onLocalAudioLevel(0f)
        onRemoteAudioLevel(0f)
    }

    private fun computeRms(buffer: ByteArray, length: Int): Float {
        var sum = 0.0
        var count = 0
        var i = 0
        while (i < length - 1) {
            val sample = (buffer[i].toInt() and 0xFF) or (buffer[i + 1].toInt() shl 8)
            val s = sample.toShort()
            sum += s * s
            count++
            i += 2
        }
        if (count == 0) return 0f
        val rms = sqrt(sum / count)
        return (rms / 8000.0).toFloat().coerceIn(0f, 1f)
    }
}

object ToneHelper {
    private var toneGen: ToneGenerator? = null

    fun playRingback() {
        try {
            stopTone()
            toneGen = ToneGenerator(AudioManager.STREAM_VOICE_CALL, 80)
            toneGen?.startTone(ToneGenerator.TONE_SUP_RINGTONE)
        } catch (e: Exception) {}
    }

    fun playEndBeep() {
        try {
            stopTone()
            val gen = ToneGenerator(AudioManager.STREAM_VOICE_CALL, 90)
            gen.startTone(ToneGenerator.TONE_PROP_BEEP2, 300)
        } catch (e: Exception) {}
    }

    fun stopTone() {
        try {
            toneGen?.stopTone()
            toneGen?.release()
            toneGen = null
        } catch (e: Exception) {}
    }
}
