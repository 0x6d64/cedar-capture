package org.example.voicecapture

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Placeholder screen (UNTESTED skeleton, never built). STEP-06: request RECORD_AUDIO (and the notification permission)
 * and only start the service once granted. The UI only observes the service; it owns nothing (ADR-0003).
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text("Voice Capture - skeleton")
                    Button(onClick = {
                        // TODO(STEP-06): permission flow first. TODO(STEP-07): start only while this screen is visible.
                        startForegroundService(Intent(this@MainActivity, CaptureService::class.java))
                    }) { Text("Start recording") }
                    Button(onClick = { stopService(Intent(this@MainActivity, CaptureService::class.java)) }) {
                        Text("Stop")
                    }
                }
            }
        }
    }
}
