package mg.sitraka.phrasometre

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

object Rappels {
    const val CANAL = "rappel_quotidien"
    private const val NOM_TRAVAIL = "rappel_quotidien"

    fun planifier(ctx: Context, heure: Int, minute: Int) {
        val maintenant = LocalDateTime.now()
        var cible = maintenant.withHour(heure).withMinute(minute).withSecond(0).withNano(0)
        if (!cible.isAfter(maintenant)) cible = cible.plusDays(1)
        val delai = Duration.between(maintenant, cible).toMillis()
        val req = PeriodicWorkRequestBuilder<RappelWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(delai, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(NOM_TRAVAIL, ExistingPeriodicWorkPolicy.UPDATE, req)
    }

    fun annuler(ctx: Context) {
        WorkManager.getInstance(ctx).cancelUniqueWork(NOM_TRAVAIL)
    }
}

class RappelWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val d = LocalStore.charger(ctx)
        if (d.phrases.none { it.mois == moisCourant() }) return Result.success()
        if (d.clics.any { it.jour == LocalDate.now().toString() }) return Result.success()
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return Result.success()

        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(Rappels.CANAL, "Rappel quotidien", NotificationManager.IMPORTANCE_DEFAULT)
        )
        val pi = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notif = NotificationCompat.Builder(ctx, Rappels.CANAL)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle("Phrasomètre")
            .setContentText("Pensez à compter vos phrases d'aujourd'hui.")
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify(1001, notif)
        } catch (e: SecurityException) {
            // permission refusée
        }
        return Result.success()
    }
}
