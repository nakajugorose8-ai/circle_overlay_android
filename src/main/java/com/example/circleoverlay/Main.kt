package com.example.circleoverlay

import android.animation.ValueAnimator
import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import android.widget.Toast

// Open the app once to show the circle, open it again to remove it.
class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(
                this,
                "Allow \"Display over other apps\", then open the app again",
                Toast.LENGTH_LONG
            ).show()
            startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            )
        } else {
            val intent = Intent(this, OverlayService::class.java)
            if (OverlayService.running) stopService(intent) else startForegroundService(intent)
        }
        finish()
    }
}

class OverlayService : Service() {
    private lateinit var windowManager: WindowManager
    private var circle: CircleView? = null

    override fun onCreate() {
        super.onCreate()
        running = true

        val channelId = "circle_overlay"
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(channelId, "Circle overlay", NotificationManager.IMPORTANCE_LOW)
        )
        val notification = Notification.Builder(this, channelId)
            .setContentTitle("Circle overlay is on")
            .setContentText("Open the app again to turn it off")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, notification)
        }

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val sizePx = (200 * resources.displayMetrics.density).toInt()
        val params = WindowManager.LayoutParams(
            sizePx, sizePx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.CENTER }

        circle = CircleView(this).also { windowManager.addView(it, params) }
    }

    override fun onDestroy() {
        circle?.let {
            it.stop()
            windowManager.removeView(it)
        }
        running = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        var running = false
    }
}

class CircleView(context: Context) : View(context) {

    // ---- settings ----
    private val pulsing = true            // false = static circle
    private val circleColor = Color.parseColor("#00FF66")
    private val baseRadiusDp = 30f
    private val pulseDp = 12f
    private val periodMs = 800L
    // ------------------

    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f * density
        color = circleColor
    }
    private var radius = baseRadiusDp * density
    private var animator: ValueAnimator? = null

    init {
        if (pulsing) {
            animator = ValueAnimator.ofFloat(-1f, 1f).apply {
                duration = periodMs
                repeatMode = ValueAnimator.REVERSE
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
                addUpdateListener {
                    radius = (baseRadiusDp + pulseDp * (it.animatedValue as Float)) * density
                    invalidate()
                }
                start()
            }
        }
    }

    fun stop() {
        animator?.cancel()
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawCircle(width / 2f, height / 2f, radius, paint)
    }
}
