package solutions.chencohen.sleepalarm

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import solutions.chencohen.sleepalarm.data.EventLog
import solutions.chencohen.sleepalarm.data.Session
import solutions.chencohen.sleepalarm.data.SessionStore
import solutions.chencohen.sleepalarm.ui.AlarmScreen
import solutions.chencohen.sleepalarm.ui.ProbeScreen
import solutions.chencohen.sleepalarm.ui.SetupCard
import solutions.chencohen.sleepalarm.ui.SetupState
import solutions.chencohen.sleepalarm.ui.loadSetup

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme { AppRoot() }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppRoot() {
    val ctx = LocalContext.current
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var refresh by remember { mutableIntStateOf(0) }
    var setup by remember { mutableStateOf<SetupState?>(null) }
    var session by remember { mutableStateOf<Session?>(null) }
    var events by remember { mutableStateOf(emptyList<String>()) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh++ }

    LaunchedEffect(refresh) {
        setup = withContext(Dispatchers.IO) { loadSetup(ctx) }
    }
    // The worker and receivers update the session in the background; keep the screen in step.
    LaunchedEffect(refresh) {
        val store = SessionStore(ctx)
        val log = EventLog(ctx)
        while (true) {
            session = withContext(Dispatchers.IO) { store.load() }
            events = withContext(Dispatchers.IO) { log.recentEvents() }
            delay(3_000)
        }
    }

    Scaffold { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text("Sleep Alarm", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(vertical = 8.dp))
            SetupCard(setup) { refresh++ }
            PrimaryTabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Alarm") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Test sync") })
            }
            when (tab) {
                0 -> AlarmScreen(setup, session) { refresh++ }
                else -> ProbeScreen(setup, session, events) { refresh++ }
            }
        }
    }
}
