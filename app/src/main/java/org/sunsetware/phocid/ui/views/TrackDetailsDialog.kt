package org.sunsetware.phocid.ui.views

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.ibm.icu.number.LocalizedNumberFormatter
import com.ibm.icu.number.Notation
import com.ibm.icu.number.NumberFormatter
import com.ibm.icu.number.Precision
import com.ibm.icu.text.DateFormat
import com.ibm.icu.util.MeasureUnit
import java.util.Date
import java.util.Locale
import kotlin.math.max
import org.sunsetware.phocid.Dialog
import org.sunsetware.phocid.MainViewModel
import org.sunsetware.phocid.R
import org.sunsetware.phocid.data.Track
import org.sunsetware.phocid.globals.Strings
import org.sunsetware.phocid.ui.components.DialogBase

@Stable
class TrackDetailsDialog(private val track: Track) : Dialog() {
    @Composable
    override fun Compose(viewModel: MainViewModel) {
        val scrollState = rememberScrollState()
        val content =
            remember(track) {
                fun formatterBase(): LocalizedNumberFormatter {
                    return NumberFormatter.withLocale(Locale.getDefault())
                        .notation(Notation.simple())
                }

                listOfNotNull(
                    Strings[R.string.track_details_title] to track.displayTitle,
                    Strings[R.string.track_details_artist] to track.displayArtist,
                    Strings[R.string.track_details_album] to track.displayAlbum,
                    Strings[R.string.track_details_album_artist] to track.displayAlbumArtist,
                    Strings[R.string.track_details_genre] to track.displayGenre,
                    Strings[R.string.track_details_year] to track.displayYear,
                    track.originalYear?.let {
                        Strings[R.string.track_details_original_release_year] to it.toString()
                    },
                    Strings[R.string.track_details_track_number] to track.displayNumber,
                    Strings[R.string.track_details_path] to track.path,
                    Strings[R.string.track_details_date_added] to
                        DateFormat.getInstance().format(Date(track.dateAdded * 1000)),
                    Strings[R.string.track_details_date_modified] to
                        DateFormat.getInstance().format(Date(track.version * 1000)),
                    Strings[R.string.track_details_size] to
                        formatterBase()
                            .unit(MeasureUnit.MEGABYTE)
                            .precision(Precision.maxFraction(1))
                            .format(track.size / 1048576.0)
                            .toString(),
                    Strings[R.string.track_details_format] to track.format,
                    Strings[R.string.track_details_sample_rate] to
                        formatterBase().unit(MeasureUnit.HERTZ).format(track.sampleRate).toString(),
                    Strings[R.string.track_details_bit_rate] to
                        formatterBase()
                            .unit(MeasureUnit.KILOBIT)
                            .perUnit(MeasureUnit.SECOND)
                            .precision(Precision.integer())
                            .format(track.bitRate / 1000.0)
                            .toString(),
                    Strings[R.string.track_details_bit_depth] to track.bitDepth.toString(),
                    track.unsyncedLyrics?.let {
                        Strings[R.string.track_details_unsynced_lyrics] to it
                    },
                    track.comment?.let { Strings[R.string.track_details_comment] to it },
                )
            }
        DialogBase(
            title = Strings[R.string.track_details],
            onConfirmOrDismiss = { viewModel.uiManager.closeDialog() },
        ) {
            Layout(
                modifier = Modifier.verticalScroll(scrollState).padding(horizontal = 24.dp),
                content = {
                    for ((key, value) in content) {
                        Text(key, color = MaterialTheme.colorScheme.primary)
                        Text(value)
                    }
                },
            ) { measurables, constraints ->
                val horizontalSpacing = 16.dp.roundToPx()
                val verticalSpacing = 4.dp.roundToPx()
                val pairs = measurables.chunked(2)
                val keys =
                    pairs.map {
                        it[0].measure(
                            Constraints(
                                maxWidth =
                                    ((constraints.maxWidth - horizontalSpacing) / 2).coerceAtLeast(
                                        0
                                    )
                            )
                        )
                    }
                val keyWidth = keys.maxOf { it.width }
                val valueWidth =
                    (constraints.maxWidth - keyWidth - horizontalSpacing).coerceAtLeast(0)
                val values = pairs.map { it[1].measure(Constraints(maxWidth = valueWidth)) }
                val rows =
                    keys.zip(values) { key, value ->
                        Triple(key, value, max(key.height, value.height))
                    }

                layout(
                    constraints.maxWidth,
                    rows.sumOf { it.third } + verticalSpacing * (rows.size - 1),
                ) {
                    var y = 0
                    for ((key, value, height) in rows) {
                        key.placeRelative(0, y)
                        value.placeRelative(keyWidth + horizontalSpacing, y)
                        y += height + verticalSpacing
                    }
                }
            }
        }
    }
}
