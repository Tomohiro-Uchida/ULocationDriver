import 'dart:io';

import 'package:flutter/cupertino.dart';
import 'package:path_provider/path_provider.dart';

class WriteToFile {

  void write(String message) async {

    Directory? documentDir;
    if (Platform.isAndroid) {
      // documentDir = await getExternalStorageDirectory();
      documentDir = Directory("/storage/emulated/0/Documents");
    } else if (Platform.isIOS) {
      documentDir = await getApplicationDocumentsDirectory();
    }
    if (documentDir != null) {
      String filePtah = "${documentDir.path}/uLocationDriverLog.txt";
      debugPrint("DateTime = ${DateTime.now()}, filePath = $filePtah");
      File(filePtah).writeAsString("$message\n", mode: FileMode.append);
    }
  }
}