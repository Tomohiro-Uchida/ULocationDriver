package com.jimdo.uchida001tmhr.u_location_driver

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
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresPermission
import androidx.compose.ui.window.application
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.embedding.engine.dart.DartExecutor
import io.flutter.embedding.engine.loader.FlutterLoader
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.view.FlutterCallbackInformation
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

class ULocationDriverPlugin : FlutterPlugin, MethodCallHandler, ActivityAware {
  /// The MethodChannel that will the communication between Flutter and native Android
  ///
  /// This local reference serves to register the plugin with the Flutter Engine and unregister it
  /// when the Flutter Engine is detached from the Activity
  private lateinit var fromDartChannel: MethodChannel
  private lateinit var thisActivity: Activity
  private lateinit var thisContext: Context
  private lateinit var locationCallback: LocationCallback
  private lateinit var fusedLocationClient: FusedLocationProviderClient
  private lateinit var requestPermissionLauncherFineLocation: ActivityResultLauncher<String>
  private lateinit var requestPermissionLauncherBackgroundLocation: ActivityResultLauncher<String>
  private var bound = false
  private var serviceMessenger: Messenger? = null
  private var activityMessenger: Messenger? = null
  private var flutterEngine: FlutterEngine? = null

  companion object {
    var isScreenActive = false
    var toDartChannelForeground: MethodChannel? = null
    var toDartChannelBackground: MethodChannel? = null
    val toDartChannelNameForegournd = "com.jimdo.uchida001tmhr.u_location_driver/toDartForeground"
    val toDartChannelNameBackground = "com.jimdo.uchida001tmhr.u_location_driver/toDartBackground"
    // val messageInformToDartForeground = 1000
    val messageInformToDartBackground = 1001
    // val messageChangeToForeground = 2000
    // val messageChangeToBackground = 2001
    val CALLBACK_HANDLE_KEY = "callback_handle_key"
  }

  private fun getLocationPermissionForegroundLocation() {
    val permissionFineLocation = ContextCompat.checkSelfPermission(
      thisContext.applicationContext,
      Manifest.permission.ACCESS_FINE_LOCATION
    )
    print("permissionFineLocation=$permissionFineLocation")
    if (permissionFineLocation == PackageManager.PERMISSION_GRANTED) {
      getLocationPermissionBackgroundLocation()
    } else {
      requestPermissionLauncherFineLocation.launch(ACCESS_FINE_LOCATION)
    }
  }

  private fun getLocationPermissionBackgroundLocation() {
    val permissionBackgroundLocation = ContextCompat.checkSelfPermission(
      thisContext.applicationContext,
      Manifest.permission.ACCESS_BACKGROUND_LOCATION
    )
    print("permissionBackgroundLocation=$permissionBackgroundLocation")
    if (permissionBackgroundLocation == PackageManager.PERMISSION_GRANTED) {
      requestDeviceLocation()
    } else {
      requestPermissionLauncherBackgroundLocation.launch(ACCESS_BACKGROUND_LOCATION)
    }
  }

  override fun onAttachedToActivity(binding: ActivityPluginBinding) {
    thisActivity = binding.activity
    activityMessenger = Messenger(ActivityHandler(thisActivity))
    locationCallback = object : LocationCallback() {
      override fun onLocationResult(locationResult: LocationResult) {
        if (isScreenActive) {
          informLocationToDartForeground(locationResult.lastLocation)
        } else {
          Handler(Looper.getMainLooper()).post {
            // ここにUIスレッドで実行したいコードを書く
            sendMessageToService(locationResult.lastLocation)
          }
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

  override fun onDetachedFromActivityForConfigChanges() {
  }

  override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
  }

  override fun onDetachedFromActivity() {
  }

  override fun onAttachedToEngine(flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
    print("onAttachedToEngine()")
    fromDartChannel =
      MethodChannel(flutterPluginBinding.binaryMessenger, "com.jimdo.uchida001tmhr.u_location_driver/fromDart")
    toDartChannelForeground = MethodChannel(flutterPluginBinding.binaryMessenger, toDartChannelNameForegournd)
    fromDartChannel.setMethodCallHandler(this)
    thisContext = flutterPluginBinding.applicationContext
  }

  private val connection = object : ServiceConnection {
    override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
      serviceMessenger = Messenger(binder)
      bound = true
    }

    override fun onServiceDisconnected(name: ComponentName?) {
      serviceMessenger = null
      bound = false
    }
  }

  override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
    print("onMethodCall()")
    val intentLocation = Intent(thisContext, BackgroundLocationService::class.java)
    when (call.method) {
      "activateForeground" -> {
        isScreenActive = true
        stopFlutterBackgroundEngine()
        // thisContext.startForegroundService(intentLocation)
        // thisContext.bindService(intentLocation, connection, Context.BIND_AUTO_CREATE)
        // serviceChangeToForeground()
        getLocationPermissionForegroundLocation()
        result.success("success")
      }

      "activateBackground" -> {
        isScreenActive = false
        startFlutterBackgroundEngine(call.argument<Long>(CALLBACK_HANDLE_KEY)!!)
        thisContext.startForegroundService(intentLocation)
        thisContext.bindService(intentLocation, connection, Context.BIND_AUTO_CREATE)
        // serviceChangeToBackground()
        getLocationPermissionForegroundLocation()
        result.success("success")
      }

      "inactivate" -> {
        isScreenActive = false
        stopFlutterBackgroundEngine()
        thisContext.stopService(intentLocation)
        stopLocationUpdates()
        result.success("success")
      }

      else ->
        result.notImplemented()
    }
  }

  override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
    fromDartChannel.setMethodCallHandler(null)
  }

