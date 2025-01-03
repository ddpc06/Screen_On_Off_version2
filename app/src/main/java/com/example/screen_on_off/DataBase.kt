package com.example.screen_on_off

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException

class DataBase private constructor(context: Context) : SQLiteOpenHelper(context, "LED_TABLE", null, 9) {

    private val _authState = MutableStateFlow<Int?>(null)
    val authState: StateFlow<Int?> = _authState

    private val dbMutex = Mutex()

    init {
        _authState.value = checkAuthenticate(1)
    }

    override fun onCreate(db: SQLiteDatabase?) {
        db?.execSQL("""
            CREATE TABLE IF NOT EXISTS LED_VALUE (
                Id INTEGER PRIMARY KEY AUTOINCREMENT,
                Brightness INTEGER DEFAULT 2, 
                WarmCool INTEGER DEFAULT 0,
                AuthState INTEGER DEFAULT 0,
                LedState INTEGER DEFAULT 0
            )
        """)
        db?.execSQL("INSERT INTO LED_VALUE (Id) VALUES (1)")
    }

    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {


    }

    suspend  fun updateLedState(id: Int, ledState: Int): Boolean = dbMutex.withLock {

        withContext(Dispatchers.IO){
            Log.d( "Database", "Updating Led State: $ledState for Id: $id")
            val db = writableDatabase
            val cursor = db.rawQuery("SELECT * FROM LED_VALUE WHERE Id = ?" ,  arrayOf(id.toString()))
            var result = false

            try {
                if(cursor.moveToFirst()){
                    val values = ContentValues().apply {
                        put("LedState", ledState)
                    }
                    val rowsUpdated = db.update("LED_VALUE" , values , "Id=?" , arrayOf(id.toString()))
                    result = rowsUpdated > 0
                }else{
                    val values = ContentValues().apply {
                        put("Id" , id)
                        put("LedState", ledState)
                    }
                    result = db.insert("LED_VALUE", null, values) != -1L
                }
            }catch (e: IOException){
                Log.e("Database", "Error updating Brightness: ${e.message}", e)
            } finally {
                cursor.close()
                db.close()
            }
            Log.d("Database", "Led State Update Result: $result")
            return@withContext result
        }


    }
    suspend fun updateAuthenticate(id: Int, auth: Int): Boolean = dbMutex.withLock{

        withContext(Dispatchers.IO){
            Log.d("Database", "Updating Authenticate: $auth for Id: $id")
            val db = writableDatabase
            val cursor = db.rawQuery("SELECT * FROM LED_VALUE WHERE Id = ?", arrayOf(id.toString()))
            var result = false

            try {
                if(cursor.moveToFirst()){
                    val values = ContentValues().apply {
                        put("AuthState", auth)
                    }
                    val rowsUpdated = db.update("LED_VALUE", values, "Id=?", arrayOf(id.toString()))
                    result = rowsUpdated > 0
                }else{
                    val values = ContentValues().apply {
                        put("Id", id)
                        put("AuthState", auth)
                    }
                    result = db.insert("LED_VALUE", null, values) != -1L
                }
            }catch (e: IOException){
                Log.e("Database", "Error updating Authenticate: ${e.message}", e)
            }finally {
                cursor.close()
                db.close()
            }
            Log.d("Database", "Authenticate Update Result: $result")
            return@withContext result
        }

    }
    suspend fun updateBrightness(id: Int, brightness: Int): Boolean = dbMutex.withLock{
        withContext(Dispatchers.IO){
            Log.d("Database", "Updating Brightness: $brightness for Id: $id")
            val db = writableDatabase
            val cursor = db.rawQuery("SELECT * FROM LED_VALUE WHERE Id = ?", arrayOf(id.toString()))
            var result = false
            try {
                if (cursor.moveToFirst()) {
                    val values = ContentValues().apply {
                        put("Brightness", brightness)
                    }
                    val rowsUpdated = db.update("LED_VALUE", values, "Id=?", arrayOf(id.toString()))
                    result = rowsUpdated > 0
                } else {
                    val values = ContentValues().apply {
                        put("Id", id)
                        put("Brightness", brightness)
                    }
                    result = db.insert("LED_VALUE", null, values) != -1L
                }
            } catch (e: Exception) {
                Log.e("Database", "Error updating Brightness: ${e.message}", e)
            } finally {
                cursor.close()
                db.close()
            }
            Log.d("Database", "Brightness Update Result: $result")
            return@withContext result
        }

    }
    suspend fun updateWarmCool(id: Int, warmCool: Int): Boolean = dbMutex.withLock{
        withContext(Dispatchers.IO){
            Log.d("Database", "Updating warmCool: $warmCool for Id: $id")
            val db = writableDatabase
            val cursor = db.rawQuery("SELECT * FROM LED_VALUE WHERE Id = ?", arrayOf(id.toString()))
            var result = false

            try {
                if(cursor.moveToFirst()){
                    val values = ContentValues().apply {
                        put("WarmCool", warmCool)
                    }
                    val rowsUpdated = db.update("LED_VALUE", values, "Id=?", arrayOf(id.toString()))
                    result = rowsUpdated > 0

                }else{
                    val values = ContentValues().apply {
                        put("Id", id)
                        put("WarmCool", warmCool)
                    }
                    result = db.insert("LED_VALUE", null, values) != -1L

                }
            }catch (e: Exception) {
                Log.e("Database", "Error updating Brightness: ${e.message}", e)
            } finally {
                cursor.close()
                db.close()
            }
            Log.d("Database", "Brightness Update Result: $result")
            return@withContext result
        }


    }

