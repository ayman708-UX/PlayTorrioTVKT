package com.playtorrio.tv.ui.components

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.playtorrio.tv.R

@Composable
fun BrandWordmark(
    modifier: Modifier = Modifier,
    contentDescription: String? = "PlayTorrio TV",
    contentScale: ContentScale = ContentScale.Fit,
    alpha: Float = 1f
) {
    Image(
        painter = painterResource(id = R.drawable.playtorrio_tv),
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
        alpha = alpha
    )
}
