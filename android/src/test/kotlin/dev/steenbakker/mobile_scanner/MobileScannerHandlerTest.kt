package dev.steenbakker.mobile_scanner

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for the lens classification functions in [MobileScannerHandler].
 *
 * These tests verify the 35mm equivalent focal length classification logic.
 * Tests that require Android SDK classes (like SizeF) should be run as
 * instrumented tests on a device or emulator.
 *
 * Run these tests from the command line by running `./gradlew testDebugUnitTest`
 * in the `example/android/` directory.
 */
internal class MobileScannerHandlerTest {

    // ==========================================================================
    // classifyLensType(equivalent35mm: Int) tests
    // ==========================================================================

    @Test
    fun classifyLensType_belowWideThreshold_returnsWide() {
        // Values below 20mm 35mm-equivalent should be classified as wide (ultra-wide)
        assertEquals(MobileScannerHandler.LENS_TYPE_WIDE, MobileScannerHandler.classifyLensType(13))
        assertEquals(MobileScannerHandler.LENS_TYPE_WIDE, MobileScannerHandler.classifyLensType(16))
        assertEquals(MobileScannerHandler.LENS_TYPE_WIDE, MobileScannerHandler.classifyLensType(19))
    }

    @Test
    fun classifyLensType_atWideThreshold_returnsNormal() {
        // Exactly 20mm should be classified as normal (>= 20)
        assertEquals(MobileScannerHandler.LENS_TYPE_NORMAL, MobileScannerHandler.classifyLensType(20))
    }

    @Test
    fun classifyLensType_justAboveWideThreshold_returnsNormal() {
        assertEquals(MobileScannerHandler.LENS_TYPE_NORMAL, MobileScannerHandler.classifyLensType(21))
    }

    @Test
    fun classifyLensType_inNormalRange_returnsNormal() {
        // Values between 20mm and 35mm should be classified as normal (standard main lens)
        assertEquals(MobileScannerHandler.LENS_TYPE_NORMAL, MobileScannerHandler.classifyLensType(24))
        assertEquals(MobileScannerHandler.LENS_TYPE_NORMAL, MobileScannerHandler.classifyLensType(26))
        assertEquals(MobileScannerHandler.LENS_TYPE_NORMAL, MobileScannerHandler.classifyLensType(28))
    }

    @Test
    fun classifyLensType_atZoomThreshold_returnsNormal() {
        // Exactly 35mm should be classified as normal (<= 35)
        assertEquals(MobileScannerHandler.LENS_TYPE_NORMAL, MobileScannerHandler.classifyLensType(35))
    }

    @Test
    fun classifyLensType_justAboveZoomThreshold_returnsZoom() {
        assertEquals(MobileScannerHandler.LENS_TYPE_ZOOM, MobileScannerHandler.classifyLensType(36))
    }

    @Test
    fun classifyLensType_aboveZoomThreshold_returnsZoom() {
        // Values above 35mm should be classified as zoom (telephoto)
        assertEquals(MobileScannerHandler.LENS_TYPE_ZOOM, MobileScannerHandler.classifyLensType(50))
        assertEquals(MobileScannerHandler.LENS_TYPE_ZOOM, MobileScannerHandler.classifyLensType(75))
        assertEquals(MobileScannerHandler.LENS_TYPE_ZOOM, MobileScannerHandler.classifyLensType(120))
    }

    @Test
    fun classifyLensType_typicalUltraWide_returnsWide() {
        // ~13-16mm is typical for "0.5x" ultra-wide lenses
        assertEquals(MobileScannerHandler.LENS_TYPE_WIDE, MobileScannerHandler.classifyLensType(13))
        assertEquals(MobileScannerHandler.LENS_TYPE_WIDE, MobileScannerHandler.classifyLensType(14))
        assertEquals(MobileScannerHandler.LENS_TYPE_WIDE, MobileScannerHandler.classifyLensType(16))
    }

