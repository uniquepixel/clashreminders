package de.pixel.clashreminders.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.updateAppWidgetState
import de.pixel.clashreminders.scheduling.WidgetUpdateWorker
import de.pixel.clashreminders.widget.TodoWidgetState.Companion.encode

class RefreshTodoAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        updateAppWidgetState(context, glanceId) { prefs ->
            prefs[TodoWidgetState.KEY] =
                TodoWidgetState.from(prefs).copy(refreshing = true).encode()
        }
        TodoWidget().update(context, glanceId)
        WidgetUpdateWorker.enqueue(context, forced = true)
    }
}
