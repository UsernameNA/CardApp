package com.github.username.cardapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.github.username.cardapp.ui.cards.CardsScreen
import com.github.username.cardapp.ui.collection.CollectionScreen
import com.github.username.cardapp.ui.detail.CardDetailScreen
import com.github.username.cardapp.ui.landing.LandingScreen
import com.github.username.cardapp.ui.scan.ScanScreen
import com.github.username.cardapp.ui.theme.CardAppTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.serialization.Serializable

@Serializable object Landing
@Serializable object Cards
@Serializable object Collection
@Serializable object Scan
@Serializable data class CardDetail(val cardName: String)

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CardAppTheme {
                val navController = rememberNavController()
                NavHost(navController = navController, startDestination = Landing) {
                    composable<Landing> {
                        LandingScreen(
                            onViewCards = { navController.navigate(Cards) },
                            onViewCollection = { navController.navigate(Collection) },
                            onScanCards = { navController.navigate(Scan) },
                        )
                    }
                    composable<Cards> {
                        CardsScreen(
                            onCardClick = { cardName -> navController.navigate(CardDetail(cardName)) },
                        )
                    }
                    composable<CardDetail> {
                        CardDetailScreen(onBack = { navController.popBackStack() })
                    }
                    composable<Collection> {
                        CollectionScreen()
                    }
                    composable<Scan> {
                        ScanScreen(onBack = { navController.popBackStack() })
                    }
                }
            }
        }
    }
}
