package app.neara.desktop.audio

import app.neara.service.AudioCaptureProvider
import kotlinx.coroutines.*
import javax.sound.sampled.*

class DesktopAudioCaptureProvider : AudioCaptureProvider {
    private var line: TargetDataLine? = null
    private var captureJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun startCapture(onAudioData: (ByteArray) -> Unit) {
        captureJob?.cancel()
        captureJob = scope.launch {
            try {
                val format = AudioFormat(16000f, 16, 1, true, false)
                val info = DataLine.Info(TargetDataLine::class.java, format)
                if (AudioSystem.isLineSupported(info)) {
                    val targetLine = AudioSystem.getLine(info) as TargetDataLine
                    line = targetLine
                    targetLine.open(format)
                    targetLine.start()

                    val buffer = ByteArray(1024)
                    while (isActive) {
                        val read = targetLine.read(buffer, 0, buffer.size)
                        if (read > 0) {
                            onAudioData(buffer.copyOf(read))
                        }
                    }
                    targetLine.stop()
                    targetLine.close()
                }
            } catch (e: Exception) {
                // Line unavailable
            }
        }
    }

    override fun stopCapture() {
        captureJob?.cancel()
        try {
            line?.stop()
            line?.close()
        } catch (e: Exception) {}
        line = null
    }
}
