package com.example.feature.media.image

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.core.database.VaultItemEntity
import com.example.core.database.VaultRepository
import com.example.core.designsystem.VaultColors
import com.example.core.security.LockState
import com.example.core.security.SessionSecurityManager
import com.example.feature.viewer.formatBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * High-Security Fullscreen Image Viewer.
 *
 * Security Requirements:
 * - Decrypts directly into memory; no disk files generated.
 * - Multi-touch zoom (pinch), pan, and double-tap zoom.
 * - Non-destructive viewer rotation (0°, 90°, 180°, 270°).
 * - Immediately invalidates bitmap and dismisses upon vault lock.
 */
@Composable
fun AdvancedImageViewerDialog(
    initialIndex: Int,
    images: List<VaultItemEntity>,
    repository: VaultRepository,
    sessionManager: SessionSecurityManager,
    onDismiss: () -> Unit,
    onShowInfo: (VaultItemEntity) -> Unit,
    onExportRequested: (VaultItemEntity) -> Unit,
    onDeleteRequested: (VaultItemEntity) -> Unit
) {
    if (images.isEmpty()) {
        onDismiss()
        return
    }

    val scope = rememberCoroutineScope()
    val lockState by sessionManager.lockState.collectAsState()

    // Immediate lock interruption guard
    LaunchedEffect(lockState) {
        if (lockState !is LockState.Unlocked) {
            onDismiss()
        }
    }

    var currentIndex by remember { mutableIntStateOf(initialIndex.coerceIn(0, images.size - 1)) }
    val currentItem = images[currentIndex]

    var imageBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var rawBitmapRef by remember { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isFavorite by remember { mutableStateOf(currentItem.isFavorite) }

    // Transformation states
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var rotationDegrees by remember { mutableFloatStateOf(0f) }
    var showHud by remember { mutableStateOf(true) }

    // Synchronize favorite status
    LaunchedEffect(currentItem.isFavorite) {
        isFavorite = currentItem.isFavorite
    }

    // Decrypt into memory on image change
    LaunchedEffect(currentItem.id) {
        isLoading = true
        scale = 1f
        offset = Offset.Zero
        rotationDegrees = 0f

        rawBitmapRef?.recycle()
        rawBitmapRef = null
        imageBitmap = null

        withContext(Dispatchers.IO) {
            try {
                val bytesResult = repository.decryptItemBytes(currentItem)
                val bytes = bytesResult.getOrNull()
                if (bytes != null && bytes.isNotEmpty()) {
                    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    rawBitmapRef = bmp
                    imageBitmap = bmp?.asImageBitmap()
                }
            } catch (_: Exception) {
                imageBitmap = null
            } finally {
                withContext(Dispatchers.Main) {
                    isLoading = false
                }
            }
        }

        repository.recordFileAccess(currentItem)
    }

    // Recycle memory on dismiss
    DisposableEffect(Unit) {
        onDispose {
            rawBitmapRef?.recycle()
            rawBitmapRef = null
            imageBitmap = null
        }
    }

    val hasPrev = currentIndex > 0
    val hasNext = currentIndex < images.size - 1

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .testTag("advanced_image_viewer")
        ) {
            // Interactive Image Canvas
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = { tapOffset ->
                                if (scale > 1.2f) {
                                    scale = 1f
                                    offset = Offset.Zero
                                } else {
                                    scale = 2.5f
                                    offset = Offset(
                                        x = (size.width / 2f - tapOffset.x) * 1.5f,
                                        y = (size.height / 2f - tapOffset.y) * 1.5f
                                    )
                                }
                            },
                            onTap = { showHud = !showHud }
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
                        modifier = Modifier.size(40.dp)
                    )
                } else {
                    imageBitmap?.let { bmp ->
                        Image(
                            bitmap = bmp,
                            contentDescription = currentItem.title,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer(
                                    scaleX = scale,
                                    scaleY = scale,
                                    translationX = offset.x,
                                    translationY = offset.y,
                                    rotationZ = rotationDegrees
                                )
                        )
                    } ?: Text(
                        text = "Unable to decode image",
                        color = VaultColors.TextSecondary,
                        fontSize = 14.sp
                    )
                }
            }

            // HUD Top & Bottom Controls
            AnimatedVisibility(
                visible = showHud,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxSize()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Top Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(CircleShape)
                            .background(VaultColors.SurfaceElevated.copy(alpha = 0.75f))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color.White
                            )
                        }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = currentItem.title,
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${currentIndex + 1} of ${images.size} • ${formatBytes(currentItem.sizeBytes)}",
                                color = VaultColors.TextSecondary,
                                fontSize = 11.sp
                            )
                        }

                        // Info Button
                        IconButton(onClick = { onShowInfo(currentItem) }) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Image Details",
                                tint = Color.White
                            )
                        }
                    }

                    // Bottom Action Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(CircleShape)
                            .background(VaultColors.SurfaceElevated.copy(alpha = 0.85f))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        // Previous Image
                        IconButton(
                            onClick = { if (hasPrev) currentIndex-- },
                            enabled = hasPrev
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Previous Image",
                                tint = if (hasPrev) Color.White else VaultColors.TextDisabled
                            )
                        }

                        // Rotate 90° Clockwise
                        IconButton(
                            onClick = {
                                rotationDegrees = (rotationDegrees + 90f) % 360f
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.RotateRight,
                                contentDescription = "Rotate 90°",
                                tint = Color.White
                            )
                        }

                        // Favorite Toggle
                        IconButton(
                            onClick = {
                                val nextFav = !isFavorite
                                isFavorite = nextFav
                                scope.launch {
                                    repository.setFavorite(currentItem, nextFav)
                                }
                            }
                        ) {
                            Icon(
                                imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = if (isFavorite) "Favorited" else "Favorite",
                                tint = if (isFavorite) VaultColors.AccentCrimson else Color.White
                            )
                        }

                        // Share / Export Button
                        IconButton(onClick = { onExportRequested(currentItem) }) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Export Image",
                                tint = Color.White
                            )
                        }

                        // Delete to Trash
                        IconButton(onClick = { onDeleteRequested(currentItem) }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete to Trash",
                                tint = VaultColors.AccentCrimson
                            )
                        }

                        // Next Image
                        IconButton(
                            onClick = { if (hasNext) currentIndex++ },
                            enabled = hasNext
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "Next Image",
                                tint = if (hasNext) Color.White else VaultColors.TextDisabled
                            )
                        }
                    }
                }
            }
        }
    }
}
