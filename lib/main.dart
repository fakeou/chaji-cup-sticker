import 'package:flutter/material.dart';
import 'screens/home_screen.dart';

void main() {
  runApp(const SketchReproducerApp());
}

class SketchReproducerApp extends StatelessWidget {
  const SketchReproducerApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: '简笔画复刻',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        colorSchemeSeed: Colors.blue,
        useMaterial3: true,
      ),
      home: const HomeScreen(),
    );
  }
}
