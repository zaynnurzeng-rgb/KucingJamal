package com.jamal.kucingjamal

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.AudioAttributes
import android.media.ExifInterface
import android.media.SoundPool
import android.net.Uri
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Locale
import kotlin.math.hypot
import kotlin.math.roundToInt

enum class JamalAnim(val folder: String, val frames: Int, val sound: String? = null) {
    IDLE("idle", 1),
    BLINK("blink", 1),
    LISTEN("listen", 1),
    TALK("talk", 16),
    ANGRY("angry", 24, "angry.wav"),
    CYMBAL("cymbal", 11, "cymbal.wav"),
    DRINK("drink", 79, "p_drink_milk.wav"),
    EAT("eat", 38, "p_eat.wav"),
    FART("fart", 26, "fart001.wav"),
    FOOT_LEFT("foot_left", 28, "p_foot3.wav"),
    FOOT_RIGHT("foot_right", 28, "p_foot4.wav"),
    HAPPY_SHORT("happy_short", 23, "purr.wav"),
    KNOCKOUT("knockout", 79, "slap1.wav"),
    PIE("pie", 21, "splat_01.wav"),
    SCRATCH("scratch", 54, "scratch_kratzen.wav"),
    SNEEZE("sneeze", 12, "p_sneeze.wav"),
    STOMACH("stomach", 32, "p_belly1.wav"),
    YAWN("yawn", 29, "p_yawn2_11a.wav")
}

private data class PreparedPrPhoto(
    val preview: ImageBitmap,
    val base64: String
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                JamalApp()
            }
        }
    }
}

private fun loadFrameBitmap(context: Context, path: String): ImageBitmap? = runCatching {
    val opts = BitmapFactory.Options().apply {
        inPreferredConfig = Bitmap.Config.RGB_565
    }
    context.assets.open(path).use { stream ->
        BitmapFactory.decodeStream(stream, null, opts)?.asImageBitmap()
    }
}.getOrNull()

private fun loadUiIcon(context: Context, fileName: String): ImageBitmap? = runCatching {
    context.assets.open("tom/ui/$fileName").use { stream ->
        BitmapFactory.decodeStream(stream)?.asImageBitmap()
    }
}.getOrNull()

