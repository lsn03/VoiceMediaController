package ru.lsn03.voicemediacontroller.core.log

interface AppLogger {
    fun d(tag: String, message: String)
    fun i(tag: String, message: String)
    fun w(tag: String, message: String, cause: Throwable? = null)
    fun e(tag: String, message: String, cause: Throwable? = null)
}
