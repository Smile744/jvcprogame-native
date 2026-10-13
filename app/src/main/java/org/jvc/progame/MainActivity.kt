package org.jvc.progame

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import rikka.shizuku.Shizuku

class MainActivity : AppCompatActivity() {

    private lateinit var log: TextView
    private lateinit var estado: TextView
    private lateinit var densidadInput: EditText
    private var paqueteElegido: String? = null
    private var nombresApps: List<String> = emptyList()
    private var paquetesApps: List<String> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(construirUI())
        ShizukuShell.iniciar(this)
        refrescarEstado()
    }

    override fun onResume() {
        super.onResume()
        refrescarEstado()
    }

    // ---------- UI ----------

    private fun construirUI(): ScrollView {
        val raiz = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 28, 28, 28)
        }

        raiz.addView(TextView(this).apply {
            text = "JVC-PRO-GAME"
            textSize = 26f
            setTextColor(0xFFC81919.toInt())
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 56))

        estado = TextView(this).apply { textSize = 14f; setTextColor(0xFFAAAAAA.toInt()) }
        raiz.addView(estado)

        raiz.addBoton("Conceder permiso a Shizuku") { pedirPermisoShizuku() }
        raiz.addBoton("Comprobar estado Shizuku") {
            lanzar { loguea("Shizuku vivo: ${ShizukuShell.binderVivo()} · permiso: ${ShizukuShell.permisoConcedido()}") }
        }

        raiz.addTitulo("DNS PRIVADO")
        JvcCommands.DNS_PRESETS.forEach { (nombre, host) ->
            raiz.addBoton("DNS: $nombre") {
                lanzar { val r = JvcCommands.ponDns(host); loguea("DNS $host -> ${r.resumen()}") }
            }
        }
        raiz.addBoton("DNS: apagar") { lanzar { loguea("DNS off -> ${JvcCommands.apagaDns().resumen()}") } }
        raiz.addBoton("DNS: leer el actual") { lanzar { val r = JvcCommands.leeDnsActual(); loguea(r.out.ifBlank { r.resumen() }) } }

        raiz.addTitulo("DENSIDAD (DPI)")
        densidadInput = EditText(this).apply {
            hint = "ej. 440 (160-640)"
            setTextColor(0xFFFFFFFF.toInt())
        }
        raiz.addView(densidadInput)
        raiz.addBoton("Aplicar densidad") {
            val v = densidadInput.text.toString().trim().toIntOrNull()
            if (v == null) loguea("Densidad: no es un numero")
            else lanzar { loguea("wm density $v -> ${JvcCommands.ponDensidad(v).resumen()}") }
        }
        raiz.addBoton("Resetear densidad") { lanzar { loguea("reset -> ${JvcCommands.reseteaDensidad().resumen()}") } }
        raiz.addBoton("Leer densidad") { lanzar { val r = JvcCommands.leeDensidad(); loguea(r.out.ifBlank { r.resumen() }) } }

        raiz.addTitulo("REFRESH RATE")
        listOf(60, 90, 120).forEach { hz ->
            raiz.addBoton("Forzar $hz Hz") {
                lanzar {
                    val r = JvcCommands.ponHz(hz)
                    loguea("$hz Hz -> ${r.resumen()} (algunos fabricantes lo ignoran)")
                }
            }
        }

        raiz.addTitulo("OVERLAY FPS + Hz")
        raiz.addBoton("Elegir juego y lanzar contador") { elegirJuego(porOverlay = true) }
        raiz.addBoton("Quitar overlay") {
            stopService(Intent(this, FpsOverlayService::class.java))
            loguea("Overlay quitado")
        }

        raiz.addTitulo("CONGELADOR")
        raiz.addBoton("Elegir app y congelarla") { elegirJuego(porOverlay = false, accion = "congelar") }
        raiz.addBoton("Elegir app y descongelarla") { elegirJuego(porOverlay = false, accion = "descongelar") }
        raiz.addBoton("Elegir app y matarla") { elegirJuego(porOverlay = false, accion = "matar") }
        raiz.addBoton("Elegir app y abrirla") { elegirJuego(porOverlay = false, accion = "abrir") }

        raiz.addTitulo("REGISTRO")
        log = TextView(this).apply {
            textSize = 13f
            setTextColor(0xFF00FFB7.toInt())
            text = ""
        }
        raiz.addView(log)

        return ScrollView(this).apply {
            addView(raiz)
            setBackgroundColor(0xFF0D0D0F.toInt())
        }
    }

    private fun LinearLayout.addTitulo(t: String) = addView(TextView(this@MainActivity).apply {
        text = t
        textSize = 15f
        setTextColor(0xFFC81919.toInt())
        setPadding(0, 28, 0, 6)
    })

    private fun LinearLayout.addBoton(texto: String, accion: () -> Unit) = addView(
        Button(this@MainActivity).apply {
            text = texto
            setOnClickListener { accion() }
        },
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    )

    // ---------- Shizuku ----------

    private fun pedirPermisoShizuku() {
        if (!ShizukuShell.binderVivo()) {
            loguea("Shizuku no esta corriendo. Abre la app Shizuku y activalo (pairing inalambrico).")
            return
        }
        if (ShizukuShell.permisoConcedido()) {
            loguea("Ya tienes el permiso concedido.")
            return
        }
        try {
            Shizuku.requestPermission(0)
            loguea("Permiso pedido: acepta el popup de Shizuku.")
        } catch (e: Exception) {
            loguea("Fallo al pedir permiso: ${e.message}")
        }
    }

    private fun refrescarEstado() {
        val vivo = ShizukuShell.binderVivo()
        val permiso = ShizukuShell.permisoConcedido()
        val overlay = Settings.canDrawOverlays(this)
        estado.text = buildString {
            append("Shizuku: "); append(if (vivo && permiso) "activo" else if (vivo) "sin permiso" else "APAGADO")
            append("  ·  Overlay: "); append(if (overlay) "permitido" else "no permitido")
            append("  ·  Juego: "); append(paqueteElegido ?: "ninguno")
            if (vivo && !permiso) Handler(Looper.getMainLooper()).postDelayed({ refrescarEstado() }, 1500)
        }
    }

    // ---------- acciones ----------

    private fun elegirJuego(porOverlay: Boolean, accion: String? = null) {
        lanzar {
            val lista = JvcCommands.appsInstaladas(this)
            nombresApps = lista.map { it.first }
            paquetesApps = lista.map { it.second }
            if (nombresApps.isEmpty()) {
                loguea("No encuentro apps de usuario instaladas.")
                return@lanzar
            }
            runOnUiThread {
                AlertDialog.Builder(this)
                    .setTitle("Elige una app")
                    .setItems(nombresApps.toTypedArray()) { _, i ->
                        val pkg = paquetesApps[i]
                        paqueteElegido = pkg
                        refrescarEstado()
                        when {
                            porOverlay -> pedirOverlayYMostrar(pkg)
                            accion == "congelar" -> lanzar { loguea("$pkg congelar -> ${JvcCommands.congela(pkg).resumen()}") }
                            accion == "descongelar" -> lanzar {
                                if (!JvcCommands.soportaSuspension()) loguea("pm unsuspend necesita Android 12+. Usa 'matar'.")
                                else loguea("$pkg descongelar -> ${JvcCommands.descongela(pkg).resumen()}")
                            }
                            accion == "matar" -> lanzar { loguea("$pkg matar -> ${JvcCommands.mata(pkg).resumen()}") }
                            accion == "abrir" -> abrirApp(pkg)
                        }
                    }
                    .show()
            }
        }
    }

    private fun pedirOverlayYMostrar(pkg: String) {
        if (!Settings.canDrawOverlays(this)) {
            loguea("Falta el permiso de overlay: toca Allow en la pantalla de Ajustes que se abre.")
            startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return
        }
        ContextCompat.startForegroundService(
            this,
            Intent(this, FpsOverlayService::class.java).putExtra("pkg", pkg)
        )
        loguea("Overlay de FPS activo para $pkg")
    }

    private fun abrirApp(pkg: String) {
        val it = packageManager.getLaunchIntentForPackage(pkg)
        if (it == null) loguea("$pkg no tiene actividad de lanzamiento.")
        else startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun lanzar(bloque: () -> Unit) {
        Thread {
            try {
                bloque()
            } catch (e: Exception) {
                runOnUiThread { loguea("Excepcion: ${e.javaClass.simpleName} ${e.message}") }
            }
        }.start()
    }

    private fun loguea(m: String) = runOnUiThread { log.text = "$m\n${log.text}" }
}
