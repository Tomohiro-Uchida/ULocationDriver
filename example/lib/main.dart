
import 'dart:collection';
import 'dart:isolate';
import 'dart:ui';

import 'package:flutter/material.dart';
import 'dart:async';

import 'package:flutter/services.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:u_location_driver/u_location_driver.dart';
import 'package:u_location_driver_example/send_to_host.dart';

final toDartChannelBackground = const BasicMessageChannel<String>(
  'com.jimdo.uchida001tmhr.u_location_driver/backgroundIsolate',
  StringCodec(),
);

void listenMessagesToDartBackground() {
  toDartChannelBackground.setMessageHandler((message) async {
    if (message != null) {
      SendToHost sendToHost = SendToHost();
      sendToHost.send(message);
    }
    return ("success");
  });
}

@pragma('vm:entry-point')
void backgroundEntryPoint() {
  WidgetsFlutterBinding.ensureInitialized();
  listenMessagesToDartBackground();
}

@pragma('vm:entry-point') // JIT/AOTコンパイラにエントリポイントであることを知らせる
void main() { // The name must be main().
  debugPrint("main()");
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
  final BasicMessageChannel toDartChannelForeground = BasicMessageChannel(
    "com.jimdo.uchida001tmhr.u_location_driver/toDartForeground",
    StringCodec()
  );
  String messageFromNative = "Waiting for message form Native ...";
  TextEditingController textEditingControllerFrom = TextEditingController();
  TextEditingController textEditingControllerPassword = TextEditingController();
  TextEditingController textEditingControllerTo = TextEditingController();
  late SharedPreferences prefs;
  ReceivePort receivePort = ReceivePort();

  late final AppLifecycleListener appLifecycleListener;

  void listenMessagesToDartForeground() {
    toDartChannelForeground.setMessageHandler((message) async {
      if (message != null) {
        SendToHost sendToHost = SendToHost();
        sendToHost.send(message);
      }
      return ("success");
    });
  }

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
    listenMessagesToDartForeground();
    WidgetsBinding.instance.addPostFrameCallback((_) {
    });
  }

  @override
  // Widget破棄時
  void dispose() {
    // 監視の終了を登録
    appLifecycleListener.dispose();
    super.dispose();
  }

  Future<dynamic> handleMethodCallForeground(MethodCall call) async {
    debugPrint("handleMethodCallForeground -> call.method == ${call.method}");
    if (call.method == "informToForeground") {
      debugPrint("handleMethodCallForeground -> call.method is processing informToForeground");
      setState(() {
        messageFromNative = call.arguments;
        debugPrint("messageFromNative = $messageFromNative");
      });
      SendToHost sendToHost = SendToHost();
      sendToHost.send(call.arguments);
    }
    return Future.value("success");
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
                  final callbackHandle = PluginUtilities.getCallbackHandle(backgroundEntryPoint);
                  if (callbackHandle != null) {
                    HashMap<String, dynamic> arguments = HashMap();
                    arguments.addAll({"callbackHandle": callbackHandle.toRawHandle()});
                    debugPrint("activateForeground");
                    uLocationDriverPlugin.activateForeground(arguments: arguments);
                    prefs.setBool("activatedByUser", true);
                  }
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
