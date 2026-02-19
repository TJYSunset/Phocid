package org.sunsetware.phocid.ui.components

import android.graphics.Bitmap
import android.util.Log
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.LinkedHashMap
import java.util.Locale
import java.util.zip.CRC32
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async


object ArtworkMemoryCache {
    private const val TAG = "phocid.ArtworkMemoryCache"
    private const val DEFAULT_MAX_ENTRIES = 50
    private const val MAX_PIXEL_BUFFER = 16 * 1024 * 1024L
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()
    private val inFlight = mutableMapOf<String, Deferred<Bitmap?>>()
    private val requestToContentKey = mutableMapOf<String, String>()
    private val contentToRequests = mutableMapOf<String, MutableSet<String>>()
    private val contentEntries =
        object : LinkedHashMap<String, Bitmap>(DEFAULT_MAX_ENTRIES, 0.75f, true) {}

    suspend fun getOrPut(requestKey: String, load: suspend () -> Bitmap?): Bitmap? {
        val existing = get(requestKey)
        if (existing != null) {
            Log.i(TAG, "artwork cache hit for: $requestKey")
            return existing
        }
        Log.i(TAG, "artwork cache miss for: $requestKey")

        val deferred =
            synchronized(lock) {
                inFlight[requestKey]
                    ?.also {
                        Log.i(TAG, "artwork cache wait for in flight: $requestKey")
                    }
                    ?: coroutineScope.async {
                            try {
                                val loaded = load() ?: return@async null
                                putLoaded(requestKey, loaded)
                            } finally {
                                synchronized(lock) { inFlight.remove(requestKey) }
                            }
                        }
                        .also {
                            inFlight[requestKey] = it
                            Log.i(TAG, "artwork cache started load for: $requestKey")
                        }
            }

        return deferred.await()
    }

    fun get(requestKey: String): Bitmap? {
        synchronized(lock) {
            val contentKey = requestToContentKey[requestKey] ?: return null
            val cached = contentEntries[contentKey]
            if (cached == null) {
                unlinkRequestLocked(requestKey, contentKey)
            }
            return cached
        }
    }

    fun clear() {
        synchronized(lock) {
            val inFlightSize = inFlight.size
            val requestSize = requestToContentKey.size
            val entrySize = contentEntries.size
            val totalBytesBeforeClear = totalCachedBytesLocked()
            inFlight.clear()
            requestToContentKey.clear()
            contentToRequests.clear()
            contentEntries.clear()
            Log.i(
                TAG,
                "cleared in memory, entries=$entrySize, request=$requestSize, inFlight=$inFlightSize, totalBeforeClear=${totalBytesBeforeClear.toMibString()}",
            )
        }
    }

    fun trimToSize(maxEntries: Int) {
        synchronized(lock) {
            trimLocked(maxEntries.coerceAtLeast(0))
        }
    }

    private fun putLoaded(requestKey: String, loaded: Bitmap): Bitmap {
        synchronized(lock) {
            val contentKey = loaded.contentKey()
            val cached = contentEntries[contentKey] ?: loaded
            contentEntries[contentKey] = cached

            val previousContentKey = requestToContentKey.put(requestKey, contentKey)
            if (previousContentKey != null && previousContentKey != contentKey) {
                unlinkRequestLocked(requestKey, previousContentKey)
            }

            contentToRequests.getOrPut(contentKey) { mutableSetOf() }.add(requestKey)
            trimLocked(DEFAULT_MAX_ENTRIES)
            return cached
        }
    }

    private fun trimLocked(maxEntries: Int) {
        while (contentEntries.size > maxEntries) {
            val oldestContentKey = contentEntries.entries.firstOrNull()?.key ?: break
            contentEntries.remove(oldestContentKey)
            val requestKeys = contentToRequests.remove(oldestContentKey)
            requestKeys?.forEach { requestToContentKey.remove(it) }
            Log.i(
                TAG,
                "evicted content key: $oldestContentKey to keep max entries: $maxEntries",
            )
            val totalBytes = totalCachedBytesLocked()
            Log.i(
                TAG,
                "total memory bytes=$totalBytes total memory mib=${totalBytes.toMibString()}",
            )
        }
    }

    private fun totalCachedBytesLocked(): Long {
        return contentEntries.values.fold(0L) { acc, bitmap ->
            acc +
                (bitmap.allocationByteCount.takeIf { it > 0 }?.toLong()
                    ?: bitmap.byteCount.takeIf { it > 0 }?.toLong()
                    ?: 0L)
        }
    }

    private fun Long.toMibString(): String {
        return String.format(Locale.ROOT, "%.2f", this / (1024.0 * 1024.0))
    }

    private fun unlinkRequestLocked(requestKey: String, contentKey: String) {
        requestToContentKey.remove(requestKey)
        contentToRequests[contentKey]?.let { keys ->
            keys.remove(requestKey)
            if (!keys.any()) {
                contentToRequests.remove(contentKey)
                contentEntries.remove(contentKey)
            }
        }
    }

    private fun Bitmap.contentKey(): String {
        val crc32 = CRC32()
        val metadata = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN)
        metadata.putInt(width)
        metadata.putInt(height)
        metadata.putInt(density)
        metadata.putInt(colorSpace?.hashCode() ?: 0)
        metadata.putInt(config?.ordinal ?: -1)
        metadata.putInt(hasAlpha().compareTo(false))
        crc32.update(metadata.array())

        try {
            val capacity =
                allocationByteCount.takeIf { it > 0 } ?: byteCount.takeIf { it > 0 } ?: 0
            if (capacity in 1..MAX_PIXEL_BUFFER) {
                val pixelBuffer = ByteBuffer.allocate(capacity)
                copyPixelsToBuffer(pixelBuffer)
                crc32.update(pixelBuffer.array(), 0, pixelBuffer.position())
                return crc32.value.toString(16)
            }
        } catch (@Suppress("TooGenericExceptionCaught") _: Exception) {
            // fallback below.
        }

        val output = ByteArrayOutputStream()
        if (compress(Bitmap.CompressFormat.PNG, 100, output)) {
            val bytes = output.toByteArray()
            crc32.update(bytes)
        }
        return crc32.value.toString(16)
    }
}
