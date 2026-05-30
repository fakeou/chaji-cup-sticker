package com.sketchreproducer.sketch_reproducer

import android.content.Context
import android.graphics.*
import android.os.CountDownTimer
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View

/**
 * 全屏覆盖层：
 * - 半透明遮罩 + 可拖拽缩放框
 * - 框内笔画预览
 * - 底部「开始绘制」按钮（点击后 3 秒倒计时然后绘制）
 */
class OverlayView(context: Context) : View(context) {

    // ===== 回调 =====
    var onDraw: ((RectF) -> Unit)? = null
    var onCancel: (() -> Unit)? = null

    // ===== 框参数 =====
    private var frameRect = RectF(100f, 300f, 600f, 800f)
    private val handleRadius = 24f
    private val minFrameSize = 100f

    // ===== 笔画数据 =====
    private var strokes: List<List<FloatArray>> = emptyList()
    private var canvasWidth: Int = 1
    private var canvasHeight: Int = 1

    // ===== 倒计时 =====
    private var countdown = -1  // -1 = 未开始, 0 = 绘制中, >0 = 倒计时
    private var countdownTimer: CountDownTimer? = null

    // ===== 画笔 =====
    private val maskPaint = Paint().apply {
        color = Color.argb(150, 0, 0, 0)
        style = Paint.Style.FILL
    }
    private val framePaint = Paint().apply {
        color = Color.parseColor("#2196F3")
        style = Paint.Style.STROKE
        strokeWidth = 4f
        isAntiAlias = true
    }
    private val handlePaint = Paint().apply {
        color = Color.parseColor("#2196F3")
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    private val strokePaint = Paint().apply {
        color = Color.argb(128, 0, 150, 255)
        style = Paint.Style.STROKE
        strokeWidth = 3f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        isAntiAlias = true
    }
    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 36f
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
    }
    private val countdownPaint = Paint().apply {
        color = Color.WHITE
        textSize = 120f
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
        setShadowLayer(10f, 0f, 0f, Color.BLACK)
    }
    private val buttonPaint = Paint().apply {
        isAntiAlias = true
        textSize = 42f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }

    // 按钮区域
    private val btnHeight = dpToPx(52f)
    private val btnMargin = dpToPx(16f)
    private lateinit var cancelBtnRect: RectF
    private lateinit var startBtnRect: RectF

    // 触摸
    private enum class DragMode { NONE, MOVE, TL, TR, BL, BR, TOP, BOTTOM, LEFT, RIGHT }
    private var dragMode = DragMode.NONE
    private var lastX = 0f
    private var lastY = 0f

