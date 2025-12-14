package dev.steenbakker.mobile_scanner

import android.app.Activity
import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ExperimentalLensFacing
import androidx.camera.camera2.interop.Camera2CameraInfo
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import dev.steenbakker.mobile_scanner.objects.BarcodeFormats
import dev.steenbakker.mobile_scanner.objects.DetectionSpeed
import dev.steenbakker.mobile_scanner.objects.MobileScannerErrorCodes
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.PluginRegistry.RequestPermissionsResultListener
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.EventChannel
import io.flutter.view.TextureRegistry
import java.io.File
import com.google.mlkit.vision.barcode.ZoomSuggestionOptions

class MobileScannerHandler(
    private val activity: Activity,
    private val barcodeHandler: BarcodeHandler,
    binaryMessenger: BinaryMessenger,
    private val permissions: MobileScannerPermissions,
    private val addPermissionListener: (RequestPermissionsResultListener) -> Unit,
    textureRegistry: TextureRegistry): MethodChannel.MethodCallHandler {

    companion object {
        /**
         * Lens type constants matching the Dart enum values.
         */
        const val LENS_TYPE_NORMAL = 0
        const val LENS_TYPE_WIDE = 1
        const val LENS_TYPE_ZOOM = 2
        const val LENS_TYPE_ANY = -1

        /**
         * Focal length thresholds for classifying smartphone camera lenses.
         *
         * These values are heuristics based on typical smartphone camera modules.
         * There is no official standard, but these ranges are commonly observed:
         *
         * - Ultra-wide lenses: ~1.5-3.5mm physical focal length (~13-16mm 35mm equivalent)
         * - Standard/Wide lenses: ~4-6mm physical focal length (~24-28mm 35mm equivalent)
         * - Telephoto lenses: ~6mm+ physical focal length (~50mm+ 35mm equivalent)
         *
         * The 35mm equivalent depends on the sensor size, which varies between devices.
         * These thresholds work well for most modern smartphones but may need adjustment
         * for unusual camera configurations.
         *
         * References:
         * - Android CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS documentation
         * - https://developer.android.com/reference/android/hardware/camera2/CameraCharacteristics#LENS_INFO_AVAILABLE_FOCAL_LENGTHS
         */
        const val FOCAL_LENGTH_WIDE_THRESHOLD = 4.0f
        const val FOCAL_LENGTH_ZOOM_THRESHOLD = 6.0f

        /**
         * Classifies a focal length into a lens type category.
         *
         * @param focalLength The physical focal length in millimeters
         * @return The lens type: [LENS_TYPE_WIDE], [LENS_TYPE_NORMAL], or [LENS_TYPE_ZOOM]
         */
        fun classifyLensType(focalLength: Float): Int {
            return when {
                focalLength < FOCAL_LENGTH_WIDE_THRESHOLD -> LENS_TYPE_WIDE
                focalLength <= FOCAL_LENGTH_ZOOM_THRESHOLD -> LENS_TYPE_NORMAL
                else -> LENS_TYPE_ZOOM
            }
        }

        /**
         * Checks if a focal length matches the requested lens type.
         *
         * @param focalLength The physical focal length in millimeters
         * @param lensType The requested lens type
         * @return True if the focal length matches the lens type category
         */
        fun matchesLensType(focalLength: Float, lensType: Int): Boolean {
            if (lensType == LENS_TYPE_ANY) return true
            return classifyLensType(focalLength) == lensType
        }
    }

    private val analyzeImageErrorCallback: AnalyzerErrorCallback = {
        Handler(Looper.getMainLooper()).post {
            analyzerResult?.error(MobileScannerErrorCodes.BARCODE_ERROR, it, null)
            analyzerResult = null
        }
    }

    private val analyzeImageSuccessCallback: AnalyzerSuccessCallback = {
        Handler(Looper.getMainLooper()).post {
            // TODO: Open for discussion if we want to publish the results on the barcode stream as well.
//            // Also publish on controller result
//            barcodeHandler.publishEvent(mapOf(
//                "name" to "barcode",
//                "data" to it,
//            ))

            analyzerResult?.success(mapOf(
                "name" to "barcode",
                "data" to it
            ))
            analyzerResult = null
        }
    }

    private var analyzerResult: MethodChannel.Result? = null

    private val callback: MobileScannerCallback = { barcodes: List<Map<String, Any?>>, image: ByteArray?, width: Int?, height: Int? ->
        barcodeHandler.publishEvent(mapOf(
            "name" to "barcode",
            "data" to barcodes,
            // The image dimensions are always provided.
            // The image bytes are only non-null when `returnImage` is true.
            "image" to mapOf(
                "bytes" to image,
                "width" to width?.toDouble(),
                "height" to height?.toDouble(),
            )
        ))
    }

    private val errorCallback: MobileScannerErrorCallback = {error: String ->
        barcodeHandler.publishError(MobileScannerErrorCodes.BARCODE_ERROR, error, null)
    }

    private var methodChannel: MethodChannel? = null
    private var deviceOrientationChannel: EventChannel? = null

    private var mobileScanner: MobileScanner? = null

    private val torchStateCallback: TorchStateCallback = {state: Int ->
        // Off = 0, On = 1
        barcodeHandler.publishEvent(mapOf("name" to "torchState", "data" to state))
    }

    private val zoomScaleStateCallback: ZoomScaleStateCallback = {zoomScale: Double ->
        barcodeHandler.publishEvent(mapOf("name" to "zoomScaleState", "data" to zoomScale))
    }

    init {
        methodChannel = MethodChannel(binaryMessenger,
            "dev.steenbakker.mobile_scanner/scanner/method")
        methodChannel!!.setMethodCallHandler(this)

        val deviceOrientationListener = DeviceOrientationListener(activity)

        deviceOrientationChannel = EventChannel(binaryMessenger,
            "dev.steenbakker.mobile_scanner/scanner/deviceOrientation")
        deviceOrientationChannel!!.setStreamHandler(deviceOrientationListener)

        mobileScanner = MobileScanner(
            activity, textureRegistry, callback, errorCallback, deviceOrientationListener)
    }

    fun dispose(activityPluginBinding: ActivityPluginBinding) {
        methodChannel?.setMethodCallHandler(null)
        methodChannel = null
        deviceOrientationChannel?.setStreamHandler(null)
        deviceOrientationChannel = null
        barcodeHandler.dispose()
        mobileScanner?.dispose()
        mobileScanner = null

        val listener: RequestPermissionsResultListener? = permissions.getPermissionListener()

        if(listener != null) {
            activityPluginBinding.removeRequestPermissionsResultListener(listener)
        }
    }

    @ExperimentalLensFacing
    @ExperimentalGetImage
    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            "state" -> result.success(permissions.hasCameraPermission(activity))
            "request" -> permissions.requestPermission(
                activity,
                addPermissionListener,
                object: MobileScannerPermissions.ResultCallback {
                    override fun onResult(errorCode: String?) {
                        when(errorCode) {
                            null -> result.success(true)
                            MobileScannerErrorCodes.CAMERA_ACCESS_DENIED -> result.success(false)
                            MobileScannerErrorCodes.CAMERA_PERMISSIONS_REQUEST_ONGOING -> result.error(
                                MobileScannerErrorCodes.CAMERA_PERMISSIONS_REQUEST_ONGOING,
                                MobileScannerErrorCodes.CAMERA_PERMISSIONS_REQUEST_ONGOING_MESSAGE, null)
                            else -> result.error(
                                MobileScannerErrorCodes.GENERIC_ERROR, MobileScannerErrorCodes.GENERIC_ERROR_MESSAGE, null)
                        }
                    }
                })
            "start" -> start(call, result)
            "pause" -> pause(call, result)
            "stop" -> stop(call, result)
            "toggleTorch" -> toggleTorch(result)
            "getSupportedLenses" -> getSupportedLenses(result)
            "analyzeImage" -> analyzeImage(call, result)
            "setScale" -> setScale(call, result)
            "resetScale" -> resetScale(result)
            "updateScanWindow" -> updateScanWindow(call, result)
            "setFocus" -> setFocus(call, result)
            else -> result.notImplemented()
        }
    }

    @ExperimentalLensFacing
    @ExperimentalGetImage
    private fun start(call: MethodCall, result: MethodChannel.Result) {
        val torch: Boolean = call.argument<Boolean>("torch") ?: false
        val facing: Int = call.argument<Int>("facing") ?: 0
        val lensType: Int = call.argument<Int>("lensType") ?: -1
        val formats: List<Int>? = call.argument<List<Int>>("formats")
        val returnImage: Boolean = call.argument<Boolean>("returnImage") ?: false
        val speed: Int = call.argument<Int>("speed") ?: 1
        val timeout: Int = call.argument<Int>("timeout") ?: 250
        val cameraResolutionValues: List<Int>? = call.argument<List<Int>>("cameraResolution")
        val autoZoom: Boolean = call.argument<Boolean>("autoZoom") ?: false
        val cameraResolution: Size? = if (cameraResolutionValues != null) {
            Size(cameraResolutionValues[0], cameraResolutionValues[1])
        } else {
            null
        }
        val invertImage: Boolean = call.argument<Boolean>("invertImage") ?: false
        val initialZoom: Double? = call.argument<Double?>("initialZoom")

        val barcodeScannerOptions: BarcodeScannerOptions? = buildBarcodeScannerOptions(formats, autoZoom)

        val position = selectCameraUsingFacingAndLens(facing, lensType)

        val detectionSpeed: DetectionSpeed = when (speed) {
            0 -> DetectionSpeed.NO_DUPLICATES
            1 -> DetectionSpeed.NORMAL
            else -> DetectionSpeed.UNRESTRICTED
        }

        mobileScanner!!.start(
            barcodeScannerOptions,
            returnImage,
            position,
            torch,
            detectionSpeed,
            torchStateCallback,
            zoomScaleStateCallback,
            mobileScannerStartedCallback = {
                Handler(Looper.getMainLooper()).post {
                    result.success(mapOf(
                        "textureId" to it.id,
                        "size" to mapOf("width" to it.width, "height" to it.height),
                        "naturalDeviceOrientation" to it.naturalDeviceOrientation,
                        "handlesCropAndRotation" to it.handlesCropAndRotation,
                        "sensorOrientation" to it.sensorOrientation,
                        "currentTorchState" to it.currentTorchState,
                        "numberOfCameras" to it.numberOfCameras,
                        "cameraDirection" to it.cameraDirection
                    ))
                }
            },
            mobileScannerErrorCallback = {
                Handler(Looper.getMainLooper()).post {
                    when (it) {
                        is AlreadyStarted -> {
                            result.error(
                                MobileScannerErrorCodes.ALREADY_STARTED_ERROR,
                                MobileScannerErrorCodes.ALREADY_STARTED_ERROR_MESSAGE,
                                null
                            )
                        }
                        is CameraError -> {
                            result.error(
                                MobileScannerErrorCodes.CAMERA_ERROR,
                                MobileScannerErrorCodes.CAMERA_ERROR_MESSAGE,
                                null
                            )
                        }
                        is NoCamera -> {
                            result.error(
                                MobileScannerErrorCodes.NO_CAMERA_ERROR,
                                MobileScannerErrorCodes.NO_CAMERA_ERROR_MESSAGE,
                                null
                            )
                        }
                        else -> {
                            result.error(
                                MobileScannerErrorCodes.GENERIC_ERROR,
                                MobileScannerErrorCodes.GENERIC_ERROR_MESSAGE,
                                null
                            )
                        }
                    }
                }
            },
            timeout.toLong(),
            cameraResolution,
            invertImage,
            initialZoom
        )
    }

    private fun pause(call: MethodCall, result: MethodChannel.Result) {
        val force: Boolean = call.argument<Boolean>("force") ?: false
        try {
            mobileScanner!!.pause(force)
            result.success(null)
        } catch (e: Exception) {
            when (e) {
                is AlreadyPaused, is AlreadyStopped -> result.success(null)
                else -> throw e
            }
        }
    }

    private fun stop(call: MethodCall, result: MethodChannel.Result) {
        val force: Boolean = call.argument<Boolean>("force") ?: false
        try {
            mobileScanner!!.stop(force)
            result.success(null)
        } catch (e: AlreadyStopped) {
            result.success(null)
        }
    }

    private fun analyzeImage(call: MethodCall, result: MethodChannel.Result) {
        analyzerResult = result

        val formats: List<Int>? = call.argument<List<Int>>("formats")
        val filePath: String = call.argument<String>("filePath")!!

        mobileScanner!!.analyzeImage(
            Uri.fromFile(File(filePath)),
            buildBarcodeScannerOptions(formats, false),
            analyzeImageSuccessCallback,
            analyzeImageErrorCallback)
    }

    private fun toggleTorch(result: MethodChannel.Result) {
        mobileScanner?.toggleTorch()
        result.success(null)
    }

    /**
     * Get the list of supported lens types on this device.
     *
     * Analyzes all available cameras and categorizes them by their focal lengths.
     * See [classifyLensType] for details on the classification thresholds.
     */
    private fun getSupportedLenses(result: MethodChannel.Result) {
        val cameraManager = activity.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val supportedLenses = mutableSetOf<Int>()

        try {
            for (cameraId in cameraManager.cameraIdList) {
                val characteristics = cameraManager.getCameraCharacteristics(cameraId)

                // Get focal lengths available for this camera
                val focalLengths = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)

                if (focalLengths != null && focalLengths.isNotEmpty()) {
                    // Use the first focal length as representative
                    val focalLength = focalLengths[0]
                    supportedLenses.add(classifyLensType(focalLength))
                }
            }

            result.success(supportedLenses.toList())
        } catch (e: Exception) {
            result.error(
                MobileScannerErrorCodes.GENERIC_ERROR,
                e.localizedMessage ?: MobileScannerErrorCodes.GENERIC_ERROR_MESSAGE,
                null
            )
        }
    }

    /**
     * Select the appropriate camera based on facing direction and lens type.
     *
     * See [classifyLensType] for details on the focal length classification thresholds.
     *
     * @param facing 0 = front, 1 = back
     * @param lensType [LENS_TYPE_NORMAL], [LENS_TYPE_WIDE], [LENS_TYPE_ZOOM], or [LENS_TYPE_ANY]
     * @return CameraSelector configured for the desired camera
     */
    private fun selectCameraUsingFacingAndLens(facing: Int, lensType: Int): CameraSelector {
        val lensFacing = if (facing == 0) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK

        // If no specific lens type is requested, return default camera for facing direction
        if (lensType == LENS_TYPE_ANY) {
            return if (facing == 0) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
        }

        // Build a camera selector that filters by both facing and lens characteristics
        return CameraSelector.Builder()
            .requireLensFacing(lensFacing)
            .addCameraFilter { cameraInfos ->
                val cameraManager = activity.getSystemService(Context.CAMERA_SERVICE) as CameraManager

                val filteredCameras = cameraInfos.filter { cameraInfo ->
                    try {
                        // Get the camera ID from CameraInfo
                        val cameraId = Camera2CameraInfo.from(cameraInfo).cameraId
                        val characteristics = cameraManager.getCameraCharacteristics(cameraId)

                        // Get focal lengths available for this camera
                        val focalLengths = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)

                        if (focalLengths == null || focalLengths.isEmpty()) {
                            // Without focal length info, we can't determine the lens type.
                            // Only include this camera if the user requested "any" lens type.
                            return@filter lensType == LENS_TYPE_ANY
                        }

                        // Use the first focal length as representative
                        val focalLength = focalLengths[0]
                        matchesLensType(focalLength, lensType)
                    } catch (e: Exception) {
                        // If we can't get characteristics, include this camera
                        true
                    }
                }

                // If filtering resulted in no cameras, return all cameras with correct facing
                // to prevent camera binding failures
                if (filteredCameras.isEmpty()) cameraInfos else filteredCameras
            }
            .build()
    }

    private fun setScale(call: MethodCall, result: MethodChannel.Result) {
        try {
            mobileScanner!!.setScale(call.arguments as Double)
            result.success(null)
        } catch (e: ZoomWhenStopped) {
            result.error(
                MobileScannerErrorCodes.SET_SCALE_WHEN_STOPPED_ERROR, MobileScannerErrorCodes.SET_SCALE_WHEN_STOPPED_ERROR_MESSAGE, null)
        } catch (e: ZoomNotInRange) {
            result.error(
                MobileScannerErrorCodes.GENERIC_ERROR, MobileScannerErrorCodes.INVALID_ZOOM_SCALE_ERROR_MESSAGE, null)
        }
    }

    private fun setZoomRatio(scale: Float) : Boolean {
        try {
            mobileScanner!!.setZoomRatio(scale.toDouble())
            return true
        } catch (e: ZoomWhenStopped) { }
        return false
    }

    private fun resetScale(result: MethodChannel.Result) {
        try {
            mobileScanner!!.resetScale()
            result.success(null)
        } catch (e: ZoomWhenStopped) {
            result.error(
                MobileScannerErrorCodes.SET_SCALE_WHEN_STOPPED_ERROR, MobileScannerErrorCodes.SET_SCALE_WHEN_STOPPED_ERROR_MESSAGE, null)
        }
    }

    private fun updateScanWindow(call: MethodCall, result: MethodChannel.Result) {
        mobileScanner?.scanWindow = call.argument<List<Float>?>("rect")

        result.success(null)
    }

    private fun buildBarcodeScannerOptions(formats: List<Int>?, autoZoom: Boolean): BarcodeScannerOptions? {
        val builder : BarcodeScannerOptions.Builder?
        if (formats == null) {
            builder = BarcodeScannerOptions.Builder()
        } else {
            val formatsList: MutableList<Int> = mutableListOf()

            for (formatValue in formats) {
                formatsList.add(BarcodeFormats.fromRawValue(formatValue).intValue)
            }

            if (formatsList.size == 1) {
                builder = BarcodeScannerOptions.Builder().setBarcodeFormats(formatsList.first())
            } else {
                builder = BarcodeScannerOptions.Builder().setBarcodeFormats(
                    formatsList.first(),
                    *formatsList.subList(1, formatsList.size).toIntArray()
                )
            }
        }

        if (autoZoom) {
            builder.setZoomSuggestionOptions(
                ZoomSuggestionOptions.Builder {
                    setZoomRatio(it)
                }.setMaxSupportedZoomRatio(getMaxZoomRatio())
                    .build())
        }

        return builder.build()
    }

    private fun getMaxZoomRatio(): Float {
        val cameraManager = activity.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        var maxZoom = 1.0F

        try {
            for (cameraId in cameraManager.cameraIdList) {
                val characteristics = cameraManager.getCameraCharacteristics(cameraId)

                val maxZoomRatio = characteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM)
                if (maxZoomRatio != null && maxZoomRatio > maxZoom) {
                    maxZoom = maxZoomRatio
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return maxZoom
    }

    private fun setFocus(call: MethodCall, result: MethodChannel.Result) {
        val dx = call.argument<Double>("dx")?.toFloat()
        val dy = call.argument<Double>("dy")?.toFloat()

        if (dx == null || dy == null || dx !in 0f..1f || dy !in 0f..1f) {
            result.error(
                MobileScannerErrorCodes.INVALID_FOCUS_POINT,
                MobileScannerErrorCodes.INVALID_FOCUS_POINT_MESSAGE,
                null
            )
            return
        }

        try {
            mobileScanner?.setFocus(dx, dy)
            result.success(null)
        } catch (e: ZoomWhenStopped) {
            result.error(
                MobileScannerErrorCodes.GENERIC_ERROR,
                "Cannot set focus when camera is stopped.",
                null
            )
        } catch (e: Exception) {
            result.error(
                MobileScannerErrorCodes.GENERIC_ERROR,
                MobileScannerErrorCodes.GENERIC_ERROR_MESSAGE,
                e.localizedMessage
            )
        }
    }

}
