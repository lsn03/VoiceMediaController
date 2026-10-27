package ru.lsn03.voicemediacontroller.core.log

import android.util.Log

class AndroidLogger : AppLogger {
    override fun d(tag: String, message: String) {
        Log.d(tag, message)
    }

    override fun i(tag: String, message: String) {
        Log.i(tag, message)
    }

    override fun w(tag: String, message: String, cause: Throwable?) {
        if (cause != null) Log.w(tag, message, cause) else Log.w(tag, message)
    }

    override fun e(tag: String, message: String, cause: Throwable?) {
        if (cause != null) Log.e(tag, message, cause) else Log.e(tag, message)
    }
}
