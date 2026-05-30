import 'package:flutter/material.dart';

/// 可拖拽、可缩放的调整框
/// 用户可以拖拽移动，也可以拖拽四角和四边来调整大小
class AdjustableFrame extends StatefulWidget {
  final Rect initialRect;
  final ValueChanged<Rect> onRectChanged;
  final Widget? child;

  const AdjustableFrame({
    super.key,
    required this.initialRect,
    required this.onRectChanged,
    this.child,
  });

  @override
  State<AdjustableFrame> createState() => _AdjustableFrameState();
}

class _AdjustableFrameState extends State<AdjustableFrame> {
  late Rect _rect;
  static const double _handleSize = 24.0;
  static const double _minSize = 50.0;

  @override
  void initState() {
    super.initState();
    _rect = widget.initialRect;
  }

  @override
  void didUpdateWidget(covariant AdjustableFrame oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.initialRect != widget.initialRect) {
      _rect = widget.initialRect;
    }
  }

  void _updateRect(Rect newRect) {
    // 限制最小尺寸
    if (newRect.width < _minSize || newRect.height < _minSize) return;
    // 限制在屏幕内
    setState(() {
      _rect = newRect;
    });
    widget.onRectChanged(_rect);
  }

  @override
  Widget build(BuildContext context) {
    return Stack(
      children: [
        // 半透明遮罩（框外区域）
        Positioned.fill(
          child: CustomPaint(
            painter: _MaskPainter(_rect),
          ),
        ),

        // 框主体（可拖拽移动）
        Positioned(
          left: _rect.left,
          top: _rect.top,
          width: _rect.width,
          height: _rect.height,
          child: GestureDetector(
            onPanUpdate: (details) {
              _updateRect(_rect.shift(details.delta));
            },
            child: Container(
              decoration: BoxDecoration(
                border: Border.all(color: Colors.blue, width: 2),
              ),
              child: widget.child,
            ),
          ),
        ),

        // 四个角的拖拽手柄
        _buildHandle(_HandlePosition.topLeft),
        _buildHandle(_HandlePosition.topRight),
        _buildHandle(_HandlePosition.bottomLeft),
        _buildHandle(_HandlePosition.bottomRight),

        // 四条边的拖拽手柄
        _buildHandle(_HandlePosition.top),
        _buildHandle(_HandlePosition.bottom),
        _buildHandle(_HandlePosition.left),
        _buildHandle(_HandlePosition.right),

        // 顶部提示文字
        Positioned(
          left: _rect.left,
          top: _rect.top - 32,
          child: Container(
            padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
            decoration: BoxDecoration(
              color: Colors.black87,
              borderRadius: BorderRadius.circular(4),
            ),
            child: Text(
              '${_rect.width.round()} × ${_rect.height.round()}',
              style: const TextStyle(color: Colors.white, fontSize: 12),
            ),
          ),
        ),
      ],
    );
  }

  Widget _buildHandle(_HandlePosition position) {
    double left, top;

    switch (position) {
      case _HandlePosition.topLeft:
        left = _rect.left - _handleSize / 2;
        top = _rect.top - _handleSize / 2;
      case _HandlePosition.topRight:
        left = _rect.right - _handleSize / 2;
        top = _rect.top - _handleSize / 2;
      case _HandlePosition.bottomLeft:
        left = _rect.left - _handleSize / 2;
        top = _rect.bottom - _handleSize / 2;
      case _HandlePosition.bottomRight:
        left = _rect.right - _handleSize / 2;
        top = _rect.bottom - _handleSize / 2;
      case _HandlePosition.top:
        left = _rect.center.dx - _handleSize / 2;
        top = _rect.top - _handleSize / 2;
      case _HandlePosition.bottom:
        left = _rect.center.dx - _handleSize / 2;
        top = _rect.bottom - _handleSize / 2;
      case _HandlePosition.left:
        left = _rect.left - _handleSize / 2;
        top = _rect.center.dy - _handleSize / 2;
      case _HandlePosition.right:
        left = _rect.right - _handleSize / 2;
        top = _rect.center.dy - _handleSize / 2;
    }

    return Positioned(
      left: left,
      top: top,
      width: _handleSize,
      height: _handleSize,
      child: GestureDetector(
        onPanUpdate: (details) => _onHandleDrag(position, details.delta),
        child: Container(
          decoration: BoxDecoration(
            color: Colors.blue,
            shape: position.isCorner ? BoxShape.circle : BoxShape.rectangle,
            borderRadius:
                position.isCorner ? null : BorderRadius.circular(4),
          ),
          child: Center(
            child: Icon(
              position.isCorner ? Icons.open_with : Icons.drag_handle,
              color: Colors.white,
              size: 14,
            ),
          ),
        ),
      ),
    );
  }

  void _onHandleDrag(_HandlePosition position, Offset delta) {
    double left = _rect.left;
    double top = _rect.top;
    double right = _rect.right;
    double bottom = _rect.bottom;

    switch (position) {
      case _HandlePosition.topLeft:
        left += delta.dx;
        top += delta.dy;
      case _HandlePosition.topRight:
        right += delta.dx;
        top += delta.dy;
      case _HandlePosition.bottomLeft:
        left += delta.dx;
        bottom += delta.dy;
      case _HandlePosition.bottomRight:
        right += delta.dx;
        bottom += delta.dy;
      case _HandlePosition.top:
        top += delta.dy;
      case _HandlePosition.bottom:
        bottom += delta.dy;
      case _HandlePosition.left:
        left += delta.dx;
      case _HandlePosition.right:
        right += delta.dx;
    }

    // 确保 left < right, top < bottom
    if (left >= right - _minSize || top >= bottom - _minSize) return;

    _updateRect(Rect.fromLTRB(left, top, right, bottom));
  }
}

enum _HandlePosition {
  topLeft,
  topRight,
  bottomLeft,
  bottomRight,
  top,
  bottom,
  left,
  right;

  bool get isCorner =>
      this == topLeft ||
      this == topRight ||
      this == bottomLeft ||
      this == bottomRight;
}

/// 框外半透明遮罩
class _MaskPainter extends CustomPainter {
  final Rect frameRect;

  _MaskPainter(this.frameRect);

  @override
  void paint(Canvas canvas, Size size) {
    final paint = Paint()..color = Colors.black54;

    // 上方
    canvas.drawRect(
      Rect.fromLTRB(0, 0, size.width, frameRect.top),
      paint,
    );
    // 下方
    canvas.drawRect(
      Rect.fromLTRB(0, frameRect.bottom, size.width, size.height),
      paint,
    );
    // 左方
    canvas.drawRect(
      Rect.fromLTRB(0, frameRect.top, frameRect.left, frameRect.bottom),
      paint,
    );
    // 右方
    canvas.drawRect(
      Rect.fromLTRB(frameRect.right, frameRect.top, size.width, frameRect.bottom),
      paint,
    );
  }

  @override
  bool shouldRepaint(covariant _MaskPainter oldDelegate) =>
      oldDelegate.frameRect != frameRect;
}
