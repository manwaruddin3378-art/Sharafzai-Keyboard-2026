package com.sharafzai.keyboard

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

/**
 * A small home-screen widget that displays a rotating daily remembrance.
 * Android refreshes periodic widgets at a system-controlled interval (30 min here).
 */
class DhikrWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { widgetId ->
            updateWidget(context, appWidgetManager, widgetId)
        }
    }

    companion object {
        private val adhkar = listOf(
            "سُبْحَانَ اللَّهِ وَبِحَمْدِهِ",
            "أَسْتَغْفِرُ اللَّهَ وَأَتُوبُ إِلَيْهِ",
            "لَا إِلٰهَ إِلَّا اللَّهُ",
            "اللَّهُمَّ صَلِّ وَسَلِّمْ عَلَى نَبِيِّنَا مُحَمَّدٍ",
            "سُبْحَانَ اللَّهِ، وَالْحَمْدُ لِلَّهِ، وَلَا إِلٰهَ إِلَّا اللَّهُ، وَاللَّهُ أَكْبَرُ",
            "لَا حَوْلَ وَلَا قُوَّةَ إِلَّا بِاللَّهِ"
        )

        private fun updateWidget(
            context: Context,
            manager: AppWidgetManager,
            widgetId: Int
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_adhkar)
            val slot = (System.currentTimeMillis() / 1_800_000L).toInt()
            val text = adhkar[((slot % adhkar.size) + adhkar.size) % adhkar.size]
            views.setTextViewText(R.id.adhkar_widget_text, text)

            val openApp = Intent(context, MainActivity::class.java)
                .putExtra("open_page", "adhkar")
            val pending = PendingIntent.getActivity(
                context,
                widgetId,
                openApp,
                PendingIntent.FLAG_UPDATE_CURRENT or (if (android.os.Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
            )
            views.setOnClickPendingIntent(R.id.adhkar_widget_root, pending)
            manager.updateAppWidget(widgetId, views)
        }
    }
}
