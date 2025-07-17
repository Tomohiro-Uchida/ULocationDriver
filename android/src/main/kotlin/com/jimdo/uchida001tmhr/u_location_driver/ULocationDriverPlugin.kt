package com.jimdo.uchida001tmhr.u_location_driver

import android.app.ActivityManager
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.Process
import android.Manifest
import android.Manifest.permission.ACCESS_BACKGROUND_LOCATION
import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.content.SharedPreferences
import android.location.Location
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.RemoteException
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.jimdo.uchida001tmhr.u_location_driver.MessageFromPluginToService.Companion.messageServiceToPlugin
import com.jimdo.uchida001tmhr.u_location_driver.MessageFromPluginToService.Companion.activityMessenger
import com.jimdo.uchida001tmhr.u_location_driver.MessageFromPluginToService.Companion.serviceMessenger
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.embedding.engine.FlutterEngineGroup
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
import io.flutter.plugin.common.BinaryMessenger
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

class MessageFromPluginToService {
  var messageType = messageLocation
  var message: Any = ""

  companion object {
    val messageLocation = 1000
    val messageSendForeground = 2000
    val messageSendBackground = 2010
    val messageSendInactivate = 2020
    val messageServiceToPlugin = 3000
    var serviceMessenger: Messenger? = null
    var activityMessenger: Messenger? = null
  }

  fun sendMessageToService() {
    try {
      val msg = Message.obtain(null, messageType, 0, 0)
      msg.replyTo = activityMessenger
      msg.obj = message
      println("sendMessageToService() -> serviceMessenger = $serviceMessenger")
      serviceMessenger?.send(msg)
    } catch (e: RemoteException) {
      e.printStackTrace()
    }
  }
}

class ULocationDriverPlugin : FlutterPlugin, MethodCallHandler, ActivityAware {
  private var flutterEngineMain: FlutterEngine? = null
  private lateinit var requestPermissionLauncherFineLocation: ActivityResultLauncher<String>
  private lateinit var requestPermissionLauncherBackgroundLocation: ActivityResultLauncher<String>
  private var fusedLocationClient: FusedLocationProviderClient? = null
  private lateinit var fromDartChannel: MethodChannel
  private lateinit var locationCallback: LocationCallback
  private var serviceComponentName: ComponentName? = null
  private var bound = false
  private val connection = object : ServiceConnection {
    override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
      serviceMessenger = Messenger(binder)
      println("onServiceConnected() -> serviceMessenger = $serviceMessenger")
      bound = true
    }

