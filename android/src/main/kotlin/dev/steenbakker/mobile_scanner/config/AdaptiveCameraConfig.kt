package dev.steenbakker.mobile_scanner.config

import android.content.Context
import android.graphics.Point
import android.os.Build
import android.util.Log
import android.util.Size
import android.view.WindowManager
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import java.util.Locale
import kotlin.math.abs
import kotlin.math.min

/**
 * 自适应相机配置：主要是根据纵横比和设备屏幕的分辨率找到与相机之间合适的相机配置；
 * 在适配、性能与体验之间找到平衡点，最终创建一个比较适合当前设备的CameraConfig。
 */
class AdaptiveCameraConfig(context: Context) {
    private var mAspectRatioStrategy: AspectRatioStrategy? = null
    private var mPreviewQuality = 0
    private var mAnalysisQuality = 0
    private var mPreviewTargetSize: Size? = null
    private var mAnalysisTargetSize: Size? = null

    init {
        initAdaptiveCameraConfig(context)
    }

    /**
     * 初始化配置；获取屏幕尺寸来动态计算，从而找到合适的预览尺寸和分析尺寸
     *
     * @param context 上下文
     */

    private fun initAdaptiveCameraConfig(context: Context) {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val width: Int
        val height: Int

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.currentWindowMetrics.bounds

            width = bounds.width()
            height = bounds.height()
        } else {
            val size = Point()
            @Suppress("DEPRECATION") windowManager.defaultDisplay.getRealSize(size)

            width = size.x
            height = size.y
        }

        Log.d(TAG, String.format(Locale.getDefault(), "displayMetrics: %dx%d", width, height))
        if (width < height) {
            val ratio = height / width.toFloat()
            mPreviewQuality = min(width.toDouble(), IMAGE_QUALITY_1080P.toDouble()).toInt()
            mAspectRatioStrategy =
                if (abs((ratio - ASPECT_RATIO_4_3).toDouble()) < abs((ratio - ASPECT_RATIO_16_9).toDouble())) {
                    AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY
                } else {
                    AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY
                }
            mPreviewTargetSize = Size(mPreviewQuality, Math.round(mPreviewQuality * ratio))
            mAnalysisQuality = if (width >= IMAGE_QUALITY_1080P) {
                IMAGE_QUALITY_1080P
            } else {
                min(width.toDouble(), IMAGE_QUALITY_720P.toDouble()).toInt()
            }
            mAnalysisTargetSize = Size(mAnalysisQuality, Math.round(mAnalysisQuality * ratio))
        } else {
            mPreviewQuality = min(
                height.toDouble(), IMAGE_QUALITY_1080P.toDouble()
            ).toInt()
            val ratio = width / height.toFloat()
            mAspectRatioStrategy =
                if (abs((ratio - ASPECT_RATIO_4_3).toDouble()) < abs((ratio - ASPECT_RATIO_16_9).toDouble())) {
                    AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY
                } else {
                    AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY
                }
            mPreviewTargetSize = Size(Math.round(mPreviewQuality * ratio), mPreviewQuality)
            mAnalysisQuality = if (height >= IMAGE_QUALITY_1080P) {
                IMAGE_QUALITY_1080P
            } else {
                min(height.toDouble(), IMAGE_QUALITY_720P.toDouble()).toInt()
            }
            mAnalysisTargetSize = Size(Math.round(mAnalysisQuality * ratio), mAnalysisQuality)
        }
    }

    fun options(builder: Preview.Builder): Preview {
        builder.setResolutionSelector(createPreviewResolutionSelector())
        return builder.build()
    }

    fun options(builder: ImageAnalysis.Builder): ImageAnalysis {
        builder.setResolutionSelector(createAnalysisResolutionSelector())
        return builder.build()
    }

    /**
     * 创建预览 分辨率选择器；根据自适应策略，创建一个合适的 [ResolutionSelector]
     *
     * @return [ResolutionSelector]
     */
    private fun createPreviewResolutionSelector(): ResolutionSelector {
        return ResolutionSelector.Builder().setAspectRatioStrategy(mAspectRatioStrategy!!)
            .setResolutionStrategy(
                ResolutionStrategy(
                    mPreviewTargetSize!!, ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                )
            ).setResolutionFilter { supportedSizes: List<Size>, _: Int ->
                Log.d(TAG, "Preview supportedSizes: $supportedSizes")
                val list: MutableList<Size> = ArrayList()
                for (supportedSize in supportedSizes) {
                    val size =
                        min(supportedSize.width.toDouble(), supportedSize.height.toDouble()).toInt()
                    if (size <= mPreviewQuality) {
                        list.add(supportedSize)
                    }
                }

                if (list.isEmpty()) {
                    supportedSizes
                } else {
                    list
                }
            }.build()
    }

    /**
     * 创建分析 分辨率选择器；根据自适应策略，创建一个合适的 [ResolutionSelector]
     *
     * @return [ResolutionSelector]
     */
    private fun createAnalysisResolutionSelector(): ResolutionSelector {
        return ResolutionSelector.Builder().setAspectRatioStrategy(mAspectRatioStrategy!!)
            .setResolutionStrategy(
                ResolutionStrategy(
                    mAnalysisTargetSize!!,
                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                )
            ).setResolutionFilter { supportedSizes: List<Size>, rotationDegrees: Int ->
                Log.d(TAG, "ImageAnalysis supportedSizes: $supportedSizes")
                val list: MutableList<Size> = ArrayList()
                for (supportedSize in supportedSizes) {
                    val size =
                        min(supportedSize.width.toDouble(), supportedSize.height.toDouble()).toInt()
                    if (size <= mAnalysisQuality) {
                        list.add(supportedSize)
                    }
                }

                if (list.isEmpty()) {
                    supportedSizes
                } else {
                    list
                }
            }.build()
    }

    companion object {
        const val TAG = "AdaptiveCameraConfig"

        /**
         * 纵横比：4:3
         */
        private const val ASPECT_RATIO_4_3 = 4.0f / 3.0f

        /**
         * 纵横比：16:9
         */
        private const val ASPECT_RATIO_16_9 = 16.0f / 9.0f

        /**
         * 1080P
         */
        private const val IMAGE_QUALITY_1080P = 1080

        /**
         * 720P
         */
        private const val IMAGE_QUALITY_720P = 720
    }
}
