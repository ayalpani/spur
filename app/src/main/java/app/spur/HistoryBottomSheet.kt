package app.spur

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val RecentTourLimit = 3

@Composable
internal fun HistoryBottomSheet(
    store: TourStore,
    revision: Long,
    onOpenTour: (Long) -> Unit,
) {
    var tours by remember { mutableStateOf(emptyList<Tour>()) }

    LaunchedEffect(revision) {
        tours = withContext(Dispatchers.IO) {
            store.tours().take(RecentTourLimit)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 18.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        BottomSheetHeader(title = "Letzte Touren")

        if (tours.isEmpty()) {
            Text(
                text = "Noch keine Touren",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 28.dp),
                color = Ink.copy(alpha = 0.62f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge,
            )
        } else {
            tours.forEach { tour ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenTour(tour.id) },
                    colors = CardDefaults.cardColors(containerColor = Sand),
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = tour.activity ?: formatDate(tour.startedAt),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                text = if (tour.activity == null) {
                                    formatTourTime(tour)
                                } else {
                                    "${formatDate(tour.startedAt)} · ${formatTourTime(tour)}"
                                },
                                modifier = Modifier.padding(top = 3.dp),
                                color = Ink.copy(alpha = 0.56f),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        Text(
                            text = formatMeters(tour.distanceMeters),
                            color = Moss,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}
