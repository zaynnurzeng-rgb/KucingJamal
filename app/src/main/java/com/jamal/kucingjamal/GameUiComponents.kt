package com.jamal.kucingjamal

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

object GamePalette {
    val LimeTop = Color(0xFF84CC3D)
    val LimeShadow = Color(0xFF5D9C22)

    val Teal = Color(0xFF2196A8)
    val TealShadow = Color(0xFF156977)

    val Cream = Color(0xFFF6F1E7)
    val CreamDark = Color(0xFFDDD2C0)
    val TrackBg = Color(0xFFCBBFAD)

    val Red = Color(0xFFD93B3B)
    val RedStripe = Color(0xFFB52626)
    val RedShadow = Color(0xFF9C1E1E)

    val Orange = Color(0xFFF38221)
    val OrangeStripe = Color(0xFFD66410)
    val OrangeShadow = Color(0xFFB8520A)

    val GreenBtn = Color(0xFF7EC936)
    val GreenBtnShadow = Color(0xFF56941C)

    val Purple = Color(0xFF6B53A3)
    val PurpleShadow = Color(0xFF4A3778)

    val Blue = Color(0xFF2B8EDB)
    val BlueShadow = Color(0xFF1A64A3)

    val TextBrown = Color(0xFF47372D)
    val TextBrownSoft = Color(0xFF7D6B5D)
}

@Composable
fun StripedStatusPill(
    value: Int,
    icon: ImageVector,
    iconTint: Color,
    fillColor: Color,
    stripeColor: Color,
    modifier: Modifier = Modifier
) {
    val progress by animateFloatAsState(
        targetValue = (value.coerceIn(0, 100) / 100f),
        animationSpec = tween(durationMillis = 350),
        label = "statusBar"
    )

    // Kapsul krem dengan bayangan bawah khas game UI
    Box(
        modifier = modifier
            .height(34.dp)
            .clip(RoundedCornerShape(50))
            .background(GamePalette.CreamDark)
            .padding(bottom = 3.dp)
            .clip(RoundedCornerShape(50))
            .background(GamePalette.Cream)
            .padding(horizontal = 6.dp, vertical = 4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxSize()
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(5.dp))
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(50))
                    .background(GamePalette.TrackBg)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val fillW = (size.width * progress).coerceIn(0f, size.width)
                    if (fillW > 2f) {
                        val radius = size.height / 2f
                        val clip = Path().apply {
                            addRoundRect(
                                RoundRect(
                                    rect = Rect(0f, 0f, fillW, size.height),
                                    cornerRadius = CornerRadius(radius, radius)
                                )
                            )
                        }
                        clipPath(clip) {
                            drawRect(color = fillColor, size = Size(fillW, size.height))
                            val stripeW = 5.dp.toPx()
                            val gap = 10.dp.toPx()
                            var x = -size.height
                            while (x < fillW + size.height) {
                                drawLine(
                                    color = stripeColor,
                                    start = Offset(x, size.height),
                                    end = Offset(x + size.height * 0.65f, 0f),
                                    strokeWidth = stripeW
                                )
                                x += gap
                            }
                            drawRect(
                                color = Color.White.copy(alpha = 0.24f),
                                size = Size(fillW, size.height * 0.36f)
                            )
                        }
                    }
                }
                Text(
                    text = "$value%",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }
}

