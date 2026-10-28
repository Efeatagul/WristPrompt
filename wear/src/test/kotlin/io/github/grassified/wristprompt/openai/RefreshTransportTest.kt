package io.github.grassified.wristprompt.openai

import java.net.InetAddress
import java.net.Proxy
import java.net.ServerSocket
import java.net.UnknownHostException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.*
import org.junit.Test

class RefreshTransportTest {
    @Test fun dnsFailureProvesNoRequestWasSentWithoutExposingCause() = runBlocking {
        val http = OpenAiHttp(OkHttpClient.Builder().proxy(Proxy.NO_PROXY)
            .dns { throw UnknownHostException("synthetic-private-diagnostic") }.build())
        try {
            http.execute(Request.Builder().url("https://synthetic.invalid").build(), 2, trackTransmission = true) { fail("No response expected") }
            fail("Expected network failure")
        } catch (failure: ApiFailure) {
            assertEquals(ApiProblem.NETWORK, failure.problem); assertTrue(failure.requestNotSent)
            assertNull(failure.cause); assertFalse(failure.message.orEmpty().contains("synthetic-private-diagnostic"))
        }
    }

    @Test fun connectionLostAfterRequestHeadersIsAnUncertainOutcome() = runBlocking {
        ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { server ->
            server.soTimeout = 2000
            val worker = Executors.newSingleThreadExecutor()
            try {
                val received = worker.submit<Boolean> {
                    server.accept().use { socket ->
                        socket.soTimeout = 2000
                        val reader = socket.getInputStream().bufferedReader()
                        while (true) {
                            val line = reader.readLine() ?: return@submit false
                            if (line.isEmpty()) break
                        }
                        // Deliberately close without a response after receiving the request.
                        true
                    }
                }
                val http = OpenAiHttp(OkHttpClient.Builder().proxy(Proxy.NO_PROXY).retryOnConnectionFailure(false).build())
                try {
                    http.execute(Request.Builder().url("http://127.0.0.1:${server.localPort}/synthetic").build(), 2, trackTransmission = true) { fail("No response expected") }
                    fail("Expected lost response")
                } catch (failure: ApiFailure) {
                    assertEquals(ApiProblem.NETWORK, failure.problem); assertFalse(failure.requestNotSent)
                    assertNull(failure.cause)
                }
                assertTrue(received.get(3, TimeUnit.SECONDS))
            } finally { worker.shutdownNow() }
        }
    }
}
