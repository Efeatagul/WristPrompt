package io.github.grassified.wristprompt.storage

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.graphics.*
import android.os.Bundle
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import io.github.grassified.wristprompt.openai.*
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

/** Only DebugGenerationActivity's dedicated synthetic files/SQLite. No production accounts/data.
 * Run seed then verify in separate processes; gesture uses actual multi-pointer input.
 */
class ImageGenerationInstrumentation : Instrumentation() {
    private var checkpoint = "seed"
    private var phase = "init"
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); checkpoint = arguments?.getString("checkpoint") ?: "seed"; start() }
    override fun onStart() {
        val result = Bundle()
        try {
            check(context.packageName.endsWith(".test")); runBlocking { verify() }
            result.putString("stream", "PASS: D7-F $checkpoint native JPEG/SQLite, ownership, synthetic generation and viewer; phase=$phase\n")
            finish(Activity.RESULT_OK, result)
        } catch (failure: Throwable) {
            runCatching { capture("failure") }
            runCatching {
                val tree = StringBuilder()
                fun append(n: AccessibilityNodeInfo?, depth: Int = 0) {
                    if (n == null || depth > 32 || tree.length > 20000) return
                    val bounds = Rect(); n.getBoundsInScreen(bounds)
                    tree.append("$depth text=${n.text} desc=${n.contentDescription} click=${n.isClickable} visible=${n.isVisibleToUser} bounds=$bounds state=${n.stateDescription}\n")
                    for (i in 0 until n.childCount) append(n.getChild(i), depth + 1)
                }
                append(uiAutomation.rootInActiveWindow)
                java.io.File(targetContext.createDeviceProtectedStorageContext().filesDir, "d7f-acceptance-nodes.txt").writeText(tree.toString())
            }
            result.putString("stream", "FAIL: D7-F $checkpoint phase=$phase (${failure.javaClass.simpleName}); payloads redacted\n")
            finish(Activity.RESULT_CANCELED, result)
        }
    }
    private fun node(text: String): AccessibilityNodeInfo? {
        fun find(n: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
            if (n == null) return null
            if (n.isVisibleToUser && (n.text?.toString() == text || n.contentDescription?.toString() == text)) return n
            return (0 until n.childCount).firstNotNullOfOrNull { find(n.getChild(it)) }
        }
        return find(uiAutomation.rootInActiveWindow)
    }
    private fun click(text: String) {
        idle() // Canonical draft/state can be ahead of the currently composed enabled semantics.
        val oldPhase = phase
        phase = "$oldPhase/action-$text"
        repeat(60) {
            var match = node(if (text == "Open image") "Open generated image" else text) ?: node(text)
            if (match != null) {
                while (match != null && !match.isClickable) match = match.parent
                val target = checkNotNull(match)
                if (!target.isEnabled) { idle(); return@repeat }
                // Generation/navigation use explicit semantic button actions; viewer acceptance uses real touch.
                if (text !in setOf("Open image", "Close")) {
                    uiAutomation.executeAndWaitForEvent({ check(target.performAction(AccessibilityNodeInfo.ACTION_CLICK)) }, { true }, 5000)
                    idle(); phase = oldPhase; return
                }
                phase = "$oldPhase/action-$text/visible-bounds"
                val bounds = Rect().also(target::getBoundsInScreen)
                repeat(3) { frame() }
                var fresh = node(if (text == "Open image") "Open generated image" else text) ?: return@repeat
                while (!fresh.isClickable) fresh = fresh.parent ?: return@repeat
                val freshBounds = Rect().also(fresh::getBoundsInScreen)
                if (freshBounds != bounds) return@repeat // A tap during a fling only stops scrolling.
                check(bounds.intersect(0, 0, targetContext.resources.displayMetrics.widthPixels, targetContext.resources.displayMetrics.heightPixels))
                // Lazy row semantics may extend under the fixed composer footer. Tap only its visible intersection.
                var ancestor = target.parent
                while (ancestor != null) {
                    val clip = Rect().also(ancestor::getBoundsInScreen)
                    check(bounds.intersect(clip))
                    ancestor = ancestor.parent
                }
                java.io.File(targetContext.createDeviceProtectedStorageContext().filesDir, "d7f-$checkpoint-viewer-metrics.txt")
                    .appendText("tap $text enabled=${target.isEnabled} bounds=$bounds\n")
                // Android's input command supplies the native tap sequence; no accessibility click substitutes for viewer touch.
                uiAutomation.executeAndWaitForEvent({
                    android.os.ParcelFileDescriptor.AutoCloseInputStream(uiAutomation.executeShellCommand("input tap ${bounds.centerX()} ${bounds.centerY()}"))
                        .use { it.readBytes() }
                }, { it.eventType == android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
                    it.eventType == android.view.accessibility.AccessibilityEvent.TYPE_WINDOWS_CHANGED }, 5000)
                idle()
                if (text == "Open image") {
                    val deadline = SystemClock.uptimeMillis() + 10000
                    while (node("Generated image") == null) {
                        check(SystemClock.uptimeMillis() < deadline)
                        frame()
                    }
                    idle() // Capture only after the asynchronously decoded bitmap has rendered.
                }
                phase = oldPhase; return
            }
            // Small real swipes do not page past a large-font row between observations.
            fun scrollable(n: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
                if (n == null) return null
                if (n.isVisibleToUser && n.isScrollable) return n
                return (0 until n.childCount).firstNotNullOfOrNull { scrollable(n.getChild(it)) }
            }
            val list = scrollable(uiAutomation.rootInActiveWindow)
            if (list == null) { idle(); return@repeat } // Navigation/composition can briefly have no lazy list.
            val area = Rect().also(list::getBoundsInScreen)
            val height = area.height().toFloat()
            val now = SystemClock.uptimeMillis()
            for (step in 0..16) {
                val action = if (step == 0) MotionEvent.ACTION_DOWN else if (step == 16) MotionEvent.ACTION_UP else MotionEvent.ACTION_MOVE
                // Stationary tail gives zero release velocity; acceptance must not tap an ongoing fling.
                val event = MotionEvent.obtain(now, now + step * 15L, action, area.exactCenterX(),
                    area.top + height * (0.80f - minOf(step, 8) * 0.0375f), 0)
                try { sendPointerSync(event) } finally { event.recycle() }
            }
            idle()
        }
        error("Action not found")
    }
    private fun frame() {
        val ready = java.util.concurrent.CountDownLatch(1)
        runOnMainSync { android.view.Choreographer.getInstance().postFrameCallback { ready.countDown() } }
        check(ready.await(2, java.util.concurrent.TimeUnit.SECONDS))
    }
    private fun idle() {
        waitForIdleSync(); frame(); frame(); waitForIdleSync()
        uiAutomation.waitForIdle(300, 5000)
    }
    private fun viewport(): String {
        val root = uiAutomation.rootInActiveWindow
        fun find(n: AccessibilityNodeInfo?): String? {
            if (n == null) return null
            if (n.contentDescription?.toString() == "Image viewer") { n.refresh(); return n.stateDescription?.toString() }
            return (0 until n.childCount).firstNotNullOfOrNull { find(n.getChild(it)) }
        }
        return checkNotNull(find(root))
    }
    private data class Viewport(val scale: Float, val x: Float, val y: Float, val bounds: Rect, val maxX: Float, val maxY: Float) {
        fun checkBounds() { check(scale in 1f..5f && kotlin.math.abs(x) <= maxX + 1f && kotlin.math.abs(y) <= maxY + 1f) }
    }
    private fun measured(image: GeneratedAttachment): Viewport {
        val match = checkNotNull(Regex("Zoom ([^x]+)x; pan ([^,]+), (.+)").matchEntire(viewport()))
        val bounds = Rect().also { checkNotNull(node("Image viewer")).getBoundsInScreen(it) }
        val scale = match.groupValues[1].toFloat()
        val fit = minOf(bounds.width().toFloat() / image.width, bounds.height().toFloat() / image.height)
        return Viewport(scale, match.groupValues[2].toFloat(), match.groupValues[3].toFloat(), bounds,
            ((image.width * fit * scale - bounds.width()) / 2).coerceAtLeast(0f),
            ((image.height * fit * scale - bounds.height()) / 2).coerceAtLeast(0f)).also { it.checkBounds() }
    }
    private fun record(name: String, value: Viewport) {
        java.io.File(targetContext.createDeviceProtectedStorageContext().filesDir, "d7f-$checkpoint-viewer-metrics.txt")
            .appendText("$name scale=${value.scale} pan=${value.x},${value.y} viewport=${value.bounds.width()}x${value.bounds.height()} max=${value.maxX},${value.maxY}\n")
    }
    private fun gesture(pinch: Boolean, bounds: Rect, direction: Float = 1f) {
        val start = SystemClock.uptimeMillis()
        val props = Array(2) { index -> MotionEvent.PointerProperties().apply { id = index; toolType = MotionEvent.TOOL_TYPE_FINGER } }
        fun event(action: Int, count: Int, x0: Float, y0: Float, x1: Float, y1: Float, step: Int) {
            val coords = arrayOf(MotionEvent.PointerCoords().apply { x = x0; y = y0; pressure = 1f; size = 1f },
                MotionEvent.PointerCoords().apply { x = x1; y = y1; pressure = 1f; size = 1f })
            val event = MotionEvent.obtain(start, start + step * 16L, action, count, props, coords, 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0)
            try { sendPointerSync(event) } finally { event.recycle() }
        }
        val cx = bounds.exactCenterX(); val cy = bounds.exactCenterY()
        val initial = minOf(bounds.width(), bounds.height()) * 0.09f
        val spread = minOf(bounds.width(), bounds.height()) * 0.23f
        val pan = bounds.width() * 0.32f * direction
        event(MotionEvent.ACTION_DOWN, 1, cx - if (pinch) initial else 0f, cy, cx + initial, cy, 0)
        if (pinch) event(MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), 2, cx - initial, cy, cx + initial, cy, 1)
        repeat(12) { i ->
            val offset = initial + spread * i / 11
            event(MotionEvent.ACTION_MOVE, if (pinch) 2 else 1, cx + if (pinch) -offset else pan * i / 11, cy,
                cx + offset, cy, i + 2)
        }
        if (pinch) event(MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), 2, cx - initial - spread, cy, cx + initial + spread, cy, 14)
        event(MotionEvent.ACTION_UP, 1, cx + if (pinch) -initial - spread else pan, cy, cx + initial + spread, cy, 15)
        idle()
    }
    private fun capture(name: String) {
        val screenshot = checkNotNull(uiAutomation.takeScreenshot())
        try {
            val directory = targetContext.createDeviceProtectedStorageContext().filesDir
            java.io.File(directory, "d7f-$checkpoint-$name.png").outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally { screenshot.recycle() }
    }
    private suspend fun verify() {
        java.io.File(targetContext.createDeviceProtectedStorageContext().filesDir, "d7f-$checkpoint-viewer-metrics.txt").delete()
        phase = "native-output-validation"
        val bitmap = Bitmap.createBitmap(32, 24, Bitmap.Config.ARGB_8888)
        val bytes = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }.toByteArray()
        val png = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        bitmap.recycle()
        val normalized = GenerationOutput.normalize(GeneratedOutput(bytes)); check(normalized.width == 32 && normalized.height == 24)
        val tooWide = Bitmap.createBitmap(1281, 1, Bitmap.Config.ARGB_8888)
        val dimensions = ByteArrayOutputStream().also { tooWide.compress(Bitmap.CompressFormat.JPEG, 80, it) }.toByteArray(); tooWide.recycle()
        for (invalid in listOf(bytes.dropLast(8).toByteArray(), png, ByteArray(100), ByteArray(GenerationOutput.MAX_BYTES + 1), dimensions)) {
            check(runCatching { GenerationOutput.normalize(GeneratedOutput(invalid)) }.isFailure)
        }
        phase = "open-synthetic-activity"
        val activity = startActivitySync(Intent(targetContext, DebugGenerationActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as DebugGenerationActivity
        idle()
        val fixture = activity.acceptanceOwner()
        val vm = fixture.vm
        withTimeout(10000) { vm.state.first { it.stage == ProofStage.READY } }
        suspend fun main(block: () -> Unit) = withContext(Dispatchers.Main) { block() }
        suspend fun done(stage: ChatStage) = withTimeout(10000) { vm.chat.first { it.stage == stage } }
        val previous = fixture.requests
        if (checkpoint == "seed") {
            phase = "explicit-generation"
            main { fixture.command("NEW"); fixture.command("DRAFT"); fixture.command("COMPLETE") }
            check(fixture.requests == previous)
            click("Generate image"); done(ChatStage.COMPLETED); idle()
            check(fixture.requests == previous + 1 && vm.chat.value.messages.last().generated != null)
        } else {
            phase = "inert-restart-reopen"
            val seedPid = java.io.File(targetContext.createDeviceProtectedStorageContext().filesDir, "d7f-seed-pid.txt").readText()
            check(seedPid.toInt() != android.os.Process.myPid())
            check(vm.chat.value.messages.last().generated != null && fixture.requests == previous)
            check(vm.chat.value.draft.isEmpty())
            main { vm.refreshHistory() }; withTimeout(10000) { vm.history.first { !it.loading && it.entries.isNotEmpty() } }
            val reopened = CompletableDeferred<Unit>()
            main { vm.openChat(vm.history.value.entries.first().id) { reopened.complete(Unit) } }; withTimeout(10000) { reopened.await() }
            click("Back to chat")
        }
        phase = "rendered-image-open"
        val reference = checkNotNull(vm.chat.value.messages.last().generated)
        click("Open image"); check(viewport().startsWith("Zoom 1.0x")); capture("viewer-fit")
        record("fit image=${reference.width}x${reference.height}", measured(reference))
        phase = "pinch"
        gesture(true, measured(reference).bounds); check(!viewport().startsWith("Zoom 1.0x")); capture("viewer-zoom")
        val zoomed = measured(reference); record("zoom", zoomed)
        phase = "pan"
        check(zoomed.maxX > 0f)
        val direction = if (zoomed.x >= 0f) -1f else 1f // Always move toward available space, even if pinch ended at an edge.
        gesture(false, zoomed.bounds, direction)
        val panned = measured(reference); record("pan direction=$direction start=${zoomed.bounds.exactCenterX()},${zoomed.bounds.exactCenterY()} end=${zoomed.bounds.exactCenterX() + zoomed.bounds.width() * 0.32f * direction},${zoomed.bounds.exactCenterY()} delta=${panned.x - zoomed.x}", panned)
        check(kotlin.math.abs(panned.x - zoomed.x) > 1f); capture("viewer-pan")
        phase = "pan-clamping"
        repeat(8) { gesture(false, zoomed.bounds, direction); measured(reference).checkBounds() }
        val edge = measured(reference); record("edge", edge)
        check(kotlin.math.abs(kotlin.math.abs(edge.x) - edge.maxX) < 1f)
        gesture(false, edge.bounds, direction)
        check(kotlin.math.abs(measured(reference).x - edge.x) < 1f)
        gesture(false, edge.bounds, -direction)
        check(kotlin.math.abs(measured(reference).x - edge.x) > 1f)
        phase = "configuration-recreation"
        val monitor = addMonitor(DebugGenerationActivity::class.java.name, null, false)
        try {
            runOnMainSync { activity.recreate() }
            val recreated = checkNotNull(monitor.waitForActivityWithTimeout(10000)) as DebugGenerationActivity
            idle(); check(recreated.acceptanceOwner() === fixture)
            check(viewport().startsWith("Zoom 1.0x")) // Viewport is intentionally ephemeral; image ownership survives.
        } finally { removeMonitor(monitor) }
        phase = "close-reopen-back"
        click("Close"); click("Open image"); check(viewport().startsWith("Zoom 1.0x"))
        sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK); idle(); check(node("Close") == null)
        phase = "surrounding-reader-rotary"
        fun readerPosition(): String {
            fun list(n: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
                if (n == null) return null
                n.refresh()
                if (n.isVisibleToUser && n.isScrollable) return n
                return (0 until n.childCount).firstNotNullOfOrNull { list(n.getChild(it)) }
            }
            val positions = mutableListOf<String>()
            fun collect(n: AccessibilityNodeInfo?) {
                if (n == null || positions.size >= 128 || !n.isVisibleToUser) return
                positions += Rect().also(n::getBoundsInScreen).toString()
                for (index in 0 until n.childCount) collect(n.getChild(index))
            }
            collect(list(uiAutomation.rootInActiveWindow))
            return positions.joinToString(";") // Geometry only; no prompt, image payload or path.
        }
        val beforeRotary = readerPosition()
        fun rotary(amount: Int) {
            android.os.ParcelFileDescriptor.AutoCloseInputStream(uiAutomation.executeShellCommand("input rotaryencoder scroll --axis SCROLL,$amount"))
                .use { it.readBytes() }
            idle()
        }
        rotary(6); val afterRotary = readerPosition()
        rotary(-6); val returnedRotary = readerPosition()
        java.io.File(targetContext.createDeviceProtectedStorageContext().filesDir, "d7f-$checkpoint-viewer-metrics.txt")
            .appendText("rotary before=$beforeRotary after=$afterRotary returned=$returnedRotary requests=${fixture.requests}\n")
        check(beforeRotary != afterRotary || afterRotary != returnedRotary)
        capture("reader-rotary")
        check(fixture.requests == previous + if (checkpoint == "seed") 1 else 0)
        if (checkpoint == "seed") {
            phase = "stop-before-ownership"
            main { fixture.command("NEW"); fixture.command("DRAFT"); fixture.command("HOLD_PROVIDER"); vm.generateImage() }
            done(ChatStage.RECEIVING)
            main { vm.stop(); fixture.command("RELEASE") }
            check(vm.chat.value.stage == ChatStage.CANCELLED)
            phase = "stop-after-ownership"
            main { fixture.command("NEW"); fixture.command("DRAFT"); fixture.command("HOLD_COMMIT"); vm.generateImage() }
            withTimeout(10000) { fixture.commitEntered.await() }
            main { vm.stop(); fixture.command("RELEASE") }; done(ChatStage.COMPLETED)
            check(vm.chat.value.messages.last().generated != null)
            main { fixture.command("COMPLETE") }
            java.io.File(targetContext.createDeviceProtectedStorageContext().filesDir, "d7f-seed-pid.txt")
                .writeText(android.os.Process.myPid().toString())
        }
        phase = "complete"
        main { fixture.report() }
        fixture.store.close()
    }
}
