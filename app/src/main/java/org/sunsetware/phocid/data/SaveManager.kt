@file:OptIn(ExperimentalSerializationApi::class)

package org.sunsetware.phocid.data

import android.content.Context
import android.util.Log
import java.io.File
import kotlin.reflect.KType
import kotlin.reflect.typeOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.withIndex
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.serializer

/** The initial value in flow will not be saved. */
class SaveManager<T : Any>(
    kType: KType,
    context: Context,
    coroutineScope: CoroutineScope,
    flow: Flow<T>,
    fileName: String,
    isCache: Boolean,
) : AutoCloseable {
    private val job =
        coroutineScope.launch {
            var lastSavedVersion = 0
            flow
                .withIndex()
                .conflate()
                .cancellable()
                .collect { (version, value) ->
                    if (lastSavedVersion < version) {
                        if (
                            withContext(Dispatchers.IO) {
                                saveCbor(kType, context, fileName, isCache, value)
                            }
                        ) {
                            lastSavedVersion = version
                        }
                    }
                }
        }

    override fun close() {
        job.cancel()
    }
}

inline fun <reified T : Any> SaveManager(
    context: Context,
    coroutineScope: CoroutineScope,
    flow: Flow<T>,
    fileName: String,
    isCache: Boolean,
): SaveManager<T> {
    return SaveManager(typeOf<T>(), context, coroutineScope, flow, fileName, isCache)
}

fun loadCbor(type: KType, context: Context, fileName: String, isCache: Boolean): Any? {
    val directory = if (isCache) context.cacheDir else context.filesDir
    val file = File(directory, fileName)
    val backupFile = File(directory, "$fileName.bak")
    return try {
        Cbor { ignoreUnknownKeys = true }
            .decodeFromByteArray(Cbor.serializersModule.serializer(type), file.readBytes())
    } catch (ex: Exception) {
        if (ex is SerializationException || ex is IllegalArgumentException) {
            Log.e("loadCbor", "$fileName is corrupted, loading backup")
            try {
                Cbor { ignoreUnknownKeys = true }
                    .decodeFromByteArray(
                        Cbor.serializersModule.serializer(type),
                        backupFile.readBytes(),
                    )
            } catch (ex2: Exception) {
                Log.e("loadCbor", "$fileName's backup is corrupted", ex2)
                null
            }
        } else {
            Log.e("loadCbor", "Can't load $fileName", ex)
            null
        }
    }
}

inline fun <reified T : Any> loadCbor(context: Context, fileName: String, isCache: Boolean): T? {
    return loadCbor(typeOf<T>(), context, fileName, isCache) as T?
}

fun saveCbor(
    type: KType,
    context: Context,
    fileName: String,
    isCache: Boolean,
    value: Any,
): Boolean {
    val directory = if (isCache) context.cacheDir else context.filesDir
    val file = File(directory, fileName)
    val backupFile = File(directory, "$fileName.bak")
    try {
        file.copyTo(backupFile, true)
    } catch (ex: Exception) {
        Log.e("saveCbor", "Can't create backup for $fileName", ex)
    }
    try {
        file.writeBytes(Cbor.encodeToByteArray(Cbor.serializersModule.serializer(type), value))
        return true
    } catch (ex: Exception) {
        Log.e("saveCbor", "Can't save $fileName", ex)
        return false
    }
}

inline fun <reified T : Any> saveCbor(
    context: Context,
    fileName: String,
    isCache: Boolean,
    value: T,
): Boolean {
    return saveCbor(typeOf<T>(), context, fileName, isCache, value)
}
