package ru.lsn03.voicemediacontroller.core.fakes

import ru.lsn03.voicemediacontroller.core.log.AppLogger

/**
 * In-memory [AppLogger] that records every call in insertion order.
 *
 * Tests can assert on [entries] to pin logging behaviour (level, tag,
 * message, cause). The returned list is an immutable snapshot — callers
 * can hold on to it without worrying about later mutation.
 *
 * Zero Android deps.
 */
class RecordingLogger : AppLogger {

    data class Entry(
        val level: Char,
        val tag: String,
        val message: String,
        val cause: Throwable?,
    )

    private val _entries: MutableList<Entry> = mutableListOf()

    val entries: List<Entry>
        get() = _entries.toList()

    override fun d(tag: String, message: String) {
        _entries.add(Entry('d', tag, message, cause = null))
    }

    override fun i(tag: String, message: String) {
        _entries.add(Entry('i', tag, message, cause = null))
    }

    override fun w(tag: String, message: String, cause: Throwable?) {
        _entries.add(Entry('w', tag, message, cause))
    }

    override fun e(tag: String, message: String, cause: Throwable?) {
        _entries.add(Entry('e', tag, message, cause))
    }

    fun clear() {
        _entries.clear()
    }
}
