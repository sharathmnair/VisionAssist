package com.programminghut.realtime_object

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.*
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.os.*
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.Surface
import android.view.TextureView
import android.widget.Button
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

    lateinit var labels: List<String>
    lateinit var imageProcessor: ImageProcessor
    lateinit var bitmap: Bitmap
    lateinit var cameraDevice: CameraDevice
    lateinit var handler: Handler
    lateinit var handlerThread: HandlerThread
    lateinit var cameraManager: CameraManager
    lateinit var textureView: TextureView
    lateinit var model: SsdMobilenetV11Metadata1
    lateinit var textToSpeech: TextToSpeech

    var lastSpokenText: String = ""
    var isSpeaking = false

    val paint = Paint().apply {
        color = Color.RED
        style = Paint.Style.STROKE
        strokeWidth = 8f
        textSize = 50f
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val stopDetectionButton: Button = findViewById(R.id.stopDetectionButton)
        val exitButton: Button = findViewById(R.id.exitButton)

        exitButton.setOnClickListener {
            val intent = Intent(this, StartScreenActivity::class.java)
            startActivity(intent)
            finish()
        }

        var lastTapTime = 0L
        stopDetectionButton.setOnClickListener {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastTapTime < 300) {
                val intent = Intent(this, TextRecognitionActivity::class.java)
                startActivity(intent)
                finish()
            } else {
                lastTapTime = currentTime
            }
        }

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
                speakOut("Object Detection Started.")
            }
        }

        textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
                openCamera()
            }

            override fun onSurfaceTextureSizeChanged(surfaceTexture: SurfaceTexture, width: Int, height: Int) {}

            override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean {
                return false
            }

            override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) {
                if (isSpeaking) return

                bitmap = textureView.bitmap ?: return
                var image = TensorImage.fromBitmap(bitmap)
                image = imageProcessor.process(image)

                val outputs = model.process(image)
                val locations = outputs.locationsAsTensorBuffer.floatArray
                val classes = outputs.classesAsTensorBuffer.floatArray
                val scores = outputs.scoresAsTensorBuffer.floatArray

                val canvas = Canvas(bitmap)
                val h = bitmap.height
                val w = bitmap.width

                val detectedObjects = mutableSetOf<String>()
                scores.forEachIndexed { index, score ->
                    if (score > 0.5) {
                        val label = labels[classes[index].toInt()]
                        val x = index * 4

                        val left = locations[x + 1] * w
                        val top = locations[x] * h
                        val right = locations[x + 3] * w
                        val bottom = locations[x + 2] * h

                        val boxHeight = bottom - top

                        val estimatedDistance = estimateDistance(boxHeight, h)

                        val detectedText = "$label, $estimatedDistance meters"
                        detectedObjects.add(detectedText)

                        paint.style = Paint.Style.STROKE
                        canvas.drawRect(RectF(left, top, right, bottom), paint)

                        paint.style = Paint.Style.FILL
                        canvas.drawText("$label (${String.format("%.1f", estimatedDistance)}m)", left, top - 10, paint)
                    }
                }

                val detectedSpeech = detectedObjects.joinToString(", ")
                if (detectedSpeech.isNotEmpty() && detectedSpeech != lastSpokenText) {
                    speakOut(detectedSpeech)
                    lastSpokenText = detectedSpeech
                    isSpeaking = true
                }
            }
        }

        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
    }

    override fun onResume() {
        super.onResume()
        if (::textToSpeech.isInitialized) {
            speakOut("Object Detection Started.")
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

    private fun getPermission() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.CAMERA), 101)
        }
    }

    private fun speakOut(text: String) {
        val params = Bundle()
        textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, params, "ObjectDetectionTTS")
    }

    private fun estimateDistance(boxHeight: Float, imageHeight: Int): Float {
        val referenceHeight = 200f // Assume 200 pixels corresponds to 1 meter
        val distance = referenceHeight / boxHeight
        return String.format("%.1f", distance).toFloat() // Rounds to 2 decimal places
    }

    override fun onBackPressed() {
        super.onBackPressed()
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        model.close()
        textToSpeech.stop()
        textToSpeech.shutdown()
        handlerThread.quitSafely()
        if (::cameraDevice.isInitialized) {
            cameraDevice.close()
        }
    }
}