package com.jimdo.uchida001tmhr.u_location_driver

import android.app.ForegroundServiceStartNotAllowedException
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.ActivityManager
import android.content.Intent
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.Manifest
import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat.startForeground
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.jimdo.uchida001tmhr.u_location_driver.MessageFromPluginToService.Companion.registerBackgroundIsolate
import com.jimdo.uchida001tmhr.u_location_driver.MessageFromPluginToService.Companion.startBackgroundIsolate
import com.jimdo.uchida001tmhr.u_location_driver.MessageFromPluginToService.Companion.messageStartLocation
import com.jimdo.uchida001tmhr.u_location_driver.MessageFromPluginToService.Companion.messageSendInactivate
import com.jimdo.uchida001tmhr.u_location_driver.MessageFromPluginToService.Companion.stopBackgroundIsolate
import com.jimdo.uchida001tmhr.u_location_driver.MessageFromPluginToService.Companion.stopMainIsolate
import com.jimdo.uchida001tmhr.u_location_driver.ULocationDriverPlugin.Companion.flutterEngineBackground
import com.jimdo.uchida001tmhr.u_location_driver.ULocationDriverPlugin.Companion.myPackageName
import com.jimdo.uchida001tmhr.u_location_driver.ULocationDriverPlugin.Companion.thisContext
import com.jimdo.uchida001tmhr.u_location_driver.ULocationDriverPlugin.Companion.toDartChannelNameBackground
import com.jimdo.uchida001tmhr.u_location_driver.ULocationDriverPlugin.Companion.toDartChannel
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.embedding.engine.loader.FlutterLoader
import io.flutter.embedding.engine.dart.DartExecutor
import io.flutter.embedding.engine.dart.DartExecutor.DartEntrypoint
import io.flutter.plugin.common.BasicMessageChannel
import io.flutter.plugin.common.StringCodec
import io.flutter.view.FlutterCallbackInformation
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

class BackgroundLocationService : Service() {
  val serviceContext = this
  val sendToForeground = 1000
  val sendToBackground = 1010
  var sendToDart = sendToForeground
  var callbackHandler = 0L
  var fusedLocationClients = mutableListOf<FusedLocationProviderClient>()

  override fun onBind(intent: Intent): IBinder {
    println("BackgroundLocationService: onBind()")
    val serviceMessenger = Messenger(ServiceHandler(this))
    return serviceMessenger.binder
  }

  private fun _startBackgroundIsolate(callbackHandle: Long) {
    println("startBackgroundIsolate: received callbackHandle = $callbackHandle")

    // Flutter 初期化
    val flutterLoader = FlutterLoader()
    flutterLoader.startInitialization(applicationContext)
    flutterLoader.ensureInitializationComplete(applicationContext, null)

    // callbackHandle から Dart 側の関数情報を取得
    val callbackInfo = FlutterCallbackInformation.lookupCallbackInformation(callbackHandle)
    if (callbackInfo == null) {
      println("startBackgroundIsolate: ERROR - callbackInfo is null (invalid callbackHandle?)")
      return
    }

    println("startBackgroundIsolate: callbackLibraryPath = ${callbackInfo.callbackLibraryPath}")
    println("startBackgroundIsolate: callbackName = ${callbackInfo.callbackName}")

    // FlutterEngine を生成して DartEntrypoint を起動
    flutterEngineBackground = FlutterEngine(applicationContext)
    val dartEntrypoint = DartExecutor.DartEntrypoint(
      flutterLoader.findAppBundlePath(),
      callbackInfo.callbackLibraryPath,
      callbackInfo.callbackName
    )
    flutterEngineBackground?.dartExecutor?.executeDartEntrypoint(dartEntrypoint)

    // BasicMessageChannel 経由の通信チャネルをセットアップ
    toDartChannel = BasicMessageChannel(
      flutterEngineBackground!!.dartExecutor.binaryMessenger,
      toDartChannelNameBackground,
      StringCodec.INSTANCE
    )

    println("startBackgroundIsolate: Background isolate started")
  }

  fun informLocationToDartForeground(location: Location?) {
    val locale = Locale.JAPAN
    val dateTimeFormatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withLocale(locale)
    val dateString = dateTimeFormatter.format(LocalDateTime.now())
    val message = "$dateString,${location?.latitude},${location?.longitude}"
    println("BackgroundLocationService: informLocationToDartForeground(): toDartChannelToForeground = $toDartChannel")
    if (toDartChannel != null) {
      toDartChannel?.send(message) { reply: String? ->
        println("BackgroundLocationService: Sent via toDartChannelToForeground -> $reply")
      }
    }
  }

  fun informLocationToDartBackground(location: Location?) {
    val locale = Locale.JAPAN
    val dateTimeFormatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withLocale(locale)
    val dateString = dateTimeFormatter.format(LocalDateTime.now())
    val message = "$dateString,${location?.latitude},${location?.longitude}"
    println("BackgroundLocationService: informLocationToDartBackground(): toDartChannelToBackground = $toDartChannel")
    if (toDartChannel != null) {
      toDartChannel?.send(message) { reply: String? ->
        println("BackgroundLocationService: Sent via toDartChannelToBackground -> $reply")
      }
    }
  }

  fun stopBackgroundIsolate() {
    println("BackgroundLocationService: stopBackgroundIsolate() start : toDartChannelToBackground = $toDartChannel")
    val message = "stopBackgroundIsolate"
    if (toDartChannel != null) {
      toDartChannel?.send(message) { reply: String? ->
        println("BackgroundLocationService: Sent via toDartChannelToForeground -> $reply")
      }
    }
  }

