package com.example.wearabledatasync

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.wearabledatasync.ui.theme.WearableDataSyncTheme
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import kotlin.text.Charsets

class MainActivity : ComponentActivity(), MessageClient.OnMessageReceivedListener {

    private val tag = "PhoneActivity"
    private val count = mutableIntStateOf(100)
    private lateinit var messageClient: MessageClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(tag, "onCreate: Activity starting")
        messageClient = Wearable.getMessageClient(this)
        Log.d(tag, "onCreate: MessageClient initialized")

        setContent {
            WearableDataSyncTheme {
                WearApp(count.intValue)
            }
        }
        Log.d(tag, "onCreate: Content set")
    }

    override fun onResume() {
        super.onResume()
        Log.d(tag, "onResume: Adding message listener")
        messageClient.addListener(this)
    }

    override fun onPause() {
        super.onPause()
        Log.d(tag, "onPause: Removing message listener")
        messageClient.removeListener(this)
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        Log.d(tag, "onMessageReceived: ${'$'}{messageEvent.path}")
        if (messageEvent.path == MESSAGE_PATH_RESET) {
            Log.d(tag, "Watch sent RESET signal. Node ID: ${'$'}{messageEvent.sourceNodeId}")
            runOnUiThread {
                count.intValue = 0
            }
            sendConfirmation(messageEvent.sourceNodeId)
        }
    }

    private fun sendConfirmation(targetNodeId: String) {
        val message = "RESET_OK".toByteArray(Charsets.UTF_8)
        messageClient.sendMessage(targetNodeId, MESSAGE_PATH_CONFIRM, message)
            .addOnSuccessListener {
                Log.d(tag, "Confirmation message SENT back to watch.")
            }
            .addOnFailureListener { e ->
                Log.e(tag, "Failed to send confirmation: ${'$'}{e.message}")
            }
    }
}

@Composable
fun WearApp(count: Int) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = "PHONE APPLICATION", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Current Count: $count",
                fontSize = 48.sp,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = "Awaiting Watch RESET signal...",
                style = MaterialTheme.typography.bodyMedium,
                fontSize = 20.sp,
            )
        }
        Text(
            text = "Cesar Enrique Bernal Zurita",
            style = MaterialTheme.typography.bodyLarge,
            fontSize = 20.sp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
        )
    }
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    WearableDataSyncTheme {
        WearApp(100)
    }
}