    @Test
    fun classifyLensType_typicalMainLens_returnsNormal() {
        // ~24-28mm is typical for "1x" main lenses
        assertEquals(MobileScannerHandler.LENS_TYPE_NORMAL, MobileScannerHandler.classifyLensType(24))
        assertEquals(MobileScannerHandler.LENS_TYPE_NORMAL, MobileScannerHandler.classifyLensType(26))
        assertEquals(MobileScannerHandler.LENS_TYPE_NORMAL, MobileScannerHandler.classifyLensType(28))
    }

    @Test
    fun classifyLensType_typicalTelephoto_returnsZoom() {
        // ~50-75mm is typical for "2x" or "3x" telephoto lenses
        assertEquals(MobileScannerHandler.LENS_TYPE_ZOOM, MobileScannerHandler.classifyLensType(50))
        assertEquals(MobileScannerHandler.LENS_TYPE_ZOOM, MobileScannerHandler.classifyLensType(70))
        assertEquals(MobileScannerHandler.LENS_TYPE_ZOOM, MobileScannerHandler.classifyLensType(75))
    }

    @Test
    fun classifyLensType_typicalPeriscope_returnsZoom() {
        // ~120mm+ is typical for "5x" or "10x" periscope lenses
        assertEquals(MobileScannerHandler.LENS_TYPE_ZOOM, MobileScannerHandler.classifyLensType(120))
        assertEquals(MobileScannerHandler.LENS_TYPE_ZOOM, MobileScannerHandler.classifyLensType(200))
        assertEquals(MobileScannerHandler.LENS_TYPE_ZOOM, MobileScannerHandler.classifyLensType(240))
    }

    @Test
    fun classifyLensType_extremeSmallValue_returnsWide() {
        assertEquals(MobileScannerHandler.LENS_TYPE_WIDE, MobileScannerHandler.classifyLensType(1))
        assertEquals(MobileScannerHandler.LENS_TYPE_WIDE, MobileScannerHandler.classifyLensType(5))
    }

    @Test
    fun classifyLensType_extremeLargeValue_returnsZoom() {
        assertEquals(MobileScannerHandler.LENS_TYPE_ZOOM, MobileScannerHandler.classifyLensType(500))
        assertEquals(MobileScannerHandler.LENS_TYPE_ZOOM, MobileScannerHandler.classifyLensType(1000))
    }

    @Test
    fun classifyLensType_zeroValue_returnsWide() {
        // Zero focal length (edge case) should be classified as wide
        assertEquals(MobileScannerHandler.LENS_TYPE_WIDE, MobileScannerHandler.classifyLensType(0))
    }

    @Test
    fun classifyLensType_negativeValue_returnsWide() {
        // Negative focal length (invalid, but should not crash) returns wide
        assertEquals(MobileScannerHandler.LENS_TYPE_WIDE, MobileScannerHandler.classifyLensType(-1))
    }

    // ==========================================================================
    // matchesLensType(equivalent35mm: Int, lensType: Int) tests
    // ==========================================================================

    @Test
    fun matchesLensType_anyLensType_alwaysReturnsTrue() {
        // LENS_TYPE_ANY should match any focal length
        assertTrue(MobileScannerHandler.matchesLensType(13, MobileScannerHandler.LENS_TYPE_ANY))
        assertTrue(MobileScannerHandler.matchesLensType(24, MobileScannerHandler.LENS_TYPE_ANY))
        assertTrue(MobileScannerHandler.matchesLensType(35, MobileScannerHandler.LENS_TYPE_ANY))
        assertTrue(MobileScannerHandler.matchesLensType(50, MobileScannerHandler.LENS_TYPE_ANY))
        assertTrue(MobileScannerHandler.matchesLensType(120, MobileScannerHandler.LENS_TYPE_ANY))
    }

