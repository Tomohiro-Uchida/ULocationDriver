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
import com.jimdo.uchida001tmhr.u_location_driver.ULocationDriverPlugin.Companion.messageChangeToBackground
import com.jimdo.uchida001tmhr.u_location_driver.ULocationDriverPlugin.Companion.messageChangeToForeground
import com.jimdo.uchida001tmhr.u_location_driver.ULocationDriverPlugin.Companion.messageInformToDart
import com.jimdo.uchida001tmhr.u_location_driver.ULocationDriverPlugin.Companion.toChannelForeground
import com.jimdo.uchida001tmhr.u_location_driver.ULocationDriverPlugin.Companion.toChannelBackground
import com.jimdo.uchida001tmhr.u_location_driver.ULocationDriverPlugin.Companion.toChannelName
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.embedding.engine.dart.DartExecutor
import io.flutter.embedding.engine.dart.DartExecutor.DartEntrypoint
import io.flutter.embedding.engine.FlutterEngineCache
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.coroutines.CoroutineContext

class BackgroundLocationService : Service() {
  private var serviceMessenger:  Messenger? = null
  val serviceContext = this

  override fun onBind(intent: Intent): IBinder {
    serviceMessenger = Messenger(ServiceHandler(this))
    return serviceMessenger!!.binder
  }

  inner class ServiceHandler(service: BackgroundLocationService) : Handler(Looper.getMainLooper()) {

    var toChannel = toChannelForeground

    override fun handleMessage(msg: Message) {
      when (msg.what) {
        messageInformToDart -> {
          informLocationToDart(msg.obj as Location?)
        }
        messageChangeToForeground -> {
          toChannel = toChannelForeground
        }
        messageChangeToBackground -> {
          toChannel = toChannelBackground
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
        toChannel?.invokeMethod("informLocationToDart", message)
      } catch (e: Exception) {
        print(e)
      }
    }

  }

  fun  createBackgroundMethodChannel(): MethodChannel? {
    var backgroundMethodChannel: MethodChannel? = null;
    var engine: FlutterEngine? = null;
    if (getEngine() == null) {
      engine = FlutterEngine(serviceContext)
      // Define a DartEntrypoint
      val entrypoint: DartExecutor.DartEntrypoint =
        DartExecutor.DartEntrypoint.createDefault()
      // Execute the DartEntrypoint within the FlutterEngine.
      engine.dartExecutor.executeDartEntrypoint(entrypoint)
    } else {
      engine = getEngine();
    }
    backgroundMethodChannel = MethodChannel(engine?.dartExecutor?.binaryMessenger!!, toChannelName)!!
    return backgroundMethodChannel
  }

  fun getEngine(): FlutterEngine? {
    return FlutterEngineCache.getInstance().get(toChannelName)
  }

  override fun onCreate() {
    toChannelBackground = createBackgroundMethodChannel()
    super.onCreate()
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    val serviceChannelId = "LocationServiceChannel"
    val serviceChannelName = "Location Channel"
    val notificationId = 1000
    try {
      val notificationChannel = NotificationChannel(serviceChannelId, serviceChannelName, NotificationManager.IMPORTANCE_DEFAULT)
      val notificationManager = applicationContext.getSystemService(NotificationManager::class.java)
      notificationManager.createNotificationChannel(notificationChannel)
      val notification = NotificationCompat.Builder(this, serviceChannelId).build()

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
    super.onDestroy()
  }

  override fun onUnbind(intent: Intent?): Boolean {
    return super.onUnbind(intent)
  }


}