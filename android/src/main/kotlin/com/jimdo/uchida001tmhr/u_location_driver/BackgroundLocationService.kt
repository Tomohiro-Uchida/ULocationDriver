package com.jimdo.uchida001tmhr.u_location_driver

import android.app.ForegroundServiceStartNotAllowedException
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat.startForeground
// import io.flutter.embedding.engine.FlutterJNI;
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

class BackgroundLocationService : Service() {
  private var serviceMessenger:  Messenger? = null

  // private val flutterJNI = FlutterJNI()

  override fun onBind(intent: Intent): IBinder {
    serviceMessenger = Messenger(ServiceHandler(this))
    return serviceMessenger!!.binder
  }

  internal class ServiceHandler(service: BackgroundLocationService) : Handler(Looper.getMainLooper()) {
    // private val mService = WeakReference<Service>(service)
    private val messagePluginToService = 1

    override fun handleMessage(msg: Message) {
      when (msg.what) {
        messagePluginToService -> {
          informLocationToDart(msg.obj as Location?)
        }
        else -> super.handleMessage(msg)
      }
    }

    fun informLocationToDart(location: Location?) {
      val locale = Locale.JAPAN
      val dateTimeFormatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withLocale(locale)
      val dateString = dateTimeFormatter.format(LocalDateTime.now())
      val message = "$dateString,${location?.latitude},${location?.longitude}"
      try {
        ULocationDriverPlugin.Companion.toChannel.invokeMethod("informLocationToDart", message)
      } catch (e: Exception) {
        print(e)
      }
    }

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

  override fun onUnbind(intent: Intent?): Boolean {
    return super.onUnbind(intent)
  }

}