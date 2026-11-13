package mg.sitraka.phrasometre

import android.content.Context
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID

/**
 * Source unique des données.
 * - Hors connexion : tout est local (fichier JSON).
 * - Connecté : chaque ajout / modification / suppression est écrit dans Firestore
 *   (cache hors ligne intégré) et les écouteurs gardent l'état à jour.
 */
class Depot(private val ctx: Context) {
    private val db = FirebaseFirestore.getInstance()
    private val etat = MutableStateFlow(LocalStore.charger(ctx))
    val donnees: StateFlow<Donnees> = etat.asStateFlow()

    private val _cloud = MutableStateFlow(false)
    val cloud: StateFlow<Boolean> = _cloud.asStateFlow()

    private var uid: String? = null
    private var ecouteurs: List<ListenerRegistration> = emptyList()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        scope.launch {
            etat.collectLatest { d ->
                delay(250)
                LocalStore.sauver(ctx, d)
                PhrasometreWidget.rafraichir(ctx)
            }
        }
    }

    private fun ref(u: String, nom: String) = db.collection("users").document(u).collection(nom)

    private fun mapPhrase(p: Phrase): Map<String, Any> =
        hashMapOf("mois" to p.mois, "texte" to p.texte, "ordre" to p.ordre, "objectif" to p.objectif)

    private fun mapClic(c: Clic): Map<String, Any> =
        hashMapOf("phraseId" to c.phraseId, "mois" to c.mois, "jour" to c.jour, "ts" to c.ts)

    // ───────────── Connexion / déconnexion ─────────────

    fun connecter(nouvelUid: String, migrerLocal: Boolean) {
        if (uid == nouvelUid) return
        arreter()
        uid = nouvelUid
        _cloud.value = true
        if (migrerLocal) migrer(nouvelUid)
        demarrer(nouvelUid)
    }

    fun deconnecter() {
        arreter()
        uid = null
        _cloud.value = false
        etat.value = Donnees()
        LocalStore.vider(ctx)
    }

    private fun arreter() {
        ecouteurs.forEach { it.remove() }
        ecouteurs = emptyList()
    }

    private fun migrer(u: String) {
        val d = etat.value
        if (d.groupes.isEmpty() && d.phrases.isEmpty() && d.clics.isEmpty()) return
        val docs = ArrayList<Pair<com.google.firebase.firestore.DocumentReference, Map<String, Any>>>()
        d.phrases.forEach { docs.add(ref(u, "phrases").document(it.id) to mapPhrase(it)) }
        d.clics.forEach { docs.add(ref(u, "clics").document(it.id) to mapClic(it)) }
        docs.chunked(400).forEach { lot ->
            val b = db.batch()
            lot.forEach { (r, m) -> b.set(r, m) }
            b.commit()
        }
        val locaux = d.groupes
        ref(u, "groupes").get()
            .addOnSuccessListener { snap ->
                val existants = snap.documents.map { it.id }.toSet()
                locaux.filter { it.mois !in existants }.forEach {
                    ref(u, "groupes").document(it.mois).set(mapOf("max" to it.max))
                }
            }
            .addOnFailureListener {
                locaux.forEach { ref(u, "groupes").document(it.mois).set(mapOf("max" to it.max)) }
            }
    }

    private fun demarrer(u: String) {
        val premier = booleanArrayOf(true, true, true)

        val l1 = ref(u, "groupes").addSnapshotListener { snap, err ->
            if (err != null || snap == null) return@addSnapshotListener
            if (premier[0]) {
                premier[0] = false
                if (snap.metadata.isFromCache && snap.isEmpty) return@addSnapshotListener
            }
            val liste = snap.documents.map { Groupe(it.id, (it.getLong("max") ?: MAX_DEFAUT.toLong()).toInt()) }
            etat.update { it.copy(groupes = liste) }
        }

        val l2 = ref(u, "phrases").addSnapshotListener { snap, err ->
            if (err != null || snap == null) return@addSnapshotListener
            if (premier[1]) {
                premier[1] = false
                if (snap.metadata.isFromCache && snap.isEmpty) return@addSnapshotListener
            }
            val liste = snap.documents.mapNotNull {
                val mois = it.getString("mois") ?: return@mapNotNull null
                Phrase(it.id, mois, it.getString("texte") ?: "", (it.getLong("ordre") ?: 0L).toInt(), (it.getLong("objectif") ?: 0L).toInt())
            }
            etat.update { it.copy(phrases = liste) }
        }

        val l3 = ref(u, "clics").addSnapshotListener { snap, err ->
            if (err != null || snap == null) return@addSnapshotListener
            if (premier[2]) {
                premier[2] = false
                if (snap.metadata.isFromCache && snap.isEmpty) return@addSnapshotListener
            }
            val liste = snap.documents.mapNotNull {
                val jour = it.getString("jour") ?: return@mapNotNull null
                val mois = it.getString("mois") ?: return@mapNotNull null
                Clic(it.id, it.getString("phraseId") ?: "", mois, jour, it.getLong("ts") ?: 0L)
            }
            etat.update { it.copy(clics = liste) }
        }
        ecouteurs = listOf(l1, l2, l3)
    }

    // ───────────── Écritures de base ─────────────

    private fun putGroupe(g: Groupe) {
        etat.update { d -> d.copy(groupes = d.groupes.filter { it.mois != g.mois } + g) }
        uid?.let { ref(it, "groupes").document(g.mois).set(mapOf("max" to g.max)) }
    }

    private fun putPhrase(p: Phrase) {
        etat.update { d -> d.copy(phrases = d.phrases.filter { it.id != p.id } + p) }
        uid?.let { ref(it, "phrases").document(p.id).set(mapPhrase(p)) }
    }

    private fun putClic(c: Clic) {
        etat.update { d -> d.copy(clics = d.clics + c) }
        uid?.let { ref(it, "clics").document(c.id).set(mapClic(c)) }
    }

    private fun supprDocs(nom: String, ids: Collection<String>) {
        val u = uid ?: return
        ids.chunked(400).forEach { lot ->
            val b = db.batch()
            lot.forEach { b.delete(ref(u, nom).document(it)) }
            b.commit()
        }
    }

    private fun nouveauClic(p: Phrase) = Clic(
        UUID.randomUUID().toString(), p.id, p.mois,
        LocalDate.now().toString(), System.currentTimeMillis()
    )

    // ───────────── Opérations publiques ─────────────

    fun maxDuGroupe(mois: String): Int =
        etat.value.groupes.firstOrNull { it.mois == mois }?.max ?: MAX_DEFAUT

    fun ajouterPhrase(mois: String, texte: String): Boolean {
        val propre = texte.trim()
        if (propre.isEmpty()) return false
        val d = etat.value
        val max = maxDuGroupe(mois)
        val existantes = d.phrases.filter { it.mois == mois }
        if (existantes.size >= max) return false
        if (d.groupes.none { it.mois == mois }) putGroupe(Groupe(mois, max))
        putPhrase(Phrase(UUID.randomUUID().toString(), mois, propre, (existantes.maxOfOrNull { it.ordre } ?: 0) + 1, 0))
        return true
    }

    fun ajouterPlusieurs(mois: String, lignes: List<String>): Int {
        var n = 0
        for (l in lignes) {
            if (l.isBlank()) continue
            if (ajouterPhrase(mois, l)) n++ else break
        }
        return n
    }

    fun modifierPhrase(id: String, texte: String, objectif: Int) {
        val p = etat.value.phrases.firstOrNull { it.id == id } ?: return
        putPhrase(p.copy(texte = texte.trim(), objectif = objectif.coerceAtLeast(0)))
    }

    fun supprimerPhrase(id: String) {
        val clicsIds = etat.value.clics.filter { it.phraseId == id }.map { it.id }
        etat.update { d -> d.copy(phrases = d.phrases.filter { it.id != id }, clics = d.clics.filter { it.phraseId != id }) }
        uid?.let { ref(it, "phrases").document(id).delete() }
        supprDocs("clics", clicsIds)
    }

    fun definirMax(mois: String, max: Int) = putGroupe(Groupe(mois, max.coerceIn(1, 500)))

    fun supprimerGroupe(mois: String) {
        val d = etat.value
        val idsPhrases = d.phrases.filter { it.mois == mois }.map { it.id }
        val idsClics = d.clics.filter { it.mois == mois }.map { it.id }
        etat.update { x ->
            x.copy(
                groupes = x.groupes.filter { it.mois != mois },
                phrases = x.phrases.filter { it.mois != mois },
                clics = x.clics.filter { it.mois != mois }
            )
        }
        uid?.let { ref(it, "groupes").document(mois).delete() }
        supprDocs("phrases", idsPhrases)
        supprDocs("clics", idsClics)
    }

    fun copierDepuis(source: String, cible: String): Int {
        val d = etat.value
        val sources = d.phrases.filter { it.mois == source }.sortedBy { it.ordre }
        if (sources.isEmpty()) return 0
        if (d.groupes.none { it.mois == cible }) putGroupe(Groupe(cible, maxOf(maxDuGroupe(source), sources.size)))
        val deja = d.phrases.filter { it.mois == cible }.map { it.texte.lowercase() }.toSet()
        var n = 0
        for (p in sources) {
            if (p.texte.lowercase() in deja) continue
            if (ajouterPhrase(cible, p.texte)) n++ else break
        }
        return n
    }

    /** Ajoute un clic et renvoie son identifiant (pour pouvoir l'annuler). */
    fun cliquer(phraseId: String): String? {
        val p = etat.value.phrases.firstOrNull { it.id == phraseId } ?: return null
        val c = nouveauClic(p)
        putClic(c)
        return c.id
    }

    fun annulerClic(id: String) {
        etat.update { d -> d.copy(clics = d.clics.filter { it.id != id }) }
        supprDocs("clics", listOf(id))
    }

    fun definirCompteur(phraseId: String, n: Int) {
        val p = etat.value.phrases.firstOrNull { it.id == phraseId } ?: return
        val actuels = etat.value.clics.filter { it.phraseId == phraseId }.sortedBy { it.ts }
        val cible = n.coerceIn(0, 99999)
        if (cible > actuels.size) {
            repeat(cible - actuels.size) { putClic(nouveauClic(p)) }
        } else if (cible < actuels.size) {
            val aSupprimer = actuels.takeLast(actuels.size - cible).map { it.id }
            val set = aSupprimer.toSet()
            etat.update { d -> d.copy(clics = d.clics.filter { it.id !in set }) }
            supprDocs("clics", aSupprimer)
        }
    }

    fun toutSupprimer() {
        val d = etat.value
        etat.value = Donnees()
        supprDocs("clics", d.clics.map { it.id })
        supprDocs("phrases", d.phrases.map { it.id })
        supprDocs("groupes", d.groupes.map { it.mois })
    }
}
