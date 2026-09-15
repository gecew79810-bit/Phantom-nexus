package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ai.CapabilityNode
import com.example.ui.theme.CyanNeon
import com.example.ui.theme.DeepSpaceBlack
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.MagentaNeon
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VioletNeon
import kotlin.math.cos
import kotlin.math.sin

/**
 * Photorealistic Futuristic Planet MAX CORE with:
 * - Detailed spherical atmosphere and dark continent depth
 * - Millions of light points / city cluster stars
 * - Multiple orbital rings (primary, secondary, data ring)
 * - Animated data flow energy links connecting nodes to the core
 * - 6 Floating Orbital Capability Nodes (THINK, EXECUTE, SEARCH, AUTOMATE, ANALYZE, LEARN)
 */
@Composable
fun MaxPlanetCore(
    activeCapability: CapabilityNode,
    modifier: Modifier = Modifier,
    onNodeClicked: (CapabilityNode) -> Unit = {}
) {
    val infiniteTransition = rememberInfiniteTransition(label = "planet_orbit_anim")

    // Slow orbital ring rotation
    val angleDegrees by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(28000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orbit_angle"
    )

    // Reverse slow rotation for secondary ring
    val reverseAngleDegrees by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(36000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "reverse_orbit_angle"
    )

    // Gentle pulse for atmosphere glow & energy stream
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(2800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_pulse"
    )

    // Energy packet traveling along streams (0f to 1f)
    val energyPulseProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "energy_stream"
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(240.dp),
        contentAlignment = Alignment.Center
    ) {
        val viewWidth = maxWidth
        val viewHeight = maxHeight

        // Canvas for realistic digital planet, rings, particle fields and data flow energy streams
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f - 4.dp.toPx())
            val planetRadius = 56.dp.toPx()

            // 1. Deep Space Atmospheric Bloom (Radial)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        CyanNeon.copy(alpha = 0.35f * pulseAlpha),
                        VioletNeon.copy(alpha = 0.18f * pulseAlpha),
                        ElectricBlue.copy(alpha = 0.08f * pulseAlpha),
                        Color.Transparent
                    ),
                    center = center,
                    radius = planetRadius * 2.2f
                ),
                radius = planetRadius * 2.2f,
                center = center
            )

            // 2. Distant Deep Space Background Starfield (deterministic micro-stars)
            for (i in 0 until 40) {
                val seedAngle = (i * 9.2f) * (Math.PI / 180f)
                val distFactor = (i % 7) / 7f * (size.width * 0.46f) + planetRadius * 1.1f
                val sx = center.x + cos(seedAngle).toFloat() * distFactor
                val sy = center.y + sin(seedAngle).toFloat() * (distFactor * 0.48f)
                val starAlpha = ((i % 5) + 2) / 10f * pulseAlpha
                drawCircle(
                    color = if (i % 3 == 0) CyanNeon.copy(alpha = starAlpha) else Color.White.copy(alpha = starAlpha),
                    radius = if (i % 4 == 0) 1.8f else 1.0f,
                    center = Offset(sx, sy)
                )
            }

            // 3. Primary Orbital Ring (Tilted Oval with glowing dash)
            val ring1Width = planetRadius * 1.85f
            val ring1Height = planetRadius * 0.72f
            drawOval(
                brush = Brush.sweepGradient(
                    colors = listOf(
                        CyanNeon.copy(alpha = 0.15f),
                        CyanNeon.copy(alpha = 0.85f),
                        VioletNeon.copy(alpha = 0.9f),
                        MagentaNeon.copy(alpha = 0.4f),
                        CyanNeon.copy(alpha = 0.15f)
                    ),
                    center = center
                ),
                topLeft = Offset(center.x - ring1Width, center.y - ring1Height),
                size = androidx.compose.ui.geometry.Size(ring1Width * 2, ring1Height * 2),
                style = Stroke(
                    width = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(24f, 16f), angleDegrees * 1.2f)
                )
            )

            // 4. Secondary Cross Orbital Ring
            val ring2Width = planetRadius * 1.55f
            val ring2Height = planetRadius * 0.52f
            drawOval(
                brush = Brush.sweepGradient(
                    colors = listOf(
                        VioletNeon.copy(alpha = 0.6f),
                        ElectricBlue.copy(alpha = 0.7f),
                        CyanNeon.copy(alpha = 0.2f),
                        VioletNeon.copy(alpha = 0.6f)
                    ),
                    center = center
                ),
                topLeft = Offset(center.x - ring2Width, center.y - ring2Height),
                size = androidx.compose.ui.geometry.Size(ring2Width * 2, ring2Height * 2),
                style = Stroke(
                    width = 1.2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 12f), reverseAngleDegrees)
                )
            )

            // 5. Data HUD Coordinate Ring
            drawCircle(
                color = CyanNeon.copy(alpha = 0.15f),
                radius = planetRadius * 1.22f,
                center = center,
                style = Stroke(
                    width = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 18f), angleDegrees * 0.4f)
                )
            )

            // 6. Realistic Digital Planet Core Sphere Body
            // Radial depth shading giving a cinematic 3D sphere look
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF1E3A5F), // Sunlit cyber oceanic surface
                        Color(0xFF0D1E35),
                        Color(0xFF060D19),
                        Color(0xFF02040A)  // Deep shadow edge
                    ),
                    center = Offset(center.x - planetRadius * 0.35f, center.y - planetRadius * 0.35f),
                    radius = planetRadius * 1.15f
                ),
                radius = planetRadius,
                center = center
            )

            // 7. Planet Surface Continents & Digital Network Structures
            // Cyber latitudes & longitudes across the curved sphere
            val latLines = 5
            for (i in -latLines..latLines) {
                val fraction = i / (latLines.toFloat() + 0.5f)
                val yOff = fraction * planetRadius * 0.85f
                val horizontalChord = kotlin.math.sqrt((planetRadius * planetRadius - yOff * yOff).coerceAtLeast(0f))
                if (horizontalChord > 10f) {
                    drawOval(
                        color = CyanNeon.copy(alpha = 0.16f),
                        topLeft = Offset(center.x - horizontalChord, center.y + yOff - 6.dp.toPx()),
                        size = androidx.compose.ui.geometry.Size(horizontalChord * 2, 12.dp.toPx()),
                        style = Stroke(width = 1.dp.toPx())
                    )
                }
            }

            // City-Light Clusters / Data Points on Planet
            val cityCount = 20
            for (c in 0 until cityCount) {
                val cityAngle = (c * (360f / cityCount) + angleDegrees * 0.6f) * (Math.PI / 180f).toFloat()
                val radiusFraction = 0.3f + (c % 4) * 0.16f
                val dist = planetRadius * radiusFraction
                val cx = center.x + cos(cityAngle) * dist
                val cy = center.y + sin(cityAngle) * dist * 0.7f

                // Distance check so city lights don't overflow the sphere
                val distanceFromCenter = kotlin.math.hypot(cx - center.x, cy - center.y)
                if (distanceFromCenter < planetRadius * 0.92f) {
                    val clusterAlpha = ((c % 3) + 3) / 6f
                    drawCircle(
                        color = if (c % 2 == 0) CyanNeon.copy(alpha = clusterAlpha) else Color(0xFFFFD54F).copy(alpha = clusterAlpha),
                        radius = if (c % 5 == 0) 2.4.dp.toPx() else 1.4.dp.toPx(),
                        center = Offset(cx, cy)
                    )
                }
            }

            // 8. Planet Atmospheric Rim Glow & Electric Halo
            drawCircle(
                brush = Brush.sweepGradient(
                    colors = listOf(
                        CyanNeon.copy(alpha = 0.95f),
                        ElectricBlue.copy(alpha = 0.8f),
                        VioletNeon.copy(alpha = 0.85f),
                        Color(0xFF030712),
                        CyanNeon.copy(alpha = 0.95f)
                    ),
                    center = center
                ),
                radius = planetRadius,
                center = center,
                style = Stroke(width = 2.2.dp.toPx())
            )

            // Inner soft atmospheric rim reflection
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.Transparent,
                        CyanNeon.copy(alpha = 0.35f * pulseAlpha)
                    ),
                    center = center,
                    radius = planetRadius
                ),
                radius = planetRadius - 1.dp.toPx(),
                center = center,
                style = Stroke(width = 4.dp.toPx())
            )

            // 9. ENERGY PATHWAYS / DATA CONNECTIONS TO 6 CAPABILITY NODES
            // Node anchor positions (left column and right column coordinates)
            val leftX = 75.dp.toPx()
            val rightX = size.width - 75.dp.toPx()
            val y1 = size.height * 0.20f
            val y2 = size.height * 0.50f
            val y3 = size.height * 0.80f

            val nodeEndpoints = listOf(
                Pair(Offset(leftX, y1), CapabilityNode.THINK),
                Pair(Offset(leftX, y2), CapabilityNode.SEARCH),
                Pair(Offset(leftX, y3), CapabilityNode.ANALYZE),
                Pair(Offset(rightX, y1), CapabilityNode.EXECUTE),
                Pair(Offset(rightX, y2), CapabilityNode.AUTOMATE),
                Pair(Offset(rightX, y3), CapabilityNode.LEARN)
            )

            nodeEndpoints.forEach { (nodePos, capability) ->
                val isNodeActive = activeCapability == capability
                val streamColor = if (isNodeActive) CyanNeon else VioletNeon.copy(alpha = 0.45f)
                val strokeWidth = if (isNodeActive) 2.dp.toPx() else 1.dp.toPx()

                // Baseline energy pathway
                drawLine(
                    color = streamColor.copy(alpha = if (isNodeActive) 0.8f else 0.35f),
                    start = nodePos,
                    end = center,
                    strokeWidth = strokeWidth,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 10f), 0f)
                )

                // Moving Energy Pulse / Data Packet traveling towards core
                val currentPulseX = nodePos.x + (center.x - nodePos.x) * energyPulseProgress
                val currentPulseY = nodePos.y + (center.y - nodePos.y) * energyPulseProgress
                drawCircle(
                    color = if (isNodeActive) CyanNeon else MagentaNeon.copy(alpha = 0.9f),
                    radius = if (isNodeActive) 3.5.dp.toPx() else 2.2.dp.toPx(),
                    center = Offset(currentPulseX, currentPulseY)
                )
            }
        }

        // 6 FLOATING ORBITAL CAPABILITY NODES
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Column: THINK, SEARCH, ANALYZE
            Column(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalAlignment = Alignment.Start
            ) {
                OrbitalCapabilityNode(
                    name = "THINK",
                    icon = Icons.Default.Psychology,
                    isActive = activeCapability == CapabilityNode.THINK,
                    tag = "orbital_think",
                    onClick = { onNodeClicked(CapabilityNode.THINK) }
                )
                OrbitalCapabilityNode(
                    name = "SEARCH",
                    icon = Icons.Default.Search,
                    isActive = activeCapability == CapabilityNode.SEARCH,
                    tag = "orbital_search",
                    onClick = { onNodeClicked(CapabilityNode.SEARCH) }
                )
                OrbitalCapabilityNode(
                    name = "ANALYZE",
                    icon = Icons.Default.AutoAwesome,
                    isActive = activeCapability == CapabilityNode.ANALYZE,
                    tag = "orbital_analyze",
                    onClick = { onNodeClicked(CapabilityNode.ANALYZE) }
                )
            }

            // Right Column: EXECUTE, AUTOMATE, LEARN
            Column(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalAlignment = Alignment.End
            ) {
                OrbitalCapabilityNode(
                    name = "EXECUTE",
                    icon = Icons.Default.PlayArrow,
                    isActive = activeCapability == CapabilityNode.EXECUTE,
                    tag = "orbital_execute",
                    onClick = { onNodeClicked(CapabilityNode.EXECUTE) }
                )
                OrbitalCapabilityNode(
                    name = "AUTOMATE",
                    icon = Icons.Default.Build,
                    isActive = activeCapability == CapabilityNode.AUTOMATE,
                    tag = "orbital_automate",
                    onClick = { onNodeClicked(CapabilityNode.AUTOMATE) }
                )
                OrbitalCapabilityNode(
                    name = "LEARN",
                    icon = Icons.Default.Code,
                    isActive = activeCapability == CapabilityNode.LEARN,
                    tag = "orbital_learn",
                    onClick = { onNodeClicked(CapabilityNode.LEARN) }
                )
            }
        }

        // Status Badge centered directly under the digital planet
        val isAnyNodeActive = activeCapability != CapabilityNode.NONE
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 6.dp)
                .crystallineGlass(
                    shape = RoundedCornerShape(16.dp),
                    borderColor = CyanNeon.copy(alpha = 0.5f),
                    glowAccent = CyanNeon,
                    specularGleam = true,
                    isActive = isAnyNodeActive,
                    alphaSubstrate = 0.88f
                )
                .padding(horizontal = 14.dp, vertical = 5.dp)
        ) {
            Text(
                text = if (isAnyNodeActive)
                    "✦ MAX ${activeCapability.name} ACTIVE ✦"
                else
                    "✦ MAX CORE ACTIVE ✦",
                color = CyanNeon,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

/**
 * High-precision futuristic floating orbital capability chip with crystalline glassmorphism
 */
@Composable
fun OrbitalCapabilityNode(
    name: String,
    icon: ImageVector,
    isActive: Boolean,
    tag: String,
    onClick: () -> Unit
) {
    val iconColor = if (isActive) CyanNeon else TextSecondary

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .testTag(tag)
            .crystallineGlass(
                shape = RoundedCornerShape(10.dp),
                borderColor = if (isActive) CyanNeon else Color(0x3300E5FF),
                glowAccent = CyanNeon,
                specularGleam = true,
                innerShadow = true,
                innerShadowDepth = 3.5.dp,
                crystalFacets = true,
                noiseDensity = 1.35f,
                isActive = isActive,
                alphaSubstrate = if (isActive) 0.92f else 0.72f
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() }
            .padding(horizontal = 11.dp, vertical = 7.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = name,
            tint = iconColor,
            modifier = Modifier.size(15.dp)
        )
        Text(
            text = name,
            color = if (isActive) Color.White else TextPrimary,
            fontSize = 11.sp,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
            letterSpacing = 1.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}
