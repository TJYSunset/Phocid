package org.sunsetware.phocid

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Dummy receiver for making Samsung recognize this as a music player */
class MediaButtonReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {}
}
