package com.valoranttracker.app

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import com.valoranttracker.app.widget.MatchNotificationWorker
import com.valoranttracker.app.widget.getNextMatchInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import java.net.URL

class MainActivity : ComponentActivity() {
    private val notificationPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { isGranted ->
            if (isGranted) {
                MatchNotificationWorker.schedule(this)
            }
        }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MandatoryTheme {
                val context = LocalContext.current
                val scope = rememberCoroutineScope()
                val teamId = "7967"

                var isLoading by remember { mutableStateOf(true) }
                var isRefreshing by remember { mutableStateOf(false) }
                var upcomingMatches by remember { mutableStateOf<List<MatchData>>(emptyList()) }
                var pastMatches by remember { mutableStateOf<List<MatchData>>(emptyList()) }
                var notificationStatus by remember { mutableStateOf<String?>(null) }

                fun refresh() {
                    scope.launch {
                        isRefreshing = true
                        try {
                            val matches =
                                withContext(Dispatchers.IO) {
                                    fetchUpcomingMatches(teamId)
                                }
                            upcomingMatches = matches

                            val results =
                                withContext(Dispatchers.IO) {
                                    fetchPastMatches(teamId)
                                }
                            pastMatches = results
                        } catch (e: Exception) {
                        } finally {
                            isRefreshing = false
                            isLoading = false
                        }
                    }
                }

                LaunchedEffect(Unit) {
                    try {
                        val info = getNextMatchInfo(context)
                        notificationStatus = info?.let { (opponent, time) ->
                            "Next: vs $opponent in $time"
                        } ?: "No upcoming match"
                    } catch (e: Exception) {
                        notificationStatus = null
                    }
                    refresh()
                }

                PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = { refresh() },
                    modifier = Modifier.fillMaxSize().background(DarkBackground),
                ) {
                    LazyColumn(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        item {
                            HeaderSection(
                                notificationStatus = notificationStatus,
                            )
                        }

                        if (isLoading || isRefreshing) {
                            item {
                                SectionTitle("UPCOMING MATCHES")
                            }
                            items(3) {
                                ShimmerUpcomingCard()
                            }

                            item {
                                Spacer(modifier = Modifier.height(8.dp))
                                SectionTitle("LATEST RESULTS")
                            }
                            items(3) {
                                ShimmerCompletedCard()
                            }
                        } else {
                            if (upcomingMatches.isNotEmpty()) {
                                item {
                                    SectionTitle("UPCOMING MATCHES (${upcomingMatches.size})")
                                }
                                items(upcomingMatches.size) { index ->
                                    UpcomingMatchCard(match = upcomingMatches[index], teamId = teamId)
                                }
                            } else if (upcomingMatches.isEmpty()) {
                                item {
                                    EmptyStateCard("No upcoming matches found")
                                }
                            }

                            if (pastMatches.isNotEmpty()) {
                                item {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    SectionTitle("LATEST RESULTS (${pastMatches.size})")
                                }
                                items(pastMatches.size) { index ->
                                    CompletedMatchCard(match = pastMatches[index], teamId = teamId)
                                }
                            }
                        }

                        item {
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun HeaderSection(notificationStatus: String?) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                painter = painterResource(R.drawable.mandatory_logo),
                contentDescription = "Mandatory Logo",
                modifier = Modifier.size(80.dp),
                contentScale = ContentScale.Fit,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "MANDATORY",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = MandatoryRed,
            )
            Text(
                text = "Valorant Tracker",
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.7f),
            )
            notificationStatus?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = it,
                    fontSize = 12.sp,
                    color = MandatoryRed.copy(alpha = 0.8f),
                )
            }
        }
    }

    @Composable
    private fun SectionTitle(title: String) {
        Text(
            text = title,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White.copy(alpha = 0.8f),
            modifier = Modifier.padding(vertical = 8.dp),
        )
    }

    @Composable
    private fun UpcomingMatchCard(
        match: MatchData,
        teamId: String,
    ) {
        val team1 = match.teams.getOrNull(0)
        val team2 = match.teams.getOrNull(1)
        val opponent = if (team1?.id == teamId) team2 else team1
        val ourTeam = if (team1?.id == teamId) team1 else team2

        val matchDate =
            try {
                val instant = Instant.fromEpochSeconds(match.timestamp)
                val local = instant.toLocalDateTime(TimeZone.currentSystemDefault())
                "${local.dayOfMonth}/${local.monthNumber} @ ${local.hour}:${local.minute.toString().padStart(2, '0')}"
            } catch (e: Exception) {
                match.timeUntil
            }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CardDark),
            shape = RoundedCornerShape(12.dp),
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Image(
                            painter = rememberAsyncImagePainter(opponent?.logo),
                            contentDescription = opponent?.name,
                            modifier = Modifier.size(40.dp),
                            contentScale = ContentScale.Fit,
                        )
                        Text(
                            text = opponent?.name ?: "",
                            fontSize = 10.sp,
                            color = Color.White.copy(alpha = 0.8f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.width(60.dp),
                            textAlign = TextAlign.Center,
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("vs", fontSize = 12.sp, color = MandatoryRed)
                        Text(
                            match.event,
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.6f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            match.tournament,
                            fontSize = 10.sp,
                            color = Color.White.copy(alpha = 0.5f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Image(
                            painter = rememberAsyncImagePainter(ourTeam?.logo),
                            contentDescription = ourTeam?.name,
                            modifier = Modifier.size(40.dp),
                            contentScale = ContentScale.Fit,
                        )
                        Text(
                            text = ourTeam?.name ?: "",
                            fontSize = 10.sp,
                            color = Color.White.copy(alpha = 0.8f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.width(60.dp),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(matchDate, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MandatoryRed)
                    Text(match.timeUntil, fontSize = 9.sp, color = Color.White.copy(alpha = 0.5f))
                }
            }
        }
    }

    @Composable
    private fun CompletedMatchCard(
        match: MatchData,
        teamId: String,
    ) {
        val team1 = match.teams.getOrNull(0)
        val team2 = match.teams.getOrNull(1)
        val opponent = if (team1?.id == teamId) team2 else team1
        val ourTeam = if (team1?.id == teamId) team1 else team2

        val isWin = ourTeam?.won == true
        val ourScore = ourTeam?.score ?: "0"
        val oppScore = opponent?.score ?: "0"
        // Score should be shown as opponent - our team (left to right order)
        val scoreText = "$oppScore - $ourScore"

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors =
                CardDefaults.cardColors(
                    containerColor = if (isWin) WinGreen.copy(alpha = 0.15f) else LossRed.copy(alpha = 0.15f),
                ),
            shape = RoundedCornerShape(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Image(
                            painter = rememberAsyncImagePainter(opponent?.logo),
                            contentDescription = opponent?.name,
                            modifier = Modifier.size(40.dp),
                            contentScale = ContentScale.Fit,
                        )
                        Text(
                            opponent?.name ?: "",
                            fontSize = 10.sp,
                            color = Color.White.copy(alpha = 0.8f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.width(60.dp),
                            textAlign = TextAlign.Center,
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            if (isWin) "WIN" else "LOSS",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isWin) WinGreen else LossRed,
                        )
                        Text(scoreText, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text(
                            match.event,
                            fontSize = 10.sp,
                            color = Color.White.copy(alpha = 0.6f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            match.tournament,
                            fontSize = 9.sp,
                            color = Color.White.copy(alpha = 0.5f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Image(
                            painter = rememberAsyncImagePainter(ourTeam?.logo),
                            contentDescription = ourTeam?.name,
                            modifier = Modifier.size(40.dp),
                            contentScale = ContentScale.Fit,
                        )
                        Text(
                            ourTeam?.name ?: "",
                            fontSize = 10.sp,
                            color = Color.White.copy(alpha = 0.8f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.width(60.dp),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(match.ago ?: "", fontSize = 10.sp, color = Color.White.copy(alpha = 0.6f))
                    Text("ago", fontSize = 9.sp, color = Color.White.copy(alpha = 0.5f))
                }
            }
        }
    }

    @Composable
    private fun EmptyStateCard(message: String) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CardDark),
            shape = RoundedCornerShape(12.dp),
        ) {
            Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(text = message, fontSize = 14.sp, color = Color.White.copy(alpha = 0.6f))
            }
        }
    }

    @Composable
    private fun shimmerBrush(): Brush {
        val transition = rememberInfiniteTransition(label = "shimmer")
        val translateAnimation =
            transition.animateFloat(
                initialValue = 0f,
                targetValue = 1000f,
                animationSpec =
                    infiniteRepeatable(
                        animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Restart,
                    ),
                label = "shimmer",
            )
        return Brush.linearGradient(
            colors =
                listOf(
                    CardDark,
                    CardDark.copy(alpha = 0.5f),
                    CardDark,
                ),
            start = Offset(translateAnimation.value - 200f, 0f),
            end = Offset(translateAnimation.value, 0f),
        )
    }

    @Composable
    private fun ShimmerUpcomingCard() {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CardDark),
            shape = RoundedCornerShape(12.dp),
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier =
                                Modifier
                                    .size(40.dp)
                                    .background(shimmerBrush(), RoundedCornerShape(4.dp)),
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier =
                                Modifier
                                    .width(60.dp)
                                    .height(10.dp)
                                    .background(shimmerBrush(), RoundedCornerShape(2.dp)),
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Box(
                            modifier =
                                Modifier
                                    .width(30.dp)
                                    .height(12.dp)
                                    .background(shimmerBrush(), RoundedCornerShape(2.dp)),
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier =
                                Modifier
                                    .width(80.dp)
                                    .height(10.dp)
                                    .background(shimmerBrush(), RoundedCornerShape(2.dp)),
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier =
                                Modifier
                                    .width(60.dp)
                                    .height(8.dp)
                                    .background(shimmerBrush(), RoundedCornerShape(2.dp)),
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier =
                                Modifier
                                    .size(40.dp)
                                    .background(shimmerBrush(), RoundedCornerShape(4.dp)),
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier =
                                Modifier
                                    .width(60.dp)
                                    .height(10.dp)
                                    .background(shimmerBrush(), RoundedCornerShape(2.dp)),
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Box(
                        modifier =
                            Modifier
                                .width(50.dp)
                                .height(12.dp)
                                .background(shimmerBrush(), RoundedCornerShape(2.dp)),
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier =
                            Modifier
                                .width(30.dp)
                                .height(8.dp)
                                .background(shimmerBrush(), RoundedCornerShape(2.dp)),
                    )
                }
            }
        }
    }

    @Composable
    private fun ShimmerCompletedCard() {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CardDark),
            shape = RoundedCornerShape(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier =
                                Modifier
                                    .size(40.dp)
                                    .background(shimmerBrush(), RoundedCornerShape(4.dp)),
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier =
                                Modifier
                                    .width(60.dp)
                                    .height(10.dp)
                                    .background(shimmerBrush(), RoundedCornerShape(2.dp)),
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Box(
                            modifier =
                                Modifier
                                    .width(40.dp)
                                    .height(12.dp)
                                    .background(shimmerBrush(), RoundedCornerShape(2.dp)),
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier =
                                Modifier
                                    .width(50.dp)
                                    .height(16.dp)
                                    .background(shimmerBrush(), RoundedCornerShape(2.dp)),
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier =
                                Modifier
                                    .width(80.dp)
                                    .height(10.dp)
                                    .background(shimmerBrush(), RoundedCornerShape(2.dp)),
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier =
                                Modifier
                                    .width(60.dp)
                                    .height(8.dp)
                                    .background(shimmerBrush(), RoundedCornerShape(2.dp)),
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier =
                                Modifier
                                    .size(40.dp)
                                    .background(shimmerBrush(), RoundedCornerShape(4.dp)),
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier =
                                Modifier
                                    .width(60.dp)
                                    .height(10.dp)
                                    .background(shimmerBrush(), RoundedCornerShape(2.dp)),
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Box(
                        modifier =
                            Modifier
                                .width(40.dp)
                                .height(10.dp)
                                .background(shimmerBrush(), RoundedCornerShape(2.dp)),
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier =
                            Modifier
                                .width(20.dp)
                                .height(8.dp)
                                .background(shimmerBrush(), RoundedCornerShape(2.dp)),
                    )
                }
            }
        }
    }

    private fun fetchUpcomingMatches(teamId: String): List<MatchData> {
        Log.d("MainActivity", "Fetching upcoming for team $teamId")
        val url = URL("https://vlr.orlandomm.net/api/v1/matches")
        val connection = url.openConnection() as java.net.HttpURLConnection
        connection.setRequestProperty("User-Agent", "ValorantTracker/1.0")
        connection.connectTimeout = 15000
        connection.readTimeout = 15000
        val response = connection.inputStream.bufferedReader().readText()
        connection.disconnect()

        val json = Json { ignoreUnknownKeys = true }
        val data = json.decodeFromString<ApiResponse>(response)

        Log.d("MainActivity", "Total matches from API: ${data.data.size}")

        val allUpcoming = data.data.filter { it.status.equals("Upcoming", ignoreCase = true) }
        Log.d("MainActivity", "Upcoming matches: ${allUpcoming.size}")

        val withTeam = allUpcoming.filter { match -> match.teams.any { team -> team.id == teamId } }
        Log.d("MainActivity", "Matches with team $teamId: ${withTeam.size}")

        return withTeam.sortedBy { it.timestamp }
    }

    private fun fetchPastMatches(teamId: String): List<MatchData> {
        Log.d("MainActivity", "Fetching past for team $teamId")
        val allMatches = mutableListOf<MatchData>()

        for (page in 1..5) {
            try {
                val url = URL("https://vlr.orlandomm.net/api/v1/results?page=$page")
                val connection = url.openConnection() as java.net.HttpURLConnection
                connection.setRequestProperty("User-Agent", "ValorantTracker/1.0")
                connection.connectTimeout = 15000
                connection.readTimeout = 15000
                val response = connection.inputStream.bufferedReader().readText()
                connection.disconnect()

                val json = Json { ignoreUnknownKeys = true }
                val data = json.decodeFromString<ApiResponse>(response)

                if (data.data.isEmpty()) break
                allMatches.addAll(data.data)
            } catch (e: Exception) {
                break
            }
        }

        Log.d("MainActivity", "Total results fetched: ${allMatches.size}")

        val withTeam = allMatches.filter { match -> match.teams.any { team -> team.id == teamId } }
        Log.d("MainActivity", "Results with team $teamId: ${withTeam.size}")

        return withTeam.take(10)
    }
}

