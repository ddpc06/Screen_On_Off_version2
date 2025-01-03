package com.example.screen_on_off

import android.app.PendingIntent
import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.media.AudioManager
import android.net.NetworkInfo
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.felhr.usbserial.UsbSerialDevice
import com.felhr.usbserial.UsbSerialInterface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.IOException
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.ConcurrentHashMap

class ServerViewModel(private val context: Context, private val dbHelper: DataBase) : ViewModel() {


    private var serverSocket: ServerSocket? = null
    private var registrationListener: NsdManager.RegistrationListener? = null
    private val handler = Handler(Looper.getMainLooper())
    // private var connectionCheckRunnable: Runnable? = null
    val ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION"
    private var ClientSocket: Socket? = null // Store the client socket reference
    val clientSockets = ConcurrentHashMap<String, Socket>()


    // Server variable
    private val _server_connectionStatus = MutableStateFlow("Starting server...")
    val serverConnectionStatus: StateFlow<String> = _server_connectionStatus
    private val _server_isConnection = MutableStateFlow(false)
    val isServerConnection: StateFlow<Boolean> = _server_isConnection

    // Audio Manager
    val audioManager: AudioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    // NSD Manager for network
    private val nsdManager: NsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    // Power Manager instance
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    // USB Manager instance
    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    // device manager instance
    private val deviceManager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    // Server receive SMS
    private val _server_ReceivedMessage = MutableStateFlow("Waiting for message...")
    val serverReceivedMessage: StateFlow<String> = _server_ReceivedMessage

