package io.github.halilozel1903.cropper.sample

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.halilozel1903.cropper.CropScreen
import io.github.halilozel1903.cropper.CropScreenLabels
import io.github.halilozel1903.cropper.CropShape
import io.github.halilozel1903.cropper.CropperDefaults
import io.github.halilozel1903.cropper.rememberCropState
import io.github.halilozel1903.cropper.core.AspectRatio
import io.github.halilozel1903.cropper.core.CropRect

private enum class Photo { Landscape, Avatar }

@Composable
fun SampleApp(scene: String?) {
    var photo by rememberSaveable { mutableStateOf(if (scene == "circle") Photo.Avatar else Photo.Landscape) }
    // Apply the scene once, not again after a configuration change.
    var sceneApplied by rememberSaveable { mutableStateOf(false) }
    var result by remember { mutableStateOf<ImageBitmap?>(null) }
    val landscape = remember { DemoImages.landscape() }
    val avatar = remember { DemoImages.avatar() }

    val cropped = result
    if (cropped != null) {
        ResultScreen(
            bitmap = cropped,
            photo = photo,
            onCropAgain = { result = null },
            onSwitchPhoto = {
                photo = if (photo == Photo.Landscape) Photo.Avatar else Photo.Landscape
                result = null
            },
        )
    } else key(photo) {
        val isAvatar = photo == Photo.Avatar
        val state = rememberCropState(
            aspectRatio = if (scene == "ratios" && !sceneApplied) AspectRatio.SixteenNine else null,
            shape = if (isAvatar) CropShape.Circle else CropShape.Rectangle,
        )
        if (!sceneApplied) {
            LaunchedEffect(Unit) {
                when (scene) {
                    null, "crop" -> state.setCropRegion(CropRect(0.1f, 0.12f, 0.78f, 0.9f))
                    "circle" -> state.setCropRegion(DemoImages.AvatarFace)
                }
                sceneApplied = true
            }
        }
        CropScreen(
            bitmap = if (isAvatar) avatar else landscape,
            onCrop = { result = it },
            onCancel = { state.reset() },
            state = state,
            maxOutputSize = 2048,
            labels = CropScreenLabels(title = if (isAvatar) "Profile photo" else "Crop photo"),
            colors = CropperDefaults.colors(background = Color(0xFF111014)),
        )
    }
}

@Composable
private fun ResultScreen(
    bitmap: ImageBitmap,
    photo: Photo,
    onCropAgain: () -> Unit,
    onSwitchPhoto: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Cropped", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)
        Text(
            "${bitmap.width} x ${bitmap.height} px",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                bitmap = bitmap,
                contentDescription = "The cropped image",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .clip(RoundedCornerShape(if (photo == Photo.Avatar) 0.dp else 16.dp)),
            )
        }
        Text(
            if (photo == Photo.Avatar) "Circle crops have transparent corners." else "Rotated and cropped at the original resolution.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onSwitchPhoto) {
                Text(if (photo == Photo.Avatar) "Try a landscape" else "Try an avatar")
            }
            Button(onClick = onCropAgain) { Text("Crop again") }
        }
        Spacer(Modifier.height(8.dp))
    }
}
