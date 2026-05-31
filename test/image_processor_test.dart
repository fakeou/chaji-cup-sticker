import 'dart:typed_data';
import 'dart:ui' as ui;

import 'package:flutter_test/flutter_test.dart';
import 'package:sketch_reproducer/models/stroke.dart';
import 'package:sketch_reproducer/services/image_processor.dart';

Future<Uint8List> _filledCirclePng() async {
  final recorder = ui.PictureRecorder();
  final canvas = ui.Canvas(recorder);
  final paint = ui.Paint()..color = const ui.Color(0xffffffff);
  canvas.drawRect(const ui.Rect.fromLTWH(0, 0, 100, 100), paint);

  paint.color = const ui.Color(0xff000000);
  canvas.drawCircle(const ui.Offset(50, 50), 20, paint);

  final image = await recorder.endRecording().toImage(100, 100);
  final bytes = await image.toByteData(format: ui.ImageByteFormat.png);
  return bytes!.buffer.asUint8List();
}

Future<Uint8List> _twoCloseFilledCirclesPng() async {
  final recorder = ui.PictureRecorder();
  final canvas = ui.Canvas(recorder);
  final paint = ui.Paint()..color = const ui.Color(0xffffffff);
  canvas.drawRect(const ui.Rect.fromLTWH(0, 0, 100, 70), paint);

  paint.color = const ui.Color(0xff000000);
  canvas.drawCircle(const ui.Offset(35, 35), 12, paint);
  canvas.drawCircle(const ui.Offset(65, 35), 12, paint);

  final image = await recorder.endRecording().toImage(100, 70);
  final bytes = await image.toByteData(format: ui.ImageByteFormat.png);
  return bytes!.buffer.asUint8List();
}

ui.Rect _bounds(List<Stroke> strokes) {
  var left = double.infinity;
  var top = double.infinity;
  var right = double.negativeInfinity;
  var bottom = double.negativeInfinity;

  for (final stroke in strokes) {
    for (final point in stroke.points) {
      if (point.x < left) left = point.x;
      if (point.y < top) top = point.y;
      if (point.x > right) right = point.x;
      if (point.y > bottom) bottom = point.y;
    }
  }

  return ui.Rect.fromLTRB(left, top, right, bottom);
}

void main() {
  test(
    'filled black regions remain drawable instead of collapsing to a dot',
    () async {
      final sketch = await ImageProcessor.processImage(
        await _filledCirclePng(),
        targetSize: 100,
      );

      final bounds = _bounds(sketch.strokes);

      expect(sketch.strokes.length, greaterThan(4));
      expect(bounds.width, greaterThan(30));
      expect(bounds.height, greaterThan(30));
    },
  );

  test('filled region strokes are not mergeable', () async {
    final sketch = await ImageProcessor.processImage(
      await _twoCloseFilledCirclesPng(),
      targetSize: 100,
    );

    final filledStrokes = sketch.strokes.where((stroke) => !stroke.mergeable);

    expect(filledStrokes, isNotEmpty);
    expect(
      filledStrokes.every((stroke) => _bounds([stroke]).width < 25),
      isTrue,
    );
  });
}
