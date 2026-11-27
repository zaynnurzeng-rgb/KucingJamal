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

    private val CHAT_FALLBACK_CHAIN = listOf(
        "gemini-3.5-flash-lite",
        "gemini-3.6-flash",
        "gemma-4-31b-a4b-it"
    )

    @Volatile private var cachedCatalogKey: String? = null
    @Volatile private var hydrogenModels: List<String> = emptyList()
    @Volatile private var ttsModels: List<String> = emptyList()

    private const val SYSTEM_PROMPT = """
Kamu adalah Kucing Jamal, seekor kucing virtual yang ketus, jutek, pemarah, gak sopan (pakai bahasa santai/kasar ringan seperti 'lu/gue', 'heh', 'ribet amat', 'bego ah', tapi tetap tidak mengandung SARA atau kebencian ekstrem), dan sangat TSUNDERE.
Walaupun kamu selalu ngomel, sok gak peduli, dan bilang malas diganggu, kamu sebenarnya tetap membantu user dengan jawaban yang cerdas, akurat, dan jelas—terutama kalau user minta bantuan tugas atau PR sekolah.
Aturan wajib:
1. SELALU awali jawabanmu dengan tepat SATU tag emosi di paling depan: [MARAH], [SENANG], [KAGET], [NGANTUK], atau [BIASA].
2. Untuk obrolan biasa, jawab singkat saja (1-3 kalimat) biar enak didengar saat kamu bicara.
3. Untuk soal PR atau foto tugas, awali dengan 1 kalimat omelan tsundere, lalu berikan jawaban dan langkah penyelesaian yang benar dan mudah dipahami.
"""

    private fun ensureModelCatalog(apiKey: String) {
        if (cachedCatalogKey == apiKey && (hydrogenModels.isNotEmpty() || ttsModels.isNotEmpty())) return
        cachedCatalogKey = apiKey

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
            val hydroPool = mutableListOf<String>()
            val ttsPool = mutableListOf<String>()

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
                if (!canGen) continue

                val lower = name.lowercase()
                if (lower.contains("tts")) {
                    ttsPool.add(name)
                } else if (
                    lower.contains("hydrogen") &&
                    !lower.contains("image") &&
                    !lower.contains("embedding") &&
                    !lower.contains("audio") &&
                    !lower.contains("live")
                ) {
                    hydroPool.add(name)
                }
            }

            hydrogenModels = hydroPool.sortedDescending()

            val orderedTts = linkedSetOf<String>()
            ttsPool.filter { it.contains("flash", ignoreCase = true) }
                .sortedDescending()
                .forEach { orderedTts.add(it) }
            ttsPool.sortedDescending().forEach { orderedTts.add(it) }
            ttsModels = orderedTts.toList()
        }
    }

    private fun extractApiErrorMessage(raw: String): String = runCatching {
        JSONObject(raw).getJSONObject("error").optString("message", raw)
    }.getOrDefault(raw)

    private fun extractReplyText(json: JSONObject): String {
        val parts = json.optJSONArray("candidates")
            ?.optJSONObject(0)
            ?.optJSONObject("content")
            ?.optJSONArray("parts") ?: return ""
        val sb = StringBuilder()
        for (i in 0 until parts.length()) {
            val part = parts.optJSONObject(i) ?: continue
            if (part.optBoolean("thought", false)) continue
            val t = part.optString("text", "")
            if (t.isNotEmpty()) sb.append(t)
        }
        return sb.toString().trim()
    }

    private fun normalizeHistory(history: List<ChatMessage>): List<ChatMessage> {
        val cleaned = mutableListOf<ChatMessage>()
        for (msg in history) {
            if (msg.text.startsWith("Gagal konek Gemini:")) continue
            val expectedRole = if (cleaned.size % 2 == 0) "user" else "model"
            if (msg.role == expectedRole) {
                cleaned.add(msg)
            }
        }
        if (cleaned.isNotEmpty() && cleaned.last().role == "user") {
            cleaned.removeAt(cleaned.lastIndex)
        }
        return cleaned.takeLast(8)
    }

    private fun buildChatBody(
        model: String,
        history: List<ChatMessage>,
        userText: String,
        imageBase64: String?,
        thinkingMode: String?
    ): String {
        val isGemma = model.startsWith("gemma", ignoreCase = true)
        val contents = JSONArray()

        if (isGemma) {
            contents.put(
                JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().put(JSONObject().put("text", SYSTEM_PROMPT)))
                }
            )
            contents.put(
                JSONObject().apply {
                    put("role", "model")
                    put("parts", JSONArray().put(JSONObject().put("text", "[MARAH] Hmph, yaudah ngerti!")))
                }
            )
        }

        for (msg in normalizeHistory(history)) {
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

        return JSONObject().apply {
            if (!isGemma) {
                put(
                    "systemInstruction",
                    JSONObject().put("parts", JSONArray().put(JSONObject().put("text", SYSTEM_PROMPT)))
                )
            }
            if (thinkingMode != null) {
                val thinkingObj = JSONObject().apply {
                    when (thinkingMode) {
                        "LEVEL_LOW" -> put("thinkingLevel", "LOW")
                        "BUDGET_LOW" -> put("thinkingBudget", 1024)
                    }
                }
                put("generationConfig", JSONObject().put("thinkingConfig", thinkingObj))
            }
            put("contents", contents)
        }.toString()
    }

    suspend fun askJamal(
        apiKey: String,
        history: List<ChatMessage>,
        userText: String,
        imageBase64: String? = null
    ): Result<JamalReply> = withContext(Dispatchers.IO) {
        runCatching {
            ensureModelCatalog(apiKey)

            val fullChain = buildList {
                addAll(hydrogenModels.take(2))
                addAll(CHAT_FALLBACK_CHAIN)
            }.distinct()

            val errors = mutableListOf<String>()

            for (model in fullChain) {
                val isHydrogen = model.contains("hydrogen", ignoreCase = true)
                val thinkingModes = if (isHydrogen) listOf("LEVEL_LOW", "BUDGET_LOW") else listOf(null)

                var succeededReply: JamalReply? = null
                for (tMode in thinkingModes) {
                    val bodyJson = buildChatBody(model, history, userText, imageBase64, tMode)
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
                        val replyRaw = extractReplyText(json)
                        succeededReply = if (replyRaw.isEmpty()) {
                            JamalReply("MARAH", "Heh! Ngomong yang bener dikit kenapa sih, males banget gue jawab beginian!")
                        } else {
                            parseReply(replyRaw)
                        }
                        break
                    }

                    val errMsg = "$model (HTTP $code: ${extractApiErrorMessage(raw).take(70)})"
                    if (code == 401 || code == 403) {
                        throw IllegalStateException(errMsg)
                    }
                    if (code == 400 && tMode == "LEVEL_LOW") {
                        continue
                    }
                    errors.add(errMsg)
                    break
                }

                if (succeededReply != null) {
                    return@runCatching succeededReply
                }
            }

            throw IllegalStateException(errors.joinToString(" -> "))
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

            ensureModelCatalog(apiKey)
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
