package com.github.username.cardapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.github.username.cardapp.ui.collection.CardListScreen
import com.github.username.cardapp.ui.landing.LandingScreen
import com.github.username.cardapp.ui.theme.CardAppTheme

private object Routes {
    const val LANDING = "landing"
    const val COLLECTION = "collection"
}

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
                            onViewCollection = { navController.navigate(Routes.COLLECTION) },
                            onScanCards = { /* TODO */ },
                        )
                    }
                    composable(Routes.COLLECTION) {
                        CardListScreen()
                    }
                }
            }
        }
    }
}
