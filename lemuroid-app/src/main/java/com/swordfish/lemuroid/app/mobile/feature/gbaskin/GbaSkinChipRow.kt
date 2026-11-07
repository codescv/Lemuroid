package com.swordfish.lemuroid.app.mobile.feature.gbaskin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.swordfish.lemuroid.R
import com.swordfish.touchinput.radial.skins.GbaSkin

/**
 * Horizontally scrollable row of GBA skin chips. Selecting a chip applies the skin immediately, so the console
 * deck and the buttons behind the edit touch controls dialog change live.
 */
@Composable
fun GbaSkinChipRow(
    selected: GbaSkin,
    onSkinSelected: (GbaSkin) -> Unit,
    modifier: Modifier = Modifier,
) {
    val skins = GbaSkin.entries
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = skins.indexOf(selected))

    LaunchedEffect(selected) {
        listState.animateScrollToItem(skins.indexOf(selected).coerceAtLeast(0))
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Palette,
                contentDescription = null,
            )
            Text(
                text = stringResource(R.string.game_menu_gba_skin),
                style = MaterialTheme.typography.titleSmall,
            )
        }
        LazyRow(
            state = listState,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 4.dp),
        ) {
            items(skins, key = { it.id }) { skin ->
                FilterChip(
                    selected = skin == selected,
                    onClick = { onSkinSelected(skin) },
                    label = { Text(text = stringResource(skin.titleRes)) },
                    leadingIcon = { GbaSkinColorDot(skin) },
                )
            }
        }
    }
}

/** Small swatch showing the shell gradient and the accent color of a skin. */
@Composable
fun GbaSkinColorDot(
    skin: GbaSkin,
    modifier: Modifier = Modifier,
) {
    val palette = skin.palette
    Box(
        modifier =
            modifier
                .size(18.dp)
                .background(PREVIEW_DOT_BACKDROP, CircleShape)
                .background(Brush.linearGradient(listOf(palette.shellTop, palette.shellBottom)), CircleShape)
                .border(1.5.dp, palette.accent, CircleShape),
    )
}

private val PREVIEW_DOT_BACKDROP = Color(0xFF2A2E38)
