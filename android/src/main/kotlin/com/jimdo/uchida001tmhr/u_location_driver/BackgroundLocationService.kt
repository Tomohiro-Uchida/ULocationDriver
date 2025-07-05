package com.jimdo.uchida001tmhr.u_location_driver

import android.app.ForegroundServiceStartNotAllowedException
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat.startForeground
import io.flutter.embedding.engine.FlutterJNI;

class BackgroundLocationService : Service() {

  // private val flutterJNI = FlutterJNI()

  override fun onBind(intent: Intent): IBinder {
    TODO("Return the communication channel to the service.")
  }

  override fun onCreate() {
    super.onCreate()
    // flutterJNI.attachToNative()
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    val channelId = "LocationServiceChannel"
    val channelName = "Location Channel"
    val notificationId = 1000

    try {
      val notificationChannel = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_DEFAULT)
      val notificationManager = applicationContext.getSystemService(NotificationManager::class.java)
      notificationManager.createNotificationChannel(notificationChannel)
      val notification = NotificationCompat.Builder(this, channelId).build()

      startForeground(
        /* service = */ this,
        /* id = */ notificationId, // Cannot be 0
        /* notification = */ notification,
        /* foregroundServiceType = */
        ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
      )
    } catch (e: Exception) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        && e is ForegroundServiceStartNotAllowedException
      ) {
        print("ForegroundServiceStartNotAllowedException")
      }
    }

    super.onStartCommand(intent, flags, startId)
    return START_STICKY
  }

  override fun onDestroy() {
    // flutterJNI.detachFromNativeAndReleaseResources();
    super.onDestroy()
  }

}