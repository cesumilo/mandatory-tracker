package com.valoranttracker.app.widget

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.valoranttracker.app.MainActivity

class MatchWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val prefs = context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
        val opponent = prefs.getString("opponent", null)
        val event = prefs.getString("event", null)
        val timeUntil = prefs.getString("timeUntil", null)
        val tournament = prefs.getString("tournament", null)
        
        provideContent {
            WidgetContent(
                opponent = opponent,
                event = event,
                timeUntil = timeUntil,
                tournament = tournament
            )
        }
    }
    
    companion object {
        suspend fun updateWidget(context: Context, opponent: String?, event: String?, timeUntil: String?, tournament: String?) {
            context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
                .edit()
                .putString("opponent", opponent)
                .putString("event", event)
                .putString("timeUntil", timeUntil)
                .putString("tournament", tournament)
                .apply()
            MatchWidget().updateAll(context)
        }
    }
}

private val BackgroundColor = ColorProvider(Color(0xFF0D0D0D))
private val TextColor = ColorProvider(Color(0xFFFFFFFF))
private val SecondaryTextColor = ColorProvider(Color(0xB3FFFFFF))
private val AccentColor = ColorProvider(Color(0xFFFF2C2C))

@Composable
private fun WidgetContent(
    opponent: String?,
    event: String?,
    timeUntil: String?,
    tournament: String?
) {
    Box(
        modifier = androidx.glance.GlanceModifier
            .fillMaxSize()
            .background(BackgroundColor)
            .cornerRadius(16.dp)
            .padding(16.dp)
            .clickable(actionStartActivity<MainActivity>()),
        contentAlignment = Alignment.TopStart
    ) {
        Column(
            modifier = androidx.glance.GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = "MANDATORY",
                style = TextStyle(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = AccentColor
                )
            )
            Spacer(modifier = androidx.glance.GlanceModifier.height(8.dp))
            
            if (opponent != null) {
                Text(
                    text = "vs $opponent",
                    style = TextStyle(
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextColor
                    )
                )
            } else {
                Text(
                    text = "No upcoming match",
                    style = TextStyle(
                        fontSize = 14.sp,
                        color = SecondaryTextColor
                    )
                )
            }
            
            if (event != null) {
                Spacer(modifier = androidx.glance.GlanceModifier.height(4.dp))
                Text(
                    text = event,
                    style = TextStyle(
                        fontSize = 12.sp,
                        color = SecondaryTextColor
                    )
                )
            }
            
            if (tournament != null) {
                Spacer(modifier = androidx.glance.GlanceModifier.height(2.dp))
                Text(
                    text = tournament,
                    style = TextStyle(
                        fontSize = 10.sp,
                        color = ColorProvider(Color(0x80FFFFFF))
                    )
                )
            }
            
            Spacer(modifier = androidx.glance.GlanceModifier.height(8.dp))
            
            if (timeUntil != null) {
                Text(
                    text = "in $timeUntil",
                    style = TextStyle(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = AccentColor
                    )
                )
            }
        }
    }
}