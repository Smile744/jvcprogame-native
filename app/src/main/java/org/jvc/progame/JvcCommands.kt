package org.jvc.progame

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import kotlin.math.roundToInt

object JvcCommands {

    // Hostnames DoT reales y verificados. Android con DNS privado estricto NO vuelve al DNS
    // normal: un nombre que no existe deja el movil sin Internet hasta apagarlo.
    val DNS_PRESETS = listOf(
        "AdGuard (bloquea anuncios)" to "dns.adguard-dns.com",
        "Cloudflare (rapido)" to "one.one.one.one",
        "Quad9 (seguridad)" to "dns.quad9.net",
    )

    private val HOST_RE = Regex("^[a-z0-9.-]{4,253}$")
    private val IPv4_RE = Regex("\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}")

    fun ponDns(host: String): ShellResult {
        if (!HOST_RE.matches(host)) {
            return ShellResult(-3, "", "Hostname DNS invalido: $host")
        }
        // Prima el nombre: si no resuelve, aplicarlo corta el Internet del movil.
        val prueba = ShizukuShell.ejecutar("nslookup $host")
        if (!IPv4_RE.containsMatchIn(prueba.out) && !prueba.err.contains("not found")) {
            return ShellResult(
                -8, "",
                "$host no resuelve, no lo aplico · " +
                    (prueba.out.trim().ifBlank { prueba.err.trim() }.ifBlank { "nslookup callado" })
            )
        }
        return ShizukuShell.ejecutar(
            "settings put global private_dns_mode hostname && " +
                "settings put global private_dns_specifier $host"
        )
    }

    /** nslookup + intento real de conexion al 853: ahi se ve si la operadora corta el DoT. */
    fun pruebaRutaDns(): ShellResult {
        val partes = ArrayList<String>()
        DNS_PRESETS.forEach { (_, host) ->
            partes.add("echo == $host")
            partes.add("nslookup $host | grep -m1 -A1 Address")
            partes.add("nc -w 3 $host 853 </dev/null && echo '853 ABIERTO' || echo '853 BLOQUEADO'")
        }
        return ShizukuShell.ejecutar(partes.joinToString("; "))
    }

    fun apagaDns(): ShellResult =
        ShizukuShell.ejecutar("settings put global private_dns_mode off")

    fun leeDnsActual(): ShellResult =
        ShizukuShell.ejecutar(
            "settings get global private_dns_mode && settings get global private_dns_specifier"
        )

    fun ponDensidad(valor: Int): ShellResult {
        if (valor !in 160..640) return ShellResult(-4, "", "Densidad fuera de rango (160-640)")
        return ShizukuShell.ejecutar("wm density $valor")
    }

    fun reseteaDensidad(): ShellResult = ShizukuShell.ejecutar("wm density reset")

    fun leeDensidad(): ShellResult = ShizukuShell.ejecutar("wm density")

    private val TAMANO_RE = Regex("Physical size:\\s*(\\d+)x(\\d+)")

    /** Tamano real del panel, el que no cambia aunque ya haya un ajuste puesto. */
    fun tamanoNativo(ctx: Context): Pair<Int, Int> {
        TAMANO_RE.find(ShizukuShell.ejecutar("wm size").out)?.let {
            return it.groupValues[1].toInt() to it.groupValues[2].toInt()
        }
        val dm = ctx.resources.displayMetrics
        return dm.widthPixels to dm.heightPixels
    }

    /**
     * Bajar de la mitad de la resolucion real deja la interfaz inservible y subir de la nativa
     * rompe el compositor: esos dos limites son el freno para no dejar el movil colgado.
     */
    fun ponTamano(ancho: Int, alto: Int, nativo: Pair<Int, Int>): ShellResult {
        val (nw, nh) = nativo
        if (ancho > alto) return ShellResult(-7, "", "Android exige ancho <= alto (pediste ${ancho}x${alto})")
        val minW = nw / 2
        val minH = nh / 2
        if (ancho !in minW..nw || alto !in minH..nh) {
            return ShellResult(
                -7, "",
                "Fuera de limite seguro: ancho entre $minW y $nw, alto entre $minH y $nh " +
                    "(nativo ${nw}x${nh}, pediste ${ancho}x${alto})"
            )
        }
        return ShizukuShell.ejecutar("wm size ${ancho}x${alto}")
    }

