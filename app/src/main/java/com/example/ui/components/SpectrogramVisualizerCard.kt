package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.SpectrogramSnapshot
import com.example.model.TranscriptFormatter
import com.example.ui.theme.ElectricAmethyst
import com.example.ui.theme.MagmaCoral
import com.example.ui.theme.StudioBackground
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.util.Locale

@Composable
fun SpectrogramVisualizerCard(
    spectrogram: SpectrogramSnapshot,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, StudioBorder, RoundedCornerShape(14.dp))
            .testTag("spectrogram_card"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = StudioSurface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Filled.GraphicEq,
                        contentDescription = "Mel Spectrogram Visualizer",
                        tint = ElectricAmethyst,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (spectrogram.fileName.isNotBlank()) {
                            "Mel Spectrogram • ${spectrogram.fileName}"
                        } else {
                            "Audio Mel Spectrogram (128-Mel FFT)"
                        },
                        style = MaterialTheme.typography.titleSmall,
                        color = ElectricAmethyst,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (spectrogram.showPlayhead) {
                    Text(
                        text = "t = ${String.format(Locale.US, "%.2fs", spectrogram.playheadTimeSec)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MagmaCoral,
                        modifier = Modifier
                            .background(Color(0xFF3A1A1E), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                } else if (spectrogram.durationSec > 0) {
                    Text(
                        text = "${spectrogram.sampleRate / 1000}kHz • ${TranscriptFormatter.formatEta(spectrogram.durationSec)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Canvas Plot Area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(148.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF0B0A10))
                    .border(1.dp, StudioBorder.copy(alpha = 0.7f), RoundedCornerShape(10.dp))
                    .testTag("spectrogram_canvas_box"),
                contentAlignment = Alignment.Center
            ) {
                when {
                    spectrogram.isComputing -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = ElectricAmethyst
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Computing 128-Mel FFT spectrogram...",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }
                    spectrogram.columns.isEmpty() -> {
                        Text(
                            text = "Select a media file or start transcription to view Mel spectrogram",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                    else -> {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val cols = spectrogram.columns
                            val numCols = cols.size
                            val numMels = spectrogram.nMels.coerceAtLeast(1)
                            val cellWidth = size.width / numCols.toFloat()
                            // Render 64 grouped Mel bands vertically for crisp high-speed rendering
                            val renderBands = 64
                            val binsPerBand = (numMels / renderBands).coerceAtLeast(1)
                            val cellHeight = size.height / renderBands.toFloat()

                            for (c in 0 until numCols) {
                                val colData = cols[c]
                                val x = c * cellWidth
                                for (b in 0 until renderBands) {
                                    val melIdx = (b * binsPerBand).coerceAtMost(colData.size - 1)
                                    val normVal = colData[melIdx]
                                    // Low frequency at bottom (y inverted)
                                    val y = size.height - (b + 1) * cellHeight
                                    drawRect(
                                        color = magmaColorMap(normVal),
                                        topLeft = Offset(x, y),
                                        size = Size(cellWidth + 0.8f, cellHeight + 0.8f)
                                    )
                                }
                            }

                            // Subtle horizontal frequency grid lines (2kHz, 4kHz, 6kHz)
                            val gridColor = Color.White.copy(alpha = 0.10f)
                            for (lineIdx in 1..3) {
                                val gy = size.height * (lineIdx / 4f)
                                drawLine(
                                    color = gridColor,
                                    start = Offset(0f, gy),
                                    end = Offset(size.width, gy),
                                    strokeWidth = 1f
                                )
                            }

                            // Red dashed progress line (matching Python's Line2D progress indicator)
                            if (spectrogram.showPlayhead && spectrogram.durationSec > 0.0) {
                                val fraction = (spectrogram.playheadTimeSec / spectrogram.durationSec)
                                    .toFloat()
                                    .coerceIn(0f, 1f)
                                val lineX = fraction * size.width
                                drawLine(
                                    color = Color(0xFFFF3B30),
                                    start = Offset(lineX, 0f),
                                    end = Offset(lineX, size.height),
                                    strokeWidth = 2.5.dp.toPx(),
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
                                )
                            }
                        }

                        // Overlay Axis Labels
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(6.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "8000 Hz (Mel)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextPrimary.copy(alpha = 0.72f),
                                    modifier = Modifier
                                        .background(StudioBackground.copy(alpha = 0.65f), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                                Text(
                                    text = "0 dB",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFFFCFDBF),
                                    modifier = Modifier
                                        .background(StudioBackground.copy(alpha = 0.65f), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "0.0s",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextPrimary.copy(alpha = 0.72f),
                                    modifier = Modifier
                                        .background(StudioBackground.copy(alpha = 0.65f), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                                Text(
                                    text = String.format(Locale.US, "%.1fs", spectrogram.durationSec),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextPrimary.copy(alpha = 0.72f),
                                    modifier = Modifier
                                        .background(StudioBackground.copy(alpha = 0.65f), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Magma dB Scale Legend Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Frequency (Mel) vs Time (s)",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "-80 dB",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .width(72.dp)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        Color(0xFF000004),
                                        Color(0xFF3B0F70),
                                        Color(0xFF8C2981),
                                        Color(0xFFDE4968),
                                        Color(0xFFFE9F6D),
                                        Color(0xFFFCFDBF)
                                    )
                                )
                            )
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "0 dB",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }
            }
        }
    }
}

/**
 * Perceptually uniform Magma colormap matching librosa.display.specshow(..., cmap='magma').
 */
private fun magmaColorMap(value: Float): Color {
    val t = value.coerceIn(0f, 1f)
    return when {
        t < 0.25f -> lerpColor(Color(0xFF07051A), Color(0xFF3B0F70), t / 0.25f)
        t < 0.50f -> lerpColor(Color(0xFF3B0F70), Color(0xFF8C2981), (t - 0.25f) / 0.25f)
        t < 0.75f -> lerpColor(Color(0xFF8C2981), Color(0xFFDE4968), (t - 0.50f) / 0.25f)
        t < 0.90f -> lerpColor(Color(0xFFDE4968), Color(0xFFFE9F6D), (t - 0.75f) / 0.15f)
        else -> lerpColor(Color(0xFFFE9F6D), Color(0xFFFCFDBF), (t - 0.90f) / 0.10f)
    }
}

private fun lerpColor(start: Color, end: Color, fraction: Float): Color {
    val f = fraction.coerceIn(0f, 1f)
    return Color(
        red = start.red + (end.red - start.red) * f,
        green = start.green + (end.green - start.green) * f,
        blue = start.blue + (end.blue - start.blue) * f,
        alpha = 1f
    )
}
