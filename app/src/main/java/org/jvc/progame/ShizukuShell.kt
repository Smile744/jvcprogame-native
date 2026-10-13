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
    @Volatile private var sello: String = "?"

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

    /** Guarda el numero de build en el texto de los errores y precalenta el servicio si hace falta. */
    fun iniciar(ctx: Context) {
        componente = ComponentName(ctx.packageName, JvcUserService::class.java.name)
        sello = try {
            ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "?"
        } catch (e: Exception) {
            "?"
        }
        if (canal == null && listo() && servicio == null) Thread { enlazar() }.start()
    }

    /** Un boton que dice en el registro por que no levanto el servicio. */
    fun pruebaServicio(): String {
        if (!binderVivo()) return "Shizuku apagado: abre la app Shizuku y activalo"
        if (!permisoConcedido()) return "Falta el permiso: pulsa Conceder permiso a Shizuku"
        ultimoError = null
        val nombre = canal?.first ?: "ninguno"
        val remoto = porProcesoRemoto("id")
        if (remoto != null && remoto.ok) {
            return "Canal $nombre OK · Shizuku ${versionTexto()} · ${remoto.out.trim()}"
        }
        val motivoRemoto = remoto?.let { "$nombre: ${it.err}" } ?: "no hay canal directo"
        val s = servicio() ?: return "[$sello] ningun canal vale · $motivoRemoto · bind: ${ultimoError ?: "sin motivo"} · Shizuku ${versionTexto()}"
        val r = try {
            s.exec("id")
        } catch (e: Exception) {
            return "Servicio conectado pero exec rechaza: ${e.javaClass.simpleName}: ${e.message}"
        }
        return "Servicio de usuario OK · $motivoRemoto · Shizuku ${versionTexto()} · ${r.split(SEP).getOrElse(1) { r }}"
    }

    private fun versionTexto() = try { "v" + Shizuku.getVersion() } catch (e: Exception) { "?" }

    /**
     * Shizuku puede lanzar procesos hijo de su propio daemon shell sin crear un UserService.
     * El metodo esta en la libreria pero es privado, y su firma ha cambiado entre versiones,
     * por eso se busca por nombre y se construyen los argumentos segun los parametros reales.
     */
    private fun canalPorMetodo(): ((String) -> Process)? {
        val m = try {
            Shizuku::class.java.declaredMethods
                .firstOrNull { it.name == "newProcess" && it.parameterTypes.isNotEmpty() }
                ?.apply { isAccessible = true }
        } catch (e: Exception) {
            null
        } ?: return null
        val idx = m.parameterTypes.indexOfFirst { it.isArray && it.componentType == String::class.java }
        if (idx < 0) return null
        return { cmd: String ->
            val args = arrayOfNulls<Any>(m.parameterTypes.size)
            args[idx] = arrayOf("sh", "-c", cmd)
            m.invoke(null, *args) as Process
        }
    }

    /** Segunda via: la interfaz del daemon, saltandose el envoltorio de la libreria. */
    private fun canalPorServicio(): ((String) -> Process)? {
        return try {
            val binder = Shizuku::class.java.getMethod("getBinder").invoke(null) as? IBinder
            val svc = binder?.let {
                Class.forName("moe.shizuku.server.IShizukuService${'$'}Stub")
                    .getMethod("asInterface", IBinder::class.java)
                    .invoke(null, it)
            }
            val np = svc?.javaClass?.methods?.firstOrNull { it.name == "newProcess" }
            val ctor = Class.forName("rikka.shizuku.ShizukuRemoteProcess").declaredConstructors
                .firstOrNull { it.parameterTypes.size == 1 }
                ?.apply { isAccessible = true }
            if (svc == null || np == null || ctor == null) null else { cmd: String ->
                val args = arrayOfNulls<Any>(np.parameterTypes.size)
                val i = np.parameterTypes.indexOfFirst { it.isArray && it.componentType == String::class.java }
                if (i >= 0) args[i] = arrayOf("sh", "-c", cmd)
                ctor.newInstance(np.invoke(svc, *args)!!) as Process
            }
        } catch (e: Exception) {
            null
        }
    }

    private val canal by lazy {
        canalPorMetodo()?.let { Pair("envoltorio", it) }
            ?: canalPorServicio()?.let { Pair("daemon", it) }
    }

    /** Devuelve null solo cuando ningun canal directo existe; los demas casos dan respuesta. */
    private fun porProcesoRemoto(cmd: String): ShellResult? {
        val abrir = canal?.second ?: return null
        val proc = try {
            abrir(cmd)
        } catch (e: Exception) {
            val c = if (e is java.lang.reflect.InvocationTargetException) e.targetException else e
            return ShellResult(-3, "", "el proceso remoto fue rechazado: ${c.javaClass.simpleName}: ${c.message}")
        }
        return try {
            var err = ""
            val hilo = Thread {
                err = try { proc.errorStream.bufferedReader().use { it.readText() } } catch (e: Exception) { "" }
            }
            hilo.start()
            val out = proc.inputStream.bufferedReader().use { it.readText() }
            val code = proc.waitFor()
            hilo.join(3000)
            ShellResult(code, out, err)
        } catch (e: Exception) {
            try { proc.destroy() } catch (_: Exception) {}
            ShellResult(-4, "", "${e.javaClass.simpleName}: ${e.message}")
        }
    }

    fun ejecutar(cmd: String): ShellResult {
        porProcesoRemoto(cmd)?.let { return it }
        val s = servicio() ?: return ShellResult(
            -1, "",
            if (listo()) "[$sello] canal=${canal?.first ?: "ninguno"} · ${ultimoError ?: "pulsa Probar servicio de comandos"}"
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
