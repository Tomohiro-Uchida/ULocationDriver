import 'dart:io';

import 'package:flutter/cupertino.dart';
import 'package:flutter/services.dart';
import 'package:u_location_driver_example/send_to_host.dart';

// @pragma('vm:entry-point') はR8/ProGuardによる難読化を防ぐために重要
// この関数がバックグラウンドEngineのDartエントリポイントになります。
@pragma('vm:entry-point')
void backgroundLocationMain() {
  if (Platform.isAndroid) {
    // Flutter Engineが初期化されたことを確認し、WidgetsFlutterBindingを有効にする
    // これにより、MethodChannelなどのFlutterサービスがバックグラウンドでも利用可能になります。
    WidgetsFlutterBinding.ensureInitialized();

    // Android側（BackgroundLocationService）からのイベントを受信するチャンネル
    const MethodChannel backgroundChannel = MethodChannel("com.jimdo.uchida001tmhr.u_location_driver/toDartBackground");

    // Androidからのメソッド呼び出しをリッスンするハンドラを設定
    backgroundChannel.setMethodCallHandler((MethodCall call) async {
      switch (call.method) {
        case 'informLocationToDartBackground':
          SendToHost sendToHost = SendToHost();
          sendToHost.send(call.arguments);
          debugPrint('Dart Background: Received location update: ${call.arguments}');
          break;
        default:
          debugPrint('Dart Background: Unknown method call: ${call.method}');
          break;
      }
      return null;
    });
    debugPrint('Dart Background: backgroundLocationMain started.');
  }
}