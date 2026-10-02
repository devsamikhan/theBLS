package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R

/**
 * Minimalist Institutional Emblem for Bright Light School (BLS).
 * Formatted with balanced squircle geometry, breathing margin, and hairline border.
 */
@Composable
fun BLSLogo(
    modifier: Modifier = Modifier,
    scale: Float = 1.0f,
    customSize: Dp? = null
) {
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp
    
    // Automatically constrain logo on compact or landscape phones
    val maxHeightConstraint = (screenHeight * 0.18f).coerceAtLeast(36.dp)
    val defaultBaseSize = (120 * scale).dp
    val effectiveSize = (customSize ?: defaultBaseSize).coerceAtMost(maxHeightConstraint)

    Surface(
        modifier = modifier
            .size(effectiveSize)
            .testTag("bls_logo_view"),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        shadowElevation = 0.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(6.dp),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.bls_logo),
                contentDescription = "BLS Official School Logo",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp))
            )
        }
    }
}
