package com.biaruuu.kiro.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.biaruuu.kiro.R
import com.biaruuu.kiro.data.Anime
import com.biaruuu.kiro.ui.components.KiroBottomBar
import com.biaruuu.kiro.ui.components.KiroSideRail
import com.biaruuu.kiro.ui.components.NavDestination
import com.biaruuu.kiro.ui.home.HomeScreen
import com.biaruuu.kiro.ui.search.SearchScreen
import com.biaruuu.kiro.ui.theme.KiroBackground
import com.biaruuu.kiro.ui.theme.KiroPurple
import com.biaruuu.kiro.ui.theme.LocalDimens
import com.biaruuu.kiro.ui.theme.WindowClass
import com.biaruuu.kiro.ui.theme.dimensFor
import kotlinx.coroutines.delay

private const val TAB_HOME = 0
private const val TAB_SEARCH = 1
private const val TAB_PEEK = 2
private const val TAB_LIBRARY = 3
private const val TAB_ME = 4

private val Destinations = listOf(
    NavDestination("Home", R.drawable.fluent_home_regular, R.drawable.fluent_home_filled),
    NavDestination("Search", R.drawable.fluent_search_regular, R.drawable.fluent_search_filled),
    NavDestination(
        "Peek",
        R.drawable.fluent_video_clip_multiple_regular,
        R.drawable.fluent_video_clip_multiple_filled,
        badge = true,
    ),
    NavDestination("Library", R.drawable.fluent_library_regular, R.drawable.fluent_library_filled),
    NavDestination("Me", R.drawable.fluent_emoji_regular, R.drawable.fluent_emoji_filled),
)

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
    var tab by rememberSaveable { mutableIntStateOf(TAB_HOME) }

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
                KiroBottomBar(
                    destinations = Destinations,
                    selectedIndex = tab,
                    onSelect = { tab = it },
                )
            }
        },
    ) { innerPadding ->
        Row(modifier = Modifier.fillMaxSize()) {
            if (useRail) {
                KiroSideRail(
                    destinations = Destinations,
                    selectedIndex = tab,
                    onSelect = { tab = it },
                    width = dims.navRailWidth,
                )
            }

            // Only the bottom inset is applied here; each screen handles the status bar itself
            // so the Home hero can extend behind it.
            val contentModifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(KiroBackground)
                .padding(bottom = innerPadding.calculateBottomPadding())

            when (tab) {
                TAB_HOME -> HomeScreen(
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

                TAB_SEARCH -> SearchScreen(
                    gridState = searchGridState,
                    onAnimeClick = onAnimeClick,
                    modifier = contentModifier,
                )

                TAB_PEEK -> PlaceholderScreen("Peek", contentModifier)
                TAB_LIBRARY -> PlaceholderScreen("Library", contentModifier)
                TAB_ME -> PlaceholderScreen("Me", contentModifier)
            }
        }
    }
}
