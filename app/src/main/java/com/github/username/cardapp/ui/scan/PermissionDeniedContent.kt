package com.github.username.cardapp.ui.scan

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.github.username.cardapp.ui.theme.CreamFaded
import com.github.username.cardapp.ui.theme.GoldDark
import com.github.username.cardapp.ui.theme.GoldLight
import com.github.username.cardapp.ui.theme.GoldMuted
import com.github.username.cardapp.ui.theme.GoldPrimary
import com.github.username.cardapp.ui.theme.Typography

@Composable
internal fun PermissionDeniedContent(
    onRequestAgain: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "CAMERA ACCESS REQUIRED",
            style = Typography.labelLarge.copy(color = GoldPrimary),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Grant camera permission to scan cards",
            style = Typography.bodyMedium.copy(
                color = CreamFaded,
                textAlign = TextAlign.Center,
            ),
        )
        Spacer(Modifier.height(24.dp))
        val permInteraction = remember { MutableInteractionSource() }
        val permPressed by permInteraction.collectIsPressedAsState()
        Box(
            modifier = Modifier
                .border(
                    1.dp,
                    if (permPressed) GoldLight else GoldMuted,
                    RoundedCornerShape(2.dp),
                )
                .background(if (permPressed) GoldDark.copy(alpha = 0.35f) else Color.Transparent)
                .clickable(
                    interactionSource = permInteraction,
                    indication = null,
                    onClick = onRequestAgain,
                )
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            Text(
                text = "GRANT PERMISSION",
                style = Typography.labelMedium.copy(
                    color = if (permPressed) GoldLight else GoldPrimary,
                ),
            )
        }
    }
}
