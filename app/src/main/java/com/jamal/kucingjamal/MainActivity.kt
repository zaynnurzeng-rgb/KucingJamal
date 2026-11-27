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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
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
    var showSettings by remember { mutableStateOf(false) }
    var showChat by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var showPhotoSourceDialog by remember { mutableStateOf(false) }
    var pendingPrPhoto by remember { mutableStateOf<PreparedPrPhoto?>(null) }
    var prNoteInput by remember { mutableStateOf("") }
    var cameraPhotoUri by remember { mutableStateOf<Uri?>(null) }

    var chatInput by remember { mutableStateOf("") }
    var messages by remember { mutableStateOf(ChatStorage.load(context)) }
    var isBusy by remember { mutableStateOf(false) }
    var isSpeaking by remember { mutableStateOf(false) }
    var isListening by remember { mutableStateOf(false) }
    var speechJob by remember { mutableStateOf<Job?>(null) }
    var ttsErrorBanner by remember { mutableStateOf<String?>(null) }
    var lastInteractionAt by remember { mutableLongStateOf(System.currentTimeMillis()) }

    val petLines = remember {
        listOf(
            "Hmph! Jangan elus-elus, bukan berarti gue suka ya!",
            "Ih apaan sih pegang-pegang... eh bentar, lanjut dikit boleh deh.",
            "Ck, sok akrab banget ngelus-ngelus gue!"
        )
    }

    val audioPlayer = remember { PcmAudioPlayer() }
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
    var statusText by remember { mutableStateOf("Heh, mau ngapain lu?!") }

    // Simpan riwayat obrolan secara permanen tiap ada perubahan
    LaunchedEffect(messages) {
        withContext(Dispatchers.IO) {
            ChatStorage.save(context, messages)
        }
    }

    fun hasValidKeys(): Boolean = GeminiClient.parseApiKeys(apiKeysRaw).isNotEmpty()

    fun markActive() {
        lastInteractionAt = System.currentTimeMillis()
    }

    fun stopCurrentSpeech() {
        speechJob?.cancel()
        speechJob = null
        audioPlayer.stop()
        isSpeaking = false
    }

    fun speakBubble(
        text: String,
        delayMs: Long = 0L,
        forceTalkAnim: Boolean = false,
        onDone: (() -> Unit)? = null
    ) {
        if (!hasValidKeys() || text.isBlank()) {
            onDone?.invoke()
            return
        }
        stopCurrentSpeech()
        speechJob = scope.launch {
            val res = GeminiClient.synthesizeVoiceCached(context, apiKeysRaw, text)
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

    fun playAnim(anim: JamalAnim, caption: String? = null, speak: Boolean = true) {
        markActive()
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

    // Ucapkan sapaan bubble pertama kali saat aplikasi masuk layar utama
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
            statusText = "Isi Gemini API Key dulu di ikon gerigi kanan atas!"
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
            val res = GeminiClient.askJamal(apiKeysRaw, messages.dropLast(1), prompt, imageBase64)
            res.onSuccess { reply ->
                messages = messages + ChatMessage("model", reply.text)
                statusText = reply.text
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
        if (granted) startVoiceInput()
        else {
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

    // Engine Animasi ~16 FPS + Perilaku Idle Otomatis (Menguap / Bersin > 20 detik)
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
                    val overlayOpen = showChat || showSettings || showPhotoSourceDialog || pendingPrPhoto != null
                    if (idleMs >= 20_000L && !isBusy && !isSpeaking && !isListening && !overlayOpen) {
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
                        val dt = System.currentTimeMillis() - t0
                        if (dt < 65L) delay(65L - dt)
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
                    val dt = System.currentTimeMillis() - t0
                    if (dt < 62L) delay(62L - dt)
                }
                markActive()
                currentAnim = if (isSpeaking) JamalAnim.TALK else JamalAnim.IDLE
            }
        }
    }

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
                                            ny in 0.14f..0.46f -> playAnim(
                                                JamalAnim.KNOCKOUT,
                                                "Woi! Jangan pukul kepala gue!"
                                            )
                                            ny in 0.46f..0.72f -> playAnim(
                                                JamalAnim.STOMACH,
                                                "Ih apaan sih colek-colek perut!"
                                            )
                                            ny > 0.72f && nx < 0.5f -> playAnim(
                                                JamalAnim.FOOT_RIGHT,
                                                "Aw! Kaki kanan gue sakit tau!"
                                            )
                                            ny > 0.72f && nx >= 0.5f -> playAnim(
                                                JamalAnim.FOOT_LEFT,
                                                "Heh! Jangan injek kaki kiri!"
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
                                    if (ny in 0.14f..0.72f) {
                                        petTriggered = true
                                        playAnim(JamalAnim.HAPPY_SHORT, petLines.random())
                                    }
                                }
                            }
                        }
                    }
            )
        }

        // Layer 2: Status Bubble + Tombol Stop Bicara + Tombol Pengaturan + Info Error TTS
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(top = 10.dp, start = 14.dp, end = 14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    color = Color.Black.copy(alpha = 0.62f),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            markActive()
                            showChat = true
                        }
                ) {
                    Text(
                        text = statusText,
                        color = Color.White,
                        fontSize = 13.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }

                if (isSpeaking) {
                    Spacer(Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            markActive()
                            stopCurrentSpeech()
                            currentAnim = JamalAnim.IDLE
                            statusText = "Iya iya, gue diem deh!"
                        },
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(Color(0xD9D32F2F))
                    ) {
                        Icon(
                            Icons.Filled.VolumeOff,
                            contentDescription = "Stop Bicara",
                            tint = Color.White
                        )
                    }
                }

                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        markActive()
                        showSettings = true
                    },
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.62f))
                ) {
                    Icon(Icons.Filled.Settings, contentDescription = "API Key", tint = Color.White)
                }
            }

            ttsErrorBanner?.let { errText ->
                Spacer(Modifier.height(6.dp))
                Surface(
                    color = Color(0xCCB71C1C),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { ttsErrorBanner = null }
                ) {
                    Text(
                        text = errText,
                        color = Color.White,
                        fontSize = 11.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }

        // Layer 3: Tombol Aksi Klasik (Sisi Kiri)
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 12.dp)
        ) {
            ClassicIconButton(context, "button_milk.png", "Susu") {
                playAnim(JamalAnim.DRINK, "Hmph, yaudah deh gue minum susunya...")
            }
            ClassicIconButton(context, "cake.png", "Pai") {
                playAnim(JamalAnim.PIE, "Woi kurang ajar lempar-lempar pai!")
            }
            ClassicIconButton(context, "cinele.png", "Simbal") {
                playAnim(JamalAnim.CYMBAL, "BERISIK TAU GAK!!")
            }
            ClassicIconButton(context, "btngasmask.png", "Kentut") {
                playAnim(JamalAnim.FART, "Bukan gue! Salahin temboknya!")
            }
            ClassicIconButton(context, "btnprint.png", "Cakar") {
                playAnim(JamalAnim.SCRATCH, "Rasain nih cakaran gue!")
            }
        }

        // Layer 4: Bar Asisten AI (Bawah)
        Row(
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 22.dp)
        ) {
            AiActionButton(
                icon = Icons.Filled.CameraAlt,
                label = "Foto PR",
                bg = Color(0xFF1E88E5)
            ) {
                markActive()
                showPhotoSourceDialog = true
            }
            AiActionButton(
                icon = Icons.Filled.Mic,
                label = if (isListening) "Dengerin..." else "Ngobrol",
                bg = if (isListening) Color(0xFFE53935) else Color(0xFF43A047),
                big = true
            ) {
                markActive()
                val hasPerm = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
                if (hasPerm) startVoiceInput()
                else micPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
            AiActionButton(
                icon = Icons.Filled.Chat,
                label = "Chat",
                bg = Color(0xFFFB8C00)
            ) {
                markActive()
                showChat = true
            }
        }
    }

    // Dialog Pilih Sumber Foto PR (Kamera Langsung vs Galeri)
    if (showPhotoSourceDialog) {
        AlertDialog(
            onDismissRequest = { showPhotoSourceDialog = false },
            title = { Text("Kirim Soal PR") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Pilih mau foto langsung soalnya atau ambil dari galeri:",
                        fontSize = 13.sp
                    )
                    Button(
                        onClick = {
                            showPhotoSourceDialog = false
                            val hasCam = ContextCompat.checkSelfPermission(
                                context, Manifest.permission.CAMERA
                            ) == PackageManager.PERMISSION_GRANTED
                            if (hasCam) launchCameraForPr()
                            else cameraPermLauncher.launch(Manifest.permission.CAMERA)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.CameraAlt, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Foto Langsung (Kamera)")
                    }
                    OutlinedButton(
                        onClick = {
                            showPhotoSourceDialog = false
                            photoPicker.launch("image/*")
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.PhotoLibrary, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Pilih dari Galeri")
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showPhotoSourceDialog = false }) { Text("Batal") }
            }
        )
    }

    // Dialog Pratinjau Foto PR + Catatan Tambahan Sebelum Dikirim
    pendingPrPhoto?.let { photo ->
        AlertDialog(
            onDismissRequest = { pendingPrPhoto = null },
            title = { Text("Pratinjau Soal PR") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Image(
                        bitmap = photo.preview,
                        contentDescription = "Pratinjau Foto PR",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 220.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF1E1E1E))
                    )
                    OutlinedTextField(
                        value = prNoteInput,
                        onValueChange = { prNoteInput = it },
                        label = { Text("Catatan tambahan (opsional)") },
                        placeholder = { Text("Misal: kerjakan nomor 3 aja") },
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
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
                }) {
                    Text("Kirim ke Jamal")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingPrPhoto = null }) { Text("Batal") }
            }
        )
    }

    // Panel Chat & Jawaban PR (Bottom Sheet + Riwayat Permanen + Hapus Riwayat)
    if (showChat) {
        val listState = rememberLazyListState()
        LaunchedEffect(messages.size, showChat) {
            if (messages.isNotEmpty()) {
                listState.animateScrollToItem(messages.lastIndex)
            }
        }

        ModalBottomSheet(onDismissRequest = { showChat = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.78f)
                    .padding(horizontal = 16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                ) {
                    Text(
                        "Obrolan & PR bareng Kucing Jamal",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isSpeaking) {
                            IconButton(onClick = {
                                stopCurrentSpeech()
                                currentAnim = JamalAnim.IDLE
                                statusText = "Iya iya, gue diem deh!"
                            }) {
                                Icon(
                                    Icons.Filled.VolumeOff,
                                    contentDescription = "Stop Bicara",
                                    tint = Color(0xFFD32F2F)
                                )
                            }
                        }
                        IconButton(onClick = { showClearConfirm = true }) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = "Hapus Riwayat",
                                tint = Color(0xFF757575)
                            )
                        }
                    }
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(messages) { msg ->
                        val isUser = msg.role == "user"
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
                        ) {
                            Surface(
                                color = if (isUser) Color(0xFF1E88E5) else Color(0xFFEEEEEE),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.widthIn(max = 300.dp)
                            ) {
                                SelectionContainer {
                                    Text(
                                        text = msg.text,
                                        color = if (isUser) Color.White else Color.Black,
                                        fontSize = 14.sp,
                                        modifier = Modifier.padding(10.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp)
                ) {
                    OutlinedTextField(
                        value = chatInput,
                        onValueChange = { chatInput = it },
                        placeholder = { Text("Tanya PR atau ajak ngobrol...") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(
                        enabled = !isBusy && chatInput.isNotBlank(),
                        onClick = {
                            val txt = chatInput.trim()
                            chatInput = ""
                            sendToJamal(txt)
                        }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Kirim")
                    }
                }
            }
        }
    }

    // Konfirmasi Hapus Riwayat Chat
    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Hapus Semua Riwayat Chat?") },
            text = { Text("Semua riwayat obrolan dan jawaban PR sebelumnya bakal dihapus permanen dari HP.") },
            confirmButton = {
                TextButton(onClick = {
                    messages = ChatStorage.clear(context)
                    statusText = "Riwayat chat udah gue bersihin. Mulai dari nol lagi deh!"
                    showClearConfirm = false
                }) {
                    Text("Hapus", color = Color(0xFFD32F2F))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("Batal") }
            }
        )
    }

    // Dialog Pengaturan Multi API Key
    if (showSettings) {
        var tempKeys by remember {
            mutableStateOf(GeminiClient.parseApiKeys(apiKeysRaw).joinToString("\n"))
        }
        val detectedCount = GeminiClient.parseApiKeys(tempKeys).size

        AlertDialog(
            onDismissRequest = { showSettings = false },
            title = { Text("Rotasi Multi API Key") },
            text = {
                Column {
                    Text(
                        "Masukkan 1 atau beberapa Gemini API Key (1 key per baris, dari Project/akun berbeda). Otomatis pindah saat kuota habis.",
                        fontSize = 12.sp
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = GeminiClient.getRotationStatus(apiKeysRaw),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF1E88E5)
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = tempKeys,
                        onValueChange = { tempKeys = it },
                        label = { Text("AIza... (terdeteksi $detectedCount key)") },
                        minLines = 3,
                        maxLines = 6,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (BuildConfig.GEMINI_API_KEY.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        TextButton(
                            onClick = {
                                tempKeys = GeminiClient.parseApiKeys(BuildConfig.GEMINI_API_KEY)
                                    .joinToString("\n")
                            },
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text("Muat ulang dari GitHub Secret", fontSize = 12.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val normalized = GeminiClient.parseApiKeys(tempKeys).joinToString("\n")
                    apiKeysRaw = normalized
                    GeminiClient.resetKeyRotation()
                    prefs.edit().putString("gemini_api_key", normalized).apply()
                    ttsErrorBanner = null
                    val count = GeminiClient.parseApiKeys(normalized).size
                    val msg = if (count > 1) {
                        "Oke, $count API Key cadangan udah dipasang. Aman nih buat ngoceh!"
                    } else {
                        "Yaudah, API Key udah disimpan. Mau nanya apaan cepetan!"
                    }
                    statusText = msg
                    speakBubble(msg, forceTalkAnim = true)
                    showSettings = false
                }) { Text("Simpan") }
            },
            dismissButton = {
                TextButton(onClick = { showSettings = false }) { Text("Batal") }
            }
        )
    }
}

@Composable
private fun ClassicIconButton(
    context: Context,
    fileName: String,
    desc: String,
    onClick: () -> Unit
) {
    val icon = remember(fileName) { loadUiIcon(context, fileName) }
    if (icon != null) {
        Image(
            bitmap = icon,
            contentDescription = desc,
            modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
                .clickable(onClick = onClick)
        )
    }
}

@Composable
private fun AiActionButton(
    icon: ImageVector,
    label: String,
    bg: Color,
    big: Boolean = false,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(if (big) 68.dp else 54.dp)
                .clip(CircleShape)
                .background(bg.copy(alpha = 0.92f))
                .clickable(onClick = onClick)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = Color.White,
                modifier = Modifier.size(if (big) 32.dp else 26.dp)
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
