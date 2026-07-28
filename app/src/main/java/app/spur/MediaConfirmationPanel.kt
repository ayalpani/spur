package app.spur

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun ColumnScope.AnimatedMediaConfirmationPanel(
    onDiscard: () -> Unit,
    onAccept: () -> Unit,
    metadata: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val visibility = remember {
        MutableTransitionState(false).apply { targetState = true }
    }
    AnimatedVisibility(
        visibleState = visibility,
        enter = expandVertically(
            animationSpec = tween(durationMillis = 420),
            expandFrom = Alignment.Bottom,
        ) + slideInVertically(
            animationSpec = tween(durationMillis = 420),
            initialOffsetY = { it },
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                color = SheetBackground,
                shadowElevation = 16.dp,
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    metadata?.invoke(this)
                    SpurSecondaryButton(
                        label = "Verwerfen",
                        onClick = onDiscard,
                    )
                    SpurPrimaryButton(
                        label = "Bestätigen",
                        onClick = onAccept,
                    )
                }
            }
        }
    }
}
