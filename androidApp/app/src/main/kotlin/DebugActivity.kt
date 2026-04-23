package com.valoranttracker.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class DebugActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                DebugScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugScreen() {
    var refreshState by remember { mutableStateOf("Not initialized") }
    var lastError by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Debug Info") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Sync State", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Status: $refreshState")
                    if (lastError != null) {
                        Text("Last Error: $lastError", color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            Button(
                onClick = {
                    refreshState = "Refreshing..."
                    // TODO: Connect to repository
                    refreshState = "Sync completed"
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Force Refresh")
            }

            Button(
                onClick = {
                    refreshState = "Cleared cache"
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Clear Cache")
            }
        }
    }
}