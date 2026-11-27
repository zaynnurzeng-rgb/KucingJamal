package com.jamal.kucingjamal

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioAttributes
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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.Locale

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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                KucingJamalScreen()
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

private fun uriToBase64Jpeg(context: Context, uri: Uri): String? = runCatching {
    context.contentResolver.openInputStream(uri)?.use { input ->
        val orig = BitmapFactory.decodeStream(input) ?: return null
        val maxSide = 1024
        val scale = minOf(1f, maxSide.toFloat() / maxOf(orig.width, orig.height))
        val bmp = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                orig,
                (orig.width * scale).toInt(),
                (orig.height * scale).toInt(),
                true
            )
        } else {
            orig
        }
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 82, out)
        Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }
}.getOrNull()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KucingJamalScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("jamal_prefs", Context.MODE_PRIVATE) }

    var apiKey by remember { mutableStateOf(prefs.getString("gemini_api_key", "")?.ifBlank { BuildConfig.GEMINI_API_KEY } ?: BuildConfig.GEMINI_API_KEY) }
    var showSettings by remember { mutableStateOf(false) }
    var showChat by remember { mutableStateOf(false) }
    var chatInput by remember { mutableStateOf("") }
    var messages by remember {
        mutableStateOf(
            listOf(
                ChatMessage("model", "Heh, ngapain buka-buka aplikasi ini? Kalau mau nanya PR atau ngobrol cepetan, gue sibuk!")
            )
        )
    }
    var isBusy by remember { mutableStateOf(false) }
    var isSpeaking by remember { mutableStateOf(false) }
    var isListening by remember { mutableStateOf(false) }

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

    fun playAnim(anim: JamalAnim, caption: String? = null) {
        currentAnim = anim
        triggerCount++
        if (caption != null) statusText = caption
        anim.sound?.let { soundName ->
            soundIds[soundName]?.let { id ->
                soundPool.play(id, 1f, 1f, 1, 0, 1f)
            }
        }
    }

    fun sendToJamal(prompt: String, imageBase64: String? = null, displayPrompt: String = prompt) {
        if (apiKey.isBlank()) {
            statusText = "Isi Gemini API Key dulu di ikon gerigi kanan atas!"
            showSettings = true
            return
        }
        if (isBusy) return
        audioPlayer.stop()
        isBusy = true
        messages = messages + ChatMessage("user", displayPrompt)
        statusText = "Bentar, ribet amat sih..."
        currentAnim = JamalAnim.IDLE

        scope.launch {
            val res = GeminiClient.askJamal(apiKey.trim(), messages.dropLast(1), prompt, imageBase64)
            res.onSuccess { reply ->
                messages = messages + ChatMessage("model", reply.text)
                statusText = reply.text

                val ttsRes = GeminiClient.synthesizeVoice(apiKey.trim(), reply.text)
                isBusy = false

                ttsRes.onSuccess { pcm ->
                    isSpeaking = true
                    currentAnim = JamalAnim.TALK
                    triggerCount++
                    audioPlayer.play(pcm)
                    isSpeaking = false
                }.onFailure { ttsErr ->
                    isSpeaking = false
                    statusText = "Teks OK, suara gagal: ${ttsErr.message?.take(120)}"
                }

                when (reply.emotion) {
                    "MARAH" -> playAnim(JamalAnim.ANGRY)
                    "SENANG" -> playAnim(JamalAnim.HAPPY_SHORT)
                    "KAGET" -> playAnim(JamalAnim.KNOCKOUT)
                    "NGANTUK" -> playAnim(JamalAnim.YAWN)
                    else -> currentAnim = JamalAnim.IDLE
                }
            }.onFailure { err ->
                isBusy = false
                val msg = "Gagal konek Gemini: ${err.message?.take(300)}"
                statusText = msg
                messages = messages + ChatMessage("model", msg)
                playAnim(JamalAnim.ANGRY)
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
                playAnim(JamalAnim.LISTEN, "Ngomong cepetan, gue dengerin...")
            }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { isListening = false }
            override fun onError(error: Int) {
                isListening = false
                statusText = "Gak kedengeran! Ngomong yang jelas dong."
                currentAnim = JamalAnim.IDLE
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
            audioPlayer.stop()
            soundPool.release()
            speechRecognizer?.destroy()
        }
    }

    fun startVoiceInput() {
        if (speechRecognizer == null) {
            statusText = "HP ini gak dukung SpeechRecognizer, pakai Chat aja."
            return
        }
        audioPlayer.stop()
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
        else statusText = "Izin mikrofon ditolak, gimana gue mau denger?!"
    }

    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val b64 = withContext(Dispatchers.IO) { uriToBase64Jpeg(context, uri) }
                if (b64 != null) {
                    sendToJamal(
                        prompt = "Tolong kerjakan dan jelaskan soal PR yang ada di gambar ini dengan jelas.",
                        imageBase64 = b64,
                        displayPrompt = "[Foto Soal PR dikirim]"
                    )
                }
            }
        }
    }

    // Engine Animasi ~16 FPS (62ms aksi, 65ms bicara)
    LaunchedEffect(currentAnim, triggerCount, isSpeaking) {
        when (currentAnim) {
            JamalAnim.IDLE -> {
                currentFrame = idleBitmap
                while (true) {
                    delay((2800L..5200L).random())
                    currentFrame = blinkBitmap
                    delay(130L)
                    currentFrame = idleBitmap
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
                currentAnim = JamalAnim.IDLE
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Layer 1: Kucing Jamal Full-Screen + Zona Sentuh
        currentFrame?.let { bmp ->
            Image(
                bitmap = bmp,
                contentDescription = "Kucing Jamal",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            val nx = offset.x / size.width.toFloat()
                            val ny = offset.y / size.height.toFloat()
                            when {
                                ny in 0.14f..0.46f -> playAnim(JamalAnim.KNOCKOUT, "Woi! Jangan pukul kepala gue!")
                                ny in 0.46f..0.72f -> playAnim(JamalAnim.STOMACH, "Ih apaan sih colek-colek perut!")
                                ny > 0.72f && nx < 0.5f -> playAnim(JamalAnim.FOOT_RIGHT, "Aw! Kaki kanan gue sakit tau!")
                                ny > 0.72f && nx >= 0.5f -> playAnim(JamalAnim.FOOT_LEFT, "Heh! Jangan injek kaki kiri!")
                            }
                        }
                    }
            )
        }

        // Layer 2: Status Bubble + Tombol Pengaturan API Key
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(top = 10.dp, start = 14.dp, end = 14.dp)
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.62f),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .weight(1f)
                    .clickable { showChat = true }
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
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = { showSettings = true },
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.62f))
            ) {
                Icon(Icons.Filled.Settings, contentDescription = "API Key", tint = Color.White)
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
                photoPicker.launch("image/*")
            }
            AiActionButton(
                icon = Icons.Filled.Mic,
                label = if (isListening) "Dengerin..." else "Ngobrol",
                bg = if (isListening) Color(0xFFE53935) else Color(0xFF43A047),
                big = true
            ) {
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
                showChat = true
            }
        }
    }

    // Panel Chat & Jawaban PR (Bottom Sheet)
    if (showChat) {
        ModalBottomSheet(onDismissRequest = { showChat = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.75f)
                    .padding(horizontal = 16.dp)
            ) {
                Text(
                    "Obrolan & PR bareng Kucing Jamal",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                LazyColumn(
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

    // Dialog Pengaturan Gemini API Key
    if (showSettings) {
        var tempKey by remember { mutableStateOf(apiKey) }
        AlertDialog(
            onDismissRequest = { showSettings = false },
            title = { Text("Pengaturan Gemini API Key") },
            text = {
                Column {
                    Text(
                        "Masukkan API Key dari Google AI Studio. Key disimpan lokal di HP, bukan di GitHub.",
                        fontSize = 13.sp
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = tempKey,
                        onValueChange = { tempKey = it },
                        label = { Text("AIza...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    apiKey = tempKey.trim()
                    prefs.edit().putString("gemini_api_key", apiKey).apply()
                    statusText = "API Key tersimpan! Coba ajak ngobrol sekarang."
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
