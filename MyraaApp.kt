package com.myraa.app

import android.app.Application

/** Writes a short crash note so the next launch can show what went wrong. */
class MyraaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val old = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                java.io.File(filesDir, "crash.txt").writeText(e.toString() + "\n" + e.stackTrace.take(8).joinToString("\n"))
            } catch (x: Exception) { }
            old?.uncaughtException(t, e)
        }
    }
}
