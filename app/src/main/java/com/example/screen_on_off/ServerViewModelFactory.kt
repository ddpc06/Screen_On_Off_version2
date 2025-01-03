package com.example.screen_on_off

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class ServerViewModelFactory(private val context: Context ,private val dataBase: DataBase) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ServerViewModel::class.java)) {
            requireNotNull(context) { "Context must not be null" }
            return ServerViewModel(context , dataBase) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
