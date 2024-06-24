package dev.steenbakker.mobile_scanner

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Rect
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Surface
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.TorchState
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.ZoomSuggestionOptions
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import dev.steenbakker.mobile_scanner.config.AdaptiveCameraConfig
import dev.steenbakker.mobile_scanner.objects.DetectionSpeed
import dev.steenbakker.mobile_scanner.objects.MobileScannerStartParameters
import dev.steenbakker.mobile_scanner.utils.YuvToRgbConverter
import io.flutter.BuildConfig
import io.flutter.view.TextureRegistry
import java.io.ByteArrayOutputStream
import kotlin.math.roundToInt


class MobileScanner(
    private val activity: Activity,
    private val textureRegistry: TextureRegistry,
    private val mobileScannerCallback: MobileScannerCallback,
    private val mobileScannerErrorCallback: MobileScannerErrorCallback
) {
    companion object {
        const val TAG = "MobileScanner"
    }

    /// Internal variables
    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var preview: Preview? = null
    private var textureEntry: TextureRegistry.SurfaceTextureEntry? = null
    private var scanner: BarcodeScanner? = null
    private var lastScanned: List<String?>? = null
    private var scannerTimeout = false

    /// Configurable variables
    var scanWindow: List<Float>? = null
    private var detectionSpeed: DetectionSpeed = DetectionSpeed.NO_DUPLICATES
    private var detectionTimeout: Long = 250
    private var returnImage = false

    private val debug: Boolean = BuildConfig.DEBUG

    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

    private var receivedFirstFrame = false

    /**
     * callback for the camera. Every frame is passed through this function.
     */
    @ExperimentalGetImage
    val captureOutput = ImageAnalysis.Analyzer { imageProxy -> // YUV_420_888 format
        if (!receivedFirstFrame) {
            receivedFirstFrame = true
            this.mobileScannerErrorCallback("ReceivedFirstFrame")
        }

        val scanner = this.scanner
        val mediaImage = imageProxy.image

        if (scanner == null || mediaImage == null) {
            imageProxy.close()
            return@Analyzer
        }

        val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

        if (detectionSpeed == DetectionSpeed.NORMAL && scannerTimeout) {
            imageProxy.close()
            return@Analyzer
        } else if (detectionSpeed == DetectionSpeed.NORMAL) {
            scannerTimeout = true
        }

        scanner.process(inputImage)
            .addOnSuccessListener { barcodes ->
                if (detectionSpeed == DetectionSpeed.NO_DUPLICATES) {
                    val newScannedBarcodes = barcodes.mapNotNull { barcode -> barcode.rawValue }.sorted()
                    if (newScannedBarcodes == lastScanned) {
                        // New scanned is duplicate, returning
                        return@addOnSuccessListener
                    }
                    if (newScannedBarcodes.isNotEmpty()) lastScanned = newScannedBarcodes
                }

                val barcodeMap: MutableList<Map<String, Any?>> = mutableListOf()

                for (barcode in barcodes) {
                    if (scanWindow != null) {
                        val match = isBarcodeInScanWindow(scanWindow!!, barcode, imageProxy)
                        if (!match) {
                            continue
                        } else {
                            barcodeMap.add(barcode.data)
                        }
                    } else {
                        barcodeMap.add(barcode.data)
                    }
                }


                if (barcodeMap.isNotEmpty()) {
                    if (returnImage) {

                        val bitmap = Bitmap.createBitmap(mediaImage.width, mediaImage.height, Bitmap.Config.ARGB_8888)

                        val imageFormat = YuvToRgbConverter(activity.applicationContext)

                        imageFormat.yuvToRgb(mediaImage, bitmap)

                        val bmResult = rotateBitmap(bitmap, camera?.cameraInfo?.sensorRotationDegrees?.toFloat() ?: 90f)

                        val stream = ByteArrayOutputStream()
                        bmResult.compress(Bitmap.CompressFormat.JPEG, 90, stream)
                        val byteArray = stream.toByteArray()
                        val bmWidth = bmResult.width
                        val bmHeight = bmResult.height
                        bmResult.recycle()


                        mobileScannerCallback(
                            barcodeMap,
                            byteArray,
                            bmWidth,
                            bmHeight
                        )

                    } else {

                        mobileScannerCallback(
                            barcodeMap,
                            null,
                            null,
                            null
                        )
                    }
                }
            }
            .addOnFailureListener { e ->
                mobileScannerErrorCallback(
                    e.localizedMessage ?: e.toString()
                )
            }
            .addOnCompleteListener { imageProxy.close() }

        if (detectionSpeed == DetectionSpeed.NORMAL) {
            // Set timer and continue
            mainHandler.postDelayed({
                scannerTimeout = false
            }, detectionTimeout)
        }
    }

    private fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(degrees)
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }


    // scales the scanWindow to the provided inputImage and checks if that scaled
    // scanWindow contains the barcode
    private fun isBarcodeInScanWindow(
        scanWindow: List<Float>,
        barcode: Barcode,
        inputImage: ImageProxy
    ): Boolean {
        val barcodeBoundingBox = barcode.boundingBox ?: return false

        val imageWidth = inputImage.height
        val imageHeight = inputImage.width

        val left = (scanWindow[0] * imageWidth).roundToInt()
        val top = (scanWindow[1] * imageHeight).roundToInt()
        val right = (scanWindow[2] * imageWidth).roundToInt()
        val bottom = (scanWindow[3] * imageHeight).roundToInt()

        val scaledScanWindow = Rect(left, top, right, bottom)
        return scaledScanWindow.contains(barcodeBoundingBox)
    }

    /**
     * Start barcode scanning by initializing the camera and barcode scanner.
     */
    @ExperimentalGetImage
    fun start(
        barcodeFormats: List<Int>,
        autoZoom: Boolean,
        returnImage: Boolean,
        cameraPosition: CameraSelector,
        torch: Boolean,
        detectionSpeed: DetectionSpeed,
        torchStateCallback: TorchStateCallback,
        zoomScaleStateCallback: ZoomScaleStateCallback,
        mobileScannerStartedCallback: MobileScannerStartedCallback,
        mobileScannerErrorCallback: (exception: Exception) -> Unit,
        detectionTimeout: Long
    ) {
        this.detectionSpeed = detectionSpeed
        this.detectionTimeout = detectionTimeout
        this.returnImage = returnImage

        if (camera?.cameraInfo != null && preview != null && textureEntry != null) {
            mobileScannerErrorCallback(AlreadyStarted())

            return
        }

        lastScanned = null

        if (debug) Log.d(TAG, "barcodeFormats: $barcodeFormats")
        scanner = if (barcodeFormats.isEmpty() && !autoZoom) {
            BarcodeScanning.getClient()
        } else {
            val builder = BarcodeScannerOptions.Builder()

            if (barcodeFormats.isNotEmpty()) {
                if (barcodeFormats.size == 1) {
                    builder.setBarcodeFormats(barcodeFormats.first())
                } else {
                    builder.setBarcodeFormats(
                        barcodeFormats.first(),
                        *barcodeFormats.subList(1, barcodeFormats.size).toIntArray()
                    )
                }
            }

            if (autoZoom) {
                builder.setZoomSuggestionOptions(ZoomSuggestionOptions.Builder { requestRatio ->
                    val camera = camera
                    if (camera != null) {
                        val maxZoomRatio = camera.cameraInfo.zoomState.value?.maxZoomRatio

                        if (debug) Log.d(TAG, "request: $requestRatio maxZoomRatio: $maxZoomRatio")

                        if (maxZoomRatio != null) {
                            if (requestRatio <= maxZoomRatio) {
                                camera.cameraControl.setZoomRatio(requestRatio)
                                true
                            } else {
                                false
                            }
                        } else {
                            false
                        }
                    } else {
                        false
                    }
                }.build())
            }

            BarcodeScanning.getClient(builder.build())
        }

        val cameraProviderFuture = ProcessCameraProvider.getInstance(activity)
        val executor = ContextCompat.getMainExecutor(activity)
        val adaptiveCameraConfig = AdaptiveCameraConfig(activity)

        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            val numberOfCameras = cameraProvider?.availableCameraInfos?.size

            if (cameraProvider == null) {
                mobileScannerErrorCallback(CameraError())

                return@addListener
            }

            cameraProvider?.unbindAll()
            textureEntry = textureRegistry.createSurfaceTexture()

            // Preview
            val surfaceProvider = Preview.SurfaceProvider { request ->
                if (isStopped()) {
                    return@SurfaceProvider
                }

                if (debug) Log.d(
                    TAG,
                    "Preview.SurfaceProvider request: ${request.resolution.width}, ${request.resolution.height}"
                )

                val texture = textureEntry!!.surfaceTexture()
                texture.setDefaultBufferSize(
                    request.resolution.width,
                    request.resolution.height
                )

                val surface = Surface(texture)
                request.provideSurface(surface, executor) { }
            }

            // Build the preview to be shown on the Flutter texture
            val previewBuilder = Preview.Builder()
            preview = adaptiveCameraConfig.options(previewBuilder)
                .apply { setSurfaceProvider(surfaceProvider) }

            // Build the analyzer to be passed on to MLKit
            val analysisBuilder = ImageAnalysis.Builder()
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)

            val analysis = adaptiveCameraConfig.options(analysisBuilder)
                .apply { setAnalyzer(executor, captureOutput) }

            try {
                camera = cameraProvider?.bindToLifecycle(
                    activity as LifecycleOwner,
                    cameraPosition,
                    preview,
                    analysis
                )
                camera?.cameraInfo?.cameraState?.observe(activity as LifecycleOwner) { cameraState ->
                    if (cameraState.error?.code == androidx.camera.core.CameraState.ERROR_OTHER_RECOVERABLE_ERROR) {
                        this.mobileScannerErrorCallback("Other recoverable error")
                    }
                }
            } catch (exception: Exception) {
                exception.printStackTrace()
                mobileScannerErrorCallback(NoCamera())

                return@addListener
            }

            camera?.let {
                // Register the torch listener
                it.cameraInfo.torchState.observe(activity as LifecycleOwner) { state ->
                    // TorchState.OFF = 0; TorchState.ON = 1
                    torchStateCallback(state)
                }

                // Register the zoom scale listener
                it.cameraInfo.zoomState.observe(activity) { state ->
                    zoomScaleStateCallback(state.linearZoom.toDouble())
                }

                // Enable torch if provided
                if (it.cameraInfo.hasFlashUnit()) {
                    it.cameraControl.enableTorch(torch)
                }
            }

            val resolution = preview!!.resolutionInfo!!.resolution
            val width = resolution.width.toDouble()
            val height = resolution.height.toDouble()
            val portrait = (camera?.cameraInfo?.sensorRotationDegrees ?: 0) % 180 == 0
            if (debug) Log.d(
                TAG,
                "camera: preview(${resolution}) analysis(${analysis.resolutionInfo?.resolution})"
            )

            // Start with 'unavailable' torch state.
            var currentTorchState: Int = -1

            camera?.cameraInfo?.let {
                if (!it.hasFlashUnit()) {
                    return@let
                }

                currentTorchState = it.torchState.value ?: -1
            }

            mobileScannerStartedCallback(
                MobileScannerStartParameters(
                    if (portrait) width else height,
                    if (portrait) height else width,
                    currentTorchState,
                    textureEntry!!.id(),
                    numberOfCameras ?: 0
                )
            )
        }, executor)

    }

    /**
     * Stop barcode scanning.
     */
    fun stop() {
        if (isStopped()) {
            throw AlreadyStopped()
        }

        val owner = activity as LifecycleOwner
        camera?.cameraInfo?.torchState?.removeObservers(owner)
        camera?.cameraInfo?.cameraState?.removeObservers(owner)
        cameraProvider?.unbindAll()
        textureEntry?.release()
        scanner?.close()

        camera = null
        preview = null
        textureEntry = null
        cameraProvider = null
        scanner = null
    }

    private fun isStopped() = camera == null && preview == null

    /**
     * Toggles the flash light on or off.
     */
    fun toggleTorch() {
        camera?.let {
            if (!it.cameraInfo.hasFlashUnit()) {
                return@let
            }

            when(it.cameraInfo.torchState.value) {
                TorchState.OFF -> it.cameraControl.enableTorch(true)
                TorchState.ON -> it.cameraControl.enableTorch(false)
            }
        }
    }

    /**
     * Analyze a single image.
     */
    fun analyzeImage(image: Uri, onSuccess: AnalyzerSuccessCallback, onError: AnalyzerErrorCallback) {
        val inputImage = InputImage.fromFilePath(activity, image)

        val scanner = BarcodeScanning.getClient()
        scanner.process(inputImage)
            .addOnSuccessListener { barcodes ->
                val barcodeMap = barcodes.map { barcode -> barcode.data }

                if (barcodeMap.isNotEmpty()) {
                    onSuccess(barcodeMap)
                } else {
                    onSuccess(null)
                }
            }
            .addOnFailureListener { e ->
                onError(e.localizedMessage ?: e.toString())
            }.addOnCompleteListener {
                scanner.close()
            }
    }

    /**
     * Set the zoom rate of the camera.
     */
    fun setScale(scale: Double) {
        if (scale > 1.0 || scale < 0) throw ZoomNotInRange()
        if (camera == null) throw ZoomWhenStopped()
        camera?.cameraControl?.setLinearZoom(scale.toFloat())
    }

    /**
     * Reset the zoom rate of the camera.
     */
    fun resetScale() {
        if (camera == null) throw ZoomWhenStopped()
        camera?.cameraControl?.setZoomRatio(1f)
    }

}
