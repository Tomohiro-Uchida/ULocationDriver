package com.jimdo.uchida001tmhr.u_location_driver

import io.flutter.app.FlutterApplication
import io.flutter.embedding.engine.FlutterEngineGroup

class EngineGroupManager : FlutterApplication() {

  // 複数のEngineを管理するためのEngineGroup
  // これをMyApplicationクラスのプロパティとして持つことで、アプリケーション全体で共有できます。
  lateinit var flutterEngineGroup: FlutterEngineGroup

  override fun onCreate() {
    super.onCreate()
    // アプリケーションが起動する際にFlutterEngineGroupを初期化
    flutterEngineGroup = FlutterEngineGroup(this)
  }
}