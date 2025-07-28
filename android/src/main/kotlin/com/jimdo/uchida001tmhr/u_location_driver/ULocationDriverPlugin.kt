package com.jimdo.uchida001tmhr.u_location_driver

import android.app.Service
import android.os.Bundle
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.ServiceConnection
import android.Manifest.permission.ACCESS_BACKGROUND_LOCATION
import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.Manifest.permission.POST_NOTIFICATIONS
import android.annotation.SuppressLint
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.RemoteException
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.jimdo.uchida001tmhr.u_location_driver.MessageFromPluginToService.Companion.messageServiceToPlugin
import com.jimdo.uchida001tmhr.u_location_driver.MessageFromPluginToService.Companion.activityMessenger
import com.jimdo.uchida001tmhr.u_location_driver.MessageFromPluginToService.Companion.serviceMessenger
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
  var messageType = messageSendInactivate
  var message: Any? = null
  var backgroundService: Service? = null

  companion object {
    val registerBackgroundIsolate = 1000
    val startBackgroundIsolate = 1010
    val stopBackgroundIsolate = 1020
    val stopMainIsolate = 1030
    val messageStartLocation = 2000
    // val messageSendActivate = 3000
    val messageSendInactivate = 3020
    val messageServiceToPlugin = 4000
    var serviceMessenger: Messenger? = null
    var activityMessenger: Messenger? = null
  }

  fun sendMessageToService() {
    try {
      val msg = Message.obtain(null, messageType, 0, 0)
      msg.replyTo = activityMessenger
      val bundle = Bundle()
      when (messageType) {
        registerBackgroundIsolate -> {
          if (message != null) {
            bundle.putLong("callbackHandle", message as Long)
          }
        }
      }
      msg.data = bundle
      println("ULocationDriverPlugin: sendMessageToService($messageType)")
      serviceMessenger?.send(msg)
    } catch (e: RemoteException) {
      e.printStackTrace()
    }
  }
}

class ULocationDriverPlugin : FlutterPlugin, MethodCallHandler, ActivityAware {
  private lateinit var requestPermissionLauncherPostNotification: ActivityResultLauncher<String>
  private lateinit var requestPermissionLauncherFineLocation: ActivityResultLauncher<String>
  private lateinit var requestPermissionLauncherBackgroundLocation: ActivityResultLauncher<String>
  private lateinit var fromDartChannel: MethodChannel
  private var serviceComponentName: ComponentName? = null
  private var intentToService: Intent? = null
  private val connection = object : ServiceConnection {
    override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
      serviceMessenger = Messenger(binder)
      println("ULocationDriverPlugin: onServiceConnected() -> serviceMessenger = $serviceMessenger")
    }

