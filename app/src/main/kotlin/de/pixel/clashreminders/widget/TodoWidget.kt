package de.pixel.clashreminders.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.CircularProgressIndicator
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.material3.ColorProviders
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import de.pixel.clashreminders.MainActivity
import de.pixel.clashreminders.R
import de.pixel.clashreminders.api.dto.CurrentWarDto
import de.pixel.clashreminders.domain.ClanGamesAnalysis
import java.text.DateFormat
import java.util.Date

class TodoWidget : GlanceAppWidget() {

    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val prefs = currentState<Preferences>()
            val state = TodoWidgetState.from(prefs)
            GlanceTheme {
                TodoWidgetContent(context, state)
            }
        }
    }
}

@Composable
private fun TodoWidgetContent(context: Context, state: TodoWidgetState) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .padding(12.dp)
            .clickable(actionStartActivity<MainActivity>()),
    ) {
        HeaderRow(context, state)
        Spacer(GlanceModifier.size(8.dp))
        BodyContent(context, state)
    }
}

@Composable
private fun HeaderRow(context: Context, state: TodoWidgetState) {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = context.getString(R.string.app_name),
            style = TextStyle(
                color = GlanceTheme.colors.onSurface,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            ),
            modifier = GlanceModifier.defaultWeight(),
        )
        if (state.updatedAt > 0L) {
            val timeStr = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(state.updatedAt))
            Text(
                text = context.getString(R.string.widget_updated_at, timeStr),
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = 11.sp,
                ),
            )
            Spacer(GlanceModifier.width(8.dp))
        }
        if (state.refreshing) {
            CircularProgressIndicator(
                modifier = GlanceModifier.size(20.dp),
                color = GlanceTheme.colors.primary,
            )
        } else {
            Image(
                provider = ImageProvider(R.drawable.ic_widget_refresh),
                contentDescription = context.getString(R.string.widget_refresh),
                modifier = GlanceModifier
                    .size(20.dp)
                    .clickable(actionRunCallback<RefreshTodoAction>()),
                colorFilter = androidx.glance.ColorFilter.tint(GlanceTheme.colors.onSurfaceVariant),
            )
        }
    }
}

@Composable
private fun BodyContent(context: Context, state: TodoWidgetState) {
    when {
        !state.hasApiKey -> {
            Text(
                text = context.getString(R.string.widget_no_api_key),
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp),
            )
        }
        !state.hasAccounts -> {
            Text(
                text = context.getString(R.string.widget_no_accounts),
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp),
            )
        }
        state.updatedAt == 0L -> {
            Text(
                text = context.getString(R.string.widget_no_data),
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp),
            )
        }
        state.entries.isEmpty() -> {
            Text(
                text = context.getString(R.string.widget_all_done),
                style = TextStyle(
                    color = GlanceTheme.colors.primary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
        }
        else -> {
            LazyColumn {
                items(state.entries) { entry ->
                    EntryRow(context, entry)
                }
            }
        }
    }
}

@Composable
private fun EntryRow(context: Context, entry: TodoWidgetState.Entry) {
    val statusParts = mutableListOf<String>()

    // Only show chips for things that still need doing — the widget is a to-do list.
    val hasOpenAttacks = entry.warState == CurrentWarDto.STATE_IN_WAR &&
        entry.warDone < entry.warRequired
    if (hasOpenAttacks) {
        statusParts += context.getString(R.string.home_chip_war, entry.warDone, entry.warRequired)
    }

    val hasOpenCwl = entry.cwlState == CurrentWarDto.STATE_IN_WAR && entry.cwlDone < entry.cwlRequired
    if (hasOpenCwl) {
        statusParts += context.getString(R.string.home_chip_cwl, entry.cwlDone, entry.cwlRequired)
    }

    val hasOpenRaid = entry.raidAttacks != null && entry.raidLimit != null &&
        entry.raidAttacks < entry.raidLimit
    if (hasOpenRaid) {
        statusParts += context.getString(R.string.home_chip_raid, entry.raidAttacks!!, entry.raidLimit!!)
    }

    val hasOpenCg = entry.cgActive &&
        (entry.cgPoints == null || entry.cgPoints < ClanGamesAnalysis.DEFAULT_THRESHOLD)
    if (hasOpenCg) {
        val pts = entry.cgPoints
        if (pts != null) {
            statusParts += context.getString(R.string.home_chip_cg, pts)
        } else {
            statusParts += context.getString(R.string.home_chip_cg_unknown)
        }
    }

    val isUrgent = hasOpenAttacks || hasOpenCwl || hasOpenRaid || hasOpenCg

    Row(
        modifier = GlanceModifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = entry.name,
            style = TextStyle(
                color = GlanceTheme.colors.onSurface,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            ),
            modifier = GlanceModifier.defaultWeight(),
            maxLines = 1,
        )
        Spacer(GlanceModifier.width(8.dp))
        Text(
            text = statusParts.joinToString(" · "),
            style = TextStyle(
                color = if (isUrgent) GlanceTheme.colors.error else GlanceTheme.colors.onSurfaceVariant,
                fontSize = 12.sp,
            ),
            maxLines = 1,
        )
    }
}
