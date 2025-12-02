package com.example.wearabledatasync.presentation

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.wear.compose.material.*
import androidx.wear.compose.foundation.lazy.items // Importante para listas
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlin.text.Charsets

// --- CONSTANTES Y MODELO DE DATOS ---
// Nota: Para cumplir con los entregables del proyecto, idealmente mover esto a un archivo 'WearableData.kt'
const val WORKOUT_PATH = "/workout-entry" // Ruta para enviar datos al teléfono
const val WORKOUT_VALIDATED_PATH = "/workout-validated" // Ruta para recibir confirmación

// Modelo de datos simple. Se serializará manualmente a String para el envío.
data class Exercise(val name: String, val sets: Int, val reps: Int)

// Estados de la UI para manejar el flujo de la aplicación
sealed class UiState {
    data class Selecting(val selectedExercises: List<Exercise> = emptyList()) : UiState()
    object Sending : UiState()
    object Finished : UiState()
}

class MainActivity : ComponentActivity(), MessageClient.OnMessageReceivedListener {

    private val tag = "WatchActivity"
    private lateinit var messageClient: MessageClient
    // Estado mutable para recomponer la UI automáticamente
    private val uiState = mutableStateOf<UiState>(UiState.Selecting())

    // Datos hardcodeados para la demostración
    private val allExercises = listOf(
        Exercise("Push-ups", 3, 15),
        Exercise("Squats", 4, 20),
        Exercise("Plank", 3, 60),
        Exercise("Bicep Curls", 3, 12),
        Exercise("Tricep Dips", 3, 10)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Inicialización del Cliente de Mensajería de Wear OS
        messageClient = Wearable.getMessageClient(this)
        Log.d(tag, "MessageClient inicializado")

        setContent {
            // Diseño visual con fondo BLANCO según tus requisitos
            Box(modifier = Modifier.fillMaxSize().background(Color.White)) {
                val currentState = uiState.value
                when (currentState) {
                    is UiState.Selecting -> {
                        WorkoutList(
                            allExercises = allExercises,
                            selectedExercises = currentState.selectedExercises,
                            onExerciseToggle = { exercise ->
                                // Lógica para añadir/quitar ejercicios de la selección
                                val currentSelected = (uiState.value as? UiState.Selecting)?.selectedExercises ?: emptyList()
                                val newSelected = if (exercise in currentSelected) {
                                    currentSelected - exercise
                                } else {
                                    currentSelected + exercise
                                }
                                uiState.value = UiState.Selecting(newSelected)
                            },
                            onFinishClick = {
                                val selected = (uiState.value as? UiState.Selecting)?.selectedExercises ?: emptyList()
                                if (selected.isNotEmpty()) {
                                    uiState.value = UiState.Sending
                                    // Iniciar el proceso de comunicación
                                    sendAllWorkouts(selected)
                                }
                            }
                        )
                    }
                    is UiState.Sending -> SendingScreen()
                    is UiState.Finished -> {
                        FinishedScreen {
                            uiState.value = UiState.Selecting() // Reiniciar flujo
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Registrar el listener para escuchar mensajes del teléfono cuando la app está activa
        messageClient.addListener(this)
    }

    override fun onPause() {
        super.onPause()
        // Importante: Eliminar listener para ahorrar batería
        messageClient.removeListener(this)
    }

    // --- LÓGICA DE COMUNICACIÓN (MessageClient) ---

    // FEATURE 1: Enviar datos al teléfono
    private fun sendAllWorkouts(exercises: List<Exercise>) {
        lifecycleScope.launch {
            try {
                // Buscamos nodos conectados (el teléfono)
                // .await() suspende la corrutina hasta tener respuesta sin bloquear el hilo principal
                val nodes = Wearable.getNodeClient(this@MainActivity).connectedNodes.await()

                if (nodes.isEmpty()) {
                    Log.e(tag, "No hay nodos conectados")
                    uiState.value = UiState.Selecting(exercises) // Volver atrás si falla
                    return@launch
                }

                // SERIALIZACIÓN MANUAL: Convertimos la lista de objetos a un String separada por "|"
                // Ejemplo: "Push-ups;3;15|Squats;4;20"
                val message = exercises.joinToString("|") { "${it.name};${it.sets};${it.reps}" }
                val data = message.toByteArray(Charsets.UTF_8) // Payload en bytes

                // Enviamos el mensaje a todos los nodos conectados (usualmente solo el teléfono)
                nodes.forEach { node ->
                    messageClient.sendMessage(node.id, WORKOUT_PATH, data)
                        .addOnSuccessListener {
                            Log.d(tag, "Datos enviados a: ${node.displayName}")
                        }
                        .addOnFailureListener { e ->
                            Log.e(tag, "Fallo al enviar a ${node.displayName}: ${e.message}")
                            // Volvemos al hilo principal para actualizar UI
                            runOnUiThread { uiState.value = UiState.Selecting(exercises) }
                        }
                }
            } catch (e: Exception) {
                Log.e(tag, "Excepción en envío: ${e.message}")
                uiState.value = UiState.Selecting(exercises)
            }
        }
    }

    // FEATURE 2: Recibir confirmación del teléfono
    override fun onMessageReceived(messageEvent: MessageEvent) {
        // Filtramos por la ruta específica (Path)
        if (messageEvent.path == WORKOUT_VALIDATED_PATH) {
            Log.d(tag, "Validación recibida del teléfono")

            // Actualizamos la UI al estado final.
            // runOnUiThread asegura que no crashee si viene de un hilo de fondo.
            runOnUiThread {
                uiState.value = UiState.Finished
            }
        }
    }
}

// --- COMPONENTES VISUALES (Jetpack Compose) ---

@Composable
fun SendingScreen() {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(indicatorColor = MaterialTheme.colors.primary)
        Spacer(Modifier.height(16.dp))
        Text("Enviando...", textAlign = TextAlign.Center, color = Color.Black)
    }
}

@Composable
fun FinishedScreen(onDoneClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "¡Entrenamiento Guardado!",
            style = MaterialTheme.typography.title2,
            color = Color(0xFF006400), // Verde oscuro para contraste en fondo blanco
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onDoneClick) {
            Text("Listo")
        }
    }
}

@Composable
fun ExerciseCard(exercise: Exercise, isSelected: Boolean, onExerciseClicked: (Exercise) -> Unit) {
    // Usamos Card pero manipulamos los colores para que se vea bien en fondo blanco
    Card(
        onClick = { onExerciseClicked(exercise) },
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        backgroundPainter = if (isSelected) {
            // Si está seleccionado: Azul claro (primary variant)
            CardDefaults.cardBackgroundPainter(startBackgroundColor = Color(0xFFE3F2FD), endBackgroundColor = Color(0xFFBBDEFB))
        } else {
            // Si no: Gris muy claro para diferenciarse del fondo blanco
            CardDefaults.cardBackgroundPainter(startBackgroundColor = Color(0xFFF5F5F5), endBackgroundColor = Color(0xFFEEEEEE))
        }
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = exercise.name,
                style = MaterialTheme.typography.title3,
                color = Color.Black // Texto negro
            )
            Text(
                text = "${exercise.sets} sets x ${exercise.reps} reps",
                style = MaterialTheme.typography.body2,
                color = Color.DarkGray // Texto gris oscuro
            )
        }
    }
}

