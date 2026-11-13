@file:OptIn(ExperimentalMaterial3Api::class)

package mg.sitraka.phrasometre

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.time.YearMonth

@Composable
fun EcranSaisie(vm: AppViewModel, snack: SnackbarHostState, onMenu: () -> Unit) {
    val d by vm.donnees.collectAsState()
    val mois by vm.moisSaisie.collectAsState()
    val scope = rememberCoroutineScope()

    val phrases = remember(d, mois) { d.phrases.filter { it.mois == mois }.sortedBy { it.ordre } }
    val comptes = remember(d) { d.clics.groupingBy { it.phraseId }.eachCount() }
    val max = d.groupes.firstOrNull { it.mois == mois }?.max ?: MAX_DEFAUT
    val complet = phrases.size >= max
    val moisPrecedent = YearMonth.parse(mois).minusMonths(1).toString()
    val precedentDisponible = d.phrases.any { it.mois == moisPrecedent }

    var texte by rememberSaveable { mutableStateOf("") }
    var editer by remember { mutableStateOf<Phrase?>(null) }
    var supprimer by remember { mutableStateOf<Phrase?>(null) }
    var dialogMax by remember { mutableStateOf(false) }
    var dialogListe by remember { mutableStateOf(false) }
    var dialogGroupe by remember { mutableStateOf(false) }

    fun message(m: String) { scope.launch { snack.showSnackbar(m) } }

    fun ajouter() {
        if (texte.isBlank()) return
        if (vm.depot.ajouterPhrase(mois, texte)) texte = ""
        else message("Maximum atteint : augmentez le nombre de phrases autorisées.")
    }

    Scaffold(
        topBar = { BarreTitre("Saisie des phrases", onMenu) },
        snackbarHost = { SnackbarHost(snack) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier.imePadding(),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 32.dp
            )
        ) {
            item { SelecteurMois(mois, { vm.moisSaisie.value = it }) }

            item {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Phrases du groupe", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "${phrases.size} / $max",
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { vm.depot.definirMax(mois, max - 1) },
                                enabled = max > maxOf(phrases.size, 1)
                            ) { Icon(Icons.Default.Remove, contentDescription = "Diminuer le maximum") }
                            FilledTonalButton(onClick = { dialogMax = true }) { Text("Maximum : $max") }
                            IconButton(
                                onClick = { vm.depot.definirMax(mois, max + 1) },
                                enabled = max < 500
                            ) { Icon(Icons.Default.Add, contentDescription = "Augmenter le maximum") }
                        }
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { if (max > 0) (phrases.size.toFloat() / max).coerceIn(0f, 1f) else 0f },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surface
                        )
                    }
                }
            }

            item {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = texte,
                    onValueChange = { texte = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Nouvelle phrase") },
                    enabled = !complet,
                    supportingText = {
                        if (complet) Text("Groupe complet : augmentez le maximum pour en ajouter.")
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { ajouter() }),
                    shape = RoundedCornerShape(16.dp),
                    trailingIcon = {
                        IconButton(onClick = { ajouter() }, enabled = !complet && texte.isNotBlank()) {
                            Icon(Icons.Default.Add, contentDescription = "Ajouter la phrase")
                        }
                    }
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { dialogListe = true }, enabled = !complet) { Text("Ajouter une liste") }
                    if (precedentDisponible) {
                        TextButton(onClick = {
                            val n = vm.depot.copierDepuis(moisPrecedent, mois)
                            message(if (n > 0) "$n phrase(s) copiée(s) depuis ${moisLibelle(moisPrecedent)}." else "Rien à copier.")
                        }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Copier le mois précédent")
                        }
                    }
                }
            }

            if (phrases.isEmpty()) {
                item {
                    Text(
                        "Aucune phrase pour ${moisLibelle(mois)}.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                }
            } else {
                items(phrases, key = { it.id }) { p ->
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Row(Modifier.padding(start = 14.dp, top = 6.dp, bottom = 6.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
                                Text(p.texte, style = MaterialTheme.typography.bodyLarge)
                                val n = comptes[p.id] ?: 0
                                Text(
                                    (if (n > 1) "$n clics" else "$n clic") + (if (p.objectif > 0) ", objectif ${p.objectif}" else ""),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { editer = p }) { Icon(Icons.Default.Edit, contentDescription = "Modifier") }
                            IconButton(onClick = { supprimer = p }) {
                                Icon(Icons.Default.Delete, contentDescription = "Supprimer", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }

            if (phrases.isNotEmpty() || d.groupes.any { it.mois == mois }) {
                item {
                    Spacer(Modifier.height(16.dp))
                    OutlinedButton(
                        onClick = { dialogGroupe = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Supprimer tout le groupe de ${moisLibelle(mois)}")
                    }
                }
            }
        }
    }

    editer?.let { p ->
        DialogEdition(
            p = p,
            compteur = comptes[p.id] ?: 0,
            onDismiss = { editer = null },
            onValider = { t, obj, cpt ->
                vm.depot.modifierPhrase(p.id, t, obj)
                if (cpt != (comptes[p.id] ?: 0)) vm.depot.definirCompteur(p.id, cpt)
                editer = null
            }
        )
    }

    supprimer?.let { p ->
        AlertDialog(
            onDismissRequest = { supprimer = null },
            title = { Text("Supprimer la phrase ?") },
            text = { Text("« ${p.texte} » et tous ses clics seront supprimés.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.depot.supprimerPhrase(p.id)
                    supprimer = null
                }) { Text("Supprimer", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { supprimer = null }) { Text("Annuler") } }
        )
    }

    if (dialogGroupe) {
        AlertDialog(
            onDismissRequest = { dialogGroupe = false },
            title = { Text("Supprimer le groupe ?") },
            text = { Text("Les ${phrases.size} phrases de ${moisLibelle(mois)} et tous leurs clics seront supprimés. Cette action est définitive.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.depot.supprimerGroupe(mois)
                    dialogGroupe = false
                }) { Text("Tout supprimer", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { dialogGroupe = false }) { Text("Annuler") } }
        )
    }

    if (dialogMax) {
        var saisie by remember { mutableStateOf(max.toString()) }
        val valeur = saisie.toIntOrNull()
        val minimum = maxOf(phrases.size, 1)
        val valide = valeur != null && valeur in minimum..500
        AlertDialog(
            onDismissRequest = { dialogMax = false },
            title = { Text("Nombre maximum de phrases") },
            text = {
                OutlinedTextField(
                    value = saisie,
                    onValueChange = { saisie = it.filter(Char::isDigit).take(3) },
                    singleLine = true,
                    isError = !valide,
                    supportingText = { Text("Entre $minimum et 500. Modifiable à tout moment.") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            },
            confirmButton = {
                TextButton(enabled = valide, onClick = {
                    vm.depot.definirMax(mois, valeur ?: max)
                    dialogMax = false
                }) { Text("Enregistrer") }
            },
            dismissButton = { TextButton(onClick = { dialogMax = false }) { Text("Annuler") } }
        )
    }

    if (dialogListe) {
        var brut by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { dialogListe = false },
            title = { Text("Ajouter une liste") },
            text = {
                OutlinedTextField(
                    value = brut,
                    onValueChange = { brut = it },
                    minLines = 6,
                    maxLines = 10,
                    label = { Text("Une phrase par ligne") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(enabled = brut.isNotBlank(), onClick = {
                    val lignes = brut.lines().map { it.trim() }.filter { it.isNotEmpty() }
                    val n = vm.depot.ajouterPlusieurs(mois, lignes)
                    dialogListe = false
                    message(
                        if (n < lignes.size) "$n phrase(s) ajoutée(s) : maximum atteint."
                        else "$n phrase(s) ajoutée(s)."
                    )
                }) { Text("Ajouter") }
            },
            dismissButton = { TextButton(onClick = { dialogListe = false }) { Text("Annuler") } }
        )
    }
}

@Composable
private fun DialogEdition(p: Phrase, compteur: Int, onDismiss: () -> Unit, onValider: (String, Int, Int) -> Unit) {
    var t by remember { mutableStateOf(p.texte) }
    var obj by remember { mutableStateOf(if (p.objectif > 0) p.objectif.toString() else "") }
    var cpt by remember { mutableStateOf(compteur.toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Modifier la phrase") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = t, onValueChange = { t = it }, label = { Text("Phrase") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    value = cpt,
                    onValueChange = { cpt = it.filter(Char::isDigit).take(5) },
                    label = { Text("Nombre de clics") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = obj,
                    onValueChange = { obj = it.filter(Char::isDigit).take(5) },
                    label = { Text("Objectif (facultatif)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(enabled = t.isNotBlank(), onClick = {
                onValider(t.trim(), obj.toIntOrNull() ?: 0, cpt.toIntOrNull() ?: compteur)
            }) { Text("Enregistrer", fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    )
}
