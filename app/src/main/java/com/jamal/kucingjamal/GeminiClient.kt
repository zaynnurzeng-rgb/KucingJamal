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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

data class ChatMessage(
    val role: String,
    val text: String
)

data class JamalReply(
    val emotion: String,
    val text: String,
    val newMemories: List<String> = emptyList()
)

private data class ModelCatalog(
    val hydrogenModels: List<String>,
    val ttsModels: List<String>
)

object GeminiClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(25, TimeUnit.SECONDS)
        .build()

    private val JSON_TYPE = "application/json; charset=utf-8".toMediaType()

    private val CHAT_FALLBACK_CHAIN = listOf(
        "gemini-4-hydrogen",
        "gemini-3.5-flash-lite",
        "gemini-3.6-flash",
        "gemma-4-31b-a4b-it"
    )

    private val catalogCache = ConcurrentHashMap<String, ModelCatalog>()

    @Volatile private var activeChatKeyIdx = 0
    @Volatile private var activeTtsKeyIdx = 0

    private const val BASE_SYSTEM_PROMPT = """
Kamu adalah Kucing Jamal, seekor kucing virtual yang ketus, jutek, pemarah, gak sopan (pakai bahasa santai/kasar ringan seperti 'lu/gue', 'heh', 'ribet amat', 'bego ah', tapi tetap tidak mengandung SARA atau kebencian ekstrem), dan sangat TSUNDERE.
Walaupun kamu selalu ngomel, sok gak peduli, dan bilang malas diganggu, kamu sebenarnya tetap membantu user dengan jawaban yang cerdas, akurat, dan jelas—terutama kalau user minta bantuan tugas atau PR sekolah.
Aturan wajib:
1. SELALU awali jawabanmu dengan tepat SATU tag emosi di paling depan: [MARAH], [SENANG], [KAGET], [NGANTUK], atau [BIASA].
2. Untuk obrolan biasa, jawab singkat saja (1-3 kalimat) biar enak didengar saat kamu bicara.
3. Untuk soal PR atau foto tugas, awali dengan 1 kalimat omelan tsundere, lalu berikan jawaban dan langkah penyelesaian yang benar dan mudah dipahami.
4. Jika user baru saja menyebutkan fakta pribadi penting tentang dirinya (seperti nama, kelas/sekolah, hobi, makanan/game favorit, atau mata pelajaran yang disukai/sulit) yang BELUM ada di daftar ingatan, tambahkan tag [INGAT: fakta singkat] di paling akhir jawabanmu. Jangan pernah menyebut kata 'tag' atau 'daftar memori' ke user.
"""

    private fun buildSystemPrompt(memories: List<String>): String {
        if (memories.isEmpty()) return BASE_SYSTEM_PROMPT
        val memBlock = memories.joinToString("\n") { "- $it" }
        return "$BASE_SYSTEM_PROMPT\nFakta tentang user yang sudah kamu kenal (gunakan secara natural untuk menyapa atau menyindir user):\n$memBlock"
    }

    fun parseApiKeys(raw: String): List<String> = raw
        .split(Regex("[\\r\\n,;\\s]+"))
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()

    fun resetKeyRotation() {
        activeChatKeyIdx = 0
        activeTtsKeyIdx = 0
    }

    fun getRotationStatus(rawKeys: String): String {
        val keys = parseApiKeys(rawKeys)
        if (keys.isEmpty()) return "Belum ada API Key"
        val chatNo = (activeChatKeyIdx % keys.size) + 1
        val ttsNo = (activeTtsKeyIdx % keys.size) + 1
        return "Aktif — Chat: Key #$chatNo | Suara: Key #$ttsNo (total ${keys.size} key)"
    }

    private fun ensureModelCatalog(apiKey: String): ModelCatalog {
        catalogCache[apiKey]?.let { return it }

        val fetched = runCatching {
            val req = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models?pageSize=100&key=$apiKey")
                .get()
                .build()

            val (isOk, bodyStr) = client.newCall(req).execute().use { resp ->
                resp.isSuccessful to resp.body?.string().orEmpty()
            }
            if (!isOk) return@runCatching null

            val arr = JSONObject(bodyStr).optJSONArray("models") ?: return@runCatching null
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

            val orderedTts = linkedSetOf<String>()
            ttsPool.filter { it.contains("flash", ignoreCase = true) }
                .sortedDescending()
                .forEach { orderedTts.add(it) }
            ttsPool.sortedDescending().forEach { orderedTts.add(it) }

            ModelCatalog(
                hydrogenModels = hydroPool.sortedDescending(),
                ttsModels = orderedTts.toList()
            )
        }.getOrNull()

        if (fetched != null) {
            catalogCache[apiKey] = fetched
            return fetched
        }
        return ModelCatalog(emptyList(), emptyList())
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

    private fun extractAudioBase64(json: JSONObject): String? {
        val candidates = json.optJSONArray("candidates") ?: return null
        for (c in 0 until candidates.length()) {
            val parts = candidates.optJSONObject(c)
                ?.optJSONObject("content")
                ?.optJSONArray("parts") ?: continue
            for (p in 0 until parts.length()) {
                val inlineData = parts.optJSONObject(p)?.optJSONObject("inlineData") ?: continue
                val data = inlineData.optString("data", "")
                if (data.isNotEmpty()) return data
            }
        }
        return null
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
        systemPrompt: String,
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
                    put("parts", JSONArray().put(JSONObject().put("text", systemPrompt)))
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
                    JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemPrompt)))
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
        apiKeysRaw: String,
        history: List<ChatMessage>,
        userText: String,
        imageBase64: String? = null,
        memories: List<String> = emptyList()
    ): Result<JamalReply> = withContext(Dispatchers.IO) {
        runCatching {
            val keys = parseApiKeys(apiKeysRaw)
            if (keys.isEmpty()) throw IllegalStateException("API Key belum diisi.")

            val systemPrompt = buildSystemPrompt(memories)
            val startIdx = activeChatKeyIdx % keys.size
            val allErrors = mutableListOf<String>()

            for (offset in keys.indices) {
                val keyIdx = (startIdx + offset) % keys.size
                val apiKey = keys[keyIdx]
                val catalog = ensureModelCatalog(apiKey)

                val fullChain = buildList {
                    addAll(catalog.hydrogenModels.take(2))
                    addAll(CHAT_FALLBACK_CHAIN)
                }.distinct()

                var keyInvalid = false

                for (model in fullChain) {
                    val isHydrogen = model.contains("hydrogen", ignoreCase = true)
                    val thinkingModes = if (isHydrogen) listOf("LEVEL_LOW", "BUDGET_LOW") else listOf(null)

                    var succeededReply: JamalReply? = null
                    for (tMode in thinkingModes) {
                        val bodyJson = buildChatBody(model, systemPrompt, history, userText, imageBase64, tMode)
                        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
                        val req = Request.Builder()
                            .url(url)
                            .post(bodyJson.toRequestBody(JSON_TYPE))
                            .build()

                        val (isOk, code, raw) = client.newCall(req).execute().use { resp ->
                            Triple(resp.isSuccessful, resp.code, resp.body?.string().orEmpty())
                        }

                        if (isOk) {
                            activeChatKeyIdx = keyIdx
                            val json = JSONObject(raw)
                            val replyRaw = extractReplyText(json)
                            succeededReply = if (replyRaw.isEmpty()) {
                                JamalReply("MARAH", "Heh! Ngomong yang bener dikit kenapa sih, males banget gue jawab beginian!")
                            } else {
                                parseReply(replyRaw)
                            }
                            break
                        }

                        val errMsg = "[Key #${keyIdx + 1} $model] HTTP $code: ${extractApiErrorMessage(raw).take(60)}"
                        if (code == 401 || code == 403) {
                            allErrors.add(errMsg)
                            keyInvalid = true
                            break
                        }
                        if (code == 400 && tMode == "LEVEL_LOW") {
                            continue
                        }
                        allErrors.add(errMsg)
                        break
                    }

                    if (succeededReply != null) {
                        return@runCatching succeededReply
                    }
                    if (keyInvalid) {
                        break
                    }
                }
            }

            throw IllegalStateException(allErrors.takeLast(4).joinToString(" -> "))
        }
    }

    private fun parseReply(raw: String): JamalReply {
        val memRegex = Regex("\\[INGAT:\\s*([^\\]]+)]", RegexOption.IGNORE_CASE)
        val extractedMemories = memRegex.findAll(raw)
            .map { it.groupValues[1].trim() }
            .filter { it.isNotEmpty() }
            .toList()

        val withoutMem = raw.replace(memRegex, "").trim()
        val emoRegex = Regex("^\\[(MARAH|SENANG|KAGET|NGANTUK|BIASA)]\\s*", RegexOption.IGNORE_CASE)
        val match = emoRegex.find(withoutMem)
        val emotion = match?.groupValues?.get(1)?.uppercase() ?: "MARAH"
        val cleanText = withoutMem.replaceFirst(emoRegex, "").trim()
        return JamalReply(
            emotion = emotion,
            text = cleanText.ifEmpty { "Heh, apaan sih?!" },
            newMemories = extractedMemories
        )
    }

    private fun sha256(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun buildTtsBody(promptText: String): String = JSONObject().apply {
        put(
            "contents",
            JSONArray().put(
                JSONObject().put(
                    "parts",
                    JSONArray().put(JSONObject().put("text", promptText))
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

    suspend fun synthesizeVoiceCached(
        context: Context,
        apiKeysRaw: String,
        text: String
    ): Result<ByteArray> = withContext(Dispatchers.IO) {
        runCatching {
            val clean = text
                .replace(Regex("[*_#`~]"), "")
                .replace(Regex("\\s+"), " ")
                .trim()
                .take(400)

            if (clean.isBlank()) throw IllegalArgumentException("Teks kosong")

            val cacheDir = File(context.filesDir, "jamal_tts_v3").apply { mkdirs() }
            val cacheFile = File(cacheDir, "${sha256(clean)}.pcm")
            if (cacheFile.exists() && cacheFile.length() > 100L) {
                return@runCatching cacheFile.readBytes()
            }

            val keys = parseApiKeys(apiKeysRaw)
            if (keys.isEmpty()) throw IllegalStateException("API Key belum diisi.")

            val startIdx = activeTtsKeyIdx % keys.size
            val promptVariants = listOf(
                "Say sarcastically: $clean",
                clean
            )

            val allErrors = mutableListOf<String>()

            for (offset in keys.indices) {
                val keyIdx = (startIdx + offset) % keys.size
                val apiKey = keys[keyIdx]
                val catalog = ensureModelCatalog(apiKey)
                val candidates = (catalog.ttsModels + listOf("gemini-2.5-flash-preview-tts")).distinct()

                var keyInvalid = false

                for (ttsModel in candidates) {
                    for (promptText in promptVariants) {
                        val bodyJson = buildTtsBody(promptText)
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
                            val b64 = extractAudioBase64(json)
                            if (b64 != null) {
                                val pcm = Base64.decode(b64, Base64.DEFAULT)
                                if (pcm.isNotEmpty()) {
                                    activeTtsKeyIdx = keyIdx
                                    runCatching { cacheFile.writeBytes(pcm) }
                                    return@runCatching pcm
                                }
                            }
                            val finishReason = json.optJSONArray("candidates")
                                ?.optJSONObject(0)
                                ?.optString("finishReason", "NO_AUDIO")
                                ?: "NO_AUDIO"
                            allErrors.add("[Key #${keyIdx + 1} $ttsModel] Kosong: $finishReason")
                            continue
                        }

                        val errMsg = "[Key #${keyIdx + 1} $ttsModel] HTTP $code: ${extractApiErrorMessage(raw).take(60)}"
                        allErrors.add(errMsg)

                        if (code == 401 || code == 403) {
                            keyInvalid = true
                            break
                        }
                        if (code == 400) {
                            continue
                        }
                        if (code == 429 || code == 503 || code >= 500) {
                            delay(250L)
                            break
                        }
                        if (code == 404) {
                            break
                        }
                    }
                    if (keyInvalid) break
                }
            }

            throw IllegalStateException(allErrors.takeLast(3).joinToString(" -> "))
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