    override fun onServiceDisconnected(name: ComponentName?) {
      println("onServiceDisconnected()")
      serviceMessenger = null
      bound = false
    }
  }
  // バックグラウンドエンジンの DartExecutor を保持

  companion object {
    private lateinit var thisActivity: Activity
    private lateinit var thisContext: Context
    val fromDartChannelName = "com.jimdo.uchida001tmhr.u_location_driver/fromDart"
    val toDartChannelNameForeground = "com.jimdo.uchida001tmhr.u_location_driver/toDartForeground"
    val toDartChannelNameBackground = "com.jimdo.uchida001tmhr.u_location_driver/toDartBackground"
    lateinit var toDartChannelToForeground: MethodChannel
    lateinit var toDartChannelToBackground: MethodChannel
    var myPackageName: String? = ""
    var backgroundFlutterEngine: FlutterEngine? = null
    var backgroundDartExecutor: DartExecutor? = null
    lateinit var eventSinkForeground: EventChannel.EventSink
    lateinit var eventSinkBackground: EventChannel.EventSink
  }

  private fun getLocationPermissionLocation() {
    val permissionFineLocation = ContextCompat.checkSelfPermission(
      thisContext,
      Manifest.permission.ACCESS_FINE_LOCATION
    )
    if (permissionFineLocation == PackageManager.PERMISSION_GRANTED) {
      getLocationPermissionBackgroundLocation()
    } else {
      requestPermissionLauncherFineLocation.launch(ACCESS_FINE_LOCATION)
    }
  }

  private fun getLocationPermissionBackgroundLocation() {
    val permissionBackgroundLocation = ContextCompat.checkSelfPermission(
      thisContext,
      Manifest.permission.ACCESS_BACKGROUND_LOCATION
    )
    if (permissionBackgroundLocation == PackageManager.PERMISSION_GRANTED) {
      requestDeviceLocation()
    } else {
      requestPermissionLauncherBackgroundLocation.launch(ACCESS_BACKGROUND_LOCATION)
    }
  }

  override fun onAttachedToEngine(flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
    println("onAttachedToEngine() - 1")
    fromDartChannel = MethodChannel(flutterPluginBinding.binaryMessenger, fromDartChannelName)
    fromDartChannel.setMethodCallHandler(this)
    thisContext = flutterPluginBinding.applicationContext
    val intentLocation = Intent(thisContext, BackgroundLocationService::class.java)
    toDartChannelToForeground = MethodChannel(flutterPluginBinding.binaryMessenger, toDartChannelNameForeground)
    toDartChannelToBackground = MethodChannel(flutterPluginBinding.binaryMessenger, toDartChannelNameBackground)
    thisContext.startForegroundService(intentLocation)
    thisContext.bindService(intentLocation, connection, Context.BIND_AUTO_CREATE)
  }

  override fun onAttachedToActivity(binding: ActivityPluginBinding) {
    println("onAttachedToActivity() - 1")
    myPackageName = binding.activity.intent.getComponent()?.getPackageName()
    println("onAttachedToActivity() - 2")
    thisActivity = binding.activity
    println("onAttachedToActivity() - 3")
    activityMessenger = Messenger(ActivityHandler(thisActivity))
    println("onAttachedToActivity() - 4")
    locationCallback = object : LocationCallback() {
      override fun onLocationResult(locationResult: LocationResult) {
        Handler(Looper.getMainLooper()).post {
          // ここにUIスレッドで実行したいコードを書く
          println("onLocationResult()")
          val messageFromPluginToService = MessageFromPluginToService()
          messageFromPluginToService.messageType = MessageFromPluginToService.messageLocation
          messageFromPluginToService.message = locationResult.lastLocation!!
          messageFromPluginToService.sendMessageToService()
        }
      }
    }

    requestPermissionLauncherFineLocation =
      (thisActivity as ComponentActivity).registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
          getLocationPermissionBackgroundLocation()
        }
      }
    requestPermissionLauncherBackgroundLocation =
      (thisActivity as ComponentActivity).registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        requestDeviceLocation()
      }
  }

  override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
    println("onDetachedFromEngine()")
  }

  override fun onDetachedFromActivity() {
    println("onDetachedFromActivity()")
  }

  override fun onDetachedFromActivityForConfigChanges() {
    println("onDetachedFromActivityForConfigChanges()")
  }

  override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
    println("onReattachedToActivityForConfigChanges()")
  }

  override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
    println("onMethodCall() -> ${call.method}")
    val intentLocation = Intent(thisContext, BackgroundLocationService::class.java)
    when (call.method) {
      "activateForeground" -> {
        val messageFromPluginToService = MessageFromPluginToService()
        messageFromPluginToService.messageType = MessageFromPluginToService.messageSendForeground
        if (call.arguments != null) {
          val argCallbackHandle: Long? = call.argument<Long>("callbackHandle")
          if (argCallbackHandle != null) {
            messageFromPluginToService.message = argCallbackHandle
          }
        }
        messageFromPluginToService.sendMessageToService()
        getLocationPermissionLocation()
        result.success("success")
      }

      "activateBackground" -> {
        val messageFromPluginToService = MessageFromPluginToService()
        messageFromPluginToService.messageType = MessageFromPluginToService.messageSendBackground
        messageFromPluginToService.sendMessageToService()
        getLocationPermissionLocation()
        result.success("success")
      }

      "inactivate" -> {
        val messageFromPluginToService = MessageFromPluginToService()
        messageFromPluginToService.messageType = MessageFromPluginToService.messageSendInactivate
        messageFromPluginToService.sendMessageToService()
        stopLocationUpdates()
        result.success("success")
      }

      else ->
        result.notImplemented()
    }
  }


  internal class ActivityHandler(activity: Activity) : Handler(Looper.getMainLooper()) {
    override fun handleMessage(msg: Message) {
      when (msg.what) {
        messageServiceToPlugin -> {
          //something
        }

        else -> super.handleMessage(msg)
      }
    }
  }


  @RequiresPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
  private fun requestDeviceLocation() {
    val permissionFineLocation = ContextCompat.checkSelfPermission(
      thisContext.applicationContext,
      Manifest.permission.ACCESS_FINE_LOCATION
    )
    val permissionBackgroundLocation = ContextCompat.checkSelfPermission(
      thisContext.applicationContext,
      Manifest.permission.ACCESS_BACKGROUND_LOCATION
    )
    if (permissionFineLocation == PackageManager.PERMISSION_GRANTED &&
      permissionBackgroundLocation == PackageManager.PERMISSION_GRANTED &&
      fusedLocationClient == null
    ) {
      fusedLocationClient = LocationServices.getFusedLocationProviderClient(thisActivity)
      startLocationUpdates()
    }
  }

  fun getProcessInfo(): ActivityManager.RunningAppProcessInfo? {
    val activityManager = thisActivity.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val runningAppProcessInfoList = activityManager.runningAppProcesses
    for (processInfo in runningAppProcessInfoList) {
      if (processInfo.processName == myPackageName) {
        return processInfo
      }
    }
    return null
  }

  @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
  private fun startLocationUpdates() {
    if (fusedLocationClient != null) {
      val _processInfo = getProcessInfo()
      if (_processInfo?.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND ||
        _processInfo?.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE
      ) {
        fusedLocationClient!!.requestLocationUpdates(
          LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 10 * 1000 /*10秒*/)
            .setMinUpdateIntervalMillis(5 * 1000 /*5秒*/)
            .build(), locationCallback, Looper.getMainLooper()
        )
      } else {
        fusedLocationClient!!.requestLocationUpdates(
          LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 30 * 1000 /*30秒*/)
            .setMinUpdateIntervalMillis(10 * 1000 /*10秒*/)
            .build(), locationCallback, Looper.getMainLooper()
        )
      }
    }
  }

  private fun stopLocationUpdates() {
    fusedLocationClient?.removeLocationUpdates(locationCallback)
    fusedLocationClient = null
  }
}
