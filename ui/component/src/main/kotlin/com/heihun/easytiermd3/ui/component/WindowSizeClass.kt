package com.heihun.easytiermd3.ui.component

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration

const val EXPANDED_WIDTH_DP = 840

@Composable
fun isWindowExpandedWidth(): Boolean {
    val configuration = LocalConfiguration.current
    return configuration.screenWidthDp >= EXPANDED_WIDTH_DP
}

@Composable
fun isLandscape(): Boolean =
    LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE