package org.jvc.progame

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View

data class MiraConfig(
    val activa: Boolean,
    val estilo: Int,
    val color: Int,
    val tamano: Int,
    val grosor: Int,
    val desfaseY: Int,
)

object MiraPrefs {

    private const val ARCHIVO = "mira"

    fun leer(ctx: Context): MiraConfig {
        val p = ctx.getSharedPreferences(ARCHIVO, Context.MODE_PRIVATE)
        return MiraConfig(
            activa = p.getBoolean("activa", false),
            estilo = p.getInt("estilo", 1),
            color = p.getInt("color", COLOR_VERDE),
            tamano = p.getInt("tamano", 60),
            grosor = p.getInt("grosor", 4),
            desfaseY = p.getInt("desfase", 0),
        )
    }

    fun escribir(ctx: Context, config: MiraConfig) {
        ctx.getSharedPreferences(ARCHIVO, Context.MODE_PRIVATE).edit()
            .putBoolean("activa", config.activa)
            .putInt("estilo", config.estilo)
            .putInt("color", config.color)
            .putInt("tamano", config.tamano)
            .putInt("grosor", config.grosor)
            .putInt("desfase", config.desfaseY)
            .apply()
    }

    const val COLOR_VERDE = 0xFF00FF00.toInt()
    const val COLOR_ROJO = 0xFFFF3B30.toInt()
    const val COLOR_BLANCO = 0xFFFFFFFF.toInt()
    const val COLOR_AMARILLO = 0xFFFFFF00.toInt()
    const val COLOR_CIAN = 0xFF00E5FF.toInt()
}

/**
 * Mira dibujada en su propia capa: no toca el juego ni intercepta un solo toque, el dedo
 * pasa a traves porque la ventana se crea sin foco y sin tacto.
 */
class MiraView(ctx: Context) : View(ctx) {

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private var cfg = MiraPrefs.leer(ctx)

    fun aplicar(nuevo: MiraConfig) {
        if (nuevo == cfg) return
        cfg = nuevo
        invalidate()
    }

    override fun onDraw(c: Canvas) {
        val cx = width / 2f
        val cy = height / 2f + cfg.desfaseY
        val brazo = cfg.tamano.toFloat()
        val hueco = brazo / 3f

        p.color = cfg.color
        p.strokeWidth = cfg.grosor.toFloat()
        p.style = Paint.Style.STROKE

        when (cfg.estilo) {
            2 -> {
                c.drawLine(cx - brazo, cy, cx - hueco, cy, p)
                c.drawLine(cx + hueco, cy, cx + brazo, cy, p)
                c.drawLine(cx, cy - brazo, cx, cy - hueco, p)
                c.drawLine(cx, cy + hueco, cx, cy + brazo, p)
            }
            3 -> c.drawCircle(cx, cy, brazo, p)
            4 -> {
                p.style = Paint.Style.FILL
                c.drawCircle(cx, cy, cfg.grosor.toFloat() * 1.6f, p)
            }
            5 -> {
                c.drawCircle(cx, cy, brazo, p)
                c.drawLine(cx - brazo, cy, cx + brazo, cy, p)
                c.drawLine(cx, cy - brazo, cx, cy + brazo, p)
            }
            else -> {
                c.drawLine(cx - brazo, cy, cx + brazo, cy, p)
                c.drawLine(cx, cy - brazo, cx, cy + brazo, p)
            }
        }
    }
}