  fun stopMainIsolate() {
    println("BackgroundLocationService: stopMainIsolate() start : toDartChannelToForeground = $toDartChannel")
    val message = "stopMainIsolate"
    if (toDartChannel != null) {
      toDartChannel?.send(message) { reply: String? ->
        println("BackgroundLocationService: Sent via toDartChannelToBackground -> $reply")
      }
    }
  }

  @RequiresPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
  fun requestDeviceLocation() {
    val permissionFineLocation = ContextCompat.checkSelfPermission(
      thisContext.applicationContext,
      Manifest.permission.ACCESS_FINE_LOCATION
    )
    val permissionBackgroundLocation = ContextCompat.checkSelfPermission(
      thisContext.applicationContext,
      Manifest.permission.ACCESS_BACKGROUND_LOCATION
    )
    if (permissionFineLocation == PackageManager.PERMISSION_GRANTED &&
      permissionBackgroundLocation == PackageManager.PERMISSION_GRANTED
    ) {
      stopLocationUpdates()
      fusedLocationClients.add(LocationServices.getFusedLocationProviderClient(serviceContext))
      startLocationUpdates()
    }
  }

  fun getProcessInfo(): ActivityManager.RunningAppProcessInfo? {
    val activityManager = serviceContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val runningAppProcessInfoList = activityManager.runningAppProcesses
    for (processInfo in runningAppProcessInfoList) {
      if (processInfo.processName == myPackageName) {
        return processInfo
      }
    }
    return null
  }

  val locationCallback: LocationCallback = object : LocationCallback() {
    override fun onLocationResult(locationResult: LocationResult) {
      println("ULocationDriverPlugin: onLocationResult()")
      val _processInfo = getProcessInfo()
      if (_processInfo?.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND ||
        _processInfo?.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE
      ) {
        println("BackgroundLocationService: sendToDart is connected to Foreground")
        sendToDart = sendToForeground
      } else {
        println("BackgroundLocationService: sendToDart is connected to Background")
        sendToDart = sendToBackground
      }
      when (sendToDart) {
        sendToForeground -> {
          println("BackgroundLocationService: messageLocation -> sendToForeground")
          informLocationToDartForeground(locationResult.lastLocation!!)
        }

        sendToBackground -> {
          println("BackgroundLocationService: messageLocation -> sendToBackground")
          informLocationToDartBackground(locationResult.lastLocation!!)
        }

        else -> {
          println("BackgroundLocationService: messageLocation -> else")
        }
      }
    }
  }

  @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
  fun startLocationUpdates() {
    fusedLocationClients.forEach { it ->
      it.requestLocationUpdates(
        LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 10 * 1000 /*10秒*/)
          .setMinUpdateIntervalMillis(5 * 1000 /*5秒*/)
          .build(), locationCallback, Looper.getMainLooper()
      )
    }
  }

  fun stopLocationUpdates() {
    fusedLocationClients.forEach { it ->
      it.removeLocationUpdates(locationCallback)
    }
    fusedLocationClients.clear()
  }

  @SuppressLint("HandlerLeak")
  inner class ServiceHandler(service: BackgroundLocationService) : Handler(Looper.getMainLooper()) {

    @RequiresPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
    override fun handleMessage(msg: Message) {
      println("BackgroundLocationService: handleMessage(${msg.what})")
      when (msg.what) {
        registerBackgroundIsolate -> {
          println("BackgroundLocationService: registerBackgroundIsolate: msg.data = ${msg.data}")
          if (msg.data != null) {
            val bundle: Bundle = msg.data
            callbackHandler = bundle.getLong("callbackHandle", 0L)
            println("BackgroundLocationService: registerBackgroundIsolate: callbackHandler = $callbackHandler")
          }
        }

        startBackgroundIsolate -> {
          println("BackgroundLocationService: startBackgroundIsolate: callbackHandler = $callbackHandler")
          if (callbackHandler != 0L) {
            _startBackgroundIsolate(callbackHandler)
          }
        }

        stopBackgroundIsolate -> {
          stopBackgroundIsolate()
          println("BackgroundLocationService: end : stopBackgroundIsolate")
        }

        stopMainIsolate -> {
          stopMainIsolate()
          println("BackgroundLocationService: end : stopMainIsolate")
        }

        messageSendInactivate -> {
          try {
            flutterEngineBackground?.destroy()
            flutterEngineBackground = null
          } catch (e: Exception) {
            println(e)
          }
          println("BackgroundLocationService: messageSendInactivate -> flutterEngineBackground.destroy()")
          val uLocationDriverPlugin = ULocationDriverPlugin()
          stopLocationUpdates()
          println("BackgroundLocationService: messageSendInactivate -> uLocationDriverPlugin.stopLocationUpdates()")
        }

        messageStartLocation -> {
          requestDeviceLocation()
          println("BackgroundLocationService: end : requestDeviceLocation")
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
      val notification = NotificationCompat.Builder(this, serviceChannelId)
        .setContentTitle("Serves Location")
        .setContentText("Serving location update...")
        .build()
      startForeground(
        /* service = */ this,
        /* id = */ notificationId, // Cannot be 0
        /* notification = */ notification,
        /* foregroundServiceType = */
        ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
      )
    } catch (e: Exception) {
      if (e is ForegroundServiceStartNotAllowedException) {
        println("BackgroundLocationService: ForegroundServiceStartNotAllowedException")
      }
    }
    super.onStartCommand(intent, flags, startId)
    return START_STICKY
  }

  override fun onDestroy() {
    println("BackgroundLocationService: BackgroundLocationService was destroyed!");
    flutterEngineBackground?.destroy()
    stopLocationUpdates()
    super.onDestroy()
  }

  override fun onUnbind(intent: Intent?): Boolean {
    return super.onUnbind(intent)
  }

}