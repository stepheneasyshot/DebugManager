package com.stephen.debugmanager.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import com.stephen.debugmanager.ui.theme.defaultText
import com.stephen.debugmanager.ui.theme.fontPrimaryColor

@Composable
fun CenterText(
    text: String,
    style: TextStyle = defaultText,
    modifier: Modifier = Modifier,
    color: Color = fontPrimaryColor,
    alignment: Alignment = Alignment.Center
) {
    Box(
        contentAlignment = alignment,
        modifier = modifier
    ) {
        Text(
            text = text,
            style = style,
            color = color
        )
    }
}