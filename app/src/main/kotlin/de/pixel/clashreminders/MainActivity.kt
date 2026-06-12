package de.pixel.clashreminders

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import de.pixel.clashreminders.ui.AppNavGraph
import de.pixel.clashreminders.ui.theme.ClashRemindersTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as ClashRemindersApp
        setContent {
            ClashRemindersTheme {
                AppNavGraph(app = app)
            }
        }
    }
}
