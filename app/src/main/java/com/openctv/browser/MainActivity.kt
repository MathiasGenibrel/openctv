package com.openctv.browser

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.openctv.browser.core.designsystem.OpenCtvTheme
import com.openctv.browser.core.designsystem.OverscanSafeArea
import com.openctv.browser.core.navigation.Destination
import com.openctv.browser.features.browser.ui.BrowserScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            OpenCtvTheme {
                // Destination hissée ici : l'accueil ouvre un site, le navigateur revient à l'accueil.
                var destination: Destination by rememberSaveable(stateSaver = Destination.Saver) {
                    mutableStateOf(Destination.Home)
                }
                when (val current = destination) {
                    Destination.Home -> OverscanSafeArea {
                        StartScreen(onOpenUrl = { destination = Destination.Browser(it) })
                    }
                    is Destination.Browser -> BrowserScreen(
                        url = current.url,
                        onClose = { destination = Destination.Home },
                    )
                }
            }
        }
    }
}
