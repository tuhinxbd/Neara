package app.neara.android

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import app.neara.android.call.*
import app.neara.android.ui.*

class MainActivity : ComponentActivity() {

    private lateinit var appState: AndroidAppState

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        // Permissions handled
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appState = AndroidAppState(applicationContext)

        requestRequiredPermissions()

        setContent {
            var showSplash by remember { mutableStateOf(true) }

            androidx.compose.animation.Crossfade(
                targetState = showSplash,
                animationSpec = androidx.compose.animation.core.tween(450),
                label = "splashTransition"
            ) { isSplash ->
                if (isSplash) {
                    NearaSplashScreen(
                        onSplashFinished = { showSplash = false }
                    )
                } else {
                    NearaMobileTheme {
                        NearaMobileApp(appState)
                    }
                }
            }
        }
    }

    private fun requestRequiredPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }

        val toRequest = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (toRequest.isNotEmpty()) {
            permissionLauncher.launch(toRequest.toTypedArray())
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        appState.close()
    }
}

private data class TabBarConfig(
    val title: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val color: Color
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NearaMobileApp(appState: AndroidAppState) {
    val currentTab by appState.currentTab.collectAsState()
    val activePeer by appState.activeConversationPeer.collectAsState()
    val activeNetwork by appState.activeConversationNetwork.collectAsState()
    val showGroupInfo by appState.showGroupInfo.collectAsState()
    val conversations by appState.conversationList.collectAsState()

    // Find current active conversation item (to get isMuted, etc.)
    val activeConvItem = remember(conversations, activeNetwork, activePeer) {
        if (activeNetwork != null) {
            conversations.find { it.network?.networkId == activeNetwork?.networkId }
        } else if (activePeer != null) {
            conversations.find { it.peer?.peerId == activePeer?.peerId }
        } else null
    }
    val isActiveMuted = activeConvItem?.isMuted ?: false
    val context = androidx.compose.ui.platform.LocalContext.current
    var isMuteBannerDismissed by remember(activeConvItem?.conversationId) { mutableStateOf(false) }
    var showActiveChatMuteDialog by remember { mutableStateOf(false) }
    var groupCallPickerState by remember { mutableStateOf<Pair<app.neara.core.model.Network, Boolean>?>(null) }

    if (showActiveChatMuteDialog && activeConvItem != null) {
        MuteNotificationsDialog(
            conversationId = activeConvItem.conversationId,
            isMuted = activeConvItem.isMuted,
            appState = appState,
            onDismiss = { showActiveChatMuteDialog = false }
        )
    }

    LaunchedEffect(currentTab, activeNetwork) {
        if (activeNetwork == null || currentTab != AndroidTab.CHATS) {
            appState.showGroupInfo.value = false
        }
    }

    val tabConfig = when (currentTab) {
        AndroidTab.CHATS -> TabBarConfig("Neara", Icons.Default.NearMe, AccentEmerald)
        AndroidTab.NEARBY -> TabBarConfig("Nearby", Icons.Default.Radar, AccentEmerald)
        AndroidTab.NETWORKS -> TabBarConfig("Networks", Icons.Default.Hub, AccentIndigo)
        AndroidTab.PROFILE -> TabBarConfig("My Identity", Icons.Default.PersonOutline, AccentEmerald)
        AndroidTab.SETTINGS -> TabBarConfig("Settings", Icons.Default.Settings, TextSecondary)
        AndroidTab.FILES -> TabBarConfig("Files", Icons.Default.FolderShared, AccentEmerald)
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        if (currentTab == AndroidTab.CHATS && (activePeer != null || activeNetwork != null)) {
                            if (showGroupInfo && activeNetwork != null) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = { appState.showGroupInfo.value = false },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.ArrowBack,
                                            contentDescription = "Back",
                                            tint = TextPrimary
                                        )
                                    }
                                    Spacer(Modifier.width(6.dp))
                                    Column {
                                        Text(
                                            "Group Settings",
                                            color = TextPrimary,
                                            fontSize = 17.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            "Channel controls & members",
                                            color = AccentIndigo,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            } else {
                                val title = activePeer?.displayName ?: activeNetwork?.name ?: "Chat"
                                val subtitle = if (activeNetwork != null) "Local Group Channel" else "End-to-End Encrypted"
                                val initial = title.take(1).uppercase()
                                val tintColor = if (activeNetwork != null) AccentIndigo else AccentCyan

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = {
                                            appState.activeConversationPeer.value = null
                                            appState.activeConversationNetwork.value = null
                                            appState.activeConversationId.value = null
                                            appState.refreshConversations()
                                        },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.ArrowBack,
                                            contentDescription = "Back",
                                            tint = TextPrimary
                                        )
                                    }
                                    Spacer(Modifier.width(4.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(CircleShape)
                                            .background(tintColor.copy(alpha = 0.15f))
                                            .clickable {
                                                if (activeNetwork != null) {
                                                    appState.showGroupInfo.value = true
                                                } else if (activeConvItem != null) {
                                                    showActiveChatMuteDialog = true
                                                }
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            initial,
                                            color = tintColor,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                    }
                                    Spacer(Modifier.width(10.dp))
                                    Column(
                                        verticalArrangement = Arrangement.Center,
                                        modifier = Modifier
                                            .padding(top = 3.dp)
                                            .clickable {
                                                if (activeNetwork != null) {
                                                    appState.showGroupInfo.value = true
                                                } else if (activeConvItem != null) {
                                                    showActiveChatMuteDialog = true
                                                }
                                            }
                                    ) {
                                        Text(
                                            title,
                                            color = TextPrimary,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        Spacer(Modifier.height(1.dp))
                                        Text(
                                            subtitle,
                                            color = if (activeNetwork != null) AccentIndigo else AccentEmerald,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(RoundedCornerShape(9.dp))
                                        .background(tabConfig.color.copy(alpha = 0.12f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        tabConfig.icon,
                                        contentDescription = tabConfig.title,
                                        tint = tabConfig.color,
                                        modifier = Modifier.size(19.dp)
                                    )
                                }
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    tabConfig.title,
                                    color = TextPrimary,
                                    fontSize = 19.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = (-0.3).sp
                                )
                            }
                        }
                    },
                    actions = {
                        if (currentTab == AndroidTab.CHATS && (activePeer != null || activeNetwork != null) && !showGroupInfo) {
                            IconButton(onClick = {
                                if (activePeer != null) {
                                    appState.startVoiceCall(activePeer!!)
                                } else if (activeNetwork != null) {
                                    val otherMembers = activeNetwork!!.members.filter { it.peerId != appState.localPeerId }
                                    if (otherMembers.size <= 1) {
                                        appState.startGroupCall(activeNetwork!!, isVideo = false)
                                    } else {
                                        groupCallPickerState = Pair(activeNetwork!!, false)
                                    }
                                }
                            }) {
                                Icon(
                                    Icons.Default.Call,
                                    contentDescription = "Voice Call",
                                    tint = TextPrimary
                                )
                            }
                            IconButton(onClick = {
                                if (activePeer != null) {
                                    appState.startVideoCall(activePeer!!)
                                } else if (activeNetwork != null) {
                                    val otherMembers = activeNetwork!!.members.filter { it.peerId != appState.localPeerId }
                                    if (otherMembers.size <= 1) {
                                        appState.startGroupCall(activeNetwork!!, isVideo = true)
                                    } else {
                                        groupCallPickerState = Pair(activeNetwork!!, true)
                                    }
                                }
                            }) {
                                Icon(
                                    Icons.Default.Videocam,
                                    contentDescription = "Video Call",
                                    tint = TextPrimary
                                )
                            }
                            IconButton(onClick = {
                                if (activeNetwork != null) {
                                    appState.showGroupInfo.value = true
                                } else if (activeConvItem != null) {
                                    showActiveChatMuteDialog = true
                                }
                            }) {
                                Icon(
                                    Icons.Default.Info,
                                    contentDescription = "Chat Info & Settings",
                                    tint = if (activeNetwork != null) AccentIndigo else TextPrimary
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = BgCard,
                        titleContentColor = TextPrimary
                    )
                )
                HorizontalDivider(color = BorderSubtle, thickness = 1.dp)

                if (currentTab == AndroidTab.CHATS && (activePeer != null || activeNetwork != null) && !showGroupInfo) {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = isActiveMuted && !isMuteBannerDismissed
                    ) {
                        MessengerMuteBanner(
                            onTurnOn = {
                                activeConvItem?.let { appState.unmuteConversation(it.conversationId) }
                            },
                            onDismiss = {
                                isMuteBannerDismissed = true
                            }
                        )
                    }
                }
            }
        },
        bottomBar = {
            val inActiveChat = currentTab == AndroidTab.CHATS && (activePeer != null || activeNetwork != null)
            if (!inActiveChat) {
                NearaBottomNavBar(
                    currentTab = currentTab,
                    onTabSelected = {
                        appState.showGroupInfo.value = false
                        appState.setTab(it)
                    }
                )
            }
        },
        containerColor = BgDark
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (currentTab == AndroidTab.CHATS && activeNetwork != null && showGroupInfo) {
                AndroidGroupInfoScreen(
                    group = activeNetwork!!,
                    appState = appState,
                    onBack = { appState.showGroupInfo.value = false }
                )
            } else {
                when (currentTab) {
                    AndroidTab.CHATS -> AndroidChatScreen(appState)
                    AndroidTab.NEARBY -> AndroidNearbyScreen(appState)
                    AndroidTab.NETWORKS -> AndroidNetworksScreen(appState)
                    AndroidTab.FILES -> AndroidNearbyScreen(appState)
                    AndroidTab.PROFILE -> AndroidProfileScreen(appState)
                    AndroidTab.SETTINGS -> AndroidSettingsScreen(appState)
                }
            }
        }
    }

    // Group call picker modal
    val pickerState = groupCallPickerState
    if (pickerState != null) {
        GroupCallPickerModal(
            network = pickerState.first,
            isVideo = pickerState.second,
            appState = appState,
            onDismiss = { groupCallPickerState = null },
            onCallPeer = { peer, isVideo ->
                if (isVideo) appState.startVideoCall(peer, pickerState.first.networkId)
                else appState.startVoiceCall(peer, pickerState.first.networkId)
            }
        )
    }

    // Full-screen incoming / outgoing / connected call overlay
    AndroidCallOverlay(callManager = appState.callManager)
}

