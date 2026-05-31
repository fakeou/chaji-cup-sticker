package com.sketchreproducer.sketch_reproducer

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent

class DrawingAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "DrawingA11y"

        private const val PRE_DRAW_DELAY_MS = 700L
        private const val POINT_DURATION_MS = 60L
        private const val PX_DURATION_MS = 6L
        private const val MIN_SEGMENT_DURATION_MS = 600L
        private const val MAX_SEGMENT_DURATION_MS = 12000L
        private const val INTERNAL_SEGMENT_GAP_MS = 80L
        private const val STROKE_GAP_MS = 450L
        private const val BATCH_STROKE_SIZE = 5
        private const val BATCH_PAUSE_MS = 1200L
        private const val MAX_SEGMENT_POINTS = 70
        private const val SEGMENT_OVERLAP_POINTS = 1
        private const val MAX_RETRY_COUNT = 2
        private const val CHAJI_SLIDER_MIN_X_RATIO = 191f / 1440f
        private const val CHAJI_SLIDER_Y_RATIO = 2828f / 3200f
        private const val BRUSH_TAP_DURATION_MS = 80L
        private const val BRUSH_SET_DELAY_MS = 350L

        var instance: DrawingAccessibilityService? = null
            private set

        private var pendingStrokes: List<DrawingStroke>? = null
        private var pendingFrame: FloatArray? = null
        private var canvasWidth: Int = 0
        private var canvasHeight: Int = 0
        private var isDrawing = false

        fun setDrawData(
            strokes: List<DrawingStroke>,
            frame: FloatArray,
            cw: Int,
            ch: Int,
        ) {
            pendingStrokes = strokes
            pendingFrame = frame
            canvasWidth = cw
            canvasHeight = ch
        }

        fun startDrawing(): Boolean {
            val svc = instance ?: return false
            val strokes = pendingStrokes ?: return false
            val frame = pendingFrame ?: return false
            if (isDrawing) return false
            isDrawing = true
            svc.executeDrawing(strokes, frame, canvasWidth, canvasHeight)
            return true
        }

        fun stopDrawing() {
            isDrawing = false
            instance?.handler?.removeCallbacksAndMessages(null)
        }
    }

    private val handler = Handler(Looper.getMainLooper())

    private data class DrawSegment(
        val points: List<FloatArray>,
        val strokeIndex: Int,
        val segmentIndex: Int,
        val segmentCount: Int,
    )

    private var continuingStroke: GestureDescription.StrokeDescription? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "无障碍服务已连接")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
    override fun onDestroy() { instance = null; super.onDestroy() }

    private fun executeDrawing(
        strokes: List<DrawingStroke>,
        frame: FloatArray,
        cw: Int,
        ch: Int,
    ) {
        val fl = frame[0]; val ft = frame[1]; val fr = frame[2]; val fb = frame[3]
        val fw = fr - fl; val fh = fb - ft

        Log.d(TAG, "=== 开始绘制 ===")
        Log.d(TAG, "区域: ($fl,$ft)-($fr,$fb), 画布: ${cw}x${ch}")
        Log.d(TAG, "原始笔画: ${strokes.size}, 使用最小画笔")

        // 1. 映射到屏幕坐标
        val screenStrokes = mutableListOf<DrawingStroke>()
        for (stroke in strokes) {
            if (stroke.points.size < 2) continue
            screenStrokes.add(
                DrawingStroke(
                    stroke.points.map { p ->
                        floatArrayOf(fl + (p[0] / cw) * fw, ft + (p[1] / ch) * fh)
                    },
                    stroke.mergeable
                )
            )
        }

        // 2. 合并端点接近的笔画（减少抬手次数）
        val merged = mergeNearbyStrokes(screenStrokes, maxGap = 30f)
        Log.d(TAG, "合并后笔画: ${merged.size}")

        val segments = buildDrawSegments(merged)
        Log.d(TAG, "拆分后手势段: ${segments.size}, " +
            "每点 ${POINT_DURATION_MS}ms, 笔画间隔 ${STROKE_GAP_MS}ms, " +
            "每 ${BATCH_STROKE_SIZE} 条笔画暂停 ${BATCH_PAUSE_MS}ms")

        // 3. 逐条绘制
        handler.postDelayed(
            { setChajiBrushToMinimum { drawNext(segments, 0) } },
            PRE_DRAW_DELAY_MS
        )
    }

    /**
     * 合并端点接近的笔画：
     * 如果一条笔画的终点和另一条笔画的起点距离 < maxGap，就连起来
     */
    private fun mergeNearbyStrokes(strokes: List<DrawingStroke>, maxGap: Float): List<DrawingStroke> {
        if (strokes.size <= 1) return strokes

        val used = BooleanArray(strokes.size)
        val result = mutableListOf<DrawingStroke>()

        for (i in strokes.indices) {
            if (used[i]) continue
            used[i] = true
            val stroke = strokes[i]
            if (!stroke.mergeable) {
                result.add(stroke)
                continue
            }
            val chain = mutableListOf<FloatArray>()
            chain.addAll(stroke.points)

            // 尝试往后接
            var searching = true
            while (searching) {
                searching = false
                val endPt = chain.last()
                var bestIdx = -1
                var bestDist = maxGap

                for (j in strokes.indices) {
                    val other = strokes[j]
                    if (used[j] || !other.mergeable) continue
                    val startPt = other.points.first()
                    val endPtOther = other.points.last()

                    // 正向距离
                    val d1 = dist(endPt, startPt)
                    // 反向距离
                    val d2 = dist(endPt, endPtOther)

                    if (d1 < bestDist) {
                        bestDist = d1; bestIdx = j
                    }
                    if (d2 < bestDist) {
                        bestDist = d2; bestIdx = -j - 1 // 负数表示反向
                    }
                }

                if (bestIdx >= 0) {
                    used[bestIdx] = true
                    chain.addAll(strokes[bestIdx].points)
                    searching = true
                } else if (bestIdx < -1) {
                    val realIdx = -bestIdx - 1
                    used[realIdx] = true
                    chain.addAll(strokes[realIdx].points.reversed())
                    searching = true
                }
            }

            if (chain.size >= 2) result.add(DrawingStroke(chain, mergeable = true))
        }

        return result
    }

    private fun dist(a: FloatArray, b: FloatArray): Float {
        val dx = a[0] - b[0]; val dy = a[1] - b[1]
        return Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
    }

    private fun buildDrawSegments(strokes: List<DrawingStroke>): List<DrawSegment> {
        val segments = mutableListOf<DrawSegment>()

        strokes.forEachIndexed { strokeIndex, stroke ->
            val points = stroke.points
            if (points.size < 2) return@forEachIndexed

            val ranges = mutableListOf<Pair<Int, Int>>()
            var start = 0
            while (start < points.size - 1) {
                val end = (start + MAX_SEGMENT_POINTS).coerceAtMost(points.size)
                ranges.add(start to end)
                if (end >= points.size) break
                start = (end - SEGMENT_OVERLAP_POINTS).coerceAtLeast(start + 1)
            }

            val segmentCount = ranges.size
            ranges.forEachIndexed { segmentIndex, (from, to) ->
                val segmentPoints = points.subList(from, to)
                if (segmentPoints.size >= 2) {
                    segments.add(DrawSegment(segmentPoints, strokeIndex, segmentIndex, segmentCount))
                }
            }
        }

        return segments
    }

    private fun setChajiBrushToMinimum(after: () -> Unit) {
        if (!isDrawing) return

        val (screenWidth, screenHeight) = screenSize()
        val x = screenWidth * CHAJI_SLIDER_MIN_X_RATIO
        val y = screenHeight * CHAJI_SLIDER_Y_RATIO

        val path = Path().apply {
            moveTo(x, y)
            lineTo(x + 0.1f, y)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, BRUSH_TAP_DURATION_MS))
            .build()

        Log.d(TAG, "设置霸王茶姬最小画笔: tap=(${x.toInt()},${y.toInt()})")

        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gesture: GestureDescription?) {
                handler.postDelayed({ after() }, BRUSH_SET_DELAY_MS)
            }

            override fun onCancelled(gesture: GestureDescription?) {
                Log.w(TAG, "设置画笔粗细手势被取消，继续绘制")
                handler.postDelayed({ after() }, BRUSH_SET_DELAY_MS)
            }
        }, null).also { accepted ->
            if (!accepted) {
                Log.w(TAG, "设置画笔粗细 dispatchGesture 返回 false，继续绘制")
                handler.postDelayed({ after() }, BRUSH_SET_DELAY_MS)
            }
        }
    }

    private fun screenSize(): Pair<Int, Int> {
        val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.currentWindowMetrics.bounds
            bounds.width() to bounds.height()
        } else {
            @Suppress("DEPRECATION")
            val display = windowManager.defaultDisplay
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            display.getRealMetrics(metrics)
            metrics.widthPixels to metrics.heightPixels
        }
    }

    /**
     * 逐段绘制。长笔画会被拆成短手势，避免小程序 canvas 漏收过长手势中的 move 事件。
     */
    private fun drawNext(segments: List<DrawSegment>, index: Int, retryCount: Int = 0) {
        if (!isDrawing || index >= segments.size) {
            isDrawing = false
            continuingStroke = null
            Log.d(TAG, "=== 绘制完成，共 ${segments.size} 段 ===")
            return
        }

        val segment = segments[index]
        val points = segment.points
        if (points.size < 2) {
            drawNext(segments, index + 1)
            return
        }

        // 构建完整路径
        val path = Path()
        path.moveTo(points[0][0], points[0][1])
        for (i in 1 until points.size) {
            path.lineTo(points[i][0], points[i][1])
        }

        val pathLength = pathLength(points)
        val pointDuration = points.size * POINT_DURATION_MS
        val distanceDuration = (pathLength * PX_DURATION_MS).toLong()
        val duration = maxOf(pointDuration, distanceDuration)
            .coerceIn(MIN_SEGMENT_DURATION_MS, MAX_SEGMENT_DURATION_MS)

        Log.d(TAG, "段 $index/${segments.size}: 笔画 ${segment.strokeIndex} " +
            "${segment.segmentIndex + 1}/${segment.segmentCount}, " +
            "${points.size}点, ${pathLength.toInt()}px, ${duration}ms, " +
            "(${points[0][0].toInt()},${points[0][1].toInt()}) → (${points.last()[0].toInt()},${points.last()[1].toInt()})")

        try {
            val willContinue = segment.segmentIndex < segment.segmentCount - 1
            val strokeDesc = if (segment.segmentIndex == 0) {
                GestureDescription.StrokeDescription(path, 0, duration, willContinue)
            } else {
                val previousStroke = continuingStroke
                if (previousStroke == null) {
                    Log.w(TAG, "段 $index 缺少连续笔画状态，降级为新手势")
                    GestureDescription.StrokeDescription(path, 0, duration, willContinue)
                } else {
                    previousStroke.continueStroke(path, 0, duration, willContinue)
                }
            }
            val gesture = GestureDescription.Builder().addStroke(strokeDesc).build()

            dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(gesture: GestureDescription?) {
                    continuingStroke = if (willContinue) strokeDesc else null
                    handler.postDelayed(
                        { drawNext(segments, index + 1) },
                        delayAfterSegment(index, segments)
                    )
                }
                override fun onCancelled(gesture: GestureDescription?) {
                    continuingStroke = null
                    Log.w(TAG, "段 $index 被取消, retry=$retryCount")
                    if (isDrawing && retryCount < MAX_RETRY_COUNT) {
                        handler.postDelayed(
                            { drawNext(segments, index, retryCount + 1) },
                            BATCH_PAUSE_MS
                        )
                    } else {
                        handler.postDelayed(
                            { drawNext(segments, index + 1) },
                            delayAfterSegment(index, segments)
                        )
                    }
                }
            }, null).also { accepted ->
                if (!accepted) {
                    continuingStroke = null
                    Log.w(TAG, "段 $index dispatchGesture 返回 false, retry=$retryCount")
                    if (retryCount < MAX_RETRY_COUNT) {
                        handler.postDelayed(
                            { drawNext(segments, index, retryCount + 1) },
                            BATCH_PAUSE_MS
                        )
                    } else {
                        handler.postDelayed(
                            { drawNext(segments, index + 1) },
                            delayAfterSegment(index, segments)
                        )
                    }
                }
            }
        } catch (e: Exception) {
            continuingStroke = null
            Log.e(TAG, "段 $index 异常: ${e.message}")
            handler.postDelayed({ drawNext(segments, index + 1) }, delayAfterSegment(index, segments))
        }
    }

    private fun delayAfterSegment(index: Int, segments: List<DrawSegment>): Long {
        val nextIndex = index + 1
        if (nextIndex >= segments.size) return STROKE_GAP_MS

        val current = segments[index]
        val next = segments[nextIndex]
        return when {
            current.strokeIndex == next.strokeIndex -> INTERNAL_SEGMENT_GAP_MS
            next.strokeIndex > 0 && next.strokeIndex % BATCH_STROKE_SIZE == 0 -> BATCH_PAUSE_MS
            else -> STROKE_GAP_MS
        }
    }

    private fun pathLength(points: List<FloatArray>): Float {
        var total = 0f
        for (i in 1 until points.size) {
            total += dist(points[i - 1], points[i])
        }
        return total
    }
}