    // intent filter for wifi
    val intentFilterWifi = IntentFilter().apply {
        addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
        addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION)
    }
    // intent filter usb
    val intentFilterUSB = IntentFilter().apply {
        if (!ACTION_USB_PERMISSION.isNullOrEmpty()) {
            addAction(ACTION_USB_PERMISSION)
        } else {
            Log.e("ServerViewModel", "ACTION_USB_PERMISSION is null or empty")
        }
        addAction(UsbManager.ACTION_USB_ACCESSORY_ATTACHED)
        addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
        addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        addAction(Intent.ACTION_SCREEN_ON)
        addAction(Intent.ACTION_SCREEN_OFF)
    }

    // USB- Related Variable :
    private var m_device: UsbDevice? = null
    private var m_Connection: android.hardware.usb.UsbDeviceConnection? = null
    private var m_serial: UsbSerialDevice? = null

    private val _usb_ConnectionStatus = MutableStateFlow("Disconnected")
    val usb_ConnectionStatus : StateFlow<String> = _usb_ConnectionStatus

    private val _usb_PermissionState = MutableStateFlow(false)
    val usb_PermissionState : StateFlow<Boolean> = _usb_PermissionState

    private val _usb_lastDataSend = MutableStateFlow("No data Send")
    val usb_lastDataSend : StateFlow<String> = _usb_lastDataSend

    private val _usb_receiveData = MutableStateFlow("No data receive")
    val usb_receiveData : StateFlow<String> = _usb_receiveData

    private val _isQR_Scan = MutableStateFlow(false)
    val isQR_Scan : StateFlow<Boolean> = _isQR_Scan

    private val _isDisplay = MutableStateFlow(false)
    val isDisplay : StateFlow<Boolean> = _isDisplay

    private val _dbBrightness = MutableStateFlow(0f)
    val dbBrightness : StateFlow<Float> = _dbBrightness

    private val _dbWarmCool = MutableStateFlow(0f)
    val dbWarmCool : StateFlow<Float> = _dbWarmCool

    private val _ledState = MutableStateFlow(false)
    val ledState : StateFlow<Boolean> = _ledState

    private val _dbLogin = MutableStateFlow(false)
    val dbLogin : StateFlow<Boolean> = _dbLogin

    private val _isLedOn = MutableStateFlow(false)
    val isLedOn : StateFlow<Boolean> = _isLedOn

    private val _boostMode = MutableStateFlow(false)
    val boostMode: StateFlow<Boolean> = _boostMode

    private val TARGET_VENDOR_ID = 12346
    private val TARGET_PRODUCT_ID = 4097


    // usb receiver
    val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                // when permission granted it will open usb device and can start communication
                ACTION_USB_PERMISSION -> {
                    val granted: Boolean = intent.extras!!.getBoolean(UsbManager.EXTRA_PERMISSION_GRANTED)
                    if (granted) {
                        m_Connection = usbManager.openDevice(m_device) // creating connection
                        m_serial = UsbSerialDevice.createUsbSerialDevice(m_device, m_Connection) // Creating Serial
                        if (m_serial != null && m_serial!!.open()) {
                            m_serial!!.setBaudRate(500000) // set Baud Rate
                            m_serial!!.setDataBits(UsbSerialInterface.DATA_BITS_8) // set bits
                            m_serial!!.setParity(UsbSerialInterface.PARITY_NONE) // set priority
                            m_serial!!.setFlowControl(UsbSerialInterface.FLOW_CONTROL_OFF)

                            // Register the read callback for receiving data
                            m_serial!!.read(mCallback)

                            _usb_PermissionState.value = true
                        } else {
                            Log.i("Serial", "Serial port Not Open")
                        }
                    } else {
                        _usb_PermissionState.value = false
                    }
                }
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> startUSBConnect() // USB Attach
                UsbManager.ACTION_USB_DEVICE_DETACHED -> usbDisconnect()  // USB Detached
                // checking display is on
            /*    Intent.ACTION_SCREEN_ON -> {
                    if (!_isDisplay.value) {
                        sendDataToUSB("Display") // sending Data to Microcontroller that display is on
                        _isDisplay.value = true // Mark that the data has been sent
                    }
                }
                // when display is off
                Intent.ACTION_SCREEN_OFF -> {
                    _isDisplay.value = false // Reset the flag when the screen is turned off
                } */
            }
        }

    }
    // usb data receive
    private val mCallback = UsbSerialInterface.UsbReadCallback{ data->
        // store the receive data
        val receivedData = String(data)
        // check receive data or sending data
        if (receivedData.contains("ACK")) {
            Log.i("Receive", "Data acknowledgment received: $receivedData")

        } else {
            if (receivedData.isNotEmpty()) {
                // Update the UI state for received data
                _usb_receiveData.value = "Received data: $receivedData"

                // Use viewModelScope for coroutine operations
                viewModelScope.launch(Dispatchers.IO) {
                    when (receivedData) {
                        "OFF", "FF" -> {
                            val led = dbHelper.updateLedState(1, 0)
                            if (led) {
                                withContext(Dispatchers.Main) {
                                    _ledState.value = false
                                }
                            }
                        }
                        "ON" -> {
                            val led = dbHelper.updateLedState(1, 1)
                            if (led) {
                                withContext(Dispatchers.Main) {
                                    _ledState.value = true
                                }
                            }
                        }
                    }
                }
            }



        }

    }
    // wifi receiver wifi state change
    val wifiReceiver = object: BroadcastReceiver(){
        override fun onReceive(context: Context?, intent: Intent?) {
            when(intent?.action){
                WifiManager.WIFI_STATE_CHANGED_ACTION->{
                    val wifiState = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE , WifiManager.WIFI_STATE_UNKNOWN)
                    when(wifiState){
                        WifiManager.WIFI_STATE_ENABLED->{
                            Log.d("Wifi" , "ON")
                        }
                        WifiManager.WIFI_STATE_DISABLED->{
                            Log.d("Wifi", "OFF")
                            // Server UnRegister
                            stopRegister()
                        }

                    }
                }

                WifiManager.NETWORK_STATE_CHANGED_ACTION->{
                    val wifiInfo = intent.getParcelableExtra<NetworkInfo>(WifiManager.EXTRA_NETWORK_INFO)
                    // Unregister server


                    if(wifiInfo?.isConnected == true){

                        resetServer()
                        handler.postDelayed({
                            initializeServerSocket()
                        }, 700) // Keep delay short but enough to ensure cleanup
                    }
                }

            }
        }

    }

    init {
        try {

            initializeValues()
            initializeServerSocket()
        } catch (e: Exception) {
            Log.e("ServerViewModel", "Initialization failed: ${e.message}", e)
        }

    }
    // Server Functions :----------
    private fun initializeServerSocket() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Reset any existing server state
                resetServer()

                serverSocket = ServerSocket(0).apply {
                    val localPort = this.localPort
                    registerService(localPort) // Register NSD service with the obtained port
                    Log.d("ServerViewModel", "ServerSocket initialized on port: $localPort")
                }

                // Listen for incoming connections
                serverSocket?.soTimeout = 0 // Set a 5-second timeout

                while (true) {
                    try {
                        val clientSocket = serverSocket!!.accept()
                        Log.d("ServerViewModel", "Client connected: ${clientSocket.inetAddress.hostAddress}")
                        handleClientConnection(clientSocket)
                    } catch (e: SocketTimeoutException) {
                        Log.d("ServerViewModel", "No connections received, continuing...")
                        // Optionally, perform additional checks or tasks
                    } catch (e: IOException) {
                        Log.e("ServerViewModel", "Error in server loop: ${e.message}", e)
                        break
                    }
                }

            } catch (e: IOException) {
                Log.e("ServerViewModel", "Error initializing ServerSocket: ${e.message}", e)
                _server_connectionStatus.value = "Error initializing server: ${e.message}"
            }
        }
    }
    // Start server registration
    private fun registerService(port: Int) {

        val serviceInfo = NsdServiceInfo().apply {
            serviceName = "FloveIt" // Make sure this is correctly set
            serviceType = "_http._tcp."   // Make sure this is correctly set
            this.port = port
        }

        registrationListener = object : NsdManager.RegistrationListener {
            // if server register successful
            override fun onServiceRegistered(nsdServiceInfo: NsdServiceInfo) {
                _server_connectionStatus.value = "Service registered: ${nsdServiceInfo.serviceName}"
                Log.i("ServerViewModel", "Service registered: ${nsdServiceInfo.serviceName}")
                _server_isConnection.value = true
            }
            // if server register Failed
            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                _server_connectionStatus.value = "Registration failed: $errorCode"
                _server_isConnection.value = false
                Log.d("ServerViewModel", "Registration failed: $errorCode")
            }
            // if server unregister successful
            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {
                _server_connectionStatus.value = "Service unregistered"
                _server_isConnection.value = false
                Log.d("ServerViewModel", "Service unregistered")
            }
            // if server unregister Failed
            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                _server_connectionStatus.value = "Unregistration failed: $errorCode"
                Log.d("ServerViewModel", "Unregistration failed: $errorCode")
            }
        }

        try {
            // start the Server registration
            nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
        } catch (e: IllegalArgumentException) {
            e.printStackTrace()
            _server_connectionStatus.value = "Service registration failed due to missing parameters"
        }
    }
    // Server register stop
    fun stopRegister() {
        try {
            // Check if the registrationListener is not null before attempting to unregister
            registrationListener?.let {
                nsdManager.unregisterService(it) //  unregister of server
                _server_connectionStatus.value = "Service unregistered"
                Log.i("ServerViewModel", "Service unregistered successfully")
            } ?: Log.e("ServerViewModel", "Unregister failed: Registration listener is null")
        } catch (e: Exception) {
            Log.e("ServerViewModel", "Unregister failed with error: ${e.message}", e)
        }
    }

    // Client Connection handler
    private  fun handleClientConnection(clientSocket: Socket) {
        viewModelScope.launch(Dispatchers.IO) {

            val clientId = clientSocket.inetAddress.hostAddress ?: "unknown_${System.currentTimeMillis()}"
            clientSockets[clientId] = clientSocket // Add client to the map
            Log.d("ServerViewModel", "New client connected: $clientId")

            ClientSocket = clientSocket // Store the active client
            try {
                clientSocket.keepAlive = true // Enable Keep-Alive

                val reader = clientSocket.getInputStream().bufferedReader()
                val writer = clientSocket.getOutputStream().bufferedWriter()
                val data = mapOf(
                    "ServerBrightness" to _dbBrightness.value,
                    "WarmCool" to _dbWarmCool.value,
                    "LoggedIn" to _dbLogin.value ,
                    "LedState" to _ledState.value
                )

                Log.d("ServerViewModel", "Handling new client connection: ${clientSocket.inetAddress}")

                sendDataToClient(clientId ,data)

                // Keep the connection alive for continuous communication
                while (!clientSocket.isClosed) {
                    val message = reader.readLine() ?: break // Break on null or empty line
                    if (message.isNotBlank()) {
                        Log.d("ServerViewModel", "Message received: $message")
                        withContext(Dispatchers.Main) {
                            _server_ReceivedMessage.value = message
                            processServerMessage(message)
                        }
                        handleClientMessage(clientId, message)

                    } else {
                        Log.w("ServerViewModel", "Received empty or null message from client.")
                        break
                    }
                }
            } catch (e: IOException) {
                Log.e("ServerViewModel", "Error reading from client: ${e.message}", e)
            } finally {
                try {
                    clientSocket.close() // Clean up when the client disconnects
                    ClientSocket = null
                } catch (e: IOException) {
                    Log.e("ServerViewModel", "Error closing client socket: ${e.message}", e)
                }
            }
        }
    }

    fun processServerMessage(message: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                when {
                    message.startsWith("Brightness") && !_boostMode.value -> {
                        withContext(Dispatchers.Main){
                            val brightness = message.substring(10).toFloatOrNull()?.coerceIn(0f, 100f) ?: return@withContext
                            val sendUSB =  sendDataToUSB("Brightness${brightness.toInt()}")

                            updateBrightness(brightness)
                            updateBrightnessDatabase(brightness)
                            if(sendUSB && !_ledState.value){
                                _ledState.value = true
                            }
                        }

                    }
                    message.startsWith("WarmCool") && !_boostMode.value -> {
                        withContext(Dispatchers.Main) {
                            val warmCool = message.substring(8).toFloatOrNull()?.coerceIn(0f, 255f)
                                ?: return@withContext
                            val sendUSB =  sendDataToUSB("WarmCool${warmCool.toInt()}")
                            updateWarmCool(warmCool)
                            updateWarmCoolDatabase(warmCool)
                            if(sendUSB && !_ledState.value){
                                _ledState.value = true
                            }
                        }
                    }
                    message == "OFF" -> {
                        withContext(Dispatchers.Main) {
                        val sendUSB = sendDataToUSB("OFF")
                            if(sendUSB && _ledState.value){
                                updateLedState(false)
                            }

                        }
                    }
                    message == "ON" -> {
                        withContext(Dispatchers.Main){
                        val sendUSB =  sendDataToUSB("ON")
                            if(sendUSB && !_ledState.value){
                                updateLedState(true)
                            }

                        }
                    }

                    message == "BoostMode" ->{
                        withContext(Dispatchers.Main){
                            val sendUSB =  sendDataToUSB("BoostMode")
                            if(sendUSB && !_boostMode.value){
                               _boostMode.value = true
                                _ledState.value = true
                            }

                        }
                    }

                    message == "BoostModeOFF" ->{
                        withContext(Dispatchers.Main){
                            val sendUSB =  sendDataToUSB("BoostModeOFF")
                            if(sendUSB && _boostMode.value){
                                _boostMode.value = false
                                if(_ledState.value){
                                    _ledState.value = true
                                }
                            }

                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("ServerViewModel", "Error processing server message: ${e.message}", e)
            }
        }
    }

    private fun handleClientMessage(clientID: String ,data : String){

        if(data.isEmpty()){
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {

                if(data.startsWith("Authenticate")){
                  val send =  dbHelper.updateAuthenticate(1 , 1)
                    if(send){
                        _dbLogin.value = true
                        val androidID = data.substring(12 , data.length)
                        Log.d("Get Android ID From Client" , androidID)
                        sendDataToClient(clientID ,androidID)
                    }
                }else if(data.startsWith("Unauthenticated")){
                    val send =  dbHelper.updateAuthenticate(1 , 0)
                    if(send){
                        _dbLogin.value = false
                        sendDataToClient(clientID ,"Unauthenticated")
                    }
                }

            } catch ( e: IOException){
                Log.e("ServerViewModel", "Error handling client message: ${e.message}", e)
            }
        }
    }
    // Function to handle sending data to the client
    // robust methode to send

    fun sendDataToClient(clientId: String, data: String) {
        viewModelScope.launch(Dispatchers.IO) {
            clientSockets[clientId]?.takeIf { !it.isClosed && it.isConnected }?.let { socket ->
                try {
                    val writer = socket.getOutputStream().bufferedWriter()
                    writer.write(data)
                    writer.newLine()
                    writer.flush()
                    Log.d("ServerViewModel", "Data sent to $clientId: $data")
                } catch (e: IOException) {
                    Log.e("ServerViewModel", "Error sending data to $clientId: ${e.message}", e)
                }
            } ?: Log.w("ServerViewModel", "Client $clientId not connected.")
        }
    }

   private fun sendDataToClient(clientId: String,data: Map<String, Any>) {
        viewModelScope.launch(Dispatchers.IO) {
            try {

                val formattedData = data.entries.joinToString(separator = "&") { "${it.key}=${it.value}" }
                clientSockets[clientId]?.takeIf { !it.isClosed && it.isConnected }?.let { socket ->
                    try {
                        val writer = socket.getOutputStream().bufferedWriter()
                        writer.write(formattedData)
                        writer.newLine()
                        writer.flush()
                        Log.d("ServerViewModel", "Data sent to $clientId: $data")
                    } catch (e: IOException) {
                        Log.e("ServerViewModel", "Error sending data to $clientId: ${e.message}", e)
                    }
                } ?: Log.w("ServerViewModel", "Client $clientId not connected.")

            } catch (e : IOException){
                Log.e("ERROR" , "${e.message}")
            }

        }
    }

    // Reset the Server
    private fun resetServer() {
        try {
            serverSocket?.close() // Close any existing socket
            stopRegister() // Unregister the NSD service
            serverSocket = null
            _server_connectionStatus.value = "Server reset complete."
            Log.d("ServerViewModel", "Server reset complete.")
        } catch (e: Exception) {
            Log.e("ServerViewModel", "Error resetting server: ${e.message}", e)
        }
    }
    // Power manager to wake UP Device
    private fun wakeUpDevice(){
        val wakeLock = powerManager.newWakeLock(
            PowerManager.FULL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE,
            "MyApp::MyWakelockTag")
        wakeLock.acquire(10 * 60 * 1000L)
        wakeLock.release()
    }
    // USB Functions :---------
    // USB Connection Function
    fun startUSBConnect(){
        val usbDevices: HashMap<String, UsbDevice>? = usbManager.deviceList // get all available devices
        if (!usbDevices.isNullOrEmpty()) {
            var keep = true
            usbDevices.forEach { entry ->
                m_device = entry.value // assign device
                val deviceVendorId: Int? = m_device?.vendorId // device vendor id
                val deviceProductId: Int? = m_device?.productId // device product id
                Log.i("VendorID: ", "$deviceVendorId")
                // checking is device vendor id and product id same
                if (deviceVendorId == TARGET_VENDOR_ID && deviceProductId == TARGET_PRODUCT_ID) {
                    //checking android version
                    val flag = if(Build.VERSION.SDK_INT > Build.VERSION_CODES.TIRAMISU){
                        PendingIntent.FLAG_IMMUTABLE
                    }else{
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                    }
                    // create a intent for USB permission
                    val intent: PendingIntent = PendingIntent.getBroadcast(context, 0, Intent(ACTION_USB_PERMISSION), flag)
                    usbManager.requestPermission(m_device, intent) // request for device permission
                    _usb_ConnectionStatus.value = "Device connected with Vendor ID: $deviceVendorId"
                    keep = false
                } else {
                    m_Connection = null
                    m_device = null
                    _usb_ConnectionStatus.value = "Unable to connect to device"
                    Log.d("Device", "Unable to connect")
                }

                if (!keep) {
                    return
                }
            }
        } else {
            _usb_ConnectionStatus.value = "No USB device connected"
            Log.i("Device", "No usb device connected")
        }

    }
    // send data through USB
    fun sendDataToUSB(data: String) : Boolean{
        var result : Boolean
        // if serial is open
        if(m_serial != null && _usb_PermissionState.value){
            try {
                m_serial!!.write(data.toByteArray()) // sending data
                _usb_lastDataSend.value = "Sent data: $data"
                result = true
            }catch (e: Exception){
                _usb_lastDataSend.value = e.message.toString()
                result = false
            }
        }else{
            _usb_lastDataSend.value = "No connection to the device"
            result = false
        }
        return result
    }
    // Usb Disconnected functions
    fun usbDisconnect(){
        // closing usb connection
        m_serial?.close()
        _usb_ConnectionStatus.value = "Disconnected"
        _usb_PermissionState.value = false
    }
    // get value from database
    fun initializeValues() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val brightness = dbHelper.getBrightness(1)?.coerceIn(2, 100) ?: 2
                val warmCool = dbHelper.getWarmCool(1)?.coerceIn(0, 255) ?: 0
                val ledState = dbHelper.getLedState(1) ?: 0
                val login = dbHelper.checkAuthenticate(1) ?: 0

                withContext(Dispatchers.Main) {
                    _dbBrightness.value = brightness.toFloat()
                    _dbWarmCool.value = warmCool.toFloat()
                    _ledState.value = ledState == 1
                    _dbLogin.value = login == 1
                    Log.d("ServerViewModel", "Initialized Brightness: $brightness, WarmCool: $warmCool, ledState: $ledState")
                }
            } catch (e: IOException) {
                Log.e("ServerViewModel", "Error initializing values: ${e.message}", e)

                _dbBrightness.value = 2f // Set fallback value
                _dbWarmCool.value = 0f
            }
        }
    }

    // update brightness
    fun updateBrightness(brightness: Float){
     if(_dbBrightness.value == brightness){
         return
     }

     viewModelScope.launch(Dispatchers.IO){
         withContext(Dispatchers.Main){
             try {
                 _dbBrightness.value = brightness
             }catch (e: IOException){
                 Log.e("ServerViewModel", "Error updating Brightness: ${e.message}", e)
             }

         }
     }
 }

    fun updateBrightnessDatabase(brightness: Float) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Update brightness in the database
                val sendDatabase = dbHelper.updateBrightness(1, brightness.toInt())
                if (sendDatabase) {
                    // Only update the state if the database update is successful
                    Log.d("ServerViewModel", "Success to update brightness in database.")

                } else {
                    Log.i("ServerViewModel", "Failed to update brightness in database.")
                }
            } catch (e: IOException) {
                Log.e("ServerViewModel", "Error updating Brightness: ${e.message}", e)
            }
        }
    }

    // update warm cool value
    fun updateWarmCool(warmCool: Float){
        if(_dbWarmCool.value == warmCool){
            return
        }

        viewModelScope.launch(Dispatchers.IO){
            withContext(Dispatchers.Main){
                try {
                    _dbWarmCool.value = warmCool
                } catch (e: IOException){
                    Log.e("ServerViewModel", "Failed to update warmCool : ${e.message}")
                }
            }
        }
    }

    // update warm cool value
    fun updateWarmCoolDatabase(warmCool: Float){
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Update brightness in the database
                val sendDatabase = dbHelper.updateWarmCool(1, warmCool.toInt())
                if (sendDatabase) {
                    // Only update the state if the database update is successful
                    Log.d("ServerViewModel", "Success to update brightness in database.")
                } else {
                    Log.i("ServerViewModel", "Failed to update brightness in database.")
                }
            } catch (e: IOException) {
                Log.e("ServerViewModel", "Error updating Brightness: ${e.message}", e)
            }
        }
    }
    // update led state
    fun updateLedState(ledState: Boolean) {
        if (_ledState.value == ledState) {
            // No update needed if the state is the same
            Log.d("ServerViewModel", "LED State unchanged: $ledState")
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {

                val result = dbHelper.updateLedState(1, if(ledState) 1 else 0)
                if (result) {
                    withContext(Dispatchers.Main) {
                        _ledState.value = ledState
                        Log.d("ServerViewModel", "LED State updated to: $ledState")
                    }
                } else {
                    Log.e("ServerViewModel", "Failed to update LED State in database")
                }
            } catch (e: IOException){
                Log.e("ServerViewModel", "Error updating LED State: ${e.message}", e)
            }


        }
    }

    fun toggleBoostMode() {
        viewModelScope.launch(Dispatchers.IO) {
            val command = if (_boostMode.value) "Brightness${(_dbBrightness.value).toInt()}" else "BoostMode"
            if (sendDataToUSB(command)) {
                _boostMode.value = !_boostMode.value

                if(_boostMode.value){
                    _ledState.value = true
                }
            }
        }
    }

    // clean up all activity
    fun cleanup() {
        try {
            serverSocket?.close()
            serverSocket = null
            stopRegister()
            handler.removeCallbacksAndMessages(null)
            _server_connectionStatus.value = "Server cleaned up."
            Log.d("ServerViewModel", "Server cleaned up.")
        } catch (e: Exception) {
            Log.e("ServerViewModel", "Error during cleanup: ${e.message}", e)
        }
    }
    private fun cleanupDisconnectedClients() {
        clientSockets.entries.removeIf { (_, socket) ->
            socket.isClosed || !socket.isConnected
        }
        Log.d("ServerViewModel", "Disconnected clients cleaned up.")
    }
    private fun closeAllClientSockets() {
        clientSockets.values.forEach { socket ->
            try {
                socket.close()
            } catch (e: IOException) {
                Log.e("ServerViewModel", "Failed to close client socket: ${e.message}", e)
            }
        }
        clientSockets.clear()
    }

    // destroy every work
    override fun onCleared() {
        super.onCleared()
        try {
            cleanup()
            cleanupDisconnectedClients()
            closeAllClientSockets()
            viewModelScope.cancel()
            dbHelper.closeDatabase()
            handler.removeCallbacksAndMessages(null)
            Log.d("ServerViewModel", "Resources cleaned up.")
        } catch (e: IOException){
            Log.e("Error" , "${e.message}")
        }

    }

}




