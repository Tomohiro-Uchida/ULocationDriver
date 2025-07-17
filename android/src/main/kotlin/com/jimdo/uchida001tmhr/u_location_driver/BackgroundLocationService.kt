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
import com.jimdo.uchida001tmhr.u_location_driver.MessageFromPluginToService.Companion.messageLocation
import com.jimdo.uchida001tmhr.u_location_driver.MessageFromPluginToService.Companion.messageSendBackground
import com.jimdo.uchida001tmhr.u_location_driver.MessageFromPluginToService.Companion.messageSendForeground
import com.jimdo.uchida001tmhr.u_location_driver.MessageFromPluginToService.Companion.messageSendInactivate
import com.jimdo.uchida001tmhr.u_location_driver.ULocationDriverPlugin.Companion.backgroundDartExecutor
import com.jimdo.uchida001tmhr.u_location_driver.ULocationDriverPlugin.Companion.eventSinkBackground
import com.jimdo.uchida001tmhr.u_location_driver.ULocationDriverPlugin.Companion.eventSinkForeground
import com.jimdo.uchida001tmhr.u_location_driver.ULocationDriverPlugin.Companion.toDartChannelToBackground
import com.jimdo.uchida001tmhr.u_location_driver.ULocationDriverPlugin.Companion.toDartChannelToForeground
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
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.BasicMessageChannel
import io.flutter.plugin.common.StringCodec
import io.flutter.view.FlutterCallbackInformation
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

class BackgroundLocationService : Service() {
  val serviceContext = this
  val sendNone = 1000
  val sendToForeground = 2000
  val sendToBackground = 3000
  var sendToDart = sendNone
  lateinit var flutterEngineBackground: FlutterEngine
  var callbackHandler = 0L
  lateinit var messageChannelToBackgroundIsolate: BasicMessageChannel<String>

  override fun onBind(intent: Intent): IBinder {
    println("onBind()")
    val serviceMessenger = Messenger(ServiceHandler(this))
    return serviceMessenger!!.binder
  }

  private fun startBackgroundIsolate(callbackHandle: Long) {
    val flutterLoader = FlutterLoader()
    flutterLoader.startInitialization(applicationContext)
    flutterLoader.ensureInitializationComplete(applicationContext, null)

    flutterEngineBackground = FlutterEngine(applicationContext)

    val callbackInfo = FlutterCallbackInformation.lookupCallbackInformation(callbackHandle)

    val dartCallback = DartExecutor.DartCallback(
      assets,
      flutterLoader.findAppBundlePath(),
      callbackInfo!!
    )

    flutterEngineBackground.dartExecutor.executeDartCallback(dartCallback)

    // Dart Isolate との通信チャネルを初期化
    messageChannelToBackgroundIsolate = BasicMessageChannel(
      flutterEngineBackground.dartExecutor.binaryMessenger,
      "com.jimdo.uchida001tmhr.u_location_driver/backgroundIsolate",
      StringCodec.INSTANCE
    )
  }

  fun informLocationToDartForeground(location: Location?) {
    val locale = Locale.JAPAN
    val dateTimeFormatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withLocale(locale)
    val dateString = dateTimeFormatter.format(LocalDateTime.now())
    val message = "$dateString,${location?.latitude},${location?.longitude}"
    Handler(Looper.getMainLooper()).post {
      println("informLocationToDartForeground -> message = $message")
      toDartChannelToForeground.invokeMethod("informToForeground", message, object : MethodChannel.Result {
        override fun success(result: Any?) {
          println("invokeMethod -> success")
        }

        override fun error(errorCode: String, errorMessage: String?, errorDetails: Any?) {
          println("invokeMethod -> errorMessage == $errorMessage")
        }

        override fun notImplemented() {
          println("invokeMethod -> notImplemented")
        }
      })
    }
  }

  fun informLocationToDartBackground(location: Location?) {
    val locale = Locale.JAPAN
    val dateTimeFormatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withLocale(locale)
    val dateString = dateTimeFormatter.format(LocalDateTime.now())
    val message = "$dateString,${location?.latitude},${location?.longitude}"
    if (::messageChannelToBackgroundIsolate.isInitialized) {
      println("informLocationToDartBackground -> message = $message")
      messageChannelToBackgroundIsolate.send(message)
    }
  }

  inner class ServiceHandler(service: BackgroundLocationService) : Handler(Looper.getMainLooper()) {

    override fun handleMessage(msg: Message) {
      when (msg.what) {
        messageLocation -> {
          println("messageLocation -> $sendToDart")
          when(sendToDart) {
            sendToForeground -> {
              println("messageLocation -> sendToForeground")
              informLocationToDartForeground(msg.obj as Location?)
            }
            sendToBackground -> {
              println("messageLocation -> sendToBackground")
              informLocationToDartBackground(msg.obj as Location?)
            }
            else -> {
              println("messageLocation -> else")
            }
          }
        }
        messageSendForeground -> {
          println("messageSendForeground")
          if (msg.obj != null) {
            callbackHandler = msg.obj as Long
            // startBackgroundIsolate(callbackHandler)
          }
          sendToDart = sendToForeground
        }
        messageSendBackground -> {
          println("messageSendBackground")
          sendToDart = sendToBackground
        }
        messageSendInactivate -> {
          println("messageSendInactivate")
          (this@BackgroundLocationService).stopSelf()
        }
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
    super.onStartCommand(intent, flags, startId)
    return START_STICKY
  }

  override fun onDestroy() {
    println("BackgroundLocationService was destroyed!");
    flutterEngineBackground?.destroy()
    super.onDestroy()
  }

  override fun onUnbind(intent: Intent?): Boolean {
    return super.onUnbind(intent)
  }

}