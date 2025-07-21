package com.jimdo.uchida001tmhr.u_location_driver

import android.app.ActivityManager
import android.os.Bundle
import android.Manifest
import android.Manifest.permission.ACCESS_BACKGROUND_LOCATION
import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
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
import com.jimdo.uchida001tmhr.u_location_driver.MessageFromPluginToService.Companion.registerBackgroundIsolate
import com.jimdo.uchida001tmhr.u_location_driver.MessageFromPluginToService.Companion.startBackgroundIsolate
import com.jimdo.uchida001tmhr.u_location_driver.MessageFromPluginToService.Companion.messageServiceToPlugin
import com.jimdo.uchida001tmhr.u_location_driver.MessageFromPluginToService.Companion.activityMessenger
import com.jimdo.uchida001tmhr.u_location_driver.MessageFromPluginToService.Companion.serviceMessenger
import com.jimdo.uchida001tmhr.u_location_driver.ULocationDriverPlugin.Companion.serviceBound
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.embedding.engine.dart.DartExecutor
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.BasicMessageChannel
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.StringCodec

class MessageFromPluginToService {
  var messageType = messageLocation
  var message: Any? = null

  companion object {
    val registerBackgroundIsolate = 1000
    val startBackgroundIsolate = 1010
    val messageLocation = 2000
    val messageSendForeground = 3000
    val messageSendBackground = 3010
    val messageSendInactivate = 3020
    val messageServiceToPlugin = 4000
    var serviceMessenger: Messenger? = null
    var activityMessenger: Messenger? = null
  }

  fun sendMessageToService() {
    try {
      if (serviceBound) {
        val msg = Message.obtain(null, messageType, 0, 0)
        msg.replyTo = activityMessenger
        val bundle = Bundle()
        when (messageType) {
          messageLocation -> {
            if (message != null) {
              bundle.putParcelable("location", message as Location)
            }
          }

          registerBackgroundIsolate -> {
            if (message != null) {
              bundle.putLong("callbackHandle", message as Long)
            }
          }
        }
        msg.data = bundle
        println("ULocationDriverPlugin: sendMessageToService($messageType)")
        serviceMessenger?.send(msg)
      }
    } catch (e: RemoteException) {
      e.printStackTrace()
    }
  }
}

class ULocationDriverPlugin : FlutterPlugin, MethodCallHandler, ActivityAware {
  private lateinit var requestPermissionLauncherFineLocation: ActivityResultLauncher<String>
  private lateinit var requestPermissionLauncherBackgroundLocation: ActivityResultLauncher<String>
  private var fusedLocationClient: FusedLocationProviderClient? = null
  private lateinit var fromDartChannel: MethodChannel
  val locationCallback: LocationCallback = object : LocationCallback() {
    override fun onLocationResult(locationResult: LocationResult) {
      Handler(Looper.getMainLooper()).post {
        // ここにUIスレッドで実行したいコードを書く
        println("ULocationDriverPlugin: onLocationResult()")
        val messageFromPluginToService = MessageFromPluginToService()
        messageFromPluginToService.messageType = MessageFromPluginToService.messageLocation
        messageFromPluginToService.message = locationResult.lastLocation!!
        messageFromPluginToService.sendMessageToService()
      }
    }
  }
  private var serviceComponentName: ComponentName? = null
  private val connection = object : ServiceConnection {
    override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
      serviceMessenger = Messenger(binder)
      println("ULocationDriverPlugin: onServiceConnected() -> serviceMessenger = $serviceMessenger")
      serviceBound = true
    }

