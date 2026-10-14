package org.jvc.stock

import android.app.Activity
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.sql.Connection
import java.sql.DriverManager

class StockActivity : Activity() {

    private val ui = Handler(Looper.getMainLooper())
    private var conexion: Connection? = null
    private var ocupado = false

    private lateinit var cajaHost: EditText
    private lateinit var cajaPuerto: EditText
    private lateinit var cajaBase: EditText
    private lateinit var cajaUsuario: EditText
    private lateinit var cajaClave: EditText
    private lateinit var cajaBusqueda: EditText
    private lateinit var cajaTabla: EditText
    private lateinit var estado: TextView
    private lateinit var resultados: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setStatusBarColor(Color.BLACK)
        val prefs = getSharedPreferences("stock", MODE_PRIVATE)

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
        resultados = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        val botones = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        botones.addView(boton("ENTRAR") { entrar() }, pesoHorizontal(1f))
        botones.addView(boton("BUSCAR") { buscar() }, pesoHorizontal(1f))
        botones.addView(boton("SALIR") { desconectar() }, pesoHorizontal(1f))

        val pantalla = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF0D0D0F.toInt())
            setPadding(24, 24, 24, 24)
            addView(titulo())
            addView(linea(sello(), 0xFF7A7A86.toInt()))
            addView(etiqueta("Servidor"))
            addView(cajaHost, anchoCompleto(10))
            addView(filaPuertoBase())
            addView(etiqueta("Usuario de la base"))
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
        decir("conectando...", 0xFFFFD479.toInt())
        ocupado = true
        val h = textoDe(cajaHost)
        val p = textoDe(cajaPuerto)
        val b = textoDe(cajaBase)
        val u = textoDe(cajaUsuario)
        val c = cajaClave.text.toString()
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

    private fun desconectar() {
        val vieja = conexion
        conexion = null
        resultados.removeAllViews()
        decir("sali de la base", 0xFF7A7A86.toInt())
        hilo { try { vieja?.close() } catch (ignora: Exception) { } }
    }

    private fun buscar() {
        val con = conexion
        if (con == null) { decir("primero ENTRAR", 0xFFFF6B6B.toInt()); return }
        if (ocupado) return
        val q = textoDe(cajaBusqueda)
        if (q.length < 2) { decir("escribi algo que buscar", 0xFFFF6B6B.toInt()); return }
        decir("buscando...", 0xFFFFD479.toInt())
        ocupado = true
        val pedida = textoDe(cajaTabla)
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
        val vieja = conexion
        conexion = null
        hilo { try { vieja?.close() } catch (ignora: Exception) { } }
    }
}
