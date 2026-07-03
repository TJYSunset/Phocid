@file:OptIn(ExperimentalMaterial3Api::class)

package org.sunsetware.phocid.ui.views

import android.app.Activity
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.commons.io.FilenameUtils
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.sunsetware.phocid.Dialog
import org.sunsetware.phocid.MainViewModel
import org.sunsetware.phocid.R
import org.sunsetware.phocid.data.Track
import org.sunsetware.phocid.data.loadArtwork
import org.sunsetware.phocid.globals.Strings
import org.sunsetware.phocid.ui.components.DialogBase
import org.sunsetware.phocid.utils.icuFormat

/** Supported audio formats for tag editing */
private val SUPPORTED_TAG_FORMATS = setOf(
    "mp3", "flac", "ogg", "m4a", "mp4", "wma", "wav", "aif", "aiff", "dsf"
)

/** Regex for sanitizing filename - removes invalid characters */
private val INVALID_FILENAME_CHARS_REGEX = Regex("[\\\\/:*?\"<>|]")

/** Delay in milliseconds before rescanning library after tag edit to allow file system sync */
private const val RESCAN_DELAY_MS = 500L

/** Result class for tag saving operation */
private sealed class EditTagsSaveResult {
    data object Success : EditTagsSaveResult()
    data class Error(val message: String) : EditTagsSaveResult()
}

@Stable
class EditTagsDialog(private val track: Track) : Dialog() {
    @Composable
    override fun Compose(viewModel: MainViewModel) {
        val context = LocalContext.current
        val coroutineScope = rememberCoroutineScope()
        val scrollState = rememberScrollState()
        val uiManager = viewModel.uiManager
        val configuration = LocalConfiguration.current
        val maxDialogHeight = (configuration.screenHeightDp * 0.7).dp

        // Editable fields
        var title by rememberSaveable { mutableStateOf(track.title ?: "") }
        var artist by rememberSaveable { mutableStateOf(track.artists.joinToString(", ")) }
        var album by rememberSaveable { mutableStateOf(track.album ?: "") }
        var albumArtist by rememberSaveable { mutableStateOf(track.albumArtists.joinToString(", ")) }
        var genre by rememberSaveable { mutableStateOf(track.genres.joinToString(", ")) }
        var year by rememberSaveable { mutableStateOf(track.year?.toString() ?: "") }
        var trackNumber by rememberSaveable { mutableStateOf(track.trackNumber?.toString() ?: "") }
        var discNumber by rememberSaveable { mutableStateOf(track.discNumber?.toString() ?: "") }
        var comment by rememberSaveable { mutableStateOf(track.comment ?: "") }
        var lyrics by rememberSaveable { mutableStateOf(track.unsyncedLyrics ?: "") }

        var isSaving by remember { mutableStateOf(false) }

        val originalArtwork = remember(track) {
            loadArtwork(context, track.id, track.path, sizeLimit = 256)
        }

        var selectedImageUri by rememberSaveable { mutableStateOf<Uri?>(null) }
        var selectedImageBitmap by remember { mutableStateOf<Bitmap?>(null) }
        var selectedImageMimeType by rememberSaveable { mutableStateOf<String?>(null) }

        LaunchedEffect(selectedImageUri) {
            selectedImageUri?.let { uri ->
                withContext(Dispatchers.IO) {
                    try {
                        val source = ImageDecoder.createSource(context.contentResolver, uri)
                        selectedImageBitmap = ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                        }
                        selectedImageMimeType = context.contentResolver.getType(uri)
                    } catch (e: Exception) {
                        Log.e("EditTagsDialog", "Failed to load selected image", e)
                    }
                }
            }
        }

