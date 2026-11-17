package com.biaruuu.kiro.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.toMutableStateList
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.biaruuu.kiro.R
import com.biaruuu.kiro.data.Anime
import com.biaruuu.kiro.ui.auth.AuthGate
import com.biaruuu.kiro.ui.me.MeScreen
import com.biaruuu.kiro.ui.me.SettingsPage
import com.biaruuu.kiro.ui.components.KiroBottomBar
import com.biaruuu.kiro.ui.components.KiroSideRail
import com.biaruuu.kiro.ui.components.NavDestination
import com.biaruuu.kiro.ui.detail.DetailScreen
import com.biaruuu.kiro.ui.detail.MoreDetailScreen
import com.biaruuu.kiro.ui.home.HomeScreen
import com.biaruuu.kiro.ui.library.LibraryScreen
import com.biaruuu.kiro.ui.player.PlayerScreen
import com.biaruuu.kiro.ui.search.SearchScreen
import com.biaruuu.kiro.ui.theme.KiroBackground
import com.biaruuu.kiro.ui.theme.KiroPurple
import com.biaruuu.kiro.ui.theme.LocalDimens
import com.biaruuu.kiro.ui.theme.WindowClass
import com.biaruuu.kiro.ui.theme.dimensFor
import kotlinx.coroutines.delay

private const val TAB_HOME = 0
private const val TAB_SEARCH = 1
private const val TAB_LIBRARY = 2
private const val TAB_ME = 3

private val Destinations = listOf(
    NavDestination("Home", R.drawable.fluent_home_regular, R.drawable.fluent_home_filled),
    NavDestination("Search", R.drawable.fluent_search_regular, R.drawable.fluent_search_filled),
    NavDestination("Your Library", R.drawable.fluent_inbox_regular, R.drawable.fluent_inbox_regular),
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

        if (showSplash) SplashScreen() else AuthGate { KiroScaffold() }
    }
}

