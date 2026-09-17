package com.example.feature.viewer

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.designsystem.VaultColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Secure In-Memory Image Viewer.
 *
 * Security Protocol:
 * - Decrypts bytes directly into memory; NO unencrypted image files are written to disk.
 * - Supports gesture-driven pinch zoom, pan, and double-tap zoom.
 */
@Composable
fun SecureImageViewer(
    imageBytes: ByteArray,
    onToggleFullscreen: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var imageBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(imageBytes) {
        isLoading = true
        withContext(Dispatchers.IO) {
            try {
                val bmp = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                imageBitmap = bmp?.asImageBitmap()
            } catch (_: Exception) {
                imageBitmap = null
            } finally {
                withContext(Dispatchers.Main) {
                    isLoading = false
                }
            }
        }
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxSize()
            .background(VaultColors.Canvas)
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { tapOffset ->
                        if (scale > 1.2f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            scale = 2.5f
                            // Center on tap
                            offset = Offset(
                                x = (size.width / 2f - tapOffset.x) * 1.5f,
                                y = (size.height / 2f - tapOffset.y) * 1.5f
                            )
                        }
                    },
                    onTap = { onToggleFullscreen() }
                )
            }
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(0.8f, 6f)
                    offset = if (scale > 1f) {
                        Offset(x = offset.x + pan.x, y = offset.y + pan.y)
                    } else {
                        Offset.Zero
                    }
                }
            }
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                color = VaultColors.AccentCyan,
                modifier = Modifier.size(36.dp)
            )
        } else {
            imageBitmap?.let { bmp ->
                Image(
                    bitmap = bmp,
                    contentDescription = "Decrypted secure image",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offset.x,
                            translationY = offset.y
                        )
                )
            } ?: run {
                Text(
                    text = "Unable to decode image from decrypted vault object",
                    color = VaultColors.TextSecondary,
                    fontSize = 13.sp
                )
            }
        }
    }
}
