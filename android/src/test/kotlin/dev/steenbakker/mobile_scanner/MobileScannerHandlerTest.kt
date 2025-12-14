package dev.steenbakker.mobile_scanner

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for the lens classification functions in [MobileScannerHandler].
 *
 * Run these tests from the command line by running `./gradlew testDebugUnitTest`
 * in the `example/android/` directory.
 */
internal class MobileScannerHandlerTest {

    // ==========================================================================
    // classifyLensType tests
    // ==========================================================================

    @Test
    fun classifyLensType_belowWideThreshold_returnsWide() {
        // Values below 4.0mm should be classified as wide
        assertEquals(MobileScannerHandler.LENS_TYPE_WIDE, MobileScannerHandler.classifyLensType(1.0f))
        assertEquals(MobileScannerHandler.LENS_TYPE_WIDE, MobileScannerHandler.classifyLensType(2.5f))
        assertEquals(MobileScannerHandler.LENS_TYPE_WIDE, MobileScannerHandler.classifyLensType(3.99f))
    }

    @Test
    fun classifyLensType_atWideThreshold_returnsNormal() {
        // Exactly 4.0mm should be classified as normal (>= 4.0)
        assertEquals(MobileScannerHandler.LENS_TYPE_NORMAL, MobileScannerHandler.classifyLensType(4.0f))
    }

    @Test
    fun classifyLensType_justAboveWideThreshold_returnsNormal() {
        assertEquals(MobileScannerHandler.LENS_TYPE_NORMAL, MobileScannerHandler.classifyLensType(4.01f))
    }

    @Test
    fun classifyLensType_inNormalRange_returnsNormal() {
        // Values between 4.0mm and 6.0mm should be classified as normal
        assertEquals(MobileScannerHandler.LENS_TYPE_NORMAL, MobileScannerHandler.classifyLensType(4.5f))
        assertEquals(MobileScannerHandler.LENS_TYPE_NORMAL, MobileScannerHandler.classifyLensType(5.0f))
        assertEquals(MobileScannerHandler.LENS_TYPE_NORMAL, MobileScannerHandler.classifyLensType(5.5f))
    }

    @Test
    fun classifyLensType_atZoomThreshold_returnsNormal() {
        // Exactly 6.0mm should be classified as normal (<= 6.0)
        assertEquals(MobileScannerHandler.LENS_TYPE_NORMAL, MobileScannerHandler.classifyLensType(6.0f))
    }

    @Test
    fun classifyLensType_justAboveZoomThreshold_returnsZoom() {
        assertEquals(MobileScannerHandler.LENS_TYPE_ZOOM, MobileScannerHandler.classifyLensType(6.01f))
    }

    @Test
    fun classifyLensType_aboveZoomThreshold_returnsZoom() {
        // Values above 6.0mm should be classified as zoom
        assertEquals(MobileScannerHandler.LENS_TYPE_ZOOM, MobileScannerHandler.classifyLensType(7.0f))
        assertEquals(MobileScannerHandler.LENS_TYPE_ZOOM, MobileScannerHandler.classifyLensType(10.0f))
        assertEquals(MobileScannerHandler.LENS_TYPE_ZOOM, MobileScannerHandler.classifyLensType(50.0f))
    }

    @Test
    fun classifyLensType_extremeSmallValue_returnsWide() {
        assertEquals(MobileScannerHandler.LENS_TYPE_WIDE, MobileScannerHandler.classifyLensType(0.1f))
        assertEquals(MobileScannerHandler.LENS_TYPE_WIDE, MobileScannerHandler.classifyLensType(0.001f))
    }

    @Test
    fun classifyLensType_extremeLargeValue_returnsZoom() {
        assertEquals(MobileScannerHandler.LENS_TYPE_ZOOM, MobileScannerHandler.classifyLensType(100.0f))
        assertEquals(MobileScannerHandler.LENS_TYPE_ZOOM, MobileScannerHandler.classifyLensType(1000.0f))
    }

    @Test
    fun classifyLensType_zeroValue_returnsWide() {
        // Zero focal length (edge case) should be classified as wide
        assertEquals(MobileScannerHandler.LENS_TYPE_WIDE, MobileScannerHandler.classifyLensType(0.0f))
    }

    @Test
    fun classifyLensType_negativeValue_returnsWide() {
        // Negative focal length (invalid, but should not crash) returns wide
        assertEquals(MobileScannerHandler.LENS_TYPE_WIDE, MobileScannerHandler.classifyLensType(-1.0f))
    }

    // ==========================================================================
    // matchesLensType tests
    // ==========================================================================

