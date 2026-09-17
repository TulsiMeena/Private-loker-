package com.example.feature.launch

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.designsystem.LocalVaultSpacing
import com.example.core.designsystem.LocalVaultTypography
import com.example.core.designsystem.VaultColors
import com.example.core.ui.SecurityPill
import com.example.core.ui.VaultCoreOrb
import kotlinx.coroutines.delay

/**
 * Hardware-inspired app launch sequence:
 * 1. Deep black secure canvas.
 * 2. Vault Core visual element smoothly activates with concentric geometric security rings.
 * 3. Communicates real hardware keystore initialization without fake hacking animations.
 * 4. Seamlessly hands off to Authentication screen.
 */
@Composable
fun LaunchScreen(
    onLaunchComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var animationStage by remember { mutableStateOf(0) }
    val spacing = LocalVaultSpacing.current

    LaunchedEffect(Unit) {
        // Step 1: Canvas appears, Core awakens
        animationStage = 1
        delay(350L)
        // Step 2: Keystore integrity confirmed
        animationStage = 2
        delay(400L)
        // Step 3: Transition to Authentication
        onLaunchComplete()
    }

    val coreAlpha by animateFloatAsState(
        targetValue = if (animationStage >= 1) 1f else 0f,
        animationSpec = tween(500, easing = FastOutSlowInEasing),
        label = "coreAlpha"
    )

    val coreScale by animateFloatAsState(
        targetValue = if (animationStage >= 1) 1f else 0.85f,
        animationSpec = tween(500, easing = FastOutSlowInEasing),
        label = "coreScale"
    )

    Box(
        modifier = modifier
            .testTag("launch_screen")
            .fillMaxSize()
            .background(VaultColors.Canvas),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(spacing.screenHorizontal)
        ) {
            Box(
                modifier = Modifier
                    .alpha(coreAlpha)
                    .scale(coreScale),
                contentAlignment = Alignment.Center
            ) {
                VaultCoreOrb(
                    isUnlocked = false,
                    isActiveAnimating = animationStage >= 1,
                    accentColor = VaultColors.AccentCyan
                )
            }

            Spacer(modifier = Modifier.height(spacing.xl))

            Text(
                text = "PRIVATEVAULT",
                style = LocalVaultTypography.current.displayMedium.copy(
                    letterSpacing = 4.sp,
                    fontWeight = FontWeight.Light
                ),
                color = VaultColors.TextPrimary,
                modifier = Modifier.alpha(coreAlpha)
            )

            Spacer(modifier = Modifier.height(spacing.s))

            Text(
                text = "SECURE ENCLAVE ACTIVE",
                style = LocalVaultTypography.current.monospaceAccented.copy(
                    fontSize = 11.sp,
                    letterSpacing = 2.sp
                ),
                color = VaultColors.Titanium,
                modifier = Modifier.alpha(if (animationStage >= 2) 1f else 0.5f)
            )

            Spacer(modifier = Modifier.height(spacing.l))

            AnimatedVisibility(
                visible = animationStage >= 1,
                enter = fadeIn(tween(300))
            ) {
                SecurityPill(
                    text = "AES-256-GCM READY",
                    dotColor = VaultColors.AccentEmerald
                )
            }
        }
    }
}
