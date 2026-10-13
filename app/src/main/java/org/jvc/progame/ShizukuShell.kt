package org.jvc.progame

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

data class ShellResult(val code: Int, val out: String, val err: String) {
    val ok get() = code == 0
    fun resumen() = if (ok) "OK" else (err.trim().ifEmpty { "salida $code" })
}

object ShizukuShell {

    fun binderVivo() = try { Shizuku.pingBinder() } catch (e: Exception) { false }

    fun permisoConcedido() = try {
        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (e: Exception) {
        false
    }

    fun listo() = binderVivo() && permisoConcedido()

    fun ejecutar(cmd: String): ShellResult {
        if (!listo()) return ShellResult(-1, "", "Shizuku no esta activo o falta el permiso")
        return try {
            val proc = Shizuku.newProcess(arrayOf("sh", "-c", cmd), null, null)
            val out = proc.inputStream.bufferedReader().use { it.readText() }
            val err = proc.errorStream.bufferedReader().use { it.readText() }
            val code = proc.waitFor()
            ShellResult(code, out, err)
        } catch (e: Exception) {
            ShellResult(-2, "", "${e.javaClass.simpleName}: ${e.message}")
        }
    }
}
