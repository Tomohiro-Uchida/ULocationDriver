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

class ULocationDriverPlugin : FlutterPlugin, MethodCallHandler, ActivityAware {
  val DART_ENTRYPOINT_FUNCTION_NAME_MAIN = "main"
  private var flutterEngineMain: FlutterEngine? = null
  private lateinit var requestPermissionLauncherFineLocation: ActivityResultLauncher<String>
  private lateinit var requestPermissionLauncherBackgroundLocation: ActivityResultLauncher<String>
  private lateinit var fusedLocationClient: FusedLocationProviderClient

  companion object {
    private lateinit var thisActivity: Activity
    private lateinit var thisContext: Context
    var toDartChannelForeground: MethodChannel? = null
    val toDartChannelNameForeground = "com.jimdo.uchida001tmhr.u_location_driver/toDartForeground"
    // val messageInformToDartForeground = 1000
    val messageInformToDartBackground = 1001
    var myPackageName: String? = ""

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
  }

  private lateinit var fromDartChannel: MethodChannel
  private lateinit var locationCallback: LocationCallback
  private var serviceComponentName: ComponentName? = null
  private var bound = false
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
  private var serviceMessenger: Messenger? = null
  private var activityMessenger: Messenger? = null

  private fun getLocationPermissionLocation() {
    val permissionFineLocation = ContextCompat.checkSelfPermission(
      thisContext.applicationContext,
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
      thisContext.applicationContext,
      Manifest.permission.ACCESS_BACKGROUND_LOCATION
    )
    if (permissionBackgroundLocation == PackageManager.PERMISSION_GRANTED) {
      requestDeviceLocation()
    } else {
      requestPermissionLauncherBackgroundLocation.launch(ACCESS_BACKGROUND_LOCATION)
    }
  }

  fun restartMainFultterEngine() {
    if (flutterEngineMain == null) {
      // FlutterEngineを初期化
      flutterEngineMain = FlutterEngine(thisContext)
      // Dartエントリポイントを指定して実行
      val flutterLoader = FlutterLoader()
      flutterLoader.startInitialization(thisContext)
      flutterLoader.ensureInitializationComplete(thisContext, arrayOf())
      val path = flutterLoader.findAppBundlePath()
      val dartEntrypoint = DartExecutor.DartEntrypoint(path, DART_ENTRYPOINT_FUNCTION_NAME_MAIN)
      flutterEngineMain?.dartExecutor?.executeDartEntrypoint(dartEntrypoint)
      toDartChannelForeground = MethodChannel(flutterEngineMain!!.dartExecutor.binaryMessenger, toDartChannelNameForeground)
    }
  }

  override fun onAttachedToEngine(flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
    println("onAttachedToEngine()")
    fromDartChannel =
      MethodChannel(flutterPluginBinding.binaryMessenger, "com.jimdo.uchida001tmhr.u_location_driver/fromDart")
    fromDartChannel.setMethodCallHandler(this)
    if (toDartChannelForeground == null) {
      toDartChannelForeground = MethodChannel(flutterPluginBinding.binaryMessenger, toDartChannelNameForeground)
    }
    thisContext = flutterPluginBinding.applicationContext
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
        val _processInfo = getProcessInfo()
        println("onLocationResult() - 1")
        if (_processInfo != null) {
          println("onLocationResult() - 2")
          if (_processInfo.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND ||
            _processInfo.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE
          ) {
            println("onLocationResult() - 3")
            informLocationToDartForeground(locationResult.lastLocation)
          } else {
            println("onLocationResult() - 4")
            Handler(Looper.getMainLooper()).post {
              // ここにUIスレッドで実行したいコードを書く
              println("onLocationResult() - 5")
              sendMessageToService(locationResult.lastLocation)
            }
          }
        }
      }
    }

    println("onAttachedToActivity() - 5")
    val intentLocation = Intent(thisContext, BackgroundLocationService::class.java)
    if (bound) {
      println("onAttachedToActivity() - 6")
      thisContext.unbindService(connection)
    }
    println("onAttachedToActivity() - 6")
    thisContext.stopService(intentLocation)
    println("onAttachedToActivity() - 7 -> restartMainFultterEngine()")
    restartMainFultterEngine()

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
    println("onDetachedFromActivityForConfigChanges()")
  }

  override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
    println("onReattachedToActivityForConfigChanges()")
  }

  override fun onDetachedFromActivity() {
    println("onDetachedFromActivity()")
  }


  override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
    println("onMethodCall()")
    val intentLocation = Intent(thisContext, BackgroundLocationService::class.java)
    when (call.method) {
      "activateForeground" -> {
        val _error = if (serviceComponentName == null) {
          false
        } else {
          serviceComponentName = null
          if (bound) {
            thisContext.unbindService(connection)
          }
          thisContext.stopService(intentLocation)
        }
        getLocationPermissionLocation()
        if (_error) {
          result.error("-1000", "Activate Failed", "Failed in activateForeground")
        } else {
          result.success("success")
        }
      }

      "activateBackground" -> {
        toDartChannelForeground = null
        serviceComponentName = thisContext.startForegroundService(intentLocation)
        val _success = thisContext.bindService(intentLocation, connection, Context.BIND_AUTO_CREATE)
        getLocationPermissionLocation()
        if (serviceComponentName != null && _success) {
          result.success("success")
        } else {
          result.error("-1010", "Activate Faild", "Failed in activateBackground")
        }
      }

      "inactivate" -> {
        val _error = if (serviceComponentName == null) {
          false
        } else {
          serviceComponentName = null
          if (bound) {
            thisContext.unbindService(connection)
          }
          thisContext.stopService(intentLocation)
        }
        stopLocationUpdates()
        if (_error) {
          result.error("-1020", "Inactivate Failed", "Failed in inactivate")
        } else {
          result.success("success")
        }
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
      println(e)
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
      permissionBackgroundLocation == PackageManager.PERMISSION_GRANTED
    ) {
      fusedLocationClient = LocationServices.getFusedLocationProviderClient(thisActivity)
      startLocationUpdates()
    }
  }

  @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
  private fun startLocationUpdates() {
    if (::fusedLocationClient.isInitialized) {
      val _processInfo = getProcessInfo()
      if (_processInfo?.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND ||
        _processInfo?.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE
      ) {
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
  }

  private fun stopLocationUpdates() {
    fusedLocationClient.removeLocationUpdates(locationCallback)
  }

}
