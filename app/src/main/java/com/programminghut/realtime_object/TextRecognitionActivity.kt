package com.programminghut.realtime_object

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.Surface
import android.view.TextureView
import android.widget.Button
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.*
import java.util.concurrent.Executors

class TextRecognitionActivity : AppCompatActivity() {

    private lateinit var textureView: TextureView
    private lateinit var cameraDevice: CameraDevice
    private lateinit var handler: Handler
    private lateinit var handlerThread: HandlerThread
    private lateinit var cameraManager: CameraManager
    private lateinit var textToSpeech: TextToSpeech

    private var isSpeaking = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_text_recognition) // Updated layout file

        val visionAssistButton: Button = findViewById(R.id.visionAssistButton)
        visionAssistButton.setOnClickListener {
            // Optionally handle tap to switch back to object detection or other actions
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
        }
        textureView = findViewById(R.id.textureView)
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager

        handlerThread = HandlerThread("TextRecognitionThread")
        handlerThread.start()
        handler = Handler(handlerThread.looper)

        // Initialize Text-to-Speech
        textToSpeech = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech.language = Locale.US
                textToSpeech.setSpeechRate(0.8f)
                textToSpeech.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        isSpeaking = true
                    }

                    override fun onDone(utteranceId: String?) {
                        isSpeaking = false
                    }

                    override fun onError(utteranceId: String?) {
                        isSpeaking = false
                    }
                })
            }
        }

        // Check Camera Permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            setupTextureViewListener()
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 101)
        }
    }

    private fun setupTextureViewListener() {
        textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
                openCamera()
            }

            override fun onSurfaceTextureSizeChanged(surfaceTexture: SurfaceTexture, width: Int, height: Int) {}
            override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean = false

            override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) {
                if (isSpeaking) return
                val bitmap = textureView.bitmap ?: return
                processImageForTextRecognition(bitmap)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun openCamera() {
        val cameraId = cameraManager.cameraIdList[0]
        cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
            override fun onOpened(camera: CameraDevice) {
                cameraDevice = camera
                val surfaceTexture = textureView.surfaceTexture
                val surface = Surface(surfaceTexture)

                val captureRequest = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                captureRequest.addTarget(surface)

                val outputConfiguration = OutputConfiguration(surface)
                val executor = Executors.newSingleThreadExecutor()

                val sessionConfiguration = SessionConfiguration(
                    SessionConfiguration.SESSION_REGULAR,
                    listOf(outputConfiguration),
                    executor,
                    object : CameraCaptureSession.StateCallback() {
                        override fun onConfigured(session: CameraCaptureSession) {
                            session.setRepeatingRequest(captureRequest.build(), null, handler)
                        }

                        override fun onConfigureFailed(session: CameraCaptureSession) {}
                    }
                )

                cameraDevice.createCaptureSession(sessionConfiguration)
            }

            override fun onDisconnected(camera: CameraDevice) {}
            override fun onError(camera: CameraDevice, error: Int) {}
        }, handler)
    }

    private fun processImageForTextRecognition(bitmap: Bitmap) {
        val image = InputImage.fromBitmap(bitmap, 0)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                val detectedText = visionText.text
                if (detectedText.isNotEmpty()) {
                    speakOut(detectedText)
                }
            }
            .addOnFailureListener { e ->
                e.printStackTrace()
            }
    }

    private fun speakOut(text: String) {
        isSpeaking = true
        textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, "TextRecognitionTTS")
    }

    override fun onBackPressed() {
        super.onBackPressed()
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        textToSpeech.stop()
        textToSpeech.shutdown()
        handlerThread.quitSafely()
        if (::cameraDevice.isInitialized) {
            cameraDevice.close()
        }
    }
}