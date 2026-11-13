package com.biaruuu.kiro.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.biaruuu.kiro.data.Anime
import com.biaruuu.kiro.ui.home.HomeScreen
import com.biaruuu.kiro.ui.search.SearchScreen
import com.biaruuu.kiro.ui.theme.KiroBackground
import com.biaruuu.kiro.ui.theme.KiroPurple
import kotlinx.coroutines.delay

private enum class Tab(val label: String, val icon: ImageVector) {
    Home("Home", Icons.Filled.Home),
    Search("Search", Icons.Filled.Search),
    Peek("Peek", Icons.Filled.PlayArrow),
    Library("Library", Icons.Filled.FavoriteBorder),
    Me("Me", Icons.Filled.Face),
}

@Composable
fun KiroApp() {
    var showSplash by rememberSaveable { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        if (showSplash) {
            delay(1200)
            showSplash = false
        }
    }

    if (showSplash) {
        SplashScreen()
    } else {
        KiroScaffold()
    }
}

@Composable
private fun SplashScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "KIRO",
            color = KiroPurple,
            fontSize = 64.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 4.sp,
        )
    }
}

@Composable
private fun KiroScaffold() {
    val context = LocalContext.current
    var tab by rememberSaveable { mutableStateOf(Tab.Home) }

    // Hoisted so scroll position survives switching tabs.
    val homeListState: LazyListState = rememberLazyListState()
    val searchGridState = rememberLazyGridState()

    val onAnimeClick: (Anime) -> Unit = {
        // Details page is planned for a later release.
        Toast.makeText(context, "${it.displayTitle}: details coming soon", Toast.LENGTH_SHORT).show()
    }

    Scaffold(
        containerColor = KiroBackground,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            NavigationBar(containerColor = KiroBackground) {
                Tab.entries.forEach { item ->
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = { tab = item },
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label, fontSize = 12.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = KiroPurple,
                            selectedTextColor = KiroPurple,
                            unselectedIconColor = Color.White,
                            unselectedTextColor = Color.White,
                            indicatorColor = Color.Transparent,
                        ),
                    )
                }
            }
        },
    ) { innerPadding ->
        // Only the bottom inset is applied here; each screen handles the status bar itself
        // so the Home hero can extend behind it.
        val contentModifier = Modifier
            .fillMaxSize()
            .padding(bottom = innerPadding.calculateBottomPadding())

        when (tab) {
            Tab.Home -> HomeScreen(
                listState = homeListState,
                onAnimeClick = onAnimeClick,
                onPlay = {
                    Toast.makeText(context, "Streaming is coming in a future update", Toast.LENGTH_SHORT).show()
                },
                onNotifications = {
                    Toast.makeText(context, "No new notifications", Toast.LENGTH_SHORT).show()
                },
                modifier = contentModifier,
            )

            Tab.Search -> SearchScreen(
                gridState = searchGridState,
                onAnimeClick = onAnimeClick,
                modifier = contentModifier,
            )

            Tab.Peek -> PlaceholderScreen("Peek", contentModifier)
            Tab.Library -> PlaceholderScreen("Library", contentModifier)
            Tab.Me -> PlaceholderScreen("Me", contentModifier)
        }
    }
}
