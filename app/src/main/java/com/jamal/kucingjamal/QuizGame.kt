package com.jamal.kucingjamal

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

data class QuizQuestion(
    val question: String,
    val options: List<String>,
    val correctIndex: Int,
    val explanation: String
)

data class QuizStats(
    val gamesPlayed: Int = 0,
    val totalCorrect: Int = 0,
    val bestScore: Int = 0,
    val perfectRuns: Int = 0
)

object QuizStatsStorage {
    private const val PREF_NAME = "jamal_quiz_stats"

    fun load(context: Context): QuizStats {
        val p = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return QuizStats(
            gamesPlayed = p.getInt("played", 0),
            totalCorrect = p.getInt("correct", 0),
            bestScore = p.getInt("best", 0),
            perfectRuns = p.getInt("perfect", 0)
        )
    }

    fun recordResult(context: Context, score: Int, total: Int): QuizStats {
        val prev = load(context)
        val next = QuizStats(
            gamesPlayed = prev.gamesPlayed + 1,
            totalCorrect = prev.totalCorrect + score,
            bestScore = maxOf(prev.bestScore, score),
            perfectRuns = prev.perfectRuns + if (score == total && total > 0) 1 else 0
        )
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
            .putInt("played", next.gamesPlayed)
            .putInt("correct", next.totalCorrect)
            .putInt("best", next.bestScore)
            .putInt("perfect", next.perfectRuns)
            .apply()
        return next
    }
}

private enum class QuizStage {
    SELECT, LOADING, PLAYING, COMPLETED
}

