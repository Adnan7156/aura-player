package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.sin

@Composable
fun WaveformProgressBar(
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    waveColor: Color = MaterialTheme.colorScheme.primary,
    inactiveColor: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
) {
    val duration = durationMs.coerceAtLeast(1)
    val progress = (positionMs.toFloat() / duration).coerceIn(0f, 1f)
    val activeGradientEndColor = MaterialTheme.colorScheme.primaryContainer

    BoxWithConstraints(modifier = modifier.fillMaxWidth().height(48.dp)) {
        val width = constraints.maxWidth.toFloat()
        val height = constraints.maxHeight.toFloat()

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(duration) {
                    detectTapGestures { offset ->
                        val clickedProgress = (offset.x / width).coerceIn(0f, 1f)
                        onSeek((clickedProgress * duration).toLong())
                    }
                }
        ) {
            val centerY = height / 2f
            val barCount = 40
            val spacing = width / barCount

            for (i in 0 until barCount) {
                val x = i * spacing + spacing / 2f
                val barProgress = i.toFloat() / barCount

                // Generate a beautiful organic wave shape using sine waves
                val amplitudeFactor = sin(barProgress * Math.PI).toFloat()
                val waveHeight = (height * 0.75f * amplitudeFactor) * (0.4f + 0.6f * sin(i * 0.4f).toFloat())

                val activeBrush = Brush.linearGradient(
                    colors = listOf(waveColor, activeGradientEndColor),
                    start = Offset(x, centerY - waveHeight / 2),
                    end = Offset(x, centerY + waveHeight / 2)
                )

                val color = if (barProgress <= progress) waveColor else inactiveColor
                val finalBrush = if (barProgress <= progress) activeBrush else Brush.linearGradient(
                    colors = listOf(inactiveColor, inactiveColor)
                )

                drawLine(
                    brush = finalBrush,
                    start = Offset(x, centerY - waveHeight / 2),
                    end = Offset(x, centerY + waveHeight / 2),
                    strokeWidth = 6.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }
    }
}
