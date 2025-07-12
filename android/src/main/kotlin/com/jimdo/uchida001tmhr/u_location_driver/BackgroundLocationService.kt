package com.jimdo.uchida001tmhr.u_location_driver

import android.app.ForegroundServiceStartNotAllowedException
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.ActivityManager
import android.content.Intent
import android.content.Context
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
import com.jimdo.uchida001tmhr.u_location_driver.ULocationDriverPlugin.Companion.messageInformToDartBackground
import com.jimdo.uchida001tmhr.u_location_driver.ULocationDriverPlugin.Companion.myPackageName
import com.jimdo.uchida001tmhr.u_location_driver.ULocationDriverPlugin.Companion.toDartChannelForeground
import com.jimdo.uchida001tmhr.u_location_driver.ULocationDriverPlugin.Companion.toDartChannelNameForeground
import com.jimdo.uchida001tmhr.u_location_driver.ULocationDriverPlugin.Companion.getProcessInfo
import io.flutter.FlutterInjector
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.embedding.engine.loader.FlutterLoader
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.embedding.engine.dart.DartExecutor
import io.flutter.embedding.engine.dart.DartExecutor.DartEntrypoint
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.coroutines.CoroutineContext

class BackgroundLocationService : Service() {
  private var serviceMessenger: Messenger? = null
  val serviceContext = this

  companion object {
    // Dartエントリポイントの名前 (main() ではないので注意)
    // lib/main.dart に backgroundMain 関数が定義されていると仮定
    val DART_ENTRYPOINT_FUNCTION_NAME = "backgroundMain"
    private var flutterEngine: FlutterEngine? = null
    var toDartChannelBackground: MethodChannel? = null
    val toDartChannelNameBackground = "com.jimdo.uchida001tmhr.u_location_driver/toDartBackground"
  }

  override fun onBind(intent: Intent): IBinder {
    serviceMessenger = Messenger(ServiceHandler(this))
    return serviceMessenger!!.binder
  }

  fun informLocationToDartBackground(location: Location?) {
    val locale = Locale.JAPAN
    val dateTimeFormatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withLocale(locale)
    val dateString = dateTimeFormatter.format(LocalDateTime.now())
    val message = "$dateString,${location?.latitude},${location?.longitude}"
    val _processInfo = getProcessInfo()
    if (_processInfo != null) {
      when (_processInfo.importance) {
        ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE -> {
          val packageManager = getPackageManager()
          val intent = packageManager?.getLaunchIntentForPackage("com.jimdo.uchida001tmhr.u_location_driver_example")
          if (intent != null && intent.resolveActivity(packageManager) != null) {
            startActivity(intent)
          }
          toDartChannelBackground?.invokeMethod("informLocationToDartBackground", message)
        }
        else -> {
        }
      }
    }
  }

  inner class ServiceHandler(service: BackgroundLocationService) : Handler(Looper.getMainLooper()) {

    // var toChannel = toChannelForeground

    override fun handleMessage(msg: Message) {
      when (msg.what) {
        messageInformToDartBackground -> {
          informLocationToDartBackground(msg.obj as Location?)
        }

        else -> super.handleMessage(msg)
      }
    }

  }

  override fun onCreate() {
    super.onCreate()
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    val serviceChannelId = "LocationServiceChannel"
    val serviceChannelName = "Location Channel"
    val notificationId = 1000
    try {
      val notificationChannel =
        NotificationChannel(serviceChannelId, serviceChannelName, NotificationManager.IMPORTANCE_DEFAULT)
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
      if (e is ForegroundServiceStartNotAllowedException) {
        println("ForegroundServiceStartNotAllowedException")
      }
    }

    if (flutterEngine == null) {
      // FlutterEngineを初期化
      flutterEngine = FlutterEngine(this)

      // Dartエントリポイントを指定して実行
      val path = FlutterInjector.instance().flutterLoader().findAppBundlePath()
      val dartEntrypoint = DartExecutor.DartEntrypoint(path, DART_ENTRYPOINT_FUNCTION_NAME)
      flutterEngine?.dartExecutor?.executeDartEntrypoint(dartEntrypoint)
      toDartChannelBackground = MethodChannel(flutterEngine!!.dartExecutor.binaryMessenger, toDartChannelNameBackground)
    }

    super.onStartCommand(intent, flags, startId)
    return START_STICKY
  }

  override fun onDestroy() {
    flutterEngine?.destroy()
    flutterEngine = null
    super.onDestroy()
    println("BackgroundLocationService was destroyed!");
  }

  override fun onUnbind(intent: Intent?): Boolean {
    return super.onUnbind(intent)
  }

}