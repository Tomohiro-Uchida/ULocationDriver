package com.jimdo.uchida001tmhr.u_location_driver

import io.flutter.app.FlutterApplication
import io.flutter.embedding.engine.FlutterEngineGroup

class ULApplication : FlutterApplication() {

  // 複数のEngineを管理するためのEngineGroup
  lateinit var flutterEngineGroup: FlutterEngineGroup

  override fun onCreate() {
    super.onCreate()
    flutterEngineGroup = FlutterEngineGroup(this)

    // メインのFlutterEngineをプリロードする場合 (任意)
    // val mainEngine = flutterEngineGroup.createAndRunDefaultEngine(this)
    // FlutterEngineCache.getInstance().put("main_engine_id", mainEngine)

    // バックグラウンドEngineで使用するコールバックハンドルの保存
    // Dart側からこのApplicationのMethodChannel経由で呼び出す
    // これはメインのFlutterEngineがアタッチされたときに初期化される
  }
}