import 'dart:io';

import 'package:flutter/material.dart';
import 'dart:async';

import 'package:flutter/services.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:u_location_driver/u_location_driver.dart';
import 'package:mailer/smtp_server.dart';
import 'package:mailer/mailer.dart' as mailer;

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
  final MethodChannel _platform = MethodChannel("com.jimdo.uchida001tmhr.u_location_driver/toDart");
  String _messageFromIOS = "Waiting for message form Native ...";
  TextEditingController textEditingControllerFrom = TextEditingController();
  TextEditingController textEditingControllerPassword = TextEditingController();
  TextEditingController textEditingControllerTo = TextEditingController();
  late SharedPreferences prefs;

  late final AppLifecycleListener appLifecycleListener;

  @override
  void initState() {
    super.initState();
    _platform.setMethodCallHandler(_handleMethodCall);
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
  Future<void> _handleMethodCall(MethodCall call) async {
    switch (call.method) {
      case 'informLocationToDart':
        setState(() {
          _messageFromIOS = 'Message from Native:\n${call.arguments}';
        });


        String? username = prefs.getString("fromAddress");
        String? password = prefs.getString("password");
        String? toAddress = prefs.getString("toAddress");
        if (username != null &&
            username.isNotEmpty &&
            password != null &&
            password.isNotEmpty &&
            toAddress != null &&
            toAddress.isNotEmpty) {
          SmtpServer smtpServer;
          if (Platform.isIOS) {
            smtpServer = SmtpServer(
              "smtp.mail.me.com",
              port: 587,
              ssl: false,
              username: username,
              password: password,
            );
          } else {
            smtpServer = SmtpServer(
              "smtp.gmail.com",
              port: 587,
              ssl: false,
              username: username,
              password: password,
            );
          }
          final message = mailer.Message()
            ..from = mailer.Address(username, '')
            ..recipients.addAll([toAddress])
            ..subject = "Message from Native"
            ..text = "Message from Native: ${call.arguments}";
          mailer.send(message, smtpServer);
        }

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
                child: Text("Activate Foreground"),
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
