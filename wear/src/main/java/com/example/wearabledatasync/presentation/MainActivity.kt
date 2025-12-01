/* While this template provides a good starting point for using Wear Compose, you can always
 * take a look at https://github.com/android/wear-os-samples/tree/main/ComposeStarter to find the
 * most up to date changes to the libraries and their usages.
 */

package com.example.wearabledatasync.presentation

import android.R
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import androidx.wear.compose.material.*
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlin.text.Charsets

class MainActivity : ComponentActivity(), MessageClient.OnMessageReceivedListener {

    private val tag = "WatchActivity"
    private val statusMessage = mutableStateOf("Tap to send RESET signal")
    private lateinit var messageClient: MessageClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(tag, "onCreate: Activity starting")
        messageClient = Wearable.getMessageClient(this)
        Log.d(tag, "onCreate: MessageClient initialized")

        setContent {
            WearApp(statusMessage) { sendResetMessage() }
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

    private fun sendResetMessage() {
        lifecycleScope.launch {
            statusMessage.value = "Searching for phone..."
            try {
                val nodes = Wearable.getNodeClient(this@MainActivity).connectedNodes.await()
                if (nodes.isEmpty()) {
                    statusMessage.value = "Error: No connected phone found!"
                    return@launch
                }
                nodes.forEach { node ->
                    messageClient.sendMessage(node.id, MESSAGE_PATH_RESET, null)
                        .addOnSuccessListener {
                            Log.d(tag, "RESET signal SENT to phone: ${'$'}{node.displayName}")
                            statusMessage.value = "RESET signal SENT. Waiting for confirmation..."
                        }
                        .addOnFailureListener { e ->
                            Log.e(tag, "Failed to send RESET to ${'$'}{node.displayName}: ${'$'}{e.message}")
                            statusMessage.value = "Send Failed: ${'$'}{e.message}"
                        }
                }
            } catch (e: Exception) {
                Log.e(tag, "Error getting nodes: ${'$'}{e.message}")
                statusMessage.value = "Connection Error"
            }
        }
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        if (messageEvent.path == MESSAGE_PATH_CONFIRM) {
            val receivedMsg = String(messageEvent.data, Charsets.UTF_8)
            runOnUiThread {
                Log.d(tag, "Received confirmation: $receivedMsg")
                statusMessage.value = "SUCCESS: Phone Confirmed Reset ($receivedMsg)!"
            }
        }
    }
}

@Composable
fun WearApp(statusMessage: State<String>, onResetClicked: () -> Unit) {
    MaterialTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colors.background)
                .padding(horizontal = 10.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.align(Alignment.Center)
            ) {
                Text(
                    text = "WATCH APPLICATION",
                    style = MaterialTheme.typography.caption1,
                    modifier = Modifier.padding(bottom = 14.dp)
                )
                Button(onClick = onResetClicked) {
                    Text("RESET")
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = statusMessage.value,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.caption2,
                    color = MaterialTheme.colors.primaryVariant
                )
            }
            Text(
                text = "Cesar Enrique Bernal Zurita",
                style = MaterialTheme.typography.body2,
                fontSize = 8.sp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 20.dp),

            )
        }
    }
}