    fun getLedState(id: Int) : Int? {
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT LedState FROM LED_VALUE WHERE Id = ?" , arrayOf(id.toString()))
        var ledState: Int? = null

        try {
            if(cursor.moveToFirst()){
                ledState = cursor.getInt(cursor.getColumnIndexOrThrow("LedState"))
            }
        }catch (e: IOException){
            Log.e("Database", "Error fetching Led State: ${e.message}", e)
        } finally {
            cursor.close()
           // db.close()
        }
        Log.d("Database", "Fetched Auth State: $ledState for Id: $id")
        return ledState
    }
    fun checkAuthenticate(id: Int): Int? {
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT AuthState FROM LED_VALUE WHERE Id = ?", arrayOf(id.toString()))
        var authState: Int? = null

        try {
            if(cursor.moveToFirst()){
                authState = cursor.getInt(cursor.getColumnIndexOrThrow("AuthState"))
            }
        }catch (e: Exception){
            Log.e("Database", "Error fetching Brightness: ${e.message}", e)

        } finally {
            cursor.close()
           // db.close()
        }
        Log.d("Database", "Fetched Auth State: $authState for Id: $id")
        return authState
    }
    fun getBrightness(id: Int): Int? {
        Log.d("Database", "Fetching Brightness for Id: $id")
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT Brightness FROM LED_VALUE WHERE Id = ?", arrayOf(id.toString()))
        var Brightness: Int? = null
        try {
            Brightness = if (cursor.moveToFirst()) {
                cursor.getInt(cursor.getColumnIndexOrThrow("Brightness")).coerceIn(2, 100)
            }else{
                1
            }
        } catch (e: Exception) {
            Log.e("Database", "Error fetching Brightness: ${e.message}", e)
        } finally {
            cursor.close()
            //db.close()
        }
        Log.d("Database", "Fetched Brightness: $Brightness for Id: $id")
        return Brightness
    }
    fun getWarmCool(id: Int): Int? {

        val db = readableDatabase
        val cursor = db.rawQuery("SELECT WarmCool FROM LED_VALUE WHERE Id = ?", arrayOf(id.toString()))
        var warmCool: Int? = null

        try {
            warmCool = if(cursor.moveToFirst()){
                cursor.getInt(cursor.getColumnIndexOrThrow("WarmCool")).coerceIn(0, 255)
            }else{
                0
            }
        }catch (e: Exception){
            Log.e("Database", "Error fetching WarmCool: ${e.message}", e)
        } finally {
            cursor.close()
           // db.close()
        }
        Log.d("Database", "Fetched WarmCool: $warmCool for Id: $id")
        return warmCool
    }

    fun closeDatabase() {
        synchronized(this) {
            try {
                writableDatabase.close()
            } catch (e: Exception) {
                Log.e("Database", "Error closing database: ${e.message}")
            }
        }
    }


    companion object {
        @Volatile
        private var instance: DataBase? = null

        fun getInstance(context: Context): DataBase {
            return instance ?: synchronized(this) {
                instance ?: DataBase(context).also { instance = it }
            }
        }
    }
}
