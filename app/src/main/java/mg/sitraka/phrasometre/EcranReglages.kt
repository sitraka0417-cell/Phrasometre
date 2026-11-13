@file:OptIn(ExperimentalMaterial3Api::class)

package mg.sitraka.phrasometre

import android.Manifest
import android.app.TimePickerDialog
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun EcranReglages(vm: AppViewModel, snack: SnackbarHostState, onMenu: () -> Unit) {
    val r by vm.reglages.collectAsState()
    val user by vm.utilisateur.collectAsState()
    val enCours by vm.connexionEnCours.collectAsState()
    val d by vm.donnees.collectAsState()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var confirmerDeconnexion by remember { mutableStateOf(false) }
    var confirmerSuppression by remember { mutableStateOf(false) }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (!ok) scope.launch { snack.showSnackbar("Autorisez les notifications dans les réglages Android pour recevoir le rappel.") }
    }
    val csvLanceur = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) {
            val ok = ecrireUri(ctx, uri) { it.write(genererCsv(d).toByteArray(Charsets.UTF_8)) }
            scope.launch { snack.showSnackbar(if (ok) "Export CSV enregistré." else "Échec de l'export CSV.") }
        }
    }

    Scaffold(
        topBar = { BarreTitre("Paramètres", onMenu) },
        snackbarHost = { SnackbarHost(snack) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = padding.calculateTopPadding() + 4.dp,
                bottom = padding.calculateBottomPadding() + 32.dp
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Compte
            item {
                Section("Compte et synchronisation") {
                    if (user != null) {
                        val u = user!!
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    (u.nom.ifBlank { u.email }).take(1).uppercase(),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    style = MaterialTheme.typography.titleLarge
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(u.nom.ifBlank { "Compte Google" }, style = MaterialTheme.typography.titleMedium)
                                Text(u.email, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CloudDone, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Synchronisation active : chaque ajout, modification ou suppression est sauvegardé automatiquement.",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        OutlinedButton(onClick = { confirmerDeconnexion = true }, modifier = Modifier.fillMaxWidth()) {
                            Text("Se déconnecter")
                        }
                    } else {
                        Text(
                            "Mode local : vos données restent sur ce téléphone. Connectez-vous pour les sauvegarder et les retrouver si vous changez d'appareil.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(14.dp))
                        BoutonGoogle(
                            onClick = { vm.connecterGoogle(ctx.activite()) },
                            enCours = enCours,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // Apparence
            item {
                Section("Apparence", icone = { Icon(Icons.Default.Palette, contentDescription = null) }) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        TuileTheme("Auto", Color(0xFFF5F7F6), Color(0xFF0E1413), r.theme == "auto") { vm.modifierReglages { it.copy(theme = "auto") } }
                        TuileTheme("Clair", Color(0xFFF5F7F6), Color(0xFF0E6B68), r.theme == "clair") { vm.modifierReglages { it.copy(theme = "clair") } }
                        TuileTheme("Sombre", Color(0xFF0E1413), Color(0xFF7FD6D1), r.theme == "sombre") { vm.modifierReglages { it.copy(theme = "sombre") } }
                        TuileTheme("Spécial", Color(0xFF150F26), Color(0xFFD0A8FF), r.theme == "special") { vm.modifierReglages { it.copy(theme = "special") } }
                    }
                    Spacer(Modifier.height(18.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.FormatSize, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("Taille du texte", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Text("${(r.echelle * 100).toInt()} %", style = MaterialTheme.typography.labelLarge)
                    }
                    Slider(
                        value = r.echelle,
                        onValueChange = { v -> vm.modifierReglages { it.copy(echelle = v) } },
                        valueRange = 0.8f..1.4f,
                        steps = 5
                    )
                    Text(
                        "Exemple : voici une phrase à compter.",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant).padding(12.dp)
                    )
                }
            }

            // Comptage
            item {
                Section("Comptage") {
                    LigneSwitch(
                        icone = { Icon(Icons.Default.TouchApp, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        titre = "Confirmer chaque clic",
                        detail = "Évite les clics accidentels.",
                        coche = r.confirmer,
                        onChange = { v -> vm.modifierReglages { it.copy(confirmer = v) } }
                    )
                    LigneSwitch(
                        icone = { Icon(Icons.Default.Vibration, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        titre = "Vibration au clic",
                        detail = null,
                        coche = r.vibration,
                        onChange = { v -> vm.modifierReglages { it.copy(vibration = v) } }
                    )
                }
            }

            // Rappel
            item {
                Section("Rappel quotidien") {
                    LigneSwitch(
                        icone = { Icon(Icons.Default.Alarm, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        titre = "Me rappeler de compter",
                        detail = "Notification si aucun clic n'a été fait aujourd'hui.",
                        coche = r.rappelActif,
                        onChange = { v ->
                            if (v && Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            vm.definirRappel(v, r.rappelHeure, r.rappelMinute)
                        }
                    )
                    if (r.rappelActif) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                TimePickerDialog(ctx, { _, h, m -> vm.definirRappel(true, h, m) }, r.rappelHeure, r.rappelMinute, true).show()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Heure du rappel : %02d:%02d".format(r.rappelHeure, r.rappelMinute)) }
                    }
                }
            }

            // Données
            item {
                Section("Données") {
                    OutlinedButton(onClick = { csvLanceur.launch("phrasometre-donnees.csv") }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Exporter toutes les données (CSV)")
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { confirmerSuppression = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Supprimer toutes les données")
                    }
                }
            }
        }
    }

    if (confirmerDeconnexion) {
        AlertDialog(
            onDismissRequest = { confirmerDeconnexion = false },
            title = { Text("Se déconnecter ?") },
            text = { Text("Les données seront retirées de ce téléphone mais restent sauvegardées dans votre compte Google. Reconnectez-vous pour les retrouver.") },
            confirmButton = {
                TextButton(onClick = { confirmerDeconnexion = false; vm.deconnecter() }) { Text("Se déconnecter") }
            },
            dismissButton = { TextButton(onClick = { confirmerDeconnexion = false }) { Text("Annuler") } }
        )
    }
    if (confirmerSuppression) {
        AlertDialog(
            onDismissRequest = { confirmerSuppression = false },
            title = { Text("Tout supprimer ?") },
            text = { Text("Toutes les phrases, tous les groupes et tous les clics seront supprimés" + (if (user != null) ", y compris dans votre compte synchronisé." else ".") + " Action définitive.") },
            confirmButton = {
                TextButton(onClick = { confirmerSuppression = false; vm.depot.toutSupprimer() }) {
                    Text("Tout supprimer", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmerSuppression = false }) { Text("Annuler") } }
        )
    }
}

@Composable
private fun Section(titre: String, icone: (@Composable () -> Unit)? = null, contenu: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icone != null) {
                    icone()
                    Spacer(Modifier.width(8.dp))
                }
                Text(titre, style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(12.dp))
            contenu()
        }
    }
}

@Composable
private fun LigneSwitch(
    icone: @Composable () -> Unit,
    titre: String,
    detail: String?,
    coche: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!coche) }.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icone()
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(titre, style = MaterialTheme.typography.bodyLarge)
            if (detail != null) Text(detail, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = coche, onCheckedChange = onChange)
    }
}

@Composable
private fun TuileTheme(libelle: String, a: Color, b: Color, selectionnee: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(52.dp).clip(CircleShape)
                .background(Brush.linearGradient(0f to a, 0.5f to a, 0.5f to b, 1f to b))
                .border(
                    if (selectionnee) 3.dp else 1.dp,
                    if (selectionnee) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    CircleShape
                )
        )
        Spacer(Modifier.height(6.dp))
        Text(
            libelle,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selectionnee) FontWeight.Bold else FontWeight.Normal,
            textAlign = TextAlign.Center
        )
    }
}
