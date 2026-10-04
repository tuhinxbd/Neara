@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package app.neara.android.ui.map

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import app.neara.android.AndroidAppState
import app.neara.android.AndroidTab
import app.neara.android.ui.*
import app.neara.core.model.Peer
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.TilesOverlay
import kotlin.math.cos

@SuppressLint("MissingPermission")
@Composable
fun AndroidMapScreen(appState: AndroidAppState) {
    val context = LocalContext.current
    val discoveredPeers by appState.discoveredPeers.collectAsState()
    val nearbyGroups by appState.nearbyGroups.collectAsState()

    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        hasLocationPermission = perms[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                perms[Manifest.permission.ACCESS_COARSE_LOCATION] == true
    }

    LaunchedEffect(Unit) {
        if (!hasLocationPermission) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    // Default coordinate: Dhaka, Bangladesh or retrieved user location
    var userGeoPoint by remember { mutableStateOf(GeoPoint(23.8103, 90.4125)) }
    var locationAccuracy by remember { mutableStateOf<Float?>(null) }
    var mapViewInstance by remember { mutableStateOf<MapView?>(null) }
    var isDarkMode by remember { mutableStateOf(true) }
    var showRadarRange by remember { mutableStateOf(true) }
    var selectedPeer by remember { mutableStateOf<Peer?>(null) }

    // Fetch GPS / Network location
    DisposableEffect(hasLocationPermission) {
        if (hasLocationPermission) {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            val listener = object : LocationListener {
                override fun onLocationChanged(loc: Location) {
                    val gp = GeoPoint(loc.latitude, loc.longitude)
                    userGeoPoint = gp
                    locationAccuracy = loc.accuracy
                }
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {}
            }

            try {
                lm?.getLastKnownLocation(LocationManager.GPS_PROVIDER)?.let {
                    userGeoPoint = GeoPoint(it.latitude, it.longitude)
                    locationAccuracy = it.accuracy
                } ?: lm?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)?.let {
                    userGeoPoint = GeoPoint(it.latitude, it.longitude)
                    locationAccuracy = it.accuracy
                }

                lm?.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5000L, 10f, listener)
                lm?.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 5000L, 10f, listener)
            } catch (e: Exception) {
                e.printStackTrace()
            }

            onDispose {
                try {
                    lm?.removeUpdates(listener)
                } catch (e: Exception) {}
            }
        } else {
            onDispose {}
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(BgDark)) {
        // Native OpenStreetMap View
        AndroidView(
            factory = { ctx ->
                Configuration.getInstance().load(ctx, ctx.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
                Configuration.getInstance().userAgentValue = "Neara/1.0 (Android; Offline-P2P)"

                MapView(ctx).apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    isTilesScaledToDpi = true
                    controller.setZoom(16.0)
                    controller.setCenter(userGeoPoint)

                    // Apply Cyberpunk Dark Invert filter by default
                    if (isDarkMode) {
                        val inverseMatrix = ColorMatrix(
                            floatArrayOf(
                                -0.85f, 0f, 0f, 0f, 240f,
                                0f, -0.85f, 0f, 0f, 245f,
                                0f, 0f, -0.85f, 0f, 255f,
                                0f, 0f, 0f, 1f, 0f
                            )
                        )
                        overlayManager.tilesOverlay.setColorFilter(ColorMatrixColorFilter(inverseMatrix))
                    }

                    mapViewInstance = this
                }
            },
            update = { map ->
                map.overlays.clear()

                // Radar rings around local node
                if (showRadarRange) {
                    val ranges = listOf(50.0, 100.0, 200.0)
                    for (r in ranges) {
                        val circle = Polygon().apply {
                            points = Polygon.pointsAsCircle(userGeoPoint, r)
                            fillPaint.color = android.graphics.Color.argb(20, 16, 185, 129)
                            outlinePaint.color = android.graphics.Color.argb(80, 16, 185, 129)
                            outlinePaint.strokeWidth = 2f
                        }
                        map.overlays.add(circle)
                    }
                }

                // 1. Host/User Marker ("You")
                val userMarker = Marker(map).apply {
                    position = userGeoPoint
                    title = "You (${appState.cryptoEngine.localIdentity.displayName})"
                    snippet = "Host Node • IP: ${appState.localIpAddress}"
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    icon = createPulsingNodeIcon(context, isHost = true, name = "You")
                    setOnMarkerClickListener { _, _ ->
                        selectedPeer = null
                        true
                    }
                }
                map.overlays.add(userMarker)

                // 2. Discovered Peer Markers
                discoveredPeers.forEachIndexed { idx, peer ->
                    // Calculate deterministic mock offset (within 40-150m) around user for visual mesh representation
                    val hash = peer.peerId.hashCode()
                    val angle = Math.toRadians((hash % 360).toDouble().let { if (it < 0) it + 360 else it })
                    val distanceMeters = 40.0 + ((hash and 0x7F) % 90)
                    val latOffset = (distanceMeters / 111320.0) * cos(angle)
                    val lonOffset = (distanceMeters / (111320.0 * cos(Math.toRadians(userGeoPoint.latitude)))) * kotlin.math.sin(angle)
                    val peerPoint = GeoPoint(userGeoPoint.latitude + latOffset, userGeoPoint.longitude + lonOffset)

                    val peerMarker = Marker(map).apply {
                        position = peerPoint
                        title = peer.displayName
                        snippet = "IP: ${peer.ipAddress}:${peer.port} • Direct P2P"
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        icon = createPulsingNodeIcon(context, isHost = false, name = peer.displayName)
                        setOnMarkerClickListener { _, _ ->
                            selectedPeer = peer
                            true
                        }
                    }
                    map.overlays.add(peerMarker)
                }

                map.invalidate()
            },
            modifier = Modifier.fillMaxSize()
        )

        // Top Status Floating Bar
        Row(
            modifier = Modifier
                .statusBarsPadding()
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Free OpenStreetMap Badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(BgCard.copy(alpha = 0.92f))
                    .border(1.dp, BorderSubtle, RoundedCornerShape(20.dp))
                    .padding(horizontal = 12.dp, vertical = 7.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(AccentEmerald)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "🗺️ OpenStreetMap (Free)",
                        color = TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Peers Count Badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(BgCard.copy(alpha = 0.92f))
                    .border(1.dp, BorderSubtle, RoundedCornerShape(20.dp))
                    .padding(horizontal = 12.dp, vertical = 7.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.WifiTethering,
                        contentDescription = null,
                        tint = AccentCyan,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "${discoveredPeers.size} Active Nodes",
                        color = AccentCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Floating Action Buttons (Right HUD)
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // GPS Center on User
            FloatingMapButton(
                icon = Icons.Default.MyLocation,
                contentDescription = "Center on My Location",
                tint = AccentEmerald,
                onClick = {
                    mapViewInstance?.controller?.animateTo(userGeoPoint, 16.5, 1000L)
                }
            )

            // Zoom In
            FloatingMapButton(
                icon = Icons.Default.Add,
                contentDescription = "Zoom In",
                tint = TextPrimary,
                onClick = {
                    mapViewInstance?.controller?.zoomIn()
                }
            )

            // Zoom Out
            FloatingMapButton(
                icon = Icons.Default.Remove,
                contentDescription = "Zoom Out",
                tint = TextPrimary,
                onClick = {
                    mapViewInstance?.controller?.zoomOut()
                }
            )

            // Toggle Dark / Standard Map
            FloatingMapButton(
                icon = if (isDarkMode) Icons.Default.DarkMode else Icons.Default.LightMode,
                contentDescription = "Toggle Map Theme",
                tint = if (isDarkMode) AccentIndigo else AccentWarning,
                onClick = {
                    isDarkMode = !isDarkMode
                    mapViewInstance?.let { map ->
                        if (isDarkMode) {
                            val inverseMatrix = ColorMatrix(
                                floatArrayOf(
                                    -0.85f, 0f, 0f, 0f, 240f,
                                    0f, -0.85f, 0f, 0f, 245f,
                                    0f, 0f, -0.85f, 0f, 255f,
                                    0f, 0f, 0f, 1f, 0f
                                )
                            )
                            map.overlayManager.tilesOverlay.setColorFilter(ColorMatrixColorFilter(inverseMatrix))
                        } else {
                            map.overlayManager.tilesOverlay.setColorFilter(null)
                        }
                        map.invalidate()
                    }
                }
            )

            // Toggle Radar Range Rings
            FloatingMapButton(
                icon = Icons.Default.Radar,
                contentDescription = "Toggle Radar Circles",
                tint = if (showRadarRange) AccentEmerald else TextMuted,
                onClick = {
                    showRadarRange = !showRadarRange
                    mapViewInstance?.invalidate()
                }
            )
        }

        // Bottom Selected Peer Card OR Nodes Carousel
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 12.dp, start = 14.dp, end = 14.dp)
        ) {
            val peer = selectedPeer
            if (peer != null) {
                // Expanded Peer Interaction Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(16.dp, RoundedCornerShape(18.dp)),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = BgCard),
                    border = BorderStroke(1.dp, AccentEmerald.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(CircleShape)
                                        .background(AccentEmerald.copy(alpha = 0.2f))
                                        .border(1.5.dp, AccentEmerald, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        peer.displayName.take(1).uppercase(),
                                        color = AccentEmerald,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(
                                        peer.displayName,
                                        color = TextPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
                                    Text(
                                        "Node IP: ${peer.ipAddress}:${peer.port}",
                                        color = TextSecondary,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                            IconButton(onClick = { selectedPeer = null }) {
                                Icon(Icons.Default.Close, contentDescription = "Close", tint = TextMuted)
                            }
                        }

                        Spacer(Modifier.height(14.dp))

                        // Quick Actions
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = {
                                    appState.selectConversation(peer)
                                },
                                modifier = Modifier.weight(1f).height(40.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = AccentEmerald)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.Black)
                                Spacer(Modifier.width(6.dp))
                                Text("Chat", color = Color.Black, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            }

                            Button(
                                onClick = {
                                    appState.startVoiceCall(peer, peer.peerId)
                                },
                                modifier = Modifier.weight(1f).height(40.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = AccentCyan)
                            ) {
                                Icon(Icons.Default.Call, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.Black)
                                Spacer(Modifier.width(6.dp))
                                Text("Call", color = Color.Black, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            }
                        }
                    }
                }
            } else if (discoveredPeers.isNotEmpty()) {
                // Bottom Nodes Pill Carousel
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = BgCard.copy(alpha = 0.95f)),
                    border = BorderStroke(1.dp, BorderSubtle)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Nearby Mesh Nodes", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("Tap pin or node to connect", color = TextMuted, fontSize = 11.sp)
                        }

                        Spacer(Modifier.height(8.dp))

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(discoveredPeers) { p ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(BgDark)
                                        .border(1.dp, AccentEmerald.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                                        .clickable {
                                            selectedPeer = p
                                            mapViewInstance?.controller?.animateTo(userGeoPoint, 17.0, 800L)
                                        }
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(20.dp)
                                                .clip(CircleShape)
                                                .background(AccentEmerald.copy(alpha = 0.2f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                p.displayName.take(1).uppercase(),
                                                color = AccentEmerald,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            p.displayName,
                                            color = TextPrimary,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FloatingMapButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    tint: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .shadow(6.dp, CircleShape)
            .clip(CircleShape)
            .background(BgCard.copy(alpha = 0.95f))
            .border(1.dp, BorderSubtle, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(20.dp)
        )
    }
}

// Generate beautiful custom Android BitmapDrawable pins for Host and Mesh Peers
private fun createPulsingNodeIcon(context: Context, isHost: Boolean, name: String): BitmapDrawable {
    val size = 110
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val mainColor = if (isHost) android.graphics.Color.parseColor("#10B981") else android.graphics.Color.parseColor("#06B6D4")
    val alphaColor = if (isHost) android.graphics.Color.argb(50, 16, 185, 129) else android.graphics.Color.argb(50, 6, 182, 212)

    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    // Outer radar ring
    paint.color = alphaColor
    paint.style = Paint.Style.FILL
    canvas.drawCircle(size / 2f, size / 2f, size / 2f - 4f, paint)

    // Inner circle
    paint.color = mainColor
    canvas.drawCircle(size / 2f, size / 2f, 24f, paint)

    // White border around node center
    paint.color = android.graphics.Color.WHITE
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = 4f
    canvas.drawCircle(size / 2f, size / 2f, 24f, paint)

    // Initial text inside node
    paint.style = Paint.Style.FILL
    paint.textSize = 22f
    paint.isFakeBoldText = true
    paint.textAlign = Paint.Align.CENTER
    val letter = name.take(1).uppercase()
    val yPos = (size / 2f - (paint.descent() + paint.ascent()) / 2f)
    canvas.drawText(letter, size / 2f, yPos, paint)

    return BitmapDrawable(context.resources, bitmap)
}
