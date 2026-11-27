package com.jamal.kucingjamal

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

data class ChatMessage(
    val role: String,
    val text: String
)

data class JamalReply(
    val emotion: String,
    val text: String
)

object GeminiClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(25, TimeUnit.SECONDS)
        .build()

    private val JSON_TYPE = "application/json; charset=utf-8".toMediaType()

    @Volatile private var cachedKey: String? = null
    @Volatile private var chatModels: List<String> = emptyList()
    @Volatile private var ttsModels: List<String> = emptyList()

    private const val SYSTEM_PROMPT = """
Kamu adalah Kucing Jamal, seekor kucing virtual yang ketus, jutek, pemarah, gak sopan (pakai bahasa santai/kasar ringan seperti 'lu/gue', 'heh', 'ribet amat', 'bego ah', tapi tetap tidak mengandung SARA atau kebencian ekstrem), dan sangat TSUNDERE.
Walaupun kamu selalu ngomel, sok gak peduli, dan bilang malas diganggu, kamu sebenarnya tetap membantu user dengan jawaban yang cerdas, akurat, dan jelas—terutama kalau user minta bantuan tugas atau PR sekolah.
Aturan wajib:
1. SELALU awali jawabanmu dengan tepat SATU tag emosi di paling depan: [MARAH], [SENANG], [KAGET], [NGANTUK], atau [BIASA].
2. Untuk obrolan biasa, jawab singkat saja (1-3 kalimat) biar enak didengar saat kamu bicara.
3. Untuk soal PR atau foto tugas, awali dengan 1 kalimat omelan tsundere, lalu berikan jawaban dan langkah penyelesaian yang benar dan mudah dipahami.
"""

    private fun ensureModels(apiKey: String) {
        if (cachedKey == apiKey && chatModels.isNotEmpty() && ttsModels.isNotEmpty()) return
        cachedKey = apiKey

        runCatching {
            val req = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models?pageSize=100&key=$apiKey")
                .get()
                .build()

            val (isOk, bodyStr) = client.newCall(req).execute().use { resp ->
                resp.isSuccessful to resp.body?.string().orEmpty()
            }
            if (!isOk) return

            val arr = JSONObject(bodyStr).optJSONArray("models") ?: return
            val genModels = mutableListOf<String>()

            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val name = obj.optString("name").removePrefix("models/")
                val methods = obj.optJSONArray("supportedGenerationMethods") ?: continue
                var canGen = false
                for (j in 0 until methods.length()) {
                    if (methods.optString(j) == "generateContent") {
                        canGen = true
                        break
                    }
                }
                if (canGen && name.startsWith("gemini-")) {
                    genModels.add(name)
                }
            }

            val chatPool = genModels.filter { m ->
                !m.contains("tts") &&
                    !m.contains("image") &&
                    !m.contains("embedding") &&
                    !m.contains("audio") &&
                    !m.contains("live") &&
                    !m.contains("robotics") &&
                    !m.contains("computer-use")
            }

            val orderedChat = linkedSetOf<String>()
            chatPool.filter {
                it.contains("flash") && !it.contains("lite") && !it.contains("preview") && !it.contains("exp") && !it.contains("latest")
            }.sortedDescending().forEach { orderedChat.add(it) }
            if ("gemini-flash-latest" in chatPool) orderedChat.add("gemini-flash-latest")
            chatPool.filter { it.contains("flash") && it.contains("lite") && !it.contains("preview") }
                .sortedDescending().forEach { orderedChat.add(it) }
            chatPool.filter { it.contains("flash") }
                .sortedDescending().forEach { orderedChat.add(it) }
            chatPool.sortedDescending().forEach { orderedChat.add(it) }
            chatModels = orderedChat.toList()

            val ttsPool = genModels.filter { it.contains("tts") }
            val orderedTts = linkedSetOf<String>()
            ttsPool.filter { it.contains("flash") }.sortedDescending().forEach { orderedTts.add(it) }
            ttsPool.sortedDescending().forEach { orderedTts.add(it) }
            ttsModels = orderedTts.toList()
        }
    }

    private fun extractApiErrorMessage(raw: String): String = runCatching {
        JSONObject(raw).getJSONObject("error").optString("message", raw)
    }.getOrDefault(raw)

    suspend fun askJamal(
        apiKey: String,
        history: List<ChatMessage>,
        userText: String,
        imageBase64: String? = null
    ): Result<JamalReply> = withContext(Dispatchers.IO) {
        runCatching {
            ensureModels(apiKey)
            val candidates = chatModels.take(4).ifEmpty {
                listOf("gemini-2.5-flash", "gemini-flash-latest", "gemini-2.5-flash-lite")
            }

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
            }.toString()

            var lastErr = "Server Gemini lagi sibuk."

            for (model in candidates) {
                for (attempt in 1..2) {
                    val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
                    val req = Request.Builder()
                        .url(url)
                        .post(bodyJson.toRequestBody(JSON_TYPE))
                        .build()

                    val (isOk, code, raw) = client.newCall(req).execute().use { resp ->
                        Triple(resp.isSuccessful, resp.code, resp.body?.string().orEmpty())
                    }

                    if (isOk) {
                        val json = JSONObject(raw)
                        val parts = json.optJSONArray("candidates")
                            ?.optJSONObject(0)
                            ?.optJSONObject("content")
                            ?.optJSONArray("parts")
                        val replyRaw = parts?.optJSONObject(0)?.optString("text")?.trim().orEmpty()
                        return@runCatching if (replyRaw.isEmpty()) {
                            JamalReply("MARAH", "Heh! Ngomong yang bener dikit kenapa sih, males banget gue jawab beginian!")
                        } else {
                            parseReply(replyRaw)
                        }
                    }

                    lastErr = "($model) HTTP $code: ${extractApiErrorMessage(raw)}"
                    if (code == 404) {
                        break
                    }
                    if (code == 503 || code == 429 || code >= 500) {
                        if (attempt == 1) delay(700L)
                        else break
                    } else {
                        throw IllegalStateException(lastErr)
                    }
                }
            }
            throw IllegalStateException(lastErr)
        }
    }

    private fun parseReply(raw: String): JamalReply {
        val regex = Regex("^\\[(MARAH|SENANG|KAGET|NGANTUK|BIASA)]\\s*", RegexOption.IGNORE_CASE)
        val match = regex.find(raw)
        val emotion = match?.groupValues?.get(1)?.uppercase() ?: "MARAH"
        val cleanText = raw.replaceFirst(regex, "").trim()
        return JamalReply(emotion, cleanText.ifEmpty { "Heh, apaan sih?!" })
    }

    private fun sha256(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    suspend fun synthesizeVoiceCached(
        context: Context,
        apiKey: String,
        text: String
    ): Result<ByteArray> = withContext(Dispatchers.IO) {
        runCatching {
            val clean = text
                .replace(Regex("[*_#`~]"), "")
                .replace(Regex("\\s+"), " ")
                .trim()
                .take(400)

            if (clean.isBlank()) throw IllegalArgumentException("Teks kosong")

            val cacheDir = File(context.cacheDir, "jamal_tts_v2").apply { mkdirs() }
            val cacheFile = File(cacheDir, "${sha256(clean)}.pcm")
            if (cacheFile.exists() && cacheFile.length() > 100L) {
                return@runCatching cacheFile.readBytes()
            }

            ensureModels(apiKey)
            val candidates = ttsModels.take(2).ifEmpty {
                listOf("gemini-2.5-flash-preview-tts")
            }

            val bodyJson = JSONObject().apply {
                put(
                    "contents",
                    JSONArray().put(
                        JSONObject().put(
                            "parts",
                            JSONArray().put(JSONObject().put("text", clean))
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
            }.toString()

            var lastErr = "Suara Gemini lagi sibuk."

            for (ttsModel in candidates) {
                for (attempt in 1..2) {
                    val url = "https://generativelanguage.googleapis.com/v1beta/models/$ttsModel:generateContent?key=$apiKey"
                    val req = Request.Builder()
                        .url(url)
                        .post(bodyJson.toRequestBody(JSON_TYPE))
                        .build()

                    val (isOk, code, raw) = client.newCall(req).execute().use { resp ->
                        Triple(resp.isSuccessful, resp.code, resp.body?.string().orEmpty())
                    }

                    if (isOk) {
                        val json = JSONObject(raw)
                        val b64 = json.getJSONArray("candidates")
                            .getJSONObject(0)
                            .getJSONObject("content")
                            .getJSONArray("parts")
                            .getJSONObject(0)
                            .getJSONObject("inlineData")
                            .getString("data")

                        val pcm = Base64.decode(b64, Base64.DEFAULT)
                        runCatching { cacheFile.writeBytes(pcm) }
                        return@runCatching pcm
                    }

                    lastErr = "($ttsModel) HTTP $code: ${extractApiErrorMessage(raw)}"
                    if (code == 503 || code == 429 || code >= 500) {
                        if (attempt == 1) delay(600L)
                        else break
                    } else if (code == 404) {
                        break
                    } else {
                        throw IllegalStateException(lastErr)
                    }
                }
            }
            throw IllegalStateException(lastErr)
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
            while (isActive && offset < pcm24kMono16Bit.size && currentTrack === track) {
                val len = minOf(chunk, pcm24kMono16Bit.size - offset)
                val written = track.write(pcm24kMono16Bit, offset, len)
                if (written <= 0) break
                offset += written
            }
            if (isActive && currentTrack === track) {
                track.stop()
            }
        } catch (_: Exception) {
        } finally {
            runCatching {
                track.pause()
                track.flush()
                track.release()
            }
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
