package io.github.halilozel1903.cropper.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Pinches and handle drags can't be performed reliably through adb, so `scripts/screenshots.sh`
 * starts the app with `--es scene <scene>` to set up each screenshot:
 *
 * - `crop`: a free crop with the grid over the landscape, framing part of it (same as no extra)
 * - `circle`: the circle avatar crop over the portrait
 * - `ratios`: the landscape with the 16:9 chip selected
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val scene = intent.getStringExtra(EXTRA_SCENE)
        setContent {
            val colors = if (isSystemInDarkTheme()) {
                darkColorScheme(
                    primary = Color(0xFFFFB38A),
                    onPrimary = Color(0xFF4A1C00),
                    secondaryContainer = Color(0xFF5A3A2A),
                    onSecondaryContainer = Color(0xFFFFDBC9),
                    background = Color(0xFF121014),
                    surface = Color(0xFF121014),
                )
            } else {
                lightColorScheme(
                    primary = Color(0xFFB4460F),
                    onPrimary = Color.White,
                    secondaryContainer = Color(0xFFFFDBC9),
                    onSecondaryContainer = Color(0xFF3A1606),
                    background = Color(0xFFFBF7F4),
                    surface = Color(0xFFFBF7F4),
                )
            }
            MaterialTheme(colorScheme = colors) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    SampleApp(scene)
                }
            }
        }
    }

    companion object {
        const val EXTRA_SCENE = "scene"
    }
}
