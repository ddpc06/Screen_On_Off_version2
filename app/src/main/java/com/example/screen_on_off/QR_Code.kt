package com.example.screen_on_off

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.screen_on_off.navigation.navigationViewModel

@Composable
fun QR_Code(navigationViewModel: navigationViewModel) {
    val qrImage by navigationViewModel.Qr_Bitmap.collectAsState()

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize()
    ) {
        // Get the screen's dimensions
        val screenWidth = maxWidth
        val screenHeight = maxHeight

        Row(
            modifier = Modifier.fillMaxSize(),
        ) {
            // Empty Column to occupy half the width
            Column(modifier = Modifier.fillMaxHeight().weight(1f)){

            }

            // QR Code Column
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(1f)
                    .offset(
                        x = screenWidth * 0.1f, // Offset 10% of screen width
                        y = screenHeight * 0.2f  // Offset 20% of screen height
                    ),
            ) {
                Box(
                    modifier = Modifier
                        .width(screenWidth * 0.3f) // 30% of screen width
                        .height(screenWidth * 0.3f) // Keep it square
                ) {
                    qrImage?.let {
                        Image(
                            bitmap = it.asImageBitmap(),
                            contentDescription = "QR Code",
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                // Dynamic text below the QR code
                Text(
                    "Scan with Mobile App",
                    modifier = Modifier.padding(top = screenHeight * 0.05f), // 5% of screen height for spacing
                    fontSize = (screenWidth * 0.04f).value.sp, // Font size relative to screen width
                    color = Color.White
                )
            }
        }
    }
}
