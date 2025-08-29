package org.sunsetware.phocid.utils

import kotlin.math.roundToInt

fun Int.wrap(other: Int, repeat: Boolean): Int? {
    return if (other > 0) {
        if (repeat) mod(other) else this.takeIf { it in 0..<other }
    } else null
}

fun Float.roundToIntOrZero(): Int {
    return if (isNaN()) 0 else roundToInt()
}

fun Int.coerceInOrMin(min: Int, max: Int): Int {
    return if (this < min) min else if (this > max) max else this
}

fun Long.coerceInOrMin(min: Long, max: Long): Long {
    return if (this < min) min else if (this > max) max else this
}

fun Float.coerceInOrMin(min: Float, max: Float): Float {
    return if (this < min) min else if (this > max) max else this
}
