package mg.sitraka.phrasometre

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Copie locale des données (mode hors connexion, widget, rappels). */
object LocalStore {
    private const val FICHIER = "donnees.json"

    @Synchronized
    fun charger(ctx: Context): Donnees {
        val f = File(ctx.filesDir, FICHIER)
        if (!f.exists()) return Donnees()
        return try {
            val o = JSONObject(f.readText())
            val g = o.optJSONArray("groupes") ?: JSONArray()
            val p = o.optJSONArray("phrases") ?: JSONArray()
            val c = o.optJSONArray("clics") ?: JSONArray()
            Donnees(
                groupes = (0 until g.length()).map {
                    val x = g.getJSONObject(it)
                    Groupe(x.getString("mois"), x.getInt("max"))
                },
                phrases = (0 until p.length()).map {
                    val x = p.getJSONObject(it)
                    Phrase(x.getString("id"), x.getString("mois"), x.getString("texte"), x.getInt("ordre"), x.optInt("objectif", 0))
                },
                clics = (0 until c.length()).map {
                    val x = c.getJSONObject(it)
                    Clic(x.getString("id"), x.getString("phraseId"), x.getString("mois"), x.getString("jour"), x.getLong("ts"))
                }
            )
        } catch (e: Exception) {
            Donnees()
        }
    }

    @Synchronized
    fun sauver(ctx: Context, d: Donnees) {
        try {
            val g = JSONArray()
            d.groupes.forEach { g.put(JSONObject().put("mois", it.mois).put("max", it.max)) }
            val p = JSONArray()
            d.phrases.forEach {
                p.put(
                    JSONObject().put("id", it.id).put("mois", it.mois).put("texte", it.texte)
                        .put("ordre", it.ordre).put("objectif", it.objectif)
                )
            }
            val c = JSONArray()
            d.clics.forEach {
                c.put(
                    JSONObject().put("id", it.id).put("phraseId", it.phraseId).put("mois", it.mois)
                        .put("jour", it.jour).put("ts", it.ts)
                )
            }
            val o = JSONObject().put("groupes", g).put("phrases", p).put("clics", c)
            val tmp = File(ctx.filesDir, "$FICHIER.tmp")
            tmp.writeText(o.toString())
            tmp.renameTo(File(ctx.filesDir, FICHIER))
        } catch (e: Exception) {
            // ignoré : la copie locale est un cache
        }
    }

    @Synchronized
    fun vider(ctx: Context) {
        File(ctx.filesDir, FICHIER).delete()
    }
}

object ReglagesStore {
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("reglages", Context.MODE_PRIVATE)

    fun charger(ctx: Context): Reglages {
        val p = prefs(ctx)
        val d = Reglages()
        return Reglages(
            theme = p.getString("theme", d.theme) ?: d.theme,
            echelle = p.getFloat("echelle", d.echelle),
            confirmer = p.getBoolean("confirmer", d.confirmer),
            vibration = p.getBoolean("vibration", d.vibration),
            rappelActif = p.getBoolean("rappelActif", d.rappelActif),
            rappelHeure = p.getInt("rappelHeure", d.rappelHeure),
            rappelMinute = p.getInt("rappelMinute", d.rappelMinute),
            bandeauMasque = p.getBoolean("bandeauMasque", d.bandeauMasque)
        )
    }

    fun sauver(ctx: Context, r: Reglages) {
        prefs(ctx).edit()
            .putString("theme", r.theme)
            .putFloat("echelle", r.echelle)
            .putBoolean("confirmer", r.confirmer)
            .putBoolean("vibration", r.vibration)
            .putBoolean("rappelActif", r.rappelActif)
            .putInt("rappelHeure", r.rappelHeure)
            .putInt("rappelMinute", r.rappelMinute)
            .putBoolean("bandeauMasque", r.bandeauMasque)
            .apply()
    }
}
