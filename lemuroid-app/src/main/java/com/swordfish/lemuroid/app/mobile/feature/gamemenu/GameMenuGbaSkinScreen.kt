package com.swordfish.lemuroid.app.mobile.feature.gamemenu

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.mobile.feature.gbaskin.GbaSkinPreview
import com.swordfish.lemuroid.app.mobile.feature.gbaskin.descriptionRes
import com.swordfish.lemuroid.app.mobile.feature.gbaskin.titleRes
import com.swordfish.touchinput.radial.skins.GbaSkin

/** Gallery of the GBA skins. Tapping a card returns the skin to the game, which resumes wearing it. */
@Composable
fun GameMenuGbaSkinScreen(
    selectedSkin: GbaSkin,
    onSkinSelected: (GbaSkin) -> Unit,
) {
    val skins = GbaSkin.entries
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = skins.indexOf(selectedSkin).coerceAtLeast(0))

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(skins, key = { it.id }) { skin ->
            GbaSkinCard(
                skin = skin,
                selected = skin == selectedSkin,
                onClick = { onSkinSelected(skin) },
            )
        }
    }
}

@Composable
private fun GbaSkinCard(
    skin: GbaSkin,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    OutlinedCard(
        onClick = onClick,
        modifier =
            Modifier
                .fillMaxWidth()
                .semantics { this.selected = selected },
        border =
            if (selected) {
                BorderStroke(2.dp, colors.primary)
            } else {
                BorderStroke(1.dp, colors.outlineVariant)
            },
        colors =
            CardDefaults.outlinedCardColors(
                containerColor = if (selected) colors.primaryContainer.copy(alpha = 0.35f) else colors.surface,
            ),
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            GbaSkinPreview(
                skin = skin,
                modifier =
                    Modifier
                        .width(112.dp)
                        .height(68.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = stringResource(skin.titleRes),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(skin.descriptionRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (selected) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = stringResource(R.string.gba_skin_selected),
                    tint = colors.primary,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}