@Composable
fun NearaBottomNavBar(
    currentTab: AndroidTab,
    onTabSelected: (AndroidTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = BgCard,
        modifier = modifier.fillMaxWidth()
    ) {
        Column {
            HorizontalDivider(color = BorderSubtle, thickness = 1.dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val navItems = listOf(
                    Triple(AndroidTab.CHATS, Icons.Default.ChatBubbleOutline, "Chats"),
                    Triple(AndroidTab.NEARBY, Icons.Default.Radar, "Nearby"),
                    Triple(AndroidTab.NETWORKS, Icons.Default.Hub, "Networks"),
                    Triple(AndroidTab.PROFILE, Icons.Default.PersonOutline, "Profile"),
                    Triple(AndroidTab.SETTINGS, Icons.Default.Settings, "Settings")
                )

                navItems.forEach { (tab, icon, label) ->
                    val selected = currentTab == tab
                    val animatedColor by animateColorAsState(
                        targetValue = if (selected) AccentEmerald else Color(0xFF94A3B8),
                        label = "navColor"
                    )

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .weight(1f)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { onTabSelected(tab) }
                    ) {
                        Icon(
                            icon,
                            contentDescription = label,
                            tint = animatedColor,
                            modifier = Modifier.size(24.dp)
                        )

                        Spacer(Modifier.height(4.dp))

                        Text(
                            text = label,
                            color = animatedColor,
                            fontSize = 11.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            letterSpacing = (-0.2).sp
                        )
                    }
                }
            }
        }
    }
}
