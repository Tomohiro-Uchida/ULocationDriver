package com.jimdo.uchida001tmhr.u_location_driver

import android.Manifest
import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.Manifest.permission.ACCESS_BACKGROUND_LOCATION
import android.R
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
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

/** ULocationDriverPlugin */
class ULocationDriverPlugin : FlutterPlugin, MethodCallHandler, ActivityAware {
  /// The MethodChannel that will the communication between Flutter and native Android
  ///
  /// This local reference serves to register the plugin with the Flutter Engine and unregister it
  /// when the Flutter Engine is detached from the Activity
  private lateinit var fromChannel: MethodChannel
  private lateinit var toChannel: MethodChannel
  var isScreenActive = false
  private lateinit var thisActivity: Activity
  private lateinit var thisContext: Context
  private lateinit var locationCallback: LocationCallback
  private lateinit var fusedLocationClient: FusedLocationProviderClient
  private lateinit var requestPermissionLauncherFineLocation: ActivityResultLauncher<String>
  private lateinit var requestPermissionLauncherBackgroundLocation: ActivityResultLauncher<String>

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
    locationCallback = object : LocationCallback() {
      override fun onLocationResult(locationResult: LocationResult) {
        locationResult.lastLocation.also {
          Handler(Looper.getMainLooper()).post {
            // ここにUIスレッドで実行したいコードを書く
            informLocationToDart(it)
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
    fromChannel =
      MethodChannel(flutterPluginBinding.binaryMessenger, "com.jimdo.uchida001tmhr.u_location_driver/fromDart")
    toChannel = MethodChannel(flutterPluginBinding.binaryMessenger, "com.jimdo.uchida001tmhr.u_location_driver/toDart")
    fromChannel.setMethodCallHandler(this)
    // toChannel.setMethodCallHandler(this)
    thisContext = flutterPluginBinding.applicationContext
  }

  override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
    val intentLocation = Intent(thisContext, BackgroundLocationService::class.java)
    when (call.method) {
      "activateForeground" -> {
        isScreenActive = true
        getLocationPermissionForegroundLocation()
        result.success("success")
      }

      "activateBackground" -> {
        isScreenActive = false
        thisContext.startForegroundService(intentLocation)
        getLocationPermissionForegroundLocation()
        result.success("success")
      }

      "inactivate" -> {
        isScreenActive = false
        thisContext.stopService(intentLocation)
        stopLocationUpdates()
        result.success("success")
      }

      else ->
        result.notImplemented()
    }
  }

  override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
    fromChannel.setMethodCallHandler(null)
  }

  fun informLocationToDart(location: Location?) {
    val locale = Locale.JAPAN
    val dateTimeFormatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withLocale(locale)
    val dateString = dateTimeFormatter.format(LocalDateTime.now())
    val message = "$dateString,${location?.latitude},${location?.longitude}"
    try {
      toChannel.invokeMethod("informLocationToDart", message)
    } catch (e: Exception) {
      print(e)
    }
  }

  @SuppressLint("MissingPermission")
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
      try {
        val locationResult = fusedLocationClient.lastLocation
        locationResult.addOnCompleteListener { task ->
          if (task.isSuccessful) {
            val lastKnownLocation = task.result
            if (lastKnownLocation != null) {
              informLocationToDart(lastKnownLocation)
            }
          }
        }
        startLocationUpdates()
      } catch (e: SecurityException) {
        print(e)
      }
    }
  }

  @SuppressLint("MissingPermission")
  private fun startLocationUpdates() {
    locationCallback.also {
      if (isScreenActive) {
        fusedLocationClient.requestLocationUpdates(
          LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 10 * 1000 /*10秒*/)
            .setMinUpdateIntervalMillis(5 * 1000 /*5秒*/)
            .build(), it, Looper.getMainLooper()
        )
      } else {
        fusedLocationClient.requestLocationUpdates(
          LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 30 * 1000 /*30秒*/)
            .setMinUpdateIntervalMillis(10 * 1000 /*10秒*/)
            .build(), it, Looper.getMainLooper()
        )
      }
    }
  }

  @SuppressLint("MissingPermission")
  private fun stopLocationUpdates() {
    fusedLocationClient.removeLocationUpdates(locationCallback)
  }

}