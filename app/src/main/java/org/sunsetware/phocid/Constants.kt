package org.sunsetware.phocid

import android.os.Build
import androidx.compose.ui.unit.dp

val READ_PERMISSION =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        android.Manifest.permission.READ_MEDIA_AUDIO
    else android.Manifest.permission.READ_EXTERNAL_STORAGE

const val PREFERENCES_FILE_NAME = "preferences"
const val PLAYLISTS_FILE_NAME = "playlists"
const val TRACK_INDEX_FILE_NAME = "trackIndex"
const val PLAYER_STATE_FILE_NAME = "playerState"
const val PLAY_HISTORY_FILE_NAME = "playHistory"
const val UI_STATE_FILE_NAME = "uiState"

const val UNSHUFFLED_INDEX_KEY = "originalIndex"
const val SET_TIMER_COMMAND = "setTimer"
const val EXTERNAL_REPEAT_COMMAND = "repeat"
const val EXTERNAL_SHUFFLE_COMMAND = "shuffle"
const val EXTERNAL_FAVORITE_COMMAND = "favorite"
const val TIMER_TARGET_KEY = "timerTarget"
const val TIMER_FINISH_LAST_TRACK_KEY = "timerFinishLastTrack"
const val FILE_PATH_KEY = "filePath"
/**
 * Used instead of [androidx.media3.common.MediaMetadata.artworkUri]; Setting the latter would break
 * Android Auto
 */
const val URI_KEY = "bitmapUri"
const val AUDIO_SESSION_ID_KEY = "audioSessionId"

const val ROOT_MEDIA_ID = "root"

const val SHORTCUT_CONTINUE = "org.sunsetware.phocid.CONTINUE"
const val SHORTCUT_SHUFFLE = "org.sunsetware.phocid.SHUFFLE"
const val SHORTCUT_PLAYLIST = "org.sunsetware.phocid.PLAYLIST"
const val SHORTCUT_PLAYLIST_EXTRA_KEY = "playlist"

const val UNKNOWN = "<unknown>"

val DEFAULT_SWIPE_THRESHOLD = 32.dp
const val TNUM = "tnum"

const val DEPENDENCY_INFOS_FILE_NAME = "open_source_licenses.json"
const val LICENSE_MAPPINGS_FILE_NAME = "LicenseMappings.json"
