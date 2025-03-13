package com.programminghut.realtime_object

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.*
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.*


class StartScreenActivity : AppCompatActivity() {

    private inner class GestureListener : GestureDetector.SimpleOnGestureListener() {

        private val SWIPE_THRESHOLD = 100
        private val SWIPE_VELOCITY_THRESHOLD = 100
        private var lastSwipeUpTime = 0L
        private val DOUBLE_SWIPE_TIMEOUT = 1000 // 1 second

        override fun onFling(
            e1: MotionEvent?,
            e2: MotionEvent,
            velocityX: Float,
            velocityY: Float
        ): Boolean {
            if (e1 == null || e2 == null) return false
            val diffX = e2.x - e1.x
            val diffY = e2.y - e1.y

            return when {
                Math.abs(diffX) > Math.abs(diffY) -> {  // Horizontal Swipe
                    if (Math.abs(diffX) > SWIPE_THRESHOLD && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                        if (diffX > 0) openTextRecognitionScreen()  // Swipe Right
                        else openMainScreen()  // Swipe Left
                        true
                    } else false
                }
                Math.abs(diffY) > SWIPE_THRESHOLD && Math.abs(velocityY) > SWIPE_VELOCITY_THRESHOLD -> { // Vertical Swipe
                    if (diffY < 0) { // Swipe Up
                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastSwipeUpTime <= DOUBLE_SWIPE_TIMEOUT) exitApp()
                        lastSwipeUpTime = currentTime
                        true
                    } else false
                }
                else -> false
            }
        }
    }

    private lateinit var gestureDetector: GestureDetector
    private lateinit var textToSpeech: TextToSpeech
    private lateinit var vibrator: Vibrator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_start_screen)

        gestureDetector = GestureDetector(this, GestureListener())

        val rootView: View = findViewById(android.R.id.content)
        rootView.setOnTouchListener { _, event -> gestureDetector.onTouchEvent(event) }

        val startDetectionButton: Button = findViewById(R.id.startDetectionButton)
        val aboutButton: Button = findViewById(R.id.aboutButton)
        val emergencyContactButton: Button = findViewById(R.id.emergencyContactButton)
        val exitButton: Button = findViewById(R.id.exitButton)

        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        // Set touch listener to buttons
        setButtonTouchListener(aboutButton, "About", ::openAboutScreen)
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
                    override fun onError(utteranceId: String?) {}
                })
                textToSpeech.speak("Welcome to Vision Assist", TextToSpeech.QUEUE_FLUSH, null, "WelcomeTTS")
            }
        }

        // Set up startDetectionButton to announce voice command instructions
        // Set up startDetectionButton to announce voice command instructions and trigger voice recognition
        startDetectionButton.setOnTouchListener(object : View.OnTouchListener {
            private val handler = Handler(Looper.getMainLooper())
            private var isLongPress = false

            override fun onTouch(view: View?, event: MotionEvent?): Boolean {
                when (event?.action) {
                    MotionEvent.ACTION_DOWN -> {
                        isLongPress = false
                        triggerVibration(50)
                        speak("Say 'Object Detection' or 'Text Recognition' to proceed")
                        handler.postDelayed({
                            isLongPress = true
                            triggerVibration(200)
                            startObjectDetection()  // Start voice recognition when long-pressed
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

    private fun startObjectDetection() {
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

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == 200 && resultCode == RESULT_OK && data != null) {
            val spokenText = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.get(0) ?: ""

            // Check if the spoken text matches "object detection"
            when {
                spokenText.contains("object detection", ignoreCase = true) -> {
                    openMainScreen()  // Navigate to the main screen for object detection
                }
                spokenText.contains("text recognition", ignoreCase = true) -> {
                    openTextRecognitionScreen()  // Navigate to the text recognition screen
                }
                else -> {
                    textToSpeech.speak("Command not recognized. Please say 'Object detection' or 'Text recognition'.", TextToSpeech.QUEUE_FLUSH, null, null)
                }
            }
        }
    }

    private fun callEmergencyContact() {
        val emergencyNumber = "8156944286"
        val callIntent = Intent(Intent.ACTION_CALL).apply {
            data = Uri.parse("tel:$emergencyNumber")
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
            startActivity(callIntent)
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CALL_PHONE), 102)
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
        startActivity(Intent(this, AboutActivity::class.java))
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