package com.jimdo.uchida001tmhr.u_location_driver_example

import android.app.Application
import android.content.Context
import com.jimdo.uchida001tmhr.u_location_driver_example.BackgroundEngineRegistry.backgroundEngine
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.embedding.engine.FlutterEngineCache
import io.flutter.embedding.engine.loader.FlutterLoader
import io.flutter.plugins.GeneratedPluginRegistrant


object BackgroundEngineRegistry {
  var backgroundEngine: FlutterEngine? = null
}

class MyApplication : Application() {

  override fun onCreate() {
    super.onCreate()
    FlutterLoader().startInitialization(this)
  }

  fun getOrCreateEngine(context: Context): FlutterEngine? {
    if (backgroundEngine == null) {
      backgroundEngine = FlutterEngine(context)
      GeneratedPluginRegistrant.registerWith(backgroundEngine!!)
    }
    return backgroundEngine
  }
}
