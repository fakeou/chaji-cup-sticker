import 'package:flutter/material.dart';
import '../models/stroke.dart';

/// 在 Canvas 上绘制简笔画预览
class StrokePreviewPainter extends CustomPainter {
  final SketchData sketch;
  final Color strokeColor;
  final double strokeWidth;

  StrokePreviewPainter({
    required this.sketch,
    this.strokeColor = Colors.black,
    this.strokeWidth = 2.0,
  });

  @override
  void paint(Canvas canvas, Size size) {
    // 计算缩放比例，保持宽高比
    final scaleX = size.width / sketch.canvasWidth;
    final scaleY = size.height / sketch.canvasHeight;
    final scale = scaleX < scaleY ? scaleX : scaleY;

    // 居中偏移
    final offsetX = (size.width - sketch.canvasWidth * scale) / 2;
    final offsetY = (size.height - sketch.canvasHeight * scale) / 2;

    canvas.save();
    canvas.translate(offsetX, offsetY);
    canvas.scale(scale);

    final paint = Paint()
      ..color = strokeColor
      ..style = PaintingStyle.stroke
      ..strokeWidth = strokeWidth / scale
      ..strokeCap = StrokeCap.round
      ..strokeJoin = StrokeJoin.round
      ..isAntiAlias = true;

    for (final stroke in sketch.strokes) {
      if (stroke.points.length < 2) continue;

      final path = Path();
      path.moveTo(stroke.points.first.x, stroke.points.first.y);

      for (int i = 1; i < stroke.points.length; i++) {
        path.lineTo(stroke.points[i].x, stroke.points[i].y);
      }

      canvas.drawPath(path, paint);
    }

    canvas.restore();
  }

  @override
  bool shouldRepaint(covariant StrokePreviewPainter oldDelegate) =>
      oldDelegate.sketch != sketch ||
      oldDelegate.strokeColor != strokeColor ||
      oldDelegate.strokeWidth != strokeWidth;
}
