package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.BuildConfig
import com.example.service.DashcamForegroundService
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.MultiplePermissionsState
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.example.ui.theme.*

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MainScreen(onNavigateToDebug: () -> Unit = {}) {
    val context = LocalContext.current
    var isOverlayGranted by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    
    val permissions = mutableListOf(
        android.Manifest.permission.CAMERA,
        android.Manifest.permission.ACCESS_FINE_LOCATION,
        android.Manifest.permission.ACCESS_COARSE_LOCATION
    )
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        permissions.add(android.Manifest.permission.POST_NOTIFICATIONS)
    }

    val permissionState = rememberMultiplePermissionsState(permissions)
    val allGranted = permissionState.allPermissionsGranted && isOverlayGranted

    val isDriving by DashcamForegroundService.isDriving.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            Column(modifier = Modifier.background(VDashSurface).statusBarsPadding()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(VDashPrimary),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("VD", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("V-Dash ", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = VDashTextDark, letterSpacing = (-0.5).sp)
                            Text("ADAS", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = VDashPrimary)
                        }
                    }
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(VDashSurfaceAccent)
                            .clickable { /* Settings */ },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("⚙️", fontSize = 20.sp)
                    }
                }
                Text(
                    text = "Version ${BuildConfig.VERSION_NAME} | ${BuildConfig.GIT_REVISION}",
                    color = VDashTextMuted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp)
                )
                Divider(color = VDashBorder, thickness = 1.dp)
            }
        },
        bottomBar = {
            Column(modifier = Modifier.background(VDashSurface).navigationBarsPadding()) {
                Divider(color = VDashBorder, thickness = 1.dp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    BottomNavItem("🏠", "HOME", true, onClick = {})
                    BottomNavItem("📊", "HISTORY", false, onClick = {})
                    BottomNavItem("🗺️", "MAPS", false, onClick = {})
                    BottomNavItem("📡", "DEBUG", false, onClick = onNavigateToDebug)
                }
            }
        },
        containerColor = VDashBackground
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Main Action Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(VDashSurface)
                    .border(1.dp, VDashBorder, RoundedCornerShape(28.dp))
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(VDashIconBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("🚗", fontSize = 40.sp)
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Button(
                        onClick = {
                            if (!isDriving) {
                                val intent = Intent(context, DashcamForegroundService::class.java).apply {
                                    action = DashcamForegroundService.ACTION_START_DRIVE
                                }
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    context.startForegroundService(intent)
                                } else {
                                    context.startService(intent)
                                }
                            } else {
                                val intent = Intent(context, DashcamForegroundService::class.java).apply {
                                    action = DashcamForegroundService.ACTION_STOP_DRIVE
                                }
                                context.startService(intent)
                            }
                        },
                        enabled = allGranted || isDriving,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .shadow(if (isDriving) 0.dp else 8.dp, RoundedCornerShape(16.dp), spotColor = VDashPrimary.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isDriving) Color(0xFFBA1A1A) else VDashPrimary
                        )
                    ) {
                        Text(if (isDriving) "STOP DRIVE" else "START DRIVE", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "Remember: Distance estimates are approximate. Drive safely.",
                        color = VDashTextMuted,
                        fontSize = 12.sp,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // System Readiness Card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(VDashSurface)
                    .border(1.dp, VDashBorder, RoundedCornerShape(28.dp))
                    .padding(24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("SYSTEM READINESS", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = VDashTextMuted, letterSpacing = 1.sp)
                    if (allGranted) {
                        Text("ALL SYSTEMS OK", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = VDashSuccess, modifier = Modifier.background(VDashSuccessBg, RoundedCornerShape(percent = 50)).padding(horizontal = 8.dp, vertical = 2.dp))
                    } else {
                        Text("ACTION REQUIRED", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.background(Color(0xFFBA1A1A), RoundedCornerShape(percent = 50)).padding(horizontal = 8.dp, vertical = 2.dp))
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ReadinessItem(
                        icon = "📸", 
                        title = "Camera & GPS", 
                        isReady = permissionState.allPermissionsGranted,
                        modifier = Modifier.weight(1f).clickable { permissionState.launchMultiplePermissionRequest() }
                    )
                    ReadinessItem(
                        icon = "🖼️", 
                        title = "HUD Overlay", 
                        isReady = isOverlayGranted,
                        modifier = Modifier.weight(1f).clickable {
                            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                            context.startActivity(intent)
                        }
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                Divider(color = VDashBorder, thickness = 1.dp)
                Spacer(modifier = Modifier.height(16.dp))

                // Test TTS
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("VOICE ALERTS", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = VDashTextMuted, letterSpacing = 1.sp)
                        Text("Test Output", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = VDashTextDark)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(VDashSurfaceAccent).clickable {
                            val intent = Intent(context, DashcamForegroundService::class.java).apply { action = DashcamForegroundService.ACTION_TEST_TTS_EN }
                            context.startService(intent)
                        }.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Text("EN", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = VDashPrimary)
                        }
                        Box(modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(VDashSurfaceAccent).clickable {
                            val intent = Intent(context, DashcamForegroundService::class.java).apply { action = DashcamForegroundService.ACTION_TEST_TTS_VI }
                            context.startService(intent)
                        }.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Text("VN", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = VDashPrimary)
                        }
                    }
                }
                
            }
            
            // Re-check overlay when returning
            LaunchedEffect(Unit) {
                isOverlayGranted = Settings.canDrawOverlays(context)
            }
            
            Text(
                text = "Samsung Tip: Go to App Info > Battery > set to 'Unrestricted' for reliable background operation.",
                fontSize = 12.sp,
                color = VDashTextMuted,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }
    }
}

@Composable
fun ReadinessItem(icon: String, title: String, isReady: Boolean, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(VDashSurfaceAccent)
            .border(1.dp, if (isReady) Color.Transparent else Color(0xFFBA1A1A).copy(alpha = 0.3f), RoundedCornerShape(16.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(icon, fontSize = 20.sp)
        Text(title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = VDashTextDark)
        if (isReady) {
            Text("READY", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = VDashSuccess, letterSpacing = 1.sp)
        } else {
            Text("MISSING", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFBA1A1A), letterSpacing = 1.sp)
        }
    }
}

@Composable
fun BottomNavItem(icon: String, label: String, isSelected: Boolean, onClick: () -> Unit = {}) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.width(64.dp).clickable(onClick = onClick)
    ) {
        Text(icon, fontSize = 20.sp)
        Text(
            text = label, 
            fontSize = 10.sp, 
            fontWeight = FontWeight.Bold, 
            color = if (isSelected) VDashPrimary else VDashTextMuted,
            letterSpacing = 0.5.sp
        )
    }
}
