package com.biaruuu.kiro.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.biaruuu.kiro.R
import com.biaruuu.kiro.data.Anime
import com.biaruuu.kiro.ui.home.HomeScreen
import com.biaruuu.kiro.ui.search.SearchScreen
import com.biaruuu.kiro.ui.theme.KiroBackground
import com.biaruuu.kiro.ui.theme.KiroPurple
import com.biaruuu.kiro.ui.theme.LocalDimens
import com.biaruuu.kiro.ui.theme.WindowClass
import com.biaruuu.kiro.ui.theme.dimensFor
import kotlinx.coroutines.delay

private enum class Tab(val label: String, val icon: Int) {
    Home("Home", R.drawable.ic_home),
    Search("Search", R.drawable.ic_search),
    Peek("Peek", R.drawable.ic_peek),
    Library("Library", R.drawable.ic_library),
    Me("Me", R.drawable.ic_me),
}

@Composable
fun KiroApp() {
    // Sizes are recomputed whenever the window changes (rotation, split-screen, foldables).
    val widthDp = LocalConfiguration.current.screenWidthDp
    val dims = remember(widthDp) { dimensFor(widthDp) }

    CompositionLocalProvider(LocalDimens provides dims) {
        var showSplash by rememberSaveable { mutableStateOf(true) }
        LaunchedEffect(Unit) {
            if (showSplash) {
                delay(1200)
                showSplash = false
            }
        }

        if (showSplash) SplashScreen() else KiroScaffold()
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
    val dims = LocalDimens.current
    val useRail = dims.windowClass != WindowClass.Compact
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
            if (!useRail) {
                NavigationBar(containerColor = KiroBackground) {
                    Tab.entries.forEach { item ->
                        NavigationBarItem(
                            selected = tab == item,
                            onClick = { tab = item },
                            icon = {
                                Icon(
                                    painter = painterResource(item.icon),
                                    contentDescription = item.label,
                                    modifier = Modifier.size(26.dp),
                                )
                            },
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
            }
        },
    ) { innerPadding ->
        Row(modifier = Modifier.fillMaxSize()) {
            if (useRail) {
                NavigationRail(
                    containerColor = KiroBackground,
                    modifier = Modifier.fillMaxHeight(),
                ) {
                    Tab.entries.forEach { item ->
                        NavigationRailItem(
                            selected = tab == item,
                            onClick = { tab = item },
                            icon = {
                                Icon(
                                    painter = painterResource(item.icon),
                                    contentDescription = item.label,
                                    modifier = Modifier.size(26.dp),
                                )
                            },
                            label = { Text(item.label, fontSize = 12.sp) },
                            alwaysShowLabel = true,
                            colors = NavigationRailItemDefaults.colors(
                                selectedIconColor = KiroPurple,
                                selectedTextColor = KiroPurple,
                                unselectedIconColor = Color.White,
                                unselectedTextColor = Color.White,
                                indicatorColor = Color.Transparent,
                            ),
                        )
                    }
                }
            }

            // Only the bottom inset is applied here; each screen handles the status bar itself
            // so the Home hero can extend behind it.
            val contentModifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(KiroBackground)
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
}
