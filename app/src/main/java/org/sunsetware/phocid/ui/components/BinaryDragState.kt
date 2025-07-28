package org.sunsetware.phocid.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.round
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.sunsetware.phocid.ui.theme.emphasizedStandard

/**
 * Reminder: [androidx.compose.foundation.gestures.AnchoredDraggableState] and other official APIs
 * are unusable traps, don't bother "migrating".
 */
@Stable
class BinaryDragState(
    private val swipeThreshold: () -> Dp,
    /**
     * Must be a [CoroutineScope] from a composition context (i.e. not the view model scope).
     *
     * Reassign this property on activity recreation.
     */
    var coroutineScope: WeakReference<CoroutineScope> = WeakReference(null),
    initialValue: Float = 0f,
    private val onSnapToZero: () -> Unit = {},
    private val onSnapToOne: () -> Unit = {},
    private val reversed: Boolean = false,
    private val animationSpec: AnimationSpec<Float> = emphasizedStandard(),
) {
    private val _position = Animatable(initialValue)
    val position by _position.asState()

    private val _targetValue = MutableStateFlow(initialValue)
    val targetValue = _targetValue.asStateFlow()

    var length by mutableFloatStateOf(0f)

    @Volatile private var dragTotal = 0f
    @Volatile private var dragInitialPosition = initialValue

    fun onDragStart(lock: DragLock) {
        dragTotal = 0f
        dragInitialPosition = _position.value
        lock.isDragging.set(true)
        lock.isCancelling.set(false)
    }

    fun onDrag(lock: DragLock, delta: Float) {
        val delta = delta * (if (reversed) 1 else -1)
        dragTotal += delta
        coroutineScope.get()?.launch {
            if (lock.isDragging.get() && !lock.isCancelling.get()) {
                _position.snapTo(
                    (dragInitialPosition + dragTotal / length).coerceIn(0f, 1f).takeIf {
                        it.isFinite()
                    } ?: dragInitialPosition
                )
            }
        }
    }

    fun onDragEnd(lock: DragLock, density: Density) {
        lock.isDragging.set(false)
        val wasCancelling = lock.isCancelling.getAndSet(false)
        if (dragTotal == 0f || wasCancelling) return
        with(density) {
            val positionalThreshold = swipeThreshold().toPx().coerceAtMost(length / 2)
            if (dragTotal >= positionalThreshold) {
                animateTo(1f)
            } else if (dragTotal <= -positionalThreshold) {
                animateTo(0f)
            } else {
                val target = round(_position.value)
                animateTo(target)
            }
        }
        dragTotal = 0f
    }

    fun animateTo(value: Float) {
        coroutineScope.get()?.launch {
            _position.animateTo(value, animationSpec)
            if (value == 0f) onSnapToZero() else if (value == 1f) onSnapToOne()
        }
        _targetValue.update { value }
    }

    fun snapTo(value: Float) {
        coroutineScope.get()?.launch {
            _position.snapTo(value)
            if (value == 0f) onSnapToZero() else if (value == 1f) onSnapToOne()
        }
        _targetValue.update { value }
    }
}

/** This is used to prevent out-of-order execution of [BinaryDragState.onDragEnd] etc. */
@Stable
class DragLock {
    val isDragging = AtomicBoolean(false)
    val isCancelling = AtomicBoolean(false)
}
