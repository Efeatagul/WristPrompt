package io.github.grassified.wristprompt.bridge

import android.content.Context
import android.os.SystemClock
import androidx.core.net.toUri
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/** Holds one debug advertisement and awaited listeners throughout a measured observation window. */
internal class CapabilityTimingExperiment(context: Context, private val nodeSummary: (Node) -> String) {
    private val client = Wearable.getCapabilityClient(context.applicationContext)

    suspend fun collect(local: String, expected: String): String = buildString {
        val started = SystemClock.elapsedRealtime()
        val names = listOf(local, expected, DiagnosticListenerService.CAPABILITY)
        val events = mutableListOf<String>()
        val listener = CapabilityClient.OnCapabilityChangedListener { info ->
            if (events.size < 100) events += "event elapsedMs=${SystemClock.elapsedRealtime() - started} capability=${safeName(info.name)} nodes=${info.nodes.map(nodeSummary)}"
        }
        val allUri = "wear://*/".toUri()
        try {
            query("listener prefix registration") { client.addListener(listener, allUri, CapabilityClient.FILTER_PREFIX).await(); "OK" }
            for (name in names) {
                query("listener registration=$name") { client.addListener(listener, name).await(); "OK" }
            }
            // Remove only our diagnostic capability, await completion, then create a fresh advertisement.
            query("previous debug capability removal") { client.removeLocalCapability(DiagnosticListenerService.CAPABILITY).await(); "OK" }
            query("temporary debug capability registration") { client.addLocalCapability(DiagnosticListenerService.CAPABILITY).await(); "OK" }
            val registeredAt = SystemClock.elapsedRealtime()
            for (seconds in listOf(0, 1, 5, 15, 30)) {
                delay((seconds * 1000L - (SystemClock.elapsedRealtime() - registeredAt)).coerceAtLeast(0))
                appendLine("snapshot targetSeconds=$seconds elapsedMs=${SystemClock.elapsedRealtime() - registeredAt}")
                for (filter in listOf(CapabilityClient.FILTER_ALL, CapabilityClient.FILTER_REACHABLE)) {
                    query("getAllCapabilities filter=$filter") {
                        client.getAllCapabilities(filter).await().toSortedMap().entries.joinToString(prefix = "{", postfix = "}") {
                            "${safeName(it.key)}=${it.value.nodes.map(nodeSummary)}"
                        }
                    }
                    for (name in names) {
                        query("capability=$name filter=$filter") {
                            client.getCapability(name, filter).await().nodes.map(nodeSummary).toString()
                        }
                    }
                }
            }
            // The second emulator starts slightly later; preserve our advertisement through its final query.
            appendLine("holding debug advertisement for peer final snapshot: 5 seconds")
            delay(5_000)
            appendLine("capability events count=${events.size} (maximum retained=100)")
            events.forEach { appendLine(it) }
        } finally {
            withContext(NonCancellable) {
                query("debug capability cleanup") { client.removeLocalCapability(DiagnosticListenerService.CAPABILITY).await(); "OK" }
                for (name in names) {
                    query("listener cleanup=$name") { client.removeListener(listener, name).await().toString() }
                }
                query("listener prefix cleanup") { client.removeListener(listener).await().toString() }
            }
        }
    }

    private suspend fun StringBuilder.query(label: String, action: suspend () -> String) {
        val result = try { withTimeout(5_000) { action() } }
        catch (_: TimeoutCancellationException) { "TIMEOUT" }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: ApiException) { "API_STATUS_${failure.statusCode}" }
        catch (_: Exception) { "QUERY_FAILED" }
        appendLine("$label=$result")
    }

    private fun safeName(name: String) = name.filter { !it.isISOControl() }.take(128)
}
