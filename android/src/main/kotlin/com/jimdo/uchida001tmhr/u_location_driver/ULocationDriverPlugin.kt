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
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

class ULocationDriverPlugin : FlutterPlugin, MethodCallHandler, ActivityAware {
  /// The MethodChannel that will the communication between Flutter and native Android
  ///
  /// This local reference serves to register the plugin with the Flutter Engine and unregister it
  /// when the Flutter Engine is detached from the Activity
  private lateinit var requestPermissionLauncherFineLocation: ActivityResultLauncher<String>
  private lateinit var requestPermissionLauncherBackgroundLocation: ActivityResultLauncher<String>
  private lateinit var fusedLocationClient: FusedLocationProviderClient

  companion object {
    private lateinit var thisActivity: Activity
    private lateinit var thisContext: Context
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
    var toDartChannelForeground: MethodChannel? = null
    val toDartChannelNameForeground = "com.jimdo.uchida001tmhr.u_location_driver/toDartForeground"
    private var serviceMessenger: Messenger? = null
    private var activityMessenger: Messenger? = null

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

  override fun onAttachedToActivity(binding: ActivityPluginBinding) {
    println("onAttachedToActivity() - 0")
    myPackageName = binding.activity.intent.getComponent()?.getPackageName()
    println("onAttachedToActivity() - 1")
    thisActivity = binding.activity
    println("onAttachedToActivity() - 2")
    activityMessenger = Messenger(ActivityHandler(thisActivity))
    println("onAttachedToActivity() - 3")
    locationCallback = object : LocationCallback() {
      override fun onLocationResult(locationResult: LocationResult) {
        val _processInfo = getProcessInfo()
        println("onAttachedToActivity() - 4")
        if (_processInfo != null) {
          println("onAttachedToActivity() - 5")
          if (_processInfo.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND ||
            _processInfo.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE
          ) {
            println("onAttachedToActivity() - 6")
            informLocationToDartForeground(locationResult.lastLocation)
          } else {
            println("onAttachedToActivity() - 7")
            Handler(Looper.getMainLooper()).post {
              // ここにUIスレッドで実行したいコードを書く
              println("onAttachedToActivity() - 8")
              sendMessageToService(locationResult.lastLocation)
            }
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
    println("onDetachedFromActivityForConfigChanges()")
  }

  override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
    println("onReattachedToActivityForConfigChanges()")
  }

  override fun onDetachedFromActivity() {
    println("onDetachedFromActivity()")
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
    // val intentLocation = Intent(thisContext, BackgroundLocationService::class.java)
    // thisContext.unbindService(connection)
    // thisContext.stopService(intentLocation)
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
          thisContext.unbindService(connection)
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
          thisContext.unbindService(connection)
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