    @Test
    fun matchesLensType_wideLensType_matchesWideFocalLengths() {
        // Wide lens type should match focal lengths < 20mm equivalent
        assertTrue(MobileScannerHandler.matchesLensType(13, MobileScannerHandler.LENS_TYPE_WIDE))
        assertTrue(MobileScannerHandler.matchesLensType(16, MobileScannerHandler.LENS_TYPE_WIDE))
        assertTrue(MobileScannerHandler.matchesLensType(19, MobileScannerHandler.LENS_TYPE_WIDE))
    }

    @Test
    fun matchesLensType_wideLensType_doesNotMatchOtherFocalLengths() {
        assertFalse(MobileScannerHandler.matchesLensType(20, MobileScannerHandler.LENS_TYPE_WIDE))
        assertFalse(MobileScannerHandler.matchesLensType(24, MobileScannerHandler.LENS_TYPE_WIDE))
        assertFalse(MobileScannerHandler.matchesLensType(50, MobileScannerHandler.LENS_TYPE_WIDE))
    }

    @Test
    fun matchesLensType_normalLensType_matchesNormalFocalLengths() {
        // Normal lens type should match focal lengths between 20mm and 35mm
        assertTrue(MobileScannerHandler.matchesLensType(20, MobileScannerHandler.LENS_TYPE_NORMAL))
        assertTrue(MobileScannerHandler.matchesLensType(24, MobileScannerHandler.LENS_TYPE_NORMAL))
        assertTrue(MobileScannerHandler.matchesLensType(28, MobileScannerHandler.LENS_TYPE_NORMAL))
        assertTrue(MobileScannerHandler.matchesLensType(35, MobileScannerHandler.LENS_TYPE_NORMAL))
    }

    @Test
    fun matchesLensType_normalLensType_doesNotMatchOtherFocalLengths() {
        assertFalse(MobileScannerHandler.matchesLensType(19, MobileScannerHandler.LENS_TYPE_NORMAL))
        assertFalse(MobileScannerHandler.matchesLensType(36, MobileScannerHandler.LENS_TYPE_NORMAL))
    }

    @Test
    fun matchesLensType_zoomLensType_matchesZoomFocalLengths() {
        // Zoom lens type should match focal lengths > 35mm
        assertTrue(MobileScannerHandler.matchesLensType(36, MobileScannerHandler.LENS_TYPE_ZOOM))
        assertTrue(MobileScannerHandler.matchesLensType(50, MobileScannerHandler.LENS_TYPE_ZOOM))
        assertTrue(MobileScannerHandler.matchesLensType(120, MobileScannerHandler.LENS_TYPE_ZOOM))
    }

    @Test
    fun matchesLensType_zoomLensType_doesNotMatchOtherFocalLengths() {
        assertFalse(MobileScannerHandler.matchesLensType(16, MobileScannerHandler.LENS_TYPE_ZOOM))
        assertFalse(MobileScannerHandler.matchesLensType(24, MobileScannerHandler.LENS_TYPE_ZOOM))
        assertFalse(MobileScannerHandler.matchesLensType(35, MobileScannerHandler.LENS_TYPE_ZOOM))
    }

    // ==========================================================================
    // Threshold constant tests
    // ==========================================================================

    @Test
    fun thresholdConstants_haveExpectedValues() {
        assertEquals(20, MobileScannerHandler.EQUIVALENT_35MM_WIDE_THRESHOLD)
        assertEquals(35, MobileScannerHandler.EQUIVALENT_35MM_ZOOM_THRESHOLD)
    }

    @Test
    fun fullFrameDiagonal_hasExpectedValue() {
        assertEquals(43.27f, MobileScannerHandler.FULL_FRAME_DIAGONAL_MM)
    }

    @Test
    fun lensTypeConstants_haveExpectedValues() {
        assertEquals(0, MobileScannerHandler.LENS_TYPE_NORMAL)
        assertEquals(1, MobileScannerHandler.LENS_TYPE_WIDE)
        assertEquals(2, MobileScannerHandler.LENS_TYPE_ZOOM)
        assertEquals(-1, MobileScannerHandler.LENS_TYPE_ANY)
    }
}
