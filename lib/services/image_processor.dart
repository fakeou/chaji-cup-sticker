import 'dart:math';
import 'dart:typed_data';
import 'dart:ui' as ui;
import '../models/stroke.dart';

class ImageProcessor {
  /// 将图片转为简笔画数据
  static Future<SketchData> processImage(
    Uint8List imageBytes, {
    int targetSize = 800,
    int threshold = 128,
  }) async {
    final codec = await ui.instantiateImageCodec(imageBytes);
    final frame = await codec.getNextFrame();
    final image = frame.image;

    final w = image.width;
    final h = image.height;
    final scale = targetSize / max(w, h);
    final newW = (w * scale).round();
    final newH = (h * scale).round();

    final byteData = await image.toByteData(format: ui.ImageByteFormat.rawRgba);
    if (byteData == null) throw Exception('无法读取图片数据');

    final binary = _toBinary(byteData, w, h, newW, newH, threshold);
    final skeleton = _skeletonize(binary, newW, newH);
    final strokes = _tracePaths(skeleton, newW, newH);

    return SketchData(
      strokes: strokes,
      canvasWidth: newW,
      canvasHeight: newH,
    );
  }

  static List<int> _toBinary(
    ByteData rgba, int srcW, int srcH, int dstW, int dstH, int threshold,
  ) {
    final binary = List<int>.filled(dstW * dstH, 0);
    for (int y = 0; y < dstH; y++) {
      for (int x = 0; x < dstW; x++) {
        final srcX = (x * srcW / dstW).floor().clamp(0, srcW - 1);
        final srcY = (y * srcH / dstH).floor().clamp(0, srcH - 1);
        final offset = (srcY * srcW + srcX) * 4;
        final r = rgba.getUint8(offset);
        final g = rgba.getUint8(offset + 1);
        final b = rgba.getUint8(offset + 2);
        final gray = (r * 0.299 + g * 0.587 + b * 0.114).toInt();
        binary[y * dstW + x] = gray < threshold ? 1 : 0;
      }
    }
    return binary;
  }

  static List<int> _skeletonize(List<int> binary, int w, int h) {
    var img = List<int>.from(binary);
    bool changed = true;
    while (changed) {
      changed = false;
      final toRemove = <int>[];
      for (int y = 1; y < h - 1; y++) {
        for (int x = 1; x < w - 1; x++) {
          final idx = y * w + x;
          if (img[idx] == 0) continue;
          final p = [
            img[(y - 1) * w + x], img[(y - 1) * w + x + 1],
            img[y * w + x + 1], img[(y + 1) * w + x + 1],
            img[(y + 1) * w + x], img[(y + 1) * w + x - 1],
            img[y * w + x - 1], img[(y - 1) * w + x - 1],
          ];
          final neighbors = p.where((n) => n == 1).length;
          if (neighbors < 2 || neighbors > 6) continue;
          if (_countTransitions(p) != 1) continue;
          if (p[0] * p[2] * p[4] != 0) continue;
          if (p[2] * p[4] * p[6] != 0) continue;
          toRemove.add(idx);
        }
      }
      for (final idx in toRemove) { img[idx] = 0; }
      if (toRemove.isNotEmpty) changed = true;

      toRemove.clear();
      for (int y = 1; y < h - 1; y++) {
        for (int x = 1; x < w - 1; x++) {
          final idx = y * w + x;
          if (img[idx] == 0) continue;
          final p = [
            img[(y - 1) * w + x], img[(y - 1) * w + x + 1],
            img[y * w + x + 1], img[(y + 1) * w + x + 1],
            img[(y + 1) * w + x], img[(y + 1) * w + x - 1],
            img[y * w + x - 1], img[(y - 1) * w + x - 1],
          ];
          final neighbors = p.where((n) => n == 1).length;
          if (neighbors < 2 || neighbors > 6) continue;
          if (_countTransitions(p) != 1) continue;
          if (p[0] * p[2] * p[6] != 0) continue;
          if (p[0] * p[4] * p[6] != 0) continue;
          toRemove.add(idx);
        }
      }
      for (final idx in toRemove) { img[idx] = 0; }
      if (toRemove.isNotEmpty) changed = true;
    }
    return img;
  }

  static int _countTransitions(List<int> p) {
    int count = 0;
    for (int i = 0; i < 8; i++) {
      if (p[i] == 0 && p[(i + 1) % 8] == 1) count++;
    }
    return count;
  }

  static List<Stroke> _tracePaths(List<int> skeleton, int w, int h) {
    final visited = <int>{};
    final strokes = <Stroke>[];
    for (int y = 0; y < h; y++) {
      for (int x = 0; x < w; x++) {
        final idx = y * w + x;
        if (skeleton[idx] == 0 || visited.contains(idx)) continue;
        final points = <StrokePoint>[];
        var cx = x, cy = y;
        while (true) {
          final cidx = cy * w + cx;
          if (visited.contains(cidx)) break;
          visited.add(cidx);
          points.add(StrokePoint(cx.toDouble(), cy.toDouble()));
          var found = false;
          for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
              if (dx == 0 && dy == 0) continue;
              final nx = cx + dx, ny = cy + dy;
              if (nx < 0 || nx >= w || ny < 0 || ny >= h) continue;
              final nidx = ny * w + nx;
              if (skeleton[nidx] == 1 && !visited.contains(nidx)) {
                cx = nx; cy = ny; found = true; break;
              }
            }
            if (found) break;
          }
          if (!found) break;
        }
        if (points.length >= 3) {
          strokes.add(Stroke(_douglasPeucker(points, epsilon: 1.5)));
        }
      }
    }
    return strokes;
  }

  static List<StrokePoint> _douglasPeucker(List<StrokePoint> points, {double epsilon = 1.0}) {
    if (points.length <= 2) return points;
    double maxDist = 0;
    int maxIdx = 0;
    final first = points.first, last = points.last;
    for (int i = 1; i < points.length - 1; i++) {
      final dist = _pointToLineDistance(points[i], first, last);
      if (dist > maxDist) { maxDist = dist; maxIdx = i; }
    }
    if (maxDist > epsilon) {
      final left = _douglasPeucker(points.sublist(0, maxIdx + 1), epsilon: epsilon);
      final right = _douglasPeucker(points.sublist(maxIdx), epsilon: epsilon);
      return [...left.sublist(0, left.length - 1), ...right];
    }
    return [first, last];
  }

  static double _pointToLineDistance(StrokePoint p, StrokePoint a, StrokePoint b) {
    final dx = b.x - a.x, dy = b.y - a.y;
    final lenSq = dx * dx + dy * dy;
    if (lenSq == 0) return sqrt(pow(p.x - a.x, 2) + pow(p.y - a.y, 2));
    final t = ((p.x - a.x) * dx + (p.y - a.y) * dy) / lenSq;
    final ct = t.clamp(0.0, 1.0);
    return sqrt(pow(p.x - (a.x + ct * dx), 2) + pow(p.y - (a.y + ct * dy), 2));
  }
}
