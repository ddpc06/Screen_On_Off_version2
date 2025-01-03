package com.example.screen_on_off.navigation

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.provider.Settings
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.screen_on_off.DataBase
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

class navigationViewModel(private val context: Context , private val dbHelper: DataBase): ViewModel() {

    private val _android_ID = MutableStateFlow("None")
    val android_ID : StateFlow<String> = _android_ID

    private val _QR_Bitmap = MutableStateFlow<Bitmap?>(null)
    val Qr_Bitmap : StateFlow<Bitmap?> = _QR_Bitmap

    init {

        try {
            getAndroidID()
            generateQrCode(_android_ID.value)
        } catch (e: IOException){
            Log.e("ERROR" , "${e.message}")
        }

    }

    @SuppressLint("HardwareIds")
    fun getAndroidID(){
        _android_ID.value = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        Log.i("AndroidID" , _android_ID.value)
    }

    private fun generateQrCode(androidID: String) {
        val size = 512
        val hints = hashMapOf<EncodeHintType, Int>().also { it[EncodeHintType.MARGIN] = 1 }
        val bits = QRCodeWriter().encode(androidID, BarcodeFormat.QR_CODE, size, size, hints)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565).also {
            for (x in 0 until size) {
                for (y in 0 until size) {
                    it.setPixel(x, y, if (bits[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
                }
            }
        }
        _QR_Bitmap.value = bitmap
    }

    fun logout() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val result = dbHelper.updateAuthenticate(1, 0)
                if (result) {
                    withContext(Dispatchers.Main) {
                        // Perform any UI updates here, e.g., navigate to login screen
                        Log.d("Logout", "Successfully logged out")
                    }
                } else {
                    Log.e("Logout", "Failed to update authentication state")
                }
            } catch (e: Exception) {
                Log.e("Logout", "Error during logout: ${e.message}", e)
            }
        }
    }

}