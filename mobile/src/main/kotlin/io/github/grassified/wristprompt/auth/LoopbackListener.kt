package io.github.grassified.wristprompt.auth

import java.io.Closeable
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.SocketTimeoutException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** One short-lived HTTP callback. This socket never carries phone/watch traffic. */
class LoopbackListener private constructor(private val server: ServerSocket) : Closeable {
    val redirectUri = "http://127.0.0.1:${server.localPort}${OpenAiAuth.CALLBACK_PATH}"

    suspend fun await(transaction: OAuthTransaction, timeoutMs: Long = OpenAiAuth.BROWSER_MS): AuthorizationCode = withContext(Dispatchers.IO) {
        val deadline = System.nanoTime() + timeoutMs * 1_000_000
        while (true) {
            currentCoroutineContext().ensureActive()
            if (System.nanoTime() >= deadline) throw AuthException(AuthProblem.TIMEOUT)
            val socket = try { server.accept() } catch (_: SocketTimeoutException) { continue }
            socket.use {
                it.soTimeout = 2000
                val input = it.getInputStream()
                val bytes = java.io.ByteArrayOutputStream()
                var end = false
                try {
                    while (bytes.size() < 12_288) {
                        currentCoroutineContext().ensureActive()
                        // SO_TIMEOUT alone restarts for every byte; enforce the attempt's
                        // absolute deadline even when a local peer keeps sending slowly.
                        val remaining = deadline - System.nanoTime()
                        if (remaining <= 0) throw AuthException(AuthProblem.TIMEOUT)
                        it.soTimeout = minOf(2000L, (remaining / 1_000_000).coerceAtLeast(1)).toInt()
                        val b = input.read()
                        if (b == -1) break
                        bytes.write(b)
                        val a = bytes.toByteArray()
                        if (a.size >= 4 && a.takeLast(4) == listOf(13.toByte(), 10.toByte(), 13.toByte(), 10.toByte())) { end = true; break }
                    }
                } catch (_: SocketTimeoutException) { continue }
                if (!end) continue
                val raw = bytes.toByteArray()
                if (raw.any { b -> b.toInt() !in 9..126 }) continue
                val lines = raw.decodeToString().split("\r\n")
                val first = lines.first().split(' ')
                val hosts = lines.drop(1).filter { line -> line.startsWith("Host:", true) }
                // Ignore favicon/non-callback traffic; never consume OAuth state for it.
                if (first.size != 3 || first[0] != "GET" || first[2] != "HTTP/1.1" ||
                    !first[1].startsWith(OpenAiAuth.CALLBACK_PATH + "?") ||
                    hosts.size != 1 || hosts.single().substringAfter(':').trim() != "127.0.0.1:${server.localPort}") {
                    respond(it, false); continue
                }
                val result = try { transaction.callback(first[1]) } catch (e: AuthException) {
                    respond(it, false); throw e
                }
                respond(it, true)
                return@withContext result
            }
        }
        @Suppress("UNREACHABLE_CODE") error("Unreachable")
    }
    private fun respond(socket: java.net.Socket, success: Boolean) {
        // Do not echo any callback values into HTML, headers, or diagnostic errors.
        val body = if (success) "Return to WristPrompt to finish setup." else "WristPrompt could not accept this callback. Return to the app."
        try {
            socket.getOutputStream().write(("HTTP/1.1 ${if (success) "200 OK" else "400 Bad Request"}\r\n" +
                "Content-Type: text/plain; charset=utf-8\r\nCache-Control: no-store\r\n" +
                "Referrer-Policy: no-referrer\r\nContent-Security-Policy: default-src 'none'\r\n" +
                "Connection: close\r\nContent-Length: ${body.encodeToByteArray().size}\r\n\r\n$body").encodeToByteArray())
        } catch (_: Exception) { /* Browser disconnect does not undo a validated callback. */ }
    }
    override fun close() { server.close() }
    companion object {
        fun bind(): LoopbackListener {
            val server = ServerSocket()
            try {
                server.bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 1)
                server.soTimeout = 500
                return LoopbackListener(server)
            } catch (_: Exception) {
                server.close()
                throw AuthException(AuthProblem.LISTENER)
            }
        }
    }
}
