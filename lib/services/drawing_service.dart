import 'package:flutter/services.dart';
import '../models/stroke.dart';

/// Flutter ↔ Android 平台通道通信
class DrawingService {
  static const _channel = MethodChannel('com.sketchreproducer/drawing');
  static const _eventChannel = EventChannel(
    'com.sketchreproducer/drawing_events',
  );

  /// 检查无障碍服务是否已开启
  static Future<bool> isAccessibilityEnabled() async {
    final result = await _channel.invokeMethod<bool>('isAccessibilityEnabled');
    return result ?? false;
  }

  /// 打开无障碍设置页面
  static Future<void> openAccessibilitySettings() async {
    await _channel.invokeMethod('openAccessibilitySettings');
  }

  /// 检查悬浮窗权限
  static Future<bool> canDrawOverlay() async {
    final result = await _channel.invokeMethod<bool>('canDrawOverlay');
    return result ?? false;
  }

  /// 请求悬浮窗权限
  static Future<void> requestOverlayPermission() async {
    await _channel.invokeMethod('requestOverlayPermission');
  }

  /// 显示悬浮按钮
  static Future<void> showFloatingButton() async {
    await _channel.invokeMethod('showFloatingButton');
  }

  /// 隐藏悬浮按钮
  static Future<void> hideFloatingButton() async {
    await _channel.invokeMethod('hideFloatingButton');
  }

  /// 将 App 退到后台
  static Future<void> goToBackground() async {
    await _channel.invokeMethod('goToBackground');
  }

  /// 发送绘制数据到原生层
  static Future<void> sendSketchData(SketchData data) async {
    await _channel.invokeMethod('sendSketchData', data.toJson());
  }

  /// 在指定区域开始绘制
  /// [left, top, right, bottom] 是屏幕上调整框的位置（像素）
  static Future<void> startDrawing({
    required double left,
    required double top,
    required double right,
    required double bottom,
  }) async {
    await _channel.invokeMethod('startDrawing', {
      'left': left,
      'top': top,
      'right': right,
      'bottom': bottom,
    });
  }

  /// 停止绘制
  static Future<void> stopDrawing() async {
    await _channel.invokeMethod('stopDrawing');
  }

  /// 监听来自原生层的事件
  static Stream<dynamic> get eventStream =>
      _eventChannel.receiveBroadcastStream();
}
