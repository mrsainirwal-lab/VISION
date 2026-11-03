package com.vision.assistant

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var recognizer: SpeechRecognizer? = null

    private var state by mutableStateOf(VisionState.IDLE)
    private var heard by mutableStateOf("")
    private var reply by mutableStateOf("")

    private val micPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startListening()
        else speak("I need microphone permission to hear you.")
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) { state = VisionState.LISTENING }
        override fun onResults(results: Bundle?) {
            val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
            if (text.isNullOrBlank()) {
                speak("I didn't catch that.")
            } else {
                handle(text)
            }
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

        tts = TextToSpeech(this, this)
        recognizer = if (SpeechRecognizer.isRecognitionAvailable(this)) {
            SpeechRecognizer.createSpeechRecognizer(this).apply {
                setRecognitionListener(listener)
            }
        } else {
            null
        }

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
                    VisionScreen(
                        state = state,
                        heard = heard,
                        reply = reply,
                        onMic = { startListening() },
                        onSendText = { handle(it) }
                    )
                }
            }
        }
    }

    private fun startListening() {
        if (recognizer == null) {
            speak("Speech recognition isn't available on this device.")
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
        lifecycleScope.launch {
            val result = CommandProcessor.handle(text)
            result.openUrl?.let { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it))) }
            speak(result.reply)
        }
    }

    private fun speak(text: String) {
        reply = text
        val status = tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "vision-${System.nanoTime()}")
        if (status != TextToSpeech.SUCCESS) state = VisionState.IDLE
    }

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) return
        tts?.language = Locale.US
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                runOnUiThread { state = VisionState.SPEAKING }
            }
            override fun onDone(utteranceId: String?) {
                runOnUiThread { state = VisionState.IDLE }
            }
            override fun onError(utteranceId: String?) {
                runOnUiThread { state = VisionState.IDLE }
            }
        })
    }

    override fun onDestroy() {
        tts?.shutdown()
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
    onSendText: (String) -> Unit
) {
    var typed by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Text("VISION", color = Color.White, fontSize = 22.sp)

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
