package com.valoranttracker.app

import android.content.Intent
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.valoranttracker.app.widget.MatchNotificationWorker
import com.valoranttracker.app.widget.MatchSyncWorker
import com.valoranttracker.app.widget.getNextMatchInfo
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            MatchNotificationWorker.schedule(this)
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MandatoryTheme {
                val context = LocalContext.current
                var notificationStatus by remember { mutableStateOf<String?>(null) }
                var widgetRefreshStatus by remember { mutableStateOf<String?>(null) }
                val scope = rememberCoroutineScope()
                
                LaunchedEffect(Unit) {
                    scope.launch {
                        try {
                            val info = getNextMatchInfo(context)
                            notificationStatus = info?.let { (_, time) ->
                                "Notification in $time"
                            } ?: "No upcoming match"
                        } catch (e: Exception) {
                            notificationStatus = "Error: ${e.message}"
                        }
                    }
                }
                
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = DarkBackground
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Image(
                            painter = painterResource(R.drawable.mandatory_logo),
                            contentDescription = "Mandatory Logo",
                            modifier = Modifier.size(120.dp),
                            contentScale = ContentScale.Fit
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "MANDATORY",
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            color = MandatoryRed
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Valorant Tracker",
                            fontSize = 16.sp,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = notificationStatus ?: "Loading...",
                            fontSize = 14.sp,
                            color = if (notificationStatus?.startsWith("Error") == true) Color.Red else MandatoryRed
                        )
                        
                        Spacer(modifier = Modifier.height(48.dp))
                        Button(
                            onClick = { 
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    val hasPermission = ContextCompat.checkSelfPermission(
                                        context, 
                                        Manifest.permission.POST_NOTIFICATIONS
                                    ) == PackageManager.PERMISSION_GRANTED
                                    
                                    if (hasPermission) {
                                        MatchNotificationWorker.showTestNotification(context)
                                    } else {
                                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                } else {
                                    MatchNotificationWorker.showTestNotification(context)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MandatoryRed
                            )
                        ) {
                            Text("Enable/Test Notification")
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { 
                                widgetRefreshStatus = "Refreshing..."
                                MatchSyncWorker.requestImmediate(context)
                                scope.launch {
                                    kotlinx.coroutines.delay(500)
                                    val info = getNextMatchInfo(context)
                                    widgetRefreshStatus = info?.let { (opponent, time) ->
                                        "Updated: vs $opponent in $time"
                                    } ?: "Updated: No match found"
                                    kotlinx.coroutines.delay(3000)
                                    widgetRefreshStatus = null
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MandatoryRed
                            )
                        ) {
                            Text(if (widgetRefreshStatus?.startsWith("Refreshing") == true) "Refreshing..." else "Refresh Widget")
                        }
                    }
                }
            }
        }
    }
}

private val MandatoryRed = Color(0xFFFF2C2C)
private val DarkBackground = Color(0xFF0D0D0D)

@Composable
private fun MandatoryTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = MandatoryRed,
            secondary = MandatoryRed,
            background = DarkBackground,
            surface = DarkBackground,
            onPrimary = Color.White,
            onSecondary = Color.White,
            onBackground = Color.White,
            onSurface = Color.White
        )
    ) {
        content()
    }
}