package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.math.absoluteValue
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun AudioEffectsPanel(
    eqEnabled: Boolean,
    eq10Bands: List<Pair<Int, Int>>, // 10 bands: frequency to dB
    bassStrength: Int, // 0 - 1000
    loudnessGain: Int, // 0 - 2000
    onEqToggle: (Boolean) -> Unit,
    onEq10BandChange: (Int, Int) -> Unit,
    onBassChange: (Int) -> Unit,
    onLoudnessChange: (Int) -> Unit,
    isPlaying: Boolean = false,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableStateOf(0) } // 0 = Faders, 1 = Aura Curve

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp)
        ),
        shape = RoundedCornerShape(28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // Header Row with Main Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Equalizer,
                        contentDescription = "Equalizer Panel",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "10-Band Studio DSP",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Switch(
                    checked = eqEnabled,
                    onCheckedChange = onEqToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.primary,
                        checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (eqEnabled) {
                // View Selector (Faders vs Aura Curve Graph)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selectedTab == 0) MaterialTheme.colorScheme.primary else Color.Transparent)
                            .clickable { selectedTab = 0 }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Faders",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (selectedTab == 0) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selectedTab == 1) MaterialTheme.colorScheme.primary else Color.Transparent)
                            .clickable { selectedTab = 1 }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Aura Curve",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (selectedTab == 1) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Custom 10-Band EQ Views
                if (selectedTab == 0) {
                    // Traditional Mixing Board Faders (Scrollable Row)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        eq10Bands.forEachIndexed { index, (freq, level) ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .width(46.dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .padding(vertical = 8.dp)
                            ) {
                                Text(
                                    text = if (level > 0) "+$level" else "$level",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                
                                // Custom Vertical Slider
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .width(36.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    VerticalFader(
                                        value = level.toFloat(),
                                        onValueChange = { onEq10BandChange(index, it.toInt()) },
                                        valueRange = -15f..15f,
                                        modifier = Modifier.fillMaxHeight()
                                    )
                                }
                                
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = if (freq >= 1000) "${freq / 1000}k" else "$freq",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    // Parametric Spline Curve View with Draggable Node Contexts and Animated Background
                    ParametricEqualizerGraph(
                        eq10Bands = eq10Bands,
                        onEq10BandChange = onEq10BandChange,
                        isPlaying = isPlaying,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Analog Enhancements Layout
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Bass Boost Panel
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Hearing,
                                        contentDescription = "Bass Boost",
                                        tint = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Sub-Bass",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(
                                    text = "${(bassStrength / 10)}%",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Slider(
                                value = bassStrength.toFloat(),
                                onValueChange = { onBassChange(it.toInt()) },
                                valueRange = 0f..1000f,
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.secondary,
                                    activeTrackColor = MaterialTheme.colorScheme.secondary,
                                    inactiveTrackColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)
                                )
                            )
                        }
                    }

                    // Loudness Panel
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.VolumeUp,
                                        contentDescription = "Loudness",
                                        tint = MaterialTheme.colorScheme.tertiary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Gain Amp",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(
                                    text = "+${(loudnessGain / 100)}dB",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.tertiary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Slider(
                                value = loudnessGain.toFloat(),
                                onValueChange = { onLoudnessChange(it.toInt()) },
                                valueRange = 0f..2000f,
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.tertiary,
                                    activeTrackColor = MaterialTheme.colorScheme.tertiary,
                                    inactiveTrackColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f)
                                )
                            )
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
                        .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f), RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(20.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = "DSP Flat",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Aura Studio EQ is Idle",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Toggle the switch in the top-right corner to engage the 10-band processor, sub-bass engine and master gain amp.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

/**
 * A beautiful, highly-interactive custom vertical slider (Fader)
 */
@Composable
fun VerticalFader(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier
) {
    val rangeLength = valueRange.endInclusive - valueRange.start
    
    BoxWithConstraints(
        modifier = modifier
            .fillMaxHeight()
            .width(36.dp)
    ) {
        val maxHeightPx = constraints.maxHeight.toFloat()
        
        // Custom draw block
        val primaryColor = MaterialTheme.colorScheme.primary
        val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
        
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        val deltaValue = (-dragAmount.y / maxHeightPx) * rangeLength
                        val newValue = (value + deltaValue).coerceIn(valueRange)
                        onValueChange(newValue)
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val tapFraction = (maxHeightPx - offset.y) / maxHeightPx
                        val newValue = (valueRange.start + tapFraction * rangeLength).coerceIn(valueRange)
                        onValueChange(newValue)
                    }
                }
        ) {
            val width = size.width
            val height = size.height
            val halfWidth = width / 2f
            
            // Draw central background fader track line
            drawLine(
                color = onSurfaceVariant,
                start = Offset(halfWidth, 0f),
                end = Offset(halfWidth, height),
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round
            )
            
            // Draw 0dB notch marker
            val zeroFraction = (0f - valueRange.start) / rangeLength
            val zeroY = height - (zeroFraction * height)
            drawLine(
                color = primaryColor.copy(alpha = 0.5f),
                start = Offset(halfWidth - 8.dp.toPx(), zeroY),
                end = Offset(halfWidth + 8.dp.toPx(), zeroY),
                strokeWidth = 1.5.dp.toPx()
            )

            // Current value Y position
            val valueFraction = (value - valueRange.start) / rangeLength
            val currentY = height - (valueFraction * height)
            
            // Active slider line track (from center 0dB)
            drawLine(
                color = primaryColor,
                start = Offset(halfWidth, zeroY),
                end = Offset(halfWidth, currentY),
                strokeWidth = 4.5.dp.toPx(),
                cap = StrokeCap.Round
            )
            
            // Slider Thumb - elegant audio mixer handle
            drawRoundRect(
                color = primaryColor,
                topLeft = Offset(halfWidth - 10.dp.toPx(), currentY - 5.dp.toPx()),
                size = Size(20.dp.toPx(), 10.dp.toPx()),
                cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
            )
            
            // Inner glowing fader indicator
            drawRoundRect(
                color = Color.White.copy(alpha = 0.8f),
                topLeft = Offset(halfWidth - 10.dp.toPx() + 1.dp.toPx(), currentY - 1.dp.toPx()),
                size = Size(20.dp.toPx() - 2.dp.toPx(), 2.dp.toPx()),
                cornerRadius = CornerRadius(1.dp.toPx(), 1.dp.toPx())
            )
        }
    }
}

