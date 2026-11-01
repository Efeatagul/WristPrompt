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
        val oldPhase = phase
        phase = "$oldPhase/action-$text"
        repeat(30) {
            var match = node(if (text == "Open image") "Open generated image" else text) ?: node(text)
            if (match != null) {
                while (match != null && !match.isClickable) match = match.parent
                val target = checkNotNull(match)
                uiAutomation.executeAndWaitForEvent({ check(target.performAction(AccessibilityNodeInfo.ACTION_CLICK)) }, { true }, 5000)
                idle(); phase = oldPhase; return
            }
            // Small real swipes do not page past a large-font row between observations.
            val width = targetContext.resources.displayMetrics.widthPixels.toFloat()
            val height = targetContext.resources.displayMetrics.heightPixels.toFloat()
            val now = SystemClock.uptimeMillis()
            for (step in 0..12) {
                val action = if (step == 0) MotionEvent.ACTION_DOWN else if (step == 12) MotionEvent.ACTION_UP else MotionEvent.ACTION_MOVE
                val event = MotionEvent.obtain(now, now + step * 20L, action, width / 2, height * (0.60f - step * 0.015f), 0)
                try { sendPointerSync(event) } finally { event.recycle() }
            }
            idle()
        }
        error("Action not found")
    }
    private fun idle() { waitForIdleSync(); uiAutomation.waitForIdle(300, 5000) }
    private fun viewport(): String {
        val root = uiAutomation.rootInActiveWindow
        fun find(n: AccessibilityNodeInfo?): String? {
            if (n == null) return null
            if (n.contentDescription?.toString() == "Image viewer") return n.stateDescription?.toString()
            return (0 until n.childCount).firstNotNullOfOrNull { find(n.getChild(it)) }
        }
        return checkNotNull(find(root))
    }
    private fun gesture(pinch: Boolean) {
        val start = SystemClock.uptimeMillis()
        val props = Array(2) { index -> MotionEvent.PointerProperties().apply { id = index; toolType = MotionEvent.TOOL_TYPE_FINGER } }
        fun event(action: Int, count: Int, x0: Float, y0: Float, x1: Float, y1: Float, step: Int) {
            val coords = arrayOf(MotionEvent.PointerCoords().apply { x = x0; y = y0; pressure = 1f; size = 1f },
                MotionEvent.PointerCoords().apply { x = x1; y = y1; pressure = 1f; size = 1f })
            val event = MotionEvent.obtain(start, start + step * 16L, action, count, props, coords, 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0)
            try { sendPointerSync(event) } finally { event.recycle() }
        }
        val width = targetContext.resources.displayMetrics.widthPixels.toFloat()
        val height = targetContext.resources.displayMetrics.heightPixels.toFloat()
        val cx = width / 2; val cy = height / 2
        event(MotionEvent.ACTION_DOWN, 1, cx - 25, cy, cx + 25, cy, 0)
        if (pinch) event(MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), 2, cx - 25, cy, cx + 25, cy, 1)
        repeat(12) { i ->
            val offset = if (pinch) 25 + i * 6f else 25f
            event(MotionEvent.ACTION_MOVE, if (pinch) 2 else 1, cx - offset + if (pinch) 0f else i * 4f, cy,
                cx + offset, cy, i + 2)
        }
        if (pinch) event(MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), 2, cx - 91, cy, cx + 91, cy, 14)
        event(MotionEvent.ACTION_UP, 1, if (pinch) cx - 91 else cx + 19, cy, cx + 91, cy, 15)
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
            check(vm.chat.value.messages.last().generated != null && fixture.requests == previous)
            check(vm.chat.value.draft.isEmpty())
            main { vm.refreshHistory() }; withTimeout(10000) { vm.history.first { !it.loading && it.entries.isNotEmpty() } }
            val reopened = CompletableDeferred<Unit>()
            main { vm.openChat(vm.history.value.entries.first().id) { reopened.complete(Unit) } }; reopened.await()
            click("Back to chat")
        }
        phase = "rendered-image-open"
        click("Open image"); check(viewport().startsWith("Zoom 1.0x")); capture("viewer-fit")
        phase = "pinch"
        gesture(true); check(!viewport().startsWith("Zoom 1.0x")); capture("viewer-zoom")
        phase = "pan"
        val zoomed = viewport(); gesture(false)
        phase = "pan/before=$zoomed/after=${viewport()}"
        check(viewport() != zoomed); capture("viewer-pan")
        phase = "close-reopen-back"
        click("Close"); click("Open image"); check(viewport().startsWith("Zoom 1.0x"))
        sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK); idle(); check(node("Close") == null)
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
        }
        phase = "complete"
        main { fixture.report() }
        fixture.store.close()
    }
}
