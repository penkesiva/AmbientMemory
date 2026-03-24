package com.ambientmemory.timeline.diagnostics

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CaptureEventLog {
    private const val MAX_ITEMS = 24
    private val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private val _events = MutableStateFlow<List<String>>(emptyList())
    val events: StateFlow<List<String>> = _events

    fun add(message: String) {
        val line = "${timeFmt.format(Date())} · $message"
        val next = (_events.value + line).takeLast(MAX_ITEMS)
        _events.value = next
    }

    fun clear() {
        _events.value = emptyList()
    }
}