    fun reseteaTamano(): ShellResult = ShizukuShell.ejecutar("wm size reset")

    fun leeTamano(): ShellResult = ShizukuShell.ejecutar("wm size")

    fun reseteaPantalla(): ShellResult = ShizukuShell.ejecutar("wm size reset && wm density reset")

    // user_refresh_rate no lo respetan todos los fabricantes: puede decir OK y no cambiar nada.
    fun ponHz(valor: Int): ShellResult {
        if (valor !in 30..165) return ShellResult(-5, "", "Hz fuera de rango")
        return ShizukuShell.ejecutar("settings put system user_refresh_rate $valor")
    }

    private val PAQUETE_RE = Regex("^[A-Za-z0-9._]+$")
    private fun seguro(p: String) = p.matches(PAQUETE_RE)
    private fun mala(p: String) = ShellResult(-6, "", "Nombre de paquete invalido: $p")

    fun congela(paquete: String): ShellResult =
        if (!seguro(paquete)) mala(paquete) else ShizukuShell.ejecutar("pm suspend $paquete")

    fun descongela(paquete: String): ShellResult =
        if (!seguro(paquete)) mala(paquete) else ShizukuShell.ejecutar("pm unsuspend $paquete")

    fun mata(paquete: String): ShellResult =
        if (!seguro(paquete)) mala(paquete) else ShizukuShell.ejecutar("am force-stop $paquete")

