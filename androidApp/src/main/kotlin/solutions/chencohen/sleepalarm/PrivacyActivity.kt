package solutions.chencohen.sleepalarm

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Health Connect refuses to show the permission dialog unless the app declares a screen that
 * explains how it uses the data (see the manifest's rationale intent filters).
 */
class PrivacyActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.padding(24.dp).verticalScroll(rememberScrollState())) {
                        Text("How Sleep Alarm uses your health data", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            "\nSleep Alarm reads heart rate, steps and sleep sessions from Health Connect " +
                                "to work out when you fell asleep and wake you after your chosen amount of sleep.\n\n" +
                                "Background access lets it check every 15 minutes overnight while the screen is off.\n\n" +
                                "Everything stays on this phone. The app sends nothing over the network, writes nothing " +
                                "back to Health Connect, and only shares a file when you tap Export.",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        }
    }
}
