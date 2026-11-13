@file:OptIn(ExperimentalMaterial3Api::class)

package mg.sitraka.phrasometre

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.Locale

@Composable
fun EcranStats(vm: AppViewModel, snack: SnackbarHostState, onMenu: () -> Unit) {
    val d by vm.donnees.collectAsState()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var periodeNom by rememberSaveable { mutableStateOf(Periode.MOIS.name) }
    var modeNom by rememberSaveable { mutableStateOf(ModeStats.PHRASES.name) }
    var ancreTexte by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var moisTexte by rememberSaveable { mutableStateOf("1,2,3,4,5,6,7,8,9,10,11,12") }
    var filtre by rememberSaveable { mutableStateOf<String?>(null) }
    var menuExport by remember { mutableStateOf(false) }

    val periode = Periode.valueOf(periodeNom)
    val mode = ModeStats.valueOf(modeNom)
    val ancre = LocalDate.parse(ancreTexte)
    val moisSel = remember(moisTexte) { moisTexte.split(",").mapNotNull { it.toIntOrNull() }.toSet() }
    val res = remember(d, periode, mode, ancre, moisSel, filtre) { calculer(d, periode, mode, ancre, moisSel, filtre) }
    val libelle = libellePeriode(periode, ancre)
    val suivantPossible = plage(periode, ancre).fin.isBefore(LocalDate.now())

    fun message(m: String) { scope.launch { snack.showSnackbar(m) } }

    val csvLanceur = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) {
            val ok = ecrireUri(ctx, uri) { it.write(genererCsv(d).toByteArray(Charsets.UTF_8)) }
            message(if (ok) "Export CSV enregistré." else "Échec de l'export CSV.")
        }
    }
    val pdfLanceur = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) {
            val kpis = listOf(
                "Total des clics : ${res.total}",
                "Jours actifs : ${res.joursActifs}",
                "Série actuelle : ${res.serieActuelle} j (meilleure : ${res.meilleureSerie} j)"
            )
            val lignes = res.lignes.filter { it.valeur > 0 }.map { it.libelle to it.valeur }
            val ok = ecrireUri(ctx, uri) {
                genererPdf(it, "Phrasomètre : statistiques", "${periode.libelle} : $libelle", kpis, lignes)
            }
            message(if (ok) "Export PDF enregistré." else "Échec de l'export PDF.")
        }
    }

    Scaffold(
        topBar = {
            BarreTitre("Statistiques", onMenu, actions = {
                IconButton(onClick = { menuExport = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Exporter") }
                DropdownMenu(expanded = menuExport, onDismissRequest = { menuExport = false }) {
                    DropdownMenuItem(
                        text = { Text("Exporter tout en CSV") },
                        leadingIcon = { Icon(Icons.Default.TableChart, contentDescription = null) },
                        onClick = { menuExport = false; csvLanceur.launch("phrasometre-donnees.csv") }
                    )
                    DropdownMenuItem(
                        text = { Text("Exporter la vue en PDF") },
                        leadingIcon = { Icon(Icons.Default.PictureAsPdf, contentDescription = null) },
                        onClick = { menuExport = false; pdfLanceur.launch("phrasometre-stats.pdf") }
                    )
                }
            })
        },
        snackbarHost = { SnackbarHost(snack) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = padding.calculateTopPadding() + 4.dp,
                bottom = padding.calculateBottomPadding() + 32.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    Periode.entries.forEachIndexed { i, p ->
                        SegmentedButton(
                            selected = p == periode,
                            onClick = {
                                periodeNom = p.name
                                ancreTexte = LocalDate.now().toString()
                            },
                            shape = SegmentedButtonDefaults.itemShape(index = i, count = Periode.entries.size),
                            icon = {}
                        ) { Text(p.libelle, maxLines = 1) }
                    }
                }
            }
            item {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    ModeStats.entries.forEachIndexed { i, m ->
                        SegmentedButton(
                            selected = m == mode,
                            onClick = { modeNom = m.name; filtre = null },
                            shape = SegmentedButtonDefaults.itemShape(index = i, count = ModeStats.entries.size),
                            icon = {}
                        ) { Text(if (m == ModeStats.PHRASES) "Phrases" else "Groupes", maxLines = 1) }
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    IconButton(onClick = { ancreTexte = decaler(periode, ancre, -1).toString() }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Période précédente")
                    }
                    Text(libelle, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                    IconButton(onClick = { ancreTexte = decaler(periode, ancre, 1).toString() }, enabled = suivantPossible) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Période suivante")
                    }
                }
            }

            if (periode == Periode.ANNEE) {
                item {
                    Column {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AssistChip(onClick = { moisTexte = (1..12).joinToString(",") }, label = { Text("Tous les mois") })
                            AssistChip(onClick = { moisTexte = "" }, label = { Text("Aucun") })
                        }
                        Spacer(Modifier.height(4.dp))
                        for (ligne in 0 until 3) {
                            Row(Modifier.fillMaxWidth()) {
                                for (col in 0 until 4) {
                                    val m = ligne * 4 + col + 1
                                    FilterChip(
                                        selected = m in moisSel,
                                        onClick = {
                                            val n = if (m in moisSel) moisSel - m else moisSel + m
                                            moisTexte = n.sorted().joinToString(",")
                                        },
                                        label = {
                                            Text(
                                                nomMoisCourt(m).replaceFirstChar { it.uppercase(Locale.FRENCH) },
                                                modifier = Modifier.fillMaxWidth(),
                                                textAlign = TextAlign.Center,
                                                maxLines = 1
                                            )
                                        },
                                        modifier = Modifier.weight(1f).padding(horizontal = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Indicateurs
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        val variation = if (res.precedent > 0) (res.total - res.precedent) * 100 / res.precedent else null
                        CarteKpi(
                            titre = "Total des clics",
                            valeur = "${res.total}",
                            detail = when {
                                variation == null -> if (res.total > 0) "Aucune donnée avant" else null
                                variation >= 0 -> "+$variation % vs précédent"
                                else -> "$variation % vs précédent"
                            },
                            icone = Icons.Default.TouchApp,
                            modifier = Modifier.weight(1f),
                            detailCouleur = if (variation != null && variation < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                        CarteKpi(
                            titre = "Jours actifs",
                            valeur = "${res.joursActifs}",
                            detail = if (res.joursActifs > 0) "%.1f clics / jour actif".format(Locale.FRENCH, res.moyenneJourActif) else null,
                            icone = Icons.Default.Today,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CarteKpi(
                            titre = "Meilleur jour",
                            valeur = if (res.meilleurJour != null) "${res.meilleurJourValeur}" else "—",
                            detail = res.meilleurJour,
                            icone = Icons.Default.BarChart,
                            modifier = Modifier.weight(1f)
                        )
                        CarteKpi(
                            titre = "Série en cours",
                            valeur = "${res.serieActuelle} j",
                            detail = "Record : ${res.meilleureSerie} j",
                            icone = Icons.Default.LocalFireDepartment,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Graphique
            item {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(res.titreBarres, style = MaterialTheme.typography.titleMedium)
                        if (filtre != null) {
                            val nom = res.lignes.firstOrNull { it.cle == filtre }?.libelle ?: "Sélection"
                            Spacer(Modifier.height(8.dp))
                            InputChip(
                                selected = true,
                                onClick = { filtre = null },
                                label = { Text(nom, maxLines = 1) },
                                trailingIcon = { Icon(Icons.Default.Close, contentDescription = "Retirer le filtre", modifier = Modifier.size(16.dp)) }
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        if (res.total == 0) {
                            Text(
                                "Aucun clic sur cette période.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 24.dp)
                            )
                        } else {
                            GrapheBarres(res.barres)
                        }
                    }
                }
            }

            // Classement
            item {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(10.dp)) {
                        Text(
                            if (mode == ModeStats.PHRASES) "Clics par phrase" else "Clics par groupe",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(6.dp)
                        )
                        Text(
                            "Touchez une ligne pour filtrer le graphique.",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 6.dp)
                        )
                        Spacer(Modifier.height(6.dp))
                        if (res.lignes.isEmpty()) {
                            Text(
                                "Aucune donnée à afficher.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp)
                            )
                        } else {
                            val max = res.lignes.maxOf { it.valeur }
                            res.lignes.forEach { l ->
                                LigneClassement(
                                    ligne = l,
                                    max = max,
                                    selectionnee = filtre == l.cle,
                                    onClick = { filtre = if (filtre == l.cle) null else l.cle }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