/** Just the logo, centered on the dark background, fading in (like Laftel's launch screen). */
@Composable
private fun SplashScreen() {
    val alpha = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(Unit) { alpha.animateTo(1f, androidx.compose.animation.core.tween(450)) }
    Box(
        modifier = Modifier.fillMaxSize().background(KiroBackground),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.Image(
            painter = androidx.compose.ui.res.painterResource(R.drawable.kiro_logo),
            contentDescription = "Kiro",
            modifier = Modifier.width(192.dp).graphicsLayer { this.alpha = alpha.value },
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

    // Detail pages stack on top of the tabs: "d:ID" is a details page, "m:ID" its full Detail page.
    val backStack = rememberSaveable(
        saver = listSaver<MutableList<String>, String>(
            save = { it.toList() },
            restore = { it.toMutableStateList() },
        ),
    ) { mutableStateListOf<String>() }

    val onAnimeClick: (Anime) -> Unit = { backStack.add("d:${it.id}") }
    BackHandler(enabled = backStack.isNotEmpty()) { backStack.removeAt(backStack.lastIndex) }

    // After an update: show what changed once. From the update notification: show the new version.
    LaunchedEffect(Unit) {
        if (com.biaruuu.kiro.data.UpdateChecker.consumeJustUpdated(context)) backStack.add("s:whatsnew-current")
    }
    // Also check right now (not only in the background), so the notification never depends on the phone's job scheduler.
    // While Kiro is on screen it asks once a minute, so a release published right now reaches every
    // open app within a minute (a repeat check costs almost nothing, see UpdateChecker).
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED) {
            while (true) {
                com.biaruuu.kiro.data.UpdateChecker.latest()?.let {
                    com.biaruuu.kiro.data.UpdateChecker.onLatest(context, it)
                    com.biaruuu.kiro.data.UpdateWorker.notifyIfNew(context, it)
                }
                kotlinx.coroutines.delay(60_000L)
            }
        }
    }
    val releaseUnread by com.biaruuu.kiro.data.UpdateChecker.unread.collectAsState()
    val announcementUnread by com.biaruuu.kiro.data.Announcements.unread.collectAsState()
    val hasUnread = releaseUnread || announcementUnread
    LaunchedEffect(Unit) {
        com.biaruuu.kiro.data.Announcements.start(context)
        com.biaruuu.kiro.data.Designs.start(context)
    }

    // Presence: while a signed-in user has Kiro open, the developer's admin panel sees them online
    // and what they watch. It is refreshed every 30 seconds and when the title changes.
    val authState by com.biaruuu.kiro.data.auth.AuthRepository.state.collectAsState()
    val signedInUid = (authState as? com.biaruuu.kiro.data.auth.AuthState.SignedIn)?.uid
    val watchingNow by com.biaruuu.kiro.data.Presence.watching.collectAsState()
    val myName by com.biaruuu.kiro.data.ProfileRepository.profile.collectAsState()
    LaunchedEffect(lifecycleOwner, signedInUid) {
        if (signedInUid.isNullOrEmpty()) return@LaunchedEffect
        lifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED) {
            try {
                while (true) {
                    com.biaruuu.kiro.data.Presence.write(signedInUid, true)
                    kotlinx.coroutines.delay(30_000L)
                }
            } finally {
                com.biaruuu.kiro.data.Presence.write(signedInUid, false)
            }
        }
    }
    LaunchedEffect(authState, myName.username, myName.avatar, myName.bio, myName.decoration, myName.nameFont) {
        if (com.biaruuu.kiro.data.Admin.isAdmin(authState) && myName.username.isNotBlank()) {
            com.google.firebase.firestore.FirebaseFirestore.getInstance().collection("meta").document("admin").set(
                hashMapOf(
                    "username" to myName.username, "bio" to myName.bio, "avatar" to myName.avatar,
                    "email" to com.biaruuu.kiro.data.Admin.EMAIL,
                    "decoration" to myName.decoration, "nameFont" to myName.nameFont,
                ),
            )
        }
    }
    LaunchedEffect(watchingNow, myName.username) {
        if (!signedInUid.isNullOrEmpty()) com.biaruuu.kiro.data.Presence.write(signedInUid, true)
    }
    val updateRequest by com.biaruuu.kiro.data.UpdateChecker.request.collectAsState()
    LaunchedEffect(updateRequest) {
        if (updateRequest != null) {
            backStack.add(if (updateRequest == "notifications") "s:notifications" else "s:whatsnew-$updateRequest")
            com.biaruuu.kiro.data.UpdateChecker.request.value = null
        }
    }
    // Android 13+ needs permission before the update notification can be shown; ask once.
    val askNotifications = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { }
    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences("kiro_update", android.content.Context.MODE_PRIVATE)
        if (android.os.Build.VERSION.SDK_INT >= 33 && !prefs.getBoolean("asked_notifications", false) &&
            androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            prefs.edit().putBoolean("asked_notifications", true).apply()
            askNotifications.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
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
                    onPlay = onAnimeClick,
                    onContinue = { id, ep -> backStack.add("p:$id:$ep") },
                    onNotifications = { backStack.add("s:notifications") },
                    hasUnread = hasUnread,
                    modifier = contentModifier,
                )

                TAB_SEARCH -> SearchScreen(
                    gridState = searchGridState,
                    onAnimeClick = onAnimeClick,
                    modifier = contentModifier,
                )

                TAB_LIBRARY -> LibraryScreen(
                    onOpenAnime = { backStack.add("d:$it") },
                    onContinue = { id, ep -> backStack.add("p:$id:$ep") },
                    refreshKey = backStack.size,
                    onCustomize = { backStack.add("s:customize") },
                    modifier = contentModifier,
                )
                TAB_ME -> MeScreen(onOpen = { backStack.add("s:$it") }, hasUnread = hasUnread, modifier = contentModifier)
            }
        }
    }

    val top = backStack.lastOrNull()
    if (top != null) {
        val parts = top.split(":")
        val id = parts.getOrNull(1)?.toIntOrNull()
        if (parts[0] == "s") {
            SettingsPage(
                page = parts.getOrNull(1) ?: "",
                onBack = { backStack.removeAt(backStack.lastIndex) },
                onOpen = { backStack.add("s:$it") },
                modifier = Modifier.fillMaxSize(),
            )
        } else if (id != null) {
            val overlay = Modifier.fillMaxSize().background(KiroBackground)
            val pop = { backStack.removeAt(backStack.lastIndex) }
            when (parts[0]) {
                "m" -> MoreDetailScreen(animeId = id, onBack = { pop() }, modifier = overlay)
                "p" -> PlayerScreen(
                    animeId = id,
                    startEpisode = parts.getOrNull(2)?.toIntOrNull() ?: 1,
                    onClose = { pop() },
                    modifier = overlay,
                )
                else -> DetailScreen(
                    animeId = id,
                    onBack = { pop() },
                    onOpenAnime = { backStack.add("d:$it") },
                    onMore = { backStack.add("m:$it") },
                    onPlay = { anime, episode -> backStack.add("p:$anime:$episode") },
                    modifier = overlay,
                )
            }
        }
    }
    }
}
