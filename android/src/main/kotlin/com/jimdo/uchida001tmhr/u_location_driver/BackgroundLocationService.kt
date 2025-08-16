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
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.jimdo.uchida001tmhr.u_location_driver.MessageFromPluginToService.Companion.messageStartLocation
import com.jimdo.uchida001tmhr.u_location_driver.MessageFromPluginToService.Companion.messageSendInactivate
import com.jimdo.uchida001tmhr.u_location_driver.ULocationDriverPlugin.Companion.myPackageName
import com.jimdo.uchida001tmhr.u_location_driver.ULocationDriverPlugin.Companion.thisContext
import com.jimdo.uchida001tmhr.u_location_driver.ULocationDriverPlugin.Companion.toDartChannel
import com.jimdo.uchida001tmhr.u_location_driver.ULocationDriverPlugin.Companion.toDartChannelName
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.embedding.engine.FlutterJNI
import io.flutter.embedding.engine.loader.FlutterLoader
import io.flutter.embedding.engine.dart.DartExecutor
import io.flutter.embedding.engine.dart.DartExecutor.DartEntrypoint
import io.flutter.plugin.common.BasicMessageChannel
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.view.FlutterCallbackInformation
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.io.println

class BackgroundLocationService : Service() {
  val serviceContext = this
  var fusedLocationClients = mutableListOf<FusedLocationProviderClient>()

  override fun onBind(intent: Intent): IBinder {
    println("BackgroundLocationService: onBind()")
    val serviceMessenger = Messenger(ServiceHandler(this))
    return serviceMessenger.binder
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

  fun loadFlutterEngine() {
    val processInfo = getProcessInfo()
    if (processInfo?.importance != ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {
      println("BackgroundLocationService: loadFlutterEngine()")
      val flutterEngine = FlutterEngine(applicationContext)
      val dartEntrypoint = DartExecutor.DartEntrypoint.createDefault()
      flutterEngine.dartExecutor.executeDartEntrypoint(dartEntrypoint)
    }
  }

  fun informLocationToDart(location: Location?) {
    loadFlutterEngine()
    val locale = Locale.JAPAN
    val dateTimeFormatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withLocale(locale)
    val dateString = dateTimeFormatter.format(LocalDateTime.now())
    val message = "$dateString,${location?.latitude},${location?.longitude}"
    if (toDartChannel != null) {
      toDartChannel?.invokeMethod("location", message)
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
      getCurrentLocation()
      startLocationUpdates()
    }
  }

  val locationCallback: LocationCallback = object : LocationCallback() {
    override fun onLocationResult(locationResult: LocationResult) {
      println("ULocationDriverPlugin: onLocationResult()")
      informLocationToDart(locationResult.lastLocation!!)
    }
  }

  fun getCurrentLocation() {
    fusedLocationClients.forEach { it ->
      it.getCurrentLocation(
        CurrentLocationRequest.Builder().build(),
        null
      ).addOnSuccessListener { it ->
        println("ULocationDriverPlugin: getCurrentLocation()")
        informLocationToDart(it)
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
        messageSendInactivate -> {
          stopLocationUpdates()
          println("BackgroundLocationService: stopLocationUpdates()")
        }

        messageStartLocation -> {
          requestDeviceLocation()
          println("BackgroundLocationService: requestDeviceLocation()")
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
    stopLocationUpdates()
    super.onDestroy()
  }

  override fun onUnbind(intent: Intent?): Boolean {
    return super.onUnbind(intent)
  }

}