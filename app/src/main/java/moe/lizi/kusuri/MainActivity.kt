package moe.lizi.kusuri

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import moe.lizi.kusuri.ui.KusuriApp
import moe.lizi.kusuri.ui.theme.KusuriTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as KusuriApplication).container
        container.startReminderSync()
        setContent {
            KusuriTheme {
                KusuriApp()
            }
        }
    }
}
