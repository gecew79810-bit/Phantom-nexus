package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.ai.PendingAction
import com.example.ai.RiskLevel
import com.example.ui.theme.AmberNeon
import com.example.ui.theme.CrimsonNeon
import com.example.ui.theme.CyanNeon
import com.example.ui.theme.DeepSpaceBlack
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VioletNeon

@Composable
fun ConfirmationDialog(
    pendingAction: PendingAction,
    onDismiss: () -> Unit
) {
    val accentColor = when (pendingAction.riskLevel) {
        RiskLevel.CRITICAL -> CrimsonNeon
        RiskLevel.HIGH -> AmberNeon
        RiskLevel.MEDIUM -> CyanNeon
        RiskLevel.LOW -> VioletNeon
    }

    Dialog(onDismissRequest = {
        pendingAction.onCancel()
        onDismiss()
    }) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xF5080E1B))
                .border(1.5.dp, accentColor, RoundedCornerShape(16.dp))
                .padding(20.dp)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (pendingAction.riskLevel == RiskLevel.HIGH) Icons.Default.Warning else Icons.Default.Security,
                        contentDescription = "Risk Check",
                        tint = accentColor,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "MAX SECURITY VERIFICATION",
                        color = accentColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Text(
                    text = pendingAction.title,
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0x33000000))
                        .border(1.dp, GlassBorder, RoundedCornerShape(8.dp))
                        .padding(12.dp)
                ) {
                    Text(
                        text = pendingAction.description,
                        color = TextSecondary,
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            pendingAction.onCancel()
                            onDismiss()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("dialog_cancel_button"),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("रद्द करें / Cancel")
                    }

                    Button(
                        onClick = {
                            pendingAction.onConfirm()
                            onDismiss()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("dialog_confirm_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = accentColor,
                            contentColor = DeepSpaceBlack
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("पुष्टि करें / Execute", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
