package org.jvc.progame

import android.content.Intent
import android.graphics.drawable.GradientDrawable
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
    private lateinit var secShizuku: Seccion
    private lateinit var secDns: Seccion
    private lateinit var secPantalla: Seccion
    private lateinit var secHz: Seccion
    private lateinit var secOverlay: Seccion
    private lateinit var secCongelador: Seccion
    private lateinit var anchoInput: EditText
    private lateinit var altoInput: EditText
    private lateinit var dpiInput: EditText
    private var paqueteElegido: String? = null
    private var nombresApps: List<String> = emptyList()
    private var paquetesApps: List<String> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(construirUI())
        ShizukuShell.iniciar(this)
        refrescarEstado()
        lanzar { secShizuku.decir(ShizukuShell.pruebaServicio()) }
    }

    override fun onResume() {
        super.onResume()
        refrescarEstado()
    }

    /** Recuadro con su titulo y su propio resultado, para no depender del registro de abajo. */
    private inner class Seccion(val titulo: String) {

        private val salida = TextView(this@MainActivity).apply {
            textSize = 14f
            setTextColor(0xFF00FFB7.toInt())
            setPadding(6, 4, 6, 8)
        }

        val caja = LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 4, 20, 10)
            background = GradientDrawable().apply {
                setColor(0xFF16161A.toInt())
                setStroke(2, 0xFF3A3A46.toInt())
                cornerRadius = 20f
            }
        }

        init {
            caja.addView(TextView(this@MainActivity).apply {
                text = titulo
                textSize = 15f
                setTextColor(0xFFC81919.toInt())
                setPadding(0, 12, 0, 4)
            })
            caja.addView(salida)
        }

        fun boton(texto: String, accion: () -> Unit) = caja.addBoton(texto, accion)

        fun campo(pista: String): EditText {
            val e = EditText(this@MainActivity).apply {
                hint = pista
                textSize = 15f
                setTextColor(0xFFFFFFFF.toInt())
            }
            caja.addView(e)
            return e
        }

        fun decir(texto: String) {
            runOnUiThread { salida.text = texto }
            loguea("$titulo: $texto")
        }

        fun ejecutar(etiqueta: String, orden: () -> ShellResult) = lanzar {
            decir("$etiqueta -> ${orden().resumen()}")
        }

        fun lectura(etiqueta: String, orden: () -> ShellResult) = lanzar {
            val r = orden()
            decir("$etiqueta -> ${if (r.ok) r.out.trim().ifBlank { "sin dato" } else r.resumen()}")
        }
    }

    // ---------- UI ----------

    private fun construirUI(): ScrollView {
        val raiz = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }

        raiz.addView(TextView(this).apply {
            text = "JVC-PRO-GAME"
            textSize = 26f
            setTextColor(0xFFC81919.toInt())
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 56))

        estado = TextView(this).apply { textSize = 13f; setTextColor(0xFFAAAAAA.toInt()) }
        raiz.addView(estado)

        fun anadir(s: Seccion) = raiz.addView(
            s.caja,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 18, 0, 0) }
        )

        secShizuku = Seccion("SHIZUKU")
        secShizuku.boton("Conceder permiso a Shizuku") { pedirPermisoShizuku() }
        secShizuku.boton("Comprobar estado") {
            lanzar { secShizuku.decir("vivo: ${ShizukuShell.binderVivo()} · permiso: ${ShizukuShell.permisoConcedido()}") }
        }
        secShizuku.boton("Probar servicio de comandos") {
            lanzar { secShizuku.decir(ShizukuShell.pruebaServicio()) }
        }
        anadir(secShizuku)

        secDns = Seccion("DNS PRIVADO")
        JvcCommands.DNS_PRESETS.forEach { (nombre, host) ->
            secDns.boton("DNS: $nombre") { secDns.ejecutar("DNS $host") { JvcCommands.ponDns(host) } }
        }
        secDns.boton("DNS: apagar") { secDns.ejecutar("DNS off") { JvcCommands.apagaDns() } }
        secDns.boton("DNS: leer el actual") { secDns.lectura("DNS actual") { JvcCommands.leeDnsActual() } }
        anadir(secDns)

        secPantalla = Seccion("PANTALLA: ANCHO / ALTO / DPI")
        anchoInput = secPantalla.campo("ancho en px, ej. 900")
        altoInput = secPantalla.campo("alto en px, ej. 2000")
        dpiInput = secPantalla.campo("dpi, ej. 440")
        secPantalla.boton("Aplicar") { aplicarPantalla() }
        secPantalla.boton("Ver resolucion nativa y limites") { verLimites() }
        secPantalla.boton("Dejarlo como estaba (reset)") {
            secPantalla.ejecutar("reset de pantalla") { JvcCommands.reseteaPantalla() }
        }
        anadir(secPantalla)

        secHz = Seccion("REFRESH RATE")
        listOf(60, 90, 120).forEach { hz ->
            secHz.boton("Forzar $hz Hz") {
                secHz.ejecutar("$hz Hz (algun fabricante lo ignora)") { JvcCommands.ponHz(hz) }
            }
        }
        anadir(secHz)

        secOverlay = Seccion("OVERLAY FPS + Hz")
        secOverlay.boton("Elegir juego y lanzar contador") { elegirJuego(secOverlay, porOverlay = true) }
        secOverlay.boton("Quitar overlay") {
            stopService(Intent(this, FpsOverlayService::class.java))
            secOverlay.decir("overlay quitado")
        }
        anadir(secOverlay)

        secCongelador = Seccion("CONGELADOR")
        secCongelador.boton("Elegir app y congelarla") { elegirJuego(secCongelador, accion = "congelar") }
        secCongelador.boton("Elegir app y descongelarla") { elegirJuego(secCongelador, accion = "descongelar") }
        secCongelador.boton("Elegir app y matarla") { elegirJuego(secCongelador, accion = "matar") }
        secCongelador.boton("Elegir app y abrirla") { elegirJuego(secCongelador, accion = "abrir") }
        anadir(secCongelador)

        val registro = Seccion("REGISTRO")
        log = TextView(this).apply {
            textSize = 12f
            setTextColor(0xFF00FFB7.toInt())
        }
        registro.caja.addView(log)
        anadir(registro)

        return ScrollView(this).apply {
            addView(raiz)
            setBackgroundColor(0xFF0D0D0F.toInt())
        }
    }

    private fun LinearLayout.addBoton(texto: String, accion: () -> Unit) = addView(
        Button(this@MainActivity).apply {
            text = texto
            setOnClickListener { accion() }
        },
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    )

    // ---------- pantalla ----------

    private fun aplicarPantalla() {
        val a = anchoInput.text.toString().trim().toIntOrNull()
        val h = altoInput.text.toString().trim().toIntOrNull()
        val d = dpiInput.text.toString().trim().toIntOrNull()
        when {
            a == null && h == null && d == null -> secPantalla.decir("rellena ancho+alto, o al menos el dpi")
            (a == null) != (h == null) -> secPantalla.decir("para el tamano hacen falta ancho Y alto a la vez")
            else -> lanzar {
                val partes = ArrayList<String>()
                if (a != null && h != null) {
                    val nativo = JvcCommands.tamanoNativo(this)
                    partes.add("tamano ${a}x${h} -> " + JvcCommands.ponTamano(a, h, nativo).resumen())
                }
                if (d != null) partes.add("dpi $d -> " + JvcCommands.ponDensidad(d).resumen())
                secPantalla.decir(partes.joinToString(" · "))
            }
        }
    }

    private fun verLimites() {
        lanzar {
            val (nw, nh) = JvcCommands.tamanoNativo(this)
            val tam = JvcCommands.leeTamano()
            val den = JvcCommands.leeDensidad()
            secPantalla.decir(
                "nativo ${nw}x${nh} · permitido ancho ${nw / 2}-$nw, alto ${nh / 2}-$nh\n" +
                    tam.out.trim() + "\ndensidad: " + den.out.trim()
            )
        }
    }

    // ---------- Shizuku ----------

    private fun pedirPermisoShizuku() {
        if (!ShizukuShell.binderVivo()) {
            secShizuku.decir("Shizuku no esta corriendo: abre la app Shizuku y activalo")
            return
        }
        if (ShizukuShell.permisoConcedido()) {
            secShizuku.decir("ya tienes el permiso concedido")
            return
        }
        try {
            Shizuku.requestPermission(0)
            secShizuku.decir("permiso pedido: acepta el popup de Shizuku")
        } catch (e: Exception) {
            secShizuku.decir("fallo al pedir permiso: ${e.message}")
        }
    }

    private fun refrescarEstado() {
        val vivo = ShizukuShell.binderVivo()
        val permiso = ShizukuShell.permisoConcedido()
        val overlay = Settings.canDrawOverlays(this)
        estado.text = buildString {
            append("APK "); append(textoVersion())
            append("  ·  Shizuku: "); append(if (vivo && permiso) "activo" else if (vivo) "sin permiso" else "APAGADO")
            append("  ·  Overlay: "); append(if (overlay) "permitido" else "no permitido")
            append("  ·  Juego: "); append(paqueteElegido ?: "ninguno")
            if (vivo && !permiso) Handler(Looper.getMainLooper()).postDelayed({ refrescarEstado() }, 1500)
        }
    }

    private fun textoVersion(): String = try {
        val pi = packageManager.getPackageInfo(packageName, 0)
        "${pi.versionName} build ${pi.versionCode}"
    } catch (e: Exception) {
        "?"
    }

    // ---------- acciones ----------

    private fun elegirJuego(sec: Seccion, porOverlay: Boolean = false, accion: String? = null) {
        lanzar {
            val lista = JvcCommands.appsInstaladas(this)
            nombresApps = lista.map { it.first }
            paquetesApps = lista.map { it.second }
            if (nombresApps.isEmpty()) {
                sec.decir("no encuentro apps de usuario instaladas")
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
                            porOverlay -> pedirOverlayYMostrar(sec, pkg)
                            accion == "congelar" -> sec.ejecutar("$pkg congelar") { JvcCommands.congela(pkg) }
                            accion == "descongelar" -> {
                                if (!JvcCommands.soportaSuspension()) sec.decir("pm unsuspend necesita Android 12+. Usa 'matar'.")
                                else sec.ejecutar("$pkg descongelar") { JvcCommands.descongela(pkg) }
                            }
                            accion == "matar" -> sec.ejecutar("$pkg matar") { JvcCommands.mata(pkg) }
                            accion == "abrir" -> abrirApp(sec, pkg)
                        }
                    }
                    .show()
            }
        }
    }

    private fun pedirOverlayYMostrar(sec: Seccion, pkg: String) {
        if (!Settings.canDrawOverlays(this)) {
            sec.decir("falta el permiso de overlay: toca Allow en la pantalla de Ajustes que se abre")
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
        sec.decir("overlay de FPS activo para $pkg")
    }

    private fun abrirApp(sec: Seccion, pkg: String) {
        val it = packageManager.getLaunchIntentForPackage(pkg)
        if (it == null) {
            sec.decir("$pkg no tiene actividad de lanzamiento")
        } else {
            startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            sec.decir("abierta $pkg")
        }
    }

    private fun lanzar(bloque: () -> Unit) {
        Thread {
            try {
                bloque()
            } catch (e: Exception) {
                loguea("Excepcion: ${e.javaClass.simpleName} ${e.message}")
            }
        }.start()
    }

    private fun loguea(m: String) = runOnUiThread {
        log.text = (m + "\n" + log.text).take(2500)
    }
}
