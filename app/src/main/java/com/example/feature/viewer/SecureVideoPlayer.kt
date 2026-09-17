package com.example.feature.viewer

import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.example.core.designsystem.VaultColors
import com.example.core.storage.FileShredder
import java.io.File

/**
 * Secure Video Player.
 *
 * Security Protocol:
 * - Plays video stream from private ephemeral decrypted file.
 * - On exit or lock, releases media decoder and shreds the temporary file immediately.
 */
@Composable
fun SecureVideoPlayer(
    transientVideoFile: File,
    modifier: Modifier = Modifier
) {
    DisposableEffect(transientVideoFile) {
        onDispose {
            FileShredder.shredAndPurge(transientVideoFile)
        }
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxSize()
            .background(VaultColors.Canvas)
    ) {
        AndroidView(
            factory = { context ->
                VideoView(context).apply {
                    setVideoPath(transientVideoFile.absolutePath)
                    val mediaController = MediaController(context)
                    mediaController.setAnchorView(this)
                    setMediaController(mediaController)
                    setOnPreparedListener { mp ->
                        mp.isLooping = false
                        start()
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}
