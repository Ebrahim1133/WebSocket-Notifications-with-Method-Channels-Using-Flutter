package com.example.testnotification2

import android.annotation.SuppressLint
import io.flutter.embedding.android.FlutterActivity
import android.app.*
import android.content.BroadcastReceiver

import android.content.Context

import androidx.core.app.NotificationCompat
import java.util.*

import androidx.core.app.NotificationManagerCompat

import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.*
import androidx.annotation.RequiresApi
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import okhttp3.*

@SuppressLint("ServiceCast")
@Suppress("DEPRECATION")
object NotificationServiceHolder {
    var notificationService: NotificationService? = null
}
class MyBroadcastReceiver( ) : BroadcastReceiver() {
    val notificationService = NotificationServiceHolder.notificationService

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED ||
            intent?.action == Intent.ACTION_USER_PRESENT ||
            intent?.action == Intent.ACTION_SHUTDOWN||
            intent?.action == Intent.ACTION_LOCKED_BOOT_COMPLETED
        ) {
            // Attempt to reconnect when device boots, user unlocks, or shuts down
            // You might want to add a delay to avoid immediate reconnection
            if (notificationService?.webSocket == null ) {
                notificationService?.connectWebSocket()
            }
        }
    }
}
class NotificationService : Service() {
     val receiver = MyBroadcastReceiver()
     val delayMillis = 5000L // 5 seconds
     val notificationId = 1 // Unique ID for the notification
     val channelId = "notification_channel_id"
     val client = OkHttpClient()
     var webSocket: WebSocket? = null

     val handler = Handler(Looper.getMainLooper())
     val connectivityManager by lazy {
        getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    }


     val networkCallback = @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            super.onAvailable(network)
            connectWebSocket()
        }

        override fun onLost(network: Network) {
            super.onLost(network)
            // Internet connection is lost, handle as needed
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ECLAIR) {
            startForeground(notificationId, createForegroundNotification())
        }
//        handler.postDelayed(notificationRunnable, delayMillis)
        connectWebSocket()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            registerNetworkCallback()
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            registerBroadcastReceiver()
        }
        NotificationServiceHolder.notificationService = this
    }
     @RequiresApi(Build.VERSION_CODES.N)
     fun registerBroadcastReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BOOT_COMPLETED)
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_SHUTDOWN)
            addAction(Intent.ACTION_LOCKED_BOOT_COMPLETED)
        }
        registerReceiver(receiver, filter)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopWebSocket()
        stopForeground(true)
        handler.removeCallbacksAndMessages(null)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            unregisterNetworkCallback()
        }
        unregisterReceiver(receiver)
    }
     fun connectWebSocket() {
        val request = Request.Builder().url("wss://free.blr2.piesocket.com/v3/1?api_key=L53UmFrTBh9L2sXLvFhM60E6x2YaOMbcksUP0E1N&notify_self=1").build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                super.onOpen(webSocket, response)
                // WebSocket connection opened

            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                super.onMessage(webSocket, text)
                // Handle WebSocket message, and show notification
                showNotification(text)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                super.onClosed(webSocket, code, reason)
                // WebSocket connection closed
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                super.onFailure(webSocket, t, response)
                // WebSocket connection failure
            }
        })
    }
     fun stopWebSocket() {
        webSocket?.close(1000, null)
        webSocket = null
    }
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

     fun createNotificationChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val name = "Notification Channel"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(channelId, name, importance)
            val notificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
     fun registerNetworkCallback() {
        val networkRequest = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(networkRequest, networkCallback)
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
     fun unregisterNetworkCallback() {
        connectivityManager.unregisterNetworkCallback(networkCallback)
    }

     fun createForegroundNotification(): Notification {
//        val notificationIntent = Intent(this, MainActivity::class.java)
//        val pendingIntent = PendingIntent.getActivity(
//            this, 0, notificationIntent, 0
//        )

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Foreground Service")
            .setContentText("Running...")
            .setSmallIcon(R.drawable.launch_background)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
    }

     fun showNotification(text:String) {
         val intent = Intent(this, MainActivity::class.java).apply {
             flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
         }
         val pendingIntent: PendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.launch_background)
            .setContentTitle(text)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        with(NotificationManagerCompat.from(this)) {
            notify(notificationId, builder.build())
        }
    }
}
class MainActivity: FlutterActivity() {
    private  val channelName = "notifications";
    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        var channel = MethodChannel(flutterEngine.dartExecutor.binaryMessenger,channelName);
            channel.setMethodCallHandler { call, result ->
                if(call.method == "connectnotifications"){
                    val serviceIntent = Intent(this, NotificationService::class.java)
                    startService(serviceIntent)
                }
            }

    }
}
