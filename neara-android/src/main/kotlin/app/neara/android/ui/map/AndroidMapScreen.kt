@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package app.neara.android.ui.map

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
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
import android.os.Looper
import android.provider.Settings
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
import app.neara.android.ui.*
import app.neara.core.model.Peer
import kotlinx.coroutines.delay
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import kotlin.math.cos
import kotlin.math.sin

@SuppressLint("MissingPermission")
@Composable
fun AndroidMapScreen(appState: AndroidAppState) {
    val context = LocalContext.current
    val discoveredPeers by appState.discoveredPeers.collectAsState()

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

    // Check if phone location (GPS / Network) service is turned ON
    var isPhoneLocationOn by remember {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        val gps = lm?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true
        val net = lm?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true
        mutableStateOf(gps || net)
    }

    // Periodic check for location provider status (updates dynamically when returning from settings)
    LaunchedEffect(Unit) {
        if (!hasLocationPermission) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
        while (true) {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            val gps = lm?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true
            val net = lm?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true
            isPhoneLocationOn = gps || net
            hasLocationPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            delay(2000L)
        }
    }

    // Default coordinate: Dhaka, Bangladesh or retrieved user location
    var userGeoPoint by remember { mutableStateOf(GeoPoint(23.8103, 90.4125)) }
    var locationAccuracy by remember { mutableStateOf<Float?>(null) }
    var isRealLocationAcquired by remember { mutableStateOf(false) }
    var hasCenteredOnRealLocation by remember { mutableStateOf(false) }

    var mapViewInstance by remember { mutableStateOf<MapView?>(null) }
    var isDarkMode by remember { mutableStateOf(true) }
    var showRadarRange by remember { mutableStateOf(true) }
    var selectedPeer by remember { mutableStateOf<Peer?>(null) }

    // Real-Time GPS & Network Location Tracking
    DisposableEffect(hasLocationPermission, isPhoneLocationOn) {
        if (hasLocationPermission && isPhoneLocationOn) {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            val listener = object : LocationListener {
                override fun onLocationChanged(loc: Location) {
                    val gp = GeoPoint(loc.latitude, loc.longitude)
                    userGeoPoint = gp
                    locationAccuracy = loc.accuracy
                    isRealLocationAcquired = true

                    // Auto-pan to user's real location upon first live GPS fix
                    if (!hasCenteredOnRealLocation) {
                        hasCenteredOnRealLocation = true
                        mapViewInstance?.controller?.animateTo(gp, 17.0, 900L)
                    }
                }
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                override fun onProviderEnabled(provider: String) {
                    isPhoneLocationOn = true
                }
                override fun onProviderDisabled(provider: String) {
                    val gps = lm?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true
                    val net = lm?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true
                    isPhoneLocationOn = gps || net
                }
            }

            try {
                // Get the freshest last known location
                var bestLocation: Location? = null
                val providers = listOf(
                    LocationManager.GPS_PROVIDER,
                    LocationManager.NETWORK_PROVIDER,
                    LocationManager.PASSIVE_PROVIDER
                )
                for (p in providers) {
                    try {
                        val l = lm?.getLastKnownLocation(p)
                        if (l != null && (bestLocation == null || l.time > bestLocation.time)) {
                            bestLocation = l
                        }
                    } catch (e: Exception) {}
                }

                bestLocation?.let {
                    val gp = GeoPoint(it.latitude, it.longitude)
                    userGeoPoint = gp
                    locationAccuracy = it.accuracy
                    isRealLocationAcquired = true
                    if (!hasCenteredOnRealLocation) {
                        hasCenteredOnRealLocation = true
                        mapViewInstance?.controller?.setCenter(gp)
                        mapViewInstance?.controller?.setZoom(17.0)
                    }
                }

                // Register fast high-accuracy location listener
                if (lm?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true) {
                    lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 1.0f, listener, Looper.getMainLooper())
                }
                if (lm?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true) {
                    lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000L, 1.0f, listener, Looper.getMainLooper())
                }
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
        // Native OpenStreetMap View with Real Location Tracking
        AndroidView(
            factory = { ctx ->
                Configuration.getInstance().load(ctx, ctx.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
                Configuration.getInstance().userAgentValue = "Neara/1.0 (Android; Offline-P2P)"

                MapView(ctx).apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    isTilesScaledToDpi = true
                    controller.setZoom(17.0)
                    controller.setCenter(userGeoPoint)

                    // Real-time GPS overlay provider
                    val gpsProvider = GpsMyLocationProvider(ctx).apply {
                        locationUpdateMinTime = 1000L
                        locationUpdateMinDistance = 1.0f
                    }
                    val myLocOverlay = MyLocationNewOverlay(gpsProvider, this).apply {
                        enableMyLocation()
                        setDrawAccuracyEnabled(true)
                    }
                    overlays.add(myLocOverlay)

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
                // Clear dynamic markers while preserving base overlays
                val toRemove = map.overlays.filter { it is Marker || it is Polygon }
                map.overlays.removeAll(toRemove)

                // Radar mesh coverage rings around user's real location
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
                    snippet = if (isRealLocationAcquired) {
                        "Real GPS Location • Accuracy: ±${locationAccuracy?.toInt() ?: 10}m"
                    } else {
                        "Host Node • IP: ${appState.localIpAddress}"
                    }
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    icon = createPulsingNodeIcon(context, isHost = true, name = "You")
                    setOnMarkerClickListener { _, _ ->
                        selectedPeer = null
                        true
                    }
                }
                map.overlays.add(userMarker)

                // 2. Discovered Peer Markers positioned in mesh proximity
                discoveredPeers.forEach { peer ->
                    val hash = peer.peerId.hashCode()
                    val angle = Math.toRadians((hash % 360).toDouble().let { if (it < 0) it + 360 else it })
                    val distanceMeters = 35.0 + ((hash and 0x7F) % 85)
                    val latOffset = (distanceMeters / 111320.0) * cos(angle)
                    val lonOffset = (distanceMeters / (111320.0 * cos(Math.toRadians(userGeoPoint.latitude)))) * sin(angle)
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

        // Top Status & Warning Banners
        Column(
            modifier = Modifier
                .statusBarsPadding()
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Warning 1: Phone Location (GPS) is turned OFF
            if (!isPhoneLocationOn) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                    border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.8f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.LocationOff,
                            contentDescription = null,
                            tint = Color(0xFFF59E0B),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Phone Location is OFF", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("Turn on GPS for live real location tracking", color = TextSecondary, fontSize = 11.sp)
                        }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = {
                                context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("Turn On", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }

            // Warning 2: Location permission missing
            if (!hasLocationPermission) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                    border = BorderStroke(1.dp, AccentDanger.copy(alpha = 0.8f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Security,
                            contentDescription = null,
                            tint = AccentDanger,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Location Permission Needed", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("Allow location permission to show your position", color = TextSecondary, fontSize = 11.sp)
                        }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = {
                                permissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                    )
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AccentDanger),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("Allow", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }

            // Floating Header Badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Free OpenStreetMap / GPS Status Badge
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
                                .background(if (isRealLocationAcquired) AccentEmerald else Color(0xFFF59E0B))
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (isRealLocationAcquired) "🛰️ GPS Real Location" else "🗺️ OpenStreetMap (Free)",
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
        }

        // Floating Action Buttons (Right HUD)
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // GPS Center on Real Location
            FloatingMapButton(
                icon = Icons.Default.MyLocation,
                contentDescription = "Center on Real Location",
                tint = if (isRealLocationAcquired) AccentEmerald else Color(0xFFF59E0B),
                onClick = {
                    val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                    val gps = lm?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true
                    val net = lm?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true
                    if (!gps && !net) {
                        // Prompt user to turn on GPS in settings
                        context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                    } else {
                        // Immediately fetch fresh last known location
                        var freshLoc: Location? = null
                        for (p in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
                            try {
                                val l = lm?.getLastKnownLocation(p)
                                if (l != null && (freshLoc == null || l.time > freshLoc.time)) {
                                    freshLoc = l
                                }
                            } catch (e: Exception) {}
                        }
                        freshLoc?.let {
                            userGeoPoint = GeoPoint(it.latitude, it.longitude)
                            isRealLocationAcquired = true
                        }
                        mapViewInstance?.controller?.animateTo(userGeoPoint, 17.5, 900L)
                    }
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
