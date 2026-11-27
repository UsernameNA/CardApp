package com.github.username.cardapp.ui.scan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.github.username.cardapp.ui.theme.CreamFaded
import com.github.username.cardapp.ui.theme.GoldPrimary
import com.github.username.cardapp.ui.theme.Typography

/**
 * Shown in place of the camera without camera permission. Once the user has denied it
 * for good the system won't ask again, so [permanentlyDenied] swaps the request for a
 * route to the app's settings page.
 */
@Composable
internal fun PermissionDeniedContent(
    permanentlyDenied: Boolean,
    onRequestAgain: () -> Unit,
    onOpenSettings: () -> Unit,
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
            text = if (permanentlyDenied) {
                "Camera permission is turned off. Allow it in Settings to scan cards"
            } else {
                "Grant camera permission to scan cards"
            },
            style = Typography.bodyMedium.copy(
                color = CreamFaded,
                textAlign = TextAlign.Center,
            ),
        )
        Spacer(Modifier.height(24.dp))
        ScanPanelButton(
            text = if (permanentlyDenied) "OPEN SETTINGS" else "GRANT PERMISSION",
            onClick = if (permanentlyDenied) onOpenSettings else onRequestAgain,
            textStyle = Typography.labelMedium,
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
        )
    }
}
