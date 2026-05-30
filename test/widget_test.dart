import 'package:flutter_test/flutter_test.dart';
import 'package:sketch_reproducer/main.dart';

void main() {
  testWidgets('App starts', (WidgetTester tester) async {
    await tester.pumpWidget(const SketchReproducerApp());
    expect(find.text('简笔画复刻'), findsOneWidget);
  });
}
