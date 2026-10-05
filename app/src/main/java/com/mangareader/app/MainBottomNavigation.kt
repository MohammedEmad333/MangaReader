package com.mangareader.app

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp

@Composable
internal fun MainBottomNavigation(
    currentTab: Int,
    onSelectTab: (Int) -> Unit,
) {
    val colors = NavigationBarItemDefaults.colors(
        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
        selectedTextColor = MaterialTheme.colorScheme.primary,
        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    val dockShape = RoundedCornerShape(24.dp)

    NavigationBar(
        modifier = Modifier
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .shadow(
                elevation = 10.dp,
                shape = dockShape,
                clip = false,
            )
            .clip(dockShape),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 0.dp,
    ) {
        NavigationBarItem(
            selected = currentTab == 0,
            onClick = { onSelectTab(0) },
            label = { NavLabel("Library") },
            icon = { Icon(Icons.Filled.Bookmark, contentDescription = null) },
            colors = colors,
        )
        NavigationBarItem(
            selected = currentTab == 1,
            onClick = { onSelectTab(1) },
            label = { NavLabel("Browse") },
            icon = { Icon(Icons.Filled.Search, contentDescription = null) },
            colors = colors,
        )
        NavigationBarItem(
            selected = currentTab == 2,
            onClick = { onSelectTab(2) },
            label = { NavLabel("History") },
            icon = { Icon(Icons.Filled.Refresh, contentDescription = null) },
            colors = colors,
        )
        NavigationBarItem(
            selected = currentTab == 3,
            onClick = { onSelectTab(3) },
            label = { NavLabel("Downloads") },
            icon = { Icon(Icons.Filled.Download, contentDescription = null) },
            colors = colors,
        )
        NavigationBarItem(
            selected = currentTab == 4,
            onClick = { onSelectTab(4) },
            label = { NavLabel("More") },
            icon = { Icon(Icons.Filled.MoreVert, contentDescription = null) },
            colors = colors,
        )
    }
}
