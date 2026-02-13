package org.sunsetware.phocid.service

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.datasource.BitmapUtil
import androidx.media3.datasource.DataSourceUtil
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import com.google.common.base.Suppliers
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.ListeningExecutorService
import com.google.common.util.concurrent.MoreExecutors
import java.util.concurrent.Executors
import org.sunsetware.phocid.FILE_PATH_KEY
import org.sunsetware.phocid.URI_KEY
import org.sunsetware.phocid.data.loadArtwork

@UnstableApi
class CustomizedBitmapLoader(private val context: Context) : BitmapLoader {
    private val listeningExecutorService = requireNotNull(DefaultExecutorService.get())
    private val dataSourceFactory = DefaultDataSource.Factory(context)

    override fun supportsMimeType(mimeType: String): Boolean {
        return Util.isBitmapFactorySupportedMimeType(mimeType)
    }

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> {
        return listeningExecutorService.submit<Bitmap> {
            BitmapUtil.decode(data, data.size, null, C.LENGTH_UNSET)
        }
    }

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> {
        return listeningExecutorService.submit<Bitmap> {
            val dataSource = dataSourceFactory.createDataSource()
            try {
                val dataSpec = DataSpec(uri)
                dataSource.open(dataSpec)
                val readData = DataSourceUtil.readToEnd(dataSource)
                BitmapUtil.decode(readData, readData.size, null, C.LENGTH_UNSET)
            } finally {
                dataSource.close()
            }
        }
    }

    override fun loadBitmapFromMetadata(metadata: MediaMetadata): ListenableFuture<Bitmap>? {
        val uri = metadata.extras?.getString(URI_KEY)?.toUri()
        return if (uri != null)
            listeningExecutorService.submit<Bitmap> {
                loadArtwork(context, uri, metadata.extras?.getString(FILE_PATH_KEY), true)
                    .let(::requireNotNull)
            }
        else {
            null
        }
    }
}

private val DefaultExecutorService =
    Suppliers.memoize<ListeningExecutorService?> { MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor()) }
