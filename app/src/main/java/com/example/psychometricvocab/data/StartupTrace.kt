package com.example.psychometricvocab.data

import android.os.Process
import android.os.SystemClock
import android.util.Log

/**
 * Startup timing for logcat (tag StartupTiming), so a slow or ANR-ing first launch can be read
 * from numbers instead of guessed at. Each line is "+<ms since the process started> <what>
 * [<thread>]"; [time] adds how long the block took. Costs one Log.i per call.
 */
object StartupTrace {
    private const val TAG = "StartupTiming"

    private fun sinceProcessStart() = SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime()

    fun mark(what: String) {
        Log.i(TAG, "+${sinceProcessStart()}ms $what [${Thread.currentThread().name}]")
    }

    fun <T> time(what: String, block: () -> T): T {
        val begin = SystemClock.elapsedRealtime()
        try {
            return block()
        } finally {
            Log.i(TAG, "+${sinceProcessStart()}ms $what took ${SystemClock.elapsedRealtime() - begin}ms [${Thread.currentThread().name}]")
        }
    }
}
