import 'package:flutter/material.dart';
import 'dart:async';

import 'package:flutter/services.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:u_location_driver/u_location_driver.dart';
import 'package:u_location_driver_example/send_to_host.dart';

void main() {
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
  final MethodChannel toDartChannelBackground = MethodChannel("com.jimdo.uchida001tmhr.u_location_driver/toDartBackground");
  String _messageFromIOS = "Waiting for message form Native ...";
  TextEditingController textEditingControllerFrom = TextEditingController();
  TextEditingController textEditingControllerPassword = TextEditingController();
  TextEditingController textEditingControllerTo = TextEditingController();
  late SharedPreferences prefs;

  late final AppLifecycleListener appLifecycleListener;

  @override
  void initState() {
    super.initState();
    toDartChannelForeground.setMethodCallHandler(handleMethodCall);
    toDartChannelBackground.setMethodCallHandler(handleMethodCall);
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
      onResume: () {
        debugPrint("onResume() -> activateForeground");
        uLocationDriverPlugin.activateForeground();
      },
      onHide: () {
        debugPrint("onHide()");
      },
      onInactive: () {
        debugPrint("onInactive()");
      },
      onPause: () {
        debugPrint("onPause() -> activateBackground");
        uLocationDriverPlugin.activateBackground();
      },
      onDetach: () {
        debugPrint("onDetach()");
      },
      onRestart: () {
        debugPrint("onRestart()");
      },
    );
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
          _messageFromIOS = 'Message from Native:\n${call.arguments}';
        });
        SendToHost sendToHost = SendToHost();
        sendToHost.send(call.arguments);
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

  @override
  Widget build(BuildContext context) {
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
                }),
                child: Text("Activate"),
              ),
              TextButton(
                onPressed: (() {
                  uLocationDriverPlugin.inactivate();
                  debugPrint("inactivate");
                }),
                child: Text("Inactivate"),
              ),
              Text(_messageFromIOS),
            ],
          ),
        ),
      ),
    );
  }
}