    override fun onServiceDisconnected(name: ComponentName?) {
      println("ULocationDriverPlugin: onServiceDisconnected()")
      serviceMessenger = null
      serviceBound = false
    }
  }
  // バックグラウンドエンジンの DartExecutor を保持

  companion object {
    lateinit var thisActivity: Activity
    lateinit var thisContext: Context
    var serviceBound = false
    val fromDartChannelName = "com.jimdo.uchida001tmhr.u_location_driver/fromDart"
    val toDartChannelNameForeground = "com.jimdo.uchida001tmhr.u_location_driver/toDartForeground"
    val toDartChannelNameBackground = "com.jimdo.uchida001tmhr.u_location_driver/toDartBackground"
    var attachCount = 0
    var toDartChannelToForeground: BasicMessageChannel<String>? = null
    var toDartChannelToBackground: BasicMessageChannel<String>? = null
    var binaryMessengerToDart: BinaryMessenger? = null
    var intentToControlMessageChannel: Intent? = null
    var myPackageName: String? = ""
    var backgroundFlutterEngine: FlutterEngine? = null
    var backgroundDartExecutor: DartExecutor? = null
    lateinit var eventSinkForeground: EventChannel.EventSink
    lateinit var eventSinkBackground: EventChannel.EventSink
    var flutterEngineBackground: FlutterEngine? = null
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
    println("ULocationDriverPlugin: onAttachedToEngine(): ${flutterPluginBinding.binaryMessenger}")
    flutterEngineBackground?.destroy()
    println("BackgroundLocationService -> flutterEngineBackground.destroy()")
    toDartChannelToBackground = null
    stopLocationUpdates()
    println("BackgroundLocationService -> stopLocationUpdates()")
    fromDartChannel = MethodChannel(flutterPluginBinding.binaryMessenger, fromDartChannelName)
    fromDartChannel.setMethodCallHandler(this)
    thisContext = flutterPluginBinding.applicationContext
    binaryMessengerToDart = flutterPluginBinding.binaryMessenger
    toDartChannelToForeground = BasicMessageChannel(
      binaryMessengerToDart!!,
      toDartChannelNameForeground,
      StringCodec.INSTANCE
    )
    println("ULocationDriverPlugin: onAttachedToEngine(): toDartChannelToForeground = $toDartChannelToForeground")
    /*
    intentToControlMessageChannel = Intent(thisContext, BackgroundLocationService::class.java)
    intentToControlMessageChannel?.setClassName(
      thisContext.packageName,
      "com.jimdo.uchida001tmhr.u_location_driver.BackgroundLocationService"
    )
    thisContext.startForegroundService(intentToControlMessageChannel)
    thisContext.bindService(intentToControlMessageChannel!!, connection, Context.BIND_AUTO_CREATE)
     */
  }

  override fun onAttachedToActivity(binding: ActivityPluginBinding) {
    println("ULocationDriverPlugin: onAttachedToActivity()")
    myPackageName = binding.activity.intent.getComponent()?.getPackageName()
    thisActivity = binding.activity
    activityMessenger = Messenger(ActivityHandler(thisActivity))

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
    println("ULocationDriverPlugin: onDetachedFromEngine()")
  }

  override fun onDetachedFromActivity() {
    println("ULocationDriverPlugin: onDetachedFromActivity()")
  }

  override fun onDetachedFromActivityForConfigChanges() {
    println("ULocationDriverPlugin: onDetachedFromActivityForConfigChanges()")
  }

  override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
    println("ULocationDriverPlugin: onReattachedToActivityForConfigChanges()")
  }

  override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
    println("ULocationDriverPlugin: onMethodCall() -> ${call.method}")
    val intentLocation = Intent(thisContext, BackgroundLocationService::class.java)
    when (call.method) {
      "registerBackgroundIsolate" -> {
        val messageFromPluginToService = MessageFromPluginToService()
        messageFromPluginToService.messageType = MessageFromPluginToService.registerBackgroundIsolate
        if (call.arguments != null) {
          val argCallbackHandle: Long? = call.argument<Long>("callbackHandle")
          if (argCallbackHandle != null) {
            messageFromPluginToService.message = argCallbackHandle
          }
        }
        messageFromPluginToService.sendMessageToService()
        result.success("success")
      }

      "startBackgroundIsolate" -> {
        val messageFromPluginToService = MessageFromPluginToService()
        messageFromPluginToService.messageType = MessageFromPluginToService.startBackgroundIsolate
        messageFromPluginToService.sendMessageToService()
        result.success("success")
      }

      "activateForeground" -> {
        intentToControlMessageChannel = Intent(thisContext, BackgroundLocationService::class.java)
        intentToControlMessageChannel?.setClassName(
          thisContext.packageName,
          "com.jimdo.uchida001tmhr.u_location_driver.BackgroundLocationService"
        )
        thisContext.startForegroundService(intentToControlMessageChannel)
        thisContext.bindService(intentToControlMessageChannel!!, connection, Context.BIND_AUTO_CREATE)
        val messageFromPluginToService = MessageFromPluginToService()
        messageFromPluginToService.messageType = MessageFromPluginToService.messageSendForeground
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

  fun stopLocationUpdates() {
    fusedLocationClient?.removeLocationUpdates(locationCallback)
    fusedLocationClient = null
  }
}
