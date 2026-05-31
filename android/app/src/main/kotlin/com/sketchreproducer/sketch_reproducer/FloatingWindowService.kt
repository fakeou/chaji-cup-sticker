package com.sketchreproducer.sketch_reproducer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.RectF
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.graphics.Color
import android.graphics.drawable.GradientDrawable

/**
 * 悬浮窗服务：
 * 1. 屏幕右侧悬浮按钮（始终显示）
 * 2. 点击后显示全屏覆盖层（调整框 + 笔画预览 + 操作按钮）
 */
class FloatingWindowService : Service() {

    companion object {
        private const val TAG = "FloatingWindow"
        private const val CHANNEL_ID = "floating_window_channel"
        private const val NOTIFICATION_ID = 1001

        var instance: FloatingWindowService? = null
            private set

        // 待处理的笔画数据
        var pendingStrokes: List<DrawingStroke>? = null
        var pendingCanvasWidth: Int = 0
        var pendingCanvasHeight: Int = 0
        var pendingBrushWidth: Float = 1.5f
    }

    private lateinit var windowManager: WindowManager
    private var floatingButton: View? = null
    private var overlayView: OverlayView? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        createFloatingButton()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        removeOverlay()
        removeFloatingButton()
        instance = null
        super.onDestroy()
    }

    // ===== 通知 =====

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "悬浮窗服务", NotificationManager.IMPORTANCE_LOW
            ).apply { description = "简笔画复刻悬浮窗运行中" }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pi = PendingIntent.getActivity(this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("简笔画复刻").setContentText("悬浮窗运行中")
                .setSmallIcon(android.R.drawable.ic_menu_edit).setContentIntent(pi).build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("简笔画复刻").setContentText("悬浮窗运行中")
                .setSmallIcon(android.R.drawable.ic_menu_edit).setContentIntent(pi).build()
        }
    }

    // ===== 悬浮按钮 =====

    private fun createFloatingButton() {
        val size = dpToPx(52)

        val button = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_menu_edit)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            val p = dpToPx(12)
            setPadding(p, p, p, p)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#2196F3"))
            }
            setColorFilter(Color.WHITE)
            elevation = 8f
        }

        val params = WindowManager.LayoutParams(
            size, size,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = resources.displayMetrics.widthPixels - size - dpToPx(12)
            y = resources.displayMetrics.heightPixels / 3
        }

        var initX = 0; var initY = 0
        var initTX = 0f; var initTY = 0f
        var dragging = false

        button.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initX = params.x; initY = params.y
                    initTX = event.rawX; initTY = event.rawY
                    dragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initTX
                    val dy = event.rawY - initTY
                    if (dx * dx + dy * dy > 100) dragging = true
                    params.x = initX + dx.toInt()
                    params.y = initY + dy.toInt()
                    try { windowManager.updateViewLayout(button, params) } catch (_: Exception) {}
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!dragging) onFloatingButtonClicked()
                    true
                }
                else -> false
            }
        }

        floatingButton = button
        windowManager.addView(button, params)
        Log.d(TAG, "悬浮按钮已创建")
    }

    private fun removeFloatingButton() {
        floatingButton?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
        }
        floatingButton = null
    }

    // ===== 悬浮按钮点击 → 显示覆盖层 =====

    private fun onFloatingButtonClicked() {
        if (overlayView != null) {
            removeOverlay()
            return
        }

        val strokes = pendingStrokes
        if (strokes.isNullOrEmpty()) {
            Log.w(TAG, "没有笔画数据，请先在 App 中上传图片")
            return
        }

        showOverlay(strokes, pendingCanvasWidth, pendingCanvasHeight)
    }

    // ===== 覆盖层 =====

    private fun showOverlay(strokes: List<DrawingStroke>, cw: Int, ch: Int) {
        // 隐藏悬浮按钮
        floatingButton?.visibility = View.INVISIBLE

        val view = OverlayView(this).apply {
            setStrokes(strokes, cw, ch)

            onDraw = { rect ->
                // 用户点击「开始绘制」
                Log.d(TAG, "开始绘制: $rect")
                removeOverlay()
                startAccessibilityDrawing(rect)
            }

            onCancel = {
                removeOverlay()
            }
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayType(),
            // FLAG_NOT_TOUCH_MODAL 让我们能接收全屏触摸
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            // 全屏
            width = WindowManager.LayoutParams.MATCH_PARENT
            height = WindowManager.LayoutParams.MATCH_PARENT
        }

        overlayView = view
        windowManager.addView(view, params)
        Log.d(TAG, "覆盖层已显示")
    }

    private fun removeOverlay() {
        overlayView?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
        }
        overlayView = null
        // 恢复悬浮按钮
        floatingButton?.visibility = View.VISIBLE
    }

    // ===== 触发绘制 =====

    private fun startAccessibilityDrawing(frame: RectF) {
        val strokes = pendingStrokes ?: return

        DrawingAccessibilityService.setDrawData(
            strokes,
            floatArrayOf(frame.left, frame.top, frame.right, frame.bottom),
            pendingCanvasWidth,
            pendingCanvasHeight,
            pendingBrushWidth
        )

        val success = DrawingAccessibilityService.startDrawing()
        Log.d(TAG, "绘制启动: $success")
    }

    // ===== 工具 =====

    private fun overlayType(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
    }

    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), resources.displayMetrics
        ).toInt()
    }
}
