package app.spur

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun MediaConfirmationPanel(
    onDiscard: () -> Unit,
    onAccept: () -> Unit,
    metadata: (@Composable ColumnScope.() -> Unit)? = null,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        color = SheetBackground,
        shadowElevation = 16.dp,
    ) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(horizontal = 18.dp, vertical = 18.dp),
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
