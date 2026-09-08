package ni.nexo.app.ui.media

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

class VoiceNoteRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var startedAt: Long = 0L

    fun start() {
        if (recorder != null) return
        val file = File.createTempFile("nexo-voice-", ".m4a", context.cacheDir)
        val mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC)
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        mediaRecorder.setAudioEncodingBitRate(96_000)
        mediaRecorder.setAudioSamplingRate(44_100)
        mediaRecorder.setOutputFile(file.absolutePath)
        mediaRecorder.prepare()
        mediaRecorder.start()
        outputFile = file
        recorder = mediaRecorder
        startedAt = System.currentTimeMillis()
    }

    fun stop(): VoiceNoteResult? {
        val active = recorder ?: return null
        val duration = (System.currentTimeMillis() - startedAt).coerceAtLeast(0L)
        return try {
            active.stop()
            val file = outputFile
            if (file == null || !file.exists() || file.length() == 0L) null
            else VoiceNoteResult(file.readBytes(), duration, file.name)
        } finally {
            runCatching { active.release() }
            recorder = null
            outputFile?.delete()
            outputFile = null
            startedAt = 0L
        }
    }

    fun cancel() {
        val active = recorder
        if (active != null) {
            runCatching { active.stop() }
            runCatching { active.release() }
        }
        recorder = null
        outputFile?.delete()
        outputFile = null
        startedAt = 0L
    }
}

data class VoiceNoteResult(
    val bytes: ByteArray,
    val durationMs: Long,
    val fileName: String
)