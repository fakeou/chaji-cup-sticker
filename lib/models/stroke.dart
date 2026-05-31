/// 一条笔画，由一系列坐标点组成
class Stroke {
  final List<StrokePoint> points;
  final bool mergeable;

  Stroke(this.points, {this.mergeable = true});

  Map<String, dynamic> toJson() => {
    'points': points.map((p) => p.toJson()).toList(),
    'mergeable': mergeable,
  };

  factory Stroke.fromJson(Map<String, dynamic> json) => Stroke(
    (json['points'] as List)
        .map((p) => StrokePoint.fromJson(p as Map<String, dynamic>))
        .toList(),
    mergeable: json['mergeable'] as bool? ?? true,
  );
}

class StrokePoint {
  final double x;
  final double y;

  const StrokePoint(this.x, this.y);

  Map<String, dynamic> toJson() => {'x': x, 'y': y};

  factory StrokePoint.fromJson(Map<String, dynamic> json) =>
      StrokePoint((json['x'] as num).toDouble(), (json['y'] as num).toDouble());
}

/// 整个简笔画 = 多条笔画
class SketchData {
  final List<Stroke> strokes;
  final int canvasWidth;
  final int canvasHeight;

  const SketchData({
    required this.strokes,
    required this.canvasWidth,
    required this.canvasHeight,
  });

  Map<String, dynamic> toJson() => {
    'strokes': strokes.map((s) => s.toJson()).toList(),
    'canvasWidth': canvasWidth,
    'canvasHeight': canvasHeight,
  };

  factory SketchData.fromJson(Map<String, dynamic> json) => SketchData(
    strokes: (json['strokes'] as List)
        .map((s) => Stroke.fromJson(s as Map<String, dynamic>))
        .toList(),
    canvasWidth: json['canvasWidth'] as int,
    canvasHeight: json['canvasHeight'] as int,
  );
}
