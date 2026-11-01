package io.github.grassified.wristprompt.images

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Retained composer IDs only; not conversation replay or process-death draft storage. */
class ImageSelection {
    private val mutable = MutableStateFlow<List<String>>(emptyList())
    val selected = mutable.asStateFlow()
    fun replace(expected: List<String>, next: List<String>, available: Set<String>): Boolean {
        if (mutable.value != expected || next.size > ImageLimits.MAX_BATCH || next.distinct().size != next.size || next.any { !ImageLimits.validId(it) || it !in available }) return false
        mutable.value = next.toList()
        return true
    }
    fun prune(available: Set<String>) { mutable.value = mutable.value.filter { it in available } }
    fun clear() { mutable.value = emptyList() }
    override fun toString() = "ImageSelection([redacted])"
}
