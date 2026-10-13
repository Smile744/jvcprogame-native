package org.jvc.progame

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build

object JvcCommands {

    // Hostnames DoT reales. Uno mal escrito corta el Internet del movil.
    val DNS_PRESETS = listOf(
        "AdGuard (bloquea anuncios)" to "dns.adguard-dns.com",
        "Cloudflare (rapido)" to "dns.cloudflare-dns.com",
        "Quad9 (seguridad)" to "dns.quad9.net",
    )

    fun ponDns(host: String): ShellResult {
        if (!Regex("^[a-z0-9.-]+$").matches(host)) {
            return ShellResult(-3, "", "Hostname DNS invalido: $host")
        }
        return ShizukuShell.ejecutar(
            "settings put global private_dns_mode hostname && " +
                "settings put global private_dns_specifier $host"
        )
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

    private val FRAME_RE = Regex("Total frames rendered:\\s*(\\d+)")

    fun fpsDe(paquete: String): Int? {
        val r = ShizukuShell.ejecutar("dumpsys gfxinfo $paquete reset")
        if (!r.ok) return null
        return FRAME_RE.find(r.out)?.groupValues?.get(1)?.toIntOrNull()
    }
}
