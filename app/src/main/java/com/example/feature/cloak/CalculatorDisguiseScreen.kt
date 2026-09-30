package com.example.feature.cloak

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.designsystem.VaultColors
import com.example.core.security.SessionSecurityManager
import java.text.DecimalFormat

/**
 * High-grade camouflage mode: disguises PrivateVault as a fully operational, realistic calculator.
 * Entering the secret PIN followed by '=' unlocks the vault securely.
 */
@Composable
fun CalculatorDisguiseScreen(
    sessionManager: SessionSecurityManager,
    onUnlocked: () -> Unit,
    onSwitchToPinPad: () -> Unit,
    modifier: Modifier = Modifier
) {
    var displayExpression by remember { mutableStateOf("0") }
    var previousExpression by remember { mutableStateOf("") }
    var showHelpDialog by remember { mutableStateOf(false) }

    fun onDigit(d: String) {
        if (displayExpression == "0" || displayExpression == "Error") {
            displayExpression = d
        } else {
            displayExpression += d
        }
    }

    fun onOperator(op: String) {
        if (displayExpression != "Error" && displayExpression.isNotEmpty()) {
            val lastChar = displayExpression.last()
            if (lastChar in "+-×÷") {
                displayExpression = displayExpression.dropLast(1) + op
            } else {
                displayExpression += op
            }
        }
    }

    fun onClear() {
        displayExpression = "0"
        previousExpression = ""
    }

    fun onBackspace() {
        if (displayExpression != "Error" && displayExpression.length > 1) {
            displayExpression = displayExpression.dropLast(1)
        } else {
            displayExpression = "0"
        }
    }

    fun evaluateMath() {
        // First check secret passkey! If the entire expression is digits and authenticates:
        val cleanCandidate = displayExpression.replace(" ", "")
        if (cleanCandidate.all { it.isDigit() } && cleanCandidate.length in 4..8) {
            val success = sessionManager.authenticatePin(cleanCandidate)
            if (success) {
                onUnlocked()
                return
            }
        }

        // If not unlocked, execute actual arithmetic calculation:
        try {
            val result = calculateSimpleExpression(displayExpression)
            previousExpression = "$displayExpression ="
            displayExpression = result
        } catch (_: Exception) {
            displayExpression = "Error"
        }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = Color(0xFF0F172A)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Bar with Subtle camouflage indicators
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { onSwitchToPinPad() }
                ) {
                    Text(
                        text = "RAD",
                        color = Color.White.copy(alpha = 0.4f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier
                            .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                IconButton(
                    onClick = { showHelpDialog = true },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Calculator Info",
                        tint = Color.White.copy(alpha = 0.35f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Calculation Display area
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = previousExpression,
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 20.sp,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = displayExpression,
                    color = Color.White,
                    fontSize = if (displayExpression.length > 9) 36.sp else 52.sp,
                    fontWeight = FontWeight.Light,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.End,
                    maxLines = 2,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("calc_display")
                )
            }

            // Keypad Grid
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Row 1: AC, (), %, ÷
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    CalcButton("C", Color(0xFF334155), VaultColors.AccentRed, Modifier.weight(1f)) { onClear() }
                    CalcButton("⌫", Color(0xFF334155), Color.White, Modifier.weight(1f)) { onBackspace() }
                    CalcButton("%", Color(0xFF334155), VaultColors.AccentCyan, Modifier.weight(1f)) { onOperator("%") }
                    CalcButton("÷", Color(0xFF1E293B), VaultColors.AccentCyan, Modifier.weight(1f)) { onOperator("÷") }
                }

                // Row 2: 7, 8, 9, ×
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    CalcButton("7", Color(0xFF1E293B), Color.White, Modifier.weight(1f)) { onDigit("7") }
                    CalcButton("8", Color(0xFF1E293B), Color.White, Modifier.weight(1f)) { onDigit("8") }
                    CalcButton("9", Color(0xFF1E293B), Color.White, Modifier.weight(1f)) { onDigit("9") }
                    CalcButton("×", Color(0xFF1E293B), VaultColors.AccentCyan, Modifier.weight(1f)) { onOperator("×") }
                }

                // Row 3: 4, 5, 6, -
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    CalcButton("4", Color(0xFF1E293B), Color.White, Modifier.weight(1f)) { onDigit("4") }
                    CalcButton("5", Color(0xFF1E293B), Color.White, Modifier.weight(1f)) { onDigit("5") }
                    CalcButton("6", Color(0xFF1E293B), Color.White, Modifier.weight(1f)) { onDigit("6") }
                    CalcButton("-", Color(0xFF1E293B), VaultColors.AccentCyan, Modifier.weight(1f)) { onOperator("-") }
                }

                // Row 4: 1, 2, 3, +
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    CalcButton("1", Color(0xFF1E293B), Color.White, Modifier.weight(1f)) { onDigit("1") }
                    CalcButton("2", Color(0xFF1E293B), Color.White, Modifier.weight(1f)) { onDigit("2") }
                    CalcButton("3", Color(0xFF1E293B), Color.White, Modifier.weight(1f)) { onDigit("3") }
                    CalcButton("+", Color(0xFF1E293B), VaultColors.AccentCyan, Modifier.weight(1f)) { onOperator("+") }
                }

                // Row 5: 0, ., =
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    CalcButton("0", Color(0xFF1E293B), Color.White, Modifier.weight(2f)) { onDigit("0") }
                    CalcButton(".", Color(0xFF1E293B), Color.White, Modifier.weight(1f)) { onDigit(".") }
                    CalcButton("=", VaultColors.AccentCyan, Color(0xFF0F172A), Modifier.weight(1f), isBold = true) {
                        evaluateMath()
                    }
                }
            }
        }
    }

    if (showHelpDialog) {
        AlertDialog(
            onDismissRequest = { showHelpDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = VaultColors.AccentCyan)
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("Calculator Camouflage")
                }
            },
            text = {
                Text(
                    "This app is disguised as a functioning calculator.\n\n" +
                    "• To unlock the Vault: Type your Master PIN or Decoy PIN and press '='.\n" +
                    "• Normal math operations (+, -, ×, ÷) calculate real results.\n" +
                    "• Tap 'RAD' in the top-left corner to access the standard PIN pad directly."
                )
            },
            confirmButton = {
                TextButton(onClick = { showHelpDialog = false }) {
                    Text("Got It", color = VaultColors.AccentCyan)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showHelpDialog = false
                    onSwitchToPinPad()
                }) {
                    Text("Open PIN Pad", color = Color.White.copy(alpha = 0.7f))
                }
            },
            containerColor = Color(0xFF1E293B)
        )
    }
}

