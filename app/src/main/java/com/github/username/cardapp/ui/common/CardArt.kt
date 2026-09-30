package com.github.username.cardapp.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.github.username.cardapp.ui.theme.CreamFaded
import com.github.username.cardapp.ui.theme.Typography

/** Bundled art for the print [slug], with a label in place of art the APK doesn't carry. */
@Composable
fun CardArt(
    slug: String,
    contentDescription: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    crossfade: Boolean = true,
) {
    var missing by remember(slug) { mutableStateOf(false) }
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data("file:///android_asset/images/$slug.webp")
                .crossfade(crossfade)
                .build(),
            contentDescription = contentDescription,
            contentScale = contentScale,
            onSuccess = { missing = false },
            onError = { missing = true },
            modifier = Modifier.matchParentSize(),
        )
        if (missing) {
            Text(
                text = "NO ART",
                style = Typography.labelMedium.copy(color = CreamFaded, textAlign = TextAlign.Center),
                modifier = Modifier.padding(4.dp),
            )
        }
    }
}