@kotlinx.serialization.Serializable
data class ApiResponse(val status: String = "", val size: Int = 0, val data: List<MatchData> = emptyList())

@kotlinx.serialization.Serializable
data class MatchData(
    val id: String = "",
    val teams: List<TeamData> = emptyList(),
    val status: String = "",
    val event: String = "",
    val tournament: String = "",
    val img: String? = null,
    val timeUntil: String = "",
    val timestamp: Long = 0L,
    val ago: String? = null,
)

@kotlinx.serialization.Serializable
data class TeamData(
    val id: String? = null,
    val name: String = "",
    val country: String? = null,
    val score: String? = null,
    val logo: String? = null,
    val won: Boolean? = null,
)

private val MandatoryRed = Color(0xFFFF2C2C)
private val DarkBackground = Color(0xFF0D0D0D)
private val CardDark = Color(0xFF1A1A1A)
private val WinGreen = Color(0xFF4CAF50)
private val LossRed = Color(0xFFE53935)

@Composable
private fun MandatoryTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme =
            darkColorScheme(
                primary = MandatoryRed,
                secondary = MandatoryRed,
                background = DarkBackground,
                surface = DarkBackground,
                onPrimary = Color.White,
                onSecondary = Color.White,
                onBackground = Color.White,
                onSurface = Color.White,
            ),
    ) { content() }
}
