package com.programminghut.realtime_object

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import kotlin.math.abs
import android.os.*
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.*
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.programminghut.realtime_object.ml.SsdMobilenetV11Metadata1
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import java.util.*
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var labels: List<String>
    private lateinit var imageProcessor: ImageProcessor
    private lateinit var bitmap: Bitmap
    private lateinit var cameraDevice: CameraDevice
    private lateinit var handler: Handler
    private lateinit var handlerThread: HandlerThread
    private lateinit var cameraManager: CameraManager
    private lateinit var textureView: TextureView
    private lateinit var model: SsdMobilenetV11Metadata1
    private lateinit var textToSpeech: TextToSpeech
    private lateinit var gestureDetector: GestureDetector

    private var isSpeaking = false

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        getPermission()

        labels = FileUtil.loadLabels(this, "labels.txt")
        imageProcessor = ImageProcessor.Builder()
            .add(ResizeOp(300, 300, ResizeOp.ResizeMethod.BILINEAR)).build()
        model = SsdMobilenetV11Metadata1.newInstance(applicationContext)

        handlerThread = HandlerThread("videoThread")
        handlerThread.start()
        handler = Handler(handlerThread.looper)

        textureView = findViewById(R.id.textureView)

        // Initialize TTS
        textToSpeech = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech.language = Locale.US
                textToSpeech.setSpeechRate(1.0f)
                textToSpeech.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        isSpeaking = true
                    }

                    override fun onDone(utteranceId: String?) {
                        isSpeaking = false
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        isSpeaking = false
                    }
                })
                speakOut("Object Detection Started.", findViewById(android.R.id.content))
            }
        }

        textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
                openCamera()
            }

            override fun onSurfaceTextureSizeChanged(surfaceTexture: SurfaceTexture, width: Int, height: Int) {}

            override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean = false

            override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) {
                if (isSpeaking) return
                bitmap = textureView.bitmap ?: return

                var image = TensorImage.fromBitmap(bitmap)
                image = imageProcessor.process(image)

                val outputs = model.process(image)
                val locations = outputs.locationsAsTensorBuffer.floatArray
                val classes = outputs.classesAsTensorBuffer.floatArray
                val scores = outputs.scoresAsTensorBuffer.floatArray

                val h = bitmap.height
                val detectedObjects = mutableListOf<Triple<String, Float, Float>>()

                scores.forEachIndexed { index, score ->
                    if (score > 0.5) {
                        val label = labels[classes[index].toInt()]
                        val x = index * 4

                        val top = locations[x] * h
                        val bottom = locations[x + 2] * h

                        val boxHeight = bottom - top
                        val estimatedDistance = estimateDistance(boxHeight, h)

                        detectedObjects.add(Triple(label, score, estimatedDistance))
                    }
                }

                val topDetectedObjects = detectedObjects
                    .sortedByDescending { it.second }
                    .take(3)
                    .map { "${it.first}: ${String.format(Locale.US, "%.1f", it.third)} meters" }

                val detectedSpeech = topDetectedObjects.joinToString(", ")

                if (detectedSpeech.isNotEmpty()) {
                    speakOut(detectedSpeech, textureView)
                    isSpeaking = true
                }
            }
        }

        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager

        gestureDetector = GestureDetector(this, GestureListener())
        textureView.setOnTouchListener { _, event ->
            if (gestureDetector.onTouchEvent(event)) {
                true  // Gesture detected, consume the event
            } else {
                false  // Let other touch events pass through
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return if (gestureDetector.onTouchEvent(event)) {
            true
        } else {
            super.onTouchEvent(event)
        }
    }

    private fun getPermission() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.CAMERA), 101)
        }
    }

    @SuppressLint("MissingPermission")
    private fun openCamera() {
        cameraManager.openCamera(cameraManager.cameraIdList[0], object : CameraDevice.StateCallback() {
            @RequiresApi(Build.VERSION_CODES.P)
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

    private fun speakOut(text: String, view: View) {
        textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, "ObjectDetectionTTS")
        view.announceForAccessibility(text)

        runOnUiThread {
            val detectedObjectsTextView = findViewById<TextView>(R.id.detectedObjectsTextView)
            detectedObjectsTextView.text = text.replace(", ", "\n")  // New line after each object-distance pair
        }
    }

    private fun estimateDistance(boxHeight: Float, imageHeight: Int): Float {
        val sensorHeightMm = 4.29f
        val actualFocalLengthMm = (sensorHeightMm * 27f) / 35f
        val focalLengthPixels = (actualFocalLengthMm / sensorHeightMm) * imageHeight
        val knownObjectHeightMeters = 1.7f
        return (knownObjectHeightMeters * focalLengthPixels) / boxHeight
    }

    override fun onDestroy() {
        super.onDestroy()
        model.close()
        textToSpeech.stop()
        textToSpeech.shutdown()
        handlerThread.quitSafely()
        if (::cameraDevice.isInitialized) cameraDevice.close()
    }

    inner class GestureListener : GestureDetector.SimpleOnGestureListener() {
        private val swipeThreshold = 100
        private val swipeVelocityThreshold = 100

        override fun onFling(
            e1: MotionEvent?,
            e2: MotionEvent,
            velocityX: Float,
            velocityY: Float
        ): Boolean {
            if (e1 != null) {
                val diffX = e2.x - e1.x
                if (diffX > swipeThreshold && abs(velocityX) > swipeVelocityThreshold) {
                    startActivity(Intent(this@MainActivity, StartScreenActivity::class.java))
                    return true
                }
            }
            return false
        }
    }
}