  internal class ActivityHandler(activity: Activity) : Handler(Looper.getMainLooper()) {

    val messageServiceToPlugin = 2

    override fun handleMessage(msg: Message) {
      when (msg.what) {
        messageServiceToPlugin -> {
          //something
        }

        else -> super.handleMessage(msg)
      }
    }
  }

  fun sendMessageToService(location: Location?) {
    try {
      val msg = Message.obtain(null, messageInformToDartBackground, 0, 0)
      msg.replyTo = activityMessenger
      msg.obj = location
      serviceMessenger?.send(msg)
    } catch (e: RemoteException) {
      e.printStackTrace()
    }
  }

  fun informLocationToDartForeground(location: Location?) {
    val locale = Locale.JAPAN
    val dateTimeFormatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withLocale(locale)
    val dateString = dateTimeFormatter.format(LocalDateTime.now())
    val message = "$dateString,${location?.latitude},${location?.longitude}"
    try {
      toDartChannelForeground?.invokeMethod("informLocationToDartForeground", message)
    } catch (e: Exception) {
      print(e)
    }
  }

  /*
  fun serviceChangeToForeground() {
    try {
      val msg = Message.obtain(null, messageChangeToForeground, 0, 0)
      msg.replyTo = activityMessenger
      serviceMessenger?.send(msg)
    } catch (e: RemoteException) {
      e.printStackTrace()
    }
  }

  fun serviceChangeToBackground() {
    try {
      val msg = Message.obtain(null, messageChangeToBackground, 0, 0)
      msg.replyTo = activityMessenger
      serviceMessenger?.send(msg)
    } catch (e: RemoteException) {
      e.printStackTrace()
    }
  }
   */

  @RequiresPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
  private fun requestDeviceLocation() {
    val permissionFineLocation = ContextCompat.checkSelfPermission(
      thisContext.applicationContext,
      Manifest.permission.ACCESS_FINE_LOCATION
    )
    print("permissionFineLocation=$permissionFineLocation")
    val permissionBackgroundLocation = ContextCompat.checkSelfPermission(
      thisContext.applicationContext,
      Manifest.permission.ACCESS_BACKGROUND_LOCATION
    )
    print("permissionBackgroundLocation=$permissionBackgroundLocation")
    if (permissionFineLocation == PackageManager.PERMISSION_GRANTED &&
      permissionBackgroundLocation == PackageManager.PERMISSION_GRANTED
    ) {
      fusedLocationClient = LocationServices.getFusedLocationProviderClient(thisActivity)
      /*
      try {
        val locationResult = fusedLocationClient.lastLocation
        locationResult.addOnCompleteListener { task ->
          if (task.isSuccessful) {
            val lastKnownLocation = task.result
            if (lastKnownLocation != null) {
              sendMessageToService(lastKnownLocation)
            }
          }
        }
      } catch (e: SecurityException) {
        print(e)
      }
       */
      startLocationUpdates()
    }
  }

  @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
  private fun startLocationUpdates() {
    if (isScreenActive) {
      fusedLocationClient.requestLocationUpdates(
        LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 10 * 1000 /*10秒*/)
          .setMinUpdateIntervalMillis(5 * 1000 /*5秒*/)
          .build(), locationCallback, Looper.getMainLooper()
      )
    } else {
      fusedLocationClient.requestLocationUpdates(
        LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 30 * 1000 /*30秒*/)
          .setMinUpdateIntervalMillis(10 * 1000 /*10秒*/)
          .build(), locationCallback, Looper.getMainLooper()
      )
    }
  }

  private fun stopLocationUpdates() {
    fusedLocationClient.removeLocationUpdates(locationCallback)
  }

  /**
   * 新しいFlutterEngineを起動し、Dartのバックグラウンドエントリポイントを実行します。
   */
  private fun startFlutterBackgroundEngine(callbackHandle: Long) {
    val app: ULApplication = getApplication() as ULApplication
    // FlutterEngineGroupから新しいEngineを作成し、指定されたDartエントリポイントを実行
    flutterEngine = app.flutterEngineGroup.createAndRunEngine(
      thisContext,
      DartExecutor.DartEntrypoint(
        FlutterLoader().findAppBundlePath(),
        FlutterCallbackInformation.lookupCallbackInformation(callbackHandle).callbackName
      )
    )

    // バックグラウンドEngineと通信するためのMethodChannelを初期化
    toDartChannelBackground = MethodChannel(flutterEngine!!.dartExecutor.binaryMessenger, toDartChannelNameBackground)
    Log.d("BackgroundService", "Flutter background engine started with handle: $callbackHandle.")


    /*
    val flutterLoader = FlutterLoader().apply {
      startInitialization(thisContext)
      ensureInitializationComplete(thisContext, arrayOf())
    }

    val callbackInfo = FlutterCallbackInformation.lookupCallbackInformation(callbackHandle)

    if (callbackInfo == null) {
      Log.e("ERROR:", "failed to find callback info")
      return
    }

    val args = DartExecutor.DartCallback(
      thisContext.getAssets(),
      flutterLoader.findAppBundlePath(),
      callbackInfo
    )

    flutterEngine = FlutterEngine(thisContext).apply {
      getDartExecutor().executeDartCallback(args)
    }
     */

  }

  private fun stopFlutterBackgroundEngine() {
    flutterEngine?.destroy()
    flutterEngine = null
  }

}
