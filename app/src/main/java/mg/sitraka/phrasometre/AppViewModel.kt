package mg.sitraka.phrasometre

import android.app.Activity
import android.app.Application
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx = app.applicationContext
    val depot = Depot(ctx)
    val donnees: StateFlow<Donnees> = depot.donnees

    private val _reglages = MutableStateFlow(ReglagesStore.charger(ctx))
    val reglages: StateFlow<Reglages> = _reglages

    val moisAccueil = MutableStateFlow(moisCourant())
    val moisSaisie = MutableStateFlow(moisCourant())

    val utilisateur = MutableStateFlow<Utilisateur?>(null)
    val connexionEnCours = MutableStateFlow(false)
    val messages = MutableSharedFlow<String>(extraBufferCapacity = 8)

    private val auth = FirebaseAuth.getInstance()
    private val ecouteurAuth = FirebaseAuth.AuthStateListener { a ->
        utilisateur.value = a.currentUser?.let { Utilisateur(it.uid, it.displayName ?: "", it.email ?: "") }
    }

    init {
        auth.addAuthStateListener(ecouteurAuth)
        auth.currentUser?.let { depot.connecter(it.uid, false) }
        val r = _reglages.value
        if (r.rappelActif) Rappels.planifier(ctx, r.rappelHeure, r.rappelMinute)
    }

    override fun onCleared() {
        auth.removeAuthStateListener(ecouteurAuth)
        super.onCleared()
    }

    fun modifierReglages(f: (Reglages) -> Reglages) {
        _reglages.update(f)
        ReglagesStore.sauver(ctx, _reglages.value)
    }

    fun definirRappel(actif: Boolean, heure: Int, minute: Int) {
        modifierReglages { it.copy(rappelActif = actif, rappelHeure = heure, rappelMinute = minute) }
        if (actif) Rappels.planifier(ctx, heure, minute) else Rappels.annuler(ctx)
    }

    fun connecterGoogle(activity: Activity) {
        if (connexionEnCours.value) return
        viewModelScope.launch {
            connexionEnCours.value = true
            try {
                val cm = CredentialManager.create(activity)
                val option = GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    .setServerClientId(activity.getString(R.string.default_web_client_id))
                    .setAutoSelectEnabled(false)
                    .build()
                val requete = GetCredentialRequest.Builder().addCredentialOption(option).build()
                val resultat = cm.getCredential(activity, requete)
                val cred = resultat.credential
                if (cred is CustomCredential && cred.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val token = GoogleIdTokenCredential.createFrom(cred.data).idToken
                    val res = auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await()
                    val uid = res.user?.uid
                    if (uid != null) {
                        depot.connecter(uid, true)
                        messages.emit("Connecté : synchronisation activée.")
                    }
                } else {
                    messages.emit("Type d'identifiant non pris en charge.")
                }
            } catch (e: GetCredentialCancellationException) {
                messages.emit("Connexion annulée.")
            } catch (e: NoCredentialException) {
                messages.emit("Aucun compte Google disponible sur cet appareil.")
            } catch (e: Exception) {
                Log.e("Phrasometre", "Connexion Google", e)
                messages.emit("Échec de la connexion Google (${e.javaClass.simpleName}). Réessayez.")
            } finally {
                connexionEnCours.value = false
            }
        }
    }

    fun deconnecter() {
        viewModelScope.launch {
            auth.signOut()
            try {
                CredentialManager.create(ctx).clearCredentialState(ClearCredentialStateRequest())
            } catch (e: Exception) {
                // ignoré
            }
            depot.deconnecter()
            messages.emit("Déconnecté. Vos données restent dans votre compte Google.")
        }
    }
}
