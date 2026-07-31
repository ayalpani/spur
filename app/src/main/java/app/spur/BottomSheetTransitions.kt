package app.spur

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Starts the outgoing Material sheet as soon as the incoming sheet starts moving.
 * Material uses the same motion spec for [SheetState.show] and [SheetState.hide],
 * so both animations then share their complete timeline.
 */
@OptIn(ExperimentalMaterial3Api::class)
internal fun CoroutineScope.swapBottomSheets(
    currentState: SheetState,
    nextState: SheetState,
    showNext: () -> Unit,
    hideCurrent: () -> Unit,
) {
    startBottomSheetTransition(
        nextState = nextState,
        showNext = showNext,
    ) {
        currentState.hide()
        hideCurrent()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
private fun CoroutineScope.startBottomSheetTransition(
    nextState: SheetState,
    showNext: () -> Unit,
    onIncomingStarted: suspend () -> Unit,
) {
    launch(start = CoroutineStart.UNDISPATCHED) {
        showNext()
        snapshotFlow { nextState.targetValue }
            .first { it != SheetValue.Hidden }
        onIncomingStarted()
    }
}
