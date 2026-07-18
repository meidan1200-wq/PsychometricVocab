package com.example.psychometricvocab.ui.components

import android.content.Context
import android.speech.tts.TextToSpeech
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.psychometricvocab.LocalAppState
import com.example.psychometricvocab.data.Word
import com.example.psychometricvocab.theme.*
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** Swipe threshold in dp */
private val SWIPE_THRESHOLD = 100.dp

// ─── TTS Helper ──────────────────────────────────────────────────────────────

class TtsHelper(context: Context) {
    private var tts: TextToSpeech? = null
    init {
        tts = TextToSpeech(context) {}
    }
    fun speak(text: String, isEnglish: Boolean) {
        tts?.language = if (isEnglish) Locale.ENGLISH else Locale("he", "IL")
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }
    fun shutdown() { tts?.shutdown() }
}
@Composable
fun SwipeableFlashCard(
    word: Word,
    cardIndex: Int,
    totalCards: Int,
    onSwipeKnown: () -> Unit,
    onSwipeUnknown: () -> Unit
) {
    val appState = LocalAppState.current
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val isHebrew = appState.isHebrew

    val swipeThresholdPx = with(density) { SWIPE_THRESHOLD.toPx() }

    var offsetX by remember { mutableFloatStateOf(0f) }
    var isFlipped by remember { mutableStateOf(false) }
    var isDismissed by remember { mutableStateOf(false) }

    val animatedOffsetX by animateFloatAsState(
        targetValue = if (isDismissed) offsetX * 3f else offsetX,
        animationSpec = if (isDismissed) tween(300) else tween(50),
        label = "cardSwipe",
        finishedListener = {
            if (isDismissed) {
                if (offsetX > 0) onSwipeKnown() else onSwipeUnknown()
            }
        }
    )

    val rotation by animateFloatAsState(
        targetValue = (offsetX / swipeThresholdPx).coerceIn(-1f, 1f) * 8f,
        label = "cardRotation"
    )

    val tts = remember { TtsHelper(context) }
    DisposableEffect(Unit) { onDispose { tts.shutdown() } }

    // Flip animation
    val flipRotation by animateFloatAsState(
        targetValue = if (isFlipped) 180f else 0f,
        animationSpec = tween(400, easing = FastOutSlowInEasing),
        label = "flip"
    )

    // Swipe overlay alpha
    val rightAlpha = ((offsetX / swipeThresholdPx).coerceIn(0f, 1f))
    val leftAlpha = ((-offsetX / swipeThresholdPx).coerceIn(0f, 1f))
    
    val knowAlpha = rightAlpha
    val unknownAlpha = leftAlpha

    val frontWord = word.word
    val backWord = word.definition

    CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            // Stack background cards for depth
            repeat(2) { i ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .height(300.dp)
                        .offset(y = ((i + 1) * 6).dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(SurfaceGray.copy(alpha = 0.6f - i * 0.2f))
                )
            }

            // Main swipeable card
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .height(300.dp)
                    .offset { IntOffset(animatedOffsetX.roundToInt(), 0) }
                    .rotate(rotation)
                    .shadow(8.dp, RoundedCornerShape(20.dp))
                    .clip(RoundedCornerShape(20.dp))
                    .background(White)
                    .pointerInput(word.id) {
                        detectDragGestures(
                            onDragEnd = {
                                if (abs(offsetX) > swipeThresholdPx) {
                                    isDismissed = true
                                } else {
                                    scope.launch { offsetX = 0f }
                                }
                            },
                            onDragCancel = { scope.launch { offsetX = 0f } },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                offsetX += dragAmount.x
                            }
                        )
                    }
                    .clickable { isFlipped = !isFlipped },
                contentAlignment = Alignment.Center
            ) {
                // Green know overlay
                if (knowAlpha > 0f) {
                    Box(
                        modifier = Modifier.fillMaxSize()
                            .clip(RoundedCornerShape(20.dp))
                            .background(CorrectGreen.copy(alpha = knowAlpha * 0.3f)),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = null,
                            tint = CorrectGreen.copy(alpha = knowAlpha),
                            modifier = Modifier.padding(start = 24.dp).size(48.dp)
                        )
                    }
                }
                // Red unknown overlay
                if (unknownAlpha > 0f) {
                    Box(
                        modifier = Modifier.fillMaxSize()
                            .clip(RoundedCornerShape(20.dp))
                            .background(WrongRed.copy(alpha = unknownAlpha * 0.3f)),
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = null,
                            tint = WrongRed.copy(alpha = unknownAlpha),
                            modifier = Modifier.padding(end = 24.dp).size(48.dp)
                        )
                    }
                }

                // Card content (flip logic)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { rotationY = flipRotation; cameraDistance = 12f * density.density }
                ) {
                    // Front
                    if (flipRotation <= 90f) {
                        CardFace(
                            word = frontWord,
                            isBack = false,
                            onSpeak = { tts.speak(frontWord, appState.track == "english") },
                            unitNumber = word.unit
                        )
                    }
                    // Back
                    if (flipRotation > 90f) {
                        Box(
                            modifier = Modifier.fillMaxSize()
                                .graphicsLayer { rotationY = 180f }
                        ) {
                            CardFace(
                                word = backWord,
                                isBack = true,
                                onSpeak = { tts.speak(backWord, false) },
                                unitNumber = word.unit
                            )
                        }
                    }
                }

                // Flip hint
                if (offsetX == 0f && !isFlipped) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 12.dp)
                            .alpha(0.5f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Filled.TouchApp, contentDescription = null, modifier = Modifier.size(14.dp), tint = TextHint)
                        Text(
                            text = if (isHebrew) "הקש להפוך" else "Tap to flip",
                            fontSize = 11.sp,
                            color = TextHint
                        )
                    }
                }
            }

            // Swipe hint labels
            Row(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .padding(top = 340.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = null, tint = WrongRed.copy(alpha = 0.6f), modifier = Modifier.size(16.dp))
                    Text(
                        text = if (isHebrew) "לא יודע" else "Don't know",
                        fontSize = 12.sp, color = WrongRed.copy(alpha = 0.7f), fontWeight = FontWeight.Medium
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = if (isHebrew) "יודע" else "Know it",
                        fontSize = 12.sp, color = CorrectGreen.copy(alpha = 0.7f), fontWeight = FontWeight.Medium
                    )
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = CorrectGreen.copy(alpha = 0.6f), modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
private fun CardFace(
    word: String,
    isBack: Boolean,
    onSpeak: () -> Unit,
    unitNumber: Int
) {
    val appState = LocalAppState.current
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (isBack) {
            Text(
                text = if (appState.isHebrew) "תרגום" else "Translation",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        // Speaker + Word column
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Yellow.copy(alpha = 0.2f))
                    .clickable { onSpeak() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Speak", tint = YellowDark, modifier = Modifier.size(24.dp))
            }

            Text(
                text = word,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
                color = TextPrimary
            )
        }


    }
}
