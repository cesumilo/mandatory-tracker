package com.valoranttracker.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider

class MatchWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            WidgetContent()
        }
    }
}

@Composable
private fun WidgetContent() {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(R.color.background))
            .cornerRadius(16.dp)
            .padding(16.dp),
        verticalAlignment = Alignment.Top,
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            text = "Valorant Match",
            style = TextStyle(
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = ColorProvider(R.color.white)
            )
        )
        Spacer(modifier = GlanceModifier.height(8.dp))
        Text(
            text = "Loading...",
            style = TextStyle(
                fontSize = 12.sp,
                color = ColorProvider(R.color.on_surface_secondary)
            )
        )
    }
}