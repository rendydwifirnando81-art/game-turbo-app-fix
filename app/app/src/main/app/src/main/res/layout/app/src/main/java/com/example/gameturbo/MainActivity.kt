package com.example.gameturbo

import android.app.ActivityManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
import android.widget.RadioGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var boostManager: BoostManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        boostManager = BoostManager(this)

        val btnBoost = findViewById<Button>(R.id.btnBoost)
        val btnStartCrosshair = findViewById<Button>(R.id.btnStartCrosshair)
        val btnStopCrosshair = findViewById<Button>(R.id.btnStopCrosshair)
        val radioGroup = findViewById<RadioGroup>(R.id.rgCrosshairStyle)

        btnBoost.setOnClickListener {
            val freedRam = boostManager.boostPerformance()
            Toast.makeText(this, "HP Dioptimalkan! $freedRam MB RAM Dibebaskan.", Toast.LENGTH_LONG).show()
        }

        btnStartCrosshair.setOnClickListener {
            if (checkOverlayPermission()) {
                val selectedStyle = when (radioGroup.checkedRadioButtonId) {
                    R.id.rbStyle1 -> 1
                    R.id.rbStyle2 -> 2
                    R.id.rbStyle3 -> 3
                    else -> 1
                }

                val intent = Intent(this, CrosshairService::class.java).apply {
                    putExtra("CROSSHAIR_STYLE", selectedStyle)
                }
                startService(intent)
                Toast.makeText(this, "Crosshair Aktif!", Toast.LENGTH_SHORT).show()
            } else {
                requestOverlayPermission()
            }
        }

        btnStopCrosshair.setOnClickListener {
            stopService(Intent(this, CrosshairService::class.java))
            Toast.makeText(this, "Crosshair Dimatikan", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }
    }

    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivityForResult(intent, 1234)
        }
    }
}

class BoostManager(private val context: Context) {
    fun boostPerformance(): Long {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfoBefore = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfoBefore)
        val runningProcesses = activityManager.runningAppProcesses
        runningProcesses?.forEach { process ->
            if (process.pkgList.isNotEmpty() && process.pkgList[0] != context.packageName) {
                activityManager.killBackgroundProcesses(process.pkgList[0])
            }
        }
        System.gc()
        Runtime.getRuntime().gc()
        val memoryInfoAfter = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfoAfter)
        return (memoryInfoAfter.availMem - memoryInfoBefore.availMem) / (1024 * 1024)
    }
}

class CrosshairService : Service() {
    private lateinit var windowManager: WindowManager
    private var crosshairView: View? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val style = intent?.getIntExtra("CROSSHAIR_STYLE", 1) ?: 1
        showCrosshair(style)
        return START_STICKY
    }

    private fun showCrosshair(style: Int) {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        if (crosshairView != null) { windowManager.removeView(crosshairView) }
        val imageView = ImageView(this)
        when (style) {
            1 -> imageView.setImageResource(android.R.drawable.ic_menu_compass)
            2 -> imageView.setImageResource(android.R.drawable.ic_menu_add)
            3 -> imageView.setImageResource(android.R.drawable.ic_menu_search)
        }
        imageView.setColorFilter(Color.RED)
        val layoutParamsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val params = WindowManager.LayoutParams(
            120, 120, layoutParamsType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.CENTER }
        crosshairView = imageView
        windowManager.addView(crosshairView, params)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (crosshairView != null) { windowManager.removeView(crosshairView) }
    }
}
