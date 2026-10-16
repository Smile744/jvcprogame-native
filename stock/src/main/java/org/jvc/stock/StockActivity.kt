package org.jvc.stock

import android.app.Activity
import android.content.SharedPreferences
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.sql.Connection
import java.sql.DriverManager
import org.json.JSONArray
import org.json.JSONObject

class StockActivity : Activity() {

    private val ui = Handler(Looper.getMainLooper())
    private var conexion: Connection? = null
    private var enPuente = false
    private var ocupado = false
    private var autoEspejo = false
    private var tocarPermitido = false
    private var ampliacion = 1.6f

    private lateinit var cajaPuente: EditText
    private lateinit var cajaToken: EditText
    private lateinit var cajaHost: EditText
    private lateinit var cajaPuerto: EditText
    private lateinit var cajaBase: EditText
    private lateinit var cajaUsuario: EditText
    private lateinit var cajaClave: EditText
    private lateinit var cajaBusqueda: EditText
    private lateinit var cajaTabla: EditText
    private lateinit var estado: TextView
    private lateinit var estadoEspejo: TextView
    private lateinit var resultados: LinearLayout
    private lateinit var espejo: ImageView
    private lateinit var autoEspejoCaja: CheckBox
    private lateinit var tocarCaja: CheckBox
    private lateinit var cajaTexto: EditText
    private lateinit var cajaH: HorizontalScrollView
    private lateinit var cajaV: ScrollView
    private var anchoFoto = 0
    private var altoFoto = 0

    // No crearlo como propiedad: el Activity todavia no tiene contexto cuando se ejecuta <init>.
    private lateinit var gestos: GestureDetector
    private lateinit var pinza: ScaleGestureDetector

    private val tick = object : Runnable {
        override fun run() {
            if (!autoEspejo) return
            if (!ocupado) actualizarEspejo()
            ui.postDelayed(this, 8000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setStatusBarColor(Color.BLACK)
        val prefs = getSharedPreferences("stock", MODE_PRIVATE)

        cajaPuente = campo(pref(prefs, "puente", ""), InputType.TYPE_CLASS_TEXT)
        cajaPuente.hint = "http://192.168.0.120:8077  (vacio = ir directo a la base)"
        cajaToken = campo(pref(prefs, "token", ""), InputType.TYPE_CLASS_TEXT)
        cajaHost = campo(pref(prefs, "host", "190.104.134.156"), InputType.TYPE_CLASS_TEXT)
        cajaPuerto = campo(pref(prefs, "puerto", "5432"), InputType.TYPE_CLASS_NUMBER)
        cajaBase = campo(pref(prefs, "base", "motocave_sa"), InputType.TYPE_CLASS_TEXT)
        cajaUsuario = campo(pref(prefs, "usuario", ""), InputType.TYPE_CLASS_TEXT)
        cajaClave = campo(pref(prefs, "clave", ""),
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        cajaBusqueda = campo("", InputType.TYPE_CLASS_TEXT)
        cajaBusqueda.hint = "codigo o nombre"
        cajaTabla = campo("", InputType.TYPE_CLASS_TEXT)
        cajaTabla.hint = "vacio = la de productos"
        estado = linea("", 0xFF7A7A86.toInt())
        estadoEspejo = linea("todavia no pedi ninguna pantalla", 0xFF7A7A86.toInt())
        resultados = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        espejo = ImageView(this).apply {
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(0xFF16161A.toInt())
        }
        autoEspejoCaja = CheckBox(this).apply {
            text = "actualizar solo cada 8 segundos"
            textSize = 12f
            setTextColor(0xFF7A7A86.toInt())
            setOnCheckedChangeListener { _, marcado -> prenderAuto(marcado) }
        }
        tocarCaja = CheckBox(this).apply {
            text = "TOCAR: mi dedo maneja la PC (toque = clic, dos dedos = ampliar la foto)"
            textSize = 12f
            setTextColor(0xFFFFD479.toInt())
            setOnCheckedChangeListener { _, marcado -> tocarPermitido = marcado }
        }
        gestos = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true
            override fun onSingleTapUp(e: MotionEvent): Boolean { tocarEn(e.x, e.y); return true }
            override fun onDoubleTap(e: MotionEvent): Boolean { enviar("doble", e.x, e.y, ""); return true }
        })
        pinza = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(e: ScaleGestureDetector): Boolean {
                ampliacion = (ampliacion * e.scaleFactor).coerceIn(0.6f, 4.0f)
                ajustarTamano()
                return true
            }
        })
        // Un dedo: toque o arrastre para mover la foto. Dos dedos: pinza para ampliar.
        espejo.setOnTouchListener { _, ev ->
            if (!tocarPermitido) false
            else {
                pinza.onTouchEvent(ev)
                (espejo.parent as? View)?.requestDisallowInterceptTouchEvent(pinza.isInProgress())
                gestos.onTouchEvent(ev)
                true
            }
        }
        cajaTexto = campo("", InputType.TYPE_CLASS_TEXT)
        cajaTexto.hint = "texto para la PC (el codigo que buscas)"

