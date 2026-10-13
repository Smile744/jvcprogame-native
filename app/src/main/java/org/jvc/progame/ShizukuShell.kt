package org.jvc.progame

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.Looper
import rikka.shizuku.Shizuku
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

data class ShellResult(val code: Int, val out: String, val err: String) {
    val ok get() = code == 0
    fun resumen() = if (ok) "OK" else (err.trim().ifEmpty { "salida $code" })
}

object ShizukuShell {

    private const val SEP = "\u0001"

    @Volatile private var servicio: IJvcUserService? = null
    @Volatile private var componente: ComponentName? = null
    @Volatile private var encurso: CountDownLatch? = null
    @Volatile private var ultimoError: String? = null

    private val conexion = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            servicio = binder?.let { IJvcUserService.Stub.asInterface(it) }
            val espera = encurso
            encurso = null
            espera?.countDown()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            servicio = null
        }
    }

    fun binderVivo() = try { Shizuku.pingBinder() } catch (e: Exception) { false }

    fun permisoConcedido() = try {
        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (e: Exception) {
        false
    }

    fun listo() = binderVivo() && permisoConcedido()

    /** Lanza el servicio shell por adelantado, siempre fuera del hilo principal. */
    fun iniciar(ctx: Context) {
        componente = ComponentName(ctx.packageName, JvcUserService::class.java.name)
        if (listo() && servicio == null) Thread { enlazar() }.start()
    }

    /** Un boton que dice en el registro por que no levanto el servicio. */
    fun pruebaServicio(): String {
        if (!binderVivo()) return "Shizuku apagado: abre la app Shizuku y activalo"
        if (!permisoConcedido()) return "Falta el permiso: pulsa Conceder permiso a Shizuku"
        ultimoError = null
        val s = servicio() ?: return "Bind fallido: ${ultimoError ?: "sin motivo"} · Shizuku ${versionTexto()}"
        val r = try {
            s.exec("id")
        } catch (e: Exception) {
            return "Servicio conectado pero exec rechaza: ${e.javaClass.simpleName}: ${e.message}"
        }
        return "Servicio OK · Shizuku ${versionTexto()} · ${r.split(SEP).getOrElse(1) { r }}"
    }

    private fun versionTexto() = try { "v" + Shizuku.getVersion() } catch (e: Exception) { "?" }

    fun ejecutar(cmd: String): ShellResult {
        val s = servicio() ?: return ShellResult(
            -1, "",
            if (listo()) "No arranco el servicio shell: ${ultimoError ?: "sin motivo aun, pulsa Probar servicio de comandos"}"
            else "Shizuku no esta activo o falta el permiso"
        )
        return try {
            val partes = s.exec(cmd).split(SEP)
            ShellResult(
                partes.firstOrNull()?.toIntOrNull() ?: -2,
                partes.getOrElse(1) { "" },
                partes.getOrElse(2) { "" }
            )
        } catch (e: Exception) {
            servicio = null
            ShellResult(-2, "", "${e.javaClass.simpleName}: ${e.message}")
        }
    }

    private fun servicio(): IJvcUserService? {
        val actual = servicio
        if (actual != null && actual.asBinder().pingBinder()) return actual
        if (!listo()) return null
        servicio = null
        return enlazar()
    }

    private fun enlazar(): IJvcUserService? {
        val c = componente ?: run {
            ultimoError = "la app no llamo a iniciar()"
            return null
        }
        val espera: CountDownLatch
        val debeEnlazar: Boolean
        synchronized(this) {
            val otro = encurso
            if (otro != null) {
                espera = otro
                debeEnlazar = false
            } else {
                espera = CountDownLatch(1)
                encurso = espera
                debeEnlazar = true
            }
        }
        if (debeEnlazar) {
            try {
                Shizuku.bindUserService(
                    Shizuku.UserServiceArgs(c)
                        .daemon(false)
                        .processNameSuffix("shell")
                        .debuggable(false)
                        .version(2),
                    conexion
                )
            } catch (e: Exception) {
                ultimoError = "bindUserService lanzo ${e.javaClass.simpleName}: ${e.message}"
                synchronized(this) { if (encurso === espera) encurso = null }
                return null
            }
        }
        // El callback de conexion se entrega en el hilo principal: bloquearlo seria deadlock.
        if (Looper.myLooper() == Looper.getMainLooper()) return null
        if (!espera.await(20, TimeUnit.SECONDS)) {
            ultimoError = "Shizuku no devolvio el servicio en 20 s"
            synchronized(this) { if (encurso === espera) encurso = null }
            // Si esperabamos el precalentamiento, lanzamos un bind propio antes de rendirnos.
            if (!debeEnlazar) return enlazar()
        }
        return servicio
    }
}
