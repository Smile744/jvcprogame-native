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
import android.provider.Settings
import android.view.Display
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView
import androidx.core.app.NotificationCompat
import java.util.concurrent.Executors
import kotlin.math.roundToInt

class FpsOverlayService : Service() {

    private var wm: WindowManager? = null
    private var texto: TextView? = null
    private var mira: MiraView? = null
    private var paquete: String? = null
    private val main = Handler(Looper.getMainLooper())
    private val pool = Executors.newSingleThreadExecutor()
    @Volatile private var corriendo = true
    private var sinCapa = 0

    override fun onBind(i: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ShizukuShell.iniciar(this)
        startForeground(ID_NOTIF, notificacion(null))
        if (!Settings.canDrawOverlays(this)) {
            pintaGlobal("OVERLAY: falta el permiso de sobre otras apps")
            stopSelf()
            return
        }
        val w = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val t = TextView(this).apply {
            setTextColor(Color.parseColor("#00FFB7"))
            textSize = 14f
            setTypeface(typeface, Typeface.BOLD)
            setBackgroundColor(Color.parseColor("#B3000000"))
            setPadding(14, 8, 14, 8)
        }
        try {
            w.addView(t, parametros())
        } catch (e: Exception) {
            pintaGlobal("OVERLAY rechazado: ${e.javaClass.simpleName}")
            stopSelf()
            return
        }
        wm = w
        texto = t
        val cfg = MiraPrefs.leer(this)
        if (cfg.activa) {
            val m = MiraView(this)
            m.aplicar(cfg)
            try {
                w.addView(m, parametrosMira())
                mira = m
            } catch (e: Exception) {
                pintaGlobal("MIRA rechazada: ${e.javaClass.simpleName}")
            }
        }
        main.post { bucle() }
    }

    /** Ocupa la pantalla entera pero sin foco y sin tacto: el dedo llega al juego igual. */
    private fun parametrosMira(): WindowManager.LayoutParams {
        val tipo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            tipo,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
    }

    /** Si el panel no existe aun, el motivo va al registro de la activity. */
    private fun pintaGlobal(m: String) {
        MainActivity.motivoOverlay = m
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
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 24
            y = 120
        }
    }

    private fun bucle() {
        if (!corriendo || texto == null) return
        mira?.aplicar(MiraPrefs.leer(this))
        val elegido = paquete
        if (!ShizukuShell.listo()) {
            pinta(null, elegido, "Shizuku caido")
        } else {
            pool.execute {
                // Primero la app elegida; si no tira frames, se pregunta quien esta en pantalla.
                var objetivo = elegido ?: JvcCommands.appEnPrimeroPlano()
                var medida = medir(objetivo)
                var frames = medida.first
                var motivo = medida.second
                if ((frames == null || frames == 0) && elegido != null) {
                    val enPantalla = JvcCommands.appEnPrimeroPlano()
                    if (enPantalla != null && enPantalla != objetivo) {
                        val segunda = medir(enPantalla)
                        val v = segunda.first
                        if (v != null && v > 0) {
                            objetivo = enPantalla
                            frames = v
                            motivo = "(auto) " + segunda.second
                        }
                    }
                }
                main.post { pinta(frames, objetivo, motivo.trim()) }
            }
        }
        main.postDelayed({ bucle() }, 1000)
    }

    /** Dos vias de conteo: la app misma y la capa que compone SurfaceFlinger. */
    private fun medir(pkg: String?): Pair<Int?, String> {
        if (pkg == null) return Pair(null, "sin app en pantalla")
        val gfx = JvcCommands.fpsDe(pkg)
        if (gfx != null && gfx > 0) {
            sinCapa = 0
            return Pair(gfx, "")
        }
        val capa = JvcCommands.fpsSuperficie(pkg)
        if (capa != null && capa > 0) {
            sinCapa = 0
            return Pair(capa, "(capa)")
        }
        sinCapa++
        // La capa cambia cuando el juego reinicia su ventana: se busca otra vez.
        if (sinCapa >= 5) {
            JvcCommands.olvidaCapa()
            sinCapa = 0
        }
        return Pair(gfx, if (gfx == null) "gfxinfo sin dato, capa sin dato" else "gfxinfo 0, capa sin dato")
    }

    private fun pinta(fps: Int?, pkg: String?, motivo: String) {
        val t = texto ?: return
        val hz = hzPantalla()
        val nombre = pkg?.substringAfterLast('.') ?: "?"
        t.text = when {
            fps == null -> "Hz $hz | FPS sin dato $motivo"
            fps == 0 -> "Hz $hz | FPS 0 $nombre $motivo"
            else -> "Hz $hz | FPS $fps $nombre $motivo"
        }
    }

    private fun hzPantalla(): Int {
        val dm = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val d = dm.getDisplay(Display.DEFAULT_DISPLAY)
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
        intent?.getStringExtra("pkg")?.let { paquete = it }
        // Si el overlay ya estaba puesto y ella acaba de activar la mira, se anade ahora.
        val w = wm
        if (w != null && mira == null && MiraPrefs.leer(this).activa) {
            val m = MiraView(this)
            try {
                w.addView(m, parametrosMira())
                mira = m
            } catch (e: Exception) {
                pintaGlobal("MIRA rechazada: ${e.javaClass.simpleName}")
            }
        }
        startForeground(ID_NOTIF, notificacion(paquete))
        return START_STICKY
    }

    override fun onDestroy() {
        corriendo = false
        pool.shutdownNow()
        try { wm?.removeView(mira) } catch (e: Exception) { }
        try { wm?.removeView(texto) } catch (e: Exception) { }
        super.onDestroy()
    }

    companion object {
        private const val ID_NOTIF = 41
        private const val CANAL = "fps_overlay"
    }
}
