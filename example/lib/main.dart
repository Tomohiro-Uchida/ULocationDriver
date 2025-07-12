import 'package:flutter/material.dart';
import 'dart:async';

import 'package:flutter/services.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:u_location_driver/u_location_driver.dart';
import 'package:u_location_driver_example/send_to_host.dart';

// Nativeからのメソッド呼び出しを処理する関数
Future<void> handleMethodCallBackground(MethodCall call) async {
  switch (call.method) {
    case 'informLocationToDartForeground':
      break;
    case 'informLocationToDartBackground':
      SendToHost sendToHost = SendToHost();
      sendToHost.send(call.arguments);
      break;
    default:
    // 未知のメソッドが呼ばれた場合
      debugPrint('Unknown method: ${call.method}');
      throw MissingPluginException('Unknown method ${call.method}');
  }
}

// Dartのバックグラウンドエントリポイント
// トップレベル（静的）関数である必要があります。
@pragma('vm:entry-point') // JIT/AOTコンパイラにエントリポイントであることを知らせる
void backgroundMain() {  // The name must be backgroundMain().

  // ここに、バックグラウンドで実行したいFlutter/Dartの処理を書きます。
  // 例えば、HTTPリクエストの送信、データベース操作、ローカル通知の送信など。
  debugPrint("Flutter background task started!");

  // SharedPreferencesなどのプラグインも通常通り利用できます。
  // await SharedPreferences.getInstance().then((prefs) {
  //   prefs.setInt('background_run_count', (prefs.getInt('background_run_count') ?? 0) + 1);
  // });

  // 必要に応じて、Android Native側に結果を返すこともできます。
  // MethodChannel channel = MethodChannel('your_plugin_channel_name');
  // channel.invokeMethod('backgroundTaskCompleted', {'status': 'success'});
  WidgetsFlutterBinding.ensureInitialized();

  final MethodChannel toDartChannelBackground = MethodChannel(
      "com.jimdo.uchida001tmhr.u_location_driver/toDartBackground");

  toDartChannelBackground.setMethodCallHandler(handleMethodCallBackground);

  debugPrint("Flutter background task finished!");

}

@pragma('vm:entry-point') // JIT/AOTコンパイラにエントリポイントであることを知らせる
void main() { // The name must be main().
  debugPrint("main()");
  runApp(const MyApp());
}

class MyApp extends StatefulWidget {
  const MyApp({super.key});

  @override
  State<MyApp> createState() => _MyAppState();
}

class _MyAppState extends State<MyApp> {
  final uLocationDriverPlugin = ULocationDriver();
  final MethodChannel toDartChannelForeground = MethodChannel("com.jimdo.uchida001tmhr.u_location_driver/toDartForeground");
  String messageFromNative = "Waiting for message form Native ...";
  TextEditingController textEditingControllerFrom = TextEditingController();
  TextEditingController textEditingControllerPassword = TextEditingController();
  TextEditingController textEditingControllerTo = TextEditingController();
  late SharedPreferences prefs;

  late final AppLifecycleListener appLifecycleListener;

  @override
  void initState() {
    debugPrint("initState()");
    super.initState();
    SharedPreferences.getInstance().then((prefs) {
      this.prefs = prefs;
      String? username = prefs.getString("fromAddress");
      String? password = prefs.getString("password");
      String? toAddress = prefs.getString("toAddress");
      if (username != null &&
          username.isNotEmpty &&
          password != null &&
          password.isNotEmpty &&
          toAddress != null &&
          toAddress.isNotEmpty) {
        textEditingControllerFrom.text = username;
        textEditingControllerPassword.text = password;
        textEditingControllerTo.text = toAddress;
      }
    });

    appLifecycleListener = AppLifecycleListener(
      onShow: () {
        debugPrint("onShow()");
      },
      onResume: () async {
        debugPrint("onResume()");
        var activateByUser = prefs.getBool("activatedByUser");
        if (activateByUser != null && activateByUser) {
          try {
            debugPrint("onResume() -> $activateByUser -> activateForeground");
            await uLocationDriverPlugin.activateForeground();
          } catch (_) {
          }
        }
      },
      onHide: () {
        debugPrint("onHide()");
      },
      onInactive: () async {
        debugPrint("onInactive()");
        var activateByUser = prefs.getBool("activatedByUser");
        if (activateByUser != null && activateByUser) {
          try {
            debugPrint("onInactive() -> $activateByUser -> activateBackground");
            final result = await uLocationDriverPlugin.activateBackground();
            if (result == "success") {
              SystemNavigator.pop();
            }
          } catch (_) {}
        }
      },
      onPause: () {
        debugPrint("onPause()");
      },
      onDetach: () {
        debugPrint("onDetach()");
      },
      onRestart: () {
        debugPrint("onRestart()");
      },
    );
    WidgetsBinding.instance.addPostFrameCallback((_) {
      debugPrint("addPostFrameCallback() -> setMethodCallHandler()");
      toDartChannelForeground.setMethodCallHandler(handleMethodCall);
    });
  }

  @override
  // Widget破棄時
  void dispose() {
    // 監視の終了を登録
    appLifecycleListener.dispose();
    super.dispose();
  }

  // Nativeからのメソッド呼び出しを処理する関数
  Future<void> handleMethodCall(MethodCall call) async {
    switch (call.method) {
      case 'informLocationToDartForeground':
        setState(() {
          messageFromNative = 'Message from Native:\n${call.arguments}';
        });
        SendToHost sendToHost = SendToHost();
        sendToHost.send(call.arguments);
        break;
      case 'informLocationToDartBackground':
        break;
      default:
      // 未知のメソッドが呼ばれた場合
        debugPrint('Unknown method: ${call.method}');
        throw MissingPluginException('Unknown method ${call.method}');
    }
  }

  @override
  Widget build(BuildContext context) {
    debugPrint("main() -> build()");
    return MaterialApp(
      home: Scaffold(
        appBar: AppBar(title: const Text("uLocationDriverPlugin")),
        body: Center(
          child: Column(
            children: [
              TextFormField(
                decoration: InputDecoration(labelText: "From: "),
                controller: textEditingControllerFrom,
                keyboardType: TextInputType.emailAddress,
                onChanged: ((value) async {
                  textEditingControllerFrom.text = value;
                  prefs.setString("fromAddress", value);
                }),
              ),
              TextFormField(
                decoration: InputDecoration(labelText: "Password: "),
                controller: textEditingControllerPassword,
                keyboardType: TextInputType.visiblePassword,
                onChanged: ((value) async {
                  textEditingControllerPassword.text = value;
                  prefs.setString("password", value);
                }),
              ),
              TextFormField(
                decoration: InputDecoration(labelText: "To: "),
                controller: textEditingControllerTo,
                keyboardType: TextInputType.emailAddress,
                onChanged: ((value) async {
                  textEditingControllerTo.text = value;
                  prefs.setString("toAddress", value);
                }),
              ),
              TextButton(
                onPressed: (() {
                  uLocationDriverPlugin.activateForeground();
                  debugPrint("activateForeground");
                  prefs.setBool("activatedByUser", true);
                }),
                child: Text("Activate"),
              ),
              TextButton(
                onPressed: (() {
                  uLocationDriverPlugin.inactivate();
                  debugPrint("inactivate");
                  prefs.setBool("activatedByUser", false);
                }),
                child: Text("Inactivate"),
              ),
              Text(key: UniqueKey(), messageFromNative), // <- UniqueKey() must be used,
            ],
          ),
        ),
      ),
    );
  }
}
