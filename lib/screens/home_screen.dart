import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:image_picker/image_picker.dart';
import '../models/stroke.dart';
import '../services/image_processor.dart';
import '../services/drawing_service.dart';
import '../widgets/stroke_preview.dart';

class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key});

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  SketchData? _sketch;
  bool _isProcessing = false;
  String _statusText = '请上传一张简笔画图片';
  bool _accessibilityEnabled = false;
  bool _overlayPermission = false;
  bool _floatingActive = false;
  double _brushWidth = 1.5;

  @override
  void initState() {
    super.initState();
    _checkPermissions();
  }

  Future<void> _checkPermissions() async {
    final accessibility = await DrawingService.isAccessibilityEnabled();
    final overlay = await DrawingService.canDrawOverlay();
    setState(() {
      _accessibilityEnabled = accessibility;
      _overlayPermission = overlay;
    });
  }

  Future<void> _pickImage() async {
    final picker = ImagePicker();
    final picked = await picker.pickImage(source: ImageSource.gallery);
    if (picked == null) return;

    setState(() {
      _isProcessing = true;
      _statusText = '正在识别...';
    });

    try {
      final bytes = await picked.readAsBytes();
      await _processImage(bytes);
    } catch (e) {
      setState(() {
        _isProcessing = false;
        _statusText = '识别失败: $e';
      });
    }
  }

  Future<void> _processImage(Uint8List bytes) async {
    try {
      final sketch = await ImageProcessor.processImage(bytes, brushWidth: 1.0);

      if (!mounted) return;
      setState(() {
        _sketch = sketch;
        _isProcessing = false;
        _statusText = '识别完成！共 ${sketch.strokes.length} 条笔画';
      });
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _isProcessing = false;
        _statusText = '识别失败: $e';
      });
    }
  }

  Future<void> _activateFloating() async {
    if (!_accessibilityEnabled) {
      _showPermissionDialog(
        '需要无障碍权限',
        '请在设置中开启本应用的无障碍服务，用于模拟触摸绘制。',
        () async => await DrawingService.openAccessibilitySettings(),
      );
      return;
    }

    if (!_overlayPermission) {
      _showPermissionDialog(
        '需要悬浮窗权限',
        '请允许本应用显示在其他应用上层。',
        () async => await DrawingService.requestOverlayPermission(),
      );
      return;
    }

    if (_sketch == null) {
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(const SnackBar(content: Text('请先上传并识别图片')));
      return;
    }

    // 发送数据到原生层
    await DrawingService.sendSketchData(_sketch!, brushWidth: _brushWidth);
    // 显示悬浮按钮
    await DrawingService.showFloatingButton();

    setState(() {
      _floatingActive = true;
    });

    // 延迟一下再退到后台，确保 Flutter 引擎状态稳定
    await Future.delayed(const Duration(milliseconds: 500));
    try {
      await DrawingService.goToBackground();
    } catch (_) {}
  }

  Future<void> _deactivateFloating() async {
    await DrawingService.hideFloatingButton();
    setState(() {
      _floatingActive = false;
      _statusText = '悬浮按钮已关闭';
    });
  }

  void _showPermissionDialog(
    String title,
    String content,
    VoidCallback onConfirm,
  ) {
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        title: Text(title),
        content: Text(content),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx),
            child: const Text('取消'),
          ),
          TextButton(
            onPressed: () {
              Navigator.pop(ctx);
              onConfirm();
            },
            child: const Text('去设置'),
          ),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('简笔画复刻'),
        backgroundColor: Theme.of(context).colorScheme.inversePrimary,
        actions: [
          IconButton(
            icon: const Icon(Icons.refresh),
            onPressed: _checkPermissions,
            tooltip: '刷新权限状态',
          ),
        ],
      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            // 权限状态
            _buildPermissionCard(),
            const SizedBox(height: 16),

            // 上传按钮
            ElevatedButton.icon(
              onPressed: _isProcessing ? null : _pickImage,
              icon: const Icon(Icons.upload_file),
              label: Text(_isProcessing ? '处理中...' : '上传简笔画图片'),
              style: ElevatedButton.styleFrom(
                padding: const EdgeInsets.symmetric(vertical: 16),
              ),
            ),
            const SizedBox(height: 16),

            // 状态
            Text(
              _statusText,
              textAlign: TextAlign.center,
              style: const TextStyle(fontSize: 14, color: Colors.grey),
            ),
            const SizedBox(height: 16),

            // 预览
            if (_sketch != null) ...[
              const Text(
                '识别预览',
                style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold),
              ),
              const SizedBox(height: 8),
              Container(
                height: 300,
                decoration: BoxDecoration(
                  border: Border.all(color: Colors.grey.shade300),
                  borderRadius: BorderRadius.circular(8),
                ),
                child: ClipRRect(
                  borderRadius: BorderRadius.circular(8),
                  child: CustomPaint(
                    size: Size.infinite,
                    painter: StrokePreviewPainter(
                      sketch: _sketch!,
                      strokeWidth: _brushWidth,
                    ),
                  ),
                ),
              ),
              const SizedBox(height: 16),
              _buildBrushWidthControl(),
              const SizedBox(height: 8),
              Text(
                '画布: ${_sketch!.canvasWidth} × ${_sketch!.canvasHeight}  |  笔画: ${_sketch!.strokes.length}',
                textAlign: TextAlign.center,
                style: const TextStyle(fontSize: 12, color: Colors.grey),
              ),
              const SizedBox(height: 24),

              // 激活/停用悬浮按钮
              if (!_floatingActive)
                ElevatedButton.icon(
                  onPressed: _activateFloating,
                  icon: const Icon(Icons.open_in_new),
                  label: const Text('激活悬浮按钮，切换到小程序绘制'),
                  style: ElevatedButton.styleFrom(
                    padding: const EdgeInsets.symmetric(vertical: 16),
                    backgroundColor: Colors.green,
                    foregroundColor: Colors.white,
                  ),
                )
              else
                Column(
                  children: [
                    Container(
                      padding: const EdgeInsets.all(12),
                      decoration: BoxDecoration(
                        color: Colors.green.shade50,
                        borderRadius: BorderRadius.circular(8),
                        border: Border.all(color: Colors.green.shade200),
                      ),
                      child: const Row(
                        children: [
                          Icon(Icons.check_circle, color: Colors.green),
                          SizedBox(width: 8),
                          Expanded(
                            child: Text(
                              '悬浮按钮已激活！\n请切换到小程序画布 → 点击屏幕右侧蓝色按钮 → 调整框 → 开始绘制',
                              style: TextStyle(fontSize: 13),
                            ),
                          ),
                        ],
                      ),
                    ),
                    const SizedBox(height: 12),
                    OutlinedButton.icon(
                      onPressed: _deactivateFloating,
                      icon: const Icon(Icons.close),
                      label: const Text('关闭悬浮按钮'),
                    ),
                  ],
                ),
            ],
          ],
        ),
      ),
    );
  }

  Widget _buildPermissionCard() {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text('权限状态', style: TextStyle(fontWeight: FontWeight.bold)),
            const SizedBox(height: 8),
            _permRow(
              '无障碍服务',
              _accessibilityEnabled,
              () async => await DrawingService.openAccessibilitySettings(),
            ),
            _permRow(
              '悬浮窗权限',
              _overlayPermission,
              () async => await DrawingService.requestOverlayPermission(),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildBrushWidthControl() {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                const Icon(Icons.brush, size: 20),
                const SizedBox(width: 8),
                const Text(
                  '画笔粗细',
                  style: TextStyle(fontWeight: FontWeight.bold),
                ),
                const Spacer(),
                Text(_brushWidth.toStringAsFixed(1)),
              ],
            ),
            Slider(
              value: _brushWidth,
              min: 1.0,
              max: 2.0,
              divisions: 10,
              label: _brushWidth.toStringAsFixed(1),
              onChanged: (value) {
                setState(() {
                  _brushWidth = double.parse(value.toStringAsFixed(1));
                });
              },
            ),
          ],
        ),
      ),
    );
  }

  Widget _permRow(String name, bool enabled, VoidCallback onTap) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 4),
      child: Row(
        children: [
          Icon(
            enabled ? Icons.check_circle : Icons.cancel,
            color: enabled ? Colors.green : Colors.red,
            size: 20,
          ),
          const SizedBox(width: 8),
          Text(name),
          const Spacer(),
          if (!enabled) TextButton(onPressed: onTap, child: const Text('去开启')),
        ],
      ),
    );
  }
}
