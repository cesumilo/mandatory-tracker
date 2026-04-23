package com.valoranttracker.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.valoranttracker.app.widget.MatchSyncWorker

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MandatoryTheme {
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
                        Spacer(modifier = Modifier.height(48.dp))
                        Button(
                            onClick = { MatchSyncWorker.requestImmediate(this@MainActivity) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MandatoryRed
                            )
                        ) {
                            Text("Refresh Widget")
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedButton(
                            onClick = { /* TODO: Open widget settings */ },
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MandatoryRed
                            )
                        ) {
                            Text("Add Widget to Home Screen")
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