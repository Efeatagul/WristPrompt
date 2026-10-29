package io.github.grassified.wristprompt.bridge

import android.app.Activity
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Build
import android.widget.ScrollView
import android.widget.TextView
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import java.security.MessageDigest
import io.github.grassified.wristprompt.R
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await

/** Debug-source-set-only probe. Never reads credential stores, intents or auth payloads. */
@SuppressLint("SetTextI18n", "ObsoleteSdkInt") // Technical debug report; shared with API-26 mobile.
class DataLayerDiagnosticsActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val view = TextView(this).apply { text = "Collecting Data Layer snapshot…"; setPadding(36, 36, 36, 36) }
        setContentView(ScrollView(this).apply { addView(view) })
        scope.launch {
            val report = snapshot()
            withContext(Dispatchers.IO) { filesDir.resolve("data-layer-diagnostics.txt").writeText(report) }
            view.text = report
        }
    }

    @Suppress("DEPRECATION")
    private suspend fun snapshot(): String = buildString {
        appendLine("package=$packageName build=debug timestamp=${System.currentTimeMillis()}")
        val signatures = if (Build.VERSION.SDK_INT >= 28) {
            packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                .signingInfo?.apkContentsSigners.orEmpty()
        } else {
            packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES).signatures.orEmpty()
        }
        val suffixes = signatures.map {
            MessageDigest.getInstance("SHA-256").digest(it.toByteArray())
                .joinToString("") { byte -> "%02x".format(byte) }.takeLast(8)
        }
        appendLine("certificate SHA-256 suffix=$suffixes")
        appendLine("GMS availability=${GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(this@DataLayerDiagnosticsActivity)}")
        appendLine("GMS version=${packageManager.getPackageInfo("com.google.android.gms", 0).versionName}")
        val localCapability = getString(R.string.local_capability)
        val expected = if (localCapability == BridgeProtocol.WEAR_CAPABILITY) BridgeProtocol.MOBILE_CAPABILITY else BridgeProtocol.WEAR_CAPABILITY
        appendLine("declared local=$localCapability expected remote=$expected")
        val declarations = resources.getStringArray(R.array.android_wear_capabilities)
        appendLine("packaged local capability present=${localCapability in declarations}")
        appendLine("Local declaration is a packaged resource; CapabilityClient never returns its own node.")
        val nodeClient = Wearable.getNodeClient(this@DataLayerDiagnosticsActivity)
        val capabilityClient = Wearable.getCapabilityClient(this@DataLayerDiagnosticsActivity)
        query("local node") { nodeClient.localNode.await().let(::nodeSummary) }
        query("connectedNodes") {
            val nodes = nodeClient.connectedNodes.await()
            if (nodes.isEmpty()) appendLine("decision=NO_CONNECTED_NODES (before capability/filter/send)")
            nodes.joinToString(prefix = "[", postfix = "]", transform = ::nodeSummary)
        }
        query("temporary debug capability registration") {
            capabilityClient.addLocalCapability(DiagnosticListenerService.CAPABILITY).await()
            "OK"
        }
        delay(2_000)
        for (filter in listOf(CapabilityClient.FILTER_ALL, CapabilityClient.FILTER_REACHABLE)) {
            for (capability in listOf(localCapability, expected, DiagnosticListenerService.CAPABILITY)) {
                query("capability=$capability filter=$filter") {
                    capabilityClient.getCapability(capability, filter).await().nodes
                        .joinToString(prefix = "[", postfix = "]", transform = ::nodeSummary)
                }
            }
        }
        query("production discovery decision") {
            val nodes = nodeClient.connectedNodes.await()
            val peers = WearConnectivity(this@DataLayerDiagnosticsActivity, expected).reachablePeers()
            when {
                nodes.isEmpty() -> "NO_CONNECTED_NODES"
                peers.isEmpty() -> "NODE_EXISTS_CAPABILITY_MISSING; auth sendRequest NOT_ATTEMPTED"
                else -> "CAPABILITY_DISCOVERED; production selection/auth not exercised"
            }
        }
        query("debug RPC") {
            val nodes = nodeClient.connectedNodes.await()
            if (nodes.size != 1) "SKIPPED (requires exactly one connected node)" else {
                appendLine("debug sendRequest attempted target=${nodeSummary(nodes.single())}")
                val response = Wearable.getMessageClient(this@DataLayerDiagnosticsActivity)
                    .sendRequest(nodes.single().id, DiagnosticListenerService.PATH, byteArrayOf()).await()
                if (response.contentEquals(byteArrayOf(1))) "SUCCESS (remote debug listener replied)" else "INVALID_REPLY"
            }
        }
        appendLine("Production auth RPC/state propagation not exercised; probe contains no credentials.")
    }

    private suspend fun StringBuilder.query(label: String, block: suspend () -> String) {
        val result = try { withTimeout(10_000) { block() } }
        catch (_: TimeoutCancellationException) { "TIMEOUT" }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: ApiException) { "API_STATUS_${failure.statusCode}" }
        catch (_: Exception) { "QUERY_FAILED" }
        appendLine("$label=$result")
    }

    private fun nodeSummary(node: Node): String =
        "idSuffix=${node.id.takeLast(6)} name=${node.displayName.filter { !it.isISOControl() }.take(64)} nearby=${node.isNearby}"

    override fun onDestroy() {
        Wearable.getCapabilityClient(this).removeLocalCapability(DiagnosticListenerService.CAPABILITY)
        scope.cancel()
        super.onDestroy()
    }
}
