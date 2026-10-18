package com.organiser.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.io.File
import java.util.Calendar
import java.util.concurrent.TimeUnit

class MorningWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {
    override fun doWork(): Result {
        Reminder.notifyNow(applicationContext)
        return Result.success()
    }
}

object Reminder {
    private const val CHANNEL = "morning"
    private const val WORK = "morning_reminder"

    fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Morning reminder", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
    }

    /** (Re)schedules the daily reminder at the time saved in the profile, or cancels it. */
    fun schedule(ctx: Context) {
        val p = Prefs(ctx).read()
        val wm = WorkManager.getInstance(ctx)
        if (!p.remind) { wm.cancelUniqueWork(WORK); return }
        val now = Calendar.getInstance()
        val t = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, p.hour); set(Calendar.MINUTE, p.minute)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            if (before(now)) add(Calendar.DAY_OF_YEAR, 1)
        }
        val req = PeriodicWorkRequestBuilder<MorningWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(t.timeInMillis - now.timeInMillis, TimeUnit.MILLISECONDS)
            .build()
        wm.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.UPDATE, req)
    }

    @SuppressLint("MissingPermission")
    fun notifyNow(ctx: Context) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        ensureChannel(ctx)
        val p = Prefs(ctx).read()
        val todo = Store(ctx).load().flatMap { it.items }.filter { !it.purchased }
        val pick = todo.randomOrNull()
        val total = todo.sumOf { it.price ?: 0.0 }
        val hello = if (p.name.isBlank()) "Good morning" else "Good morning, ${p.name}"
        val text = when {
            pick == null -> "Your wishlist is empty. Add something you love to a board."
            else -> {
                val label = pick.name.ifBlank { "an item" }
                val sum = if (total > 0) " (${money(p.currency, total)} total)" else ""
                "${todo.size} item(s) waiting$sum. Today's pick: $label"
            }
        }
        val pi = PendingIntent.getActivity(
            ctx, 0,
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val b = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle(hello)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pi)
            .setAutoCancel(true)
        if (pick != null) {
            BitmapFactory.decodeFile(File(ctx.filesDir, pick.file).path)?.let {
                val s = 256f / maxOf(it.width, it.height)
                b.setLargeIcon(Bitmap.createScaledBitmap(it, (it.width * s).toInt().coerceAtLeast(1), (it.height * s).toInt().coerceAtLeast(1), true))
            }
        }
        NotificationManagerCompat.from(ctx).notify(1001, b.build())
    }
}
