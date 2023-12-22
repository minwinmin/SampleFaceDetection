package com.sample.facedetection

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.sample.facedetection.databinding.ActivityMainBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    private lateinit var imageCapture: ImageCapture
    private lateinit var faceDetector: FaceDetector


    private var isFaceInsideRegion = false
    private lateinit var job: Job

    private lateinit var faceDetectionFrame: View
    private var previousDetectedFaceFrame: View? = null
    private val countdownTime = 5


    companion object {
        val TAG: String = MainActivity::class.java.simpleName
    }

    private val PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    private val REQUEST_PERMISSION = 1

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        // パーミッションの許可
        permissionRequest()

        // カメラ起動
        bindCameraUseCases(this, binding.previewViewFaceDetection.surfaceProvider, this)
        // 画像の撮影枠の表示
        faceDetectionFrame = GenerateFrame(this, "put your face in the frame", 450F, 450F, 255, 255, 255)
        binding.FaceDetectionLayout.addView(faceDetectionFrame)

    }

    private fun bindCameraUseCases(context: Context, surfaceProvider: Preview.SurfaceProvider, lifecycleOwner: LifecycleOwner){
        // スレッドを生成
        val cameraExecutors = Executors.newSingleThreadExecutor()
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val cameraProvider = cameraProviderFuture.get()
        val previewUseCase = Preview.Builder()
            .build()
            .also {
                it.setSurfaceProvider(surfaceProvider)
            }
        val imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
        // val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
        val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

        cameraProviderFuture.addListener({
            try {
                faceDetector = FaceDetection.getClient()
                val analysisUseCase = ImageAnalysis.Builder().build()
                analysisUseCase.setAnalyzer(cameraExecutors)
                { imageProxy ->
                    processImageProxy(faceDetector, imageProxy)
                }

                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    previewUseCase,
                    imageCapture,
                    analysisUseCase)

            } catch (illegalStateException: IllegalStateException) {
                Log.e(TAG, illegalStateException.message.orEmpty())
            } catch (illegalArgumentException: IllegalArgumentException) {
                Log.e(TAG, illegalArgumentException.message.orEmpty())
            }

        }, ContextCompat.getMainExecutor(context))
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun processImageProxy(faceDetector: FaceDetector, imageProxy: ImageProxy, ) {
        imageProxy.image?.let { image ->
            val inputImage =
                InputImage.fromMediaImage(
                    image,
                    imageProxy.imageInfo.rotationDegrees
                )

            faceDetector.process(inputImage)
                .addOnSuccessListener { faces ->
                    // １つ前に描画された顔領域フレームを削除
                    binding.FaceDetectionLayout.removeView(previousDetectedFaceFrame)

                    for(face in faces){
                        val previewWidth = binding.previewViewFaceDetection.width
                        val previewHeight = binding.previewViewFaceDetection.height
                        // 顔の検出領域の微修正
                        val convertRect = convertRectArea(face.boundingBox, previewWidth.toFloat(), previewHeight.toFloat())
                        val currentDetectedFaceFrame = DetectionArea(this, convertRect, previewWidth.toFloat(), previewHeight.toFloat())
                        binding.FaceDetectionLayout.addView(currentDetectedFaceFrame)
                        previousDetectedFaceFrame = currentDetectedFaceFrame

                        // エリア内にいるか判定
                        val areaBoolean = isRectContaining(convertRect , RectF(510.0F, 94.0F, 1410.0F, 994.0F))
                        if (areaBoolean){
                            if(!isFaceInsideRegion){
                                binding.FaceDetectionLayout.removeView(faceDetectionFrame)
                                faceDetectionFrame = GenerateFrame(this, "put your face in the frame", 450F, 450F, 255, 0, 0) //文字色変更
                                binding.FaceDetectionLayout.addView(faceDetectionFrame)
                                // カウントダウン → 撮影
                                countDown()
                            }
                        }else{
                            binding.FaceDetectionLayout.removeView(faceDetectionFrame)
                            faceDetectionFrame = GenerateFrame(this, "put your face in the frame", 450F, 450F, 255, 255, 255) //文字色変更
                            binding.FaceDetectionLayout.addView(faceDetectionFrame)
                            binding.textCountDown.text = ""
                            // カウントダウンをキャンセル
                            cancelCountDown()
                        }

                    }
                }
                .addOnFailureListener {
                    Log.e(TAG, it.message.orEmpty())
                }.addOnCompleteListener {
                    imageProxy.image?.close()
                    imageProxy.close()
                }
        }
    }

    private fun stopPreview(){
        // previewへの投影を止める
        // cameraProvider.unbind(previewUseCase)
        // 顔検出を止める
        faceDetector.close()
    }

    private fun isRectContaining(faceRect: RectF, targetRect: RectF):Boolean{
        return targetRect.contains(faceRect)
    }

    private fun convertRectArea(detectionArea:Rect, previewWidth:Float, previewHeight:Float):RectF{
        val expandedLeft = detectionArea.left * 3
        val expandedTop = detectionArea.top * 3
        val expandedRight = detectionArea.right * 3
        val expandedBottom = detectionArea.bottom * 3

        // 画面中央に対して線対称の領域を考える
        val symmetricLeft = previewWidth - expandedRight
        val symmetricRight = previewWidth - expandedLeft
        // 顔の検出領域の微修正
        val symmetricTop = expandedTop - expandedTop * 0.5
        val symmetricBottom = expandedBottom - expandedBottom * 0.1
        val symmetricRectF = RectF(symmetricLeft, symmetricTop.toFloat(), symmetricRight, symmetricBottom.toFloat())

        return symmetricRectF
    }

    private fun cancelCountDown() {
        if (::job.isInitialized && job.isActive) {
            job.cancel()
        }
    }

    private fun countDown(){
        isFaceInsideRegion = true
        val scope = CoroutineScope(Dispatchers.Default)
        val handler = Handler(Looper.getMainLooper())
        job = scope.launch {
            try {
                handler.post(
                    kotlinx.coroutines.Runnable { binding.textCountDown.text = countdownTime.toString() }
                )
                repeat(countdownTime) { i ->
                    delay(1000L)
                    val times = i+1
                    handler.post(
                        kotlinx.coroutines.Runnable { binding.textCountDown.text = (countdownTime - times).toString() }
                    )
                }
            } finally {
                if(job.isActive){
                    // カウントダウン後の処理を記述
                    isFaceInsideRegion = false
                    handler.post(
                        kotlinx.coroutines.Runnable {
                            binding.textCountDown.text = "Face is detected ...!"
                            binding.FaceDetectionLayout.removeView(faceDetectionFrame)
                            binding.FaceDetectionLayout.removeView(previousDetectedFaceFrame)
                        }
                    )
                    stopPreview()
                    scope.cancel()
                }else{
                    isFaceInsideRegion = false
                }
            }
        }
    }

    // パーミッションのリクエスト
    private fun permissionRequest(){
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, PERMISSIONS, REQUEST_PERMISSION)
        }
    }
}