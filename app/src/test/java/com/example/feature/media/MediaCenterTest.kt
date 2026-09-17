package com.example.feature.media

import com.example.feature.media.image.AdjustmentState
import com.example.feature.media.image.CropRectRatio
import com.example.feature.media.image.ImageEditingFoundation
import com.example.feature.viewer.formatDuration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests covering Advanced Media Center business logic, metadata formatting,
 * and secure image manipulation foundations.
 */
class MediaCenterTest {

    @Test
    fun `formatDuration formats millis correctly to mm ss`() {
        assertEquals("00:00", formatDuration(0))
        assertEquals("00:05", formatDuration(5000))
        assertEquals("01:00", formatDuration(60000))
        assertEquals("03:45", formatDuration(225000))
        assertEquals("65:10", formatDuration(3910000))
    }

    @Test
    fun `ImageMetadataInfo aspectRatio computes valid proportions`() {
        val square = ImageMetadataInfo(width = 1080, height = 1080)
        assertEquals("1:1", square.aspectRatio)

        val landscape = ImageMetadataInfo(width = 1920, height = 1080)
        assertEquals("16:9", landscape.aspectRatio)

        val portrait = ImageMetadataInfo(width = 1080, height = 1920)
        assertEquals("9:16", portrait.aspectRatio)

        val photo = ImageMetadataInfo(width = 4000, height = 3000)
        assertEquals("4:3", photo.aspectRatio)

        val invalid = ImageMetadataInfo(width = 0, height = 0)
        assertNull(invalid.aspectRatio)
    }

    @Test
    fun `CropRectRatio calculation returns expected geometric ratios`() {
        val free = CropRectRatio.FREE.getRatio(100f, 200f)
        assertEquals(0.5f, free, 0.01f)

        val square = CropRectRatio.SQUARE_1_1.getRatio(100f, 200f)
        assertEquals(1.0f, square, 0.01f)

        val fourThree = CropRectRatio.RATIO_4_3.getRatio(100f, 200f)
        assertEquals(4f / 3f, fourThree, 0.01f)

        val sixteenNine = CropRectRatio.RATIO_16_9.getRatio(100f, 200f)
        assertEquals(16f / 9f, sixteenNine, 0.01f)
    }

    @Test
    fun `ImageEditingFoundation rotate90Degrees advances orientation correctly`() {
        assertEquals(90, ImageEditingFoundation.rotate90Degrees(0))
        assertEquals(180, ImageEditingFoundation.rotate90Degrees(90))
        assertEquals(270, ImageEditingFoundation.rotate90Degrees(180))
        assertEquals(0, ImageEditingFoundation.rotate90Degrees(270))
        assertEquals(90, ImageEditingFoundation.rotate90Degrees(360))
    }

    @Test
    fun `AdjustmentState resets to default values`() {
        val state = AdjustmentState(brightness = 0.5f, contrast = 1.8f, saturation = 0.2f)
        val reset = state.reset()

        assertEquals(0.0f, reset.brightness, 0.001f)
        assertEquals(1.0f, reset.contrast, 0.001f)
        assertEquals(1.0f, reset.saturation, 0.001f)
    }

    @Test
    fun `Media Category tabs display names are populated`() {
        assertEquals("Overview", MediaCategoryTab.OVERVIEW.displayName)
        assertEquals("Images", MediaCategoryTab.IMAGES.displayName)
        assertEquals("Videos", MediaCategoryTab.VIDEOS.displayName)
        assertEquals("Audio", MediaCategoryTab.AUDIO.displayName)
    }
}