@Composable
fun QuizGameDialog(
    apiKeysRaw: String,
    onDismiss: () -> Unit,
    onQuizFinished: (score: Int, total: Int, comment: String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val presetTopics = remember {
        listOf("Matematika", "IPA", "Bahasa Inggris", "Sejarah", "Pengetahuan Umum", "Game & Tech")
    }
    val difficulties = remember { listOf("Santai", "Normal", "Susah") }

    var stage by remember { mutableStateOf(QuizStage.SELECT) }
    var selectedTopic by remember { mutableStateOf("Pengetahuan Umum") }
    var customTopic by remember { mutableStateOf("") }
    var selectedDifficulty by remember { mutableStateOf("Normal") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    var questions by remember { mutableStateOf<List<QuizQuestion>>(emptyList()) }
    var currentIdx by remember { mutableIntStateOf(0) }
    var selectedOpt by remember { mutableIntStateOf(-1) }
    var score by remember { mutableIntStateOf(0) }
    var stats by remember { mutableStateOf(QuizStatsStorage.load(context)) }
    var resultReported by remember { mutableStateOf(false) }

    val goldColor = Color(0xFFFFC107)
    val goldDark = Color(0xFFD69800)

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = GamePalette.TextBrown,
        unfocusedTextColor = GamePalette.TextBrown,
        focusedBorderColor = GamePalette.Teal,
        unfocusedBorderColor = GamePalette.CreamDark,
        focusedContainerColor = Color.White,
        unfocusedContainerColor = Color.White,
        cursorColor = GamePalette.Teal
    )

    fun jamalCommentFor(s: Int, total: Int): String = when {
        s >= total -> "Hmph, kebetulan doang itu bener semua! Tapi yaudah deh, lumayan pinter juga lu."
        s == 2 -> "Not bad lah, bener dua dari tiga. Masih ada salah satu sih, teliti dikit kek!"
        s == 1 -> "Cuma bener satu?! Belajar lagi sono, untung gue masih mau bikinin soal!"
        else -> "Astaga, nol besar! Otak lu lagi nge-lag ya? Sini belajar lagi sama gue!"
    }

    fun startQuiz() {
        val activeTopic = customTopic.trim().ifBlank { selectedTopic }
        errorMsg = null
        stage = QuizStage.LOADING
        currentIdx = 0
        selectedOpt = -1
        score = 0
        resultReported = false

        scope.launch {
            val res = GeminiClient.generateQuiz(apiKeysRaw, activeTopic, selectedDifficulty)
            res.onSuccess { list ->
                questions = list
                stage = QuizStage.PLAYING
            }.onFailure { err ->
                errorMsg = "Gagal bikin soal kuis: ${err.message?.take(120)}"
                stage = QuizStage.SELECT
            }
        }
    }

    fun finishAndClose() {
        if (stage == QuizStage.COMPLETED && !resultReported) {
            resultReported = true
            onQuizFinished(score, questions.size, jamalCommentFor(score, questions.size))
        }
        onDismiss()
    }

    val headerTitle = when (stage) {
        QuizStage.SELECT -> "Kuis Kilat Jamal"
        QuizStage.LOADING -> "Menyiapkan Soal..."
        QuizStage.PLAYING -> "Soal ${currentIdx + 1} / ${questions.size}"
        QuizStage.COMPLETED -> "COMPLETED"
    }
    val headerBg = when (stage) {
        QuizStage.COMPLETED -> GamePalette.Orange
        else -> GamePalette.Purple
    }
    val headerShadow = when (stage) {
        QuizStage.COMPLETED -> GamePalette.OrangeShadow
        else -> GamePalette.PurpleShadow
    }

    GameDialogCard(
        title = headerTitle,
        headerColor = headerBg,
        headerShadow = headerShadow,
        onDismiss = { finishAndClose() }
    ) {
        when (stage) {
            QuizStage.SELECT -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 460.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    // Kartu Rekor Pribadi
                    Row(
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.White)
                            .border(1.5.dp, GamePalette.CreamDark, RoundedCornerShape(14.dp))
                            .padding(vertical = 8.dp, horizontal = 6.dp)
                    ) {
                        QuizStatMini("MAIN", "${stats.gamesPlayed}")
                        QuizStatMini("REKOR", "${stats.bestScore}/3")
                        QuizStatMini("3 BINTANG", "${stats.perfectRuns}x")
                    }

                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "PILIH TOPIK KUIS (3 SOAL)",
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
                        presetTopics.forEach { topic ->
                            val isSelected = customTopic.isBlank() && selectedTopic == topic
                            GamePillButton(
                                text = topic,
                                bgColor = if (isSelected) GamePalette.GreenBtn else GamePalette.Teal,
                                shadowColor = if (isSelected) GamePalette.GreenBtnShadow else GamePalette.TealShadow,
                                fontSize = 11.sp
                            ) {
                                selectedTopic = topic
                                customTopic = ""
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = customTopic,
                        onValueChange = { customTopic = it },
                        label = { Text("Atau ketik topik bebas (opsional)") },
                        placeholder = { Text("Misal: Pecahan, Tata Surya, Anime") },
                        singleLine = true,
                        colors = fieldColors,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "TINGKAT KESULITAN",
                        color = GamePalette.TextBrown,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        difficulties.forEach { diff ->
                            val active = selectedDifficulty == diff
                            GamePillButton(
                                text = diff.uppercase(),
                                bgColor = if (active) GamePalette.Orange else GamePalette.TrackBg,
                                shadowColor = if (active) GamePalette.OrangeShadow else GamePalette.CreamDark,
                                fontSize = 11.sp,
                                modifier = Modifier.weight(1f)
                            ) {
                                selectedDifficulty = diff
                            }
                        }
                    }

                    errorMsg?.let { err ->
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = err,
                            color = GamePalette.Red,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(Modifier.height(16.dp))
                    GamePillButton(
                        text = "MULAI KUIS!",
                        icon = Icons.Filled.PlayArrow,
                        bgColor = GamePalette.GreenBtn,
                        shadowColor = GamePalette.GreenBtnShadow,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        startQuiz()
                    }
                }
            }

            QuizStage.LOADING -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 28.dp)
                ) {
                    CircularProgressIndicator(
                        color = GamePalette.Purple,
                        strokeWidth = 4.dp,
                        modifier = Modifier.size(44.dp)
                    )
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = "Bentar, Jamal lagi nyiapin 3 soal biar otak lu kepake...",
                        color = GamePalette.TextBrown,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
            }

            QuizStage.PLAYING -> {
                val q = questions.getOrNull(currentIdx)
                if (q != null) {
                    val answered = selectedOpt != -1
                    val letters = listOf("A", "B", "C", "D")

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 470.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        // Indikator progres 3 soal
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            questions.indices.forEach { idx ->
                                val barColor = when {
                                    idx < currentIdx -> GamePalette.GreenBtn
                                    idx == currentIdx -> GamePalette.Orange
                                    else -> GamePalette.TrackBg
                                }
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(50))
                                        .background(barColor)
                                )
                            }
                        }

                        Spacer(Modifier.height(12.dp))
                        // Kartu Pertanyaan
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.White)
                                .border(2.dp, GamePalette.CreamDark, RoundedCornerShape(16.dp))
                                .padding(14.dp)
                        ) {
                            Text(
                                text = q.question,
                                color = GamePalette.TextBrown,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                lineHeight = 21.sp
                            )
                        }

                        Spacer(Modifier.height(12.dp))

                        // Pilihan A-D
                        q.options.forEachIndexed { idx, optText ->
                            val isCorrect = idx == q.correctIndex
                            val isChosen = idx == selectedOpt

                            val bgColor = when {
                                !answered -> Color.White
                                isCorrect -> GamePalette.GreenBtn
                                isChosen -> GamePalette.Red
                                else -> Color(0xFFEDE6DA)
                            }
                            val borderColor = when {
                                !answered -> GamePalette.CreamDark
                                isCorrect -> GamePalette.GreenBtnShadow
                                isChosen -> GamePalette.RedShadow
                                else -> GamePalette.CreamDark
                            }
                            val txtColor = when {
                                !answered -> GamePalette.TextBrown
                                isCorrect || isChosen -> Color.White
                                else -> GamePalette.TextBrownSoft
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(borderColor)
                                    .padding(bottom = 3.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(bgColor)
                                    .clickable(enabled = !answered) {
                                        selectedOpt = idx
                                        if (idx == q.correctIndex) score++
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(26.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (answered && (isCorrect || isChosen)) Color.White.copy(alpha = 0.25f)
                                            else GamePalette.Teal
                                        )
                                ) {
                                    Text(
                                        text = letters.getOrElse(idx) { "?" },
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = optText,
                                    color = txtColor,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f)
                                )
                                if (answered && isCorrect) {
                                    Icon(
                                        Icons.Filled.CheckCircle,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                } else if (answered && isChosen) {
                                    Icon(
                                        Icons.Filled.Cancel,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }

                        // Pembahasan singkat setelah memilih jawaban
                        if (answered) {
                            Spacer(Modifier.height(10.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color(0xFFFFF8E1))
                                    .border(1.5.dp, goldDark.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                                    .padding(10.dp)
                            ) {
                                Text(
                                    text = "Pembahasan: ${q.explanation}",
                                    color = GamePalette.TextBrown,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    lineHeight = 17.sp
                                )
                            }

                            Spacer(Modifier.height(12.dp))
                            val isLast = currentIdx >= questions.lastIndex
                            GamePillButton(
                                text = if (isLast) "LIHAT SKOR BINTANG" else "SOAL BERIKUTNYA",
                                bgColor = GamePalette.Teal,
                                shadowColor = GamePalette.TealShadow,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                if (isLast) {
                                    stats = QuizStatsStorage.recordResult(context, score, questions.size)
                                    stage = QuizStage.COMPLETED
                                } else {
                                    currentIdx++
                                    selectedOpt = -1
                                }
                            }
                        }
                    }
                }
            }

            QuizStage.COMPLETED -> {
                val total = questions.size.coerceAtLeast(1)
                val earnedStars = score.coerceIn(0, 3)
                val comment = jamalCommentFor(score, total)

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Medali / Piala
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(goldDark)
                            .padding(bottom = 3.dp)
                            .clip(CircleShape)
                            .background(goldColor)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.EmojiEvents,
                            contentDescription = "Piala",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    Spacer(Modifier.height(10.dp))

                    // 3 Bintang Penilaian
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        for (starIdx in 1..3) {
                            val earned = starIdx <= earnedStars
                            Icon(
                                imageVector = if (earned) Icons.Filled.Star else Icons.Filled.StarBorder,
                                contentDescription = null,
                                tint = if (earned) goldColor else GamePalette.TrackBg,
                                modifier = Modifier.size(if (starIdx == 2) 52.dp else 42.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "SCORE: $score / $total",
                        color = GamePalette.TextBrown,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold
                    )

                    Spacer(Modifier.height(8.dp))
                    // Komentar Tsundere Jamal
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.White)
                            .border(1.5.dp, GamePalette.CreamDark, RoundedCornerShape(14.dp))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "\"$comment\"",
                            color = GamePalette.TextBrown,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(Modifier.height(10.dp))
                    // Ringkasan Rekor Pribadi
                    Row(
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(GamePalette.CreamDark.copy(alpha = 0.45f))
                            .padding(vertical = 8.dp)
                    ) {
                        QuizStatMini("TOTAL MAIN", "${stats.gamesPlayed}")
                        QuizStatMini("TOTAL BENAR", "${stats.totalCorrect}")
                        QuizStatMini("3 BINTANG", "${stats.perfectRuns}x")
                    }

                    Spacer(Modifier.height(14.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        GamePillButton(
                            text = "MAIN LAGI",
                            icon = Icons.Filled.Replay,
                            bgColor = GamePalette.Purple,
                            shadowColor = GamePalette.PurpleShadow,
                            modifier = Modifier.weight(1f)
                        ) {
                            if (!resultReported) {
                                resultReported = true
                                onQuizFinished(score, total, comment)
                            }
                            stage = QuizStage.SELECT
                        }
                        GamePillButton(
                            text = "SELESAI",
                            bgColor = GamePalette.GreenBtn,
                            shadowColor = GamePalette.GreenBtnShadow,
                            modifier = Modifier.weight(1f)
                        ) {
                            finishAndClose()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuizStatMini(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            color = GamePalette.TealShadow,
            fontSize = 15.sp,
            fontWeight = FontWeight.ExtraBold
        )
        Text(
            text = label,
            color = GamePalette.TextBrownSoft,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
