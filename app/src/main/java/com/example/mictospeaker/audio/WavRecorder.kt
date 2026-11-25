package com.example.mictospeaker.audio

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

class WavRecorder(private val context: Context) {

    private val recording = AtomicBoolean(false)
    private var tempFile: File? = null
    private var tempStream: BufferedOutputStream? = null
    private var reusableBytes = ByteArray(8192)
    private var totalPcmBytes: Long = 0L
    private var currentSampleRate: Int = 48_000

    val isRecording: Boolean get() = recording.get()

    @Synchronized
    fun start(sampleRate: Int) {
        if (recording.get()) return
        currentSampleRate = sampleRate
        totalPcmBytes = 0L

        val file = File(context.cacheDir, "mic_temp_${System.currentTimeMillis()}.pcm")
        tempFile = file
        tempStream = BufferedOutputStream(FileOutputStream(file), 16_384)
        recording.set(true)
    }

    fun writePcm(buffer: ShortArray, count: Int) {
        if (!recording.get() || count <= 0) return
        val bytesNeeded = count * 2
        if (reusableBytes.size < bytesNeeded) {
            reusableBytes = ByteArray(bytesNeeded)
        }

        var j = 0
        for (i in 0 until count) {
            val s = buffer[i].toInt()
            reusableBytes[j++] = (s and 0xFF).toByte()
            reusableBytes[j++] = ((s ushr 8) and 0xFF).toByte()
        }

        try {
            tempStream?.write(reusableBytes, 0, bytesNeeded)
            totalPcmBytes += bytesNeeded
        } catch (_: Exception) {
            // Abaikan error I/O parsial saat transisi stop
        }
    }

    @Synchronized
    fun stopAndSave(): String? {
        if (!recording.compareAndSet(true, false)) return null

        runCatching {
            tempStream?.flush()
            tempStream?.close()
        }
        tempStream = null

        val rawFile = tempFile ?: return null
        if (!rawFile.exists() || totalPcmBytes <= 0L) {
            rawFile.delete()
            return null
        }

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = "MicToSpeaker_$timestamp.wav"

        return try {
            val header = buildWavHeader(
                pcmDataLength = totalPcmBytes,
                sampleRate = currentSampleRate,
                channels = 1,
                bitsPerSample = 16
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Audio.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Audio.Media.MIME_TYPE, "audio/wav")
                    put(MediaStore.Audio.Media.RELATIVE_PATH, "${Environment.DIRECTORY_RECORDINGS}/MicToSpeaker")
                    put(MediaStore.Audio.Media.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
                    ?: return null

                resolver.openOutputStream(uri)?.use { out ->
                    writeHeaderAndRawData(out, header, rawFile)
                }

                values.clear()
                values.put(MediaStore.Audio.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            } else {
                val dir = File(
                    context.getExternalFilesDir(Environment.DIRECTORY_MUSIC),
                    "MicToSpeaker"
                ).apply { mkdirs() }
                FileOutputStream(File(dir, fileName)).use { out ->
                    writeHeaderAndRawData(out, header, rawFile)
                }
            }
            fileName
        } catch (_: Exception) {
            null
        } finally {
            rawFile.delete()
            tempFile = null
        }
    }

    private fun writeHeaderAndRawData(out: OutputStream, header: ByteArray, rawFile: File) {
        out.write(header)
        FileInputStream(rawFile).use { input ->
            val chunk = ByteArray(16_384)
            var read: Int
            while (input.read(chunk).also { read = it } != -1) {
                out.write(chunk, 0, read)
            }
        }
        out.flush()
    }

    private fun buildWavHeader(
        pcmDataLength: Long,
        sampleRate: Int,
        channels: Int,
        bitsPerSample: Int
    ): ByteArray {
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = (channels * bitsPerSample / 8).toShort()
        val totalChunkSize = 36 + pcmDataLength

        return ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII))
            putInt(totalChunkSize.toInt())
            put("WAVE".toByteArray(Charsets.US_ASCII))
            put("fmt ".toByteArray(Charsets.US_ASCII))
            putInt(16)
            putShort(1)
            putShort(channels.toShort())
            putInt(sampleRate)
            putInt(byteRate)
            putShort(blockAlign)
            putShort(bitsPerSample.toShort())
            put("data".toByteArray(Charsets.US_ASCII))
            putInt(pcmDataLength.toInt())
        }.array()
    }
}
