package com.sketchreproducer.sketch_reproducer

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityManager
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {

    companion object {
        private const val TAG = "MainActivity"
        private const val CHANNEL_NAME = "com.sketchreproducer/drawing"
    }

    private lateinit var methodChannel: MethodChannel

    // 暂存笔画数据
    private var sketchStrokes: List<List<FloatArray>>? = null
    private var sketchCanvasWidth: Int = 0
    private var sketchCanvasHeight: Int = 0

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        methodChannel = MethodChannel(
            flutterEngine.dartExecutor.binaryMessenger,
            CHANNEL_NAME
        )

        methodChannel.setMethodCallHandler { call, result ->
            when (call.method) {
                "isAccessibilityEnabled" -> {
                    result.success(isAccessibilityServiceEnabled())
                }
                "openAccessibilitySettings" -> {
                    openAccessibilitySettings()
                    result.success(null)
                }
                "canDrawOverlay" -> {
                    result.success(canDrawOverlay())
                }
                "requestOverlayPermission" -> {
                    requestOverlayPermission()
                    result.success(null)
                }
                "showFloatingButton" -> {
                    showFloatingButton()
                    result.success(null)
                }
                "hideFloatingButton" -> {
                    hideFloatingButton()
                    result.success(null)
                }
                "goToBackground" -> {
                    goToBackground()
                    result.success(null)
                }
                "sendSketchData" -> {
                    handleSendSketchData(call.arguments)
                    result.success(null)
                }
                "stopDrawing" -> {
                    DrawingAccessibilityService.stopDrawing()
                    result.success(null)
                }
                else -> result.notImplemented()
            }
        }
    }

    // ===== 权限 =====

    private fun isAccessibilityServiceEnabled(): Boolean {
        val am = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        return am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { it.resolveInfo.serviceInfo.packageName == packageName }
    }

    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    private fun canDrawOverlay(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(this) else true
    }

    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                    .apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            )
        }
    }

    // ===== 悬浮窗 =====

    private fun showFloatingButton() {
        // 先把笔画数据传给 Service
        FloatingWindowService.pendingStrokes = sketchStrokes
        FloatingWindowService.pendingCanvasWidth = sketchCanvasWidth
        FloatingWindowService.pendingCanvasHeight = sketchCanvasHeight

        val intent = Intent(this, FloatingWindowService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun hideFloatingButton() {
        stopService(Intent(this, FloatingWindowService::class.java))
    }

    private fun goToBackground() {
        // 用 moveTaskToBack 温和地退到后台，而不是启动 HOME Intent
        moveTaskToBack(true)
    }

    // ===== 笔画数据解析 =====

    @Suppress("UNCHECKED_CAST")
    private fun handleSendSketchData(arguments: Any?) {
        try {
            val data = arguments as? Map<*, *> ?: return
            val strokesRaw = data["strokes"] as? List<*> ?: return
            val cw = data["canvasWidth"] as? Int ?: return
            val ch = data["canvasHeight"] as? Int ?: return

            val strokes = mutableListOf<List<FloatArray>>()
            for (strokeRaw in strokesRaw) {
                val pointsRaw = strokeRaw as? Map<*, *> ?: continue
                val pointsList = pointsRaw["points"] as? List<*> ?: continue
                val points = mutableListOf<FloatArray>()
                for (pointRaw in pointsList) {
                    val point = pointRaw as? Map<*, *> ?: continue
                    val x = (point["x"] as? Number)?.toFloat() ?: continue
                    val y = (point["y"] as? Number)?.toFloat() ?: continue
                    points.add(floatArrayOf(x, y))
                }
                if (points.size >= 2) strokes.add(points)
            }

            sketchStrokes = strokes
            sketchCanvasWidth = cw
            sketchCanvasHeight = ch
            Log.d(TAG, "收到笔画数据: ${strokes.size} 条笔画, 画布 ${cw}x${ch}")
        } catch (e: Exception) {
            Log.e(TAG, "解析笔画数据失败: ${e.message}")
        }
    }
}
