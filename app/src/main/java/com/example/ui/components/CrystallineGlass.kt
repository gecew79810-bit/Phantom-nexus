package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.CyanNeon
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.GlassSurface
import com.example.ui.theme.VioletNeon
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/**
 * Modifier extension that applies a rich, multi-layered glassmorphic surface:
 * 1. Deep frosted substrate with chromatic gradient & absorption depth
 * 2. Subtle volumetric inner shadow for carved, high-mass physical glass
 * 3. Sophisticated dual-lattice crystalline micro-facets (isometric cleavage planes)
 * 4. Micro-stipple crystalline noise with prismatic sparkle glints and starburst micro-flares
 * 5. Specular chromatic prism highlight & bevel ridge
 */
fun Modifier.crystallineGlass(
    shape: Shape = RoundedCornerShape(14.dp),
    borderColor: Color = GlassBorder,
    glowAccent: Color = CyanNeon,
    specularGleam: Boolean = true,
    innerShadow: Boolean = true,
    innerShadowDepth: Dp = 3.dp,
    crystalFacets: Boolean = true,
    noiseDensity: Float = 1.0f,
    isActive: Boolean = false,
    alphaSubstrate: Float = 0.75f
): Modifier = this
    .clip(shape)
    .background(
        Brush.linearGradient(
            colors = if (isActive) {
                listOf(
                    glowAccent.copy(alpha = 0.30f),
                    Color(0xFF0F1F38).copy(alpha = 0.92f),
                    Color(0xFF060D1A).copy(alpha = 0.95f)
                )
            } else {
                listOf(
                    Color(0xFF14243D).copy(alpha = alphaSubstrate),
                    Color(0xFF0B1526).copy(alpha = alphaSubstrate * 0.95f),
                    Color(0xFF040812).copy(alpha = alphaSubstrate * 0.98f)
                )
            },
            start = Offset(0f, 0f),
            end = Offset(400f, 400f)
        )
    )
    .drawBehind {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@drawBehind

        val cornerRadiusPx = 10.dp.toPx()

        // -------------------------------------------------------------
        // 1. SUBTLE INNER SHADOW & VOLUMETRIC GLASS OCCLUSION
        // -------------------------------------------------------------
        if (innerShadow) {
            val shadowPx = innerShadowDepth.toPx()
            val steps = 4

            // Perimeter inward occlusion (dark rim just inside the boundary)
            for (step in 0 until steps) {
                val inset = step * (shadowPx / steps)
                val stepAlpha = ((steps - step).toFloat() / steps) * (if (isActive) 0.35f else 0.50f)
                val currentRadius = (cornerRadiusPx - inset).coerceAtLeast(2f)

                // Directional bottom-right inner shadow (simulating physical depth and ambient occlusion)
                drawRoundRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Transparent,
                            Color(0x33000511).copy(alpha = stepAlpha * 0.35f),
                            Color(0xFF000308).copy(alpha = stepAlpha * 0.85f)
                        ),
                        start = Offset(0f, 0f),
                        end = Offset(w, h)
                    ),
                    topLeft = Offset(inset, inset),
                    size = Size(w - inset * 2, h - inset * 2),
                    cornerRadius = CornerRadius(currentRadius, currentRadius),
                    style = Stroke(width = (shadowPx / steps).coerceAtLeast(1f))
                )
            }

            // Top-Left inner bevel counter-shadow (creates sunken/carved crystal facet illusion)
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color(0x66020712),
                        Color.Transparent,
                        Color.Transparent
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(w * 0.5f, h * 0.5f)
                ),
                topLeft = Offset(1.5f, 1.5f),
                size = Size(w - 3f, h - 3f),
                cornerRadius = CornerRadius(cornerRadiusPx - 1.5f, cornerRadiusPx - 1.5f),
                style = Stroke(width = 1.5.dp.toPx())
            )

            // When active, add subtle energetic inner light bleeding into the shadow
            if (isActive) {
                drawRoundRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            glowAccent.copy(alpha = 0.20f),
                            Color.Transparent
                        ),
                        center = Offset(w * 0.25f, h * 0.25f),
                        radius = (w + h) * 0.45f
                    ),
                    topLeft = Offset(1f, 1f),
                    size = Size(w - 2f, h - 2f),
                    cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx),
                    style = Stroke(width = 2.dp.toPx())
                )
            }
        }

        // -------------------------------------------------------------
        // 2. SOPHISTICATED CRYSTALLINE SURFACE TEXTURE (DUAL-LATTICE)
        // -------------------------------------------------------------
        if (crystalFacets) {
            // Isometric cleavage angle 1 (+32 degrees)
            val facetSpacing1 = 14.dp.toPx()
            val count1 = ((w + h) / facetSpacing1).toInt()
            for (i in 0..count1) {
                val startX = i * facetSpacing1 - h
                val startY = 0f
                val endX = i * facetSpacing1
                val endY = h
                val lineAlpha = when {
                    i % 5 == 0 -> if (isActive) 0.045f else 0.030f
                    i % 2 == 0 -> if (isActive) 0.025f else 0.015f
                    else -> 0.008f
                }
                drawLine(
                    color = if (i % 5 == 0 && isActive) CyanNeon.copy(alpha = lineAlpha * 1.5f) else Color.White.copy(alpha = lineAlpha),
                    start = Offset(startX, startY),
                    end = Offset(endX, endY),
                    strokeWidth = 0.75f
                )
            }

            // Isometric cleavage angle 2 (-32 degrees intersecting facets)
            val facetSpacing2 = 18.dp.toPx()
            val count2 = ((w + h) / facetSpacing2).toInt()
            for (j in 0..count2) {
                val startX = j * facetSpacing2
                val startY = 0f
                val endX = j * facetSpacing2 - h
                val endY = h
                val lineAlpha = when {
                    j % 4 == 0 -> if (isActive) 0.038f else 0.022f
                    else -> 0.009f
                }
                drawLine(
                    color = if (j % 4 == 0) Color(0xFF90CAF9).copy(alpha = lineAlpha) else Color.White.copy(alpha = lineAlpha),
                    start = Offset(startX, startY),
                    end = Offset(endX, endY),
                    strokeWidth = 0.65f
                )
            }

            // Subtle prismatic caustic wash across crystal planes
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.Transparent,
                        CyanNeon.copy(alpha = if (isActive) 0.06f else 0.025f),
                        VioletNeon.copy(alpha = if (isActive) 0.05f else 0.020f),
                        Color.Transparent
                    ),
                    start = Offset(0f, h * 0.2f),
                    end = Offset(w, h * 0.8f)
                ),
                topLeft = Offset.Zero,
                size = size,
                cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx)
            )
        }

        // -------------------------------------------------------------
        // 3. MICRO-CRYSTALLINE STIPPLE GRAIN & STARBURST GLINTS
        // -------------------------------------------------------------
        val step = (11.dp.toPx() / noiseDensity).coerceAtLeast(6f)
        val cols = (w / step).toInt().coerceAtLeast(1)
        val rows = (h / step).toInt().coerceAtLeast(1)

        for (r in 0..rows) {
            for (c in 0..cols) {
                val seed = (r * 31337 + c * 7919)
                val hash1 = sin(seed.toDouble()).toFloat() * 43758.5453f
                val frac1 = hash1 - floor(hash1)
                val hash2 = cos((seed * 3).toDouble()).toFloat() * 24634.63f
                val frac2 = hash2 - floor(hash2)

                val px = c * step + frac1 * (step * 0.85f)
                val py = r * step + frac2 * (step * 0.85f)

                if (px < w && py < h) {
                    val isGlint = (seed % 9 == 0)
                    val isCyanGlint = (seed % 7 == 0)
                    val isStarburst = (seed % 23 == 0) // Special 4-point micro-flare

                    val dotAlpha = when {
                        isStarburst -> if (isActive) 0.16f else 0.11f
                        isGlint -> if (isActive) 0.11f else 0.07f
                        else -> if (isActive) 0.045f else 0.025f
                    }

                    val dotRadius = when {
                        isStarburst -> 1.4f
                        isGlint -> 1.1f
                        else -> 0.65f
                    }

                    val dotColor = when {
                        isCyanGlint -> CyanNeon.copy(alpha = dotAlpha * 1.3f)
                        isStarburst -> Color.White.copy(alpha = dotAlpha * 1.4f)
                        isGlint -> Color(0xFFE1F5FE).copy(alpha = dotAlpha)
                        else -> Color(0xFF90CAF9).copy(alpha = dotAlpha)
                    }

                    drawCircle(
                        color = dotColor,
                        radius = dotRadius,
                        center = Offset(px, py)
                    )

                    // 4-point micro-flare starburst on select glints
                    if (isStarburst) {
                        val flareLen = 2.4.dp.toPx()
                        val flareColor = if (isCyanGlint) CyanNeon.copy(alpha = dotAlpha * 1.2f) else Color.White.copy(alpha = dotAlpha * 1.1f)
                        // Horizontal ray
                        drawLine(
                            color = flareColor,
                            start = Offset(px - flareLen, py),
                            end = Offset(px + flareLen, py),
                            strokeWidth = 0.65f
                        )
                        // Vertical ray
                        drawLine(
                            color = flareColor,
                            start = Offset(px, py - flareLen),
                            end = Offset(px, py + flareLen),
                            strokeWidth = 0.65f
                        )
                    }
                }
            }
        }

        // -------------------------------------------------------------
        // 4. SPECULAR PRISM HIGHLIGHT (UPPER & LEFT BEVEL RIDGES)
        // -------------------------------------------------------------
        if (specularGleam) {
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = if (isActive) 0.48f else 0.24f),
                        glowAccent.copy(alpha = if (isActive) 0.36f else 0.12f),
                        VioletNeon.copy(alpha = if (isActive) 0.18f else 0.04f),
                        Color.Transparent,
                        Color.Transparent
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(w * 0.75f, h * 0.75f)
                ),
                topLeft = Offset(0.5f, 0.5f),
                size = Size(w - 1f, h - 1f),
                cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx),
                style = Stroke(width = 1.dp.toPx())
            )
        }
    }
    .border(
        width = if (isActive) 1.5.dp else 1.dp,
        brush = if (isActive) {
            Brush.linearGradient(
                listOf(
                    glowAccent,
                    glowAccent.copy(alpha = 0.85f),
                    VioletNeon.copy(alpha = 0.60f),
                    glowAccent.copy(alpha = 0.35f)
                )
            )
        } else {
            Brush.linearGradient(
                listOf(
                    Color.White.copy(alpha = 0.35f),
                    borderColor.copy(alpha = 0.65f),
                    Color(0x2200E5FF),
                    Color.Transparent
                ),
                start = Offset(0f, 0f),
                end = Offset(300f, 300f)
            )
        },
        shape = shape
    )

/**
 * High-quality Glassmorphism surface container with frosted substrate,
 * inner shadow depth, crystalline micro-texture, and specular edge highlight.
 */
@Composable
fun CrystallineGlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    borderColor: Color = GlassBorder,
    glowAccent: Color = CyanNeon,
    specularGleam: Boolean = true,
    innerShadow: Boolean = true,
    innerShadowDepth: Dp = 3.dp,
    crystalFacets: Boolean = true,
    noiseDensity: Float = 1.0f,
    isActive: Boolean = false,
    alphaSubstrate: Float = 0.75f,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier.crystallineGlass(
            shape = shape,
            borderColor = borderColor,
            glowAccent = glowAccent,
            specularGleam = specularGleam,
            innerShadow = innerShadow,
            innerShadowDepth = innerShadowDepth,
            crystalFacets = crystalFacets,
            noiseDensity = noiseDensity,
            isActive = isActive,
            alphaSubstrate = alphaSubstrate
        ),
        content = content
    )
}
