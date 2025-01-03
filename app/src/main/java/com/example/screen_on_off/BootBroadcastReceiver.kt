package com.example.screen_on_off

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbManager
import android.os.Build

class BootBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // After boot is completed, check if any USB devices are attached
            val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
            val deviceList = usbManager.deviceList
            if (deviceList.isNotEmpty()) {
                for (device in deviceList.values) {
                    if (!usbManager.hasPermission(device)) {
                        // Request permission if it's not granted yet
                        val flag =
                        if(Build.VERSION.SDK_INT > Build.VERSION_CODES.TIRAMISU){
                            PendingIntent.FLAG_IMMUTABLE
                        }else{
                            PendingIntent.FLAG_MUTABLE
                        }
                        val permissionIntent = PendingIntent.getBroadcast(
                            context, 0, Intent("com.example.USB_PERMISSION"), flag
                        )
                        usbManager.requestPermission(device, permissionIntent)
                    }
                }
            }
            // Launch MainActivity after checking USB permissions
            val mainActivityIntent = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) // Required to launch activity from BroadcastReceiver
            }
            context.startActivity(mainActivityIntent)

        }
    }
}
