package dev.opencarl.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket

data class PortResult(
    val port: Int,
    val open: Boolean,
    val banner: String = "",
    val ms: Long = 0,
) {
    fun line(): String = "$port ${if (open) "open" else "closed"} ${ms}ms" +
        if (banner.isNotBlank()) " [$banner]" else ""
}

/**
 * The network half of the toolbox: reachability, name resolution, HTTP probing
 * and a plain TCP connect scan. Only scan hosts you own or may test.
 */
class NetTools(private val http: OkHttpClient) {

    suspend fun ping(host: String, count: Int = 4): String = withContext(Dispatchers.IO) {
        val address = runCatching { InetAddress.getByName(host) }.getOrNull()
            ?: return@withContext "cannot resolve $host"
        val samples = mutableListOf<String>()
        repeat(count.coerceIn(1, 10)) { index ->
            val started = System.nanoTime()
            val reachable = runCatching { address.isReachable(1_500) }.getOrDefault(false)
            val ms = (System.nanoTime() - started) / 1_000_000
            samples += "seq=$index ${if (reachable) "reply" else "no reply"} ${ms}ms"
        }
        "$host (${address.hostAddress})\n" + samples.joinToString("\n")
    }

    suspend fun dns(name: String): String = withContext(Dispatchers.IO) {
        runCatching {
            val addresses = InetAddress.getAllByName(name)
            buildString {
                append(name).append(" ->\n")
                addresses.forEach { address ->
                    append("  ").append(address.hostAddress)
                    val reverse = runCatching { address.canonicalHostName }.getOrNull()
                    if (reverse != null && reverse != address.hostAddress) {
                        append("  (").append(reverse).append(")")
                    }
                    append('\n')
                }
            }
        }.getOrElse { "lookup failed: ${it.message}" }
    }

    suspend fun probe(url: String): String = withContext(Dispatchers.IO) {
        val target = if (url.startsWith("http")) url else "https://$url"
        runCatching {
            val started = System.nanoTime()
            val request = Request.Builder().url(target).get().build()
            http.newCall(request).execute().use { response ->
                val elapsed = (System.nanoTime() - started) / 1_000_000
                val body = response.body?.string().orEmpty()
                buildString {
                    append(target).append('\n')
                    append("status ").append(response.code).append(' ').append(response.message)
                    append(" in ").append(elapsed).append("ms\n")
                    response.headers.names().take(12).forEach { header ->
                        append("  ").append(header).append(": ")
                            .append(response.header(header)).append('\n')
                    }
                    append('\n').append(body.take(2_000))
                }
            }
        }.getOrElse { "probe failed: ${it.message}" }
    }

    suspend fun portScan(
        host: String,
        ports: IntRange = 1..1024,
        timeoutMs: Int = 350,
        concurrency: Int = 64,
    ): List<PortResult> = withContext(Dispatchers.IO) {
        ports.chunked(concurrency).flatMap { chunk ->
            chunk.map { port -> async { check(host, port, timeoutMs) } }.awaitAll()
        }.filter { it.open }.sortedBy { it.port }
    }

    private fun check(host: String, port: Int, timeoutMs: Int): PortResult {
        val started = System.nanoTime()
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), timeoutMs)
                val banner = runCatching {
                    socket.soTimeout = 250
                    val buffer = ByteArray(64)
                    val read = socket.getInputStream().read(buffer)
                    if (read > 0) String(buffer, 0, read).trim() else ""
                }.getOrDefault("")
                PortResult(port, true, banner, (System.nanoTime() - started) / 1_000_000)
            }
        } catch (error: Exception) {
            PortResult(port, false, "", (System.nanoTime() - started) / 1_000_000)
        }
    }

    fun localAddresses(): String = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { nic -> nic.inetAddresses.toList().map { nic.name to it.hostAddress } }
            .joinToString("\n") { (name, address) -> "$name  $address" }
    }.getOrDefault("no interfaces")

    suspend fun publicAddress(): String = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder().url("https://api.ipify.org").build()
            http.newCall(request).execute().use { it.body?.string().orEmpty().trim() }
        }.getOrElse { "unknown (${it.message})" }
    }

    companion object {
        val DEFAULT_PORTS = intArrayOf(
            21, 22, 23, 25, 53, 80, 110, 135, 139, 143, 443, 445, 993, 995, 1433, 1723,
            3306, 3389, 5432, 5555, 5900, 6379, 8080, 8443, 9000, 9090, 11434, 27017,
        )
    }
}
