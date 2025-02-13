package com.stephen.debugmanager.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.stephen.debugmanager.ui.theme.darkdevidelineColor
import com.stephen.debugmanager.ui.theme.lightDevidelineColor

@Composable
fun DarkDivider(modifier: Modifier = Modifier) {
    Spacer(modifier.background(darkdevidelineColor))
}

@Composable
fun LightDivider(modifier: Modifier = Modifier) {
    Spacer(modifier.background(lightDevidelineColor))
}