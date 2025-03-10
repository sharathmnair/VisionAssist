package com.programminghut.realtime_object

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.*

class StartScreenActivity : AppCompatActivity() {

    private lateinit var textToSpeech: TextToSpeech
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_start_screen)

        val startDetectionButton: Button = findViewById(R.id.startDetectionButton)
        val aboutButton: Button = findViewById(R.id.aboutButton)
        val emergencyContactButton: Button = findViewById(R.id.emergencyContactButton)
        val exitButton: Button = findViewById(R.id.exitButton)

        // Set touch listeners for buttons
        setButtonTouchListener(startDetectionButton, "Vision Assist", ::startObjectDetection)
        setButtonTouchListener(aboutButton, "About", ::openAboutScreen)
        setButtonTouchListener(emergencyContactButton, "Emergency Contacts", ::openEmergencyContacts)
        setButtonTouchListener(exitButton, "Exit", ::exitApp)

        // Request microphone permission
        checkMicrophonePermission()

        // Initialize TTS
        textToSpeech = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech.language = Locale.US
                textToSpeech.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        // TTS has started speaking, do nothing
                    }

                    override fun onDone(utteranceId: String?) {
                        // TTS finished speaking, start speech recognition
                        runOnUiThread { initializeSpeechRecognizer() }
                    }

                    override fun onError(utteranceId: String?) {
                        // Handle TTS error if needed
                    }
                })
                textToSpeech.speak("Welcome to Vision Assist", TextToSpeech.QUEUE_FLUSH, null, "WelcomeTTS")
            }
        }

        // Initialize Speech Recognition
        initializeSpeechRecognizer()
    }

    private fun setButtonTouchListener(button: Button, buttonName: String, action: () -> Unit) {
        button.setOnTouchListener(object : View.OnTouchListener {
            private val handler = Handler(Looper.getMainLooper())
            private var isLongPress = false

            override fun onTouch(view: View?, event: MotionEvent?): Boolean {
                when (event?.action) {
                    MotionEvent.ACTION_DOWN -> {
                        isLongPress = false
                        handler.postDelayed({
                            isLongPress = true
                            action() // Trigger action on long press
                        }, 800) // Long press duration: 800ms

                        textToSpeech.speak(buttonName, TextToSpeech.QUEUE_FLUSH, null, null) // Read out button name
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        handler.removeCallbacksAndMessages(null)
                    }
                }
                return true
            }
        })
    }

    private fun startObjectDetection() {
        stopSpeechRecognition()
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
    }

    private fun openAboutScreen() {
        val intent = Intent(this, AboutActivity::class.java)
        startActivity(intent)
    }

    private fun openEmergencyContacts() {
        val intent = Intent(this, EmergencyContactsActivity::class.java)
        startActivity(intent)
    }

    private fun exitApp() {
        finishAffinity()
    }

    private fun initializeSpeechRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            textToSpeech.speak("Speech recognition is not available on this device", TextToSpeech.QUEUE_FLUSH, null, "NoSpeechRecognizer")
            return
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        val speechIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.US)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2000)
        }

        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                isListening = false
                restartSpeechRecognition()
            }

            override fun onError(error: Int) {
                isListening = false
                restartSpeechRecognition()
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (matches != null) {
                    for (result in matches) {
                        if (result.equals("start scanning", ignoreCase = true)) {
                            startObjectDetection()
                            return
                        }
                    }
                }
                restartSpeechRecognition()
            }

            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        restartSpeechRecognition()
    }

    private fun restartSpeechRecognition() {
        if (!isListening) {
            isListening = true
            speechRecognizer?.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH))
        }
    }

    private fun stopSpeechRecognition() {
        isListening = false
        speechRecognizer?.stopListening()
        speechRecognizer?.destroy()
        speechRecognizer = null
    }

    private fun checkMicrophonePermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 101)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopSpeechRecognition()
        textToSpeech.stop()
        textToSpeech.shutdown()
    }
}