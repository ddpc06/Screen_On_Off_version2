package com.example.screen_on_off

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.navigation.NavController
import com.example.screen_on_off.customfunctions.CustomSwitch
import com.example.screen_on_off.navigation.Routes
import com.smarttoolfactory.slider.ColorfulSlider
import com.smarttoolfactory.slider.MaterialSliderDefaults
import com.smarttoolfactory.slider.SliderBrushColor

@Composable
fun MainUI(viewModel: ServerViewModel, dbHelper: DataBase, navController: NavController) {

    val context = LocalContext.current
    val clientSockets = viewModel.clientSockets
    var isSend by remember { mutableStateOf(false) }

    // State values
    val minValueBrightness = 2f
    val maxValueBrightness = 100f
    val maxValueWarmCool = 255f
    val minValueWarmCool = 0f

    val serverReceivedMessage by viewModel.serverReceivedMessage.collectAsState()
    val usbPermission by viewModel.usb_PermissionState.collectAsState()
    val usbBrightness by viewModel.dbBrightness.collectAsState()
    val usbWarmCool by viewModel.dbWarmCool.collectAsState()
    val ledState by viewModel.ledState.collectAsState()
    val boostMode by viewModel.boostMode.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.initializeValues()
    }

    LaunchedEffect(serverReceivedMessage) {
        viewModel.processServerMessage(serverReceivedMessage)
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val screenWidth = maxWidth
        val screenHeight = maxHeight

        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // First Column
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(1f) // Takes half of the width
            ) {
                // Add content or leave empty if not needed
            }

            // Second Column
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(1f)
                    .padding(
                        start = screenWidth * 0.1f,
                        top = screenHeight * 0.1f,
                        end = 0.dp,
                        bottom = 0.dp
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Login Button
                val buttonOffsetX = screenWidth * 0.15f
                val buttonOffsetY = screenHeight * -0.25f
                Button(
                    onClick = {
                        navController.navigate(Routes.QR_Screen)
                    },
                    modifier = Modifier.offset(x = buttonOffsetX, y = buttonOffsetY)
                ) {
                    Text("Login")
                }

                // Custom Switch
                CustomSwitch(
                    checked = ledState,
                    onCheckedChange = { isChecked ->
                        val newState = if (isChecked) 1 else 0
                        val send = viewModel.sendDataToUSB(if (newState == 1) "ON" else "OFF")
                        if (send) {
                            viewModel.updateLedState(newState == 1)
                        } else {
                            Toast.makeText(context, "Failed to send data to USB!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    thumbColor = Color.White,
                    trackColorChecked = Color.Magenta,
                    trackColorUnchecked = Color.Gray,
                    thumbSize = 50.dp,
                    trackWidth = 100.dp,
                    trackHeight = 60.dp,
                    modifier = Modifier.padding(1.dp)
                )

                Spacer(modifier = Modifier.height(screenHeight * 0.05f))

                // USB Brightness Slider
                ColorfulSlider(
                    value = usbBrightness.coerceIn(minValueBrightness, maxValueBrightness),
                    thumbRadius = 20.dp,
                    trackHeight = 60.dp,
                    onValueChange = { newValue ->
                        viewModel.updateBrightness(newValue)
                        if (!ledState) {
                            viewModel.updateLedState(true)
                        }
                        if (usbPermission && !boostMode) {
                            isSend = viewModel.sendDataToUSB("Brightness${newValue.toInt()}")
                        }
                    },
                    onValueChangeFinished = {
                        if (!usbPermission) {
                            Toast.makeText(context, "Error 101!", Toast.LENGTH_SHORT).show()
                        }
                        viewModel.updateBrightnessDatabase(usbBrightness.coerceIn(minValueBrightness, maxValueBrightness))
                    },
                    valueRange = minValueBrightness..maxValueBrightness,
                    colors = MaterialSliderDefaults.materialColors(
                        inactiveTrackColor = SliderBrushColor(color = Color.LightGray),
                        disabledInactiveTrackColor = SliderBrushColor(Color.LightGray)
                    ),
                    modifier = Modifier.width(screenWidth * 0.4f),
                    enabled = !boostMode,
                    drawInactiveTrack = true
                )

                Spacer(modifier = Modifier.height(screenHeight * 0.05f))

                // USB WarmCool Slider
                ColorfulSlider(
                    value = usbWarmCool.coerceIn(minValueWarmCool, maxValueWarmCool),
                    thumbRadius = 20.dp,
                    trackHeight = 60.dp,
                    onValueChange = { newValue ->
                        viewModel.updateWarmCool(newValue)
                        if (!ledState) {
                            viewModel.updateLedState(true)
                        }
                        if (usbPermission && !boostMode) {
                            viewModel.sendDataToUSB("WarmCool${newValue.toInt()}")
                        }
                    },
                    onValueChangeFinished = {
                        if (!usbPermission) {
                            Toast.makeText(context, "Error 101!", Toast.LENGTH_SHORT).show()
                        }
                        viewModel.updateWarmCoolDatabase(usbWarmCool.coerceIn(minValueWarmCool, maxValueWarmCool))
                    },
                    valueRange = minValueWarmCool..maxValueWarmCool,
                    colors = MaterialSliderDefaults.materialColors(
                        inactiveTrackColor = SliderBrushColor(color = Color.LightGray),
                        activeTrackColor = SliderBrushColor(
                            brush = Brush.linearGradient(colors = listOf(Color.White, Color.Yellow))
                        ),
                        disabledInactiveTrackColor = SliderBrushColor(Color.LightGray)
                    ),
                    modifier = Modifier.width(screenWidth * 0.4f),
                    enabled = !boostMode,
                    drawInactiveTrack = true
                )

                Spacer(modifier = Modifier.height(screenHeight * 0.06f))

                // Boost Button
                Button(
                    onClick = {
                        if (usbPermission) {
                            viewModel.toggleBoostMode()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (!boostMode) Color.Black else Color.Yellow,
                        contentColor = if (!boostMode) Color.White else Color.Black
                    ),
                    modifier = Modifier.border(1.dp, if (!boostMode) Color.White else Color.Black, shape = RoundedCornerShape(50))
                ) {
                    Image(
                        painter = painterResource(id = if (!boostMode) R.drawable.boost else R.drawable.booston),
                        contentDescription = "Boost Image",
                        contentScale = ContentScale.FillBounds,
                        modifier = Modifier
                            .width(30.dp)
                            .height(30.dp)
                    )
                    Spacer(modifier = Modifier.width(screenWidth * 0.03f))
                    Text(text = "Boost")
                }
            }
        }
    }
}
