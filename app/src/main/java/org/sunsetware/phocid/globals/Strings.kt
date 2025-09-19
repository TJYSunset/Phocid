package org.sunsetware.phocid.globals

import android.util.Log
import androidx.compose.runtime.Stable
import kotlin.time.Duration
import org.sunsetware.phocid.R
import org.sunsetware.phocid.utils.icuFormat

@Stable
interface StringSource {
    @Stable operator fun get(id: Int): String

    @Stable
    fun conjoin(strings: Iterable<String?>): String {
        // TODO: Require callers to pass in preferences as a dependency?
        return strings
            .filterNotNull()
            .joinToString(
                (if (GlobalData.initialized.get())
                    GlobalData.preferences.value.conjunctionSymbol.takeIf { it.isNotEmpty() }
                else null) ?: get(R.string.symbol_conjunction)
            )
    }

    @Stable
    fun conjoin(vararg strings: String?): String {
        return conjoin(strings.asIterable())
    }

    @Stable
    fun separate(strings: Iterable<String?>): String {
        return strings.filterNotNull().joinToString(get(R.string.symbol_separator))
    }

    @Stable
    fun separate(vararg strings: String?): String {
        return separate(strings.asIterable())
    }
}

/** This is only meant to be set by [org.sunsetware.phocid.MainApplication]! */
@Volatile
var Strings =
    object : StringSource {
        override fun get(id: Int): String {
            Log.e("Phocid", "Accessing string resource $id before initialization")
            return "<error>"
        }
    }

fun Duration.format(): String {
    return absoluteValue.toComponents { hours, minutes, seconds, _ ->
        if (isNegative()) {
            if (hours > 0)
                Strings[R.string.duration_negative_hours_minutes_seconds].icuFormat(
                    hours,
                    minutes,
                    seconds,
                )
            else Strings[R.string.duration_negative_minutes_seconds].icuFormat(minutes, seconds)
        } else {
            if (hours > 0)
                Strings[R.string.duration_hours_minutes_seconds].icuFormat(hours, minutes, seconds)
            else Strings[R.string.duration_minutes_seconds].icuFormat(minutes, seconds)
        }
    }
}
