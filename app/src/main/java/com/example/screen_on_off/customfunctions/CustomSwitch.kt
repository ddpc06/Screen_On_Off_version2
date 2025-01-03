package com.example.screen_on_off.customfunctions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun CustomSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    thumbColor: Color = Color.White,
    trackColorChecked: Color = Color.Magenta,
    trackColorUnchecked: Color = Color.Gray,
    thumbSize: Dp = 24.dp,
    trackWidth: Dp = 48.dp,
    trackHeight: Dp = 20.dp,
    modifier: Modifier = Modifier
) {
    Box(
        contentAlignment = Alignment.CenterStart,
        modifier = modifier
            .size(width = trackWidth, height = trackHeight)
            .background(
                color = if (checked) trackColorChecked else trackColorUnchecked,
                shape = RoundedCornerShape(percent = 50)
            )
            .clickable { onCheckedChange(!checked) }
    ) {
        Box(
            modifier = Modifier
                .size(thumbSize)
                .offset(
                    x = if (checked) (trackWidth - thumbSize)-5.dp else 5.dp,
                    y = 0.dp
                )
                .background(
                    color = thumbColor,
                    shape = CircleShape
                )
        )
    }
}
