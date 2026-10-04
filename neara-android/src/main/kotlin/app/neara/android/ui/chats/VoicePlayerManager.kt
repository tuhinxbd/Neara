@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package app.neara.android.ui




import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import app.neara.android.AndroidAppState
import app.neara.android.AndroidTab
import app.neara.android.ConversationItem
import app.neara.core.model.*
import app.neara.discovery.DiscoveredGroup
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class VoiceRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var currentFile: File? = null

    fun startRecording(): File? {
        try {
            val file = File(context.cacheDir, "voice_rec_${System.currentTimeMillis()}.m4a")
            currentFile = file
            recorder = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }).apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(64000)
                setAudioSamplingRate(44100)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            return file
        } catch (e: Exception) {
            e.printStackTrace()
            currentFile?.delete()
            currentFile = null
            recorder = null
            return null
        }
    }

    fun stopAndGetBase64(): String? {
        return try {
            recorder?.stop()
            recorder?.release()
            recorder = null
            val file = currentFile ?: return null
            val bytes = file.readBytes()
            file.delete()
            currentFile = null
            Base64.encodeToString(bytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            e.printStackTrace()
            cancelRecording()
            null
        }
    }

    fun cancelRecording() {
        try {
            recorder?.stop()
        } catch (e: Exception) {}
        try {
            recorder?.release()
        } catch (e: Exception) {}
        recorder = null
        currentFile?.delete()
        currentFile = null
    }
}

object VoicePlayerManager {
    private var currentPlayer: MediaPlayer? = null
    private var progressJob: kotlinx.coroutines.Job? = null
    var activeMessageId by mutableStateOf<String?>(null)
    var isPlaying by mutableStateOf(false)
    var progress by mutableStateOf(0f)
    var currentPositionSec by mutableStateOf(0)

    fun togglePlay(context: Context, messageId: String, base64Audio: String, scope: kotlinx.coroutines.CoroutineScope) {
        if (activeMessageId == messageId && isPlaying) {
            pause()
            return
        }

        if (activeMessageId == messageId && currentPlayer != null) {
            resume(scope)
            return
        }

        stop()
        try {
            val audioBytes = Base64.decode(base64Audio, Base64.DEFAULT)
            val tempFile = File(context.cacheDir, "play_voice_${System.currentTimeMillis()}.m4a")
            tempFile.writeBytes(audioBytes)

            val player = MediaPlayer().apply {
                setDataSource(tempFile.absolutePath)
                prepare()
                setOnCompletionListener {
                    stop()
                    tempFile.delete()
                }
                start()
            }
            currentPlayer = player
            activeMessageId = messageId
            isPlaying = true

            startProgressTicker(scope)
        } catch (e: Exception) {
            e.printStackTrace()
            stop()
        }
    }

    private fun startProgressTicker(scope: kotlinx.coroutines.CoroutineScope) {
        progressJob?.cancel()
        progressJob = scope.launch(Dispatchers.Main) {
            while (isPlaying && currentPlayer != null) {
                try {
                    val pos = currentPlayer?.currentPosition ?: 0
                    val dur = currentPlayer?.duration ?: 1
                    currentPositionSec = pos / 1000
                    progress = (pos.toFloat() / maxOf(dur, 1).toFloat()).coerceIn(0f, 1f)
                } catch (e: Exception) {}
                kotlinx.coroutines.delay(100)
            }
        }
    }

    fun pause() {
        try {
            currentPlayer?.pause()
        } catch (e: Exception) {}
        isPlaying = false
        progressJob?.cancel()
        progressJob = null
    }

    fun resume(scope: kotlinx.coroutines.CoroutineScope) {
        try {
            currentPlayer?.start()
            isPlaying = true
            startProgressTicker(scope)
        } catch (e: Exception) {
            stop()
        }
    }

    fun stop() {
        progressJob?.cancel()
        progressJob = null
        try {
            currentPlayer?.stop()
        } catch (e: Exception) {}
        try {
            currentPlayer?.release()
        } catch (e: Exception) {}
        currentPlayer = null
        activeMessageId = null
        isPlaying = false
        progress = 0f
        currentPositionSec = 0
    }
}
