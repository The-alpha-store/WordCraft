package com.wordcraft.android

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Catches any uncaught crash, saves a short report to private storage, and lets the home screen
 * offer it for sharing after the next launch, so a failure never disappears without a trace.
 */
object CrashLog {
    private fun file(context: Context) = File(context.filesDir, "crash-report.txt")

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
                val report = buildString {
                    appendLine("WordCraft crash report")
                    appendLine("Time: ${java.util.Date()}")
                    appendLine("Thread: ${thread.name}")
                    appendLine("Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}, Android ${android.os.Build.VERSION.RELEASE}")
                    appendLine()
                    append(trace.take(6000))
                }
                file(app).writeText(report)
            }
            previous?.uncaughtException(thread, error)
        }
    }

    fun read(context: Context): String? = runCatching { file(context).takeIf { it.exists() }?.readText() }.getOrNull()

    fun clear(context: Context) {
        runCatching { file(context).delete() }
    }
}
