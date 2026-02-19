package org.sunsetware.phocid.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CoroutineScope

@Composable
fun LifecycleLaunchedEffect(
    vararg keys: Any?,
    minActiveState: Lifecycle.State = Lifecycle.State.RESUMED,
    block: suspend CoroutineScope.() -> Unit,
) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle, minActiveState, *keys) {
        lifecycle.repeatOnLifecycle(minActiveState) { block() }
    }
}
