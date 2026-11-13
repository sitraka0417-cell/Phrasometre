package mg.sitraka.phrasometre

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

data class Phrase(val id: String, val mois: String, val texte: String, val ordre: Int, val objectif: Int)
data class Groupe(val mois: String, val max: Int)
data class Clic(val id: String, val phraseId: String, val mois: String, val jour: String, val ts: Long)

data class Donnees(
    val groupes: List<Groupe> = emptyList(),
    val phrases: List<Phrase> = emptyList(),
    val clics: List<Clic> = emptyList()
)

data class Utilisateur(val uid: String, val nom: String, val email: String)

data class Reglages(
    val theme: String = "auto",
    val echelle: Float = 1f,
    val confirmer: Boolean = true,
    val vibration: Boolean = true,
    val rappelActif: Boolean = false,
    val rappelHeure: Int = 20,
    val rappelMinute: Int = 0,
    val bandeauMasque: Boolean = false
)

const val MAX_DEFAUT = 30

fun Context.activite(): Activity {
    var c: Context = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    throw IllegalStateException("Aucune activité")
}
