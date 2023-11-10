package com.example.myapplication

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.util.Size
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var previewView: PreviewView
    private lateinit var overlayView: View
    private lateinit var tvInstructions: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        previewView = findViewById(R.id.previewView)
        overlayView = findViewById(R.id.overlayView)
        tvInstructions = findViewById(R.id.tvInstructions)

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

        cameraExecutor = Executors.newSingleThreadExecutor()
    }


    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

            val imageAnalysis = ImageAnalysis.Builder()
                .setTargetResolution(Size(1280, 720))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor) { imageProxy ->
                        processImageProxy(imageProxy)
                    }
                }

            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, imageAnalysis, preview)
            } catch (exc: Exception) {
                Toast.makeText(this, "Erro ao iniciar a câmera.", Toast.LENGTH_SHORT).show()
            }

        }, ContextCompat.getMainExecutor(this))
    }

    @OptIn(ExperimentalGetImage::class)
    private fun processImageProxy(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            val detector = FaceDetection.getClient(
                FaceDetectorOptions.Builder()
                    .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                    .build()
            )

            detector.process(image)
                .addOnSuccessListener { faces ->
                    processFaces(faces, imageProxy.width, imageProxy.height)
                }
                .addOnFailureListener {
                    // Tratar erro
                }
                .addOnCompleteListener {
                    imageProxy.close() // Não se esqueça de fechar o ImageProxy
                }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun processFaces(faces: List<Face>, width: Int, height: Int) {
        if (faces.isNotEmpty()) {
            val face = faces.first()
            val xCenter = face.boundingBox.centerX()
            val yCenter = face.boundingBox.centerY()

            val isFaceCentered = isFaceCentered(xCenter, yCenter, width, height)
            runOnUiThread {
                if (isFaceCentered) {
                    overlayView.setBackgroundColor(Color.parseColor("#80FF00FF")) // Verde semi-transparente
                    tvInstructions.text = "Rosto centralizado!"
                } else {
                    overlayView.setBackgroundColor(Color.parseColor("#80FF0000")) // Vermelho semi-transparente
                    tvInstructions.text = getMovementInstructions(xCenter, yCenter, width, height)
                }
            }
        }
    }

    private fun getMovementInstructions(
        xCenter: Int,
        yCenter: Int,
        width: Int,
        height: Int
    ): String {
        val horizontalMovement =
            if (xCenter < width / 2)  "Mova o rosto para esquerda" else "Mova o rosto para direita"
        val verticalMovement = if (yCenter < height / 2) "Mova para baixo" else "Mova para cima"

        return "$horizontalMovement e $verticalMovement"
    }

    private fun isFaceCentered(xCenter: Int, yCenter: Int, imageWidth: Int, imageHeight: Int): Boolean {

        val offsetX = -120 // Exemplo de deslocamento horizontal, ajuste conforme necessário
        val offsetY = 30 // Exemplo de deslocamento vertical, ajuste conforme necessário

        // Calcula o centro ajustado da imagem
        val adjustedCenterX = (imageWidth / 2) + offsetX
        val adjustedCenterY = (imageHeight / 2) + offsetY

        // Defina uma margem de tolerância para centralização
        val tolerance = 0.1f
        val xTolerance = imageWidth * tolerance
        val yTolerance = imageHeight * tolerance

        // Verifica se o centro do rosto está dentro da margem de tolerância do centro ajustado
        return Math.abs(xCenter - adjustedCenterX) < xTolerance && Math.abs(yCenter - adjustedCenterY) < yTolerance
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(this, "Permissões não concedidas pelo usuário.", Toast.LENGTH_SHORT)
                    .show()
                finish()
            }
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }
}