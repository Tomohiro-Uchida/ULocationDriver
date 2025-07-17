import 'dart:io';

import 'package:flutter/cupertino.dart';
import 'package:mailer/mailer.dart' as mailer;
import 'package:mailer/smtp_server.dart';
import 'package:shared_preferences/shared_preferences.dart';

class SendToHost {

  send(String messgae) async {
    SharedPreferences prefs = await SharedPreferences.getInstance();
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
        smtpServer = SmtpServer("smtp.mail.me.com", port: 587, ssl: false, username: username, password: password);
      } else {
        smtpServer = SmtpServer("smtp.gmail.com", port: 587, ssl: false, username: username, password: password);
      }
      final message = mailer.Message()
        ..from = mailer.Address(username, '')
        ..recipients.addAll([toAddress])
        ..subject = "Message from Native"
        ..text = "Message from Native: $messgae";
      try {
        await mailer.send(message, smtpServer);
      } catch(e) {
        debugPrint(e.toString());
      }
    }
  }

}
