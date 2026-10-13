package org.jvc.progame

import android.animation.ObjectAnimator
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {

    private var ido = false
    private val salto = Handler(Looper.getMainLooper())
    private lateinit var barra: View

    override fun onCreate(savedInstanceState: Bundle?) {
        supportActionBar?.hide()
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK

        val emblema = ImageView(this).apply {
            setImageResource(R.drawable.jvc_emblema)
            adjustViewBounds = true
            alpha = 0f
        }

        val firma = ImageView(this).apply {
            setImageResource(R.drawable.jvc_wordmark)
            adjustViewBounds = true
            alpha = 0f
        }

        barra = View(this).apply {
            background = GradientDrawable().apply { setColor(0xFFC81919.toInt()) }
            pivotX = 0f
            scaleX = 0f
        }
        val pista = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                setColor(0xFF22222A.toInt())
                cornerRadius = 6f
            }
            addView(barra, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ))
        }

        val pie = TextView(this).apply {
            text = "CONTROLES DE RENDIMIENTO · SIN ROOT · SHIZUKU"
            textSize = 12f
            setTextColor(0xFF7A7A86.toInt())
            gravity = Gravity.CENTER
            letterSpacing = 0.18f
            alpha = 0f
        }

        val cuerpo = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(40, 40, 40, 40)
            addView(emblema, LinearLayout.LayoutParams(300, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER
            })
            addView(firma, LinearLayout.LayoutParams(340, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER
                topMargin = 26
            })
            addView(pista, LinearLayout.LayoutParams(340, 8, Gravity.CENTER).apply { topMargin = 34 })
            addView(pie, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            ).apply { topMargin = 18 })
        }

        val raiz = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.BLACK)
            setOnClickListener { entrar() }
            addView(cuerpo, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ))
        }
        setContentView(raiz)

        aparece(emblema, 120)
        aparece(firma, 520)
        aparece(pista, 700)
        aparece(pie, 900)
        barra.postDelayed({
            ObjectAnimator.ofFloat(barra, "scaleX", 0f, 1f).apply {
                duration = 1900
                interpolator = AccelerateDecelerateInterpolator()
                start()
            }
        }, 700)
        salto.postDelayed({ entrar() }, 2600)
    }

    private fun aparece(v: View, retraso: Long) {
        v.postDelayed({
            ObjectAnimator.ofFloat(v, "alpha", 0f, 1f).apply { duration = 520 }.start()
        }, retraso)
    }

    private fun entrar() {
        if (ido) return
        ido = true
        salto.removeCallbacksAndMessages(null)
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (!ido) salto.removeCallbacksAndMessages(null)
    }
}
