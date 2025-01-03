package com.example.screen_on_off

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.screen_on_off.navigation.MyNavigation
import com.example.screen_on_off.navigation.navigationViewModel
import com.example.screen_on_off.ui.theme.Screen_ON_OFFTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var factory: ServerViewModelFactory
    private lateinit var serverViewModel: ServerViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Force the app to use dark mode regardless of the system setting
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)

        // Access the DataBase instance using getInstance(context)
        val dbHelper = DataBase.getInstance(this)
        factory = ServerViewModelFactory(applicationContext, dbHelper)
        serverViewModel = ViewModelProvider(this, factory)[ServerViewModel::class.java]

        // Navigation ViewModel
        val navigationViewModel = navigationViewModel(this, dbHelper)


        setContent {
            Screen_ON_OFFTheme {
                val isLedOn by serverViewModel.ledState.collectAsState()
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                ) {
                    // Background Image
                    Image(
                        painter = painterResource(id = if (isLedOn) R.drawable.ic_launcher_background else R.drawable.backgroundimagedark),
                        contentDescription = "Background Image",
                        contentScale = ContentScale.FillBounds,
                        modifier = Modifier.fillMaxSize()
                    )

                    // Main Surface for UI
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = Color.Transparent // Set transparent to allow background image to show
                    ) {
                        MyNavigation(
                            context = this@MainActivity,
                            navigationViewModel = navigationViewModel,
                            serverViewModel = serverViewModel,
                            dbHelper = dbHelper
                        )
                    }
                }
            }
        }

        // Register the USB and WiFi Receivers
        if (android.os.Build.VERSION.SDK_INT > android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(serverViewModel.usbReceiver, serverViewModel.intentFilterUSB, RECEIVER_NOT_EXPORTED)
            registerReceiver(serverViewModel.wifiReceiver, serverViewModel.intentFilterWifi, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(serverViewModel.usbReceiver, serverViewModel.intentFilterUSB)
            registerReceiver(serverViewModel.wifiReceiver, serverViewModel.intentFilterWifi)
        }
    }

    // Clean up resources
    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(serverViewModel.usbReceiver)
            unregisterReceiver(serverViewModel.wifiReceiver)
        } catch (e: IllegalArgumentException) {
            Log.e("MainActivity", "Receiver not registered: ${e.message}")
        }
        serverViewModel.cleanup()
        serverViewModel.usbDisconnect()
    }

    // Resume Function
    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            delay(2000) // Wait for 2 seconds
            if (!serverViewModel.usb_PermissionState.value) {
                serverViewModel.startUSBConnect()
            }
        }
    }
}
