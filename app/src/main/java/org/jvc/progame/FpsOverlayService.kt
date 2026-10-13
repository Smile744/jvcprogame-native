package org.jvc.progame

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Display
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView
import androidx.core.app.NotificationCompat
import java.util.concurrent.Executors
import kotlin.math.roundToInt

class FpsOverlayService : Service() {

    private lateinit var wm: WindowManager
    private lateinit var texto: TextView
    private var paquete: String? = null
    private val main = Handler(Looper.getMainLooper())
    private val pool = Executors.newSingleThreadExecutor()
    @Volatile private var corriendo = true

    override fun onBind(i: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        // Sin servicio en primer plano el sistema congela el bucle cuando abres el juego.
        startForeground(ID_NOTIF, notificacion(null))
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        texto = TextView(this).apply {
            setTextColor(Color.parseColor("#00FFB7"))
            textSize = 14f
            setTypeface(typeface, Typeface.BOLD)
            setBackgroundColor(Color.parseColor("#B3000000"))
            setPadding(14, 8, 14, 8)
        }
        wm.addView(texto, parametros())
        main.post { bucle() }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun parametros(): WindowManager.LayoutParams {
        val tipo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            tipo,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 24
            y = 120
        }
    }

    private fun bucle() {
        if (!corriendo) return
        val pkg = paquete
        if (pkg != null && ShizukuShell.listo()) {
            pool.execute {
                val fps = JvcCommands.fpsDe(pkg)
                main.post { pinta(fps, pkg) }
            }
        } else {
            main.post { pinta(null, pkg) }
        }
        main.postDelayed({ bucle() }, 1000)
    }

    private fun pinta(fps: Int?, pkg: String?) {
        val hz = hzPantalla()
        texto.text = when {
            fps == null && pkg == null -> "Hz $hz | FPS: elige juego"
            fps == null -> "Hz $hz | FPS: sin Shizuku"
            else -> "Hz $hz | FPS $fps  ${pkg?.substringAfterLast('.') ?: ""}"
        }
    }

    private fun hzPantalla(): Int {
        val dm = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val d = dm.getDisplay(DisplayManager.DEFAULT_DISPLAY)
        return try {
            d?.mode?.refreshRate?.roundToInt() ?: 0
        } catch (e: Exception) {
            0
        }
    }

    private fun notificacion(pkg: String?): Notification {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CANAL, "Contador de FPS", NotificationManager.IMPORTANCE_LOW)
            )
        }
        return NotificationCompat.Builder(this, CANAL)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("JVC-PRO-GAME")
            .setContentText(if (pkg == null) "Overlay activo" else "FPS de $pkg")
            .setOngoing(true)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        paquete = intent?.getStringExtra("pkg")
        startForeground(ID_NOTIF, notificacion(paquete))
        return START_STICKY
    }

    override fun onDestroy() {
        corriendo = false
        pool.shutdownNow()
        try { wm.removeView(texto) } catch (e: Exception) { }
        super.onDestroy()
    }

    companion object {
        private const val ID_NOTIF = 41
        private const val CANAL = "fps_overlay"
    }
}
