import 'package:flutter/material.dart';
import '../models/stroke.dart';
import '../widgets/adjustable_frame.dart';
import '../widgets/stroke_preview.dart';

/// 覆盖层页面：显示可调整的框 + 简笔画预览
/// 用户调整框的位置和大小来匹配目标画布
class OverlayScreen extends StatefulWidget {
  final SketchData sketch;

  const OverlayScreen({super.key, required this.sketch});

  @override
  State<OverlayScreen> createState() => _OverlayScreenState();
}

class _OverlayScreenState extends State<OverlayScreen> {
  late Rect _frameRect;
  bool _showPreview = true;

  @override
  void initState() {
    super.initState();
    // 初始框：屏幕中央，占 80% 宽度
    WidgetsBinding.instance.addPostFrameCallback((_) {
      final size = MediaQuery.of(context).size;
      final frameW = size.width * 0.8;
      final frameH = frameW; // 正方形
      setState(() {
        _frameRect = Rect.fromCenter(
          center: Offset(size.width / 2, size.height / 2),
          width: frameW,
          height: frameH,
        );
      });
    });
    _frameRect = const Rect.fromLTWH(50, 200, 300, 300); // 默认值
  }

  void _confirmDraw() {
    // 返回框的坐标给上一个页面
    Navigator.pop(context, {
      'left': _frameRect.left,
      'top': _frameRect.top,
      'right': _frameRect.right,
      'bottom': _frameRect.bottom,
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.transparent,
      body: Stack(
        children: [
          // 可调整的框
          AdjustableFrame(
            initialRect: _frameRect,
            onRectChanged: (rect) {
              setState(() => _frameRect = rect);
            },
            child: _showPreview
                ? ClipRect(
                    child: Opacity(
                      opacity: 0.5,
                      child: CustomPaint(
                        size: Size.infinite,
                        painter: StrokePreviewPainter(
                          sketch: widget.sketch,
                          strokeColor: Colors.blue,
                          strokeWidth: 1.5,
                        ),
                      ),
                    ),
                  )
                : null,
          ),

          // 底部操作栏
          Positioned(
            left: 0,
            right: 0,
            bottom: 0,
            child: Container(
              padding: const EdgeInsets.all(16),
              decoration: BoxDecoration(
                color: Colors.black.withValues(alpha: 0.8),
                borderRadius: const BorderRadius.vertical(
                  top: Radius.circular(16),
                ),
              ),
              child: SafeArea(
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    // 提示文字
                    const Text(
                      '拖拽调整框的位置和大小，使其匹配目标画布区域',
                      style: TextStyle(color: Colors.white70, fontSize: 13),
                      textAlign: TextAlign.center,
                    ),
                    const SizedBox(height: 12),

                    // 开关：是否显示预览
                    Row(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        const Text(
                          '显示笔画预览',
                          style: TextStyle(color: Colors.white, fontSize: 14),
                        ),
                        const SizedBox(width: 8),
                        Switch(
                          value: _showPreview,
                          onChanged: (v) => setState(() => _showPreview = v),
                          activeThumbColor: Colors.blue,
                        ),
                      ],
                    ),
                    const SizedBox(height: 12),

                    // 按钮行
                    Row(
                      children: [
                        // 取消
                        Expanded(
                          child: OutlinedButton(
                            onPressed: () => Navigator.pop(context),
                            style: OutlinedButton.styleFrom(
                              foregroundColor: Colors.white,
                              side: const BorderSide(color: Colors.white54),
                              padding: const EdgeInsets.symmetric(vertical: 14),
                            ),
                            child: const Text('取消'),
                          ),
                        ),
                        const SizedBox(width: 12),
                        // 开始绘制
                        Expanded(
                          flex: 2,
                          child: ElevatedButton(
                            onPressed: _confirmDraw,
                            style: ElevatedButton.styleFrom(
                              backgroundColor: Colors.green,
                              foregroundColor: Colors.white,
                              padding: const EdgeInsets.symmetric(vertical: 14),
                            ),
                            child: const Text(
                              '开始绘制',
                              style: TextStyle(
                                fontSize: 16,
                                fontWeight: FontWeight.bold,
                              ),
                            ),
                          ),
                        ),
                      ],
                    ),
                  ],
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }
}
