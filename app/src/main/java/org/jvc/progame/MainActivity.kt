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
    private lateinit var secRendimiento: Seccion
    private lateinit var secLanzador: Seccion
    private lateinit var anchoInput: EditText
    private lateinit var altoInput: EditText
    private lateinit var dpiInput: EditText
    private var paqueteElegido: String? = null
    private var ultimaDns: String? = null
    private var ultimoHz: Int? = null
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
            val dato = r.out.trim()
            // Un grep que no encuentra nada sale con codigo 1: si hay texto, es el dato.
            decir("$etiqueta -> ${dato.ifBlank { if (r.ok) "sin dato" else r.resumen() }}")
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
            secDns.boton("DNS: $nombre") {
                ultimaDns = host
                secDns.ejecutar("DNS $host") { JvcCommands.ponDns(host) }
            }
        }
        secDns.boton("DNS: APAGAR (si te quedaste sin Internet)") {
            ultimaDns = null
            secDns.ejecutar("DNS off") { JvcCommands.apagaDns() }
        }
        secDns.boton("DNS: como esta ahora") { secDns.lectura("DNS actual") { JvcCommands.leeDnsActual() } }
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
                ultimoHz = hz
                secHz.ejecutar("$hz Hz (algun fabricante lo ignora)") { JvcCommands.ponHz(hz) }
            }
        }
        secHz.boton("¿Hz reales que esta dando la pantalla?") {
            secHz.lectura("modo actual") { ShizukuShell.ejecutar("dumpsys display | grep -m2 -E 'mCurrentRefreshRate|modeId'") }
        }
        anadir(secHz)

        secOverlay = Seccion("OVERLAY FPS + Hz")
        secOverlay.boton("Elegir juego y lanzar contador") { elegirJuego(secOverlay, porOverlay = true) }
        secOverlay.boton("Contador automatico (detecta el juego solo)") { pedirOverlayYMostrar(secOverlay, null) }
        secOverlay.boton("Quitar overlay") {
            stopService(Intent(this, FpsOverlayService::class.java))
            secOverlay.decir("overlay quitado")
        }
        secOverlay.boton("¿Que capa ve SurfaceFlinger de mi juego?") {
            val pkg = paqueteElegido
            if (pkg == null) secOverlay.decir("primero elige el juego, o usalo desde el recuadro LANZADOR")
            else secOverlay.lectura("capas de $pkg") { JvcCommands.capasDe(pkg) }
        }
        anadir(secOverlay)

        secRendimiento = Seccion("ALTO RENDIMIENTO")
        secRendimiento.boton("Quitar animaciones (se nota al tacto)") {
            secRendimiento.ejecutar("animaciones a 0") { JvcCommands.animaCero() }
        }
        secRendimiento.boton("Poner animaciones otra vez") {
            secRendimiento.ejecutar("animaciones a 1") { JvcCommands.animaNormal() }
        }
        secRendimiento.boton("Apagar el ahorro de bateria") {
            secRendimiento.ejecutar("low_power off") { JvcCommands.ahorroOff() }
        }
        secRendimiento.boton("Sacar el juego del reposo de la ROM") { fueraDeReposo() }
        secRendimiento.boton("¿Por que frena? temperatura") {
            secRendimiento.lectura("termica") { JvcCommands.estadoTermico() }
        }
        secRendimiento.boton("Ver frenos de CPU (lectura)") {
            secRendimiento.lectura("cpu") { JvcCommands.frenosCpu() }
        }
        anadir(secRendimiento)

        secLanzador = Seccion("LANZADOR: TODO DE UN GOLPE")
        secLanzador.boton("1. Elegir el juego del lanzador") { elegirJuego(secLanzador, accion = "perfil") }
        secLanzador.boton("2. Guardar perfil con lo de las casillas") { guardarPerfil() }
        secLanzador.boton("3. LANZAR TODO y abrir el juego") { lanzarTodo() }
        secLanzador.boton("Ver perfil guardado") { verPerfil() }
        anadir(secLanzador)

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

    // ---------- lanzador ----------

    private fun prefs() = getSharedPreferences("perfil", MODE_PRIVATE)

    private fun fueraDeReposo() {
        val pkg = paqueteElegido
        if (pkg == null) secRendimiento.decir("primero elige el juego en el recuadro LANZADOR")
        else secRendimiento.ejecutar("$pkg fuera de Doze") { JvcCommands.fueraDeReposo(pkg) }
    }

    private fun guardarPerfil() {
        val a = anchoInput.text.toString().trim().toIntOrNull()
        val h = altoInput.text.toString().trim().toIntOrNull()
        val d = dpiInput.text.toString().trim().toIntOrNull()
        prefs().edit()
            .putString("pkg", paqueteElegido ?: "")
            .putString("dns", ultimaDns ?: "")
            .putInt("hz", ultimoHz ?: 0)
            .putInt("ancho", a ?: 0)
            .putInt("alto", h ?: 0)
            .putInt("dpi", d ?: 0)
            .apply()
        secLanzador.decir(
            "perfil guardado: ${paqueteElegido ?: "sin juego"} · dns ${ultimaDns ?: "ninguno"} · " +
                "hz ${ultimoHz ?: 0} · ${a ?: 0}x${h ?: 0} · dpi ${d ?: 0}"
        )
    }

    private fun verPerfil() {
        val p = prefs()
        secLanzador.decir(
            "juego " + (p.getString("pkg", "") ?: "").ifBlank { "ninguno" } +
                " · dns " + (p.getString("dns", "") ?: "").ifBlank { "ninguno" } +
                " · hz " + p.getInt("hz", 0) +
                " · tamano " + p.getInt("ancho", 0) + "x" + p.getInt("alto", 0) +
                " · dpi " + p.getInt("dpi", 0)
        )
    }

    /** Aplica todo el perfil en orden y abre el juego; cada paso escribe su propia linea. */
    private fun lanzarTodo() {
        val p = prefs()
        val pkg = p.getString("pkg", "")?.ifBlank { null }
            ?: paqueteElegido?.also { p.edit().putString("pkg", it).apply() }
        val dns = p.getString("dns", "")?.ifBlank { null }
        val hz = p.getInt("hz", 0)
        val a = p.getInt("ancho", 0)
        val h = p.getInt("alto", 0)
        val d = p.getInt("dpi", 0)
        if (pkg == null) {
            secLanzador.decir("elige el juego con el boton 1 primero")
            return
        }
        secLanzador.decir("aplicando perfil para $pkg ...")
        lanzar {
            val lineas = ArrayList<String>()
            if (dns != null) lineas.add("dns -> " + JvcCommands.ponDns(dns).resumen())
            if (a > 0 && h > 0) {
                val nativo = JvcCommands.tamanoNativo(this)
                lineas.add("tamano ${a}x${h} -> " + JvcCommands.ponTamano(a, h, nativo).resumen())
            }
            if (d > 0) lineas.add("dpi $d -> " + JvcCommands.ponDensidad(d).resumen())
            if (hz > 0) lineas.add("$hz Hz -> " + JvcCommands.ponHz(hz).resumen())
            lineas.add("animaciones -> " + JvcCommands.animaCero().resumen())
            lineas.add("ahorro bateria -> " + JvcCommands.ahorroOff().resumen())
            val r = JvcCommands.fueraDeReposo(pkg)
            lineas.add("juego fuera de Doze -> " + r.resumen())
            overlayYJuego(pkg, lineas)
        }
    }

    /** Ultimo paso dentro del hilo de fondo: overlay, abrir el juego y escribir el resumen. */
    private fun overlayYJuego(pkg: String, lineas: ArrayList<String>) {
        if (Settings.canDrawOverlays(this)) {
            motivoOverlay = null
            ContextCompat.startForegroundService(
                this,
                Intent(this, FpsOverlayService::class.java).putExtra("pkg", pkg)
            )
            Thread.sleep(1500)
            lineas.add("overlay -> " + (motivoOverlay ?: "puesto"))
        } else {
            lineas.add("overlay -> SIN permiso: usa el boton del recuadro OVERLAY y acepta en Ajustes")
        }
        secLanzador.decir(lineas.joinToString("\n"))
        abrirApp(secLanzador, pkg)
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
                            accion == "perfil" -> sec.decir("juego del lanzador: $pkg · pulsa 2 para guardarlo y 3 para aplicar todo")
                        }
                    }
                    .show()
            }
        }
    }

    private fun pedirOverlayYMostrar(sec: Seccion, pkg: String?) {
        if (!Settings.canDrawOverlays(this)) {
            sec.decir("falta el permiso de overlay: toca Allow en la pantalla de Ajustes que se abre")
            startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return
        }
        motivoOverlay = null
        ContextCompat.startForegroundService(
            this,
            Intent(this, FpsOverlayService::class.java).putExtra("pkg", pkg)
        )
        sec.decir("overlay arrancando para ${pkg ?: "la app que este en pantalla"}")
        lanzar {
            Thread.sleep(1500)
            val m = motivoOverlay
            if (m != null) sec.decir(m) else if (pkg != null) {
                val fg = JvcCommands.appEnPrimeroPlano()
                sec.decir("contador activo · en pantalla: ${fg ?: "no se detecta"}")
            }
        }
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

    companion object {
        /** El servicio escribe aqui si no pudo poner el panel, para que el recuadro lo diga. */
        @Volatile var motivoOverlay: String? = null
    }
}
