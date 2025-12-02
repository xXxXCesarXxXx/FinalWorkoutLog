package com.example.wearabledatasync

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.wearabledatasync.ui.theme.WearableDataSyncTheme
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.text.Charsets

// --- CONSTANTES DE COMUNICACIÓN ---
// Deben coincidir exactamente con las del módulo Wear OS
const val WORKOUT_PATH = "/workout-entry"
const val WORKOUT_VALIDATED_PATH = "/workout-validated"

// --- MODELO DE DATOS LOCAL ---
// Estructura para guardar los datos recibidos en la lista del teléfono
data class WorkoutEntry(
    val exercise: String,
    val sets: Int,
    val reps: Int,
    val timestamp: Long = System.currentTimeMillis()
)


// 1
class MainActivity : ComponentActivity(), MessageClient.OnMessageReceivedListener {

    private val tag = "PhoneActivity"

    // Estado de la UI: Lista de ejercicios recibidos
    private val workoutLog = mutableStateOf<List<WorkoutEntry>>(emptyList())

    // Cliente de mensajería de Google Play Services
    private lateinit var messageClient: MessageClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(tag, "onCreate: Iniciando actividad del teléfono")

        // 1. Inicializar el cliente para escuchar mensajes
        messageClient = Wearable.getMessageClient(this)

        setContent {
            WearableDataSyncTheme {
                // UI Principal
                WorkoutLogger(
                    log = workoutLog.value,
                    onClearClick = { workoutLog.value = emptyList() },
                    onDeleteEntry = { entry -> workoutLog.value = workoutLog.value - entry }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Activar el "oído" del teléfono cuando la app está abierta
        messageClient.addListener(this)
    }

    override fun onPause() {
        super.onPause()
        // Desactivar para ahorrar batería cuando la app se minimiza
        messageClient.removeListener(this)
    }

    // --- LÓGICA DE RECEPCIÓN (FEATURE 1 - Parte Receptora) ---
    override fun onMessageReceived(messageEvent: MessageEvent) {
        Log.d(tag, "Mensaje recibido desde: ${messageEvent.path}")

        // Verificamos si el mensaje viene del path correcto de entrenamiento
        if (messageEvent.path == WORKOUT_PATH) {
            // 1. Convertir los bytes crudos a String legible
            val message = String(messageEvent.data, Charsets.UTF_8)
            Log.d(tag, "Payload recibido: $message")

            // 2. DESERIALIZACIÓN: Convertir el String "Push-ups;3;15|Squats..." a Objetos
            val newEntries = message.split("|").mapNotNull { entryString ->
                val parts = entryString.split(";")
                if (parts.size == 3) {
                    WorkoutEntry(
                        exercise = parts[0],
                        sets = parts[1].toIntOrNull() ?: 0,
                        reps = parts[2].toIntOrNull() ?: 0
                    )
                } else {
                    null // Ignorar datos corruptos
                }
            }

            // 3. Actualizar la UI (Siempre dentro de runOnUiThread)
            if (newEntries.isNotEmpty()) {
                runOnUiThread {
                    workoutLog.value = workoutLog.value + newEntries
                }
                // 4. Confirmar recepción al reloj (Cerrar el ciclo)
                sendValidation(messageEvent.sourceNodeId)
            }
        }
    }

    // --- LÓGICA DE RESPUESTA (FEATURE 2 - Confirmación) ---
    private fun sendValidation(targetNodeId: String) {
        // Enviamos un mensaje vacío solo para avisar que "Llegó bien"
        messageClient.sendMessage(targetNodeId, WORKOUT_VALIDATED_PATH, null)
            .addOnSuccessListener {
                Log.d(tag, "Validación ENVIADA al reloj.")
            }
            .addOnFailureListener { e ->
                Log.e(tag, "Error enviando validación: ${e.message}")
            }
    }
}

// --- COMPONENTES DE UI (Jetpack Compose) ---

@Composable
fun WorkoutEntryCard(entry: WorkoutEntry, onDeleteClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.exercise,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row {
                    Text(text = "Sets: ${entry.sets}", style = MaterialTheme.typography.bodyLarge)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(text = "Reps: ${entry.reps}", style = MaterialTheme.typography.bodyLarge)
                }
                Spacer(modifier = Modifier.height(8.dp))
                // Formato de fecha legible
                val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                Text(
                    text = sdf.format(Date(entry.timestamp)),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            IconButton(onClick = onDeleteClick) {
                Icon(Icons.Default.Delete, contentDescription = "Eliminar ejercicio")
            }
        }
    }
}

@Composable
fun WorkoutLogger(
    log: List<WorkoutEntry>,
    onClearClick: () -> Unit,
    onDeleteEntry: (WorkoutEntry) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Workout Log",
            fontSize = 48.sp,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(vertical = 24.dp)
        )

        Button(onClick = onClearClick, modifier = Modifier.padding(bottom = 16.dp)) {
            Text("Borrar historial")
        }

        // Lista deslizable de ejercicios recibidos
        LazyColumn(modifier = Modifier.weight(1f)) {
            // reversed() para mostrar los más nuevos primero
            items(log.reversed()) { entry ->
                WorkoutEntryCard(entry = entry) {
                    onDeleteEntry(entry)
                }
            }
        }

        // Pie de página con el nombre del estudiante
        Text(
            text = "Cesar Enrique Bernal Zurita",
            style = MaterialTheme.typography.bodyLarge,
            fontSize = 20.sp,
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    WearableDataSyncTheme {
        WorkoutLogger(
            log = listOf(
                WorkoutEntry("Push-ups", 3, 15),
                WorkoutEntry("Squats", 4, 20)
            ),
            onClearClick = {},
            onDeleteEntry = {}
        )
    }
}
