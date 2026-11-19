/*
 * AA Installer - install Android Auto apps as Play Store apps
 * Copyright (C) 2026 j0p3s
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.github.j0p3s.aainstaller

import android.app.Application
import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Saves uncaught crashes so the next launch can show what went wrong. */
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { CrashLog.write(this, thread, error) }
            previous?.uncaughtException(thread, error)
        }
    }
}

object CrashLog {
    private fun file(ctx: Context) = File(ctx.filesDir, "last_crash.txt")

    fun write(ctx: Context, thread: Thread, error: Throwable) {
        val sw = StringWriter()
        error.printStackTrace(PrintWriter(sw))
        val head = buildString {
            appendLine("AA Installer ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            appendLine(SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(Date()) + " · thread ${thread.name}")
            appendLine()
        }
        file(ctx).writeText(head + sw.toString().take(16_000))
    }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("crash", Context.MODE_PRIVATE)

    /** A Java crash saved by the handler, or a native crash/ANR that Android recorded since the last check. */
    fun read(ctx: Context): String? {
        file(ctx).takeIf { it.exists() }?.readText()?.let { return it }
        return exitReason(ctx)
    }

    private fun exitReason(ctx: Context): String? {
        if (Build.VERSION.SDK_INT < 30) return null
        return try {
            val am = ctx.getSystemService(android.app.ActivityManager::class.java)
            val info = am.getHistoricalProcessExitReasons(ctx.packageName, 0, 5)
                .firstOrNull { it.reason == android.app.ApplicationExitInfo.REASON_CRASH_NATIVE || it.reason == android.app.ApplicationExitInfo.REASON_ANR }
                ?: return null
            val seen = prefs(ctx).getLong("seen", 0L)
            if (info.timestamp <= seen) return null
            prefs(ctx).edit().putLong("pending", info.timestamp).apply()
            val kind = if (info.reason == android.app.ApplicationExitInfo.REASON_ANR) "App not responding (ANR)" else "Native crash"
            buildString {
                appendLine("AA Installer ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                appendLine("${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
                appendLine(SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(Date(info.timestamp)))
                appendLine()
                appendLine("$kind: ${info.description ?: "no description"}")
                appendLine("Status ${info.status}, importance ${info.importance}")
                if (info.reason == android.app.ApplicationExitInfo.REASON_ANR) {
                    info.traceInputStream?.bufferedReader()?.use { appendLine(it.readText().take(12_000)) }
                } else {
                    appendLine("Full native backtrace: run 'adb logcat -b crash' or attach a bug report.")
                }
            }
        } catch (t: Throwable) {
            null
        }
    }

    fun clear(ctx: Context) {
        file(ctx).delete()
        val p = prefs(ctx)
        val pending = p.getLong("pending", 0L)
        if (pending > 0) p.edit().putLong("seen", pending).remove("pending").apply()
        else p.edit().putLong("seen", System.currentTimeMillis()).apply()
    }
}
