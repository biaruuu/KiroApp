package com.biaruuu.kiro.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import com.biaruuu.kiro.ui.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.biaruuu.kiro.ui.theme.KiroBackground
import com.biaruuu.kiro.ui.theme.KiroAccent

/** Hairline between the bar and the content, same as in the design. */
private val DividerColor = Color(0xFF232328)

/** One destination. Idle uses the outline Fluent icon, selected uses the filled one in white, idle ones are muted gray. */
data class NavDestination(
    val label: String,
    @DrawableRes val icon: Int,
    @DrawableRes val iconSelected: Int,
    val badge: Boolean = false,
)

@Composable
private fun NavItem(
    destination: NavDestination,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tint = if (selected) Color.White else Color(0xFF6A6A70)
    Column(
        modifier = modifier
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.Tab,
                // Flat design: no ripple blob, the colour change is the feedback.
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(contentAlignment = Alignment.Center) {
            // Quiet pill behind the active icon.
            Box(
                modifier = Modifier
                    .width(48.dp)
                    .height(28.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (selected) Color.White.copy(alpha = 0.10f) else Color.Transparent),
            )
            Icon(
                painter = painterResource(if (selected) destination.iconSelected else destination.icon),
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(22.dp),
            )
            if (destination.badge) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = (-8).dp, y = 3.dp)
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(KiroAccent),
                )
            }
        }
        Text(
            text = destination.label,
            color = tint,
            fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            maxLines = 1,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

/** Phone bottom bar: a floating dark pill with a thin outline; the active tab gets a deeper pill. */
@Composable
fun KiroBottomBar(
    destinations: List<NavDestination>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    backdrop: Color = KiroBackground,
) {
    val ui = com.biaruuu.kiro.ui.theme.rememberUi()
    val shape = RoundedCornerShape(50)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(backdrop)
            .navigationBarsPadding()
            .padding(start = ui.dp(14f), end = ui.dp(14f), top = ui.dp(6f), bottom = ui.dp(8f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(ui.dp(62f))
                .clip(shape)
                .background(Color(0xFF151517))
                .border(1.dp, Color(0xFF2C2C30), shape)
                .padding(ui.dp(5f)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            destinations.forEachIndexed { index, destination ->
                val selected = index == selectedIndex
                val tint = if (selected) Color.White else Color(0xFFB8B8BE)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(50))
                        .background(if (selected) Color(0xFF060607) else Color.Transparent)
                        .selectable(
                            selected = selected,
                            onClick = { onSelect(index) },
                            role = Role.Tab,
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(if (selected) destination.iconSelected else destination.icon),
                            contentDescription = null,
                            tint = tint,
                            modifier = Modifier.size(ui.dp(21f)),
                        )
                        if (destination.badge) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .offset(x = 4.dp, y = (-2).dp)
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(KiroAccent),
                            )
                        }
                    }
                    Text(
                        text = destination.label,
                        color = tint,
                        fontSize = ui.sp(11f),
                        fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                        maxLines = 1,
                        modifier = Modifier.padding(top = ui.dp(3f)),
                    )
                }
            }
        }
    }
}

/** Tablet / landscape: the same items stacked on the left edge, with a divider on the right. */
@Composable
fun KiroSideRail(
    destinations: List<NavDestination>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    width: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxHeight().background(KiroBackground)) {
        Column(
            modifier = Modifier
                .width(width)
                .fillMaxHeight()
                .statusBarsPadding()
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            destinations.forEachIndexed { index, destination ->
                NavItem(
                    destination = destination,
                    selected = index == selectedIndex,
                    onClick = { onSelect(index) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp),
                )
            }
        }
        Box(
            modifier = Modifier
                .width(1.dp)
                .fillMaxSize()
                .background(DividerColor),
        )
    }
}
