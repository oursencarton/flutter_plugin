import 'dart:async';

import 'package:flutter/services.dart';

class InfoSys {
  static const MethodChannel _channel =
      const MethodChannel('info_sys');


  String ram = "";
  String rom = "";
  String cache = "";
  String cpu = "";
  String romTotal = "";
  String maxRam = "";
  static const channel = const MethodChannel('plugins.flutter.io/cpuRam_Info');

  InfoSys(){
    getInfoPertinente();
  }

  Future<void> getInfoPertinente() async {
    try {
      final Map<dynamic, dynamic> map = await _channel.invokeMethod('infoRam');
      final ramVal = map['Native'] / 1024;
      final maxRamVal = map['MaxRam'] / 1073741824;
      final romVal = map['Rom'];
      final cacheVal = map['Cache'];
      final romTotalVal = map['RomLibre'] / 1024;
      this.ram = ramVal.toStringAsFixed(2);
      this.maxRam = maxRamVal.toStringAsFixed(2);
      this.rom = '$romVal';
      this.cpu = map["CPU"];
      this.cache = '$cacheVal';
      this.romTotal = romTotalVal.toStringAsFixed(2);
    } on PlatformException catch (e) {
      ram = "Erreur '${e.message}'.";
    }
  }
}