@Composable
fun WorkoutList(
    allExercises: List<Exercise>,
    selectedExercises: List<Exercise>,
    onExerciseToggle: (Exercise) -> Unit,
    onFinishClick: () -> Unit
) {
    val listState = rememberScalingLazyListState()
    Scaffold(
        // Color negro para la hora porque el fondo es blanco
        timeText = { TimeText(timeTextStyle = TextStyle(color = Color.Black)) },
        vignette = null, // Sin viñeta en fondo blanco
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) }
    ) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            autoCentering = null, // Desactiva centrado automático
            contentPadding = PaddingValues(top = 30.dp, start = 16.dp, end = 16.dp, bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            state = listState
        ) {
            item {
                Text(
                    text = "Elige Ejercicios",
                    style = MaterialTheme.typography.title2,
                    textAlign = TextAlign.Center,
                    color = Color.Black, // Texto negro sobre fondo blanco
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }
            items(allExercises) { exercise ->
                ExerciseCard(
                    exercise = exercise,
                    isSelected = exercise in selectedExercises,
                    onExerciseClicked = onExerciseToggle
                )
            }
            if (selectedExercises.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = onFinishClick, modifier = Modifier.fillMaxWidth()) {
                        Text("Terminar")
                    }
                }
            }
        }
    }
}