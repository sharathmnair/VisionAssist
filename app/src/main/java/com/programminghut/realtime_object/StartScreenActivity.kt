package com.programminghut.realtime_object

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import kotlin.math.abs
import androidx.core.net.toUri
import android.os.*
import androidx.preference.PreferenceManager
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.*


class StartScreenActivity : AppCompatActivity() {

    private inner class GestureListener : GestureDetector.SimpleOnGestureListener() {

        private val swipeThreshold = 100
        private val swipeVelocityThreshold = 100
        private var lastSwipeUpTime = 0L
        private val doubleSwipeTimeout = 1000 // 1 second

        override fun onFling(
            e1: MotionEvent?,
            e2: MotionEvent,
            velocityX: Float,
            velocityY: Float
        ): Boolean {
            if (e1 == null) return false
            val diffX = e2.x - e1.x
            val diffY = e2.y - e1.y

            return handleSwipe(diffX, diffY, velocityX, velocityY)
        }

        private fun handleSwipe(diffX: Float, diffY: Float, velocityX: Float, velocityY: Float): Boolean {
            return when {
                isHorizontalSwipe(diffX, diffY, velocityX) -> {
                    if (diffX > 0) openTextRecognitionScreen()  // Swipe Right
                    else openMainScreen()  // Swipe Left
                    true
                }
                isVerticalSwipe(diffY, velocityY) -> {
                    if (diffY < 0) handleSwipeUp()  // Swipe Up
                    else false
                }
                else -> false
            }
        }

        private fun isHorizontalSwipe(diffX: Float, diffY: Float, velocityX: Float): Boolean {
            return abs(diffX) > abs(diffY) && abs(diffX) > swipeThreshold && abs(velocityX) > swipeVelocityThreshold
        }

        private fun isVerticalSwipe(diffY: Float, velocityY: Float): Boolean {
            return abs(diffY) > swipeThreshold && abs(velocityY) > swipeVelocityThreshold
        }

        private fun handleSwipeUp(): Boolean {
            val currentTime = System.currentTimeMillis()
            return if (currentTime - lastSwipeUpTime <= doubleSwipeTimeout) {
                exitApp()  // Double Swipe Up detected
                true
            } else {
                lastSwipeUpTime = currentTime
                true  // Single Swipe Up detected
            }
        }
    }