    @Test
    fun matchesLensType_anyLensType_alwaysReturnsTrue() {
        // LENS_TYPE_ANY should match any focal length
        assertTrue(MobileScannerHandler.matchesLensType(1.0f, MobileScannerHandler.LENS_TYPE_ANY))
        assertTrue(MobileScannerHandler.matchesLensType(4.0f, MobileScannerHandler.LENS_TYPE_ANY))
        assertTrue(MobileScannerHandler.matchesLensType(5.0f, MobileScannerHandler.LENS_TYPE_ANY))
        assertTrue(MobileScannerHandler.matchesLensType(6.0f, MobileScannerHandler.LENS_TYPE_ANY))
        assertTrue(MobileScannerHandler.matchesLensType(10.0f, MobileScannerHandler.LENS_TYPE_ANY))
    }

    @Test
    fun matchesLensType_wideLensType_matchesWideFocalLengths() {
        // Wide lens type should match focal lengths < 4.0mm
        assertTrue(MobileScannerHandler.matchesLensType(1.0f, MobileScannerHandler.LENS_TYPE_WIDE))
        assertTrue(MobileScannerHandler.matchesLensType(3.99f, MobileScannerHandler.LENS_TYPE_WIDE))
    }

    @Test
    fun matchesLensType_wideLensType_doesNotMatchOtherFocalLengths() {
        assertFalse(MobileScannerHandler.matchesLensType(4.0f, MobileScannerHandler.LENS_TYPE_WIDE))
        assertFalse(MobileScannerHandler.matchesLensType(5.0f, MobileScannerHandler.LENS_TYPE_WIDE))
        assertFalse(MobileScannerHandler.matchesLensType(7.0f, MobileScannerHandler.LENS_TYPE_WIDE))
    }

    @Test
    fun matchesLensType_normalLensType_matchesNormalFocalLengths() {
        // Normal lens type should match focal lengths between 4.0mm and 6.0mm
        assertTrue(MobileScannerHandler.matchesLensType(4.0f, MobileScannerHandler.LENS_TYPE_NORMAL))
        assertTrue(MobileScannerHandler.matchesLensType(5.0f, MobileScannerHandler.LENS_TYPE_NORMAL))
        assertTrue(MobileScannerHandler.matchesLensType(6.0f, MobileScannerHandler.LENS_TYPE_NORMAL))
    }

    @Test
    fun matchesLensType_normalLensType_doesNotMatchOtherFocalLengths() {
        assertFalse(MobileScannerHandler.matchesLensType(3.99f, MobileScannerHandler.LENS_TYPE_NORMAL))
        assertFalse(MobileScannerHandler.matchesLensType(6.01f, MobileScannerHandler.LENS_TYPE_NORMAL))
    }

    @Test
    fun matchesLensType_zoomLensType_matchesZoomFocalLengths() {
        // Zoom lens type should match focal lengths > 6.0mm
        assertTrue(MobileScannerHandler.matchesLensType(6.01f, MobileScannerHandler.LENS_TYPE_ZOOM))
        assertTrue(MobileScannerHandler.matchesLensType(10.0f, MobileScannerHandler.LENS_TYPE_ZOOM))
        assertTrue(MobileScannerHandler.matchesLensType(50.0f, MobileScannerHandler.LENS_TYPE_ZOOM))
    }

    @Test
    fun matchesLensType_zoomLensType_doesNotMatchOtherFocalLengths() {
        assertFalse(MobileScannerHandler.matchesLensType(3.0f, MobileScannerHandler.LENS_TYPE_ZOOM))
        assertFalse(MobileScannerHandler.matchesLensType(5.0f, MobileScannerHandler.LENS_TYPE_ZOOM))
        assertFalse(MobileScannerHandler.matchesLensType(6.0f, MobileScannerHandler.LENS_TYPE_ZOOM))
    }

    // ==========================================================================
    // Threshold constant tests
    // ==========================================================================

    @Test
    fun thresholdConstants_haveExpectedValues() {
        assertEquals(4.0f, MobileScannerHandler.FOCAL_LENGTH_WIDE_THRESHOLD)
        assertEquals(6.0f, MobileScannerHandler.FOCAL_LENGTH_ZOOM_THRESHOLD)
    }

    @Test
    fun lensTypeConstants_haveExpectedValues() {
        assertEquals(0, MobileScannerHandler.LENS_TYPE_NORMAL)
        assertEquals(1, MobileScannerHandler.LENS_TYPE_WIDE)
        assertEquals(2, MobileScannerHandler.LENS_TYPE_ZOOM)
        assertEquals(-1, MobileScannerHandler.LENS_TYPE_ANY)
    }
}