    fun setStrokes(strokeList: List<List<FloatArray>>, cw: Int, ch: Int) {
        strokes = strokeList
        canvasWidth = cw
        canvasHeight = ch
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val frameW = w * 0.8f
        val frameH = frameW * canvasHeight / canvasWidth
        val topMargin = dpToPx(80f)
        frameRect = RectF(
            (w - frameW) / 2, topMargin,
            (w + frameW) / 2, topMargin + frameH
        )
        val btnY = h - btnHeight - btnMargin
        val btnW = (w - btnMargin * 3) / 2
        cancelBtnRect = RectF(btnMargin, btnY, btnMargin + btnW, btnY + btnHeight)
        startBtnRect = RectF(btnMargin * 2 + btnW, btnY, btnMargin * 2 + btnW * 2, btnY + btnHeight)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()

        // 遮罩
        canvas.drawRect(0f, 0f, w, frameRect.top, maskPaint)
        canvas.drawRect(0f, frameRect.bottom, w, h, maskPaint)
        canvas.drawRect(0f, frameRect.top, frameRect.left, frameRect.bottom, maskPaint)
        canvas.drawRect(frameRect.right, frameRect.top, w, frameRect.bottom, maskPaint)

        // 框边框
        canvas.drawRect(frameRect, framePaint)

        // 四角手柄
        for ((cx, cy) in listOf(
            frameRect.left to frameRect.top, frameRect.right to frameRect.top,
            frameRect.left to frameRect.bottom, frameRect.right to frameRect.bottom
        )) {
            canvas.drawCircle(cx, cy, handleRadius, handlePaint)
        }

        // 笔画预览
        if (strokes.isNotEmpty()) {
            canvas.save()
            canvas.clipRect(frameRect)
            canvas.translate(frameRect.left, frameRect.top)
            val sx = frameRect.width() / canvasWidth
            val sy = frameRect.height() / canvasHeight
            val s = minOf(sx, sy)
            val ox = (frameRect.width() - canvasWidth * s) / 2
            val oy = (frameRect.height() - canvasHeight * s) / 2
            canvas.translate(ox, oy)
            canvas.scale(s, s)
            for (stroke in strokes) {
                if (stroke.size < 2) continue
                val path = Path().apply {
                    moveTo(stroke[0][0], stroke[0][1])
                    for (i in 1 until stroke.size) lineTo(stroke[i][0], stroke[i][1])
                }
                canvas.drawPath(path, strokePaint)
            }
            canvas.restore()
        }

        // 框尺寸
        textPaint.textSize = 30f
        textPaint.color = Color.argb(200, 255, 255, 255)
        canvas.drawText("${frameRect.width().toInt()} × ${frameRect.height().toInt()}",
            frameRect.centerX(), frameRect.top - 16f, textPaint)

        // 提示
        textPaint.textSize = 28f
        textPaint.color = Color.argb(180, 255, 255, 255)
        canvas.drawText("拖拽调整框匹配目标画布，然后点击「开始绘制」",
            w / 2, cancelBtnRect.top - 20f, textPaint)

        // 取消按钮
        canvas.drawRoundRect(cancelBtnRect, 12f, 12f,
            Paint().apply { color = Color.argb(200, 80, 80, 80); isAntiAlias = true })
        buttonPaint.color = Color.WHITE
        canvas.drawText("取消", cancelBtnRect.centerX(),
            cancelBtnRect.centerY() + buttonPaint.textSize / 3, buttonPaint)

        // 开始按钮
        val startColor = if (countdown > 0) Color.parseColor("#FF9800") else Color.parseColor("#4CAF50")
        canvas.drawRoundRect(startBtnRect, 12f, 12f,
            Paint().apply { color = startColor; isAntiAlias = true })
        buttonPaint.color = Color.WHITE
        val btnText = when {
            countdown > 0 -> "${countdown} 秒后开始..."
            else -> "开始绘制"
        }
        canvas.drawText(btnText, startBtnRect.centerX(),
            startBtnRect.centerY() + buttonPaint.textSize / 3, buttonPaint)

        // 全屏倒计时大字
        if (countdown > 0) {
            canvas.drawText("$countdown", w / 2, h / 2, countdownPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (countdown > 0) return true // 倒计时中不响应

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.x; lastY = event.y
                dragMode = hitTest(event.x, event.y)
                return dragMode != DragMode.NONE
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - lastX; val dy = event.y - lastY
                lastX = event.x; lastY = event.y
                applyDrag(dx, dy)
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (dragMode == DragMode.NONE) {
                    if (cancelBtnRect.contains(event.x, event.y)) {
                        onCancel?.invoke()
                    } else if (startBtnRect.contains(event.x, event.y)) {
                        startCountdown()
                    }
                }
                dragMode = DragMode.NONE
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun startCountdown() {
        countdown = 3
        invalidate()
        countdownTimer?.cancel()
        countdownTimer = object : CountDownTimer(3000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                countdown = (millisUntilFinished / 1000).toInt() + 1
                invalidate()
            }
            override fun onFinish() {
                countdown = 0
                invalidate()
                onDraw?.invoke(RectF(frameRect))
            }
        }.start()
    }

    private fun hitTest(x: Float, y: Float): DragMode {
        val corners = arrayOf(
            Triple(DragMode.TL, frameRect.left, frameRect.top),
            Triple(DragMode.TR, frameRect.right, frameRect.top),
            Triple(DragMode.BL, frameRect.left, frameRect.bottom),
            Triple(DragMode.BR, frameRect.right, frameRect.bottom)
        )
        for ((mode, cx, cy) in corners) {
            if (dist(x, y, cx, cy) < handleRadius * 2) return mode
        }
        val e = handleRadius * 1.5f
        if (y in (frameRect.top - e)..(frameRect.top + e) && x in frameRect.left..frameRect.right) return DragMode.TOP
        if (y in (frameRect.bottom - e)..(frameRect.bottom + e) && x in frameRect.left..frameRect.right) return DragMode.BOTTOM
        if (x in (frameRect.left - e)..(frameRect.left + e) && y in frameRect.top..frameRect.bottom) return DragMode.LEFT
        if (x in (frameRect.right - e)..(frameRect.right + e) && y in frameRect.top..frameRect.bottom) return DragMode.RIGHT
        if (frameRect.contains(x, y)) return DragMode.MOVE
        return DragMode.NONE
    }

    private fun applyDrag(dx: Float, dy: Float) {
        when (dragMode) {
            DragMode.MOVE -> frameRect.offset(dx, dy)
            DragMode.TL -> { frameRect.left = (frameRect.left + dx).coerceAtMost(frameRect.right - minFrameSize); frameRect.top = (frameRect.top + dy).coerceAtMost(frameRect.bottom - minFrameSize) }
            DragMode.TR -> { frameRect.right = (frameRect.right + dx).coerceAtLeast(frameRect.left + minFrameSize); frameRect.top = (frameRect.top + dy).coerceAtMost(frameRect.bottom - minFrameSize) }
            DragMode.BL -> { frameRect.left = (frameRect.left + dx).coerceAtMost(frameRect.right - minFrameSize); frameRect.bottom = (frameRect.bottom + dy).coerceAtLeast(frameRect.top + minFrameSize) }
            DragMode.BR -> { frameRect.right = (frameRect.right + dx).coerceAtLeast(frameRect.left + minFrameSize); frameRect.bottom = (frameRect.bottom + dy).coerceAtLeast(frameRect.top + minFrameSize) }
            DragMode.TOP -> frameRect.top = (frameRect.top + dy).coerceAtMost(frameRect.bottom - minFrameSize)
            DragMode.BOTTOM -> frameRect.bottom = (frameRect.bottom + dy).coerceAtLeast(frameRect.top + minFrameSize)
            DragMode.LEFT -> frameRect.left = (frameRect.left + dx).coerceAtMost(frameRect.right - minFrameSize)
            DragMode.RIGHT -> frameRect.right = (frameRect.right + dx).coerceAtLeast(frameRect.left + minFrameSize)
            DragMode.NONE -> {}
        }
        invalidate()
    }

    private fun dist(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x1 - x2; val dy = y1 - y2
        return Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
    }

    private fun dpToPx(dp: Float): Float {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, context.resources.displayMetrics)
    }
}
