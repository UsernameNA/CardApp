package com.example.cardapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.cardapp.ui.collection.CardListScreen
import com.example.cardapp.ui.theme.CardAppTheme
import com.example.cardapp.ui.theme.CreamMuted
import com.example.cardapp.ui.theme.GoldMuted
import com.example.cardapp.ui.theme.GoldPrimary
import com.example.cardapp.ui.theme.LeatherDeep
import com.example.cardapp.ui.theme.LeatherLight
import com.example.cardapp.ui.theme.LeatherMid
import com.example.cardapp.ui.theme.Typography

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CardAppTheme {
                val navController = rememberNavController()
                NavHost(navController = navController, startDestination = "landing") {
                    composable("landing") {
                        LandingScreen(
                            onViewCollection = { navController.navigate("collection") },
                            onScanCards = { /* TODO */ },
                        )
                    }
                    composable("collection") {
                        CardListScreen()
                    }
                }
            }
        }
    }
}

@Composable
fun LandingScreen(
    onViewCollection: () -> Unit = {},
    onScanCards: () -> Unit = {},
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.radialGradient(
                    colorStops = arrayOf(
                        0.0f to LeatherLight,
                        0.5f to LeatherMid,
                        1.0f to LeatherDeep,
                    ),
                    radius = 1600f,
                )
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 48.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "CardApp",
                style = Typography.displayLarge.copy(
                    color = GoldPrimary,
                    textAlign = TextAlign.Center,
                ),
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                HorizontalDivider(
                    modifier = Modifier.weight(1f),
                    thickness = 0.5.dp,
                    color = GoldMuted,
                )
                Text(
                    text = "  ✦  ",
                    style = Typography.bodyLarge.copy(color = GoldMuted),
                )
                HorizontalDivider(
                    modifier = Modifier.weight(1f),
                    thickness = 0.5.dp,
                    color = GoldMuted,
                )
            }

            Spacer(modifier = Modifier.height(52.dp))

            LandingButton(text = "View Collection", onClick = onViewCollection)

            Spacer(modifier = Modifier.height(16.dp))

            LandingButton(text = "Scan Cards", onClick = onScanCards)
        }
    }
}

@Composable
private fun LandingButton(text: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(3.dp),
        border = BorderStroke(0.8.dp, GoldMuted),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = LeatherMid.copy(alpha = 0.6f),
            contentColor = CreamMuted,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp),
    ) {
        Text(
            text = text,
            style = Typography.labelLarge.copy(color = CreamMuted),
        )
    }
}

@Preview(showBackground = true)
@Composable
fun LandingScreenPreview() {
    CardAppTheme {
        LandingScreen()
    }
}
