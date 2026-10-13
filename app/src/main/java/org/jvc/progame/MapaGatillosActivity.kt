package org.jvc.progame

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.hypot

/**
 * Plano para colocar los puntos con el dedo. No inyecta nada: solo guarda la posicion exacta,
 * que es la que luego se escribe en X-Arena, y ahi la traduce el sistema.
 */
class MapaGatillosActivity : AppCompatActivity() {

    private lateinit var lienzo: Lienzo
    private lateinit var info: TextView
    private lateinit var nombre: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dm = resources.displayMetrics

        lienzo = Lienzo(this).apply { anchoPx = dm.widthPixels; altoPx = dm.heightPixels }
        info = TextView(this).apply { textSize = 13f; setTextColor(0xFF00FFB7.toInt()) }
        nombre = EditText(this).apply {
            hint = "nombre del perfil, ej. ff-clasico"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 15f
        }

        val botones = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }

        fun boton(texto: String, accion: () -> Unit) = botones.addView(
            Button(this).apply {
                text = texto
                textSize = 11f
                setOnClickListener { accion() }
            },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )

        boton("Punto") { lienzo.anadir(); refrescar() }
        boton("Ultimo fuera") { lienzo.quitarUltimo(); refrescar() }
        boton("Limpiar") { lienzo.vaciar(); refrescar() }
        boton("Guardar") { guardar() }
        boton("Cargar") { cargar() }

        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(12, 12, 12, 12)
            addView(info)
            addView(nombre)
            addView(botones)
            addView(
                lienzo,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
            )
            setBackgroundColor(0xFF0D0D0F.toInt())
        })
        refrescar()
    }

    private fun prefs() = getSharedPreferences("mapa", MODE_PRIVATE)

    private fun textoPuntos(): String {
        val l = lienzo.puntos()
        if (l.isEmpty()) return "sin puntos: pulsa Punto y arrastra el circulo"
        return l.mapIndexed { i, p ->
            "P${i + 1}: ${p.first}x${p.second} px (${pct(p.first, lienzo.anchoPx)}% ancho, " +
                "${pct(p.second, lienzo.altoPx)}% alto)"
        }.joinToString("\n")
    }

    private fun pct(v: Int, total: Int): Int =
        if (total == 0) 0 else (v * 100f / total).toInt()

    private fun refrescar() {
        info.text = "${lienzo.anchoPx}x${lienzo.altoPx} · " + textoPuntos()
    }

    private fun guardar() {
        val n = nombre.text.toString().trim().ifBlank { "sin-nombre" }
        val l = lienzo.puntos()
        if (l.isEmpty()) {
            refrescar()
            return
        }
        val codificado = l.joinToString(";") { "${it.first},${it.second}" }
        prefs().edit()
            .putString("perfil_$n", codificado)
            .putString("ultimo", n)
            .putString("tamano", "${lienzo.anchoPx}x${lienzo.altoPx}")
            .apply()
        val guardados = prefs().all.keys.filter { it.startsWith("perfil_") }
            .joinToString(", ") { it.removePrefix("perfil_") }
        info.text = "guardado '$n' (${l.size} puntos)\nperfiles: $guardados"
    }

    private fun cargar() {
        val n = nombre.text.toString().trim().ifBlank {
            prefs().getString("ultimo", "")?.ifBlank { null } ?: "sin-nombre"
        }
        val crudo = prefs().getString("perfil_$n", null)
        if (crudo == null) {
            info.text = "no existe el perfil '$n'"
            return
        }
        lienzo.cargar(crudo)
        refrescar()
    }

    private class Lienzo(ctx: Context) : View(ctx) {

        private val pintura = Paint(Paint.ANTI_ALIAS_FLAG)
        private val puntos = ArrayList<Pair<Int, Int>>()
        private var area: RectF? = null

        var anchoPx = 1080
        var altoPx = 2400

        private val radio = 26f * resources.displayMetrics.density / 2f

        fun puntos(): List<Pair<Int, Int>> = puntos.toList()

        fun anadir() {
            if (puntos.size >= 6) return
            puntos.add(anchoPx / 2 to (altoPx * 0.12f).toInt())
            invalidate()
        }

        fun quitarUltimo() {
            if (puntos.isNotEmpty()) puntos.removeAt(puntos.size - 1)
            invalidate()
        }

        fun vaciar() {
            puntos.clear()
            invalidate()
        }

        fun cargar(codificado: String) {
            puntos.clear()
            codificado.split(";").forEach { par ->
                val v = par.split(",")
                if (v.size == 2) {
                    val x = v[0].trim().toIntOrNull()
                    val y = v[1].trim().toIntOrNull()
                    if (x != null && y != null) puntos.add(x to y)
                }
            }
            invalidate()
        }

        override fun onDraw(c: Canvas) {
            val margen = 10f * resources.displayMetrics.density
            val disponibleW = (width - margen * 2).coerceAtLeast(1f)
            val disponibleH = (height - margen * 2).coerceAtLeast(1f)
            val asp = if (altoPx == 0) 0.5f else anchoPx.toFloat() / altoPx
            var w = disponibleW
            var h = w / asp
            if (h > disponibleH) {
                h = disponibleH
                w = h * asp
            }
            val izquierda = margen + (disponibleW - w) / 2
            val arriba = margen + (disponibleH - h) / 2
            val r = RectF(izquierda, arriba, izquierda + w, arriba + h)
            area = r

            pintura.style = Paint.Style.FILL
            pintura.color = 0xFF101018.toInt()
            c.drawRect(r, pintura)

            pintura.color = 0xFF26262E.toInt()
            pintura.strokeWidth = 1f
            for (i in 1..9) {
                val x = r.left + r.width() * i / 10f
                val y = r.top + r.height() * i / 10f
                c.drawLine(x, r.top, x, r.bottom, pintura)
                c.drawLine(r.left, y, r.right, y, pintura)
            }

            pintura.style = Paint.Style.STROKE
            pintura.color = 0xFF3A3A46.toInt()
            pintura.strokeWidth = 2f
            c.drawRect(r, pintura)

            puntos.forEachIndexed { i, p ->
                val x = r.left + r.width() * p.first / anchoPx
                val y = r.top + r.height() * p.second / altoPx
                pintura.style = Paint.Style.FILL
                pintura.color = 0xFFC81919.toInt()
                c.drawCircle(x, y, radio, pintura)
                pintura.color = 0xFFFFFFFF.toInt()
                pintura.textSize = 24f * resources.displayMetrics.density / 2f
                c.drawText("${i + 1}", x - radio / 2f, y + radio / 3f, pintura)
            }
        }

        override fun onTouchEvent(ev: MotionEvent): Boolean {
            val r = area ?: return false
            if (ev.action == MotionEvent.ACTION_DOWN || ev.action == MotionEvent.ACTION_MOVE) {
                val i = masCercano(ev.x, ev.y)
                if (i >= 0) {
                    val nx = ((ev.x - r.left) / r.width() * anchoPx).toInt().coerceIn(0, anchoPx)
                    val ny = ((ev.y - r.top) / r.height() * altoPx).toInt().coerceIn(0, altoPx)
                    puntos[i] = nx to ny
                    invalidate()
                }
            }
            return true
        }

        private fun masCercano(x: Float, y: Float): Int {
            val r = area ?: return -1
            val tolerancia = 40f * resources.displayMetrics.density
            var mejor = -1
            var mejorD = Float.MAX_VALUE
            puntos.forEachIndexed { i, p ->
                val px = r.left + r.width() * p.first / anchoPx
                val py = r.top + r.height() * p.second / altoPx
                val d = hypot((x - px).toDouble(), (y - py).toDouble()).toFloat()
                if (d < tolerancia && d < mejorD) {
                    mejorD = d
                    mejor = i
                }
            }
            return mejor
        }
    }
}
