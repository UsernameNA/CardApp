package com.github.username.cardapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.github.username.cardapp.ui.cards.CardsScreen
import com.github.username.cardapp.ui.collection.CollectionScreen
import com.github.username.cardapp.ui.detail.CardDetailScreen
import com.github.username.cardapp.ui.landing.LandingScreen
import com.github.username.cardapp.ui.scan.ScanScreen
import com.github.username.cardapp.ui.trackgame.TrackGameScreen
import com.github.username.cardapp.ui.theme.CardAppTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.serialization.Serializable

@Serializable object Landing
@Serializable object Cards
@Serializable object Collection
@Serializable object Scan
@Serializable data class CardDetail(val cardName: String)
@Serializable object TrackGame

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        var isReady = false
        splashScreen.setKeepOnScreenCondition { !isReady }
        splashScreen.setOnExitAnimationListener { it.remove() }
        setContent {
            LaunchedEffect(Unit) {
                withFrameNanos { }
                withFrameNanos { }
                isReady = true
            }
            CardAppTheme {
                val navController = rememberNavController()
                NavHost(navController = navController, startDestination = Landing) {
                    composable<Landing> {
                        LandingScreen(
                            onViewCards = { navController.navigate(Cards) },
                            onViewCollection = { navController.navigate(Collection) },
                            onScanCards = { navController.navigate(Scan) },
                            onTrackGame = { navController.navigate(TrackGame) },
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
                        CollectionScreen(
                            onCardClick = { cardName -> navController.navigate(CardDetail(cardName)) },
                        )
                    }
                    composable<Scan> {
                        ScanScreen(
                            onBack = { navController.popBackStack() },
                            onCardClick = { cardName -> navController.navigate(CardDetail(cardName)) },
                        )
                    }
                    composable<TrackGame> {
                        TrackGameScreen()
                    }
                }
            }
        }
    }
}
