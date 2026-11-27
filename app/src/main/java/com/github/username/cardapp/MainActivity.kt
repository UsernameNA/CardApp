package com.github.username.cardapp

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.dropUnlessResumed
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

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        // The app is always dark, so system bar icons must stay light regardless of the device theme
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
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
                            onViewCards = dropUnlessResumed { navController.navigate(Cards) { launchSingleTop = true } },
                            onViewCollection = dropUnlessResumed { navController.navigate(Collection) { launchSingleTop = true } },
                            onScanCards = dropUnlessResumed { navController.navigate(Scan) { launchSingleTop = true } },
                            onTrackGame = dropUnlessResumed { navController.navigate(TrackGame) { launchSingleTop = true } },
                        )
                    }
                    composable<Cards> { entry ->
                        CardsScreen(
                            onCardClick = { cardName ->
                                entry.ifResumed { navController.navigate(CardDetail(cardName)) { launchSingleTop = true } }
                            },
                        )
                    }
                    composable<CardDetail> {
                        CardDetailScreen(onBack = dropUnlessResumed { navController.popBackStack() })
                    }
                    composable<Collection> { entry ->
                        CollectionScreen(
                            onCardClick = { cardName ->
                                entry.ifResumed { navController.navigate(CardDetail(cardName)) { launchSingleTop = true } }
                            },
                        )
                    }
                    composable<Scan> { entry ->
                        ScanScreen(
                            onBack = dropUnlessResumed { navController.popBackStack() },
                            onCardClick = { cardName ->
                                entry.ifResumed { navController.navigate(CardDetail(cardName)) { launchSingleTop = true } }
                            },
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

// dropUnlessResumed for callbacks that take arguments: a second tap during a
// navigation transition must not stack another destination
private inline fun LifecycleOwner.ifResumed(action: () -> Unit) {
    if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) action()
}
