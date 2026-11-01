package io.github.grassified.wristprompt.images

import android.content.Context
import android.net.Uri
import com.google.android.gms.wearable.*
import io.github.grassified.wristprompt.bridge.WearConnectivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

enum class ImageSyncStatus { WAITING, QUEUED, LOCAL_READY, FAILED }

/** Assets belong to a versioned, source-bound image DataItem, never auth/preset DTOs.
 * Data Layer queuing is not a durable watch ACK. Refresh is finite and event/manual
 * driven; no polling, inference dependency or application-level retry loop.
 */
class ImageDataSync(context: Context, private val peers: WearConnectivity, private val store: ImageFileStore) {
    private val client = Wearable.getDataClient(context.applicationContext)
    private val mutex = Mutex()
    private val mutable = MutableStateFlow(ImageSyncStatus.WAITING)
    val status = mutable.asStateFlow()
    suspend fun publish() = operation {
        if (peers.reachablePeers().isEmpty()) { mutable.value = ImageSyncStatus.WAITING; return@operation }
        val catalog = store.load()
        for (record in catalog.records) {
            val request = PutDataRequest.create(ImageWire.path(record)).setData(ImageWire.encode(ImagePacket(source = catalog.source, image = record)))
            if (!record.deleted) {
                val content = store.content(record.id)
                ImageWire.validateContent(record, content)
                request.putAsset("image", Asset.createFromBytes(content.image))
                request.putAsset("thumbnail", Asset.createFromBytes(content.thumbnail))
            }
            client.putDataItem(request).await()
        }
        mutable.value = ImageSyncStatus.QUEUED
    }
    suspend fun refresh() = operation {
        val catalog = store.load() // Offline library is loaded even when phone is absent.
        val eligible = peers.reachablePeers().map { it.id }.toSet()
        val node = catalog.node?.takeIf { it in eligible } ?: if (catalog.node == null) eligible.singleOrNull() else null
        if (node == null) { mutable.value = ImageSyncStatus.WAITING; return@operation }
        val uri = Uri.Builder().scheme("wear").authority(node).path(ImageWire.PREFIX).build()
        val buffer = client.getDataItems(uri, DataClient.FILTER_PREFIX).await()
        try {
            if (buffer.count > ImageLimits.MAX_RECORDS) fail(ImageProblem.CAPACITY)
            // Parse all bounded metadata before acquiring any binary bytes.
            val entries = buffer.map { item ->
                if (item.uri.host != node) fail(ImageProblem.PEER)
                val packet = ImageWire.decode(item.uri.path ?: fail(), item.data ?: fail())
                val assets = item.assets
                ImageWire.validateAssetKeys(packet, assets.keys)
                Triple(packet, assets["image"]?.id, assets["thumbnail"]?.id)
            }
            if (entries.map { it.first.image.id }.distinct().size != entries.size || entries.map { it.first.source }.distinct().size > 1) fail()
            // Apply terminal deletions first so an almost-full watch can accept additions.
            for ((packet, imageAsset, thumbAsset) in entries.sortedBy { !it.first.image.deleted }) {
                store.accept(node, eligible, packet) {
                    val image = readAsset(imageAsset, ImageLimits.MAX_IMAGE_BYTES)
                    val thumbnail = readAsset(thumbAsset, ImageLimits.MAX_THUMB_BYTES)
                    val content = AndroidImages.validate(packet.image, ImageContent(image, thumbnail, packet.image.width, packet.image.height))
                    if (node !in peers.reachablePeers().map { it.id }) fail(ImageProblem.PEER)
                    content
                }
            }
            mutable.value = ImageSyncStatus.LOCAL_READY
        } finally { buffer.release() }
    }
    private suspend fun readAsset(id: String?, cap: Int): ByteArray {
        if (id == null || id.length > 256) fail()
        val result = client.getFdForAsset(Asset.createFromRef(id)).await()
        try { return result.inputStream.use { readImageBytes(it, cap) } }
        finally { result.release() }
    }
    private suspend fun operation(block: suspend () -> Unit): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                val done = withTimeoutOrNull(60_000) { block(); true } ?: false
                if (!done) mutable.value = ImageSyncStatus.FAILED
                done
            }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutable.value = ImageSyncStatus.FAILED; false }
        }
    }
}
