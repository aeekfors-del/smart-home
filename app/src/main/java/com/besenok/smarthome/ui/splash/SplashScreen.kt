package com.besenok.smarthome.ui.splash

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.besenok.smarthome.R
import kotlinx.coroutines.delay

private val CyanLaser = Color(0xFF00E5FF)

@Composable
fun SplashScreen(
    onSplashComplete: () -> Unit
) {
    var showLogo by remember { mutableStateOf(false) }
    var showText by remember { mutableStateOf(false) }
    
    val duration = 3500L
    
    LaunchedEffect(Unit) {
        delay(500)
        showLogo = true
        delay(800)
        showText = true
        delay(duration - 1300)
        onSplashComplete()
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0E14)),
        contentAlignment = Alignment.Center
    ) {
        // Laser lines animation
        LaserLines()
        
        // Center content
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.alpha(if (showLogo || showText) 1f else 0f)
        ) {
            // Logo with glow - loads PNG only after animation starts
            if (showLogo) {
                LogoWithGlow()
                Spacer(modifier = Modifier.height(24.dp))
            }
            
            // Text formation
            if (showText) {
                AnimatedText()
            }
        }
    }
}

@Composable
private fun LaserLines() {
    val sweep = remember { Animatable(0f) }
    
    LaunchedEffect(Unit) {
        sweep.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = 2500,
                easing = FastOutSlowInEasing
            )
        )
    }
    
    Canvas(modifier = Modifier.fillMaxSize()) {
        val centerX: Float = size.width / 2f
        val centerY: Float = size.height / 2f
        val maxRadius: Float = size.width * 0.8f
        
        // Multiple laser beams sweeping
        for (i in 0 until 8) {
            val angle = (sweep.value * 360f + i * 45f) * Math.PI / 180f
            val alpha = 0.3f * (1f - sweep.value)
            
            val startX: Float = centerX
            val startY: Float = centerY
            val endX: Float = (centerX + Math.cos(angle.toDouble()) * maxRadius.toDouble()).toFloat()
            val endY: Float = (centerY + Math.sin(angle.toDouble()) * maxRadius.toDouble()).toFloat()
            
            drawLine(
                color = CyanLaser.copy(alpha = alpha),
                start = androidx.compose.ui.geometry.Offset(startX, startY),
                end = androidx.compose.ui.geometry.Offset(endX, endY),
                strokeWidth = 2f
            )
        }
        
        // Horizontal laser beams
        for (i in 0 until 3) {
            val y = centerY - maxRadius + (maxRadius * 2 * sweep.value * (i + 1) / 3)
            val alpha = 0.2f * (1f - sweep.value * 0.5f)
            
            drawLine(
                color = CyanLaser.copy(alpha = alpha),
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1.5f
            )
        }
        
        // Vertical laser beams
        for (i in 0 until 3) {
            val x = centerX - maxRadius + (maxRadius * 2 * sweep.value * (i + 1) / 3)
            val alpha = 0.2f * (1f - sweep.value * 0.5f)
            
            drawLine(
                color = CyanLaser.copy(alpha = alpha),
                start = Offset(x, 0f),
                end = Offset(x, size.height),
                strokeWidth = 1.5f
            )
        }
    }
}

@Composable
private fun LogoWithGlow() {
    val glowRadius = remember { Animatable(0f) }
    
    LaunchedEffect(Unit) {
        glowRadius.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = 1000,
                easing = EaseOut
            )
        )
    }
    
    val glowAlpha = glowRadius.value
    
    Box(
        modifier = Modifier
            .size(160.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(
                Color(0xFF0D1B2A),
                RoundedCornerShape(24.dp)
            )
            .alpha(0.5f + 0.5f * glowAlpha),
        contentAlignment = Alignment.Center
    ) {
        // Glow effect
        if (glowAlpha > 0) {
            Canvas(
                modifier = Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(24.dp))
            ) {
                drawRect(
                    color = CyanLaser.copy(alpha = 0.3f * glowAlpha),
                    size = size,
                    style = Stroke(width = 6f)
                )
            }
        }
        
        // PNG logo - loaded only after animation starts (showLogo = true)
        Image(
            painter = painterResource(id = R.drawable.logo_splash),
            contentDescription = "Smart Home Monitor Logo",
            modifier = Modifier.size(160.dp)
        )
    }
}

@Composable
private fun AnimatedText() {
    val letters = "Smart Home System Monitor".toList()
    val visibleCount = remember { mutableIntStateOf(0) }
    
    LaunchedEffect(Unit) {
        for (i in letters.indices) {
            delay(50)
            visibleCount.intValue = i + 1
        }
    }
    
    Text(
        text = letters.take(visibleCount.intValue).joinToString(""),
        fontSize = 28.sp,
        fontWeight = FontWeight.Bold,
        color = Color.White,
        letterSpacing = 2.sp
    )
}
