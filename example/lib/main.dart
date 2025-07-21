import 'dart:collection';
import 'dart:isolate';
import 'dart:ui';

import 'package:flutter/material.dart';
import 'dart:async';

import 'package:flutter/services.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:u_location_driver/u_location_driver.dart';
import 'package:u_location_driver_example/send_to_host.dart';

BasicMessageChannel<String>? toDartChannelBackground;

void connectBackgroundMessageHandler() {
  final messenger = ServicesBinding.instance.defaultBinaryMessenger;
  toDartChannelBackground = BasicMessageChannel(
    "com.jimdo.uchida001tmhr.u_location_driver/toDartBackground",
    StringCodec(),
    binaryMessenger: messenger,
  );
  debugPrint("Dart: registering handler for toDartChannelBackground");
  toDartChannelBackground?.setMessageHandler((message) async {
    debugPrint("Dart: received message in background isolate: $message");
    if (message != null) {
      SendToHost sendToHost = SendToHost();
      sendToHost.send(message);
    }
    return "ACK";
  });
}

@pragma('vm:entry-point')
void backgroundEntryPoint() async {
  debugPrint("Dart: backgroundEntryPoint() called");
  // Bindingを初期化（これは必須）
  WidgetsFlutterBinding.ensureInitialized();
  // 少し遅延してから登録（これがポイント）
  await Future.delayed(const Duration(milliseconds: 500));
  connectBackgroundMessageHandler();
}

@pragma('vm:entry-point') // JIT/AOTコンパイラにエントリポイントであることを知らせる
void main() {
  // The name must be main().
  debugPrint("Dart: main()");
  WidgetsFlutterBinding.ensureInitialized();
  runApp(const MyApp());
}

class MyApp extends StatefulWidget {
  const MyApp({super.key});

  @override
  State<MyApp> createState() => _MyAppState();
}

class _MyAppState extends State<MyApp> {
  final uLocationDriverPlugin = ULocationDriver();
  late BasicMessageChannel toDartChannelForeground;
  String messageFromNative = "Waiting for message form Native ...";
  TextEditingController textEditingControllerFrom = TextEditingController();
  TextEditingController textEditingControllerPassword = TextEditingController();
  TextEditingController textEditingControllerTo = TextEditingController();
  late SharedPreferences prefs;
  ReceivePort receivePort = ReceivePort();

  late final AppLifecycleListener appLifecycleListener;

  void connectForegroundMassageHandler() {
    final messenger = ServicesBinding.instance.defaultBinaryMessenger;
    toDartChannelForeground = BasicMessageChannel(
      "com.jimdo.uchida001tmhr.u_location_driver/toDartForeground",
      StringCodec(),
      binaryMessenger: messenger,
    );
    toDartChannelForeground.setMessageHandler((message) async {
      debugPrint("Dart: received message in main isolate: $message");
      if (message != null) {
        setState(() {
          messageFromNative = message;
        });
        SendToHost sendToHost = SendToHost();
        sendToHost.send(message);
      }
      return ("ACK");
    });
  }

  Future<void> registerBackgroundIsolate() async {
    final callbackHandle = PluginUtilities.getCallbackHandle(backgroundEntryPoint);
    if (callbackHandle != null) {
      HashMap<String, dynamic> arguments = HashMap();
      arguments.addAll({"callbackHandle": callbackHandle.toRawHandle()});
      await uLocationDriverPlugin.registerBackgroundIsolate(arguments);
      debugPrint("Dart: registerBackgroundIsolate");
    }
    return;
  }

  @override
  void initState() {
    debugPrint("Dart: initState()");
    super.initState();

    appLifecycleListener = AppLifecycleListener(
      onShow: () {
        debugPrint("Dart: onShow()");
      },
      onResume: () async {
        debugPrint("Dart: onResume()");
        connectForegroundMassageHandler();
        await uLocationDriverPlugin.activateForeground();
      },
      onHide: () {
        debugPrint("Dart: onHide()");
      },
      onInactive: () async {
        debugPrint("Dart: onInactive()");
        await registerBackgroundIsolate();
        await uLocationDriverPlugin.startBackgroundIsolate();
        await uLocationDriverPlugin.activateBackground();
      },
      onPause: () {
        debugPrint("Dart: onPause()");
      },
      onDetach: () {
        debugPrint("Dart: onDetach()");
      },
      onRestart: () {
        debugPrint("Dart: onRestart()");
      },
    );
    WidgetsBinding.instance.addPostFrameCallback((_) async {
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
    });
  }

  @override
  // Widget破棄時
  void dispose() {
    // 監視の終了を登録
    appLifecycleListener.dispose();
    debugPrint("executed appLifecycleListener.dispose()");
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    debugPrint("Dart: main() -> build()");
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
                  connectForegroundMassageHandler();
                  uLocationDriverPlugin.activateForeground();
                  debugPrint("Dart: activateForeground");
                }),
                child: Text("Activate"),
              ),
              TextButton(
                onPressed: (() {
                  uLocationDriverPlugin.inactivate();
                  debugPrint("Dart: inactivate");
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
