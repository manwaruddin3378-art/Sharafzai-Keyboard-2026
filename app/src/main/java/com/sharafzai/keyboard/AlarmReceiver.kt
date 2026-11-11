package com.sharafzai.keyboard

import android.app.*
import android.content.*
import android.os.Build

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra("alarm_id", 1)
        val label = intent.getStringExtra("label") ?: "شرفزی الارم"
        val manager = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(NotificationChannel("sharafzai_alarms", "Sharafzai Alarms", NotificationManager.IMPORTANCE_HIGH))
        }
        val open = PendingIntent.getActivity(context, id, Intent(context, MainActivity::class.java).putExtra("open_page", "alarms"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(context, "sharafzai_alarms") else Notification.Builder(context)
        manager.notify(id, builder.setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("د شرفزی الارم").setContentText(label).setCategory(Notification.CATEGORY_ALARM)
            .setPriority(Notification.PRIORITY_MAX).setAutoCancel(true).setContentIntent(open).build())
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val manager = context.getSystemService(AlarmManager::class.java)
        AlarmStore.read(context).filter { it.enabled }.forEach { alarm ->
            if (Build.VERSION.SDK_INT >= 31 && !manager.canScheduleExactAlarms()) return@forEach
            val trigger = java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.HOUR_OF_DAY, alarm.hour); set(java.util.Calendar.MINUTE, alarm.minute)
                set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
                if (timeInMillis <= System.currentTimeMillis()) add(java.util.Calendar.DAY_OF_YEAR, 1)
            }
            val pending = PendingIntent.getBroadcast(context, alarm.id,
                Intent(context, AlarmReceiver::class.java).putExtra("alarm_id", alarm.id).putExtra("label", alarm.label),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            if (Build.VERSION.SDK_INT >= 23) manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger.timeInMillis, pending)
            else manager.setExact(AlarmManager.RTC_WAKEUP, trigger.timeInMillis, pending)
        }
    }
}
