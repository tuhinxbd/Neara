package app.neara.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import app.neara.desktop.state.AppTab
import app.neara.desktop.state.NearaAppState
import app.neara.desktop.ui.screens.*
import app.neara.desktop.ui.theme.*

fun main() = application {
    val windowState = rememberWindowState(width = 1100.dp, height = 750.dp)

    Window(
        onCloseRequest = ::exitApplication,
        title = "Neara - Offline Nearby Mesh & Local Communication",
        state = windowState
    ) {
        NearaApp()
    }
}

@Composable
fun NearaApp() {
    val appState = remember { NearaAppState() }
    val currentTab by appState.currentTab.collectAsState()

    DisposableEffect(Unit) {
        onDispose {
            appState.close()
        }
    }

    NearaTheme {
        Row(modifier = Modifier.fillMaxSize().background(BgDark)) {
            // Left Sidebar
            Column(
                modifier = Modifier
                    .width(220.dp)
                    .fillMaxHeight()
                    .background(BgSidebar)
                    .border(1.dp, BorderSubtle)
                    .padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    // App Brand
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(AccentEmerald),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.CellWifi, contentDescription = null, tint = Color.Black, modifier = Modifier.size(22.dp))
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "Neara",
                            color = TextPrimary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(Modifier.height(28.dp))

                    // Navigation Tabs
                    NavItem(
                        icon = Icons.Default.Radar,
                        label = "Nearby",
                        isSelected = currentTab == AppTab.NEARBY,
                        onClick = { appState.setTab(AppTab.NEARBY) }
                    )
                    NavItem(
                        icon = Icons.Default.ChatBubbleOutline,
                        label = "Chats",
                        isSelected = currentTab == AppTab.CHATS,
                        onClick = { appState.setTab(AppTab.CHATS) }
                    )
                    NavItem(
                        icon = Icons.Default.Hub,
                        label = "Networks",
                        isSelected = currentTab == AppTab.NETWORKS,
                        onClick = { appState.setTab(AppTab.NETWORKS) }
                    )
                    NavItem(
                        icon = Icons.Default.FolderOpen,
                        label = "Files",
                        isSelected = currentTab == AppTab.FILES,
                        onClick = { appState.setTab(AppTab.FILES) }
                    )
                    NavItem(
                        icon = Icons.Default.PersonOutline,
                        label = "Profile",
                        isSelected = currentTab == AppTab.PROFILE,
                        onClick = { appState.setTab(AppTab.PROFILE) }
                    )
                    NavItem(
                        icon = Icons.Default.Settings,
                        label = "Settings",
                        isSelected = currentTab == AppTab.SETTINGS,
                        onClick = { appState.setTab(AppTab.SETTINGS) }
                    )
                }

                // Bottom Local Status Pill
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(BgCard)
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(AccentEmerald)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Off-grid Ready", color = TextSecondary, fontSize = 11.sp)
                }
            }

            // Main Content Screen
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                when (currentTab) {
                    AppTab.NEARBY -> NearbyScreen(appState)
                    AppTab.CHATS -> ChatScreen(appState)
                    AppTab.NETWORKS -> NetworksScreen(appState)
                    AppTab.FILES -> FilesScreen(appState)
                    AppTab.PROFILE -> ProfileScreen(appState)
                    AppTab.SETTINGS -> SettingsScreen(appState)
                }
            }
        }
    }
}

@Composable
fun NavItem(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) AccentEmerald.copy(alpha = 0.15f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (isSelected) AccentEmerald else TextSecondary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(12.dp))
        Text(
            label,
            color = if (isSelected) TextPrimary else TextSecondary,
            fontSize = 14.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
    Spacer(Modifier.height(4.dp))
}