@Composable
fun GameVectorCircleButton(
    icon: ImageVector,
    bgColor: Color,
    shadowColor: Color,
    contentDescription: String,
    size: Dp = 54.dp,
    iconSize: Dp = 25.dp,
    label: String? = null,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(GamePalette.CreamDark)
                .padding(bottom = 3.dp)
                .clip(CircleShape)
                .background(GamePalette.Cream)
                .padding(4.dp)
                .clip(CircleShape)
                .background(shadowColor)
                .padding(bottom = 3.dp)
                .clip(CircleShape)
                .background(bgColor)
                .clickable(onClick = onClick)
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth(0.7f)
                    .height(size * 0.2f)
                    .padding(top = 3.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.25f))
            )
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = Color.White,
                modifier = Modifier.size(iconSize)
            )
        }
        if (label != null) {
            Spacer(Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(GamePalette.Cream.copy(alpha = 0.92f))
                    .border(1.dp, GamePalette.CreamDark, RoundedCornerShape(50))
                    .padding(horizontal = 8.dp, vertical = 1.dp)
            ) {
                Text(
                    text = label,
                    color = GamePalette.TextBrown,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun GameImageCircleButton(
    bitmap: ImageBitmap,
    bgColor: Color,
    shadowColor: Color,
    contentDescription: String,
    size: Dp = 52.dp,
    onClick: () -> Unit
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(GamePalette.CreamDark)
            .padding(bottom = 3.dp)
            .clip(CircleShape)
            .background(GamePalette.Cream)
            .padding(3.5.dp)
            .clip(CircleShape)
            .background(shadowColor)
            .padding(bottom = 2.5.dp)
            .clip(CircleShape)
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(4.dp)
    ) {
        Image(
            bitmap = bitmap,
            contentDescription = contentDescription,
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
        )
    }
}

@Composable
fun GamePillButton(
    text: String,
    bgColor: Color,
    shadowColor: Color,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    fontSize: TextUnit = 13.sp,
    onClick: () -> Unit
) {
    val activeBg = if (enabled) bgColor else Color(0xFFB5ADA2)
    val activeShadow = if (enabled) shadowColor else Color(0xFF948C82)

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(GamePalette.CreamDark)
            .padding(bottom = 2.dp)
            .clip(RoundedCornerShape(50))
            .background(GamePalette.Cream)
            .padding(3.dp)
            .clip(RoundedCornerShape(50))
            .background(activeShadow)
            .padding(bottom = 3.dp)
            .clip(RoundedCornerShape(50))
            .background(activeBg)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text = text,
                color = Color.White,
                fontSize = fontSize,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun GameDialogCard(
    title: String,
    onDismiss: () -> Unit,
    headerColor: Color = GamePalette.Teal,
    headerShadow: Color = GamePalette.TealShadow,
    content: @Composable ColumnScope.() -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(GamePalette.CreamDark)
                .padding(bottom = 5.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(GamePalette.Cream)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Header Teal khas kartu OPTION / STORE
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(headerShadow)
                        .padding(bottom = 4.dp)
                        .background(headerColor)
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = title.uppercase(),
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(end = 38.dp)
                    )
                    // Tombol X merah bulat
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(GamePalette.RedShadow)
                            .padding(bottom = 2.dp)
                            .clip(CircleShape)
                            .background(GamePalette.Red)
                            .clickable(onClick = onDismiss)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Tutup",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    content = content
                )
            }
        }
    }
}

fun parseGameRichText(raw: String): AnnotatedString = buildAnnotatedString {
    val lines = raw.lines()
    lines.forEachIndexed { lineIdx, rawLine ->
        val trimmed = rawLine.trimStart()
        val isHeading = trimmed.startsWith("### ") || trimmed.startsWith("## ") || trimmed.startsWith("# ")
        val lineText = when {
            trimmed.startsWith("### ") -> trimmed.removePrefix("### ")
            trimmed.startsWith("## ") -> trimmed.removePrefix("## ")
            trimmed.startsWith("# ") -> trimmed.removePrefix("# ")
            trimmed.startsWith("* ") -> "• " + trimmed.removePrefix("* ")
            trimmed.startsWith("- ") -> "• " + trimmed.removePrefix("- ")
            else -> rawLine
        }

        if (isHeading) {
            withStyle(SpanStyle(fontWeight = FontWeight.ExtraBold)) {
                appendInlineMarkdown(lineText.replace("**", ""))
            }
        } else {
            appendInlineMarkdown(lineText)
        }

        if (lineIdx < lines.lastIndex) {
            append("\n")
        }
    }
}

private fun AnnotatedString.Builder.appendInlineMarkdown(text: String) {
    var i = 0
    val n = text.length
    while (i < n) {
        if (i + 1 < n && text[i] == '*' && text[i + 1] == '*') {
            val end = text.indexOf("**", startIndex = i + 2)
            if (end != -1) {
                withStyle(SpanStyle(fontWeight = FontWeight.ExtraBold)) {
                    append(text.substring(i + 2, end))
                }
                i = end + 2
                continue
            }
        }
        if (text[i] == '`') {
            val end = text.indexOf('`', startIndex = i + 1)
            if (end != -1) {
                withStyle(
                    SpanStyle(
                        fontWeight = FontWeight.SemiBold,
                        background = Color(0x22000000)
                    )
                ) {
                    append(" ${text.substring(i + 1, end)} ")
                }
                i = end + 1
                continue
            }
        }
        if (text[i] == '*' && (i + 1 < n && text[i + 1] != ' ')) {
            val end = text.indexOf('*', startIndex = i + 1)
            if (end != -1 && end > i + 1) {
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                    append(text.substring(i + 1, end))
                }
                i = end + 1
                continue
            }
        }
        append(text[i])
        i++
    }
}
