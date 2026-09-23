package com.mangareader.app

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.runtime.Composable

@Composable
internal fun MainBottomNavigation(
    currentTab: Int,
    onSelectTab: (Int) -> Unit
) {
    NavigationBar {
        NavigationBarItem(
            selected = currentTab == 0,
            onClick = { onSelectTab(0) },
            label = { NavLabel("Library") },
            icon = { Icon(Icons.Filled.Bookmark, contentDescription = null) }
        )
        NavigationBarItem(
            selected = currentTab == 1,
            onClick = { onSelectTab(1) },
            label = { NavLabel("Browse") },
            icon = { Icon(Icons.Filled.Search, contentDescription = null) }
        )
        NavigationBarItem(
            selected = currentTab == 2,
            onClick = { onSelectTab(2) },
            label = { NavLabel("History") },
            icon = { Icon(Icons.Filled.Refresh, contentDescription = null) }
        )
        NavigationBarItem(
            selected = currentTab == 3,
            onClick = { onSelectTab(3) },
            label = { NavLabel("Downloads") },
            icon = { Icon(Icons.Filled.Download, contentDescription = null) }
        )
        NavigationBarItem(
            selected = currentTab == 4,
            onClick = { onSelectTab(4) },
            label = { NavLabel("More") },
            icon = { Icon(Icons.Filled.MoreVert, contentDescription = null) }
        )
    }
}
