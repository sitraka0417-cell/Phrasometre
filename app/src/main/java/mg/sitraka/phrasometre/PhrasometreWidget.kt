package mg.sitraka.phrasometre

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import java.time.LocalDate

class PhrasometreWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { appWidgetManager.updateAppWidget(it, construire(context)) }
    }

    companion object {
        fun construire(ctx: Context): RemoteViews {
            val d = LocalStore.charger(ctx)
            val aujourdhui = LocalDate.now().toString()
            val nbJour = d.clics.count { it.jour == aujourdhui }
            val mois = moisCourant()
            val nbMois = d.clics.count { it.mois == mois }
            val v = RemoteViews(ctx.packageName, R.layout.widget_phrasometre)
            v.setTextViewText(R.id.widget_jour, nbJour.toString())
            v.setTextViewText(R.id.widget_mois, "$nbMois ce mois-ci")
            val pi = PendingIntent.getActivity(
                ctx, 0, Intent(ctx, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            v.setOnClickPendingIntent(R.id.widget_racine, pi)
            return v
        }

        fun rafraichir(ctx: Context) {
            try {
                val m = AppWidgetManager.getInstance(ctx)
                val ids = m.getAppWidgetIds(ComponentName(ctx, PhrasometreWidget::class.java))
                if (ids.isEmpty()) return
                val v = construire(ctx)
                ids.forEach { m.updateAppWidget(it, v) }
            } catch (e: Exception) {
                // ignoré
            }
        }
    }
}
