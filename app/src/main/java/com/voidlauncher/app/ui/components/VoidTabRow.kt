package com.voidlauncher.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

data class VoidTab(val label: String, val icon: ImageVector)

/** A row of pill-shaped tabs that animate between selected and idle states. */
@Composable
fun VoidTabRow(
    tabs: List<VoidTab>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tabs.forEachIndexed { index, tab ->
            VoidTabItem(
                tab = tab,
                selected = index == selectedIndex,
                onClick = { onSelect(index) },
            )
        }
    }
}

@Composable
private fun VoidTabItem(tab: VoidTab, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val background by animateColorAsState(
        targetValue = if (selected) colors.primary.copy(alpha = 0.22f) else Color.Transparent,
        label = "tab-background",
    )
    val content by animateColorAsState(
        targetValue = if (selected) colors.primary else colors.onSurfaceVariant,
        label = "tab-content",
    )
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.05f else 1f,
        label = "tab-scale",
    )

    Row(
        modifier = Modifier
            .scale(scale)
            .clip(RoundedCornerShape(percent = 50))
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = tab.icon,
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = tab.label,
            style = MaterialTheme.typography.labelLarge,
            color = content,
        )
    }
}
