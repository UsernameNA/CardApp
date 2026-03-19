package com.github.username.cardapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.github.username.cardapp.ui.cards.CardsScreen
import com.github.username.cardapp.ui.collection.CollectionScreen
import com.github.username.cardapp.ui.landing.LandingScreen
import com.github.username.cardapp.ui.scan.ScanScreen
import com.github.username.cardapp.ui.theme.CardAppTheme

private object Routes {
    const val LANDING = "landing"
    const val CARDS = "cards"
    const val COLLECTION = "collection"
    const val SCAN = "scan"
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CardAppTheme {
                val navController = rememberNavController()
                NavHost(navController = navController, startDestination = Routes.LANDING) {
                    composable(Routes.LANDING) {
                        LandingScreen(
                            onViewCards = { navController.navigate(Routes.CARDS) },
                            onViewCollection = { navController.navigate(Routes.COLLECTION) },
                            onScanCards = { navController.navigate(Routes.SCAN) },
                        )
                    }
                    composable(Routes.CARDS) {
                        CardsScreen()
                    }
                    composable(Routes.COLLECTION) {
                        CollectionScreen()
                    }
                    composable(Routes.SCAN) {
                        ScanScreen(onBack = { navController.popBackStack() })
                    }
                }
            }
        }
    }
}
