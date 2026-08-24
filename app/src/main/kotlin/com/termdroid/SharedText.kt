package com.termdroid

import android.content.Intent

/** Texto que otra app compartio hacia Termdroid, o null si el intent no trae ninguno. */
fun sharedTextOf(intent: Intent?): String? = when (intent?.action) {
    Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
    Intent.ACTION_PROCESS_TEXT -> intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
    else -> null
}?.takeIf { it.isNotBlank() }