private fun preparePrPhotoFromUri(context: Context, uri: Uri): PreparedPrPhoto? = runCatching {
    val rawBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        ?: return null

    val orientation = runCatching {
        ExifInterface(ByteArrayInputStream(rawBytes)).getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL
        )
    }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

    val orig = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size) ?: return null
    val maxSide = 1280
    val scale = minOf(1f, maxSide.toFloat() / maxOf(orig.width, orig.height))
    val scaled = if (scale < 1f) {
        Bitmap.createScaledBitmap(
            orig,
            (orig.width * scale).toInt(),
            (orig.height * scale).toInt(),
            true
        )
    } else {
        orig
    }

    val matrix = Matrix().apply {
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(270f)
        }
    }
    val finalBmp = if (!matrix.isIdentity) {
        Bitmap.createBitmap(scaled, 0, 0, scaled.width, scaled.height, matrix, true)
    } else {
        scaled
    }

    val out = ByteArrayOutputStream()
    finalBmp.compress(Bitmap.CompressFormat.JPEG, 84, out)
    val b64 = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    PreparedPrPhoto(preview = finalBmp.asImageBitmap(), base64 = b64)
}.getOrNull()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KucingJamalScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("jamal_prefs", Context.MODE_PRIVATE) }

    var apiKeysRaw by remember {
        mutableStateOf(
            prefs.getString("gemini_api_key", "")?.ifBlank { BuildConfig.GEMINI_API_KEY }
                ?: BuildConfig.GEMINI_API_KEY
        )
    }
    var appSettings by remember { mutableStateOf(AppSettingsStorage.load(context)) }
    var showSettings by remember { mutableStateOf(false) }
    var showMemoryDialog by remember { mutableStateOf(false) }
    var showChat by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var showPhotoSourceDialog by remember { mutableStateOf(false) }
    var pendingPrPhoto by remember { mutableStateOf<PreparedPrPhoto?>(null) }
    var prNoteInput by remember { mutableStateOf("") }
    var cameraPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var pendingMicAction by remember { mutableStateOf("ai") }

    var chatInput by remember { mutableStateOf("") }
    var messages by remember { mutableStateOf(ChatStorage.load(context)) }
    var memories by remember { mutableStateOf(MemoryStorage.load(context)) }
    var petStatus by remember { mutableStateOf(PetStateStorage.load(context)) }

    var isBusy by remember { mutableStateOf(false) }
    var isSpeaking by remember { mutableStateOf(false) }
    var isListening by remember { mutableStateOf(false) }
    var isMimicRecording by remember { mutableStateOf(false) }
    var speechJob by remember { mutableStateOf<Job?>(null) }
    var mimicJob by remember { mutableStateOf<Job?>(null) }
    var ttsErrorBanner by remember { mutableStateOf<String?>(null) }
    var lastInteractionAt by remember { mutableLongStateOf(System.currentTimeMillis()) }

    val petLines = remember {
        listOf(
            "Hmph! Jangan elus-elus, bukan berarti gue suka ya!",
            "Ih apaan sih pegang-pegang... eh bentar, lanjut dikit boleh deh.",
            "Ck, sok akrab banget ngelus-ngelus gue!"
        )
    }
    val eatLines = remember {
        listOf(
            "Hmph, lumayan enak... bukan berarti gue berterima kasih ya!",
            "Akhirnya dikasih makan juga! Tadi perut gue hampir protes.",
            "Nyam nyam... yaudah deh, kali ini gue maafin lu."
        )
    }

    val audioPlayer = remember { PcmAudioPlayer() }
    val voiceMimicEngine = remember { VoiceMimicEngine() }
    val soundPool = remember {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        SoundPool.Builder().setMaxStreams(4).setAudioAttributes(attrs).build()
    }

    val soundIds = remember {
        val map = mutableMapOf<String, Int>()
        JamalAnim.entries.mapNotNull { it.sound }.distinct().forEach { file ->
            runCatching {
                context.assets.openFd("tom/sounds/$file").use { afd ->
                    map[file] = soundPool.load(afd, 1)
                }
            }
        }
        map
    }

    val idleBitmap = remember { loadFrameBitmap(context, "tom/animations/idle/frame_001.webp") }
    val blinkBitmap = remember { loadFrameBitmap(context, "tom/animations/blink/frame_001.webp") }
    val listenBitmap = remember { loadFrameBitmap(context, "tom/animations/listen/frame_001.webp") }

    var currentFrame by remember { mutableStateOf(idleBitmap) }
    var currentAnim by remember { mutableStateOf(JamalAnim.IDLE) }
    var triggerCount by remember { mutableIntStateOf(0) }
    var statusText by remember {
        mutableStateOf(
            when {
                petStatus.hunger <= 30 -> "Woi! Gue laper nih, kasih makan dulu kek!"
                petStatus.mood <= 30 -> "Ck, mood gue lagi jelek. Jangan macem-macem!"
                else -> "Heh, mau ngapain lu?!"
            }
        )
    }

    LaunchedEffect(messages) {
        withContext(Dispatchers.IO) {
            ChatStorage.save(context, messages)
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(180_000L)
            petStatus = PetStateStorage.load(context)
        }
    }

    fun hasValidKeys(): Boolean = GeminiClient.parseApiKeys(apiKeysRaw).isNotEmpty()

    fun markActive() {
        lastInteractionAt = System.currentTimeMillis()
    }

    fun updateStats(deltaHunger: Int = 0, deltaMood: Int = 0) {
        petStatus = PetStateStorage.adjust(context, petStatus, deltaHunger, deltaMood)
    }

    fun stopCurrentSpeech() {
        speechJob?.cancel()
        speechJob = null
        voiceMimicEngine.requestStop()
        mimicJob?.cancel()
        mimicJob = null
        isMimicRecording = false
        audioPlayer.stop()
        isSpeaking = false
    }

    fun speakBubble(
        text: String,
        delayMs: Long = 0L,
        forceTalkAnim: Boolean = false,
        voiceOverride: String? = null,
        onDone: (() -> Unit)? = null
    ) {
        if (!hasValidKeys() || text.isBlank()) {
            onDone?.invoke()
            return
        }
        stopCurrentSpeech()
        val chosenVoice = voiceOverride ?: appSettings.voiceName
        speechJob = scope.launch {
            val res = GeminiClient.synthesizeVoiceCached(context, apiKeysRaw, text, chosenVoice)
            if (delayMs > 0L) delay(delayMs)
            res.onSuccess { pcm ->
                ttsErrorBanner = null
                isSpeaking = true
                if (forceTalkAnim || currentAnim == JamalAnim.IDLE || currentAnim == JamalAnim.LISTEN) {
                    currentAnim = JamalAnim.TALK
                    triggerCount++
                }
                audioPlayer.play(pcm)
                isSpeaking = false
                markActive()
                if (currentAnim == JamalAnim.TALK) {
                    currentAnim = JamalAnim.IDLE
                }
                onDone?.invoke()
            }.onFailure { err ->
                isSpeaking = false
                ttsErrorBanner = "Suara Gemini gagal: ${err.message?.take(140)}"
                if (currentAnim == JamalAnim.TALK) {
                    currentAnim = JamalAnim.IDLE
                }
                onDone?.invoke()
            }
        }
    }

    fun playAnim(
        anim: JamalAnim,
        caption: String? = null,
        speak: Boolean = true,
        deltaHunger: Int = 0,
        deltaMood: Int = 0
    ) {
        markActive()
        if (deltaHunger != 0 || deltaMood != 0) {
            updateStats(deltaHunger, deltaMood)
        }
        currentAnim = anim
        triggerCount++
        if (caption != null) statusText = caption
        anim.sound?.let { soundName ->
            soundIds[soundName]?.let { id ->
                soundPool.play(id, 1f, 1f, 1, 0, 1f)
            }
        }
        if (speak && caption != null) {
            speakBubble(caption, delayMs = if (anim.sound != null) 350L else 0L)
        }
    }

    LaunchedEffect(Unit) {
        markActive()
        if (hasValidKeys()) {
            delay(350L)
            speakBubble(statusText, forceTalkAnim = true)
        }
    }

    fun sendToJamal(prompt: String, imageBase64: String? = null, displayPrompt: String = prompt) {
        markActive()
        if (!hasValidKeys()) {
            statusText = "Isi Gemini API Key dulu di tombol Option kanan atas!"
            showSettings = true
            return
        }
        if (isBusy) return
        stopCurrentSpeech()
        isBusy = true
        messages = messages + ChatMessage("user", displayPrompt)
        statusText = "Bentar, ribet amat sih..."
        currentAnim = JamalAnim.IDLE

        scope.launch {
            val res = GeminiClient.askJamal(
                apiKeysRaw = apiKeysRaw,
                history = messages.dropLast(1),
                userText = prompt,
                imageBase64 = imageBase64,
                memories = memories,
                petStatus = petStatus
            )
            res.onSuccess { reply ->
                if (reply.newMemories.isNotEmpty()) {
                    memories = MemoryStorage.merge(context, memories, reply.newMemories)
                }
                updateStats(deltaHunger = -3, deltaMood = 3)
                messages = messages + ChatMessage("model", reply.text)
                statusText = reply.text.replace("**", "")
                isBusy = false
                markActive()

                speakBubble(reply.text, forceTalkAnim = true) {
                    when (reply.emotion) {
                        "MARAH" -> playAnim(JamalAnim.ANGRY, speak = false)
                        "SENANG" -> playAnim(JamalAnim.HAPPY_SHORT, speak = false)
                        "KAGET" -> playAnim(JamalAnim.KNOCKOUT, speak = false)
                        "NGANTUK" -> playAnim(JamalAnim.YAWN, speak = false)
                        else -> currentAnim = JamalAnim.IDLE
                    }
                }
            }.onFailure { err ->
                isBusy = false
                val shortErr = err.message?.take(240) ?: "Server sibuk"
                val chatMsg = "Gagal konek Gemini: $shortErr"
                messages = messages + ChatMessage("model", chatMsg)
                playAnim(JamalAnim.ANGRY, "Aduh servernya lagi rame banget, coba lagi bentar!")
            }
        }
    }

    fun startOfflineMimic() {
        markActive()
        if (isMimicRecording) {
            voiceMimicEngine.requestStop()
            return
        }
        stopCurrentSpeech()
        isMimicRecording = true
        playAnim(JamalAnim.LISTEN, "Mode Tirukan: ngomong aja, nanti gue ledek!", speak = false)

        mimicJob = scope.launch {
            val res = voiceMimicEngine.recordAndPitchShift(
                pitchFactor = appSettings.mimicPitch,
                maxDurationMs = 8000L,
                onVoiceDetected = {
                    scope.launch {
                        statusText = "Lagi dengerin suara lu... (tap lagi buat stop)"
                    }
                }
            )
            isMimicRecording = false

            res.onSuccess { pitchedPcm ->
                updateStats(deltaMood = 4)
                statusText = "Hehe, dengerin suara lu sendiri nih!"
                isSpeaking = true
                currentAnim = JamalAnim.TALK
                triggerCount++
                audioPlayer.play(pitchedPcm)
                isSpeaking = false
                markActive()
                if (currentAnim == JamalAnim.TALK) {
                    currentAnim = JamalAnim.IDLE
                }
            }.onFailure { err ->
                isSpeaking = false
                currentAnim = JamalAnim.IDLE
                statusText = err.message ?: "Gagal rekam suara."
            }
        }
    }

    fun loadPrImageForPreview(uri: Uri) {
        markActive()
        scope.launch {
            val prepared = withContext(Dispatchers.IO) { preparePrPhotoFromUri(context, uri) }
            if (prepared != null) {
                prNoteInput = ""
                pendingPrPhoto = prepared
            } else {
                statusText = "Gagal baca fotonya, coba foto ulang deh."
            }
        }
    }

    val speechRecognizer = remember {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            SpeechRecognizer.createSpeechRecognizer(context)
        } else null
    }

    DisposableEffect(Unit) {
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                isListening = true
                playAnim(JamalAnim.LISTEN, "Ngomong cepetan, gue dengerin...", speak = false)
            }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { isListening = false }
            override fun onError(error: Int) {
                isListening = false
                currentAnim = JamalAnim.IDLE
                val msg = "Gak kedengeran! Ngomong yang jelas dong."
                statusText = msg
                speakBubble(msg, forceTalkAnim = true)
            }
            override fun onResults(results: Bundle?) {
                isListening = false
                val spoken = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                if (!spoken.isNullOrBlank()) {
                    sendToJamal(spoken)
                } else {
                    currentAnim = JamalAnim.IDLE
                }
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        onDispose {
            stopCurrentSpeech()
            soundPool.release()
            speechRecognizer?.destroy()
        }
    }

    fun startVoiceInput() {
        markActive()
        if (speechRecognizer == null) {
            val msg = "HP ini gak dukung mikrofon otomatis, pakai Chat aja."
            statusText = msg
            speakBubble(msg, forceTalkAnim = true)
            return
        }
        stopCurrentSpeech()
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "id-ID")
        }
        speechRecognizer.startListening(intent)
    }

    val micPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            if (pendingMicAction == "mimic") startOfflineMimic()
            else startVoiceInput()
        } else {
            val msg = "Izin mikrofon ditolak, gimana gue mau denger?!"
            statusText = msg
            speakBubble(msg, forceTalkAnim = true)
        }
    }

    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) loadPrImageForPreview(uri)
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = cameraPhotoUri
        if (success && uri != null) {
            loadPrImageForPreview(uri)
        }
    }

    fun launchCameraForPr() {
        markActive()
        runCatching {
            val dir = File(context.cacheDir, "pr_photos").apply { mkdirs() }
            val photoFile = File(dir, "pr_${System.currentTimeMillis()}.jpg")
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                photoFile
            )
            cameraPhotoUri = uri
            cameraLauncher.launch(uri)
        }.onFailure {
            statusText = "Gagal buka kamera: ${it.message?.take(60)}"
        }
    }

    val cameraPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) launchCameraForPr()
        else {
            val msg = "Izin kamera ditolak, gimana mau foto soal PR?!"
            statusText = msg
            speakBubble(msg, forceTalkAnim = true)
        }
    }

    // Engine Animasi Dinamis (mengikuti slider FPS di Option: 12-24 fps) + Idle Otomatis
    LaunchedEffect(currentAnim, triggerCount) {
        when (currentAnim) {
            JamalAnim.IDLE -> {
                currentFrame = idleBitmap
                while (true) {
                    delay((2800L..4800L).random())
                    currentFrame = blinkBitmap
                    delay(130L)
                    currentFrame = idleBitmap

                    val idleMs = System.currentTimeMillis() - lastInteractionAt
                    val overlayOpen = showChat || showSettings || showMemoryDialog ||
                        showPhotoSourceDialog || pendingPrPhoto != null
                    if (idleMs >= 20_000L && !isBusy && !isSpeaking && !isListening && !isMimicRecording && !overlayOpen) {
                        if ((0..1).random() == 0) {
                            playAnim(
                                JamalAnim.YAWN,
                                "Hooaam... ngantuk gue dicuekin mulu.",
                                speak = false
                            )
                        } else {
                            playAnim(
                                JamalAnim.SNEEZE,
                                "Hatsyi! Pasti lu lagi ngomongin gue ya?!",
                                speak = false
                            )
                        }
                        break
                    }
                }
            }
            JamalAnim.LISTEN -> {
                currentFrame = listenBitmap
            }
            JamalAnim.TALK -> {
                while (isSpeaking) {
                    for (i in 1..currentAnim.frames) {
                        if (!isSpeaking) break
                        val t0 = System.currentTimeMillis()
                        val path = String.format(Locale.US, "tom/animations/talk/frame_%03d.webp", i)
                        val bmp = withContext(Dispatchers.IO) { loadFrameBitmap(context, path) }
                        if (bmp != null) currentFrame = bmp
                        val targetMs = (1000L / appSettings.animFps.coerceIn(12, 24)) + 3L
                        val dt = System.currentTimeMillis() - t0
                        if (dt < targetMs) delay(targetMs - dt)
                    }
                }
                currentFrame = idleBitmap
            }
            else -> {
                for (i in 1..currentAnim.frames) {
                    val t0 = System.currentTimeMillis()
                    val path = String.format(
                        Locale.US,
                        "tom/animations/%s/frame_%03d.webp",
                        currentAnim.folder,
                        i
                    )
                    val bmp = withContext(Dispatchers.IO) { loadFrameBitmap(context, path) }
                    if (bmp != null) currentFrame = bmp
                    val targetMs = 1000L / appSettings.animFps.coerceIn(12, 24)
                    val dt = System.currentTimeMillis() - t0
                    if (dt < targetMs) delay(targetMs - dt)
                }
                markActive()
                currentAnim = if (isSpeaking) JamalAnim.TALK else JamalAnim.IDLE
            }
        }
    }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = GamePalette.TextBrown,
        unfocusedTextColor = GamePalette.TextBrown,
        focusedBorderColor = GamePalette.Teal,
        unfocusedBorderColor = GamePalette.CreamDark,
        focusedContainerColor = Color.White,
        unfocusedContainerColor = Color.White,
        cursorColor = GamePalette.Teal
    )

    val gameSliderColors = SliderDefaults.colors(
        thumbColor = GamePalette.GreenBtn,
        activeTrackColor = GamePalette.LimeTop,
        inactiveTrackColor = GamePalette.TrackBg
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Layer 1: Kucing Jamal Full-Screen + Zona Sentuh (Tap) & Elus (Swipe)
        currentFrame?.let { bmp ->
            Image(
                bitmap = bmp,
                contentDescription = "Kucing Jamal",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        val touchSlop = viewConfiguration.touchSlop
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val startPos = down.position
                            var lastPos = startPos
                            var totalDrag = 0f
                            var petTriggered = false

                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) {
                                    if (!petTriggered && totalDrag < touchSlop) {
                                        val nx = startPos.x / size.width.toFloat()
                                        val ny = startPos.y / size.height.toFloat()
                                        when {
                                            ny in 0.16f..0.46f -> playAnim(
                                                JamalAnim.KNOCKOUT,
                                                "Woi! Jangan pukul kepala gue!",
                                                deltaMood = -18
                                            )
                                            ny in 0.46f..0.72f -> playAnim(
                                                JamalAnim.STOMACH,
                                                "Ih apaan sih colek-colek perut!",
                                                deltaMood = -6
                                            )
                                            ny > 0.72f && nx < 0.5f -> playAnim(
                                                JamalAnim.FOOT_RIGHT,
                                                "Aw! Kaki kanan gue sakit tau!",
                                                deltaMood = -8
                                            )
                                            ny > 0.72f && nx >= 0.5f -> playAnim(
                                                JamalAnim.FOOT_LEFT,
                                                "Heh! Jangan injek kaki kiri!",
                                                deltaMood = -8
                                            )
                                        }
                                    }
                                    break
                                }

                                val dx = change.position.x - lastPos.x
                                val dy = change.position.y - lastPos.y
                                totalDrag += hypot(dx, dy)
                                lastPos = change.position

                                if (!petTriggered && totalDrag > maxOf(touchSlop * 2.5f, 55f)) {
                                    val ny = startPos.y / size.height.toFloat()
                                    if (ny in 0.16f..0.72f) {
                                        petTriggered = true
                                        playAnim(
                                            JamalAnim.HAPPY_SHORT,
                                            petLines.random(),
                                            deltaMood = 15
                                        )
                                    }
                                }
                            }
                        }
                    }
            )
        }

        // Layer 2: Top Game HUD Hijau Limau + Bubble Ucapan Krem
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(GamePalette.LimeShadow)
                    .padding(bottom = 4.dp)
                    .clip(RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp))
                    .background(GamePalette.LimeTop)
                    .statusBarsPadding()
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    StripedStatusPill(
                        value = petStatus.mood,
                        icon = Icons.Filled.Favorite,
                        iconTint = GamePalette.Red,
                        fillColor = GamePalette.Red,
                        stripeColor = GamePalette.RedStripe,
                        modifier = Modifier.weight(1f)
                    )
                    StripedStatusPill(
                        value = petStatus.hunger,
                        icon = Icons.Filled.Restaurant,
                        iconTint = GamePalette.Orange,
                        fillColor = GamePalette.Orange,
                        stripeColor = GamePalette.OrangeStripe,
                        modifier = Modifier.weight(1f)
                    )
                    GameVectorCircleButton(
                        icon = Icons.Filled.Psychology,
                        bgColor = if (memories.isNotEmpty()) GamePalette.Purple else GamePalette.Teal,
                        shadowColor = if (memories.isNotEmpty()) GamePalette.PurpleShadow else GamePalette.TealShadow,
                        contentDescription = "Memori Jamal",
                        size = 40.dp,
                        iconSize = 19.dp
                    ) {
                        markActive()
                        showMemoryDialog = true
                    }
                    GameVectorCircleButton(
                        icon = Icons.Filled.Settings,
                        bgColor = GamePalette.Teal,
                        shadowColor = GamePalette.TealShadow,
                        contentDescription = "Option",
                        size = 40.dp,
                        iconSize = 19.dp
                    ) {
                        markActive()
                        showSettings = true
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, start = 12.dp, end = 12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(18.dp))
                        .background(GamePalette.CreamDark)
                        .padding(bottom = 3.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(GamePalette.Cream.copy(alpha = 0.95f))
                        .clickable {
                            markActive()
                            showChat = true
                        }
                        .padding(horizontal = 14.dp, vertical = 9.dp)
                ) {
                    Text(
                        text = statusText,
                        color = GamePalette.TextBrown,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (isSpeaking) {
                    Spacer(Modifier.width(8.dp))
                    GameVectorCircleButton(
                        icon = Icons.Filled.VolumeOff,
                        bgColor = GamePalette.Red,
                        shadowColor = GamePalette.RedShadow,
                        contentDescription = "Stop Bicara",
                        size = 44.dp,
                        iconSize = 20.dp
                    ) {
                        markActive()
                        stopCurrentSpeech()
                        currentAnim = JamalAnim.IDLE
                        statusText = "Iya iya, gue diem deh!"
                    }
                }
            }

            ttsErrorBanner?.let { errText ->
                Spacer(Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(GamePalette.RedShadow)
                        .padding(bottom = 2.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(GamePalette.Red)
                        .clickable { ttsErrorBanner = null }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = errText,
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Layer 3: Tombol Aksi Klasik + Makan (Sisi Kiri)
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 10.dp, top = 46.dp)
        ) {
            GameVectorCircleButton(
                icon = Icons.Filled.Restaurant,
                bgColor = GamePalette.Orange,
                shadowColor = GamePalette.OrangeShadow,
                contentDescription = "Makan",
                size = 50.dp,
                iconSize = 23.dp
            ) {
                playAnim(
                    JamalAnim.EAT,
                    eatLines.random(),
                    deltaHunger = 30,
                    deltaMood = 10
                )
            }
            ClassicGameIconButton(context, "button_milk.png", "Susu", GamePalette.Teal, GamePalette.TealShadow) {
                playAnim(
                    JamalAnim.DRINK,
                    "Hmph, yaudah deh gue minum susunya...",
                    deltaHunger = 18,
                    deltaMood = 8
                )
            }
            ClassicGameIconButton(context, "cake.png", "Pai", GamePalette.Orange, GamePalette.OrangeShadow) {
                playAnim(
                    JamalAnim.PIE,
                    "Woi kurang ajar lempar-lempar pai!",
                    deltaMood = -10
                )
            }
            ClassicGameIconButton(context, "cinele.png", "Simbal", GamePalette.GreenBtn, GamePalette.GreenBtnShadow) {
                playAnim(
                    JamalAnim.CYMBAL,
                    "BERISIK TAU GAK!!",
                    deltaMood = -10
                )
            }
            ClassicGameIconButton(context, "btngasmask.png", "Kentut", GamePalette.Purple, GamePalette.PurpleShadow) {
                playAnim(
                    JamalAnim.FART,
                    "Bukan gue! Salahin temboknya!",
                    deltaMood = -4
                )
            }
            ClassicGameIconButton(context, "btnprint.png", "Cakar", GamePalette.Red, GamePalette.RedShadow) {
                playAnim(
                    JamalAnim.SCRATCH,
                    "Rasain nih cakaran gue!",
                    deltaMood = -8
                )
            }
        }

        // Layer 4: Bar Asisten AI + Mode Tirukan Offline (Bawah)
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 20.dp)
        ) {
            GameVectorCircleButton(
                icon = Icons.Filled.CameraAlt,
                bgColor = GamePalette.Blue,
                shadowColor = GamePalette.BlueShadow,
                contentDescription = "Foto PR",
                size = 54.dp,
                iconSize = 24.dp,
                label = "Foto PR"
            ) {
                markActive()
                showPhotoSourceDialog = true
            }
            GameVectorCircleButton(
                icon = Icons.Filled.Mic,
                bgColor = if (isListening) GamePalette.Red else GamePalette.GreenBtn,
                shadowColor = if (isListening) GamePalette.RedShadow else GamePalette.GreenBtnShadow,
                contentDescription = "Ngobrol",
                size = 66.dp,
                iconSize = 30.dp,
                label = if (isListening) "Dengerin" else "Ngobrol"
            ) {
                markActive()
                pendingMicAction = "ai"
                val hasPerm = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
                if (hasPerm) startVoiceInput()
                else micPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
            GameVectorCircleButton(
                icon = Icons.Filled.RecordVoiceOver,
                bgColor = if (isMimicRecording) GamePalette.Red else GamePalette.Purple,
                shadowColor = if (isMimicRecording) GamePalette.RedShadow else GamePalette.PurpleShadow,
                contentDescription = "Tirukan",
                size = 54.dp,
                iconSize = 24.dp,
                label = if (isMimicRecording) "Stop" else "Tirukan"
            ) {
                markActive()
                pendingMicAction = "mimic"
                val hasPerm = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
                if (hasPerm) startOfflineMimic()
                else micPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
            GameVectorCircleButton(
                icon = Icons.Filled.Chat,
                bgColor = GamePalette.Orange,
                shadowColor = GamePalette.OrangeShadow,
                contentDescription = "Chat",
                size = 54.dp,
                iconSize = 24.dp,
                label = "Chat"
            ) {
                markActive()
                showChat = true
            }
        }
    }

    // Dialog Memori Jangka Panjang Kucing Jamal
    if (showMemoryDialog) {
        var newFactInput by remember { mutableStateOf("") }
        GameDialogCard(
            title = "Ingatan Jamal (${memories.size})",
            headerColor = GamePalette.Purple,
            headerShadow = GamePalette.PurpleShadow,
            onDismiss = { showMemoryDialog = false }
        ) {
            Text(
                "Jamal otomatis mencatat fakta tentang kamu saat ngobrol dan tetap ingat walau chat dihapus.",
                color = GamePalette.TextBrownSoft,
                fontSize = 12.sp
            )
            Spacer(Modifier.height(10.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = newFactInput,
                    onValueChange = { newFactInput = it },
                    placeholder = { Text("Misal: Nama gue Zayn", fontSize = 13.sp) },
                    singleLine = true,
                    colors = fieldColors,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                GameVectorCircleButton(
                    icon = Icons.Filled.Add,
                    bgColor = GamePalette.GreenBtn,
                    shadowColor = GamePalette.GreenBtnShadow,
                    contentDescription = "Tambah",
                    size = 46.dp,
                    iconSize = 22.dp
                ) {
                    if (newFactInput.isNotBlank()) {
                        memories = MemoryStorage.merge(context, memories, listOf(newFactInput))
                        newFactInput = ""
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            if (memories.isEmpty()) {
                Text(
                    "Belum ada ingatan. Coba kenalkan namamu atau hobimu ke Jamal!",
                    fontSize = 12.sp,
                    color = GamePalette.TextBrownSoft
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 210.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    itemsIndexed(memories) { idx, item ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White)
                                .border(1.5.dp, GamePalette.CreamDark, RoundedCornerShape(12.dp))
                                .padding(start = 10.dp, end = 6.dp, top = 6.dp, bottom = 6.dp)
                        ) {
                            Text(
                                text = "• $item",
                                color = GamePalette.TextBrown,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = {
                                    memories = MemoryStorage.removeAt(context, memories, idx)
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = "Hapus",
                                    tint = GamePalette.Red,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (memories.isNotEmpty()) {
                    GamePillButton(
                        text = "HAPUS SEMUA",
                        bgColor = GamePalette.Red,
                        shadowColor = GamePalette.RedShadow,
                        modifier = Modifier.weight(1f)
                    ) {
                        memories = MemoryStorage.clear(context)
                        statusText = "Semua ingatan tentang lu udah gue lupain!"
                    }
                }
                GamePillButton(
                    text = "OK",
                    bgColor = GamePalette.Teal,
                    shadowColor = GamePalette.TealShadow,
                    modifier = Modifier.weight(1f)
                ) {
                    showMemoryDialog = false
                }
            }
        }
    }

    // Dialog Pilih Sumber Foto PR
    if (showPhotoSourceDialog) {
        GameDialogCard(
            title = "Kirim Soal PR",
            headerColor = GamePalette.Blue,
            headerShadow = GamePalette.BlueShadow,
            onDismiss = { showPhotoSourceDialog = false }
        ) {
            Text(
                "Pilih mau foto langsung pakai kamera atau ambil gambar soal dari galeri:",
                color = GamePalette.TextBrown,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(14.dp))
            GamePillButton(
                text = "FOTO LANGSUNG (KAMERA)",
                icon = Icons.Filled.CameraAlt,
                bgColor = GamePalette.GreenBtn,
                shadowColor = GamePalette.GreenBtnShadow,
                modifier = Modifier.fillMaxWidth()
            ) {
                showPhotoSourceDialog = false
                val hasCam = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED
                if (hasCam) launchCameraForPr()
                else cameraPermLauncher.launch(Manifest.permission.CAMERA)
            }
            Spacer(Modifier.height(10.dp))
            GamePillButton(
                text = "PILIH DARI GALERI",
                icon = Icons.Filled.PhotoLibrary,
                bgColor = GamePalette.Orange,
                shadowColor = GamePalette.OrangeShadow,
                modifier = Modifier.fillMaxWidth()
            ) {
                showPhotoSourceDialog = false
                photoPicker.launch("image/*")
            }
        }
    }

    // Dialog Pratinjau Foto PR
    pendingPrPhoto?.let { photo ->
        GameDialogCard(
            title = "Pratinjau Soal PR",
            headerColor = GamePalette.Teal,
            headerShadow = GamePalette.TealShadow,
            onDismiss = { pendingPrPhoto = null }
        ) {
            Image(
                bitmap = photo.preview,
                contentDescription = "Pratinjau Foto PR",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 210.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF231F1D))
                    .border(2.dp, GamePalette.CreamDark, RoundedCornerShape(14.dp))
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = prNoteInput,
                onValueChange = { prNoteInput = it },
                label = { Text("Catatan tambahan (opsional)") },
                placeholder = { Text("Misal: kerjakan nomor 3 aja") },
                maxLines = 3,
                colors = fieldColors,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(14.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                GamePillButton(
                    text = "BATAL",
                    bgColor = GamePalette.Red,
                    shadowColor = GamePalette.RedShadow,
                    modifier = Modifier.weight(1f)
                ) {
                    pendingPrPhoto = null
                }
                GamePillButton(
                    text = "KIRIM KE JAMAL",
                    bgColor = GamePalette.GreenBtn,
                    shadowColor = GamePalette.GreenBtnShadow,
                    modifier = Modifier.weight(1.4f)
                ) {
                    val note = prNoteInput.trim()
                    val promptText = if (note.isEmpty()) {
                        "Tolong kerjakan dan jelaskan soal PR yang ada di gambar ini dengan jelas."
                    } else {
                        "Tolong kerjakan dan jelaskan soal PR di gambar ini dengan jelas. Catatan tambahan: $note"
                    }
                    val displayText = if (note.isEmpty()) {
                        "[Foto Soal PR dikirim]"
                    } else {
                        "[Foto Soal PR] $note"
                    }
                    val b64 = photo.base64
                    pendingPrPhoto = null
                    prNoteInput = ""
                    sendToJamal(prompt = promptText, imageBase64 = b64, displayPrompt = displayText)
                }
            }
        }
    }

    // Panel Chat & Tutor PR
    if (showChat) {
        val listState = rememberLazyListState()
        LaunchedEffect(messages.size, showChat) {
            if (messages.isNotEmpty()) {
                listState.animateScrollToItem(messages.lastIndex)
            }
        }

        ModalBottomSheet(
            onDismissRequest = { showChat = false },
            containerColor = GamePalette.Cream,
            dragHandle = null
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.82f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(GamePalette.TealShadow)
                        .padding(bottom = 4.dp)
                        .background(GamePalette.Teal)
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Text(
                        "OBROLAN & TUTOR PR JAMAL",
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (isSpeaking) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(GamePalette.RedShadow)
                                    .padding(bottom = 2.dp)
                                    .clip(CircleShape)
                                    .background(GamePalette.Red)
                                    .clickable {
                                        stopCurrentSpeech()
                                        currentAnim = JamalAnim.IDLE
                                        statusText = "Iya iya, gue diem deh!"
                                    }
                            ) {
                                Icon(
                                    Icons.Filled.VolumeOff,
                                    contentDescription = "Stop Bicara",
                                    tint = Color.White,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                        }
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(GamePalette.OrangeShadow)
                                .padding(bottom = 2.dp)
                                .clip(CircleShape)
                                .background(GamePalette.Orange)
                                .clickable { showClearConfirm = true }
                        ) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = "Hapus Riwayat",
                                tint = Color.White,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(GamePalette.RedShadow)
                                .padding(bottom = 2.dp)
                                .clip(CircleShape)
                                .background(GamePalette.Red)
                                .clickable { showChat = false }
                        ) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "Tutup",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(messages) { msg ->
                        val isUser = msg.role == "user"
                        val isErrorBubble = msg.text.startsWith("Gagal konek Gemini:")

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
                        ) {
                            Column(
                                horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
                                modifier = Modifier.widthIn(max = 315.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(
                                            when {
                                                isUser -> GamePalette.TealShadow
                                                isErrorBubble -> GamePalette.RedShadow
                                                else -> GamePalette.CreamDark
                                            }
                                        )
                                        .padding(bottom = 3.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(
                                            when {
                                                isUser -> GamePalette.Teal
                                                isErrorBubble -> GamePalette.Red
                                                else -> Color.White
                                            }
                                        )
                                        .padding(horizontal = 12.dp, vertical = 10.dp)
                                ) {
                                    SelectionContainer {
                                        if (isUser || isErrorBubble) {
                                            Text(
                                                text = msg.text,
                                                color = Color.White,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        } else {
                                            Text(
                                                text = parseGameRichText(msg.text),
                                                color = GamePalette.TextBrown,
                                                fontSize = 14.sp,
                                                lineHeight = 20.sp
                                            )
                                        }
                                    }
                                }

                                if (!isUser && !isErrorBubble) {
                                    Spacer(Modifier.height(4.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(50))
                                            .background(GamePalette.CreamDark.copy(alpha = 0.65f))
                                            .clickable {
                                                markActive()
                                                statusText = msg.text.replace("**", "")
                                                speakBubble(msg.text, forceTalkAnim = true)
                                            }
                                            .padding(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.VolumeUp,
                                            contentDescription = "Putar Ulang Suara",
                                            tint = GamePalette.TealShadow,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            text = "Putar Suara",
                                            color = GamePalette.TealShadow,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (messages.size > 1 && !isBusy) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 14.dp, vertical = 4.dp)
                    ) {
                        GamePillButton(
                            text = "Jelaskan Lebih Simpel",
                            icon = Icons.Filled.AutoAwesome,
                            bgColor = GamePalette.Orange,
                            shadowColor = GamePalette.OrangeShadow,
                            fontSize = 11.sp
                        ) {
                            sendToJamal(
                                prompt = "Jelaskan jawaban terakhirmu tadi dengan bahasa yang jauh lebih simpel, singkat, dan langkah mudah dipahami.",
                                displayPrompt = "Jelaskan lebih simpel dong!"
                            )
                        }
                        GamePillButton(
                            text = "Bikin 1 Soal Latihan",
                            icon = Icons.Filled.Quiz,
                            bgColor = GamePalette.Purple,
                            shadowColor = GamePalette.PurpleShadow,
                            fontSize = 11.sp
                        ) {
                            sendToJamal(
                                prompt = "Buatkan 1 soal latihan baru yang mirip dengan topik terakhir tadi untuk aku kerjakan. Jangan langsung kasih jawabannya dulu, tunggu aku jawab.",
                                displayPrompt = "Bikinin 1 soal latihan mirip tadi!"
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    OutlinedTextField(
                        value = chatInput,
                        onValueChange = { chatInput = it },
                        placeholder = { Text("Tanya PR atau ajak ngobrol...", fontSize = 14.sp) },
                        singleLine = true,
                        colors = fieldColors,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    GameVectorCircleButton(
                        icon = Icons.AutoMirrored.Filled.Send,
                        bgColor = if (!isBusy && chatInput.isNotBlank()) GamePalette.GreenBtn else Color(0xFFB5ADA2),
                        shadowColor = if (!isBusy && chatInput.isNotBlank()) GamePalette.GreenBtnShadow else Color(0xFF948C82),
                        contentDescription = "Kirim",
                        size = 48.dp,
                        iconSize = 21.dp
                    ) {
                        if (!isBusy && chatInput.isNotBlank()) {
                            val txt = chatInput.trim()
                            chatInput = ""
                            sendToJamal(txt)
                        }
                    }
                }
            }
        }
    }

    // Konfirmasi Hapus Riwayat Chat
    if (showClearConfirm) {
        GameDialogCard(
            title = "Hapus Riwayat Chat?",
            headerColor = GamePalette.Red,
            headerShadow = GamePalette.RedShadow,
            onDismiss = { showClearConfirm = false }
        ) {
            Text(
                "Semua riwayat obrolan & jawaban PR bakal dihapus permanen dari HP. Ingatan jangka panjang Jamal tetap aman.",
                color = GamePalette.TextBrown,
                fontSize = 13.sp
            )
            Spacer(Modifier.height(14.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                GamePillButton(
                    text = "BATAL",
                    bgColor = GamePalette.Teal,
                    shadowColor = GamePalette.TealShadow,
                    modifier = Modifier.weight(1f)
                ) {
                    showClearConfirm = false
                }
                GamePillButton(
                    text = "HAPUS",
                    bgColor = GamePalette.Red,
                    shadowColor = GamePalette.RedShadow,
                    modifier = Modifier.weight(1f)
                ) {
                    messages = ChatStorage.clear(context)
                    statusText = "Riwayat chat udah gue bersihin. Mulai dari nol lagi deh!"
                    showClearConfirm = false
                }
            }
        }
    }

    // Dialog Option Lengkap (Suara Gemini + Pitch Tirukan + FPS Animasi + Cache + Multi API Key)
    if (showSettings) {
        var tempKeys by remember {
            mutableStateOf(GeminiClient.parseApiKeys(apiKeysRaw).joinToString("\n"))
        }
        var tempVoice by remember { mutableStateOf(appSettings.voiceName) }
        var tempPitch by remember { mutableFloatStateOf(appSettings.mimicPitch) }
        var tempFps by remember { mutableIntStateOf(appSettings.animFps) }
        var cacheStats by remember { mutableStateOf(AppSettingsStorage.getTtsCacheStats(context)) }
        val detectedCount = GeminiClient.parseApiKeys(tempKeys).size

        GameDialogCard(
            title = "Option • Kucing Jamal",
            headerColor = GamePalette.Teal,
            headerShadow = GamePalette.TealShadow,
            onDismiss = { showSettings = false }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // 1. Karakter Suara Gemini
                Text(
                    text = "KARAKTER SUARA GEMINI",
                    color = GamePalette.TextBrown,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                ) {
                    AppSettingsStorage.availableVoices.forEach { opt ->
                        val selected = tempVoice.equals(opt.name, ignoreCase = true)
                        GamePillButton(
                            text = "${opt.name} (${opt.label})",
                            bgColor = if (selected) GamePalette.GreenBtn else GamePalette.Teal,
                            shadowColor = if (selected) GamePalette.GreenBtnShadow else GamePalette.TealShadow,
                            fontSize = 11.sp
                        ) {
                            tempVoice = opt.name
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // 2. Slider Pitch Mode Tirukan
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "PITCH TIRUKAN OFFLINE",
                        color = GamePalette.TextBrown,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = String.format(Locale.US, "%.2fx", tempPitch),
                        color = GamePalette.TealShadow,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                Slider(
                    value = tempPitch,
                    onValueChange = { tempPitch = it },
                    valueRange = 1.15f..1.75f,
                    colors = gameSliderColors
                )

                // 3. Slider Kecepatan Animasi (FPS)
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "KECEPATAN ANIMASI",
                        color = GamePalette.TextBrown,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "$tempFps FPS",
                        color = GamePalette.TealShadow,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                Slider(
                    value = tempFps.toFloat(),
                    onValueChange = { tempFps = it.roundToInt().coerceIn(12, 24) },
                    valueRange = 12f..24f,
                    steps = 11,
                    colors = gameSliderColors
                )

                Spacer(Modifier.height(6.dp))

                // 4. Cache Suara
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White)
                        .border(1.5.dp, GamePalette.CreamDark, RoundedCornerShape(14.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "CACHE SUARA LOKAL",
                            color = GamePalette.TextBrown,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = cacheStats.formatted(),
                            color = GamePalette.TextBrownSoft,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    GamePillButton(
                        text = "BERSIHKAN",
                        bgColor = GamePalette.Red,
                        shadowColor = GamePalette.RedShadow,
                        fontSize = 11.sp,
                        enabled = cacheStats.fileCount > 0
                    ) {
                        cacheStats = AppSettingsStorage.clearTtsCache(context)
                    }
                }

                Spacer(Modifier.height(12.dp))

                // 5. Rotasi Multi API Key
                Text(
                    text = "ROTASI MULTI API KEY",
                    color = GamePalette.TextBrown,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White)
                        .border(1.5.dp, GamePalette.CreamDark, RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = GeminiClient.getRotationStatus(apiKeysRaw),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = GamePalette.TealShadow
                    )
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = tempKeys,
                    onValueChange = { tempKeys = it },
                    label = { Text("AIza... (terdeteksi $detectedCount key)") },
                    minLines = 2,
                    maxLines = 4,
                    colors = fieldColors,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                if (BuildConfig.GEMINI_API_KEY.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "↺ Muat ulang dari GitHub Secret",
                        color = GamePalette.Teal,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clickable {
                                tempKeys = GeminiClient.parseApiKeys(BuildConfig.GEMINI_API_KEY)
                                    .joinToString("\n")
                            }
                            .padding(vertical = 4.dp)
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Versi v${BuildConfig.VERSION_NAME}",
                    color = GamePalette.TextBrownSoft,
                    fontSize = 11.sp
                )
            }

            Spacer(Modifier.height(12.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                GamePillButton(
                    text = "BATAL",
                    bgColor = GamePalette.Orange,
                    shadowColor = GamePalette.OrangeShadow,
                    modifier = Modifier.weight(1f)
                ) {
                    showSettings = false
                }
                GamePillButton(
                    text = "SIMPAN",
                    bgColor = GamePalette.GreenBtn,
                    shadowColor = GamePalette.GreenBtnShadow,
                    modifier = Modifier.weight(1.3f)
                ) {
                    val normalized = GeminiClient.parseApiKeys(tempKeys).joinToString("\n")
                    val oldVoice = appSettings.voiceName
                    val newSettings = AppSettings(
                        voiceName = tempVoice,
                        mimicPitch = tempPitch,
                        animFps = tempFps
                    )
                    appSettings = newSettings
                    AppSettingsStorage.save(context, newSettings)

                    if (normalized != apiKeysRaw) {
                        apiKeysRaw = normalized
                        GeminiClient.resetKeyRotation()
                        prefs.edit().putString("gemini_api_key", normalized).apply()
                    }
                    ttsErrorBanner = null

                    val msg = if (!oldVoice.equals(tempVoice, ignoreCase = true)) {
                        "Suara gue udah diganti ke $tempVoice. Gimana, makin keren kan?!"
                    } else {
                        "Pengaturan udah disimpan. Mau ngapain lagi cepetan!"
                    }
                    statusText = msg
                    speakBubble(msg, forceTalkAnim = true, voiceOverride = tempVoice)
                    showSettings = false
                }
            }
        }
    }
}

@Composable
private fun ClassicGameIconButton(
    context: Context,
    fileName: String,
    desc: String,
    bgColor: Color,
    shadowColor: Color,
    onClick: () -> Unit
) {
    val icon = remember(fileName) { loadUiIcon(context, fileName) }
    if (icon != null) {
        GameImageCircleButton(
            bitmap = icon,
            bgColor = bgColor,
            shadowColor = shadowColor,
            contentDescription = desc,
            size = 50.dp,
            onClick = onClick
        )
    }
}