        val botones = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        botones.addView(boton("ENTRAR") { entrar() }, pesoHorizontal(1f))
        botones.addView(boton("BUSCAR") { buscar() }, pesoHorizontal(1f))
        botones.addView(boton("SALIR") { desconectar() }, pesoHorizontal(1f))

        val botonesEspejo = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        botonesEspejo.addView(boton("VER PANTALLA PC") { actualizarEspejo() }, pesoHorizontal(2f))
        botonesEspejo.addView(boton("TAMANO") { ciclarTamano() }, pesoHorizontal(1f))

        val pantalla = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF0D0D0F.toInt())
            setPadding(24, 24, 24, 24)
            addView(titulo())
            addView(linea(sello(), 0xFF7A7A86.toInt()))
            addView(etiqueta("Puente en tu PC (recomendado: tu PC tiene la llave, el celular no)"))
            addView(cajaPuente, anchoCompleto(10))
            addView(etiqueta("Token del puente"))
            addView(cajaToken, anchoCompleto(10))
            addView(etiqueta("ESPEJO: la pantalla del Datacenter en tu PC, foto fresca. Solo necesita puente y token."))
            addView(botonesEspejo, anchoCompleto(6))
            addView(estadoEspejo, anchoCompleto(6))
            addView(autoEspejoCaja, anchoCompleto(6))
            addView(tocarCaja, anchoCompleto(6))
            addView(cajaEspejo(), anchoCompleto(8))
            val teclado = LinearLayout(this@StockActivity).apply { orientation = LinearLayout.HORIZONTAL }
            teclado.addView(cajaTexto, pesoHorizontal(2.4f))
            teclado.addView(boton("ESCRIBIR") { escribirEnPc(false) }, pesoHorizontal(1f))
            teclado.addView(boton("IR") { escribirEnPc(true) }, pesoHorizontal(0.7f))
            addView(teclado, anchoCompleto(6))
            val filaTeclas = LinearLayout(this@StockActivity).apply { orientation = LinearLayout.HORIZONTAL }
            filaTeclas.addView(boton("SHIFT+F9") { teclaEnPc(120, true) }, pesoHorizontal(1f))
            filaTeclas.addView(boton("RETROCESO") { teclaEnPc(8, false) }, pesoHorizontal(1f))
            addView(filaTeclas, anchoCompleto(6))
            val filaRueda = LinearLayout(this@StockActivity).apply { orientation = LinearLayout.HORIZONTAL }
            filaRueda.addView(boton("RUEDA ARRIBA") { ruedaEnCentro(6) }, pesoHorizontal(1f))
            filaRueda.addView(boton("RUEDA ABAJO") { ruedaEnCentro(-6) }, pesoHorizontal(1f))
            addView(filaRueda, anchoCompleto(6))
            addView(linea("toque = clic · doble toque = doble clic · dos dedos = ampliar · un dedo = mover la foto", 0xFF7A7A86.toInt()), anchoCompleto(6))
            addView(linea("---- abajo: solo si vas directo a la base ----", 0xFF4A4A55.toInt()))
            addView(etiqueta("Servidor"))
            addView(cajaHost, anchoCompleto(10))
            addView(filaPuertoBase())
            addView(etiqueta("Usuario (el de operadora si usas puente)"))
            addView(cajaUsuario, anchoCompleto(10))
            addView(etiqueta("Contrasena"))
            addView(cajaClave, anchoCompleto(10))
            addView(botones, anchoCompleto(14))
            addView(estado, anchoCompleto(14))
            addView(etiqueta("Buscar en la tabla"))
            addView(cajaTabla, anchoCompleto(10))
            addView(cajaBusqueda, anchoCompleto(10))
            addView(resultados, anchoCompleto(0))
        }
        setContentView(ScrollView(this).apply {
            addView(pantalla, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        })
    }

    private fun sello(): String {
        val pi = packageManager.getPackageInfo(packageName, 0)
        return "APK ${pi.versionName ?: "?"} build ${pi.versionCode}"
    }

    private fun titulo() = TextView(this).apply {
        text = "JVC STOCK"
        textSize = 22f
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(0xFFC81919.toInt())
        letterSpacing = 0.12f
    }

    private fun etiqueta(t: String) = TextView(this).apply {
        text = t
        textSize = 11f
        setTextColor(0xFF7A7A86.toInt())
    }

    private fun linea(t: String, color: Int) = TextView(this).apply {
        text = t
        textSize = 13f
        setTextColor(color)
    }

    private fun campo(valor: String, tipo: Int) = EditText(this).apply {
        setText(valor)
        inputType = tipo
        setTextColor(0xFFE8E8EA.toInt())
        setHintTextColor(0xFF5A5A66.toInt())
        setBackgroundColor(0xFF16161A.toInt())
        textSize = 15f
        setSingleLine(true)
    }

    private fun boton(nombre: String, accion: () -> Unit) = Button(this).apply {
        text = nombre
        setTextColor(Color.WHITE)
        setBackgroundColor(0xFFC81919.toInt())
        textSize = 12f
        setOnClickListener { accion() }
    }

    private fun pesoHorizontal(f: Float) =
        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, f)

    private fun anchoCompleto(margenAbajo: Int) = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
    ).apply { bottomMargin = margenAbajo }

    private fun filaPuertoBase(): View {
        val l = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val izquierda = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        izquierda.addView(etiqueta("Puerto"))
        izquierda.addView(cajaPuerto, anchoCompleto(0))
        val derecha = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        derecha.addView(etiqueta("Base de datos"))
        derecha.addView(cajaBase, anchoCompleto(0))
        l.addView(izquierda, pesoHorizontal(1f))
        l.addView(derecha, pesoHorizontal(2f))
        return l
    }

    private fun entrar() {
        if (ocupado) return
        guardarPrefs()
        ocupado = true
        val puente = textoDe(cajaPuente)
        if (puente.isNotBlank()) {
            decir("hablando con el puente...", 0xFFFFD479.toInt())
            hilo { entrarPorPuente(puente) }
            return
        }
        decir("conectando...", 0xFFFFD479.toInt())
        val h = textoDe(cajaHost)
        val p = textoDe(cajaPuerto)
        val b = textoDe(cajaBase)
        val u = textoDe(cajaUsuario)
        val c = cajaClave.text.toString()
        enPuente = false
        hilo {
            var ultimo: Throwable? = null
            for (modo in listOf("disable", "prefer")) {
                var nueva: Connection? = null
                try {
                    Class.forName("org.postgresql.Driver")
                    ui.post { decir("intentando ssl=$modo...", 0xFFFFD479.toInt()) }
                    nueva = DriverManager.getConnection(
                        "jdbc:postgresql://$h:$p/$b?connectTimeout=12&socketTimeout=30&sslmode=$modo", u, c)
                    nueva.isReadOnly = true
                    val tablas = listarTablas(nueva, 200)
                    val vieja = conexion
                    conexion = nueva
                    vieja?.close()
                    val sugerida = mejorDe(tablas)
                    ui.post {
                        ocupado = false
                        decir("${tablas.size} tablas (ssl=$modo). " +
                            (sugerida ?: "ninguna parece de productos: escribi una"), 0xFF00FFB7.toInt())
                        if (textoDe(cajaTabla).isBlank()) cajaTabla.setText(sugerida ?: "")
                    }
                    return@hilo
                } catch (e: Throwable) {
                    try { nueva?.close() } catch (ignora: Exception) { }
                    ultimo = e
                }
            }
            val fin = ultimo
            ui.post { ocupado = false; decir("no entro: ${if (fin == null) "?" else mensaje(fin)}", 0xFFFF6B6B.toInt()) }
        }
    }

    private fun entrarPorPuente(direccion: String) {
        val url = direccion.trimEnd('/')
        val t = textoDe(cajaToken)
        val cuerpo = "token=${enc(t)}&usuario=${enc(textoDe(cajaUsuario))}&clave=${enc(cajaClave.text.toString())}"
        try {
            JSONObject(http(url + "/salud", null))
        } catch (e: Throwable) {
            fracaso("el puente no responde: ${mensaje(e)}")
            return
        }
        ui.post { decir("puente vivo, validando tu usuario...", 0xFFFFD479.toInt()) }
        val login = try {
            JSONObject(http(url + "/login", cuerpo))
        } catch (e: Throwable) {
            fracaso("el puente rechazo el login: ${mensaje(e)}")
            return
        }
        if (!login.optBoolean("ok")) {
            fracaso("el puente no deja pasar: " + login.optString("motivo", "?"))
            return
        }
        enPuente = true
        val nombres = ArrayList<String>()
        var aviso = login.optString("aviso", "")
        try {
            val filas = filasDe(JSONObject(http("$url/tablas?token=${enc(t)}", null)))
            for (i in 0 until filas.length()) {
                filas.optJSONObject(i)?.optString("tabla")?.let { if (it.isNotBlank()) nombres.add(it) }
            }
        } catch (e: Throwable) {
            aviso = aviso + " / tablas: " + mensaje(e)
        }
        val sugerida = mejorDe(nombres)
        ui.post {
            ocupado = false
            val extra = if (aviso.isBlank()) "" else "  [$aviso]"
            decir("dentro por puente. ${nombres.size} tablas. " +
                (sugerida ?: "escribi la tabla") + extra, 0xFF00FFB7.toInt())
            if (textoDe(cajaTabla).isBlank()) cajaTabla.setText(sugerida ?: "")
        }
    }

    private fun fracaso(texto: String) {
        ui.post { ocupado = false; decir(texto, 0xFFFF6B6B.toInt()) }
    }

    private fun desconectar() {
        val vieja = conexion
        conexion = null
        enPuente = false
        resultados.removeAllViews()
        decir("sali", 0xFF7A7A86.toInt())
        hilo { try { vieja?.close() } catch (ignora: Exception) { } }
    }

    private fun buscar() {
        if (ocupado) return
        if (conexion == null && !enPuente) { decir("primero ENTRAR", 0xFFFF6B6B.toInt()); return }
        val q = textoDe(cajaBusqueda)
        if (q.length < 2) { decir("escribi algo que buscar", 0xFFFF6B6B.toInt()); return }
        if (enPuente) { buscarPorPuente(q); return }
        decir("buscando...", 0xFFFFD479.toInt())
        ocupado = true
        val con = conexion
        val pedida = textoDe(cajaTabla)
        if (con == null) { ocupado = false; return }
        hilo {
            try {
                val tabla = if (pedida.isNotBlank()) pedida
                            else mejorDe(listarTablas(con, 200)).orEmpty()
                if (tabla.isBlank()) {
                    ui.post { ocupado = false; decir("no hay tabla donde buscar", 0xFFFF6B6B.toInt()) }
                    return@hilo
                }
                val columnas = listarColumnas(con, tabla)
                if (columnas.isEmpty()) {
                    ui.post { ocupado = false; decir("\"$tabla\" no tiene columnas a la vista", 0xFFFF6B6B.toInt()) }
                    return@hilo
                }
                val buscab = columnas.count {
                    it.second.contains(Regex("(?i)(cod|barra|sku|cbu|nombre|descr|detalle|marca|modelo|medida|rubro|familia)"))
                }
                val cuantas = if (buscab == 0) columnas.size else buscab
                val st = con.prepareStatement(consulta(tabla, columnas, cuantas))
                for (i in 0 until cuantas) st.setString(i + 1, "%$q%")
                st.executeQuery().use { rs ->
                    val meta = rs.metaData
                    val n = meta.columnCount
                    val cab = (1..n).map { meta.getColumnLabel(it) ?: "" }
                    val filas = ArrayList<ArrayList<String>>()
                    while (rs.next()) filas.add(ArrayList((1..n).map { rs.getString(it) ?: "" }))
                    ui.post {
                        ocupado = false
                        cajaTabla.setText(tabla)
                        decir("${filas.size} fila(s) en $tabla", 0xFF00FFB7.toInt())
                        pintar(cab, filas, tabla, q)
                    }
                }
            } catch (e: Throwable) {
                ui.post { ocupado = false; decir("rechazada: ${mensaje(e)}", 0xFFFF6B6B.toInt()) }
            }
        }
    }

    private fun buscarPorPuente(q: String) {
        decir("buscando por puente...", 0xFFFFD479.toInt())
        ocupado = true
        val url = textoDe(cajaPuente).trimEnd('/')
        val tabla = textoDe(cajaTabla)
        var direccion = "$url/buscar?token=${enc(textoDe(cajaToken))}&q=${enc(q)}"
        if (tabla.isNotBlank()) direccion = direccion + "&tabla=${enc(tabla)}"
        hilo {
            try {
                val o = JSONObject(http(direccion, null))
                if (!o.optBoolean("ok")) {
                    fracaso("el puente dijo: " + o.optString("motivo", "?"))
                    return@hilo
                }
                val filas = filasDe(o)
                val cab = ArrayList<String>()
                val arreglo = o.optJSONArray("orden")
                if (arreglo != null) for (i in 0 until arreglo.length()) cab.add(arreglo.optString(i))
                if (cab.isEmpty()) filas.optJSONObject(0)?.let { primera -> for (k in primera.keys()) cab.add(k) }
                val datos = ArrayList<ArrayList<String>>()
                for (i in 0 until filas.length()) {
                    val fila = filas.optJSONObject(i) ?: continue
                    datos.add(ArrayList(cab.map { fila.optString(it, "") }))
                }
                ui.post {
                    ocupado = false
                    decir("${filas.length()} fila(s) para \"$q\"", 0xFF00FFB7.toInt())
                    pintar(cab, datos, tabla, q)
                }
            } catch (e: Throwable) {
                fracaso("puente: ${mensaje(e)}")
            }
        }
    }

    private fun actualizarEspejo() {
        if (ocupado) return
        val puente = textoDe(cajaPuente)
        if (puente.isBlank()) { decirEspejo("arriba falta la direccion del puente", 0xFFFF6B6B.toInt()); return }
        guardarPrefs()
        ocupado = true
        decirEspejo("pidiendole la pantalla a tu PC...", 0xFFFFD479.toInt())
        val direccion = puente.trimEnd('/') + "/pantalla?ancho=1400"
        hilo {
            try {
                val datos = bytesDe(direccion)
                val mapa = BitmapFactory.decodeByteArray(datos, 0, datos.size)
                if (mapa == null) { fracasoEspejo("el puente mando algo que no es imagen") }
                else ui.post {
                    ocupado = false
                    anchoFoto = mapa.width
                    altoFoto = mapa.height
                    espejo.setImageBitmap(mapa)
                    ajustarTamano()
                    decirEspejo("actualizada " + horaActual(), 0xFF00FFB7.toInt())
                }
            } catch (e: Throwable) {
                fracasoEspejo(mensaje(e))
            }
        }
    }

    private fun bytesDe(direccion: String): ByteArray {
        val c = URL(direccion).openConnection() as HttpURLConnection
        c.connectTimeout = 8000
        c.readTimeout = 30000
        c.setRequestProperty("Accept", "image/png")
        val t = textoDe(cajaToken)
        if (t.isNotBlank()) c.setRequestProperty("x-token", t)
        try {
            val codigo = c.responseCode
            val datos = (if (codigo in 200..299) c.inputStream else c.errorStream)?.readBytes() ?: ByteArray(0)
            if (codigo !in 200..299) {
                val motivo = try { JSONObject(String(datos, Charsets.UTF_8)).optString("motivo", "http $codigo") }
                             catch (ignora: Exception) { "http $codigo" }
                throw RuntimeException(motivo)
            }
            return datos
        } finally {
            c.disconnect()
        }
    }

    // La foto se mueve en las dos direcciones cuando esta mas grande que la pantalla.
    private fun cajaEspejo(): View {
        val alto = (resources.displayMetrics.heightPixels * 0.45f).toInt()
        cajaV = ScrollView(this)
        cajaV.addView(espejo, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT))
        cajaH = HorizontalScrollView(this)
        cajaH.addView(cajaV, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, alto))
        return cajaH
    }

    private fun ajustarTamano() {
        val anchoPantalla = resources.displayMetrics.widthPixels
        val lp = espejo.layoutParams as? FrameLayout.LayoutParams
            ?: FrameLayout.LayoutParams(anchoPantalla, FrameLayout.LayoutParams.WRAP_CONTENT)
        lp.width = (anchoPantalla * ampliacion).toInt()
        lp.height = FrameLayout.LayoutParams.WRAP_CONTENT
        espejo.layoutParams = lp
    }

    // La rueda de la PC se manda al centro de lo que ella esta mirando en la foto.
    private fun ruedaEnCentro(giros: Int) {
        if (anchoFoto == 0 || espejo.width == 0 || espejo.height == 0) {
            decirEspejo("primero pedi la pantalla (VER PANTALLA PC)", 0xFFFF6B6B.toInt()); return
        }
        val vx = (cajaH.scrollX + cajaH.width / 2f).coerceAtMost((espejo.width - 1).toFloat())
        val vy = (cajaV.scrollY + cajaV.height / 2f).coerceAtMost((espejo.height - 1).toFloat())
        enviar("rueda", vx, vy, giros.toString())
    }

    private fun ciclarTamano() {
        ampliacion = when {
            ampliacion < 0.8f -> 1.0f
            ampliacion < 1.2f -> 1.6f
            ampliacion < 1.8f -> 2.4f
            ampliacion < 3.0f -> 3.6f
            else -> 0.6f
        }
        ajustarTamano()
        decirEspejo("tamano x" + String.format("%.1f", ampliacion), 0xFF7A7A86.toInt())
    }

    private fun tocarEn(x: Float, y: Float) = enviar("clic", x, y, "")

    // El dedo esta sobre la foto; el puente necesita pixeles de la ventana real.
    private fun enviar(accion: String, x: Float, y: Float, extra: String) {
        val puente = textoDe(cajaPuente)
        if (puente.isBlank()) { decirEspejo("falta la direccion del puente", 0xFFFF6B6B.toInt()); return }
        if (anchoFoto == 0 || espejo.width == 0 || espejo.height == 0) {
            decirEspejo("primero pedi la pantalla (VER PANTALLA PC)", 0xFFFF6B6B.toInt()); return
        }
        val fx = (x / espejo.width * anchoFoto).toInt().coerceIn(0, anchoFoto - 1)
        val fy = (y / espejo.height * altoFoto).toInt().coerceIn(0, altoFoto - 1)
        val cola = if (accion == "rueda") "&giros=" + extra else ""
        val url = puente.trimEnd('/') + "/tocar?accion=" + enc(accion) + "&x=" + fx + "&y=" + fy + cola
        decirEspejo("mandando " + accion + " a la PC...", 0xFFFFD479.toInt())
        hilo {
            try {
                http(url, null)
                ui.post {
                    decirEspejo(accion + " ok " + horaActual(), 0xFF00FFB7.toInt())
                    ui.postDelayed({ actualizarEspejo() }, 500)
                }
            } catch (e: Throwable) {
                fracasoEspejo(mensaje(e))
            }
        }
    }

    private fun escribirEnPc(conEnter: Boolean) {
        val puente = textoDe(cajaPuente)
        val t = cajaTexto.text.toString()
        if (puente.isBlank()) { decirEspejo("falta la direccion del puente", 0xFFFF6B6B.toInt()); return }
        if (t.isBlank()) { decirEspejo("escribe algo en el campo de texto", 0xFFFF6B6B.toInt()); return }
        if (ocupado) return
        ocupado = true
        decirEspejo("escribiendo en la PC...", 0xFFFFD479.toInt())
        hilo {
            try {
                val base = puente.trimEnd('/') + "/tocar?"
                http(base + "accion=texto&texto=" + enc(t), null)
                if (conEnter) Thread.sleep(250)
                if (conEnter) http(base + "accion=tecla&vk=13", null)
                ui.post {
                    ocupado = false
                    decirEspejo("escrito en la PC " + horaActual(), 0xFF00FFB7.toInt())
                    actualizarEspejo()
                }
            } catch (e: Throwable) {
                ocupado = false
                fracasoEspejo(mensaje(e))
            }
        }
    }

    private fun teclaEnPc(vk: Int, conShift: Boolean) {
        val puente = textoDe(cajaPuente)
        if (puente.isBlank()) { decirEspejo("falta la direccion del puente", 0xFFFF6B6B.toInt()); return }
        if (ocupado) return
        ocupado = true
        decirEspejo("mandando tecla a la PC...", 0xFFFFD479.toInt())
        hilo {
            try {
                val cola = if (conShift) "&shift=1" else ""
                val url = puente.trimEnd('/') + "/tocar?accion=tecla&vk=" + vk + cola
                http(url, null)
                ui.post {
                    ocupado = false
                    decirEspejo("tecla ok " + horaActual(), 0xFF00FFB7.toInt())
                    actualizarEspejo()
                }
            } catch (e: Throwable) {
                ocupado = false
                fracasoEspejo(mensaje(e))
            }
        }
    }

    private fun prenderAuto(marcado: Boolean) {
        autoEspejo = marcado
        ui.removeCallbacks(tick)
        if (marcado) ui.postDelayed(tick, 800)
    }

    private fun fracasoEspejo(texto: String) {
        ui.post { ocupado = false; decirEspejo("espejo: $texto", 0xFFFF6B6B.toInt()) }
    }

    private fun decirEspejo(texto: String, color: Int) {
        Log.i("JVCSTOCK", texto)
        estadoEspejo.text = texto
        estadoEspejo.setTextColor(color)
    }

    private fun horaActual(): String =
        android.text.format.DateFormat.format("HH:mm:ss", java.util.Date()).toString()

    private fun http(direccion: String, cuerpo: String?): String {
        val c = URL(direccion).openConnection() as HttpURLConnection
        c.connectTimeout = 8000
        c.readTimeout = 25000
        c.setRequestProperty("Accept", "application/json")
        val tk = textoDe(cajaToken)
        if (tk.isNotBlank()) c.setRequestProperty("x-token", tk)
        try {
            if (cuerpo != null) {
                c.requestMethod = "POST"
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                c.outputStream.use { it.write(cuerpo.toByteArray(Charsets.UTF_8)) }
            } else {
                c.requestMethod = "GET"
            }
            val codigo = c.responseCode
            val texto = (if (codigo in 200..299) c.inputStream else c.errorStream)
                ?.readBytes()?.toString(Charsets.UTF_8) ?: ""
            if (codigo !in 200..299) {
                val motivo = try { JSONObject(texto).optString("motivo", "http $codigo") }
                             catch (ignora: Exception) { "http $codigo" }
                throw RuntimeException(motivo)
            }
            return texto
        } finally {
            c.disconnect()
        }
    }

    private fun enc(v: String) = URLEncoder.encode(v, "UTF-8")

    private fun filasDe(o: JSONObject): JSONArray {
        return when (val f = o.opt("filas")) {
            is JSONArray -> f
            is JSONObject -> JSONArray().put(f)
            else -> JSONArray()
        }
    }

    private fun consulta(tabla: String, columnas: List<Pair<String, String>>, cuantas: Int): String {
        val seguras = columnas.map { it.first.replace("\"", "") }
        val where = seguras.take(cuantas).joinToString(" OR ") { "coalesce(\"$it\"::text,'') ILIKE ?" }
        val lista = seguras.joinToString(", ") { "\"$it\"" }
        return "SELECT $lista FROM \"${tabla.replace("\"", "")}\" WHERE $where LIMIT 60"
    }

    private fun listarTablas(con: Connection, maximo: Int): List<String> {
        val out = ArrayList<String>()
        con.createStatement().use { st ->
            st.executeQuery(
                "SELECT table_name FROM information_schema.tables " +
                "WHERE table_schema NOT IN ('pg_catalog','information_schema') ORDER BY 1"
            ).use { rs -> while (rs.next() && out.size < maximo) out.add(rs.getString(1)) }
        }
        return out
    }

    private fun listarColumnas(con: Connection, tabla: String): List<Pair<String, String>> {
        val out = ArrayList<Pair<String, String>>()
        val st = con.prepareStatement(
            "SELECT column_name, data_type FROM information_schema.columns " +
            "WHERE table_name = ? AND table_schema NOT IN ('pg_catalog','information_schema') " +
            "ORDER BY ordinal_position"
        )
        st.setString(1, tabla)
        st.executeQuery().use { rs ->
            while (rs.next()) out.add(Pair(rs.getString(1) ?: "", rs.getString(2) ?: ""))
        }
        return out
    }

    private fun mejorDe(tablas: List<String>): String? =
        tablas.firstOrNull {
            it.matches(Regex("(?i)^(producto|productos|articulo|articulos|insumo|insumos|item|items|material|repuesto|existencia)"))
        } ?: tablas.firstOrNull { it.contains(Regex("(?i)(producto|articulo|insumo|material)")) }

    private fun pintar(cab: List<String>, filas: ArrayList<ArrayList<String>>, tabla: String, consulta: String) {
        resultados.removeAllViews()
        if (filas.isEmpty()) {
            resultados.addView(linea("nada para \"$consulta\" en $tabla", 0xFFFF6B6B.toInt()))
            return
        }
        for ((indice, fila) in filas.withIndex()) {
            val bloque = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(12, 12, 12, 12)
                setBackgroundColor(if (indice % 2 == 0) 0xFF16161A.toInt() else 0xFF1D1D24.toInt())
            }
            for (i in cab.indices) {
                val valor = fila.getOrElse(i) { "" }
                if (valor.isBlank()) continue
                bloque.addView(linea("${cab[i]}:  $valor", 0xFFE8E8EA.toInt()))
            }
            resultados.addView(bloque, anchoCompleto(3))
        }
    }

    private fun decir(texto: String, color: Int) {
        Log.i("JVCSTOCK", texto)
        estado.text = texto
        estado.setTextColor(color)
    }

    private fun textoDe(campo: EditText) = campo.text.toString().trim()

    private fun pref(prefs: SharedPreferences, clave: String, porDefecto: String): String =
        prefs.getString(clave, porDefecto) ?: porDefecto

    private fun mensaje(e: Throwable): String {
        val causa = e.cause?.message
        val texto = listOfNotNull(e.javaClass.simpleName, e.message, if (causa != null) "causa: $causa" else null)
            .joinToString(" / ")
        return texto.take(300)
    }

    private fun guardarPrefs() {
        getSharedPreferences("stock", MODE_PRIVATE).edit()
            .putString("puente", textoDe(cajaPuente))
            .putString("token", textoDe(cajaToken))
            .putString("host", textoDe(cajaHost))
            .putString("puerto", textoDe(cajaPuerto))
            .putString("base", textoDe(cajaBase))
            .putString("usuario", textoDe(cajaUsuario))
            .putString("clave", cajaClave.text.toString())
            .apply()
    }

    private fun hilo(bloque: () -> Unit) { Thread(bloque).start() }

    override fun onDestroy() {
        super.onDestroy()
        autoEspejo = false
        ui.removeCallbacks(tick)
        val vieja = conexion
        conexion = null
        hilo { try { vieja?.close() } catch (ignora: Exception) { } }
    }
}