@Composable
private fun CalcButton(
    label: String,
    backgroundColor: Color,
    textColor: Color,
    modifier: Modifier = Modifier,
    isBold: Boolean = false,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(68.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .testTag("calc_btn_$label"),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 24.sp,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Medium,
            fontFamily = FontFamily.Monospace
        )
    }
}

private fun calculateSimpleExpression(expr: String): String {
    val clean = expr.trim()
    val ops = listOf("+", "-", "×", "÷", "%")
    var operator: String? = null
    var opIndex = -1

    for (i in 1 until clean.length) {
        val ch = clean[i].toString()
        if (ch in ops) {
            operator = ch
            opIndex = i
            break
        }
    }

    if (operator == null || opIndex == -1) {
        return clean
    }

    val leftStr = clean.substring(0, opIndex).trim()
    val rightStr = clean.substring(opIndex + 1).trim()

    val left = leftStr.toDoubleOrNull() ?: return "Error"
    val right = rightStr.toDoubleOrNull() ?: return "Error"

    val res = when (operator) {
        "+" -> left + right
        "-" -> left - right
        "×" -> left * right
        "÷" -> if (right == 0.0) return "Error" else left / right
        "%" -> (left * right) / 100.0
        else -> return "Error"
    }

    val df = DecimalFormat("#.########")
    return df.format(res)
}
