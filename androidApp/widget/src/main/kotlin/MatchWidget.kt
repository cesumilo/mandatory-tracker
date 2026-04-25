package com.valoranttracker.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
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

class MatchWidget : GlanceAppWidget() {
    override suspend fun provideGlance(
        context: Context,
        id: GlanceId,
    ) {
        provideContent {
            WidgetContent()
        }
    }
}

private val BackgroundColor = ColorProvider(Color(0xFF1A1A2E))
private val TextColor = ColorProvider(Color(0xFFFFFFFF))
private val SecondaryTextColor = ColorProvider(Color(0xB3FFFFFF))
private val AccentColor = ColorProvider(Color(0xFFCE2B2B))

@Composable
private fun WidgetContent() {
    Box(
        modifier =
            GlanceModifier
                .fillMaxSize()
                .background(BackgroundColor)
                .cornerRadius(16.dp)
                .padding(16.dp),
        contentAlignment = Alignment.TopStart,
    ) {
        Column(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalAlignment = Alignment.Start,
        ) {
            Text(
                text = "Valorant Match",
                style =
                    TextStyle(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextColor,
                    ),
            )
            Spacer(modifier = GlanceModifier.height(8.dp))
            Text(
                text = "vs Mandatory",
                style =
                    TextStyle(
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextColor,
                    ),
            )
            Spacer(modifier = GlanceModifier.height(4.dp))
            Text(
                text = "Challengers France",
                style =
                    TextStyle(
                        fontSize = 12.sp,
                        color = SecondaryTextColor,
                    ),
            )
            Spacer(modifier = GlanceModifier.height(8.dp))
            Text(
                text = "in 1d 17h",
                style =
                    TextStyle(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = AccentColor,
                    ),
            )
        }
    }
}
