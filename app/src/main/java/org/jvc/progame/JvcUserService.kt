package org.jvc.progame

/**
 * Este proceso lo arranca Shizuku como uid shell, asi que un exec normal hereda
 * los permisos de adb shell sin tocar newProcess, que es privado en la API 13.
 */
class JvcUserService : IJvcUserService.Stub() {

    override fun exec(cmd: String): String = try {
        val proc = Runtime.getRuntime().exec(arrayOf("sh", "-c", cmd))
        var err = ""
        val hilo = Thread { err = proc.errorStream.bufferedReader().use { it.readText() } }
        hilo.start()
        val out = proc.inputStream.bufferedReader().use { it.readText() }
        val code = proc.waitFor()
        hilo.join(3000)
        "$code$SEP$out$SEP$err"
    } catch (e: Exception) {
        "$COD_FALLO$SEP$SEP${e.javaClass.simpleName}: ${e.message}"
    }

    override fun destroy() {
        System.exit(0)
    }

    companion object {
        private const val SEP = "\u0001"
        private const val COD_FALLO = -99
    }
}