    fun appsInstaladas(ctx: Context): List<Pair<String, String>> {
        val pm = ctx.packageManager
        return pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 }
            .map { it.loadLabel(pm).toString() to it.packageName }
            .sortedBy { it.first.lowercase() }
    }

    fun soportaSuspension() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    // ---------- alto rendimiento ----------

    /** Quita las animaciones: lo que mas se nota al tacto y lo unico "acelerador" legal sin root. */
    fun animaCero(): ShellResult = ShizukuShell.ejecutar(
        "settings put global window_animation_scale 0 && " +
            "settings put global transition_animation_scale 0 && " +
            "settings put global animator_duration_scale 0"
    )

    fun animaNormal(): ShellResult = ShizukuShell.ejecutar(
        "settings put global window_animation_scale 1 && " +
            "settings put global transition_animation_scale 1 && " +
            "settings put global animator_duration_scale 1"
    )

    /** El ahorro de bateria es el que baja los nucleos: apagarlo antes de jugar. */
    fun ahorroOff(): ShellResult =
        ShizukuShell.ejecutar("settings put global low_power 0 ; cmd power set-mode 0")

    /** Saca el juego de la lista de reposo de Doze para que la ROM no lo duerma ni le frene. */
    fun fueraDeReposo(paquete: String): ShellResult =
        if (!seguro(paquete)) mala(paquete) else ShizukuShell.ejecutar("dumpsys deviceidle whitelist +$paquete")

    fun estadoTermico(): ShellResult =
        ShizukuShell.ejecutar("dumpsys thermalservice | grep -E 'mStatus|Temperature|type'")

    /**
     * Frenos reales de CPU/GPU. Escalamos los nucleos por shell NO se puede (eso es root),
     * pero si se puede leer y ensenar donde esta el limite puesto por el fabricante.
     */
    fun frenosCpu(): ShellResult = ShizukuShell.ejecutar(
        "grep '' /sys/devices/system/cpu/cpu*/cpufreq/scaling_governor 2>/dev/null; " +
            "grep '' /sys/devices/system/cpu/cpu*/cpufreq/scaling_max_freq 2>/dev/null"
    )

    private val RESUMIDA_RE = Regex("([A-Za-z0-9._]+)/[A-Za-z0-9._]+")

    /** Que app esta realmente en pantalla ahora mismo, para que el contador funcione sin elegir nada. */
    fun appEnPrimeroPlano(): String? {
        val r = ShizukuShell.ejecutar(
            "dumpsys activity activities | grep -m1 -E 'topResumedActivity|mResumedActivity'"
        )
        if (!r.ok) return null
        val pkg = RESUMIDA_RE.find(r.out)?.groupValues?.get(1) ?: return null
        return if (pkg.matches(PAQUETE_RE) && pkg != "android") pkg else null
    }

    private val FRAME_RE = Regex("Total frames rendered:\\s*(\\d+)")

    /** Frames del hilo de dibujo de la app. Los juegos Unity/Vulkan casi no usan esa via. */
    fun fpsDe(paquete: String): Int? {
        val r = ShizukuShell.ejecutar("dumpsys gfxinfo $paquete reset")
        if (!r.ok) return null
        return FRAME_RE.find(r.out)?.groupValues?.get(1)?.toIntOrNull()
    }

    // ---------- FPS por SurfaceFlinger ----------

    private val CAPA_SEGURA = Regex("^[A-Za-z0-9._\\[\\]()#/ -]+$")
    private var capaNombre: String? = null
    private var capaDe: String? = null

    /** El juego se dibuja en su propia capa: ahi siestan los timestamps reales de cada frame. */
    private fun capaDelJuego(pkg: String): String? {
        if (capaDe == pkg && capaNombre != null) return capaNombre
        val r = ShizukuShell.ejecutar("dumpsys SurfaceFlinger --list | grep -F $pkg")
        if (!r.ok) return null
        // Algunas ROMs no ponen el sufijo #id: se acepta cualquier linea que sea una capa.
        val filas = r.out.lines().map { it.trim() }
            .filter { it.contains(pkg) && !it.startsWith("Window{") && !it.startsWith("Background") }
        val elegida = filas.firstOrNull { it.contains("SurfaceView") }
            ?: filas.firstOrNull { it.contains("/") }
            ?: filas.firstOrNull()
            ?: return null
        if (!elegida.matches(CAPA_SEGURA)) return null
        ShizukuShell.ejecutar("dumpsys SurfaceFlinger --latency-enable '$elegida'")
        // El anillo de timestamps tarda un instante en llenarse tras activar el muestreo.
        try {
            Thread.sleep(1200)
        } catch (e: InterruptedException) {
        }
        capaDe = pkg
        capaNombre = elegida
        return elegida
    }

    /** Lo que ve el sistema de tu juego, en texto, para que lo pegues en el chat sin adb. */
    fun diagnosticoCapas(paquete: String): ShellResult =
        if (!seguro(paquete)) mala(paquete) else ShizukuShell.ejecutar(
            "echo LISTA DE CAPAS:; dumpsys SurfaceFlinger --list | grep -F $paquete; " +
                "echo GFXINFO:; dumpsys gfxinfo $paquete | grep -E 'Total frames rendered|SurfaceView|Layer'"
        )

    fun olvidaCapa() {
        capaDe = null
        capaNombre = null
    }

    /** FPS medidos sobre los ultimos ~127 frames que guarda SurfaceFlinger. */
    fun fpsSuperficie(paquete: String): Int? {
        val capa = capaDelJuego(paquete) ?: return null
        val r = ShizukuShell.ejecutar("dumpsys SurfaceFlinger --latency '$capa'")
        if (!r.ok) return null
        val filas = r.out.lines().drop(1).map { it.trim() }.filter { it.isNotEmpty() }
        val series = filas.map { it.split(Regex("\\s+")).mapNotNull { x -> x.toLongOrNull() } }
            .filter { it.isNotEmpty() }
        if (series.isEmpty()) return null
        val tam = series.first().size
        val i = when {
            tam >= 3 -> 2
            tam == 2 -> 1
            else -> 0
        }
        val utiles = series.filter { it.size > i }
        if (utiles.size < 2) return null
        val t0 = utiles.first()[i]
        val t1 = utiles.last()[i]
        if (t1 <= t0) return null
        return (((utiles.size - 1) * 1_000_000_000.0) / (t1 - t0)).roundToInt()
    }
}
