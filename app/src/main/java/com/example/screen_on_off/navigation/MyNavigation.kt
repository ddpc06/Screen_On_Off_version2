package com.example.screen_on_off.navigation

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.screen_on_off.DataBase
import com.example.screen_on_off.MainUI
import com.example.screen_on_off.QR_Code
import com.example.screen_on_off.ServerViewModel

@Composable
fun MyNavigation(context: Context, navigationViewModel: navigationViewModel, serverViewModel: ServerViewModel, dbHelper: DataBase) {

    val navController = rememberNavController()

    val loginState by serverViewModel.dbLogin.collectAsState()


    NavHost(navController = navController, startDestination =  Routes.Home_Screen) {


        composable(Routes.Home_Screen) {
            MainUI(serverViewModel, dbHelper , navController)
        }

        composable(Routes.QR_Screen) {
            QR_Code(navigationViewModel)
        }
    }

    LaunchedEffect(loginState) {
        if (loginState) {
            navController.navigate(Routes.Home_Screen) {
                popUpTo(Routes.QR_Screen) { inclusive = true }
            }
        }
    }
}

