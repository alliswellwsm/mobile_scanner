package dev.steenbakker.mobile_scanner

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.hardware.SensorManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Display
import android.view.OrientationEventListener
import android.view.Surface
import android.view.WindowManager
import dev.steenbakker.mobile_scanner.utils.serialize
import io.flutter.embedding.engine.systemchannels.PlatformChannel
import io.flutter.plugin.common.EventChannel

/**
 * This class will listen to device orientation changes.
 *
 * When a new orientation is received, the registered listener will be invoked.
 */
class DeviceOrientationListener(
    private val activity: Activity,
) : OrientationEventListener(activity, SensorManager.SENSOR_DELAY_NORMAL), EventChannel.StreamHandler {

    // The event sink that handles device orientation events.
    private var deviceOrientationEventSink: EventChannel.EventSink? = null

    // The last received orientation. This is used to prevent duplicate events.
    private var lastOrientation: PlatformChannel.DeviceOrientation? = null

    override fun onListen(event: Any?, eventSink: EventChannel.EventSink?) {
        deviceOrientationEventSink = eventSink
    }

    override fun onCancel(event: Any?) {
        deviceOrientationEventSink = null
    }

    /**
     * Start listening to device orientation changes.
     */
    fun start() {
        if (canDetectOrientation()) {
            enable()
        }
    }

    /**
     * Stop listening to device orientation changes.
     */
    fun stop() {
        disable()
    }

    /**
     * Check if the system's auto-rotate setting is enabled.
     */
    private fun isAutoRotateEnabled(): Boolean {
        return try {
            Settings.System.getInt(
                activity.contentResolver,
                Settings.System.ACCELEROMETER_ROTATION,
                0
            ) == 1
        } catch (e: Exception) {
            true
        }
    }

    @Suppress("deprecation")
    private fun getDisplay(): Display {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            activity.display!!
        } else {
            (activity.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay
        }
    }

    /**
     * Gets the current user interface orientation from the display.
     * This respects the system's rotation lock setting.
     */
    private fun getUIOrientation(): PlatformChannel.DeviceOrientation {
        val rotation: Int = getDisplay().rotation
        val orientation: Int = activity.resources.configuration.orientation

        return when (orientation) {
            Configuration.ORIENTATION_PORTRAIT -> {
                if (rotation == Surface.ROTATION_0 || rotation == Surface.ROTATION_90) {
                    PlatformChannel.DeviceOrientation.PORTRAIT_UP
                } else {
                    PlatformChannel.DeviceOrientation.PORTRAIT_DOWN
                }
            }
            Configuration.ORIENTATION_LANDSCAPE -> {
                if (rotation == Surface.ROTATION_0 || rotation == Surface.ROTATION_90) {
                    PlatformChannel.DeviceOrientation.LANDSCAPE_LEFT
                } else {
                    PlatformChannel.DeviceOrientation.LANDSCAPE_RIGHT
                }
            }
            else -> PlatformChannel.DeviceOrientation.PORTRAIT_UP
        }
    }

    override fun onOrientationChanged(orientation: Int) {
        if (orientation == ORIENTATION_UNKNOWN) {
            return
        }

        val newOrientation: PlatformChannel.DeviceOrientation

        if (isAutoRotateEnabled()) {
            newOrientation = when (orientation) {
                in 45..134 -> PlatformChannel.DeviceOrientation.LANDSCAPE_RIGHT
                in 135..224 -> PlatformChannel.DeviceOrientation.PORTRAIT_DOWN
                in 225..314 -> PlatformChannel.DeviceOrientation.LANDSCAPE_LEFT
                else -> PlatformChannel.DeviceOrientation.PORTRAIT_UP
            }
        } else {
            newOrientation = getUIOrientation()
        }

        if (newOrientation != lastOrientation) {
            lastOrientation = newOrientation
            Handler(Looper.getMainLooper()).post {
                deviceOrientationEventSink?.success(newOrientation.serialize())
            }
        }
    }

    fun getOrientation(): PlatformChannel.DeviceOrientation {
        return lastOrientation ?: PlatformChannel.DeviceOrientation.PORTRAIT_UP
    }
}