    override fun onServiceDisconnected(name: ComponentName?) {
      serviceMessenger = null
      println("ULocationDriverPlugin: onServiceDisconnected()")
    }
  }

  companion object {
    @SuppressLint("StaticFieldLeak")
    lateinit var thisActivity: Activity
    @SuppressLint("StaticFieldLeak")
    lateinit var thisContext: Context
    val fromDartChannelName = "com.jimdo.uchida001tmhr.u_location_driver/fromDart"
    val toDartChannelNameForeground = "com.jimdo.uchida001tmhr.u_location_driver/toDartForeground"
    val toDartChannelNameBackground = "com.jimdo.uchida001tmhr.u_location_driver/toDartBackground"
    var attachCount = 0
    var toDartChannel: BasicMessageChannel<String>? = null
    var binaryMessengerToDart: BinaryMessenger? = null
    var myPackageName: String? = ""
    var backgroundFlutterEngine: FlutterEngine? = null
    var backgroundDartExecutor: DartExecutor? = null
    lateinit var eventSinkForeground: EventChannel.EventSink
    lateinit var eventSinkBackground: EventChannel.EventSink
    var flutterEngineBackground: FlutterEngine? = null
    private var isMainIsolateRunning = true

  }

  private fun getNotficationPermissionLocation() {
    val permissionPostNotification = ContextCompat.checkSelfPermission(thisContext, POST_NOTIFICATIONS)
    if (permissionPostNotification == PackageManager.PERMISSION_GRANTED) {
      getLocationPermissionLocation()
    } else {
      requestPermissionLauncherPostNotification.launch(POST_NOTIFICATIONS)
    }
  }

  private fun getLocationPermissionLocation() {
    val permissionFineLocation = ContextCompat.checkSelfPermission(thisContext, ACCESS_FINE_LOCATION)
    if (permissionFineLocation == PackageManager.PERMISSION_GRANTED) {
      getLocationPermissionBackgroundLocation()
    } else {
      requestPermissionLauncherFineLocation.launch(ACCESS_FINE_LOCATION)
    }
  }

  private fun getLocationPermissionBackgroundLocation() {
    val permissionBackgroundLocation = ContextCompat.checkSelfPermission(thisContext, ACCESS_BACKGROUND_LOCATION)
    if (permissionBackgroundLocation == PackageManager.PERMISSION_GRANTED) {
      Handler(Looper.getMainLooper()).post {
        val messageFromPluginToServiceStop = MessageFromPluginToService()
        messageFromPluginToServiceStop.messageType = MessageFromPluginToService.messageStartLocation
        messageFromPluginToServiceStop.sendMessageToService()
      }
    } else {
      requestPermissionLauncherBackgroundLocation.launch(ACCESS_BACKGROUND_LOCATION)
    }
  }

  override fun onAttachedToEngine(flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
    println("ULocationDriverPlugin: onAttachedToEngine(): start : ${flutterPluginBinding.binaryMessenger}")
    fromDartChannel = MethodChannel(flutterPluginBinding.binaryMessenger, fromDartChannelName)
    fromDartChannel.setMethodCallHandler(this)
    thisContext = flutterPluginBinding.applicationContext
    binaryMessengerToDart = flutterPluginBinding.binaryMessenger
    println("ULocationDriverPlugin: onAttachedToEngine(): isMainIsolateRunning = $isMainIsolateRunning")
    if (isMainIsolateRunning) {
      // Stop Background Isolate
      val messageFromPluginToServiceStop = MessageFromPluginToService()
      println("ULocationDriverPlugin: onAttachedToEngine(): stopBackgroundIsolate")
      messageFromPluginToServiceStop.messageType = MessageFromPluginToService.stopBackgroundIsolate
      messageFromPluginToServiceStop.sendMessageToService()
      // Stop Background Location Setvice
      println("ULocationDriverPlugin: onAttachedToEngine(): stopService()")
      intentToService = Intent(thisContext, BackgroundLocationService::class.java)
      intentToService?.setClassName(
        thisContext.packageName,
        "com.jimdo.uchida001tmhr.u_location_driver.BackgroundLocationService"
      )
      thisContext.stopService(intentToService)
      // Build Message Channel to Foreground
      toDartChannel = BasicMessageChannel(
        binaryMessengerToDart!!,
        toDartChannelNameForeground,
        StringCodec.INSTANCE
      )
      println("ULocationDriverPlugin: onAttachedToEngine(): end : toDartChannelToForeground = $toDartChannel")
      isMainIsolateRunning = true
    } else {
      // Build Message Channel to Background
      toDartChannel = BasicMessageChannel(
        binaryMessengerToDart!!,
        toDartChannelNameBackground,
        StringCodec.INSTANCE
      )
      println("ULocationDriverPlugin: onAttachedToEngine(): end : toDartChannelToBackground = $toDartChannel")
    }
  }

  override fun onAttachedToActivity(binding: ActivityPluginBinding) {
    println("ULocationDriverPlugin: onAttachedToActivity()")
    myPackageName = binding.activity.intent.getComponent()?.getPackageName()
    thisActivity = binding.activity
    activityMessenger = Messenger(ActivityHandler(thisActivity))

    intentToService = Intent(thisContext, BackgroundLocationService::class.java)
    intentToService?.setClassName(
      thisContext.packageName,
      "com.jimdo.uchida001tmhr.u_location_driver.BackgroundLocationService"
    )
    thisContext.startForegroundService(intentToService)
    thisContext.bindService(intentToService!!, connection, Context.BIND_AUTO_CREATE)

    requestPermissionLauncherPostNotification =
      (thisActivity as ComponentActivity).registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
          getLocationPermissionBackgroundLocation()
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
        if (isGranted) {
          Handler(Looper.getMainLooper()).post {
            val messageFromPluginToServiceStop = MessageFromPluginToService()
            messageFromPluginToServiceStop.messageType = MessageFromPluginToService.messageStartLocation
            messageFromPluginToServiceStop.sendMessageToService()
          }
        }
      }
  }

  override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
    println("ULocationDriverPlugin: onDetachedFromEngine(): Start : isMainIsolateRunning = $isMainIsolateRunning")
    if (isMainIsolateRunning) {
      Handler(Looper.getMainLooper()).post {
        val messageFromPluginToServiceStop = MessageFromPluginToService()
        messageFromPluginToServiceStop.messageType = MessageFromPluginToService.stopMainIsolate
        messageFromPluginToServiceStop.sendMessageToService()
      }
      Handler(Looper.getMainLooper()).post {
        val messageFromPluginToServiceStart = MessageFromPluginToService()
        messageFromPluginToServiceStart.messageType = MessageFromPluginToService.startBackgroundIsolate
        messageFromPluginToServiceStart.sendMessageToService()
      }
      isMainIsolateRunning = false
    } else {
    }
    println("ULocationDriverPlugin: onDetachedFromEngine(): end")
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
        println("ULocationDriverPlugin: registerBackgroundIsolate: Start")

        Handler(Looper.getMainLooper()).post {
          val messageFromPluginToService = MessageFromPluginToService()
          messageFromPluginToService.messageType = MessageFromPluginToService.registerBackgroundIsolate
          if (call.arguments != null) {
            val argCallbackHandle: Long? = call.argument<Long>("callbackHandle")
            println("ULocationDriverPlugin: registerBackgroundIsolate argCallbackHandle = ${argCallbackHandle}")
            if (argCallbackHandle != null) {
              messageFromPluginToService.message = argCallbackHandle
            }
          }
          messageFromPluginToService.sendMessageToService()
        }

        result.success("success")
        println("ULocationDriverPlugin: registerBackgroundIsolate: end")
      }

      "activate" -> {
        getNotficationPermissionLocation()
        result.success("success")
      }

      "inactivate" -> {
        Handler(Looper.getMainLooper()).post {
          val messageFromPluginToService = MessageFromPluginToService()
          messageFromPluginToService.messageType = MessageFromPluginToService.messageSendInactivate
          messageFromPluginToService.sendMessageToService()
        }
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

}