    private lateinit var gestureDetector: GestureDetector
    private lateinit var textToSpeech: TextToSpeech
    private lateinit var vibrator: Vibrator

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_start_screen)

        gestureDetector = GestureDetector(this, GestureListener())

        val rootView: View = findViewById(android.R.id.content)
        rootView.setOnTouchListener { _, event -> gestureDetector.onTouchEvent(event) }

        val startDetectionButton: Button = findViewById(R.id.startDetectionButton)
        val aboutButton: Button = findViewById(R.id.settingsButton)
        val emergencyContactButton: Button = findViewById(R.id.emergencyContactButton)
        val exitButton: Button = findViewById(R.id.exitButton)

        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        // Set touch listener to buttons
        setButtonTouchListener(aboutButton, "Settings", ::openAboutScreen)
        setButtonTouchListener(emergencyContactButton, "Emergency", ::callEmergencyContact)
        setButtonTouchListener(exitButton, "Exit", ::exitApp)

        checkCallPermission()

        // Initialize Text-to-Speech
        textToSpeech = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech.language = Locale.US
                textToSpeech.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) {}
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {}
                })
                textToSpeech.speak("Welcome to Vision Assist", TextToSpeech.QUEUE_FLUSH, null, "WelcomeTTS")
            }
        }

        // Set up startDetectionButton to announce voice command instructions and trigger voice recognition
        startDetectionButton.setOnTouchListener(object : View.OnTouchListener {
            private val handler = Handler(Looper.getMainLooper())
            private var isLongPress = false

            @SuppressLint("ClickableViewAccessibility")
            override fun onTouch(view: View?, event: MotionEvent?): Boolean {
                when (event?.action) {
                    MotionEvent.ACTION_DOWN -> {
                        isLongPress = false
                        triggerVibration(50)
                        speak("long press for voice command")
                        handler.postDelayed({
                            isLongPress = true
                            triggerVibration(200)
                            voiceCommand()  // Start voice recognition when long-pressed
                        }, 800)  // Trigger voice recognition after 0.8 seconds
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        handler.removeCallbacksAndMessages(null)
                    }
                }
                return true
            }
        })
    }

    // Function to speak the given text
    private fun speak(text: String) {
        textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setButtonTouchListener(button: Button, buttonName: String, action: () -> Unit) {
        button.setOnTouchListener(object : View.OnTouchListener {
            private val handler = Handler(Looper.getMainLooper())
            private var isLongPress = false

            override fun onTouch(view: View?, event: MotionEvent?): Boolean {
                when (event?.action) {
                    MotionEvent.ACTION_DOWN -> {
                        isLongPress = false
                        triggerVibration(50)
                        handler.postDelayed({
                            isLongPress = true
                            triggerVibration(200)
                            action()
                        }, 800)
                        textToSpeech.speak(buttonName, TextToSpeech.QUEUE_FLUSH, null, null)
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        handler.removeCallbacksAndMessages(null)
                    }
                }
                return true
            }
        })
    }

    private fun triggerVibration(duration: Long) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val effect = VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE)
            vibrator.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(duration)
        }
    }
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
    }

    private fun voiceCommand() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Listening for your command...")

        try {
            startActivityForResult(intent, 200)  // Start the speech recognition activity
        } catch (e: Exception) {
            e.printStackTrace()
            textToSpeech.speak("Voice recognition is not supported on your device.", TextToSpeech.QUEUE_FLUSH, null, null)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == 200 && resultCode == RESULT_OK && data != null) {
            val spokenText = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.get(0) ?: ""

            when {
                spokenText.contains("object detection", ignoreCase = true) -> {
                    openMainScreen()  // Navigate to the main screen for object detection
                }
                spokenText.contains("read text", ignoreCase = true) -> {
                    openTextRecognitionScreen()  // Navigate to the text recognition screen
                }
                spokenText.contains("emergency", ignoreCase = true) -> {
                    callEmergencyContact()  // Trigger the emergency contact call
                }
                spokenText.contains("settings", ignoreCase = true) -> {
                    openAboutScreen()  // Open the Settings screen
                }
                spokenText.contains("exit", ignoreCase = true) -> {
                    exitApp()  // Exit the application
                }
                else -> {
                    textToSpeech.speak(
                        "Command not recognized. Please say 'Object detection', 'Read text', 'Emergency', 'Settings', or 'Exit'.",
                        TextToSpeech.QUEUE_FLUSH,
                        null,
                        null
                    )
                }
            }
        }
    }

    private fun callEmergencyContact() {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        val selectedNumber = sharedPreferences.getString("selectedEmergencyContact", null)

        if (selectedNumber != null && selectedNumber.matches(Regex("\\d{2,}"))) { // Valid phone number check
            val callIntent = Intent(Intent.ACTION_CALL)
            callIntent.data = "tel:$selectedNumber".toUri()

            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                startActivity(callIntent)
            } else {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CALL_PHONE), 1)
            }
        } else {
            Toast.makeText(this, "Please select a valid emergency contact from Settings", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkCallPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CALL_PHONE), 102)
        }
    }

    private fun openMainScreen() {
        startActivity(Intent(this, MainActivity::class.java))
    }

    private fun openTextRecognitionScreen() {
        startActivity(Intent(this, TextRecognitionActivity::class.java))
    }

    private fun openAboutScreen() {
        startActivity(Intent(this, SettingsActivity::class.java))
    }

    private fun exitApp() {
        finishAffinity()
    }

    override fun onDestroy() {
        super.onDestroy()
        textToSpeech.stop()
        textToSpeech.shutdown()
    }
}