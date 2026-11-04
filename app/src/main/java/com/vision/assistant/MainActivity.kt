package com.vision.assistant

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : ComponentActivity() {

    private lateinit var actions: PhoneActions
    private lateinit var brain: Brain
    private lateinit var voice: Voice
    private var recognizer: SpeechRecognizer? = null

    private var state by mutableStateOf(VisionState.IDLE)
    private var heard by mutableStateOf("")
    private var reply by mutableStateOf("")
    private var showSettings by mutableStateOf(false)

    private val micPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startListening() else say("I need microphone permission to hear you.")
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) { state = VisionState.LISTENING }
        override fun onResults(results: Bundle?) {
            val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
            if (text.isNullOrBlank()) say("I didn't catch that.") else handle(text)
        }
        override fun onError(error: Int) { state = VisionState.IDLE }
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onPartialResults(partialResults: Bundle?) {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        actions = PhoneActions(this)
        brain = Brain(this, actions)
        voice = Voice(this)
        voice.onSpeakStart = { runOnUiThread { state = VisionState.SPEAKING } }
        voice.onSpeakDone = { runOnUiThread { state = VisionState.IDLE } }

        recognizer = if (SpeechRecognizer.isRecognitionAvailable(this)) {
            SpeechRecognizer.createSpeechRecognizer(this).apply { setRecognitionListener(listener) }
        } else {
            null
        }

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
                    if (showSettings) {
                        SettingsScreen(onBack = { showSettings = false })
                    } else {
                        VisionScreen(
                            state = state,
                            heard = heard,
                            reply = reply,
                            onMic = { startListening() },
                            onSendText = { handle(it) },
                            onSettings = { showSettings = true }
                        )
                    }
                }
            }
        }
    }

    private fun startListening() {
        if (recognizer == null) {
            say("Speech recognition isn't available on this device.")
            return
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        }
        state = VisionState.LISTENING
        recognizer?.startListening(intent)
    }

    private fun handle(text: String) {
        heard = text
        reply = "..."
        lifecycleScope.launch {
            val answer = brain.process(text)
            say(answer)
        }
    }

    private fun say(text: String) {
        reply = text
        lifecycleScope.launch { voice.speak(text) }
    }

    override fun onDestroy() {
        voice.shutdown()
        recognizer?.destroy()
        super.onDestroy()
    }
}

@Composable
fun VisionScreen(
    state: VisionState,
    heard: String,
    reply: String,
    onMic: () -> Unit,
    onSendText: (String) -> Unit,
    onSettings: () -> Unit
) {
    var typed by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("VISION", color = Color.White, fontSize = 22.sp)
            TextButton(onClick = onSettings) { Text("Settings") }
        }

        HologramOrb(state = state, modifier = Modifier.size(260.dp))

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (heard.isNotEmpty()) {
                Text("You: $heard", color = Color.White.copy(alpha = 0.7f), textAlign = TextAlign.Center)
            }
            if (reply.isNotEmpty()) {
                Text("Vision: $reply", color = Color.White, textAlign = TextAlign.Center)
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            OutlinedTextField(
                value = typed,
                onValueChange = { typed = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Type a command") }
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onMic) { Text("🎤 Speak") }
                Button(onClick = {
                    if (typed.isNotBlank()) {
                        onSendText(typed)
                        typed = ""
                    }
                }) { Text("Send") }
            }
        }
    }
}

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current

    var groq by remember { mutableStateOf(Keys.get(ctx, Keys.GROQ)) }
    var gemini by remember { mutableStateOf(Keys.get(ctx, Keys.GEMINI)) }
    var eleven1 by remember { mutableStateOf(Keys.get(ctx, Keys.ELEVEN_1)) }
    var eleven2 by remember { mutableStateOf(Keys.get(ctx, Keys.ELEVEN_2)) }
    var voiceId by remember { mutableStateOf(Keys.get(ctx, Keys.VOICE_ID)) }

    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Settings", color = Color.White, fontSize = 22.sp)

        SecretField("Groq API key", groq) { groq = it }
        SecretField("Gemini API key", gemini) { gemini = it }
        SecretField("ElevenLabs key (slot 1)", eleven1) { eleven1 = it }
        SecretField("ElevenLabs key (slot 2)", eleven2) { eleven2 = it }

        OutlinedTextField(
            value = voiceId,
            onValueChange = { voiceId = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("ElevenLabs voice ID") }
        )

        Button(
            onClick = {
                Keys.set(ctx, Keys.GROQ, groq)
                Keys.set(ctx, Keys.GEMINI, gemini)
                Keys.set(ctx, Keys.ELEVEN_1, eleven1)
                Keys.set(ctx, Keys.ELEVEN_2, eleven2)
                Keys.set(ctx, Keys.VOICE_ID, voiceId)
                onBack()
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Save") }

        OutlinedButton(
            onClick = {
                ctx.startActivity(
                    Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Enable phone control (Accessibility)") }

        OutlinedButton(
            onClick = {
                ctx.startActivity(
                    Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${ctx.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Allow brightness control") }

        TextButton(onClick = onBack) { Text("Back") }
    }
}

@Composable
fun SecretField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text(label) },
        visualTransformation = PasswordVisualTransformation()
    )
}
