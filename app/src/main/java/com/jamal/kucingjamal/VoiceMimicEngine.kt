package com.jamal.kucingjamal

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.sqrt

class VoiceMimicEngine {
    @Volatile
    private var stopRequested = false

    fun requestStop() {
        stopRequested = true
    }

    @SuppressLint("MissingPermission")
    suspend fun recordAndPitchShift(
        pitchFactor: Float = 1.48f,
        maxDurationMs: Long = 8000L,
        onVoiceDetected: () -> Unit = {}
    ): Result<ByteArray> = withContext(Dispatchers.IO) {
        runCatching {
            stopRequested = false
            val inSampleRate = 16000
            val outSampleRate = 24000
            val channelConfig = AudioFormat.CHANNEL_IN_MONO
            val audioFormat = AudioFormat.ENCODING_PCM_16BIT

            val minBuf = AudioRecord.getMinBufferSize(inSampleRate, channelConfig, audioFormat)
            if (minBuf <= 0) throw IllegalStateException("Mikrofon tidak mendukung format audio.")

            val recorder = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                inSampleRate,
                channelConfig,
                audioFormat,
                maxOf(minBuf * 2, 8192)
            )

            if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                recorder.release()
                throw IllegalStateException("Gagal mengaktifkan mikrofon.")
            }

            val rawOut = ByteArrayOutputStream()
            val shortBuf = ShortArray(1024)
            val byteBuf = ByteArray(shortBuf.size * 2)

            var hasSpeech = false
            var lastSpeechAt = System.currentTimeMillis()
            val startTime = System.currentTimeMillis()
            val speechThreshold = 850.0
            val silenceAfterSpeechMs = 1150L

            try {
                recorder.startRecording()
                while (isActive && !stopRequested) {
                    val now = System.currentTimeMillis()
                    if (now - startTime >= maxDurationMs) break

                    val read = recorder.read(shortBuf, 0, shortBuf.size)
                    if (read <= 0) continue

                    var sumSq = 0.0
                    for (i in 0 until read) {
                        val v = shortBuf[i].toDouble()
                        sumSq += v * v
                    }
                    val rms = sqrt(sumSq / read)
                    if (rms > speechThreshold) {
                        if (!hasSpeech) {
                            hasSpeech = true
                            onVoiceDetected()
                        }
                        lastSpeechAt = now
                    } else if (hasSpeech && (now - lastSpeechAt) >= silenceAfterSpeechMs) {
                        break
                    }

                    val bb = ByteBuffer.wrap(byteBuf).order(ByteOrder.LITTLE_ENDIAN)
                    for (i in 0 until read) {
                        bb.putShort(shortBuf[i])
                    }
                    rawOut.write(byteBuf, 0, read * 2)
                }
            } finally {
                runCatching { recorder.stop() }
                runCatching { recorder.release() }
            }

            val rawBytes = rawOut.toByteArray()
            if (rawBytes.size < 3200) {
                throw IllegalStateException("Suaranya kependekan, ngomong lagi dong!")
            }

            val totalSamples = rawBytes.size / 2
            val samples = ShortArray(totalSamples)
            ByteBuffer.wrap(rawBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(samples)

            val trimThresh = 450
            var firstIdx = 0
            while (firstIdx < totalSamples && abs(samples[firstIdx].toInt()) < trimThresh) {
                firstIdx++
            }
            var lastIdx = totalSamples - 1
            while (lastIdx > firstIdx && abs(samples[lastIdx].toInt()) < trimThresh) {
                lastIdx--
            }

            val pad = inSampleRate / 10
            firstIdx = maxOf(0, firstIdx - pad)
            lastIdx = minOf(totalSamples - 1, lastIdx + pad)
            val trimmedLen = lastIdx - firstIdx + 1

            if (trimmedLen < 2000 || (!hasSpeech && !stopRequested)) {
                throw IllegalStateException("Gak kedengeran suara apa-apa!")
            }

            // Normalisasi volume agar suara kucing terdengar lantang & jernih
            var peak = 1
            for (i in firstIdx..lastIdx) {
                val a = abs(samples[i].toInt())
                if (a > peak) peak = a
            }
            val gain = minOf(3.2f, 26000f / peak.toFloat())

            // Resampling linear: pitch 1.48x sekaligus konversi ke 24kHz untuk PcmAudioPlayer
            val step = (inSampleRate.toFloat() * pitchFactor) / outSampleRate.toFloat()
            val outLen = ((trimmedLen - 1) / step).toInt()
            if (outLen <= 0) throw IllegalStateException("Gagal memproses suara.")

            val outBytes = ByteArray(outLen * 2)
            val outBb = ByteBuffer.wrap(outBytes).order(ByteOrder.LITTLE_ENDIAN)

            for (k in 0 until outLen) {
                val srcPos = k * step
                val idx0 = firstIdx + srcPos.toInt()
                val frac = srcPos - srcPos.toInt()
                val s0 = samples[idx0].toFloat()
                val s1 = if (idx0 + 1 <= lastIdx) samples[idx0 + 1].toFloat() else s0
                val mixed = (s0 + (s1 - s0) * frac) * gain
                val clamped = mixed.toInt()
                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                    .toShort()
                outBb.putShort(clamped)
            }

            outBytes
        }
    }
}
