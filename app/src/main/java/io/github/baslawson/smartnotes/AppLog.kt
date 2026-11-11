package io.github.baslawson.smartnotes

import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * The debug log, kept only on the phone: app.log, moved to app.log.1 once it passes [maxBytes] (so at most about
 * twice that is kept). Settings → Debug log exports it as a file or copies it. Plain JVM code, so it can be unit tested.
 */
class LogFile(private val dir: File, private val maxBytes: Long = 512 * 1024) {
    private val current get() = File(dir, "app.log")
    private val previous get() = File(dir, "app.log.1")

    @Synchronized
    fun append(line: String) {
        dir.mkdirs()
        if (current.length() > maxBytes) {
            previous.delete()
            current.renameTo(previous)
        }
        current.appendText(line + "\n")
    }

    /** Everything kept, oldest first. */
    @Synchronized
    fun read(): String = listOf(previous, current).filter { it.isFile }.joinToString("") { it.readText() }

    @Synchronized
    fun clear() {
        previous.delete()
        current.delete()
    }
}

object AppLog {
    private val TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
    @Volatile private var file: LogFile? = null

    fun init(dir: File) { file = LogFile(dir) }

    fun i(tag: String, message: String) = write("I", tag, message, null)
    fun w(tag: String, message: String, error: Throwable? = null) = write("W", tag, message, error)
    fun e(tag: String, message: String, error: Throwable? = null) = write("E", tag, message, error)

    fun read(): String = file?.read().orEmpty()
    fun clear() = file?.clear()

    private fun write(level: String, tag: String, message: String, error: Throwable?) {
        when (level) {
            "E" -> Log.e("SmartNotes/$tag", message, error)
            "W" -> Log.w("SmartNotes/$tag", message, error)
            else -> Log.i("SmartNotes/$tag", message)
        }
        val trace = error?.let { e -> "\n" + StringWriter().also { e.printStackTrace(PrintWriter(it)) }.toString().trimEnd() }.orEmpty()
        // A log that can't be written must never take the app down with it.
        runCatching { file?.append("${TIME.format(LocalDateTime.now())} $level/$tag: $message$trace") }
    }
}
