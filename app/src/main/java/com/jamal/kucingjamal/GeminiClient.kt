package com.jamal.kucingjamal

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class ChatMessage(
    val role: String, // "user" atau "model"
    val text: String
)

data class JamalReply(
    val emotion: String,
    val text: String
)

object GeminiClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val JSON_TYPE = "application/json; charset=utf-8".toMediaType()

    private const val SYSTEM_PROMPT = """
Kamu adalah Kucing Jamal, seekor kucing virtual yang ketus, jutek, pemarah, gak sopan (pakai bahasa santai/kasar ringan seperti 'lu/gue', 'heh', 'ribet amat', 'bego ah', tapi tetap tidak mengandung SARA atau kebencian ekstrem), dan sangat TSUNDERE.
Walaupun kamu selalu ngomel, sok gak peduli, dan bilang malas diganggu, kamu sebenarnya tetap membantu user dengan jawaban yang cerdas, akurat, dan jelas—terutama kalau user minta bantuan tugas atau PR sekolah.
Aturan wajib:
1. SELALU awali jawabanmu dengan tepat SATU tag emosi di paling depan: [MARAH], [SENANG], [KAGET], [NGANTUK], atau [BIASA].
2. Untuk obrolan biasa, jawab singkat saja (1-3 kalimat) biar enak didengar saat kamu bicara.
3. Untuk soal PR atau foto tugas, awali dengan 1 kalimat omelan tsundere, lalu berikan jawaban dan langkah penyelesaian yang benar dan mudah dipahami.
"""

    suspend fun askJamal(
        apiKey: String,
        history: List<ChatMessage>,
        userText: String,
        imageBase64: String? = null
    ): Result<JamalReply> = withContext(Dispatchers.IO) {
        runCatching {
            val contents = JSONArray()
            history.takeLast(8).forEach { msg ->
                contents.put(
                    JSONObject().apply {
                        put("role", msg.role)
                        put("parts", JSONArray().put(JSONObject().put("text", msg.text)))
                    }
                )
            }

            val userParts = JSONArray()
            if (imageBase64 != null) {
                userParts.put(
                    JSONObject().put(
                        "inlineData",
                        JSONObject().apply {
                            put("mimeType", "image/jpeg")
                            put("data", imageBase64)
                        }
                    )
                )
            }
            userParts.put(JSONObject().put("text", userText))
            contents.put(
                JSONObject().apply {
                    put("role", "user")
                    put("parts", userParts)
                }
            )

            val bodyJson = JSONObject().apply {
                put(
                    "systemInstruction",
                    JSONObject().put("parts", JSONArray().put(JSONObject().put("text", SYSTEM_PROMPT)))
                )
                put("contents", contents)
            }

            val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey"
            val req = Request.Builder()
                .url(url)
                .post(bodyJson.toString().toRequestBody(JSON_TYPE))
                .build()

            client.newCall(req).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    throw IllegalStateException("Gemini Error ${resp.code}: $raw")
                }
                val json = JSONObject(raw)
                val replyRaw = json.getJSONArray("candidates")
                    .getJSONObject(0)
                    .getJSONObject("content")
                    .getJSONArray("parts")
                    .getJSONObject(0)
                    .getString("text")
                    .trim()

                parseReply(replyRaw)
            }
        }
    }

    private fun parseReply(raw: String): JamalReply {
        val regex = Regex("^\\[(MARAH|SENANG|KAGET|NGANTUK|BIASA)]\\s*", RegexOption.IGNORE_CASE)
        val match = regex.find(raw)
        val emotion = match?.groupValues?.get(1)?.uppercase() ?: "MARAH"
        val cleanText = raw.replaceFirst(regex, "").trim()
        return JamalReply(emotion, cleanText.ifEmpty { "Heh, apaan sih?!" })
    }

    suspend fun synthesizeVoice(apiKey: String, text: String): Result<ByteArray> =
        withContext(Dispatchers.IO) {
            runCatching {
                val clean = text
                    .replace(Regex("[*_#`~]"), "")
                    .replace(Regex("\\s+"), " ")
                    .trim()
                    .take(380)

                val prompt = "Ucapkan kalimat bahasa Indonesia berikut dengan nada ketus, jutek, agak kesal, tapi tsundere seperti kucing pemarah: $clean"

                val bodyJson = JSONObject().apply {
                    put(
                        "contents",
                        JSONArray().put(
                            JSONObject().put(
                                "parts",
                                JSONArray().put(JSONObject().put("text", prompt))
                            )
                        )
                    )
                    put(
                        "generationConfig",
                        JSONObject().apply {
                            put("responseModalities", JSONArray().put("AUDIO"))
                            put(
                                "speechConfig",
                                JSONObject().put(
                                    "voiceConfig",
                                    JSONObject().put(
                                        "prebuiltVoiceConfig",
                                        JSONObject().put("voiceName", "Puck")
                                    )
                                )
                            )
                        }
                    )
                }

                val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-preview-tts:generateContent?key=$apiKey"
                val req = Request.Builder()
                    .url(url)
                    .post(bodyJson.toString().toRequestBody(JSON_TYPE))
                    .build()

                client.newCall(req).execute().use { resp ->
                    val raw = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        throw IllegalStateException("TTS Error ${resp.code}: $raw")
                    }
                    val json = JSONObject(raw)
                    val b64 = json.getJSONArray("candidates")
                        .getJSONObject(0)
                        .getJSONObject("content")
                        .getJSONArray("parts")
                        .getJSONObject(0)
                        .getJSONObject("inlineData")
                        .getString("data")

                    Base64.decode(b64, Base64.DEFAULT)
                }
            }
        }
}

class PcmAudioPlayer {
    @Volatile
    private var currentTrack: AudioTrack? = null

    suspend fun play(pcm24kMono16Bit: ByteArray) = withContext(Dispatchers.IO) {
        stop()
        if (pcm24kMono16Bit.isEmpty()) return@withContext

        val sampleRate = 24000
        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(maxOf(minBuf, 8192))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        currentTrack = track
        try {
            track.play()
            var offset = 0
            val chunk = 4096
            while (offset < pcm24kMono16Bit.size && currentTrack === track) {
                val len = minOf(chunk, pcm24kMono16Bit.size - offset)
                val written = track.write(pcm24kMono16Bit, offset, len)
                if (written <= 0) break
                offset += written
            }
            if (currentTrack === track) {
                track.stop()
            }
        } catch (_: Exception) {
        } finally {
            runCatching { track.release() }
            if (currentTrack === track) currentTrack = null
        }
    }

    fun stop() {
        val t = currentTrack
        currentTrack = null
        runCatching {
            t?.pause()
            t?.flush()
            t?.stop()
            t?.release()
        }
    }
}
