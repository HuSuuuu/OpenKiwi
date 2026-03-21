package com.orizon.openkiwi.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews
import com.orizon.openkiwi.MainActivity
import com.orizon.openkiwi.R

class KiwiAppWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (id in appWidgetIds) {
            updateOne(context, appWidgetManager, id)
        }
    }

    private fun updateOne(context: Context, mgr: AppWidgetManager, widgetId: Int) {
        val views = RemoteViews(context.packageName, R.layout.widget_kiwi)

        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0

        views.setOnClickPendingIntent(
            R.id.widget_btn_hello,
            PendingIntent.getActivity(
                context,
                201,
                promptIntent(context, "你好，简单聊几句。"),
                flags
            )
        )
        views.setOnClickPendingIntent(
            R.id.widget_btn_summary,
            PendingIntent.getActivity(
                context,
                202,
                promptIntent(context, "根据对话和记忆，帮我总结今天要做的三件事。"),
                flags
            )
        )
        views.setOnClickPendingIntent(
            R.id.widget_btn_open,
            PendingIntent.getActivity(
                context,
                203,
                Intent(context, MainActivity::class.java).apply {
                    setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                },
                flags
            )
        )

        mgr.updateAppWidget(widgetId, views)
    }

    private fun promptIntent(context: Context, prompt: String): Intent =
        Intent(context, MainActivity::class.java).apply {
            setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(MainActivity.EXTRA_WIDGET_PROMPT, prompt)
        }
}
