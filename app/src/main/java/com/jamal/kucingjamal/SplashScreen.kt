package com.jamal.kucingjamal

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun JamalApp() {
    var isLoaded by remember { mutableStateOf(false) }

    if (isLoaded) {
        KucingJamalScreen()
    } else {
        JamalSplashScreen(onFinished = { isLoaded = true })
    }
}

private fun loadSplashBitmap(context: Context): ImageBitmap? = runCatching {
    context.assets.open("tom/ui/splash_jamal.png").use { stream ->
        BitmapFactory.decodeStream(stream)?.asImageBitmap()
    }
}.getOrNull()

@Composable
fun JamalSplashScreen(onFinished: () -> Unit) {
    val context = LocalContext.current
    val splashBitmap = remember { loadSplashBitmap(context) }
    var progress by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        for (p in 0..100) {
            progress = p
            val stepDelay = when {
                p < 30 -> 18L
                p < 75 -> 24L
                p < 95 -> 30L
                else -> 20L
            }
            delay(stepDelay)
        }
        delay(200L)
        onFinished()
    }

    val statusCaption = when {
        progress < 30 -> "Membangunkan Kucing Jamal..."
        progress < 65 -> "Menyiapkan omelan tsundere..."
        progress < 100 -> "Bentar, sabar dikit!"
        else -> "Siap!"
    }

    val textShadow = Shadow(
        color = Color.Black.copy(alpha = 0.85f),
        offset = Offset(2f, 2f),
        blurRadius = 4f
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1A1A1A))
    ) {
        // Layer 1: Gambar Splash Full-Screen
        splashBitmap?.let { bmp ->
            Image(
                bitmap = bmp,
                contentDescription = "Splash Kucing Jamal",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Layer 2: Gradasi Gelap Halus di Bagian Bawah agar Bar & Teks Jelas Terbaca
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(180.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.65f)
                        )
                    )
                )
        )

        // Layer 3: Loading Bar 0% - 100%
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 28.dp, end = 28.dp, bottom = 36.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 6.dp)
            ) {
                Text(
                    text = statusCaption,
                    style = TextStyle(
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        shadow = textShadow
                    )
                )
                Text(
                    text = "$progress%",
                    style = TextStyle(
                        color = Color(0xFFFFD54F),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        shadow = textShadow
                    )
                )
            }

            // Track Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(22.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xCC2B1608))
                    .border(
                        width = 2.dp,
                        color = Color(0xFFFFE082),
                        shape = RoundedCornerShape(50)
                    )
                    .padding(3.dp)
            ) {
                // Indikator Maju 0% -> 100%
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fraction = (progress / 100f).coerceIn(0f, 1f))
                        .clip(RoundedCornerShape(50))
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    Color(0xFFFF8F00),
                                    Color(0xFFFFCA28),
                                    Color(0xFFFFB300)
                                )
                            )
                        )
                ) {
                    // Kilap glossy di bagian atas bar
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .background(Color.White.copy(alpha = 0.35f))
                    )
                }
            }
        }
    }
}
