package com.jamal.kucingjamal

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Mic
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
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

@Composable
fun KucingJamalScreen() {
    val context = LocalContext.current

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

    DisposableEffect(Unit) {
        onDispose { soundPool.release() }
    }

    val idleBitmap = remember { loadFrameBitmap(context, "tom/animations/idle/frame_001.webp") }
    val blinkBitmap = remember { loadFrameBitmap(context, "tom/animations/blink/frame_001.webp") }
    val listenBitmap = remember { loadFrameBitmap(context, "tom/animations/listen/frame_001.webp") }

    var currentFrame by remember { mutableStateOf(idleBitmap) }
    var currentAnim by remember { mutableStateOf(JamalAnim.IDLE) }
    var triggerCount by remember { mutableIntStateOf(0) }
    var statusText by remember { mutableStateOf("Halo! Aku Kucing Jamal.") }

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

    LaunchedEffect(currentAnim, triggerCount) {
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
                repeat(3) {
                    for (i in 1..currentAnim.frames) {
                        val t0 = System.currentTimeMillis()
                        val path = String.format(Locale.US, "tom/animations/talk/frame_%03d.webp", i)
                        val bmp = withContext(Dispatchers.IO) { loadFrameBitmap(context, path) }
                        if (bmp != null) currentFrame = bmp
                        val dt = System.currentTimeMillis() - t0
                        if (dt < 40L) delay(40L - dt)
                    }
                }
                statusText = "Siap bantu tugas atau ngobrol!"
                currentAnim = JamalAnim.IDLE
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
                    if (dt < 33L) delay(33L - dt)
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
                                ny in 0.14f..0.46f -> playAnim(JamalAnim.KNOCKOUT, "Aduh! Kepalaku pusing...")
                                ny in 0.46f..0.72f -> playAnim(JamalAnim.STOMACH, "Hehe, geli tau!")
                                ny > 0.72f && nx < 0.5f -> playAnim(JamalAnim.FOOT_RIGHT, "Aw! Kaki kanan!")
                                ny > 0.72f && nx >= 0.5f -> playAnim(JamalAnim.FOOT_LEFT, "Aw! Kaki kiri!")
                            }
                        }
                    }
            )
        }

        // Layer 2: Status Bubble di Atas
        Surface(
            color = Color.Black.copy(alpha = 0.55f),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 12.dp, start = 20.dp, end = 20.dp)
        ) {
            Text(
                text = statusText,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        // Layer 3: Tombol Aksi Klasik (Sisi Kiri)
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 12.dp)
        ) {
            ClassicIconButton(context, "button_milk.png", "Susu") {
                playAnim(JamalAnim.DRINK, "Segar banget susunya!")
            }
            ClassicIconButton(context, "cake.png", "Pai") {
                playAnim(JamalAnim.PIE, "Waduh kena pai!")
            }
            ClassicIconButton(context, "cinele.png", "Simbal") {
                playAnim(JamalAnim.CYMBAL, "JRENG!!")
            }
            ClassicIconButton(context, "btngasmask.png", "Kentut") {
                playAnim(JamalAnim.FART, "Ups, kelepasan...")
            }
            ClassicIconButton(context, "btnprint.png", "Cakar") {
                playAnim(JamalAnim.SCRATCH, "Sreeet!")
            }
        }

        // Layer 4: Bar Asisten AI (Bawah — Siap Disambungkan ke Gemini di Tahap 2)
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
                playAnim(JamalAnim.HAPPY_SHORT, "Kamera soal PR siap dipasang di Tahap 2!")
            }
            AiActionButton(
                icon = Icons.Filled.Mic,
                label = "Ngobrol",
                bg = if (currentAnim == JamalAnim.LISTEN) Color(0xFFE53935) else Color(0xFF43A047),
                big = true
            ) {
                if (currentAnim == JamalAnim.LISTEN) {
                    playAnim(JamalAnim.TALK, "Tes suara Kucing Jamal aktif!")
                } else {
                    playAnim(JamalAnim.LISTEN, "Jamal lagi dengerin... (tap lagi untuk tes bicara)")
                }
            }
            AiActionButton(
                icon = Icons.Filled.Chat,
                label = "Chat",
                bg = Color(0xFFFB8C00)
            ) {
                playAnim(JamalAnim.TALK, "Panel Chat Gemini siap dipasang di Tahap 2!")
            }
        }
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