        val imagePickerLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent()
        ) { uri: Uri? ->
            if (uri != null) {
                selectedImageUri = uri
            }
        }

        // Check if format is supported
        val isSupportedFormat = remember(track.path) {
            val extension = FilenameUtils.getExtension(track.path).lowercase()
            extension in SUPPORTED_TAG_FORMATS
        }

        // Determine if we need to rename the file (title changed and was used as display title)
        val originalTitle = remember { track.title }
        val needsRename = remember(title) {
            val newTitle = title.trim()
            newTitle.isNotEmpty() && newTitle != originalTitle && originalTitle != null
        }

        fun handleSaveResult(result: EditTagsSaveResult, newFilePath: String?) {
            isSaving = false
            when (result) {
                is EditTagsSaveResult.Success -> {
                    uiManager.toast(Strings[R.string.toast_track_tags_saved])
                    viewModel.launchDelayedLibraryScan(RESCAN_DELAY_MS)
                    uiManager.closeDialog()
                }
                is EditTagsSaveResult.Error -> {
                    uiManager.toast(
                        Strings[R.string.toast_track_tags_save_failed].icuFormat(result.message)
                    )
                }
            }
        }

        val writeResultLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartIntentSenderForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                coroutineScope.launch {
                    val imageBytes = selectedImageUri?.let { uri ->
                        withContext(Dispatchers.IO) {
                            try {
                                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                            } catch (e: Exception) {
                                Log.e("EditTagsDialog", "Failed to read image bytes", e)
                                null
                            }
                        }
                    }
                    val saveResult = withContext(Dispatchers.IO) {
                        saveTagsToFile(
                            track.path,
                            title,
                            artist,
                            album,
                            albumArtist,
                            genre,
                            year,
                            trackNumber,
                            discNumber,
                            comment,
                            lyrics,
                            imageBytes,
                            selectedImageMimeType
                        )
                    }

                    // Try to rename file if title changed
                    if (saveResult is EditTagsSaveResult.Success && needsRename) {
                        withContext(Dispatchers.IO) {
                            tryRenameFile(context, track, title.trim())
                        }
                    }

                    handleSaveResult(saveResult, null)
                }
            } else {
                isSaving = false
                uiManager.toast(
                    Strings[R.string.toast_track_tags_save_failed].icuFormat("Permission denied")
                )
            }
        }

        fun saveTagsWithPermission() {
            if (isSaving) return
            isSaving = true

            coroutineScope.launch {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    // Android 11+ uses MediaStore.createWriteRequest
                    val intentSender = MediaStore.createWriteRequest(
                        context.contentResolver,
                        listOf(track.uri)
                    ).intentSender
                    writeResultLauncher.launch(
                        IntentSenderRequest.Builder(intentSender).build()
                    )
                } else {
                    // For older Android versions, try direct write
                    val saveResult = withContext(Dispatchers.IO) {
                        val imageBytes = selectedImageUri?.let { uri ->
                            try {
                                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                            } catch (e: Exception) {
                                Log.e("EditTagsDialog", "Failed to read image bytes", e)
                                null
                            }
                        }
                        saveTagsToFile(
                            track.path,
                            title,
                            artist,
                            album,
                            albumArtist,
                            genre,
                            year,
                            trackNumber,
                            discNumber,
                            comment,
                            lyrics,
                            imageBytes,
                            selectedImageMimeType
                        )
                    }

                    // Try to rename file if title changed
                    if (saveResult is EditTagsSaveResult.Success && needsRename) {
                        withContext(Dispatchers.IO) {
                            tryRenameFile(context, track, title.trim())
                        }
                    }

                    handleSaveResult(saveResult, null)
                }
            }
        }

        DialogBase(
            title = Strings[R.string.track_edit_tags_title],
            onConfirm = { saveTagsWithPermission() },
            onDismiss = { uiManager.closeDialog() },
            confirmText = Strings[R.string.track_edit_tags_save],
            confirmEnabled = isSupportedFormat && !isSaving,
        ) {
            Column(
                modifier = Modifier
                    .heightIn(max = maxDialogHeight)
                    .verticalScroll(scrollState)
                    .padding(horizontal = 24.dp)
            ) {
                if (!isSupportedFormat) {
                    Text(
                        Strings[R.string.track_edit_tags_unsupported_format],
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                }

                if (isSupportedFormat) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = Strings[R.string.track_edit_tags_cover_art],
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val bitmapToDisplay = selectedImageBitmap ?: originalArtwork
                        if (bitmapToDisplay != null) {
                            Image(
                                bitmap = bitmapToDisplay.asImageBitmap(),
                                contentDescription = Strings[R.string.track_edit_tags_cover_art],
                                modifier = Modifier
                                    .size(80.dp)
                                    .padding(end = 16.dp)
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(80.dp)
                                    .padding(end = 16.dp)
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Image,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Button(
                            onClick = { imagePickerLauncher.launch("image/*") },
                            modifier = Modifier.wrapContentWidth()
                        ) {
                            Text(text = Strings[R.string.track_edit_tags_change_cover])
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(Strings[R.string.track_edit_tags_title_field]) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = isSupportedFormat,
                    singleLine = true,
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = artist,
                    onValueChange = { artist = it },
                    label = { Text(Strings[R.string.track_edit_tags_artist_field]) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = isSupportedFormat,
                    singleLine = true,
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = album,
                    onValueChange = { album = it },
                    label = { Text(Strings[R.string.track_edit_tags_album_field]) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = isSupportedFormat,
                    singleLine = true,
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = albumArtist,
                    onValueChange = { albumArtist = it },
                    label = { Text(Strings[R.string.track_edit_tags_album_artist_field]) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = isSupportedFormat,
                    singleLine = true,
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = genre,
                    onValueChange = { genre = it },
                    label = { Text(Strings[R.string.track_edit_tags_genre_field]) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = isSupportedFormat,
                    singleLine = true,
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = year,
                    onValueChange = { year = it },
                    label = { Text(Strings[R.string.track_edit_tags_year_field]) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = isSupportedFormat,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = trackNumber,
                    onValueChange = { trackNumber = it },
                    label = { Text(Strings[R.string.track_edit_tags_track_number_field]) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = isSupportedFormat,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = discNumber,
                    onValueChange = { discNumber = it },
                    label = { Text(Strings[R.string.track_edit_tags_disc_number_field]) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = isSupportedFormat,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = comment,
                    onValueChange = { comment = it },
                    label = { Text(Strings[R.string.track_edit_tags_comment_field]) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = isSupportedFormat,
                    minLines = 2,
                    maxLines = 3,
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = lyrics,
                    onValueChange = { lyrics = it },
                    label = { Text(Strings[R.string.track_edit_tags_lyrics_field]) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = isSupportedFormat,
                    minLines = 2,
                    maxLines = 4,
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

private fun saveTagsToFile(
    path: String,
    title: String,
    artist: String,
    album: String,
    albumArtist: String,
    genre: String,
    year: String,
    trackNumber: String,
    discNumber: String,
    comment: String,
    lyrics: String,
    imageBytes: ByteArray? = null,
    mimeType: String? = null
): EditTagsSaveResult {
    return try {
        val file = File(path)
        val audioFile = AudioFileIO.read(file)
        val tag = audioFile.tagOrCreateAndSetDefault

        // Helper function to safely set or delete a field
        fun setOrDeleteField(key: FieldKey, value: String) {
            if (value.isNotBlank()) {
                tag.setField(key, value.trim())
            } else {
                // Deleting a field that doesn't exist may throw in some formats
                // This is expected behavior and can be safely ignored
                try {
                    tag.deleteField(key)
                } catch (e: Exception) {
                    Log.d("EditTagsDialog", "Could not delete field $key: ${e.message}")
                }
            }
        }

        // Helper function to set or delete numeric fields with validation
        fun setOrDeleteNumericField(key: FieldKey, value: String) {
            val trimmedValue = value.trim()
            if (trimmedValue.isNotBlank()) {
                // Validate that the value is a valid integer
                val numericValue = trimmedValue.toIntOrNull()
                if (numericValue != null && numericValue >= 0) {
                    tag.setField(key, trimmedValue)
                } else if (trimmedValue.isNotEmpty()) {
                    // If it's not a valid number but not empty, still try to set it
                    // JAudioTagger will handle format-specific validation
                    tag.setField(key, trimmedValue)
                }
            } else {
                try {
                    tag.deleteField(key)
                } catch (e: Exception) {
                    Log.d("EditTagsDialog", "Could not delete field $key: ${e.message}")
                }
            }
        }

        setOrDeleteField(FieldKey.TITLE, title)
        setOrDeleteField(FieldKey.ARTIST, artist)
        setOrDeleteField(FieldKey.ALBUM, album)
        setOrDeleteField(FieldKey.ALBUM_ARTIST, albumArtist)
        setOrDeleteField(FieldKey.GENRE, genre)
        setOrDeleteNumericField(FieldKey.YEAR, year)
        setOrDeleteNumericField(FieldKey.TRACK, trackNumber)
        setOrDeleteNumericField(FieldKey.DISC_NO, discNumber)
        setOrDeleteField(FieldKey.COMMENT, comment)
        setOrDeleteField(FieldKey.LYRICS, lyrics)

        if (imageBytes != null) {
            try {
                tag.deleteArtworkField()
                val artwork = org.jaudiotagger.tag.images.ArtworkFactory.getNewArtwork()
                artwork.binaryData = imageBytes
                if (mimeType != null) {
                    artwork.mimeType = mimeType
                }
                artwork.pictureType = 3 // Front Cover
                tag.setField(artwork)
            } catch (e: Exception) {
                Log.e("EditTagsDialog", "Failed to set artwork in tag", e)
            }
        }

        audioFile.commit()
        EditTagsSaveResult.Success
    } catch (e: Exception) {
        Log.e("EditTagsDialog", "Error saving tags for $path", e)
        EditTagsSaveResult.Error(e.message ?: "Unknown error")
    }
}

private fun tryRenameFile(context: android.content.Context, track: Track, newTitle: String) {
    try {
        val extension = FilenameUtils.getExtension(track.path)
        // Sanitize the new title for use as filename
        val sanitizedTitle = newTitle.replace(INVALID_FILENAME_CHARS_REGEX, "_")

        // Skip if sanitized title is empty or only underscores
        if (sanitizedTitle.isBlank() || sanitizedTitle.all { it == '_' }) {
            Log.d("EditTagsDialog", "Skipping rename: sanitized title is invalid")
            return
        }

        val newFileName = "$sanitizedTitle.$extension"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // On Android 11+, use MediaStore to rename
            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, newFileName)
            }
            try {
                context.contentResolver.update(track.uri, values, null, null)
            } catch (e: Exception) {
                Log.d("EditTagsDialog", "Could not rename file via MediaStore: ${e.message}")
            }
        } else {
            // On older versions, try direct file rename
            val oldFile = File(track.path)
            val newFile = File(oldFile.parent, newFileName)
            if (oldFile.exists() && !newFile.exists()) {
                if (oldFile.renameTo(newFile)) {
                    Log.d("EditTagsDialog", "File renamed from ${track.path} to ${newFile.path}")
                }
            }
        }
    } catch (e: Exception) {
        Log.d("EditTagsDialog", "Could not rename file: ${e.message}")
    }
}