/**
 * A stunning, fully interactive parametric spline equalizer graph.
 * Users can drag nodes vertically to adjust the 10 bands.
 * Also draws a beautiful dynamic audio spectrum analysis background.
 */
@Composable
fun ParametricEqualizerGraph(
    eq10Bands: List<Pair<Int, Int>>,
    onEq10BandChange: (Int, Int) -> Unit,
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val outlineColor = MaterialTheme.colorScheme.outlineVariant
    
    // Animate phase for procedural spectral background
    val infiniteTransition = rememberInfiniteTransition(label = "spectra_visualizer")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    // Touch gesture state
    var activeNodeIndex by remember { mutableStateOf(-1) }
    
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f))
            .border(1.dp, outlineColor.copy(alpha = 0.2f), RoundedCornerShape(20.dp))
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { startOffset ->
                            val width = size.width.toFloat()
                            val numPoints = eq10Bands.size
                            val cellWidth = width / (numPoints + 1)
                            
                            // Find closest node based on X coordinate
                            var closestIdx = -1
                            var minDistance = Float.MAX_VALUE
                            
                            for (i in 0 until numPoints) {
                                val nodeX = cellWidth * (i + 1)
                                val distance = (startOffset.x - nodeX).absoluteValue
                                if (distance < minDistance) {
                                    minDistance = distance
                                    closestIdx = i
                                }
                            }
                            
                            // Only capture if X distance is reasonable (e.g. within 32dp in pixels)
                            if (closestIdx != -1 && minDistance < 40.dp.toPx()) {
                                activeNodeIndex = closestIdx
                            }
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val idx = activeNodeIndex
                            if (idx != -1) {
                                val height = size.height.toFloat()
                                val currentLevel = eq10Bands[idx].second
                                
                                // Drag sensitivity relative to canvas height (full height = 30 dB range)
                                val deltaDb = -(dragAmount.y / height) * 30f
                                val newLevel = (currentLevel + deltaDb).coerceIn(-15f, 15f)
                                onEq10BandChange(idx, newLevel.toInt())
                            }
                        },
                        onDragEnd = {
                            activeNodeIndex = -1
                        },
                        onDragCancel = {
                            activeNodeIndex = -1
                        }
                    )
                }
                .pointerInput(Unit) {
                    detectTapGestures { tapOffset ->
                        val width = size.width.toFloat()
                        val numPoints = eq10Bands.size
                        val cellWidth = width / (numPoints + 1)
                        
                        var closestIdx = -1
                        var minDistance = Float.MAX_VALUE
                        
                        for (i in 0 until numPoints) {
                            val nodeX = cellWidth * (i + 1)
                            val distance = (tapOffset.x - nodeX).absoluteValue
                            if (distance < minDistance) {
                                minDistance = distance
                                closestIdx = i
                            }
                        }
                        
                        if (closestIdx != -1 && minDistance < 40.dp.toPx()) {
                            val height = size.height.toFloat()
                            // Calculate dB level from tap Y
                            val tapFraction = (height - tapOffset.y) / height
                            val newLevel = (tapFraction * 30f - 15f).coerceIn(-15f, 15f)
                            onEq10BandChange(closestIdx, newLevel.toInt())
                        }
                    }
                }
        ) {
            val width = size.width
            val height = size.height
            
            // --- 1. Draw Real-time Procedural Visualizer (Spectral Waterfall Background) ---
            if (isPlaying) {
                val barCount = 28
                val spacing = 3.dp.toPx()
                val barWidth = (width - (spacing * (barCount + 1))) / barCount
                for (b in 0 until barCount) {
                    // Compose organic spectral waves using trigonometric coefficients
                    val waveIndexFraction = b.toFloat() / barCount
                    val sinWave = sin(waveIndexFraction * 5.2f + phase) * 0.45f
                    val cosWave = cos(waveIndexFraction * 12.1f - phase * 1.3f) * 0.25f
                    val subWave = sin(waveIndexFraction * 20f + phase * 2.2f) * 0.15f
                    
                    val amplitudeFactor = (0.35f + sinWave + cosWave + subWave).coerceIn(0.08f, 0.95f)
                    
                    // Modulate by active equalizer sliders around that frequency spectrum!
                    val bandRangeIdx = (waveIndexFraction * eq10Bands.size).toInt().coerceIn(eq10Bands.indices)
                    val eqLevelDb = eq10Bands[bandRangeIdx].second
                    val eqDbFactor = ((eqLevelDb + 15) / 30f).coerceIn(0.3f, 1.5f)
                    
                    val barHeight = height * amplitudeFactor * 0.65f * eqDbFactor
                    val x = spacing + b * (barWidth + spacing)
                    val y = height - barHeight
                    
                    // Neon gradient for analyzer bars
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                primaryColor.copy(alpha = 0.12f),
                                secondaryColor.copy(alpha = 0.02f)
                            )
                        ),
                        topLeft = Offset(x, y),
                        size = Size(barWidth, barHeight),
                        cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                    )
                }
            }

            // --- 2. Draw Horizontal DB Guide Lines ---
            val dbLevels = listOf(15, 10, 5, 0, -5, -10, -15)
            dbLevels.forEach { db ->
                val fraction = (db - (-15)) / 30f
                val y = height - (fraction * height)
                
                drawLine(
                    color = outlineColor.copy(alpha = if (db == 0) 0.25f else 0.1f),
                    start = Offset(0f, y),
                    end = Offset(width, y),
                    strokeWidth = (if (db == 0) 1.5.dp else 1.dp).toPx()
                )
            }

            // --- 3. Compute Coordinates of the 10 EQ Nodes ---
            val numPoints = eq10Bands.size
            val cellWidth = width / (numPoints + 1)
            val points = eq10Bands.mapIndexed { idx, (_, level) ->
                val x = cellWidth * (idx + 1)
                val fraction = (level - (-15)) / 30f
                val y = height - (fraction * height)
                Offset(x, y)
            }

            // --- 4. Draw Smooth Parametric Spline Path ---
            if (points.isNotEmpty()) {
                val splinePath = Path().apply {
                    // Start slightly before first node
                    moveTo(0f, points[0].y)
                    lineTo(points[0].x, points[0].y)
                    
                    for (i in 0 until points.size - 1) {
                        val p0 = points[i]
                        val p1 = points[i + 1]
                        
                        // Cubic control points for organic dsp response interpolation
                        val ctrlX1 = p0.x + (p1.x - p0.x) * 0.5f
                        val ctrlY1 = p0.y
                        val ctrlX2 = p0.x + (p1.x - p0.x) * 0.5f
                        val ctrlY2 = p1.y
                        
                        cubicTo(ctrlX1, ctrlY1, ctrlX2, ctrlY2, p1.x, p1.y)
                    }
                    
                    lineTo(width, points.last().y)
                }

                // Draw filled gradient area under the curve
                val fillPath = Path().apply {
                    addPath(splinePath)
                    lineTo(width, height)
                    lineTo(0f, height)
                    close()
                }
                
                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            primaryColor.copy(alpha = 0.22f),
                            primaryColor.copy(alpha = 0.0f)
                        )
                    )
                )

                // Draw main curve stroke with high-contrast glowing neon color
                drawPath(
                    path = splinePath,
                    color = primaryColor,
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                )

                // --- 5. Draw Individual Nodes ---
                points.forEachIndexed { idx, point ->
                    val isActive = (idx == activeNodeIndex)
                    
                    // Glowing outer circle ring
                    drawCircle(
                        color = (if (isActive) secondaryColor else primaryColor).copy(alpha = 0.25f),
                        radius = (if (isActive) 15.dp else 11.dp).toPx(),
                        center = point
                    )
                    
                    // Node stroke
                    drawCircle(
                        color = if (isActive) secondaryColor else primaryColor,
                        radius = (if (isActive) 8.dp else 6.dp).toPx(),
                        center = point,
                        style = Stroke(width = 2.dp.toPx())
                    )
                    
                    // Node solid core
                    drawCircle(
                        color = Color.White,
                        radius = (if (isActive) 4.dp else 3.dp).toPx(),
                        center = point
                    )
                }
            }
        }
    }
}
