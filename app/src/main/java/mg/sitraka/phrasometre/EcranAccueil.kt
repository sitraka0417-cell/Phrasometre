@file:OptIn(ExperimentalMaterial3Api::class)

package mg.sitraka.phrasometre

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
fun EcranAccueil(vm: AppViewModel, snack: SnackbarHostState, onMenu: () -> Unit, onSaisie: () -> Unit) {
    val d by vm.donnees.collectAsState()
    val reglages by vm.reglages.collectAsState()
    val mois by vm.moisAccueil.collectAsState()
    val user by vm.utilisateur.collectAsState()
    val enCours by vm.connexionEnCours.collectAsState()
    val ctx = LocalContext.current
    val haptique = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var aConfirmer by remember { mutableStateOf<Phrase?>(null) }

    val phrases = remember(d, mois) { d.phrases.filter { it.mois == mois }.sortedBy { it.ordre } }
    val comptes = remember(d) { d.clics.groupingBy { it.phraseId }.eachCount() }
    val aujourdhui = LocalDate.now().toString()
    val totalMois = phrases.sumOf { comptes[it.id] ?: 0 }
    val totalJour = remember(d) { d.clics.count { it.jour == aujourdhui } }
    val serie = remember(d) { serieActuelle(d.clics) }

    fun compter(p: Phrase) {
        val id = vm.depot.cliquer(p.id) ?: return
        if (reglages.vibration) haptique.performHapticFeedback(HapticFeedbackType.LongPress)
        scope.launch {
            snack.currentSnackbarData?.dismiss()
            val r = snack.showSnackbar(
                message = "+1 sur « ${p.texte} »",
                actionLabel = "Annuler",
                duration = SnackbarDuration.Short
            )
            if (r == SnackbarResult.ActionPerformed) vm.depot.annulerClic(id)
        }
    }

    Scaffold(
        topBar = { BarreTitre("Phrasomètre", onMenu) },
        snackbarHost = { SnackbarHost(snack) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 24.dp
            )
        ) {
            item { SelecteurMois(mois, { vm.moisAccueil.value = it }) }
            item {
                CarteResume(
                    jour = totalJour,
                    mois = totalMois,
                    libelleMois = if (mois == moisCourant()) "Ce mois" else "Ce groupe",
                    serie = serie
                )
            }
            if (user == null && !reglages.bandeauMasque) {
                item {
                    BandeauConnexion(
                        enCours = enCours,
                        onConnexion = { vm.connecterGoogle(ctx.activite()) },
                        onPlusTard = { vm.modifierReglages { it.copy(bandeauMasque = true) } }
                    )
                }
            }
            if (phrases.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Aucune phrase pour ${moisLibelle(mois)}", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Ajoutez les phrases de ce mois dans la section Saisie, puis touchez-les ici pour les compter.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(16.dp))
                            Button(onClick = onSaisie) { Text("Saisir des phrases") }
                        }
                    }
                }
            } else {
                items(phrases, key = { it.id }) { p ->
                    CartePhrase(p, comptes[p.id] ?: 0) {
                        if (reglages.confirmer) aConfirmer = p else compter(p)
                    }
                }
            }
        }
    }

    aConfirmer?.let { p ->
        AlertDialog(
            onDismissRequest = { aConfirmer = null },
            title = { Text("Confirmer le clic") },
            text = { Text("Ajouter +1 à « ${p.texte} » ?\nCompteur actuel : ${comptes[p.id] ?: 0}") },
            confirmButton = {
                TextButton(onClick = {
                    aConfirmer = null
                    compter(p)
                }) { Text("Oui, compter") }
            },
            dismissButton = { TextButton(onClick = { aConfirmer = null }) { Text("Annuler") } }
        )
    }
}

@Composable
private fun CarteResume(jour: Int, mois: Int, libelleMois: String, serie: Int) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
    ) {
        Row(Modifier.padding(vertical = 16.dp, horizontal = 8.dp)) {
            Stat("Aujourd'hui", "$jour", Modifier.weight(1f))
            Stat(libelleMois, "$mois", Modifier.weight(1f))
            Stat("Série", if (serie > 1) "$serie jours" else "$serie jour", Modifier.weight(1f))
        }
    }
}

@Composable
private fun Stat(label: String, valeur: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(valeur, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onPrimaryContainer, maxLines = 1)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
    }
}

@Composable
private fun BandeauConnexion(enCours: Boolean, onConnexion: () -> Unit, onPlusTard: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Gardez vos phrases en sécurité", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "Connectez-vous pour sauvegarder vos données et les retrouver sur un autre téléphone.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            BoutonGoogle(onClick = onConnexion, enCours = enCours, modifier = Modifier.fillMaxWidth())
            TextButton(onClick = onPlusTard, modifier = Modifier.align(Alignment.End)) { Text("Plus tard") }
        }
    }
}

@Composable
private fun CartePhrase(p: Phrase, n: Int, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(p.texte, style = MaterialTheme.typography.bodyLarge)
                if (p.objectif > 0) {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { (n.toFloat() / p.objectif).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (n >= p.objectif) "Objectif atteint : $n / ${p.objectif}" else "$n / ${p.objectif}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            BadgeCompteur(n)
        }
    }
}
